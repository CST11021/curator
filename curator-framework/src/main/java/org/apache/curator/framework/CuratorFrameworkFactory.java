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

package org.apache.curator.framework;

import com.google.common.collect.ImmutableList;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryPolicy;
import org.apache.curator.connection.ConnectionHandlingPolicy;
import org.apache.curator.connection.StandardConnectionHandlingPolicy;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.CompressionProvider;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.imps.CuratorFrameworkImpl;
import org.apache.curator.framework.imps.CuratorTempFrameworkImpl;
import org.apache.curator.framework.imps.DefaultACLProvider;
import org.apache.curator.framework.imps.GzipCompressionProvider;
import org.apache.curator.framework.schema.SchemaSet;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateErrorPolicy;
import org.apache.curator.framework.state.ConnectionStateListenerManagerFactory;
import org.apache.curator.framework.state.StandardConnectionStateErrorPolicy;
import org.apache.curator.utils.DefaultZookeeperFactory;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.apache.curator.utils.Compatibility.isZK34;

/**
 * 用于创建CuratorFramework实例的工厂类，CuratorFramework表示一个zk客户端
 */
public class CuratorFrameworkFactory {

    /** 默认session超时时间，这里是1分钟 */
    private static final int DEFAULT_SESSION_TIMEOUT_MS = Integer.getInteger("curator-default-session-timeout", 60 * 1000);
    /** zk客户端连接到zk服务的连接超时时间，这里是15秒 */
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = Integer.getInteger("curator-default-connection-timeout", 15 * 1000);
    /** 表示本地IP地址，这里以字节的形式保存 */
    private static final byte[] LOCAL_ADDRESS = getLocalAddress();

    private static final CompressionProvider DEFAULT_COMPRESSION_PROVIDER = new GzipCompressionProvider();
    private static final DefaultZookeeperFactory DEFAULT_ZOOKEEPER_FACTORY = new DefaultZookeeperFactory();
    private static final DefaultACLProvider DEFAULT_ACL_PROVIDER = new DefaultACLProvider();
    /** 如果连接将在 inactiveThresholdMs 毫秒不活动，将会自动关闭，默认闲置3分钟后，连接将关闭。 */
    private static final long DEFAULT_INACTIVE_THRESHOLD_MS = (int) TimeUnit.MINUTES.toMillis(3);
    private static final int DEFAULT_CLOSE_WAIT_MS = (int) TimeUnit.SECONDS.toMillis(1);

    private CuratorFrameworkFactory() {
    }

    /**
     * Return a new builder that builds a CuratorFramework
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建具有默认会话超时和默认连接超时的新客户端
     *
     * @param connectString list of servers to connect to
     * @param retryPolicy   retry policy to use
     * @return client
     */
    public static CuratorFramework newClient(String connectString, RetryPolicy retryPolicy) {
        return newClient(connectString, DEFAULT_SESSION_TIMEOUT_MS, DEFAULT_CONNECTION_TIMEOUT_MS, retryPolicy);
    }
    /**
     * Create a new client
     *
     * @param connectString       list of servers to connect to
     * @param sessionTimeoutMs    session timeout
     * @param connectionTimeoutMs connection timeout
     * @param retryPolicy         retry policy to use
     * @return client
     */
    public static CuratorFramework newClient(String connectString, int sessionTimeoutMs, int connectionTimeoutMs, RetryPolicy retryPolicy) {
        return builder().
                connectString(connectString).
                sessionTimeoutMs(sessionTimeoutMs).
                connectionTimeoutMs(connectionTimeoutMs).
                retryPolicy(retryPolicy).
                build();
    }

    /**
     * 将本地ip转为字节返回
     *
     * @return local address bytes
     */
    public static byte[] getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress().getBytes();
        } catch (UnknownHostException ignore) {
            // ignore
        }
        return new byte[0];
    }

    public static class Builder {
        /** 用于提供ZooKeeper连接字符串等信息 */
        private EnsembleProvider ensembleProvider;
        /** 默认session超时时间，这里是1分钟 */
        private int sessionTimeoutMs = DEFAULT_SESSION_TIMEOUT_MS;
        /** zk客户端连接到zk服务的连接超时时间，这里是15秒 */
        private int connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;
        private int maxCloseWaitMs = DEFAULT_CLOSE_WAIT_MS;
        private RetryPolicy retryPolicy;
        private ThreadFactory threadFactory = null;
        /** 隔离命名空间，对应zk的chroot */
        private String namespace;
        private List<AuthInfo> authInfos = null;
        private byte[] defaultData = LOCAL_ADDRESS;
        private CompressionProvider compressionProvider = DEFAULT_COMPRESSION_PROVIDER;
        private ZookeeperFactory zookeeperFactory = DEFAULT_ZOOKEEPER_FACTORY;
        private ACLProvider aclProvider = DEFAULT_ACL_PROVIDER;
        private boolean canBeReadOnly = false;
        private boolean useContainerParentsIfAvailable = true;
        private ConnectionStateErrorPolicy connectionStateErrorPolicy = new StandardConnectionStateErrorPolicy();
        private ConnectionHandlingPolicy connectionHandlingPolicy = new StandardConnectionHandlingPolicy();
        private SchemaSet schemaSet = SchemaSet.getDefaultSchemaSet();
        private boolean zk34CompatibilityMode = isZK34();
        private int waitForShutdownTimeoutMs = 0;
        private Executor runSafeService = null;
        private ConnectionStateListenerManagerFactory connectionStateListenerManagerFactory = ConnectionStateListenerManagerFactory.standard;


        private Builder() {
        }


        /**
         * 应用当前值并构建一个新的CuratorFramework
         *
         * @return new CuratorFramework
         */
        public CuratorFramework build() {
            return new CuratorFrameworkImpl(this);
        }
        /**
         * 应用当前值并构建一个新的临时CuratorFramework，此外，闲置3分钟后，连接将关闭。
         *
         * @return temp instance
         */
        public CuratorTempFramework buildTemp() {
            return buildTemp(DEFAULT_INACTIVE_THRESHOLD_MS, TimeUnit.MILLISECONDS);
        }
        /**
         * 应用当前值并构建一个新的临时CuratorFramework。
         * 临时CuratorFramework实例旨在通过易发生故障的网络（例如WAN）向ZooKeeper集合发出单个请求。
         * {@link CuratorTempFramework}提供的API是受限制的。
         * 此外，连接将在 inactiveThresholdMs 毫秒不活动后关闭。
         *
         * @param inactiveThreshold 导致连接关闭的非活动毫秒数
         * @param unit              阈值单位
         * @return temp instance
         */
        public CuratorTempFramework buildTemp(long inactiveThreshold, TimeUnit unit) {
            return new CuratorTempFrameworkImpl(this, unit.toMillis(inactiveThreshold));
        }



        /**
         * 添加连接授权，对该方法的后续调用将覆盖先前的调用。
         *
         * @param scheme 方案
         * @param auth   the auth bytes
         * @return this
         */
        public Builder authorization(String scheme, byte[] auth) {
            return authorization(ImmutableList.of(new AuthInfo(scheme, (auth != null) ? Arrays.copyOf(auth, auth.length) : null)));
        }
        /**
         * 添加连接授权。所提供的authInfo被附加到通过向{@link #authorization(String, byte[])}的调用而添加的authInfo上，以实现向后兼容。
         * 对该方法的后续调用将覆盖先前的调用。
         *
         * @param authInfos list of {@link AuthInfo} objects with scheme and auth
         * @return this
         */
        public Builder authorization(List<AuthInfo> authInfos) {
            this.authInfos = ImmutableList.copyOf(authInfos);
            return this;
        }
        /**
         * 设置要连接的服务器列表。重要说明：请使用this或{@link #ensembleProvider(EnsembleProvider)}，但不能两者都使用。
         *
         * @param connectString list of servers to connect to
         * @return this
         */
        public Builder connectString(String connectString) {
            ensembleProvider = new FixedEnsembleProvider(connectString);
            return this;
        }
        /**
         * 设置列表集合提供程序。重要说明：请使用this或{@link #connectString(String)}，但不能两者都使用。
         *
         * @param ensembleProvider the ensemble provider to use
         * @return this
         */
        public Builder ensembleProvider(EnsembleProvider ensembleProvider) {
            this.ensembleProvider = ensembleProvider;
            return this;
        }
        /**
         * 设置使用{@link PathAndBytesable#forPath(String)}时要使用的数据。
         * 这对于调试目的很有用。例如，您可以将其设置为客户端的IP。
         *
         * @param defaultData new default data to use
         * @return this
         */
        public Builder defaultData(byte[] defaultData) {
            this.defaultData = (defaultData != null) ? Arrays.copyOf(defaultData, defaultData.length) : null;
            return this;
        }
        /**
         * 由于ZooKeeper是一个共享空间，因此给定群集的用户应保留在预定义的名称空间内。
         * 如果在此处设置了名称空间，则所有路径都将在名称空间之前
         *
         * @param namespace the namespace
         * @return this
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        /**
         * 设置session的超时时间
         * @param sessionTimeoutMs session timeout
         * @return this
         */
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }
        /**
         * 设置连接的超时时间
         * @param connectionTimeoutMs connection timeout
         * @return this
         */
        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }
        /**
         * @param maxCloseWaitMs time to wait during close to join background threads
         * @return this
         */
        public Builder maxCloseWaitMs(int maxCloseWaitMs) {
            this.maxCloseWaitMs = maxCloseWaitMs;
            return this;
        }
        /**
         * @param retryPolicy retry policy to use
         * @return this
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }
        /**
         * @param threadFactory thread factory used to create Executor Services
         * @return this
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }
        /**
         * @param compressionProvider the compression provider
         * @return this
         */
        public Builder compressionProvider(CompressionProvider compressionProvider) {
            this.compressionProvider = compressionProvider;
            return this;
        }
        /**
         * @param zookeeperFactory the zookeeper factory to use
         * @return this
         */
        public Builder zookeeperFactory(ZookeeperFactory zookeeperFactory) {
            this.zookeeperFactory = zookeeperFactory;
            return this;
        }
        /**
         * @param aclProvider a provider for ACLs
         * @return this
         */
        public Builder aclProvider(ACLProvider aclProvider) {
            this.aclProvider = aclProvider;
            return this;
        }
        /**
         * @param canBeReadOnly if true, allow ZooKeeper client to enter
         *                      read only mode in case of a network partition. See
         *                      {@link ZooKeeper#ZooKeeper(String, int, Watcher, long, byte[], boolean)}
         *                      for details
         * @return this
         */
        public Builder canBeReadOnly(boolean canBeReadOnly) {
            this.canBeReadOnly = canBeReadOnly;
            return this;
        }
        /**
         * By default, Curator uses {@link CreateBuilder#creatingParentContainersIfNeeded()}
         * if the ZK JAR supports {@link CreateMode#CONTAINER}. Call this method to turn off this behavior.
         *
         * @return this
         */
        public Builder dontUseContainerParents() {
            this.useContainerParentsIfAvailable = false;
            return this;
        }
        /**
         * Set the error policy to use. The default is {@link StandardConnectionStateErrorPolicy}
         *
         * @since 3.0.0
         * @param connectionStateErrorPolicy new error policy
         * @return this
         */
        public Builder connectionStateErrorPolicy(ConnectionStateErrorPolicy connectionStateErrorPolicy) {
            this.connectionStateErrorPolicy = connectionStateErrorPolicy;
            return this;
        }
        /**
         * If mode is true, create a ZooKeeper 3.4.x compatible client. IMPORTANT: If the client
         * library used is ZooKeeper 3.4.x <code>zk34CompatibilityMode</code> is enabled by default.
         *
         * @since 3.5.0
         * @param mode true/false
         * @return this
         */
        public Builder zk34CompatibilityMode(boolean mode) {
            this.zk34CompatibilityMode = mode;
            return this;
        }
        /**
         * Set a timeout for {@link CuratorZookeeperClient#close(int)}  }.
         * The default is 0, which means that this feature is disabled.
         *
         * @since 4.0.2
         * @param waitForShutdownTimeoutMs default timeout
         * @return this
         */
        public Builder waitForShutdownTimeoutMs(int waitForShutdownTimeoutMs) {
            this.waitForShutdownTimeoutMs = waitForShutdownTimeoutMs;
            return this;
        }
        /**
         * <p>
         *     Change the connection handling policy. The default policy is {@link StandardConnectionHandlingPolicy}.
         * </p>
         * <p>
         *     <strong>IMPORTANT: </strong> StandardConnectionHandlingPolicy has different behavior than the connection
         *     policy handling prior to version 3.0.0.
         * </p>
         * <p>
         *     Major differences from the older behavior are:
         * </p>
         * <ul>
         *     <li>
         *         Session/connection timeouts are no longer managed by the low-level client. They are managed
         *         by the CuratorFramework instance. There should be no noticeable differences.
         *     </li>
         *     <li>
         *         Prior to 3.0.0, each iteration of the retry policy would allow the connection timeout to elapse
         *         if the connection hadn't yet succeeded. This meant that the true connection timeout was the configured
         *         value times the maximum retries in the retry policy. This longstanding issue has been address.
         *         Now, the connection timeout can elapse only once for a single API call.
         *     </li>
         *     <li>
         *         <strong>MOST IMPORTANTLY!</strong> Prior to 3.0.0, {@link ConnectionState#LOST} did not imply
         *         a lost session (much to the confusion of users). Now,
         *         Curator will set the LOST state only when it believes that the ZooKeeper session
         *         has expired. ZooKeeper connections have a session. When the session expires, clients must take appropriate
         *         action. In Curator, this is complicated by the fact that Curator internally manages the ZooKeeper
         *         connection. Now, Curator will set the LOST state when any of the following occurs:
         *         a) ZooKeeper returns a {@link Watcher.Event.KeeperState#Expired} or {@link KeeperException.Code#SESSIONEXPIRED};
         *         b) Curator closes the internally managed ZooKeeper instance; c) The session timeout
         *         elapses during a network partition.
         *     </li>
         * </ul>
         *
         * @param connectionHandlingPolicy the policy
         * @return this
         * @since 3.0.0
         */
        public Builder connectionHandlingPolicy(ConnectionHandlingPolicy connectionHandlingPolicy) {
            this.connectionHandlingPolicy = connectionHandlingPolicy;
            return this;
        }
        /**
         * Add an enforced schema set
         *
         * @param schemaSet the schema set
         * @return this
         * @since 3.2.0
         */
        public Builder schemaSet(SchemaSet schemaSet) {
            this.schemaSet = schemaSet;
            return this;
        }
        /**
         * Curator (and user) recipes will use this executor to call notifyAll
         * and other blocking calls that might normally block ZooKeeper's event thread.
         * By default, an executor is allocated internally using the provided (or default)
         * {@link #threadFactory(java.util.concurrent.ThreadFactory)}. Use this method
         * to set a custom executor.
         *
         * @param runSafeService executor to use for calls to notifyAll from Watcher callbacks etc
         * @return this
         * @since 4.1.0
         */
        public Builder runSafeService(Executor runSafeService) {
            this.runSafeService = runSafeService;
            return this;
        }
        /**
         * Sets the connection state listener manager factory. For example,
         * you can set {@link org.apache.curator.framework.state.ConnectionStateListenerManagerFactory#circuitBreaking(org.apache.curator.RetryPolicy)}
         *
         * @param connectionStateListenerManagerFactory manager factory to use
         * @return this
         * @since 4.2.0
         */
        public Builder connectionStateListenerManagerFactory(ConnectionStateListenerManagerFactory connectionStateListenerManagerFactory) {
            this.connectionStateListenerManagerFactory = Objects.requireNonNull(connectionStateListenerManagerFactory, "connectionStateListenerManagerFactory cannot be null");
            return this;
        }



        // getter ...

        @Deprecated
        public String getAuthScheme() {
            int qty = (authInfos != null) ? authInfos.size() : 0;
            switch (qty) {
                case 0: {
                    return null;
                }

                case 1: {
                    return authInfos.get(0).scheme;
                }

                default: {
                    throw new IllegalStateException("More than 1 auth has been added");
                }
            }
        }
        @Deprecated
        public byte[] getAuthValue() {
            int qty = (authInfos != null) ? authInfos.size() : 0;
            switch (qty) {
                case 0: {
                    return null;
                }

                case 1: {
                    byte[] bytes = authInfos.get(0).getAuth();
                    return (bytes != null) ? Arrays.copyOf(bytes, bytes.length) : null;
                }

                default: {
                    throw new IllegalStateException("More than 1 auth has been added");
                }
            }
        }
        public Executor getRunSafeService() {
            return runSafeService;
        }
        public ACLProvider getAclProvider() {
            return aclProvider;
        }
        public ZookeeperFactory getZookeeperFactory() {
            return zookeeperFactory;
        }
        public CompressionProvider getCompressionProvider() {
            return compressionProvider;
        }
        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }
        public EnsembleProvider getEnsembleProvider() {
            return ensembleProvider;
        }
        public int getSessionTimeoutMs() {
            return sessionTimeoutMs;
        }
        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }
        public int getWaitForShutdownTimeoutMs() {
            return waitForShutdownTimeoutMs;
        }
        public int getMaxCloseWaitMs() {
            return maxCloseWaitMs;
        }
        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }
        public String getNamespace() {
            return namespace;
        }
        public boolean useContainerParentsIfAvailable() {
            return useContainerParentsIfAvailable;
        }
        public ConnectionStateErrorPolicy getConnectionStateErrorPolicy() {
            return connectionStateErrorPolicy;
        }
        public ConnectionHandlingPolicy getConnectionHandlingPolicy() {
            return connectionHandlingPolicy;
        }
        public SchemaSet getSchemaSet() {
            return schemaSet;
        }
        public boolean isZk34CompatibilityMode() {
            return zk34CompatibilityMode;
        }
        public List<AuthInfo> getAuthInfos() {
            return authInfos;
        }
        public byte[] getDefaultData() {
            return defaultData;
        }
        public boolean canBeReadOnly() {
            return canBeReadOnly;
        }
        public ConnectionStateListenerManagerFactory getConnectionStateListenerManagerFactory() {
            return connectionStateListenerManagerFactory;
        }


    }


}
