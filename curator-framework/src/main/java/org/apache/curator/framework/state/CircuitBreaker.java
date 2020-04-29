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

import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.utils.ThreadUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class CircuitBreaker {

    /** 重试策略：根据当前的重试时间和重试次，决定是否再继续重试 */
    private final RetryPolicy retryPolicy;
    private final ScheduledExecutorService service;

    /** 该实例是否开启 */
    private boolean isOpen = false;
    /** 重试的次数 */
    private int retryCount = 0;
    /** 任务开始的时间 */
    private long startNanos = 0;

    private CircuitBreaker(RetryPolicy retryPolicy, ScheduledExecutorService service) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy cannot be null");
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    static CircuitBreaker build(RetryPolicy retryPolicy) {
        return new CircuitBreaker(retryPolicy, ThreadUtils.newSingleThreadScheduledExecutor("CircuitBreakingConnectionStateListener"));
    }
    static CircuitBreaker build(RetryPolicy retryPolicy, ScheduledExecutorService service) {
        return new CircuitBreaker(retryPolicy, service);
    }

    // 重要提示-以下所有方法，必须通过同步保护

    boolean isOpen() {
        return isOpen;
    }

    int getRetryCount() {
        return retryCount;
    }

    /**
     * 尝试开启，返回是否开启成功
     *
     * @param completion    尝试执行的任务
     * @return
     */
    boolean tryToOpen(Runnable completion) {

        // 如果已经是开启状态，则返回false
        if (isOpen) {
            return false;
        }

        // 标记为已开启，初始化retryCount、startNanos
        isOpen = true;
        retryCount = 0;
        startNanos = System.nanoTime();

        // 尝试执行某个任务
        if (tryToRetry(completion)) {
            return true;
        }
        close();
        return false;
    }

    /**
     * 尝试执行任务
     *
     * @param completion    重试执行的具体任务
     * @return
     */
    boolean tryToRetry(Runnable completion) {
        if (!isOpen) {
            return false;
        }

        long[] sleepTimeNanos = new long[]{0L};
        RetrySleeper retrySleeper = (time, unit) -> sleepTimeNanos[0] = unit.toNanos(time);
        Duration elapsedTime = Duration.ofNanos(System.nanoTime() - startNanos);

        // 根据当前的重试时间和重试次，决定是否再继续重试
        if (retryPolicy.allowRetry(retryCount, elapsedTime.toMillis(), retrySleeper)) {
            ++retryCount;
            service.schedule(completion, sleepTimeNanos[0], TimeUnit.NANOSECONDS);
            return true;
        }
        return false;
    }

    boolean close() {
        boolean wasOpen = isOpen;
        retryCount = 0;
        isOpen = false;
        startNanos = 0;
        return wasOpen;
    }


}
