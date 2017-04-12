package com.nettyrpc.server;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.execution.ActionQueue;
import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.protocol.IMessage;
import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * RPC Handler（RPC request processor）
 * @author luxiaoxun
 * @author jiangmin.wu
 */
public class RpcHandler extends SimpleChannelInboundHandler<IMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcHandler.class);
    
    private RpcServer rpcServer;
    private ConcurrentHashMap<String, AsyncClient> asyncClients = new ConcurrentHashMap<>();
    
    public RpcHandler(RpcServer rpcServer) {
    	this.rpcServer = rpcServer;
    }
    
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		Channel channel = ctx.channel();
		String channelId = channel.id().asLongText();
		asyncClients.remove(channelId);
	}

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, IMessage msg) throws Exception {
    	if(msg instanceof RpcRequest) {
    		final RpcRequest request = (RpcRequest) msg;
        	handRpcRequest(ctx, request);
    	}
    	if(msg instanceof AsyncMessage) {
    		AsyncMessage asyncMsg = (AsyncMessage) msg;
    		handAsyncMessage(ctx, asyncMsg);
    	}
    }

	private void handAsyncMessage(final ChannelHandlerContext ctx, AsyncMessage asyncMsg) {
		Channel channel = ctx.channel();
		String channelId = channel.id().asLongText();
		AsyncClient client = asyncClients.get(channelId);
		if(client == null) {
			synchronized (channel) {
				if(!asyncClients.containsKey(channelId)) {
					client = new AsyncClient(channel);
					if(asyncClients.putIfAbsent(channelId, client) == null) {
						client.setQueue(new ActionQueue(rpcServer.getAsyncActionExecutor()));
						client.setRpcServer(rpcServer);
					} else {
						client = asyncClients.get(channelId);
					}
				}
			}
		}
		if(client != null) {
			client.getQueue().enqueue(new AsyncAction(client.getQueue(), asyncMsg, client));
		}
	}

	private void handRpcRequest(final ChannelHandlerContext ctx, final RpcRequest request) {
		rpcServer.submit(new Runnable() {
		    @Override
		    public void run() {
		        LOGGER.debug("Receive request " + request.getRequestId());
		        RpcResponse response = new RpcResponse();
		        response.setRequestId(request.getRequestId());
		        try {
		        	String className = request.getClassName();
			        Object serviceBean = rpcServer.getHandlerMap().get(className);
		            Object result = rpcServer.getRpcRequestHandler().handle(serviceBean, request, ctx.channel());
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
