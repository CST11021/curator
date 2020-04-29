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

import com.google.common.base.Preconditions;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.utils.ThreadUtils;

import java.util.concurrent.Callable;

/**
 * Curator's standard connection handling since 3.0.0
 *
 * @since 3.0.0
 */
public class StandardConnectionHandlingPolicy implements ConnectionHandlingPolicy {

    private final int expirationPercent;

    public StandardConnectionHandlingPolicy() {
        this(100);
    }
    public StandardConnectionHandlingPolicy(int expirationPercent) {
        Preconditions.checkArgument((expirationPercent > 0) && (expirationPercent <= 100), "expirationPercent must be > 0 and <= 100");
        this.expirationPercent = expirationPercent;
    }

    @Override
    public int getSimulatedSessionExpirationPercent() {
        return expirationPercent;
    }

    @Override
    public <T> T callWithRetry(CuratorZookeeperClient client, Callable<T> proc) throws Exception {
        // 连接zk服务器，知道连接上或者超时为止
        client.internalBlockUntilConnectedOrTimedOut();

        T result = null;
        ThreadLocalRetryLoop threadLocalRetryLoop = new ThreadLocalRetryLoop();
        RetryLoop retryLoop = threadLocalRetryLoop.getRetryLoop(client::newRetryLoop);
        try {
            while (retryLoop.shouldContinue()) {
                try {
                    result = proc.call();
                    retryLoop.markComplete();
                } catch (Exception e) {
                    ThreadUtils.checkInterrupted(e);
                    retryLoop.takeException(e);
                }
            }
        } finally {
            threadLocalRetryLoop.release();
        }

        return result;
    }

    /**
     * 检查超时，注意：仅在尝试访问ZooKeeper实例且连接未完成时才调用此方法。
     *
     * @param hasNewConnectionString 该Callable调用以检查是否有新的连接字符串。重要提示：调用此状态后，内部状态会清除，因此如果返回非null，则必须处理新的连接字符串
     * @param connectionStartMs the epoch/ms time that the connection was first initiated
     * @param sessionTimeoutMs the configured/negotiated session timeout in milliseconds
     * @param connectionTimeoutMs the configured connection timeout in milliseconds
     * @return result
     * @throws Exception errors
     */
    @Override
    public CheckTimeoutsResult checkTimeouts(Callable<String> hasNewConnectionString, long connectionStartMs, int sessionTimeoutMs, int connectionTimeoutMs) throws Exception {
        if (hasNewConnectionString.call() != null) {
            return CheckTimeoutsResult.NEW_CONNECTION_STRING;
        }

        return CheckTimeoutsResult.NOP;
    }
}
