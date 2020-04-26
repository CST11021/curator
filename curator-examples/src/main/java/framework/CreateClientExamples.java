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
package framework;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class CreateClientExamples {

    /**
     * 创建一个zk客户端
     *
     * @param connectionString  zk服务IP和端口
     * @return
     */
    public static CuratorFramework createSimple(String connectionString) {

        // 这些是ExponentialBackoffRetry的合理参数。
        // 第一次重试将等待1秒-第二次将等待长达2秒-第三次将等待长达4秒。
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);

        // 获取CuratorFramework实例的最简单方法。这将使用默认值。
        // 唯一需要的参数是连接字符串和重试策略
        return CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
    }

    /**
     * 创建一个zk客户端
     *
     * @param connectionString      zk服务IP和端口
     * @param retryPolicy           重试策略
     * @param connectionTimeoutMs   连接的超时时间
     * @param sessionTimeoutMs      session超时时间
     * @return
     */
    public static CuratorFramework createWithOptions(String connectionString, RetryPolicy retryPolicy, int connectionTimeoutMs, int sessionTimeoutMs) {
        // 使用CuratorFrameworkFactory.builder（）可以对创建选项进行精细控制。
        // 查看CuratorFrameworkFactory.Builder javadoc详细信息
        return CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .retryPolicy(retryPolicy)
                .connectionTimeoutMs(connectionTimeoutMs)
                .sessionTimeoutMs(sessionTimeoutMs)
                // etc. etc.
                .build();
    }

    public static void main(String[] args) {
        CreateClientExamples.createSimple("localhost:4181");
    }
}
