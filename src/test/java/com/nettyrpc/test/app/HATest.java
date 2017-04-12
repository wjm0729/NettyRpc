package com.nettyrpc.test.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.nettyrpc.client.AsyncClientHandler;
import com.nettyrpc.client.AsyncRPCCallback;
import com.nettyrpc.client.ClientSession;
import com.nettyrpc.client.ConnectManage;
import com.nettyrpc.client.RpcFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.HelloService;
import com.nettyrpc.test.client.Person;

import io.netty.channel.Channel;

/**
 * Created by luxiaoxun on 2016/3/17.
 */
public class HATest {
	private static final String address = "192.168.1.105:4180,192.168.1.105:4181,192.168.1.105:4182";
	public static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);

	public static void main(String[] args) throws Throwable {
		//orderTest();
		 syncTest();
		//asyncTest();
		
	}
	
	private static void orderTest() throws Exception {
		long start = System.currentTimeMillis();
		ConnectManage connectManage = new ConnectManage(address, true);
		final RpcClient rpcClient = new RpcClient(connectManage);
		rpcClient.afterPropertiesSet();
		final int count = 1000;
		final CountDownLatch countDownLatch = new CountDownLatch(count+1);
		IAsyncObjectProxy hello = rpcClient.createAsync(HelloService.class);
		
		for(int i=1; i<=count; i++) {
			RpcFuture helloPersonFuture = hello.call("hello", Integer.valueOf(i));
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


	private static void syncTest() throws Exception {
		long start = System.currentTimeMillis();
		ConnectManage connectManage = new ConnectManage(address, true);
		final RpcClient rpcClient = new RpcClient(connectManage);
		rpcClient.afterPropertiesSet();
		final int count = 0;
		final CountDownLatch countDownLatch = new CountDownLatch(count+1);
		final HelloPersonService client = rpcClient.create(HelloPersonService.class);
		
		String id = "aaa";
		
		rpcClient.registerAsyncHandler(id, new AsyncClientHandler() {
			@Override
			public void handMessage(AsyncMessage message, ClientSession session) {
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

	private static void asyncTest() throws Throwable {
		long start = System.currentTimeMillis();
		ConnectManage connectManage = new ConnectManage(address, true);
		final RpcClient rpcClient = new RpcClient(connectManage);
		rpcClient.afterPropertiesSet();
		final int count = 10000;
		final CountDownLatch countDownLatch = new CountDownLatch(count);
		final IAsyncObjectProxy client = rpcClient.createAsync(HelloPersonService.class);
		for (int i = 0; i < count; i++) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					int num = 500;
					try {
						RpcFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
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
