package com.nettyrpc.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class PingPongHandler extends SimpleChannelInboundHandler<AbastractMessage> {
	private static final String PONG = "pong";
	private static final String PING = "ping";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PingPongHandler.class);

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, final AbastractMessage request) throws Exception {
		final String requestId = request.getRequestId();
		if (requestId.equals(PING)) {// client -> server
			RpcResponse response = new RpcResponse();
			response.setRequestId(PONG);
			ctx.writeAndFlush(response);
			LOGGER.debug("receive ping");
		} else if (requestId.equals(PONG)) {// server -> client
			LOGGER.debug("receive pong");
		} else {
			ctx.fireChannelRead(request);
		}
	}
}
