package com.nettyrpc.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

public class RpcPingPongHandler extends SimpleChannelInboundHandler<RpcMessage> {
	private static final String PONG = "pong";
	private static final String PING = "ping";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RpcPingPongHandler.class);

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, final RpcMessage request) throws Exception {
		final String requestId = request.getRequestId();
		if (requestId.equals(PING)) {// client -> server
			RpcResponse response = new RpcResponse();
			response.setRequestId(PONG);
			ctx.writeAndFlush(response);
			LOGGER.info("receive ping");
		} else if (requestId.equals(PONG)) {// server -> client
			LOGGER.info("receive pong");
		} else {
			ctx.fireChannelRead(request);
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent state = (IdleStateEvent) evt;
			switch (state.state()) {
			case ALL_IDLE:
				RpcRequest request = new RpcRequest();
				request.setRequestId(PING);
				ctx.writeAndFlush(request);
				LOGGER.info("send ping");
			default:
				break;
			}
		}
	}
}
