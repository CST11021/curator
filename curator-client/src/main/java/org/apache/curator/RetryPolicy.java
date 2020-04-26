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

/**
 * Abstracts the policy to use when retrying connections
 */
public interface RetryPolicy {
    /**
     * 当操作由于某种原因而失败时调用。该方法应返回true以进行另一次尝试。
     *
     *
     * @param retryCount    到目前为止，重试的次数（第一次为0）
     * @param elapsedTimeMs 自尝试执行操作以来经过的时间（以毫秒为单位）
     * @param sleeper       用它来sleeper-不要调用Thread.sleep
     * @return true/false
     */
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper);
}
