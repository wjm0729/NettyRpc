package com.nettyrpc.client;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.client.proxy.ObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.protocol.id.IRequestIDCreater;
import com.nettyrpc.thread.NamedThreadFactory;

/**
 * RPC Client（Create RPC proxy）
 * 
 * @author luxiaoxun
 * @author jiangmin.wu
 */
public class RpcClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);
	/**
	 * 请求超时时间 ms
	 */
	private int requestTimeoutMillis = 300_000;
	private ConnectManage connectManage;
	private IRequestIDCreater requestIDCreater = IRequestIDCreater.DEFAULT;
	private Map<String, AsyncClientHandler> asyncHandlerMap = Maps.newConcurrentMap();
	private int maxCore = Math.max(4, Runtime.getRuntime().availableProcessors());
	private ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(maxCore, maxCore * 2, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536), new NamedThreadFactory("RpcClient"), new RejectedExecutionHandler() {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
			if(!executor.isShutdown()) {
				LOGGER.error("RpcClient queue is full, invoke in the caller Thread.");
				r.run();
			}
		}
	});

	public RpcClient(ConnectManage connectManage) {
		this.connectManage = connectManage;
		this.connectManage.setRpcClient(this);
		this.connectManage.init();
	}
	
	@SuppressWarnings("unchecked")
	public <T> T create(Class<T> interfaceClass) {
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass }, new ObjectProxy<T>(interfaceClass, this));
	}

	public <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
		return new ObjectProxy<T>(interfaceClass, this);
	}
	
	public void registerAsyncHandler(String id, AsyncClientHandler handler) {
		asyncHandlerMap.put(id, handler);
	}
	
	public void sendAsyncMessage(AsyncMessage message) {
		connectManage.chooseHandler().sendAsyncMessage(message);
	}

	public void submit(Runnable task) {
		if(!threadPoolExecutor.isShutdown()) {
			threadPoolExecutor.submit(task);
		}
	}

	public void stop() {
		threadPoolExecutor.shutdown();
		connectManage.stop();
	}
	
	// for spring
	public IRequestIDCreater getRequestIDCreater() {
		return requestIDCreater;
	}

	public void setRequestIDCreater(IRequestIDCreater requestIDCreater) {
		this.requestIDCreater = requestIDCreater;
	}

	public ThreadPoolExecutor getThreadPoolExecutor() {
		return threadPoolExecutor;
	}

	public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
		this.threadPoolExecutor = threadPoolExecutor;
	}

	public int getRequestTimeoutMillis() {
		return requestTimeoutMillis;
	}

	public void setRequestTimeoutMillis(int requestTimeoutMillis) {
		this.requestTimeoutMillis = requestTimeoutMillis;
	}

	public Map<String, AsyncClientHandler> getAsyncHandlerMap() {
		return asyncHandlerMap;
	}

	public ConnectManage getConnectManage() {
		return connectManage;
	}
}
