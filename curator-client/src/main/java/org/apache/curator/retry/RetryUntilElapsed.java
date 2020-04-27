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

import org.apache.curator.RetrySleeper;

/**
 * 重试策略，重试直到经过指定的时间
 */
public class RetryUntilElapsed extends SleepingRetry {

    /** 重试的最大间隔时间，超过该时间，则不再尝试重试 */
    private final int maxElapsedTimeMs;
    /** 每次间隔重试的线程休眠时间 */
    private final int sleepMsBetweenRetries;

    public RetryUntilElapsed(int maxElapsedTimeMs, int sleepMsBetweenRetries) {
        super(Integer.MAX_VALUE);
        this.maxElapsedTimeMs = maxElapsedTimeMs;
        this.sleepMsBetweenRetries = sleepMsBetweenRetries;
    }

    /**
     * 当次数小于N，并且重试间隔时间还未超过最大限制时间时，返回true
     *
     * @param retryCount    到目前为止，重试的次数（第一次为0）
     * @param elapsedTimeMs 自尝试执行操作以来经过的时间（以毫秒为单位）
     * @param sleeper       用它来sleeper-不要调用Thread.sleep
     * @return
     */
    @Override
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
        return super.allowRetry(retryCount, elapsedTimeMs, sleeper) && (elapsedTimeMs < maxElapsedTimeMs);
    }

    /**
     * 获取休眠时间
     *
     * @param retryCount        当前重试的次数
     * @param elapsedTimeMs     距离最开始尝试的时间
     * @return
     */
    @Override
    protected long getSleepTimeMs(int retryCount, long elapsedTimeMs) {
        return sleepMsBetweenRetries;
    }
}

