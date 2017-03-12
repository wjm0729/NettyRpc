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
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Maps;
import com.nettyrpc.client.AsyncClientHandler;
import com.nettyrpc.client.RPCFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.registry.ServiceDiscovery;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.HelloService;
import com.nettyrpc.test.client.Person;

import io.netty.channel.Channel;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:client-spring.xml")
public class HelloServiceTest {
	
    @Autowired
    private RpcClient rpcClient;
    
    @Before
    public void init(){
    	rpcClient = new RpcClient(new ServiceDiscovery("10.1.6.72:2181"));
    }
    
    @After
	public void setTear() {
		if (rpcClient != null) {
			rpcClient.stop();
		}
	}
    
    @Test
    public void asyncMessage() throws Throwable {
    	final CountDownLatch latch = new CountDownLatch(2);
    	String id1 = "async_msg_id1";
    	String id2 = "async_msg_id2";
    	
    	final Map<String, String> m = Maps.newHashMap();
    	
    	AsyncClientHandler h = new AsyncClientHandler() {
			@Override
			public void handMessage(AsyncMessage message, Channel channel) {
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
