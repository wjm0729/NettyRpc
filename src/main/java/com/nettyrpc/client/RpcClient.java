package com.nettyrpc.client;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.google.common.collect.Maps;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.client.proxy.ObjectProxy;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.protocol.id.IRequestIDCreater;
import com.nettyrpc.thread.NamedThreadFactory;

import io.netty.channel.Channel;

/**
 * RPC Client（Create RPC proxy）
 * 
 * @author luxiaoxun
 * @author jiangmin.wu
 */
public class RpcClient implements InitializingBean {
	private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);
	/**
	 * 请求超时时间 ms
	 */
	private int requestTimeoutMillis = 300_000;
	private ConnectManage connectManage;
	private IRequestIDCreater requestIDCreater = IRequestIDCreater.DEFAULT;
	private ThreadPoolExecutor rpcCallbackThreadPool;
	private Map<String, AsyncClientHandler> asyncHandlerMap = Maps.newConcurrentMap();

	public RpcClient(ConnectManage connectManage) {
		this.connectManage = connectManage;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if(rpcCallbackThreadPool == null) {
			int maxCore = Math.max(4, Runtime.getRuntime().availableProcessors());
			rpcCallbackThreadPool = new ThreadPoolExecutor(maxCore, maxCore * 2, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536), new NamedThreadFactory("RpcClient"), new RejectedExecutionHandler() {
				@Override
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
					if(!executor.isShutdown()) {
						LOGGER.error("RpcClient queue is full, invoke in the caller Thread.");
						r.run();
					}
				}
			});
		}
		
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
	
	public ClientSession sendAsyncMessage(AsyncMessage message) {
		RpcClientHandler client = connectManage.chooseHandler();
		client.sendAsyncMessage(message);
		return client.getSession();
	}

	public void submit(Runnable task) {
		if(!rpcCallbackThreadPool.isShutdown()) {
			rpcCallbackThreadPool.submit(task);
		}
	}

	public void stop() {
		rpcCallbackThreadPool.shutdown();
		connectManage.stop();
	}
	
	// for spring
	public IRequestIDCreater getRequestIDCreater() {
		return requestIDCreater;
	}

	public void setRequestIDCreater(IRequestIDCreater requestIDCreater) {
		this.requestIDCreater = requestIDCreater;
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

	public void setRpcCallbackThreadPool(ThreadPoolExecutor rpcCallbackThreadPool) {
		this.rpcCallbackThreadPool = rpcCallbackThreadPool;
	}
}
