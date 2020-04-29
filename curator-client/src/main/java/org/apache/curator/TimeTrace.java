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

import org.apache.curator.drivers.TracerDriver;

import java.util.concurrent.TimeUnit;

/**
 * 计时方法或部分代码的实用程序
 */
public class TimeTrace {

    /** 计时器名称 */
    private final String name;
    private final TracerDriver driver;
    /** 实例创建的时间 */
    private final long startTimeNanos = System.nanoTime();

    /**
     * 创建并启动计时器
     *
     * @param name name of the event
     * @param driver driver
     */
    public TimeTrace(String name, TracerDriver driver) {
        this.name = name;
        this.driver = driver;
    }

    /**
     * 记录经过的时间
     */
    public void commit() {
        long elapsed = System.nanoTime() - startTimeNanos;
        driver.addTrace(name, elapsed, TimeUnit.NANOSECONDS);
    }
}
