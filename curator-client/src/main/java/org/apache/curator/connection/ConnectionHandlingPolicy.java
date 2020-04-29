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
package org.apache.curator.connection;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;

import java.util.concurrent.Callable;

/**
 * 提取连接处理，以便Curator可以模拟3.0.0之前的旧处理，并更新为较新的处理。
 */
public interface ConnectionHandlingPolicy {

    enum CheckTimeoutsResult {
        /** 表示连接正常 */
        NOP,
        /** 处理新的连接字符串，zk集群的连接字符串可能是动态的 */
        NEW_CONNECTION_STRING,
        /** reset/recreate the internal ZooKeeper connection */
        RESET_CONNECTION,
        /** handle a connection timeout */
        CONNECTION_TIMEOUT,
        /** handle a session timeout */
        SESSION_TIMEOUT
    }

    /**
     * 在3.0.0之前的版本中，Curator除了ZooKeeper本身提供的功能外，没有尝试管理会话到期。
     * 从3.0.0版本开始，Curator可以选择尝试监视超出ZooKeeper提供的会话到期时间。
     * 此方法返回的百分比确定Curator如何以及是否检查会话到期。
     *
     *
     * 如果此方法返回0，则Curator不会对会话到期做任何其他检查。
     *
     *
     * 如果返回正数，则Curator将检查会话是否过期，如下所示：当ZooKeeper发送Disconnect事件时，Curator将启动计时器。
     * 如果在经过的时间超过协商的会话时间乘以会话到期百分比之前未实现重新连接，则Curator将模拟会话到期。
     * 由于时间/网络问题，客户端无法完全准确地匹配服务器的会话超时。因此，需要会话到期百分比。
     *
     * @return 从0到100的百分比（0表示不进行额外的会话检查）
     */
    int getSimulatedSessionExpirationPercent();

    /**
     * 由{@link RetryLoop#callWithRetry(CuratorZookeeperClient, Callable)}调用以执行重试工作
     *
     * @param client client
     * @param proc the procedure to retry
     * @return result
     * @throws Exception errors
     */
    <T> T callWithRetry(CuratorZookeeperClient client, Callable<T> proc) throws Exception;

    /**
     * 检查超时，注意：仅在尝试访问ZooKeeper实例且连接未完成时才调用此方法。
     *
     * @param getNewConnectionString proc to call to check if there is a new connection string. Important: the internal state is cleared after this is called so you MUST handle the new connection string if non null is returned
     * @param connectionStartMs the epoch/ms time that the connection was first initiated
     * @param sessionTimeoutMs the configured/negotiated session timeout in milliseconds
     * @param connectionTimeoutMs the configured connection timeout in milliseconds
     * @return result
     * @throws Exception errors
     */
    CheckTimeoutsResult checkTimeouts(Callable<String> getNewConnectionString, long connectionStartMs, int sessionTimeoutMs, int connectionTimeoutMs) throws Exception;
}
