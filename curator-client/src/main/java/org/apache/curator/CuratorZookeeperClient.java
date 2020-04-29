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

import com.google.common.base.Preconditions;
import org.apache.curator.connection.ConnectionHandlingPolicy;
import org.apache.curator.connection.StandardConnectionHandlingPolicy;
import org.apache.curator.drivers.OperationTrace;
import org.apache.curator.drivers.TracerDriver;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.utils.DefaultTracerDriver;
import org.apache.curator.utils.DefaultZookeeperFactory;
import org.apache.curator.utils.ThreadUtils;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zookeeper周围的包装器，用于处理一些低层清洁工作
 */
@SuppressWarnings("UnusedDeclaration")
public class CuratorZookeeperClient implements Closeable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    /** 重要组件，用于处理连接和管理ZooKeeper实例 */
    private final ConnectionState state;
    private final AtomicReference<RetryPolicy> retryPolicy = new AtomicReference<RetryPolicy>();
    /** 连接超时时间 */
    private final int connectionTimeoutMs;
    /** 等待响应的时间后关闭zk客户端 */
    private final int waitForShutdownTimeoutMs;
    /** 用于标记该CuratorZookeeperClient实例是启动，调用{@link #start()}时，设置为true；调用{@link #close()}时，设置为false */
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicReference<TracerDriver> tracer = new AtomicReference<TracerDriver>(new DefaultTracerDriver());
    private final ConnectionHandlingPolicy connectionHandlingPolicy;

    // 构造器

    /**
     *
     * @param connectString list of servers to connect to
     * @param sessionTimeoutMs session timeout
     * @param connectionTimeoutMs connection timeout
     * @param watcher default watcher or null
     * @param retryPolicy the retry policy to use
     */
    public CuratorZookeeperClient(String connectString, int sessionTimeoutMs, int connectionTimeoutMs, Watcher watcher, RetryPolicy retryPolicy) {
        this(new DefaultZookeeperFactory(), new FixedEnsembleProvider(connectString), sessionTimeoutMs, connectionTimeoutMs, watcher, retryPolicy, false, new StandardConnectionHandlingPolicy());
    }
    /**
     * @param ensembleProvider the ensemble provider
     * @param sessionTimeoutMs session timeout
     * @param connectionTimeoutMs connection timeout
     * @param watcher default watcher or null
     * @param retryPolicy the retry policy to use
     */
    public CuratorZookeeperClient(EnsembleProvider ensembleProvider, int sessionTimeoutMs, int connectionTimeoutMs, Watcher watcher, RetryPolicy retryPolicy) {
        this(new DefaultZookeeperFactory(), ensembleProvider, sessionTimeoutMs, connectionTimeoutMs, watcher, retryPolicy, false, new StandardConnectionHandlingPolicy());
    }
    /**
     * @param zookeeperFactory factory for creating {@link ZooKeeper} instances
     * @param ensembleProvider the ensemble provider
     * @param sessionTimeoutMs session timeout
     * @param connectionTimeoutMs connection timeout
     * @param watcher default watcher or null
     * @param retryPolicy the retry policy to use
     * @param canBeReadOnly if true, allow ZooKeeper client to enter
     *                      read only mode in case of a network partition. See
     *                      {@link ZooKeeper#ZooKeeper(String, int, Watcher, long, byte[], boolean)}
     *                      for details
     */
    public CuratorZookeeperClient(ZookeeperFactory zookeeperFactory, EnsembleProvider ensembleProvider, int sessionTimeoutMs, int connectionTimeoutMs, Watcher watcher, RetryPolicy retryPolicy, boolean canBeReadOnly) {
        this(zookeeperFactory, ensembleProvider, sessionTimeoutMs, connectionTimeoutMs, watcher, retryPolicy, canBeReadOnly, new StandardConnectionHandlingPolicy());
    }
    /**
     * @param zookeeperFactory factory for creating {@link ZooKeeper} instances
     * @param ensembleProvider the ensemble provider
     * @param sessionTimeoutMs session timeout
     * @param connectionTimeoutMs connection timeout
     * @param watcher default watcher or null
     * @param retryPolicy the retry policy to use
     * @param canBeReadOnly if true, allow ZooKeeper client to enter
     *                      read only mode in case of a network partition. See
     *                      {@link ZooKeeper#ZooKeeper(String, int, Watcher, long, byte[], boolean)}
     *                      for details
     * @param connectionHandlingPolicy connection handling policy - use one of the pre-defined policies or write your own
     * @since 3.0.0
     */
    public CuratorZookeeperClient(ZookeeperFactory zookeeperFactory, EnsembleProvider ensembleProvider, int sessionTimeoutMs, int connectionTimeoutMs, Watcher watcher, RetryPolicy retryPolicy, boolean canBeReadOnly, ConnectionHandlingPolicy connectionHandlingPolicy) {
        this(zookeeperFactory, ensembleProvider, sessionTimeoutMs, connectionTimeoutMs, 0,
                watcher, retryPolicy, canBeReadOnly, connectionHandlingPolicy);
    }
    // 核心方法
    /**
     * @param zookeeperFactory          用于创建{@link ZooKeeper}实例的工厂
     * @param ensembleProvider          用于提供ZooKeeper连接字符串
     * @param sessionTimeoutMs          session timeout
     * @param connectionTimeoutMs       connection timeout
     * @param waitForShutdownTimeoutMs  关闭操作的默认超时时间
     * @param watcher                   默认观察者或null
     * @param retryPolicy               重试策略，不能为null
     * @param canBeReadOnly             如果为true，则在网络分区的情况下允许ZooKeeper客户端进入只读模式。 See {@link ZooKeeper#ZooKeeper(String, int, Watcher, long, byte[], boolean)} for details
     * @param connectionHandlingPolicy 连接处理策略-使用预定义策略之一或编写自己的策略
     * @since 4.0.2
     */
    public CuratorZookeeperClient(ZookeeperFactory zookeeperFactory, EnsembleProvider ensembleProvider, int sessionTimeoutMs, int connectionTimeoutMs, int waitForShutdownTimeoutMs, Watcher watcher, RetryPolicy retryPolicy, boolean canBeReadOnly, ConnectionHandlingPolicy connectionHandlingPolicy) {
        this.connectionHandlingPolicy = connectionHandlingPolicy;
        // session超时时间必须 < 连接超时时间
        if (sessionTimeoutMs < connectionTimeoutMs) {
            log.warn(String.format("session timeout [%d] is less than connection timeout [%d]", sessionTimeoutMs, connectionTimeoutMs));
        }

        retryPolicy = Preconditions.checkNotNull(retryPolicy, "retryPolicy cannot be null");
        ensembleProvider = Preconditions.checkNotNull(ensembleProvider, "ensembleProvider cannot be null");

        this.connectionTimeoutMs = connectionTimeoutMs;
        this.waitForShutdownTimeoutMs = waitForShutdownTimeoutMs;
        state = new ConnectionState(zookeeperFactory, ensembleProvider, sessionTimeoutMs, connectionTimeoutMs, watcher, tracer, canBeReadOnly, connectionHandlingPolicy);
        setRetryPolicy(retryPolicy);
    }


    /**
     * 启动zk客户端，尝试开始连接zk服务
     *
     * @throws IOException errors
     */
    public void start() throws Exception {
        log.debug("Starting");

        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Already started");
        }

        state.start();
    }

    /**
     * 获取原生的zk客户端
     *
     * @return client the client
     * @throws Exception if the connection timeout has elapsed or an exception occurs in a background process
     */
    public ZooKeeper getZooKeeper() throws Exception {
        Preconditions.checkState(started.get(), "Client is not started");
        return state.getZooKeeper();
    }

    /**
     * This method blocks until the connection to ZK succeeds. Use with caution. The block
     * will timeout after the connection timeout (as passed to the constructor) has elapsed
     *
     * @return true if the connection succeeded, false if not
     * @throws InterruptedException interrupted while waiting
     */
    public boolean blockUntilConnectedOrTimedOut() throws InterruptedException {
        Preconditions.checkState(started.get(), "Client is not started");

        log.debug("blockUntilConnectedOrTimedOut() start");
        OperationTrace trace = startAdvancedTracer("blockUntilConnectedOrTimedOut");

        internalBlockUntilConnectedOrTimedOut();

        trace.commit();

        boolean localIsConnected = state.isConnected();
        log.debug("blockUntilConnectedOrTimedOut() end. isConnected: " + localIsConnected);

        return localIsConnected;
    }
    /**
     * 仅限内部使用，一直阻塞线程，知道连接上zk服务或连接超时
     *
     * @throws InterruptedException interruptions
     */
    public void internalBlockUntilConnectedOrTimedOut() throws InterruptedException {
        long waitTimeMs = connectionTimeoutMs;
        while (!state.isConnected() && (waitTimeMs > 0)) {
            final CountDownLatch latch = new CountDownLatch(1);
            Watcher tempWatcher = new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    latch.countDown();
                }
            };

            state.addParentWatcher(tempWatcher);
            long startTimeMs = System.currentTimeMillis();
            long timeoutMs = Math.min(waitTimeMs, 1000);
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } finally {
                state.removeParentWatcher(tempWatcher);
            }
            long elapsed = Math.max(1, System.currentTimeMillis() - startTimeMs);
            waitTimeMs -= elapsed;
        }
    }



    // ConnectionState 提供的能力

    /**
     * Return the configured connection handling policy
     *
     * @return ConnectionHandlingPolicy
     */
    public ConnectionHandlingPolicy getConnectionHandlingPolicy() {
        return connectionHandlingPolicy;
    }
    /**
     * Return the most recent value of {@link ZooKeeper#getSessionTimeout()} or 0
     *
     * @return session timeout or 0
     */
    public int getLastNegotiatedSessionTimeoutMs() {
        return state.getLastNegotiatedSessionTimeoutMs();
    }
    void addParentWatcher(Watcher watcher) {
        state.addParentWatcher(watcher);
    }
    void removeParentWatcher(Watcher watcher) {
        state.removeParentWatcher(watcher);
    }
    /**
     * Every time a new {@link ZooKeeper} instance is allocated, the "instance index"
     * is incremented.
     *
     * @return the current instance index
     */
    public long getInstanceIndex() {
        return state.getInstanceIndex();
    }
    /**
     * Returns true if the client is current connected
     *
     * @return true/false
     */
    public boolean isConnected() {
        return state.isConnected();
    }
    /**
     * For internal use only - reset the internally managed ZK handle
     *
     * @throws Exception errors
     */
    public void reset() throws Exception {
        state.reset();
    }
    /**
     * Returns the current known connection string - not guaranteed to be correct
     * value at any point in the future.
     *
     * @return connection string
     */
    public String getCurrentConnectionString() {
        return state.getEnsembleProvider().getConnectionString();
    }




    /**
     * 设置重试策略
     *
     * @param policy new policy
     */
    public void setRetryPolicy(RetryPolicy policy) {
        Preconditions.checkNotNull(policy, "policy cannot be null");

        retryPolicy.set(policy);
    }
    /**
     * 返货重试策略
     *
     * @return policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy.get();
    }
    /**
     * Return the configured connection timeout
     *
     * @return timeout
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    /**
     * Return the current tracing driver
     *
     * @return tracing driver
     */
    public TracerDriver getTracerDriver() {
        return tracer.get();
    }
    /**
     * Change the tracing driver
     *
     * @param tracer new tracing driver
     */
    public void setTracerDriver(TracerDriver tracer) {
        this.tracer.set(tracer);
    }
    /**
     * 返回一个新的重试循环。所有操作应在重试循环中执行
     *
     * @return new retry loop
     */
    public RetryLoop newRetryLoop() {
        return new RetryLoopImpl(retryPolicy.get(), tracer);
    }

    /**
     * Return a new "session fail" retry loop. See {@link SessionFailRetryLoop} for details
     * on when to use it.
     *
     * @param mode failure mode
     * @return new retry loop
     */
    public SessionFailRetryLoop newSessionFailRetryLoop(SessionFailRetryLoop.Mode mode) {
        return new SessionFailRetryLoop(this, mode);
    }
    /**
     * Start a new tracer
     * @param name name of the event
     * @return the new tracer ({@link TimeTrace#commit()} must be called)
     */
    public TimeTrace startTracer(String name) {
        return new TimeTrace(name, tracer.get());
    }
    /**
     * Start a new advanced tracer with more metrics being recorded
     * @param name name of the event
     * @return the new tracer ({@link OperationTrace#commit()} must be called)
     */
    public OperationTrace startAdvancedTracer(String name) {
        return new OperationTrace(name, tracer.get(), state.getSessionId());
    }


    /**
     * Close the client.
     *
     * Same as {@link #close(int) } using the timeout set at construction time.
     *
     * @see #close(int)
     */
    @Override
    public void close() {
        close(waitForShutdownTimeoutMs);
    }
    /**
     * Close this client object as the {@link #close() } method.
     * This method will wait for internal resources to be released.
     *
     * @param waitForShutdownTimeoutMs timeout (in milliseconds) to wait for resources to be released.
     *                  Use zero or a negative value to skip the wait.
     */
    public void close(int waitForShutdownTimeoutMs) {
        log.debug("Closing, waitForShutdownTimeoutMs {}", waitForShutdownTimeoutMs);

        started.set(false);
        try {
            state.close(waitForShutdownTimeoutMs);
        } catch (IOException e) {
            ThreadUtils.checkInterrupted(e);
            log.error("", e);
        }
    }
}
