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
package org.apache.curator.framework.api.transaction;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.ZooKeeper;

/**
 * 事务/原子操作。有关ZooKeeper事务的详细信息，请参见{@link ZooKeeper#multi(Iterable)}。
 *
 * 该接口的一般形式为：
 *
 *         curator.inTransaction().operation().arguments().forPath(...).
 *             and().more-operations.
 *             and().commit();
 *
 *
 * 这是一个在事务中创建两个节点的示例
 *         curator.inTransaction().
 *             create().forPath("/path-one", path-one-data).
 *             and().create().forPath("/path-two", path-two-data).
 *             and().commit();
 *
 * 重要提示：在调用{@link CuratorTransactionFinal#commit()}之前，不会提交操作。
 *
 * @deprecated Use {@link CuratorFramework#transaction()}
 */
public interface CuratorTransaction {

    /**
     * Start a create builder in the transaction
     *
     * @return builder object
     */
    public TransactionCreateBuilder<CuratorTransactionBridge> create();

    /**
     * Start a delete builder in the transaction
     *
     * @return builder object
     */
    public TransactionDeleteBuilder<CuratorTransactionBridge> delete();

    /**
     * Start a setData builder in the transaction
     *
     * @return builder object
     */
    public TransactionSetDataBuilder<CuratorTransactionBridge> setData();

    /**
     * Start a check builder in the transaction
     *
     * @return builder object
     */
    public TransactionCheckBuilder<CuratorTransactionBridge> check();

}
