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

import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * 用于ConnectionState
 */
class HandleHolder {

    /** 用于创建ZooKeeper实例的工厂 */
    private final ZookeeperFactory zookeeperFactory;

    // 以下是ZookeeperFactory创建的zk客户端时需要的配置信息

    /** 对应ZookeeperFactory创建的zk客户端的默认Watcher监听器 */
    private final Watcher watcher;
    /** 用于提供zk连接字符串信息 */
    private final EnsembleProvider ensembleProvider;
    /** session超时时间 */
    private final int sessionTimeout;
    /** 是否以只读模式连接zk */
    private final boolean canBeReadOnly;
    /** 用于获取zk客户端和连接字符串信息 */
    private volatile Helper helper;

    HandleHolder(ZookeeperFactory zookeeperFactory, Watcher watcher, EnsembleProvider ensembleProvider, int sessionTimeout, boolean canBeReadOnly) {
        this.zookeeperFactory = zookeeperFactory;
        this.watcher = watcher;
        this.ensembleProvider = ensembleProvider;
        this.sessionTimeout = sessionTimeout;
        this.canBeReadOnly = canBeReadOnly;
    }

    /**
     * 获取zk客户端
     *
     * @return
     * @throws Exception
     */
    ZooKeeper getZooKeeper() throws Exception {
        return (helper != null) ? helper.getZooKeeper() : null;
    }

    /**
     * 获取session超时时间
     *
     * @return
     */
    int getNegotiatedSessionTimeoutMs() {
        return (helper != null) ? helper.getNegotiatedSessionTimeoutMs() : 0;
    }

    /**
     * 获取connectionString
     *
     * @return
     */
    String getConnectionString() {
        return (helper != null) ? helper.getConnectionString() : null;
    }

    /**
     * 获取当前的connectionString
     *
     * @return
     */
    String getNewConnectionString() {
        String helperConnectionString = (helper != null) ? helper.getConnectionString() : null;
        String ensembleProviderConnectionString = ensembleProvider.getConnectionString();

        // 当helper和ensembleProvider的连接字符不一样时，返回ensembleProviderConnectionString
        return ((helperConnectionString != null) && !ensembleProviderConnectionString.equals(helperConnectionString)) ? ensembleProviderConnectionString : null;
    }

    /**
     * 重置connectionString
     *
     * @param connectionString
     */
    void resetConnectionString(String connectionString) {
        if (helper != null) {
            helper.resetConnectionString(connectionString);
        }
    }

    /**
     * 关闭zk客户端并将{@link #helper}设置为null
     *
     * @param waitForShutdownTimeoutMs
     * @throws Exception
     */
    void closeAndClear(int waitForShutdownTimeoutMs) throws Exception {
        internalClose(waitForShutdownTimeoutMs);
        helper = null;
    }

    /**
     * 关闭zk客户端，并设置helper
     *
     * @throws Exception
     */
    void closeAndReset() throws Exception {
        // 立即关闭zk客户端
        internalClose(0);

        // 初始Helper和非同步Helper之间共享的数据
        Helper.Data data = new Helper.Data();
        // 调用getZooKeeper时，第一助手将同步。后续呼叫不同步。 noinspection NonAtomicOperationOnVolatileField
        helper = new Helper(data) {
            @Override
            ZooKeeper getZooKeeper() throws Exception {
                synchronized (this) {
                    if (data.zooKeeperHandle == null) {
                        resetConnectionString(ensembleProvider.getConnectionString());
                        data.zooKeeperHandle = zookeeperFactory.newZooKeeper(data.connectionString, sessionTimeout, watcher, canBeReadOnly);
                    }

                    helper = new Helper(data);

                    return super.getZooKeeper();
                }
            }
        };
    }

    /**
     * 关闭zk客户端
     *
     * @param waitForShutdownTimeoutMs  等待对应的时间后关闭zk客户端
     * @throws Exception
     */
    private void internalClose(int waitForShutdownTimeoutMs) throws Exception {
        try {
            ZooKeeper zooKeeper = (helper != null) ? helper.getZooKeeper() : null;
            if (zooKeeper != null) {
                Watcher dummyWatcher = new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                    }
                };
                // 清除默认观察者，以免错误处理任何新事件
                zooKeeper.register(dummyWatcher);

                if (waitForShutdownTimeoutMs == 0) {
                    // 来自在ZK的事件线程中执行的closeAndReset()。无法使用zooKeeper.close(n)，否则我们将获得死锁
                    zooKeeper.close();
                } else {
                    zooKeeper.close(waitForShutdownTimeoutMs);
                }
            }
        } catch (InterruptedException dummy) {
            Thread.currentThread().interrupt();
        }
    }
}
