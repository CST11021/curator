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

import org.apache.curator.drivers.EventTrace;
import org.apache.curator.drivers.TracerDriver;
import org.apache.curator.utils.DebugUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 循环重试的实现
 */
class RetryLoopImpl extends RetryLoop {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 表示本次重试是否成功 */
    private boolean isDone = false;
    /** 用于统计重试的次数 */
    private int retryCount = 0;
    /** 表示开始时间 */
    private final long startTimeMs = System.currentTimeMillis();
    /** 重试策略，比如每次间隔多次时间重试，最多重试多少次，最大重试时间间隔等 */
    private final RetryPolicy retryPolicy;
    /** 用于日志追踪 */
    private final AtomicReference<TracerDriver> tracer;
    /** 用于sleep给定的时间 */
    private static final RetrySleeper sleeper = (time, unit) -> unit.sleep(time);

    RetryLoopImpl(RetryPolicy retryPolicy, AtomicReference<TracerDriver> tracer) {
        this.retryPolicy = retryPolicy;
        this.tracer = tracer;
    }

    /**
     * 获取让线程休眠的实现
     *
     * @return
     */
    static RetrySleeper getRetrySleeper() {
        return sleeper;
    }

    /**
     * 是否继续重试
     *
     * @return
     */
    @Override
    public boolean shouldContinue() {
        return !isDone;
    }

    /**
     * 标记本次重试是否成功
     */
    @Override
    public void markComplete() {
        isDone = true;
    }

    /**
     * 在此处传递任何捕获的异常
     *
     * @param exception the exception
     * @throws Exception if not retry-able or the retry policy returned negative
     */
    @Override
    public void takeException(Exception exception) throws Exception {
        boolean rethrow = true;
        if (RetryLoop.isRetryException(exception)) {
            if (!Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES)) {
                log.debug("Retry-able exception received", exception);
            }

            if (retryPolicy.allowRetry(retryCount++, System.currentTimeMillis() - startTimeMs, sleeper)) {
                new EventTrace("retries-allowed", tracer.get()).commit();
                if (!Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES)) {
                    log.debug("Retrying operation");
                }
                rethrow = false;
            } else {
                new EventTrace("retries-disallowed", tracer.get()).commit();
                if (!Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES)) {
                    log.debug("Retry policy not allowing retry");
                }
            }
        }

        if (rethrow) {
            throw exception;
        }
    }
}
