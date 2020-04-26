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
package org.apache.curator.framework.recipes.locks;

import java.util.concurrent.TimeUnit;

/**
 * 注意：根据其实现，如果当前线程不拥有该锁，{@link #release()}可能会引发异常
 */
public interface InterProcessLock {

    /**
     * 获取互斥锁-阻止，直到可用为止。每个获取请求必须通过调用{@link #release()}来平衡
     *
     * @throws Exception ZK errors, connection interruptions
     */
    public void acquire() throws Exception;

    /**
     * 获取互斥锁-阻止，直到可用或给定时间到期为止。必须通过调用{@link #release()}来平衡每次获取true的获取请求
     *
     * @param time time to wait
     * @param unit time unit
     * @return true if the mutex was acquired, false if not
     * @throws Exception ZK errors, connection interruptions
     */
    public boolean acquire(long time, TimeUnit unit) throws Exception;

    /**
     * 执行一次互斥释放。
     *
     * @throws Exception ZK errors, interruptions
     */
    public void release() throws Exception;

    /**
     * 如果此JVM中的线程获取了互斥锁，则返回true
     *
     * @return true/false
     */
    boolean isAcquiredInThisProcess();
}
