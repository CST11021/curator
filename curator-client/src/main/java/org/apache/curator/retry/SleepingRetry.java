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
package org.apache.curator.retry;

import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;

import java.util.concurrent.TimeUnit;

/**
 * 限制重试次数的策略
 */
abstract class SleepingRetry implements RetryPolicy {

    /** 重试的最大次数 */
    private final int n;

    protected SleepingRetry(int n) {
        this.n = n;
    }


    public int getN() {
        return n;
    }

    /**
     * 当操作由于某种原因而失败时调用。该方法应返回true以进行另一次尝试。
     *
     *
     * @param retryCount    到目前为止，重试的次数（第一次为0）
     * @param elapsedTimeMs 自尝试执行操作以来经过的时间（以毫秒为单位）
     * @param sleeper       用它来sleeper-不要调用Thread.sleep
     * @return true/false
     */
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
        if (retryCount < n) {
            try {
                sleeper.sleepFor(getSleepTimeMs(retryCount, elapsedTimeMs), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 获每次重试间隔的休眠时间
     *
     * @param retryCount        当前重试的次数
     * @param elapsedTimeMs     距离最开始尝试的时间
     * @return
     */
    protected abstract long getSleepTimeMs(int retryCount, long elapsedTimeMs);
}
