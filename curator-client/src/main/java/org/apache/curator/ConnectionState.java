/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator;

import org.apache.curator.connection.ConnectionHandlingPolicy;
import org.apache.curator.drivers.EventTrace;
import org.apache.curator.drivers.OperationTrace;
import org.apache.curator.drivers.TracerDriver;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.DebugUtils;
import org.apache.curator.utils.ThreadUtils;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class ConnectionState implements Watcher, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionState.class);

    private static final int MAX_BACKGROUND_EXCEPTIONS = 10;
    private static final boolean LOG_EVENTS = Boolean.getBoolean(DebugUtils.PROPERTY_LOG_EVENTS);

    /** 用于提供ZooKeeper连接字符串等信息 */
    private final HandleHolder handleHolder;
    /** 用于提供ZooKeeper连接字符串等信息 */
    private final EnsembleProvider ensembleProvider;
    /** 表示该zk客户端是否已经连接上了zk服务 */
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    /** 表示最后经过与zk服务端协商过后的session超时时间 */
    private final AtomicInteger lastNegotiatedSessionTimeoutMs = new AtomicInteger(0);
    /** session超时时间 */
    private final int sessionTimeoutMs;
    /** 连接超时时间 */
    private final int connectionTimeoutMs;
    /** 用于关键事件（比如连接断开、超时等）发生时，打印相应的日志 */
    private final AtomicReference<TracerDriver> tracer;
    private final ConnectionHandlingPolicy connectionHandlingPolicy;
    /** 用于保存后台异步执行的异常 */
    private final Queue<Exception> backgroundExceptions = new ConcurrentLinkedQueue<Exception>();
    private final Queue<Watcher> parentWatchers = new ConcurrentLinkedQueue<Watcher>();
    /** 每次尝试连接zk服务时，该计数+1 */
    private final AtomicLong instanceIndex = new AtomicLong();
    /** 表示该客户端尝试连接zk服务器的开始时间 */
    private volatile long connectionStartMs = 0;


    ConnectionState(ZookeeperFactory zookeeperFactory, EnsembleProvider ensembleProvider, int sessionTimeoutMs, int connectionTimeoutMs, Watcher parentWatcher, AtomicReference<TracerDriver> tracer, boolean canBeReadOnly, ConnectionHandlingPolicy connectionHandlingPolicy) {
        this.ensembleProvider = ensembleProvider;
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.tracer = tracer;
        this.connectionHandlingPolicy = connectionHandlingPolicy;
        if (parentWatcher != null) {
            parentWatchers.offer(parentWatcher);
        }

        handleHolder = new HandleHolder(zookeeperFactory, this, ensembleProvider, sessionTimeoutMs, canBeReadOnly);
    }


    ZooKeeper getZooKeeper() throws Exception {
        if (SessionFailRetryLoop.sessionForThreadHasFailed()) {
            throw new SessionFailRetryLoop.SessionFailedException();
        }

        Exception exception = backgroundExceptions.poll();
        if (exception != null) {
            new EventTrace("background-exceptions", tracer.get()).commit();
            throw exception;
        }

        boolean localIsConnected = isConnected.get();
        if (!localIsConnected) {
            checkTimeouts();
        }

        return handleHolder.getZooKeeper();
    }



    /**
     * 启动连接实例：尝试开始连接zk服务
     *
     * @throws Exception
     */
    void start() throws Exception {
        log.debug("Starting");
        ensembleProvider.start();
        reset();
    }
    /**
     * 重置连接状态，并尝试开始连接zk服务
     *
     * @throws Exception
     */
    synchronized void reset() throws Exception {
        log.debug("reset");

        // 每次尝试连接zk服务时，该计数+1
        instanceIndex.incrementAndGet();
        isConnected.set(false);
        connectionStartMs = System.currentTimeMillis();
        // 关闭和重置连接zk
        handleHolder.closeAndReset();
        // 开始连接zk
        handleHolder.getZooKeeper();
    }

    @Override
    public void close() throws IOException {
        close(0);
    }
    /**
     * 等待相应的时间后，关闭连接
     *
     * @param waitForShutdownTimeoutMs
     * @throws IOException
     */
    public void close(int waitForShutdownTimeoutMs) throws IOException {
        log.debug("Closing");

        CloseableUtils.closeQuietly(ensembleProvider);
        try {
            handleHolder.closeAndClear(waitForShutdownTimeoutMs);
        } catch (Exception e) {
            ThreadUtils.checkInterrupted(e);
            throw new IOException(e);
        } finally {
            isConnected.set(false);
        }
    }

    /**
     * 处理监听事件
     *
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        if (LOG_EVENTS) {
            log.debug("ConnectState watcher: " + event);
        }

        // 没有任何节点，表示创建连接成功(客户端与服务器端创建连接成功后没有任何节点信息)
        if (event.getType() == Watcher.Event.EventType.None) {
            boolean wasConnected = isConnected.get();
            boolean newIsConnected = checkState(event.getState(), wasConnected);
            if (newIsConnected != wasConnected) {
                isConnected.set(newIsConnected);
                connectionStartMs = System.currentTimeMillis();
                if (newIsConnected) {
                    lastNegotiatedSessionTimeoutMs.set(handleHolder.getNegotiatedSessionTimeoutMs());
                    log.debug("Negotiated session timeout: " + lastNegotiatedSessionTimeoutMs.get());
                }
            }
        }

        for (Watcher parentWatcher : parentWatchers) {
            OperationTrace trace = new OperationTrace("connection-state-parent-process", tracer.get(), getSessionId());
            parentWatcher.process(event);
            trace.commit();
        }
    }

    private synchronized void checkTimeouts() throws Exception {
        final AtomicReference<String> newConnectionString = new AtomicReference<>();

        // 获取当前的连接字符串，
        Callable<String> hasNewConnectionString = new Callable<String>() {
            @Override
            public String call() {
                newConnectionString.set(handleHolder.getNewConnectionString());
                return newConnectionString.get();
            }
        };
        int lastNegotiatedSessionTimeoutMs = getLastNegotiatedSessionTimeoutMs();
        int useSessionTimeoutMs = (lastNegotiatedSessionTimeoutMs > 0) ? lastNegotiatedSessionTimeoutMs : sessionTimeoutMs;

        ConnectionHandlingPolicy.CheckTimeoutsResult result = connectionHandlingPolicy.checkTimeouts(hasNewConnectionString, connectionStartMs, useSessionTimeoutMs, connectionTimeoutMs);
        switch (result) {
            default:
            case NOP: {
                break;
            }

            case NEW_CONNECTION_STRING: {
                handleNewConnectionString(newConnectionString.get());
                break;
            }

            case RESET_CONNECTION: {
                if (!Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES)) {
                    long elapsed = System.currentTimeMillis() - connectionStartMs;
                    int maxTimeout = Math.max(useSessionTimeoutMs, connectionTimeoutMs);
                    log.warn(String.format("Connection attempt unsuccessful after %d (greater than max timeout of %d). Resetting connection and trying again with a new connection.", elapsed, maxTimeout));
                }
                reset();
                break;
            }

            case CONNECTION_TIMEOUT: {
                KeeperException.ConnectionLossException connectionLossException = new CuratorConnectionLossException();
                if (!Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES)) {
                    long elapsed = System.currentTimeMillis() - connectionStartMs;
                    log.error(String.format("Connection timed out for connection string (%s) and timeout (%d) / elapsed (%d)", handleHolder.getConnectionString(), connectionTimeoutMs, elapsed), connectionLossException);
                }
                new EventTrace("connections-timed-out", tracer.get(), getSessionId()).commit();
                throw connectionLossException;
            }

            case SESSION_TIMEOUT: {
                handleExpiredSession();
                break;
            }
        }
    }

    /**
     * 返回当前会话ID
     */
    public long getSessionId() {
        long sessionId = 0;
        try {
            ZooKeeper zk = handleHolder.getZooKeeper();
            if (zk != null) {
                sessionId = zk.getSessionId();
            }
        } catch (Exception e) {
            // Ignore the exception
        }
        return sessionId;
    }

    /**
     * 检查当前zk客户端的连接状态
     *
     * @param state
     * @param wasConnected
     * @return
     */
    private boolean checkState(Event.KeeperState state, boolean wasConnected) {
        boolean isConnected = wasConnected;
        boolean checkNewConnectionString = true;
        switch (state) {
            default:

            // 连接断开了
            case Disconnected: {
                isConnected = false;
                break;
            }

            // 连接上zk服务
            case SyncConnected:
            case ConnectedReadOnly: {
                isConnected = true;
                break;
            }

            // 认证失败
            case AuthFailed: {
                isConnected = false;
                log.error("Authentication failed");
                break;
            }

            // session超时
            case Expired: {
                isConnected = false;
                checkNewConnectionString = false;
                handleExpiredSession();
                break;
            }

            // sasl认证失败
            case SaslAuthenticated: {
                // NOP
                break;
            }
        }
        // the session expired is logged in handleExpiredSession, so not log here
        if (state != Event.KeeperState.Expired) {
            new EventTrace(state.toString(), tracer.get(), getSessionId()).commit();
        }

        if (checkNewConnectionString) {
            String newConnectionString = handleHolder.getNewConnectionString();
            if (newConnectionString != null) {
                handleNewConnectionString(newConnectionString);
            }
        }

        return isConnected;
    }

    private void handleNewConnectionString(String newConnectionString) {
        log.info("Connection string changed to: " + newConnectionString);
        new EventTrace("connection-string-changed", tracer.get(), getSessionId()).commit();

        try {
            ZooKeeper zooKeeper = handleHolder.getZooKeeper();
            if (zooKeeper == null) {
                log.warn("Could not update the connection string because getZooKeeper() returned null.");
            } else {
                if (ensembleProvider.updateServerListEnabled()) {
                    zooKeeper.updateServerList(newConnectionString);
                    handleHolder.resetConnectionString(newConnectionString);
                } else {
                    reset();
                }
            }
        } catch (Exception e) {
            ThreadUtils.checkInterrupted(e);
            queueBackgroundException(e);
        }
    }

    /**
     * 处理zk客户端session过期的情况：如果session过期，这里会尝试重连
     */
    private void handleExpiredSession() {
        log.warn("Session expired event received");
        new EventTrace("session-expired", tracer.get(), getSessionId()).commit();

        try {
            reset();
        } catch (Exception e) {
            ThreadUtils.checkInterrupted(e);
            queueBackgroundException(e);
        }
    }




    // getter ...

    long getInstanceIndex() {
        return instanceIndex.get();
    }
    int getLastNegotiatedSessionTimeoutMs() {
        return lastNegotiatedSessionTimeoutMs.get();
    }
    EnsembleProvider getEnsembleProvider() {
        return ensembleProvider;
    }
    boolean isConnected() {
        return isConnected.get();
    }
    void addParentWatcher(Watcher watcher) {
        parentWatchers.offer(watcher);
    }
    void removeParentWatcher(Watcher watcher) {
        parentWatchers.remove(watcher);
    }
    /**
     * 保存异常到backgroundExceptions
     *
     * @param e
     */
    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    private void queueBackgroundException(Exception e) {
        while (backgroundExceptions.size() >= MAX_BACKGROUND_EXCEPTIONS) {
            backgroundExceptions.poll();
        }
        backgroundExceptions.offer(e);
    }

}
