package com.nettyrpc.server;

import com.nettyrpc.execution.ActionQueue;
import com.nettyrpc.protocol.AsyncMessage;

import io.netty.channel.Channel;

public class AsyncClient {
	private RpcServer rpcServer;
	private ActionQueue queue;
	private Channel channel;

	public AsyncClient(Channel channel) {
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
}
