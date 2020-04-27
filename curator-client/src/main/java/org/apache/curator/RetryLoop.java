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

import org.apache.zookeeper.KeeperException;

import java.util.concurrent.Callable;

/**
 * 在Zookeeper上执行可防止断开连接和“可恢复”错误的操作的机制。
 *
 * 如果操作期间发生异常，则RetryLoop将对其进行处理，并检查当前的重试策略，然后尝试重新连接或重新引发该异常。
 *
 * Canonical usage:<br>
 * <pre>
 * RetryLoop retryLoop = client.newRetryLoop();
 * while ( retryLoop.shouldContinue() )
 * {
 *     try
 *     {
 *         // do your work
 *         ZooKeeper      zk = client.getZooKeeper();    // it's important to re-get the ZK instance in case there was an error and the instance was re-created
 *
 *         retryLoop.markComplete();
 *     }
 *     catch ( Exception e )
 *     {
 *         retryLoop.takeException(e);
 *     }
 * }
 * </pre>
 *
 * <p>
 *     Note: this an {@code abstract class} instead of an {@code interface} for historical reasons. It was originally a class
 *     and if it becomes an interface we risk {@link java.lang.IncompatibleClassChangeError}s with clients.
 * </p>
 */
public abstract class RetryLoop {

    /**
     * 获取重试时，线程休眠的实现
     *
     * @return sleeper
     */
    public static RetrySleeper getDefaultRetrySleeper() {
        return RetryLoopImpl.getRetrySleeper();
    }

    /**
     * 便利实用程序：创建重试循环，调用给定proc并在需要时重试
     *
     * @param client Zookeeper
     * @param proc procedure to call with retry
     * @param <T> return type
     * @return procedure result
     * @throws Exception any non-retriable errors
     */
    public static <T> T callWithRetry(CuratorZookeeperClient client, Callable<T> proc) throws Exception {
        return client.getConnectionHandlingPolicy().callWithRetry(client, proc);
    }

    /**
     * 是否继续重试
     *
     * @return true/false
     */
    public abstract boolean shouldContinue();

    /**
     * 标记本次重试是否成功
     */
    public abstract void markComplete();

    /**
     * 实用程序-如果给定的Zookeeper结果代码可重试，则返回true
     *
     * @param rc result code
     * @return true/false
     */
    public static boolean shouldRetry(int rc) {
        return (rc == KeeperException.Code.CONNECTIONLOSS.intValue()) ||
                (rc == KeeperException.Code.OPERATIONTIMEOUT.intValue()) ||
                (rc == KeeperException.Code.SESSIONMOVED.intValue()) ||
                (rc == KeeperException.Code.SESSIONEXPIRED.intValue()) ||
                (rc == -13); // KeeperException.Code.NEWCONFIGNOQUORUM.intValue()) - using hard coded value for ZK 3.4.x compatibility
    }

    /**
     * 实用程序-如果给定的异常可重试，则返回true
     *
     * @param exception exception to check
     * @return true/false
     */
    public static boolean isRetryException(Throwable exception) {
        if (exception instanceof KeeperException) {
            KeeperException keeperException = (KeeperException) exception;
            return shouldRetry(keeperException.code().intValue());
        }
        return false;
    }

    /**
     * 在此处传递任何捕获的异常
     *
     * @param exception the exception
     * @throws Exception if not retry-able or the retry policy returned negative
     */
    public abstract void takeException(Exception exception) throws Exception;
}
