package com.nettyrpc.test.app;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.nettyrpc.client.AsyncRPCCallback;
import com.nettyrpc.client.RPCFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.registry.ServiceDiscovery;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.Person;

/**
 * Created by luxiaoxun on 2016/3/17.
 */
public class HATest {
	public static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);

	public static void main(String[] args) throws Throwable {
		syncTest();
		//asyncTest();
	}

	private static void syncTest() {
		long start = System.currentTimeMillis();
		ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181", 10);
		final RpcClient rpcClient = new RpcClient(serviceDiscovery);
		final int count = 10000;
		final CountDownLatch countDownLatch = new CountDownLatch(count+1);
		final HelloPersonService client = rpcClient.create(HelloPersonService.class);
		for (int i = 0; i < count; i++) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					int num = 500;
					List<Person> persons = client.GetTestPerson("xiaoming", num);
					System.out.println("persons:" + persons.size());
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
		ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181", 10);
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
