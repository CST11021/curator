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

/**
 * 重试策略，最多重试次数
 */
public class RetryNTimes extends SleepingRetry {

    private final int sleepMsBetweenRetries;

    public RetryNTimes(int n, int sleepMsBetweenRetries) {
        super(n);
        this.sleepMsBetweenRetries = sleepMsBetweenRetries;
    }

    @Override
    protected long getSleepTimeMs(int retryCount, long elapsedTimeMs) {
        return sleepMsBetweenRetries;
    }
}
