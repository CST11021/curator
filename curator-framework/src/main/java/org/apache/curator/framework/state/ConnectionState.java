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
package org.apache.curator.framework.state;

import org.apache.curator.connection.ConnectionHandlingPolicy;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;

/**
 * Represents state changes in the connection to ZK
 */
public enum ConnectionState {

    /** 发送以首次成功连接到服务器。注意：对于任何CuratorFramework实例，您只会收到这些消息之一。 */
    CONNECTED {
        public boolean isConnected() {
            return true;
        }
    },
    /**
     * 失去了联系。领导者，锁等应暂停，直到重新建立连接。
     */
    SUSPENDED {
        public boolean isConnected() {
            return false;
        }
    },
    /** 暂停，丢失或只读连接已重新建立 */
    RECONNECTED {
        public boolean isConnected() {
            return true;
        }
    },
    /**
     * <p>
     *     Curator will set the LOST state when it believes that the ZooKeeper session
     *     has expired. ZooKeeper connections have a session. When the session expires, clients must take appropriate
     *     action. In Curator, this is complicated by the fact that Curator internally manages the ZooKeeper
     *     connection. Curator will set the LOST state when any of the following occurs:
     *     a) ZooKeeper returns a {@link Watcher.Event.KeeperState#Expired} or {@link KeeperException.Code#SESSIONEXPIRED};
     *     b) Curator closes the internally managed ZooKeeper instance; c) The session timeout
     *     elapses during a network partition.
     * </p>
     *
     * <p>
     *     NOTE: see {@link CuratorFrameworkFactory.Builder#connectionHandlingPolicy(ConnectionHandlingPolicy)} for an important note about a
     *     change in meaning to LOST since 3.0.0
     * </p>
     */
    LOST {
        public boolean isConnected() {
            return false;
        }
    },
    /**
     * The connection has gone into read-only mode. This can only happen if you pass true
     * for {@link CuratorFrameworkFactory.Builder#canBeReadOnly()}. See the ZooKeeper doc
     * regarding read only connections:
     * <a href="http://wiki.apache.org/hadoop/ZooKeeper/GSoCReadOnlyMode">http://wiki.apache.org/hadoop/ZooKeeper/GSoCReadOnlyMode</a>.
     * The connection will remain in read only mode until another state change is sent.
     */
    READ_ONLY {
        public boolean isConnected() {
            return true;
        }
    };

    /**
     * 返回是否连接上了zk服务
     * 已连接的状态：CONNECTED、RECONNECTED、READ_ONLY
     * 未连接的状态：SUSPENDED、LOST
     *
     * @return True if connected, false otherwise
     */
    public abstract boolean isConnected();
}
