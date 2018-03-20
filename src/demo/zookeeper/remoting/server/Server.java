package demo.zookeeper.remoting.server;
 
import demo.zookeeper.remoting.common.HelloService;
 
public class Server {
 
    public static void main(String[] args) throws Exception {
        String host = "192.168.1.100";
        int port = Integer.parseInt("11233");
        ServiceProvider provider = new ServiceProvider();
 
        HelloService helloService = new HelloServiceImpl();
        provider.publish(helloService, host, port);
 
        Thread.sleep(Long.MAX_VALUE);
    }
}