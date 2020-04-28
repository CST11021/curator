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
package org.apache.curator.framework.api;

import org.apache.curator.framework.CuratorFrameworkFactory;

public interface PathAndBytesable<T> {

    /**
     * 使用给定的路径和数据提交当前的构建操作
     *
     * @param path the path
     * @param data the data
     * @return operation result if any
     * @throws Exception errors
     */
    public T forPath(String path, byte[] data) throws Exception;

    /**
     * 使用给定的路径和客户端的默认数据（通常为字节[0]，除非通过{@link CuratorFrameworkFactory.Builder#defaultData(byte[])}).
     *
     * @param path the path
     * @return operation result if any
     * @throws Exception errors
     */
    public T forPath(String path) throws Exception;

}
