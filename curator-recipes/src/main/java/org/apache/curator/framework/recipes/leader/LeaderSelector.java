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

package org.apache.curator.framework.recipes.leader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.utils.CloseableExecutorService;
import org.apache.curator.utils.PathUtils;
import org.apache.curator.utils.ThreadUtils;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大致原理：LeaderSelector利用InterProcessMutex分布式锁进行抢主，抢到锁的即为Leader
 *
 * 在连接到Zookeeper集群的一组JMV中的多个竞争者中选择“领导者”的抽象
 * 如果一组N个线程/进程争夺领导权，则将分配一个领导者，直到其释放领导权为止，此时将从该组中选择另一个
 * 请注意，此类使用基础的{@link InterProcessMutex}，因此，领导者选举是“公平的” - 从ZK的角度来看，每个用户都将按照最初请求的顺序成为领导者。
 * </p>
 */
public class LeaderSelector implements Closeable {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private enum State {
        LATENT,
        STARTED,
        CLOSED
    }

    private final CuratorFramework client;
    private final LeaderSelectorListener listener;
    private final CloseableExecutorService executorService;
    private final InterProcessMutex mutex;
    private final AtomicReference<State> state = new AtomicReference<State>(State.LATENT);
    /** 默认情况下，返回{@link LeaderSelectorListener#takeLeadership(CuratorFramework)}时，不会重新排队该实例, 调用此方法会将领导者选择器置于始终会重新排队的模式 */
    private final AtomicBoolean autoRequeue = new AtomicBoolean(false);
    /** 当将该实例放到队列中进行leader选举时，会创建一个任务，该参数指向对应的任务 */
    private final AtomicReference<Future<?>> ourTask = new AtomicReference<Future<?>>(null);

    private volatile boolean hasLeadership;
    private volatile String id = "";

    @VisibleForTesting
    volatile CountDownLatch debugLeadershipLatch = null;
    volatile CountDownLatch debugLeadershipWaitLatch = null;



    /** 标记该实例是否在leader选举的队列中 */
    private boolean isQueued = false;

    private static final ThreadFactory defaultThreadFactory = ThreadUtils.newThreadFactory("LeaderSelector");


    /**
     * @param client          zk客户端
     * @param leaderPath      选举leader的节点路径
     * @param executorService thread pool to use
     * @param listener        leader选举监听
     */
    public LeaderSelector(CuratorFramework client, String leaderPath, CloseableExecutorService executorService, LeaderSelectorListener listener) {
        // 入参检查
        Preconditions.checkNotNull(client, "client cannot be null");
        PathUtils.validatePath(leaderPath);
        Preconditions.checkNotNull(listener, "listener cannot be null");

        this.client = client;
        this.listener = new WrappedListener(this, listener);
        hasLeadership = false;

        this.executorService = executorService;
        mutex = new InterProcessMutex(client, leaderPath) {
            /**
             * 返回分布式锁节点路径
             *
             * @return
             */
            @Override
            protected byte[] getLockNodeBytes() {
                return (id.length() > 0) ? getIdBytes(id) : null;
            }
        };
    }
    public LeaderSelector(CuratorFramework client, String leaderPath, LeaderSelectorListener listener) {
        this(client, leaderPath, new CloseableExecutorService(Executors.newSingleThreadExecutor(defaultThreadFactory), true), listener);
    }
    @SuppressWarnings("UnusedParameters")
    @Deprecated
    public LeaderSelector(CuratorFramework client, String leaderPath, ThreadFactory threadFactory, Executor executor, LeaderSelectorListener listener) {
        this(client, leaderPath, new CloseableExecutorService(wrapExecutor(executor), true), listener);
    }
    public LeaderSelector(CuratorFramework client, String leaderPath, ExecutorService executorService, LeaderSelectorListener listener) {
        this(client, leaderPath, new CloseableExecutorService(executorService), listener);
    }



    /**
     * 开始进行leader选举，选举完成后通过{@link LeaderSelectorListener}监听回调
     * 尝试领导。尝试在后台完成-即此方法立即返回。
     * 注意：以前的版本允许多次调用此方法，此版本，不再支持。为此，请使用{@link #requeue()}。
     */
    public void start() {
        Preconditions.checkState(state.compareAndSet(State.LATENT, State.STARTED), "Cannot be started more than once");
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        Preconditions.checkState(!hasLeadership, "Already has leadership");

        // 添加监听器
        client.getConnectionStateListenable().addListener(listener);
        requeue();
    }

    /**
     * 将id转为字节返回
     *
     * @param id
     * @return
     */
    static byte[] getIdBytes(String id) {
        try {
            return id.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // this should never happen
            throw new Error(e);
        }
    }

    /**
     * 默认情况下，返回{@link LeaderSelectorListener#takeLeadership(CuratorFramework)}时，不会重新排队该实例
     * 调用此方法会将领导者选择器置于始终会重新排队的模式。
     */
    public void autoRequeue() {
        autoRequeue.set(true);
    }

    /**
     * 重新排队尝试进行领导：
     * 如果此实例已在队列中，则什么也不会发生，并返回false。
     * 如果实例未排队，则重新对其进行排队并返回true
     *
     * @return true if re-queue is successful
     */
    public boolean requeue() {
        Preconditions.checkState(state.get() == State.STARTED, "close() has already been called");
        return internalRequeue();
    }
    /**
     * 重新排队尝试进行领导：
     * 如果此实例已在队列中，则什么也不会发生，并返回false。
     * 如果实例未排队，则重新对其进行排队并返回true
     *
     * @return true if re-queue is successful
     */
    private synchronized boolean internalRequeue() {
        if (!isQueued && (state.get() == State.STARTED)) {
            isQueued = true;
            Future<Void> task = executorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        doWorkLoop();
                    } finally {
                        // 将isQueued设置为false，表示从选举的队列中移除了该实例
                        clearIsQueued();

                        // 如果是需要自动再次进行leader选举时，则再次调用internalRequeue()继续争抢锁
                        if (autoRequeue.get()) {
                            internalRequeue();
                        }
                    }
                    return null;
                }
            });
            ourTask.set(task);

            return true;
        }
        return false;
    }
    /**
     * 开始leader选举工作
     *
     * @throws Exception
     */
    private void doWorkLoop() throws Exception {
        KeeperException exception = null;
        try {
            doWork();
        } catch (KeeperException.ConnectionLossException e) {
            exception = e;
        } catch (KeeperException.SessionExpiredException e) {
            exception = e;
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
        // autoRequeue应该忽略连接丢失或会话过期，并继续尝试
        if ((exception != null) && !autoRequeue.get()) {
            throw exception;
        }
    }

    /**
     * 开始leader选举工作
     * @throws Exception
     */
    @VisibleForTesting
    void doWork() throws Exception {
        // 开始选举前大家都不是leader
        hasLeadership = false;
        try {
            // 请求分布式锁，进行leader选举
            mutex.acquire();

            // 走到这里说明，抢到了锁，将hasLeadership = true，说明当前实例已经是leader了
            hasLeadership = true;
            try {
                if (debugLeadershipLatch != null) {
                    debugLeadershipLatch.countDown();
                }

                if (debugLeadershipWaitLatch != null) {
                    debugLeadershipWaitLatch.await();
                }

                // 触发监听器
                listener.takeLeadership(client);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Throwable e) {
                ThreadUtils.checkInterrupted(e);
            } finally {
                //
                clearIsQueued();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            // 如果是leader
            if (hasLeadership) {
                hasLeadership = false;
                // 清除所有中断的status，以便mutex.release()立即起作用
                boolean wasInterrupted = Thread.interrupted();
                try {
                    // 释放锁
                    mutex.release();
                } catch (Exception e) {
                    if (failedMutexReleaseCount != null) {
                        failedMutexReleaseCount.incrementAndGet();
                    }

                    ThreadUtils.checkInterrupted(e);
                    log.error("The leader threw an exception", e);
                    // ignore errors - this is just a safety
                } finally {
                    if (wasInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }



    /**
     * Shutdown this selector and remove yourself from the leadership group
     */
    public synchronized void close() {
        Preconditions.checkState(state.compareAndSet(State.STARTED, State.CLOSED), "Already closed or has not been started");

        client.getConnectionStateListenable().removeListener(listener);
        executorService.close();
        ourTask.set(null);
    }

    /**
     * <p>
     * 返回领导者选择中的当前参与者的集合
     *
     * 注意：此方法轮询ZK服务器。因此，它可能会返回与{@link #hasLeadership()}不匹配的值，因为hasLeadership使用该类的本地字段。
     * </p>
     *
     * @return participants
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<Participant> getParticipants() throws Exception {
        Collection<String> participantNodes = mutex.getParticipantNodes();

        return getParticipants(client, participantNodes);
    }

    static Collection<Participant> getParticipants(CuratorFramework client, Collection<String> participantNodes) throws Exception {
        ImmutableList.Builder<Participant> builder = ImmutableList.builder();

        boolean isLeader = true;
        for (String path : participantNodes) {
            Participant participant = participantForPath(client, path, isLeader);

            if (participant != null) {
                builder.add(participant);

                isLeader = false;   // by definition the first node is the leader
            }
        }

        return builder.build();
    }

    /**
     * <p>
     * Return the id for the current leader. If for some reason there is no
     * current leader, a dummy participant is returned.
     * </p>
     * <p>
     * <p>
     * <B>NOTE</B> - this method polls the ZK server. Therefore it can possibly
     * return a value that does not match {@link #hasLeadership()} as hasLeadership
     * uses a local field of the class.
     * </p>
     *
     * @return leader
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Participant getLeader() throws Exception {
        Collection<String> participantNodes = mutex.getParticipantNodes();
        return getLeader(client, participantNodes);
    }

    static Participant getLeader(CuratorFramework client, Collection<String> participantNodes) throws Exception {
        Participant result = null;

        if (participantNodes.size() > 0) {
            Iterator<String> iter = participantNodes.iterator();
            while (iter.hasNext()) {
                result = participantForPath(client, iter.next(), true);

                if (result != null) {
                    break;
                }
            }
        }

        if (result == null) {
            result = new Participant();
        }

        return result;
    }

    /**
     * Return true if leadership is currently held by this instance
     *
     * @return true/false
     */
    public boolean hasLeadership() {
        return hasLeadership;
    }

    /**
     * Attempt to cancel and interrupt the current leadership if this instance has leadership
     */
    public synchronized void interruptLeadership() {
        Future<?> task = ourTask.get();
        if (task != null) {
            task.cancel(true);
        }
    }

    private static Participant participantForPath(CuratorFramework client, String path, boolean markAsLeader) throws Exception {
        try {
            byte[] bytes = client.getData().forPath(path);
            String thisId = new String(bytes, "UTF-8");
            return new Participant(thisId, markAsLeader);
        } catch (KeeperException.NoNodeException e) {
            return null;
        }
    }

    /** 用于统计放弃leader时，释放的分布式锁的失败次数 */
    @VisibleForTesting
    volatile AtomicInteger failedMutexReleaseCount = null;





    /**
     * 当时实例放弃leader时，会调用该方法，将isQueued设置为false，
     */
    private synchronized void clearIsQueued() {
        isQueued = false;
    }

    // 弃用的构造函数的临时包装
    private static ExecutorService wrapExecutor(final Executor executor) {
        return new AbstractExecutorService() {
            private volatile boolean isShutdown = false;
            private volatile boolean isTerminated = false;

            @Override
            public void shutdown() {
                isShutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                return Lists.newArrayList();
            }

            @Override
            public boolean isShutdown() {
                return isShutdown;
            }

            @Override
            public boolean isTerminated() {
                return isTerminated;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void execute(Runnable command) {
                try {
                    executor.execute(command);
                } finally {
                    isShutdown = true;
                    isTerminated = true;
                }
            }
        };
    }




    // getter and setter ...

    /**
     * 设置要为此领导者存储的ID。将是调用{@link #getParticipants()}时返回的值。
     * 重要说明：必须先在{@link #start()}之前调用才能生效。
     *
     * @param id ID
     */
    public void setId(String id) {
        Preconditions.checkNotNull(id, "id cannot be null");
        this.id = id;
    }
    /**
     * 返回通过{@link #setId(String)}设置的ID
     *
     * @return id
     */
    public String getId() {
        return id;
    }


    private static class WrappedListener implements LeaderSelectorListener {
        private final LeaderSelector leaderSelector;
        private final LeaderSelectorListener listener;

        public WrappedListener(LeaderSelector leaderSelector, LeaderSelectorListener listener) {
            this.leaderSelector = leaderSelector;
            this.listener = listener;
        }

        @Override
        public void takeLeadership(CuratorFramework client) throws Exception {
            listener.takeLeadership(client);
        }

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            try {
                listener.stateChanged(client, newState);
            } catch (CancelLeadershipException dummy) {
                leaderSelector.interruptLeadership();
            }
        }
    }
}
