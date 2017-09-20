package com.nettyrpc.client;

import java.net.SocketAddress;

import com.nettyrpc.protocol.AsyncMessage;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class AsyncClient {
	public static final AttributeKey<AsyncClient> CLIENT = AttributeKey.newInstance("Rpc ClientSession");
	
	private Channel channel;
	private SocketAddress remotePeer;
	private RpcClientHandler handler;
	
	public AsyncClient(Channel channel, RpcClientHandler handler) {
		this.channel = channel;
		this.remotePeer = channel.remoteAddress();
		this.handler = handler;
	}

	public void sendAsyncMessage(AsyncMessage message) {
		channel.writeAndFlush(message);
	}

	public Channel getChannel() {
		return channel;
	}

	public SocketAddress getRemotePeer() {
		return remotePeer;
	}

	public RpcClientHandler getHandler() {
		return handler;
	}
}
