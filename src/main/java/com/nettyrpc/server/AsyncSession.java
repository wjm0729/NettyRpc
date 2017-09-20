package com.nettyrpc.server;

import java.util.Map;

import com.google.common.collect.Maps;
import com.nettyrpc.execution.ActionQueue;
import com.nettyrpc.protocol.AsyncMessage;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class AsyncSession {
	public static final AttributeKey<AsyncSession> SESSION = AttributeKey.newInstance("Rpc AsyncClient");
	
	private RpcServer rpcServer;
	private ActionQueue queue;
	private Channel channel;
	private Map<String, Object> attr = Maps.newConcurrentMap();

	public AsyncSession(Channel channel) {
		super();
		this.channel = channel;
	}

	public void sendMessge(AsyncMessage message) {
		if (channel != null && channel.isActive()) {
			channel.writeAndFlush(message);
		}
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public ActionQueue getQueue() {
		return queue;
	}

	public void setQueue(ActionQueue queue) {
		this.queue = queue;
	}

	public RpcServer getRpcServer() {
		return rpcServer;
	}

	public void setRpcServer(RpcServer rpcServer) {
		this.rpcServer = rpcServer;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttr(AttributeKey<T> key) {
		return (T) this.attr.get(key.name());
	}

	public <T> void setAttr(AttributeKey<T> key, T value) {
		this.attr.put(key.name(), value);
	}

	public <T> AsyncSession addAttr(AttributeKey<T> key, T value) {
		setAttr(key, value);
		return this;
	}
}
