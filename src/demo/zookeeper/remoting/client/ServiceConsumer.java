package demo.zookeeper.remoting.client;
 
import demo.zookeeper.remoting.common.Constant;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class ServiceConsumer {
 
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceConsumer.class);
 
 // 用于等待 SyncConnected 事件触发后继续执行当前线程
    private CountDownLatch latch = new CountDownLatch(1);
 
    // 定义一个 volatile 成员变量，用于保存最新的 RMI 地址（考虑到该变量或许会被其它线程所修改，一旦修改后，该变量的值会影响到所有线程）
    private volatile List<String> urlList = new ArrayList<>(); 
 
    // 构造器
    public ServiceConsumer() {
    	// 连接 ZooKeeper 服务器并获取 ZooKeeper 对象
        ZooKeeper zk = connectServer(); 
        if (zk != null) {
        	// 观察 /registry 节点的所有子节点并更新 urlList 成员变量
            watchNode(zk); 
        }
    }
 
    // 查找 RMI 服务
    public <T extends Remote> T lookup() {
        T service = null;
        int size = urlList.size();
        if (size > 0) {
            String url;
            if (size == 1) {
            	// 若 urlList 中只有一个元素，则直接获取该元素
                url = urlList.get(0); 
                LOGGER.debug("using only url: {}", url);
            } else {
            	// 若 urlList 中存在多个元素，则随机获取一个元素
                url = urlList.get(ThreadLocalRandom.current().nextInt(size));
                LOGGER.debug("using random url: {}", url);
            }
         // 从 JNDI 中查找 RMI 服务
            service = lookupService(url); 
        }
        return service;
    }
 
 // 连接 ZooKeeper 服务器
    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(Constant.ZK_CONNECTION_STRING, Constant.ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                    	// 唤醒当前正在执行的线程
                        latch.countDown(); 
                    }
                }
            });
         // 使当前线程处于等待状态
            latch.await();
        } catch (IOException | InterruptedException e) {
            LOGGER.error("", e);
        }
        return zk;
    }
 
 // 观察 /registry 节点下所有子节点是否有变化
    private void watchNode(final ZooKeeper zk) {
        try {
            List<String> nodeList = zk.getChildren(Constant.ZK_REGISTRY_PATH, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeChildrenChanged) {
                    	// 若子节点有变化，则重新调用该方法（为了获取最新子节点中的数据）
                        watchNode(zk); 
                    }
                }
            });
         // 用于存放 /registry 所有子节点中的数据
            List<String> dataList = new ArrayList<>();
            for (String node : nodeList) {
            	// 获取 /registry 的子节点中的数据
                byte[] data = zk.getData(Constant.ZK_REGISTRY_PATH + "/" + node, false, null);
                dataList.add(new String(data));
            }
            LOGGER.debug("node data: {}", dataList);
         // 更新最新的 RMI 地址
            urlList = dataList;
        } catch (KeeperException | InterruptedException e) {
            LOGGER.error("", e);
        }
    }
 
 // 在 JNDI 中查找 RMI 远程服务对象
    @SuppressWarnings("unchecked")
    private <T> T lookupService(String url) {
        T remote = null;
        try {
            remote = (T) Naming.lookup(url);
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            if (e instanceof ConnectException) {
            	 // 若连接中断，则使用 urlList 中第一个 RMI 地址来查找（这是一种简单的重试方式，确保不会抛出异常）
                LOGGER.error("ConnectException -> url: {}", url);
                if (urlList.size() != 0) {
                    url = urlList.get(0);
                    return lookupService(url);
                }
            }
            LOGGER.error("", e);
        }
        return remote;
    }
}