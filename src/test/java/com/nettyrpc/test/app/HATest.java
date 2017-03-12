package com.nettyrpc.test.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.nettyrpc.client.AsyncClientHandler;
import com.nettyrpc.client.AsyncRPCCallback;
import com.nettyrpc.client.ConnectManage;
import com.nettyrpc.client.RPCFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.registry.ServiceDiscovery;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.HelloService;
import com.nettyrpc.test.client.Person;

import io.netty.channel.Channel;

/**
 * Created by luxiaoxun on 2016/3/17.
 */
public class HATest {
	public static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);

	public static void main(String[] args) throws Throwable {
		//orderTest();
		 syncTest();
		//asyncTest();
		
	}
	private static void orderTest() throws InterruptedException {
		long start = System.currentTimeMillis();
		ConnectManage connectManage = new ConnectManage(1000, 10);
		ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181", connectManage);
		final RpcClient rpcClient = new RpcClient(serviceDiscovery);
		final int count = 1000;
		final CountDownLatch countDownLatch = new CountDownLatch(count+1);
		IAsyncObjectProxy hello = rpcClient.createAsync(HelloService.class);
		
		for(int i=1; i<=count; i++) {
			RPCFuture helloPersonFuture = hello.call("hello", Integer.valueOf(i));
			helloPersonFuture.addCallback(new AsyncRPCCallback() {
	            @Override
	            public void success(Object result) {
		            System.out.println(result);
		            countDownLatch.countDown();
	            }
	            @Override
	            public void fail(Exception e) {
		            System.out.println(e);
		            countDownLatch.countDown();
	            }
            });
		}
		countDownLatch.await();
		rpcClient.stop();
		System.err.println("-----" + executor.getPoolSize());
		executor.shutdownNow();
		System.err.println("End "+(System.currentTimeMillis() - start));
	}


	private static void syncTest() {
		long start = System.currentTimeMillis();
		ConnectManage connectManage = new ConnectManage(1000, 1);
		ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181", connectManage);
		final RpcClient rpcClient = new RpcClient(serviceDiscovery);
		final int count = 0;
		final CountDownLatch countDownLatch = new CountDownLatch(count+1);
		final HelloPersonService client = rpcClient.create(HelloPersonService.class);
		
		String id = "aaa";
		
		rpcClient.registerAsyncHandler(id, new AsyncClientHandler() {
			@Override
			public void handMessage(AsyncMessage message, Channel channel) {
				System.err.println("!!!!!!!!!!!"+message+"!!!!!!!!!!!!!");
			}
		});
		
		AsyncMessage message = new AsyncMessage();
		message.setRequestId(id);
		message.setBody("AAAAA");
		rpcClient.sendAsyncMessage(message);
		
		for (int i = 0; i < count; i++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			executor.execute(new Runnable() {
				@Override
				public void run() {
					int num =2;
					Map<String, Person> persons = client.GetTestPerson2("xiaoming", num);
					System.out.println("persons:" + persons);
					countDownLatch.countDown();
				}
			});
		}

		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		rpcClient.stop();
		System.err.println("-----" + executor.getPoolSize());
		executor.shutdownNow();
		System.err.println("End "+(System.currentTimeMillis() - start));
	}

	private static void asyncTest() {
		long start = System.currentTimeMillis();
		ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181");
		final RpcClient rpcClient = new RpcClient(serviceDiscovery);
		final int count = 10000;
		final CountDownLatch countDownLatch = new CountDownLatch(count);
		final IAsyncObjectProxy client = rpcClient.createAsync(HelloPersonService.class);
		for (int i = 0; i < count; i++) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					int num = 500;
					try {
						RPCFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
						helloPersonFuture.addCallback(new AsyncRPCCallback() {
				            @Override
				            public void success(Object result) {
					            @SuppressWarnings("unchecked")
					            List<Person> persons = (List<Person>) result;
					            System.out.println("persons:" + persons.size());
					            countDownLatch.countDown();
				            }

				            @Override
				            public void fail(Exception e) {
					            System.out.println(e);
					            countDownLatch.countDown();
				            }
			            });
					} catch (Exception e) {
						System.out.println(e);
					}
				}
			});
		}

		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		rpcClient.stop();
		System.err.println("-----" + executor.getPoolSize());
		executor.shutdownNow();
		System.err.println("End "+(System.currentTimeMillis() - start));
	}
}
