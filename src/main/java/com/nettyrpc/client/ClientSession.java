package com.nettyrpc.client;

import java.net.SocketAddress;

import com.nettyrpc.protocol.AsyncMessage;

import io.netty.channel.Channel;

public class ClientSession {
	private Channel channel;
	private SocketAddress remotePeer;
	
	public ClientSession(Channel channel) {
		this.channel = channel;
		this.remotePeer = channel.remoteAddress();
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
}
