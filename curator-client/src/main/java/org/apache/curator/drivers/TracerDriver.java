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
package org.apache.curator.drivers;

import java.util.concurrent.TimeUnit;

/**
 * 计时方法和记录计数器的机制
 */
public interface TracerDriver {

    /**
     * 记录给定的跟踪事件(打印一条相应的日志)
     *
     * @param name  事件名称
     * @param time  时间
     * @param unit  对应time的单位
     */
    public void addTrace(String name, long time, TimeUnit unit);

    /**
     * 打印一条相应的日志，记录日志
     *
     * @param name      计数器的名称
     * @param increment 当前计数器累计数量
     */
    public void addCount(String name, int increment);
}
