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
package leader;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个leader选举的示例：请注意，{@link LeaderSelectorListenerAdapter}具有针对连接状态问题的建议处理方法
 */
public class ExampleClient extends LeaderSelectorListenerAdapter implements Closeable {

    /** 实例名称 */
    private final String name;
    /** 用于选举leader */
    private final LeaderSelector leaderSelector;
    /** 用于统计leader的选举次数 */
    private final AtomicInteger leaderCount = new AtomicInteger();

    public ExampleClient(CuratorFramework client, String path, String name) {
        this.name = name;
        // 使用给定的管理路径创建领导者选择器给定领导者选择中的所有参与者都必须使用相同的路径ExampleClient这里也是LeaderSelectorListener，但这不是必需的
        leaderSelector = new LeaderSelector(client, path, this);
        // 在大多数情况下，当实例放弃领导权时，您将希望其实例重新排队
        leaderSelector.autoRequeue();
    }

    /**
     * 开始领导者选举
     *
     * @throws IOException
     */
    public void start() throws IOException {
        // 在启动领导者选择器之前，不会开始对此实例的选择，因此在后台完成领导者选择，因此对leaderSelector.start() 的调用将立即返回
        leaderSelector.start();
    }

    @Override
    public void close() throws IOException {
        leaderSelector.close();
    }

    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        // 我们现在是领导者。在我们要放弃领导之前，这种方法不应该返回

        final int waitSeconds = (int) (5 * Math.random()) + 1;

        System.out.println(name + " is now the leader. Waiting " + waitSeconds + " seconds...");
        System.out.println(name + " has been leader " + leaderCount.getAndIncrement() + " time(s) before.");
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(waitSeconds));
        } catch (InterruptedException e) {
            System.err.println(name + " was interrupted.");
            Thread.currentThread().interrupt();
        } finally {
            System.out.println(name + " 放弃Leader\n");
        }
    }

}
