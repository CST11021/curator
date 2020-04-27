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

import java.util.List;

public interface CuratorMultiTransactionMain {

    /**
     * 将给定的操作作为单个事务提交。通过{@link CuratorFramework#transactionOp()}创建操作实例
     *
     * @param operations operations that make up the transaction.
     * @return result details for foreground operations or <code>null</code> for background operations
     * @throws Exception errors
     */
    List<CuratorTransactionResult> forOperations(CuratorOp... operations) throws Exception;

    /**
     * 将给定的操作作为单个事务提交。通过{@link CuratorFramework#transactionOp()}创建操作实例
     *
     * @param operations operations that make up the transaction.
     * @return result details for foreground operations or <code>null</code> for background operations
     * @throws Exception errors
     */
    List<CuratorTransactionResult> forOperations(List<CuratorOp> operations) throws Exception;

}
