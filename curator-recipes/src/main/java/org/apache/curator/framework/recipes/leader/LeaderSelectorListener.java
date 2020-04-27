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
package org.apache.curator.framework.recipes.leader;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionStateListener;

/**
 * Notification for leadership
 *
 * @see LeaderSelector
 */
public interface LeaderSelectorListener extends ConnectionStateListener {

    /**
     * 在您的实例被授予领导权时会调用该方法, 该方法执行完成后，会放弃成为Leader
     *
     * @param client the client
     * @throws Exception any errors
     */
    public void takeLeadership(CuratorFramework client) throws Exception;
}
