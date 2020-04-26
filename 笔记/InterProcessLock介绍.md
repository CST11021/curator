InterProcessLock介绍


摘自：https://baijiahao.baidu.com/s?id=1649547145421332925&wfr=spider&for=pc

zookeeper分布式锁基于zookeeper中有序节点实现分布有序锁等待队列，通过watch机制监听锁释放，大大提升抢锁效率，避免自旋等锁，浪费cpu资源，同时只watch前一个节点可避免锁释放之后通知所有节点重新竞争锁导致的羊群效应。

curator基于zookeeper实现了：

* InterProcessMutex（分布式可重入排它锁）
* InterProcessSemaphoreMutex（分布式排它锁）
* InterProcessReadWriteLock（分布式读写锁）
* InterProcessMultiLock（将多个锁作为单个实体管理的容器）

四种不同的锁实现，这里简单说下InterProcessMutex的实现原理：

（1）在根节点（持久化节点，eg：/lock）下创建临时有序节点。

（2）获取根节点下所有子节点，判断上一步中创建的临时节点是否是最小的节点。

是：加锁成功；不是：加锁失败，对前一个节点加watch，收到删除消息后，加锁成功。

（3）执行被锁住的代码块。

（4）删除自己在根节点下创建的节点，解锁成功。



zookeeper集群使用zap协议来保证集群之间数据的一致性，频繁的进行写、监听操作将对zk集群产生较大压力，所以不推荐大家使用。