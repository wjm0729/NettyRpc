package com.nettyrpc.test.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;
import com.nettyrpc.client.AsyncClientHandler;
import com.nettyrpc.client.ClientSession;
import com.nettyrpc.client.ConnectManage;
import com.nettyrpc.client.RPCFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.registry.ServiceRegistry;
import com.nettyrpc.server.RpcServer;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.HelloService;
import com.nettyrpc.test.client.Person;
import com.nettyrpc.test.server.HelloPersonServiceImpl;
import com.nettyrpc.test.server.HelloServiceImpl;

//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(locations = "classpath:client-spring.xml")
public class HARpcClientTest {

	private boolean cluster = true;
//    @Autowired
    private RpcClient rpcClient;
    private RpcServer rpcServer;
    
    @Before
    public void init() throws Exception {
    	
    	if(cluster)
    	// 集群模式
    	{
    		String registryAddress = "10.1.6.72:2181";
			ServiceRegistry registry = new ServiceRegistry(registryAddress);
    		registry.setZkTimeoutMillis(5000);
    		
    		rpcServer = new RpcServer("127.0.0.1:18866", registry);
    		rpcServer.registerRpcService(HelloService.class.getName(), new HelloServiceImpl());
    		rpcServer.registerRpcService(HelloPersonService.class.getName(), new HelloPersonServiceImpl());
    		rpcServer.afterPropertiesSet();
    		
    		String serviceAddress = registryAddress;
    		ConnectManage connectManage = new ConnectManage(serviceAddress, true);
    		rpcClient = new RpcClient(connectManage);
    		rpcClient.afterPropertiesSet();
    	}
    	
    	else
    	// 非集群模式
    	{
    		rpcServer = new RpcServer("127.0.0.1:18866");
    		rpcServer.registerRpcService(HelloService.class.getName(), new HelloServiceImpl());
    		rpcServer.registerRpcService(HelloPersonService.class.getName(), new HelloPersonServiceImpl());
    		rpcServer.afterPropertiesSet();
    		
    		String serviceAddress = "127.0.0.1:18866";
    		ConnectManage connectManage = new ConnectManage(serviceAddress, false);
    		rpcClient = new RpcClient(connectManage);
    		rpcClient.afterPropertiesSet();
    	}
    }
    
    @After
	public void setTear() throws Throwable {
		rpcClient.stop();
		rpcServer.destroy();
	}
    
    @Test
    public void asyncMessage() throws Throwable {
    	final CountDownLatch latch = new CountDownLatch(2);
    	String id1 = "async_msg_id1";
    	String id2 = "async_msg_id2";
    	
    	final Map<String, String> m = Maps.newHashMap();
    	
    	AsyncClientHandler h = new AsyncClientHandler() {
			@Override
			public void handMessage(AsyncMessage message, ClientSession session) {
				m.put(message.getRequestId(), message.getRequestId());
				latch.countDown();
			}
		};
		
		rpcClient.registerAsyncHandler(id1, h);
    	rpcClient.registerAsyncHandler(id2, h);
    	
    	AsyncMessage msg = new AsyncMessage();
    	msg.setRequestId(id1);
    	rpcClient.sendAsyncMessage(msg);
    	
    	AsyncMessage msg2 = new AsyncMessage();
    	msg2.setRequestId(id2);
    	rpcClient.sendAsyncMessage(msg2);
    	
    	latch.await();
    	
    	Assert.assertEquals(m.get(id1), id1);
    	Assert.assertEquals(m.get(id2), id2);
    }

    @Test
    public void helloTest1() {
        HelloService helloService = rpcClient.create(HelloService.class);
        String result = helloService.hello("World");
        Assert.assertEquals("Hello! World", result);
    }

    @Test
    public void helloTest2() {
        HelloService helloService = rpcClient.create(HelloService.class);
        Person person = new Person("Yong", "Huang");
        String result = helloService.hello(person);
        Assert.assertEquals("Hello! Yong Huang", result);
    }

    @Test
    public void helloPersonTest() {
        HelloPersonService helloPersonService = rpcClient.create(HelloPersonService.class);
        int num = 5;
        List<Person>  persons = helloPersonService.GetTestPerson("xiaoming", num);
        
        for(int i=0;i<100; i++) {
        	helloPersonService.GetTestPerson("aaaaaa"+i, num);
        }
        
        System.err.println(persons.size());
        
        List<Person> expectedPersons = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            expectedPersons.add(new Person(Integer.toString(i), "xiaoming"));
        }
        assertThat(persons, equalTo(expectedPersons));

        for (int i = 0; i<persons.size(); ++i) {
            System.out.println(persons.get(i));
        }
    }

    @Test
    public void helloFutureTest1() throws Throwable {
        IAsyncObjectProxy helloService = rpcClient.createAsync(HelloService.class);
        RPCFuture result = helloService.call("hello", "World");
        Assert.assertEquals("Hello! World", result.get());
    }

    @Test
    public void helloFutureTest2() throws Throwable {
        IAsyncObjectProxy helloService = rpcClient.createAsync(HelloService.class);
        Person person = new Person("Yong", "Huang");
        RPCFuture result = helloService.call("hello", person);
        Assert.assertEquals("Hello! Yong Huang", result.get());
    }

    @Test
    public void helloPersonFutureTest1() throws Throwable {
        IAsyncObjectProxy helloPersonService = rpcClient.createAsync(HelloPersonService.class);
        int num = 5;
        RPCFuture result = helloPersonService.call("GetTestPerson", "xiaoming", num);
        List<Person> persons = (List<Person>) result.get();
        List<Person> expectedPersons = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            expectedPersons.add(new Person(Integer.toString(i), "xiaoming"));
        }
        assertThat(persons, equalTo(expectedPersons));

        for (int i = 0; i < num; ++i) {
            System.out.println(persons.get(i));
        }
    }
}
