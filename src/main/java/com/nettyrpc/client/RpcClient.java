package com.nettyrpc.client;

import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.client.proxy.ObjectProxy;
import com.nettyrpc.registry.ServiceDiscovery;
import com.nettyrpc.thread.NamedThreadFactory;

/**
 * RPC Client（Create RPC proxy）
 * 
 * @author luxiaoxun
 * @author jiangmin.wu
 */
public class RpcClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);
	
	private ServiceDiscovery serviceDiscovery;
	private volatile static ThreadPoolExecutor threadPoolExecutor = null;

	public RpcClient(ServiceDiscovery serviceDiscovery) {
		this(serviceDiscovery, null);
	}

	public RpcClient(ServiceDiscovery serviceDiscovery, ThreadPoolExecutor threadPoolExecutor) {
		this.serviceDiscovery = serviceDiscovery;
		RpcClient.threadPoolExecutor = threadPoolExecutor;
		if (RpcClient.threadPoolExecutor == null) {
			synchronized (RpcClient.class) {
				if (RpcClient.threadPoolExecutor == null) {
					int core = Runtime.getRuntime().availableProcessors();
					RpcClient.threadPoolExecutor = new ThreadPoolExecutor(core, core * 2, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536), new NamedThreadFactory("RpcClient"), new RejectedExecutionHandler() {
						@Override
						public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
							if(!executor.isShutdown()) {
								LOGGER.error("RpcClient queue is full, invoke in the caller Thread.");
								r.run();
							}
						}
					});
				}
			}
		}
		if(RpcClient.threadPoolExecutor.getThreadFactory() == null) {
			RpcClient.threadPoolExecutor.setThreadFactory(new NamedThreadFactory("RpcClient"));
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T create(Class<T> interfaceClass) {
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass }, new ObjectProxy<T>(interfaceClass));
	}

	public <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
		return new ObjectProxy<T>(interfaceClass);
	}

	public static void submit(Runnable task) {
		threadPoolExecutor.submit(task);
	}

	public void stop() {
		threadPoolExecutor.shutdown();
		serviceDiscovery.stop();
		ConnectManage.getInstance().stop();
	}
}
