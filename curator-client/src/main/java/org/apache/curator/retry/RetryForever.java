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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * 始终允许重试的{@link RetryPolicy}实现
 */
public class RetryForever implements RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryForever.class);

    /** 重试的间隔时间 */
    private final int retryIntervalMs;

    public RetryForever(int retryIntervalMs) {
        checkArgument(retryIntervalMs > 0);
        this.retryIntervalMs = retryIntervalMs;
    }

    /**
     * 该方法返回true后开始进行重试
     *
     * @param retryCount    到目前为止，重试的次数（第一次为0）
     * @param elapsedTimeMs 自尝试执行操作以来经过的时间（以毫秒为单位）
     * @param sleeper       用它来sleeper-不要调用Thread.sleep
     * @return
     */
    @Override
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
        try {
            sleeper.sleepFor(retryIntervalMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Error occurred while sleeping", e);
            return false;
        }
        return true;
    }
}
