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

import com.google.common.annotations.VisibleForTesting;

/**
 * 重试策略，可重试一定次数，并增加重试之间的睡眠时间（最多达到最大限制）
 */
public class BoundedExponentialBackoffRetry extends ExponentialBackoffRetry {

    /** 最大时间限制，超过改时间还未重试成功，则放弃重试 */
    private final int maxSleepTimeMs;

    /**
     * @param baseSleepTimeMs   重试之间等待的初始时间
     * @param maxSleepTimeMs    重试之间等待的最长时间
     * @param maxRetries        重试的最大次数
     */
    public BoundedExponentialBackoffRetry(int baseSleepTimeMs, int maxSleepTimeMs, int maxRetries) {
        super(baseSleepTimeMs, maxRetries);
        this.maxSleepTimeMs = maxSleepTimeMs;
    }

    @VisibleForTesting
    public int getMaxSleepTimeMs() {
        return maxSleepTimeMs;
    }

    /**
     * 获每次重试间隔的休眠时间，比如：第一次间隔1s，第二次间隔2s，第三次间隔4s
     *
     * @param retryCount        当前重试的次数
     * @param elapsedTimeMs     距离最开始尝试的时间
     * @return
     */
    @Override
    protected long getSleepTimeMs(int retryCount, long elapsedTimeMs) {
        return Math.min(maxSleepTimeMs, super.getSleepTimeMs(retryCount, elapsedTimeMs));
    }
}