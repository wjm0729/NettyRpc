package com.nettyrpc.client;

import java.net.SocketAddress;

public class RpcClientHandlerWraper {
	private SocketAddress remotePeer;
	private RpcClientHandler handler;

	public RpcClientHandlerWraper(SocketAddress remotePeer, RpcClientHandler handler) {
		super();
		this.remotePeer = remotePeer;
		this.handler = handler;
	}

	public SocketAddress getRemotePeer() {
		return remotePeer;
	}

	public RpcClientHandler getHandler() {
		return handler;
	}

	public void setHandler(RpcClientHandler handler) {
		this.handler = handler;
	}
}
