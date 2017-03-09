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
import com.nettyrpc.protocol.id.IRequestIDCreater;
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
	private IRequestIDCreater requestIDCreater = IRequestIDCreater.DEFAULT;
	private ThreadPoolExecutor threadPoolExecutor = null;

	public RpcClient(ServiceDiscovery serviceDiscovery) {
		this(serviceDiscovery, null);
	}
	
	public RpcClient(ServiceDiscovery serviceDiscovery, IRequestIDCreater requestIDCreater) {
		this(serviceDiscovery, null, null);
	}

	public RpcClient(ServiceDiscovery serviceDiscovery, ThreadPoolExecutor threadPoolExecutor, IRequestIDCreater requestIDCreater) {
		this.serviceDiscovery = serviceDiscovery;
		if (threadPoolExecutor == null) {
			int core = Runtime.getRuntime().availableProcessors();
			threadPoolExecutor = new ThreadPoolExecutor(core, core * 2, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536), new NamedThreadFactory("RpcClient"), new RejectedExecutionHandler() {
				@Override
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
					if(!executor.isShutdown()) {
						LOGGER.error("RpcClient queue is full, invoke in the caller Thread.");
						r.run();
					}
				}
			});
		}
		this.threadPoolExecutor = threadPoolExecutor;
		if(threadPoolExecutor.getThreadFactory() == null) {
			threadPoolExecutor.setThreadFactory(new NamedThreadFactory("RpcClient"));
		}
		
		if(requestIDCreater != null) {
			this.requestIDCreater = requestIDCreater; 
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T create(Class<T> interfaceClass) {
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass }, new ObjectProxy<T>(interfaceClass, this));
	}

	public <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
		return new ObjectProxy<T>(interfaceClass, this);
	}

	public void submit(Runnable task) {
		if(!threadPoolExecutor.isShutdown()) {
			threadPoolExecutor.submit(task);
		}
	}

	public void stop() {
		threadPoolExecutor.shutdown();
		serviceDiscovery.stop();
		getConnectManage().stop();
	}

	public ConnectManage getConnectManage() {
		return serviceDiscovery.getConnectManage();
	}

	public IRequestIDCreater getRequestIDCreater() {
		return requestIDCreater;
	}
}
