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
package org.apache.curator.framework.imps;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.PathUtils;
import org.apache.curator.utils.ThreadUtils;
import org.apache.curator.utils.ZKPaths;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于给节点路径加上或去掉命名空间
 */
class NamespaceImpl {

    /** zk客户端 */
    private final CuratorFrameworkImpl client;
    /** 隔离命名空间 */
    private final String namespace;
    /** 用于标记命名空间是否为空 */
    private final AtomicBoolean ensurePathNeeded;

    NamespaceImpl(CuratorFrameworkImpl client, String namespace) {
        if (namespace != null) {
            try {
                PathUtils.validatePath("/" + namespace);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid namespace: " + namespace + ", " + e.getMessage());
            }
        }

        this.client = client;
        this.namespace = namespace;
        ensurePathNeeded = new AtomicBoolean(namespace != null);
    }

    /**
     * 获取命名空间
     *
     * @return
     */
    String getNamespace() {
        return namespace;
    }

    /**
     * 去掉命名空间前缀
     *
     * @param path
     * @return
     */
    String unfixForNamespace(String path) {
        if ((namespace != null) && (path != null)) {
            // 返回例如：/namespace
            String namespacePath = ZKPaths.makePath(namespace, null);
            if (!namespacePath.equals("/") && path.startsWith(namespacePath)) {
                path = (path.length() > namespacePath.length()) ? path.substring(namespacePath.length()) : "/";
            }
        }
        return path;
    }

    /**
     * 加上命令空间前缀
     *
     * @param path
     * @param isSequential
     * @return
     */
    String fixForNamespace(String path, boolean isSequential) {
        if (ensurePathNeeded.get()) {
            try {
                final CuratorZookeeperClient zookeeperClient = client.getZookeeperClient();
                RetryLoop.callWithRetry(zookeeperClient, new Callable<Object>() {
                                    @Override
                                    public Object call() throws Exception {
                                        // 确保节点路径的下所有节点都已经创建
                                        ZKPaths.mkdirs(zookeeperClient.getZooKeeper(), ZKPaths.makePath("/", namespace), true, client.getAclProvider(), true);
                                        return null;
                                    }
                                });
                ensurePathNeeded.set(false);
            } catch (Exception e) {
                ThreadUtils.checkInterrupted(e);
                client.logError("Ensure path threw exception", e);
            }
        }

        return ZKPaths.fixForNamespace(namespace, path, isSequential);
    }

    /**
     * 返回一个EnsurePath，该类用于确保该节点路径下的所有节点都已经创建
     *
     * @param path
     * @return
     */
    EnsurePath newNamespaceAwareEnsurePath(String path) {
        return new EnsurePath(fixForNamespace(path, false), client.getAclProvider());
    }

}
