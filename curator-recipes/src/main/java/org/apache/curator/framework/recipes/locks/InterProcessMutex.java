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

package org.apache.curator.framework.recipes.locks;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.PathUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式可重入排它锁,此外，此互斥锁是“公平的”-每个用户都将按照请求的顺序获得互斥锁（从ZK的角度来看）
 */
public class InterProcessMutex implements InterProcessLock, Revocable<InterProcessMutex> {

    private final LockInternals internals;
    private final String basePath;

    /**
     * 保存当前线程对应的分布式锁信息
     */
    private final ConcurrentMap<Thread, LockData> threadData = Maps.newConcurrentMap();

    /**
     * 封装分布式锁的信息，包括获取锁的线程、锁节点路径和当前重入锁的次数
     */
    private static class LockData {
        final Thread owningThread;
        final String lockPath;
        final AtomicInteger lockCount = new AtomicInteger(1);

        private LockData(Thread owningThread, String lockPath) {
            this.owningThread = owningThread;
            this.lockPath = lockPath;
        }
    }

    private static final String LOCK_NAME = "lock-";



    /**
     * @param client client
     * @param path   the path to lock
     */
    public InterProcessMutex(CuratorFramework client, String path) {
        this(client, path, new StandardLockInternalsDriver());
    }
    /**
     * @param client client
     * @param path   the path to lock
     * @param driver lock driver
     */
    public InterProcessMutex(CuratorFramework client, String path, LockInternalsDriver driver) {
        this(client, path, LOCK_NAME, 1, driver);
    }
    InterProcessMutex(CuratorFramework client, String path, String lockName, int maxLeases, LockInternalsDriver driver) {
        basePath = PathUtils.validatePath(path);
        internals = new LockInternals(client, driver, path, lockName, maxLeases);
    }




    /**
     * 获取互斥锁 - 阻塞，直到可用为止。注意：同一线程可以重新调用获取，每个获取请求必须通过调用{@link #release()}来平衡
     *
     * @throws Exception zk连接中断时会有异常
     */
    @Override
    public void acquire() throws Exception {
        if (!internalLock(-1, null)) {
            throw new IOException("Lost connection while trying to acquire lock: " + basePath);
        }
    }

    /**
     * Acquire the mutex - blocks until it's available or the given time expires. Note: the same thread
     * can call acquire re-entrantly. Each call to acquire that returns true must be balanced by a call
     * to {@link #release()}
     *
     * @param time time to wait
     * @param unit time unit
     * @return true if the mutex was acquired, false if not
     * @throws Exception ZK errors, connection interruptions
     */
    @Override
    public boolean acquire(long time, TimeUnit unit) throws Exception {
        return internalLock(time, unit);
    }

    /**
     * Returns true if the mutex is acquired by a thread in this JVM
     *
     * @return true/false
     */
    @Override
    public boolean isAcquiredInThisProcess() {
        return (threadData.size() > 0);
    }

    /**
     * 如果调用线程与获取该互斥锁的线程相同，则执行一次互斥锁释放。
     * 如果线程进行了多次调用来获取，则此方法返回时，互斥体仍将保留。
     *
     * @throws Exception ZK errors, interruptions, current thread does not own the lock
     */
    @Override
    public void release() throws Exception {
        // 关于并发性的注意事项：给定的lockData实例只能由单个线程执行，因此不需要锁定

        Thread currentThread = Thread.currentThread();
        LockData lockData = threadData.get(currentThread);
        if (lockData == null) {
            throw new IllegalMonitorStateException("You do not own the lock: " + basePath);
        }

        int newLockCount = lockData.lockCount.decrementAndGet();
        if (newLockCount > 0) {
            return;
        }

        if (newLockCount < 0) {
            throw new IllegalMonitorStateException("Lock count has gone negative for lock: " + basePath);
        }

        try {
            internals.releaseLock(lockData.lockPath);
        } finally {
            threadData.remove(currentThread);
        }
    }

    /**
     * Return a sorted list of all current nodes participating in the lock
     *
     * @return list of nodes
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<String> getParticipantNodes() throws Exception {
        return LockInternals.getParticipantNodes(internals.getClient(), basePath, internals.getLockName(), internals.getDriver());
    }

    @Override
    public void makeRevocable(RevocationListener<InterProcessMutex> listener) {
        makeRevocable(listener, MoreExecutors.directExecutor());
    }

    @Override
    public void makeRevocable(final RevocationListener<InterProcessMutex> listener, Executor executor) {
        internals.makeRevocable(new RevocationSpec(executor, new Runnable() {
            @Override
            public void run() {
                listener.revocationRequested(InterProcessMutex.this);
            }
        }));
    }

    /**
     * Returns true if the mutex is acquired by the calling thread
     *
     * @return true/false
     */
    public boolean isOwnedByCurrentThread() {
        LockData lockData = threadData.get(Thread.currentThread());
        return (lockData != null) && (lockData.lockCount.get() > 0);
    }

    /**
     * 获取或节点数据，默认为null
     *
     * @return
     */
    protected byte[] getLockNodeBytes() {
        return null;
    }

    protected String getLockPath() {
        LockData lockData = threadData.get(Thread.currentThread());
        return lockData != null ? lockData.lockPath : null;
    }

    /**
     * （1）首先判断该线程是否已经获取锁，如果已经获取锁lockcount加一，加锁成功。该步骤支持锁重入。
     *
     * （2）调用attemptlock()方法获取锁。
     *
     * （3）获取锁成功后向threadData中添加数据，记录该线程已经获取锁。
     *
     * @param time
     * @param unit
     * @return
     * @throws Exception
     */
    private boolean internalLock(long time, TimeUnit unit) throws Exception {
        // 关于并发性的注意事项：给定的lockData实例只能由单个线程执行，因此不需要锁定
        Thread currentThread = Thread.currentThread();

        LockData lockData = threadData.get(currentThread);
        if (lockData != null) {
            lockData.lockCount.incrementAndGet();
            return true;
        }

        String lockPath = internals.attemptLock(time, unit, getLockNodeBytes());
        if (lockPath != null) {
            LockData newLockData = new LockData(currentThread, lockPath);
            threadData.put(currentThread, newLockData);
            return true;
        }

        return false;
    }
}
