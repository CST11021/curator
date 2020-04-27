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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 重试策略，可重试一定次数，并增加重试之间的睡眠时间，比如：第一次间隔1s，第二次间隔2s，第三次间隔4s
 */
public class ExponentialBackoffRetry extends SleepingRetry {

    private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetry.class);

    private static final int MAX_RETRIES_LIMIT = 29;
    private static final int DEFAULT_MAX_SLEEP_MS = Integer.MAX_VALUE;

    private final Random random = new Random();
    /** 表示第一次sleep的间隔时间 */
    private final int baseSleepTimeMs;
    /** 最大时间限制，超过改时间还未重试成功，则放弃重试 */
    private final int maxSleepMs;

    /**
     * @param baseSleepTimeMs   重试之间等待的初始时间
     * @param maxRetries        重试的最大次数
     * @param maxSleepMs        每次重试的最大睡眠时间（毫秒）
     */
    public ExponentialBackoffRetry(int baseSleepTimeMs, int maxRetries, int maxSleepMs) {
        super(validateMaxRetries(maxRetries));
        this.baseSleepTimeMs = baseSleepTimeMs;
        this.maxSleepMs = maxSleepMs;
    }
    public ExponentialBackoffRetry(int baseSleepTimeMs, int maxRetries) {
        this(baseSleepTimeMs, maxRetries, DEFAULT_MAX_SLEEP_MS);
    }


    @VisibleForTesting
    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs;
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
        // copied from Hadoop's RetryPolicies.java
        long sleepMs = baseSleepTimeMs * Math.max(1, random.nextInt(1 << (retryCount + 1)));
        if (sleepMs > maxSleepMs) {
            log.warn(String.format("Sleep extension too large (%d). Pinning to %d", sleepMs, maxSleepMs));
            sleepMs = maxSleepMs;
        }
        return sleepMs;
    }

    private static int validateMaxRetries(int maxRetries) {
        if (maxRetries > MAX_RETRIES_LIMIT) {
            log.warn(String.format("maxRetries too large (%d). Pinning to %d", maxRetries, MAX_RETRIES_LIMIT));
            maxRetries = MAX_RETRIES_LIMIT;
        }
        return maxRetries;
    }
}
