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
package cache;

import framework.CreateClientExamples;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.test.TestingServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 使用zk路径下所有节点数据作为缓存。这个类会监控ZK的一个路径，路径下所有的节点变动，数据变动都会被响应。 还可以通过注册自定义监听器来更细节的控制这些数据变动操作。
 */
public class TreeCacheExample {

    public static void main(String[] args) throws Exception {

        CuratorFramework client = CreateClientExamples.createSimple("127.0.0.1:2181");

        client.getUnhandledErrorListenable().addListener((message, e) -> {
            System.err.println("error=" + message);
            e.printStackTrace();
        });

        client.getConnectionStateListenable().addListener((c, newState) -> {
            System.out.println("state=" + newState);
        });

        client.start();



        TreeCache cache = TreeCache.newBuilder(client, "/").setCacheData(false).build();
        cache.getListenable().addListener((c, event) -> {
            if (event.getData() != null) {
                System.out.println("type=" + event.getType() + " path=" + event.getData().getPath());
            } else {
                System.out.println("type=" + event.getType());
            }
        });
        cache.start();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        in.readLine();
    }
}
