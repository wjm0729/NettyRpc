package com.nettyrpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * RPC Handler（RPC request processor）
 * @author luxiaoxun
 * @author jiangmin.wu
 */
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcHandler.class);
    
    private RpcServer rpcServer;
    
    public RpcHandler(RpcServer rpcServer) {
    	this.rpcServer = rpcServer;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final RpcRequest request) throws Exception {
    	rpcServer.submit(new Runnable() {
            @Override
            public void run() {
                LOGGER.debug("Receive request " + request.getRequestId());
                RpcResponse response = new RpcResponse();
                response.setRequestId(request.getRequestId());
                try {
                    Object result = rpcServer.getRpcHandlerProxy().handle(rpcServer.getHandlerMap(), request);
                    response.setResult(result);
                } catch (Throwable t) {
                    response.setError(t);
                    LOGGER.error("RPC Server handle request error",t);
                }
                ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        LOGGER.debug("Send response for request " + request.getRequestId());
                    }
                });
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("server caught exception", cause);
        ctx.close();
    }
}
