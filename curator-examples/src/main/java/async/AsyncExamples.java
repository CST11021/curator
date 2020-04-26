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
package async;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.curator.x.async.AsyncEventException;
import org.apache.curator.x.async.WatchMode;
import org.apache.zookeeper.WatchedEvent;

import java.util.concurrent.CompletionStage;

/**
 * Examples using the asynchronous DSL
 */
public class AsyncExamples {

    public static AsyncCuratorFramework wrap(CuratorFramework client) {
        // 包装一个CuratorFramework实例，以便可以异步使用它。一次执行一次，然后重新使用返回的AsyncCuratorFramework实例
        return AsyncCuratorFramework.wrap(client);
    }

    public static void create(CuratorFramework client, String path, byte[] payload) {

        // 通常，您会在应用程序的早期包装好并重用实例
        AsyncCuratorFramework async = AsyncCuratorFramework.wrap(client);

        // 使用给定的负载异步在给定的路径上创建一个节点
        async.create().forPath(path, payload).whenComplete((name, exception) -> {
            if (exception != null) {
                // there was a problem
                exception.printStackTrace();
            } else {
                System.out.println("Created node name is: " + name);
            }
        });
    }

    public static void createThenWatch(CuratorFramework client, String path) {

        // 通常，您会在应用程序的早期包装好并重用实例
        AsyncCuratorFramework async = AsyncCuratorFramework.wrap(client);

        // 此示例显示了异步使用监视程序来处理事件触发和连接问题。
        // 如果不需要通知连接问题，请使用createThenWatchSimple()中显示的更简单方法

        // 在具有给定有效负载的给定路径上异步创建一个节点，然后观察创建的节点
        async.create().forPath(path).whenComplete((name, exception) -> {
            if (exception != null) {
                // there was a problem creating the node
                exception.printStackTrace();
            } else {
                handleWatchedStage(async.watched().checkExists().forPath(path).event());
            }
        });
    }

    public static void createThenWatchSimple(CuratorFramework client, String path) {

        // 通常，您会在应用程序的早期包装好并重用实例
        AsyncCuratorFramework async = AsyncCuratorFramework.wrap(client);

        // 在具有给定有效负载的给定路径上异步创建一个节点，然后观察创建的节点
        async.create().forPath(path).whenComplete((name, exception) -> {
            if (exception != null) {
                // there was a problem creating the node
                exception.printStackTrace();
            } else {
                // 因为使用了“ WatchMode.successOnly”，所以仅在EventType是节点事件时才触发监视阶段
                async.with(WatchMode.successOnly).watched().checkExists().forPath(path).event().thenAccept(event -> {
                    System.out.println(event.getType());
                    System.out.println(event);
                });
            }
        });
    }

    private static void handleWatchedStage(CompletionStage<WatchedEvent> watchedStage) {
        // 观察者的异步处理很复杂，因为观察者可以触发多次，并且CompletionStage不支持此行为

        // thenAccept()处理正常的观察者触发。
        watchedStage.thenAccept(event -> {
            System.out.println(event.getType());
            System.out.println(event);
            // etc.
        });

        // 如果存在连接问题，则异常调用，在这种情况下，观察者触发以发出连接问题信号。
        // 必须调用“reset()”来重置监视的舞台
        watchedStage.exceptionally(exception -> {
            AsyncEventException asyncEx = (AsyncEventException) exception;
            asyncEx.printStackTrace();    // handle the error as needed
            handleWatchedStage(asyncEx.reset());
            return null;
        });
    }
}
