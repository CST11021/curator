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
package org.apache.curator.ensemble.exhibitor;

public interface ExhibitorRestClient {

    /**
     * 连接到给定的参展商并返回原始结果
     *
     * @param hostname  要连接的机器
     * @param port      要连接的机器端口
     * @param uriPath   资源URI路径
     * @param mimeType  Accept mime type
     * @return raw result
     * @throws Exception errors
     */
    public String getRaw(String hostname, int port, String uriPath, String mimeType) throws Exception;

}
