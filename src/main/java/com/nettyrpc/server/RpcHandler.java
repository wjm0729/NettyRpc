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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * RPC Handler（RPC request processor）
 * @author luxiaoxun
 * @author jiangmin.wu
 */
@Sharable
public class RpcHandler extends SimpleChannelInboundHandler<IMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcHandler.class);
    
    private RpcServer rpcServer;
    private ConcurrentHashMap<String, AsyncSession> asyncClients = new ConcurrentHashMap<>();
    
    public RpcHandler(RpcServer rpcServer) {
    	this.rpcServer = rpcServer;
    }

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		super.channelRegistered(ctx);
		final Channel channel = ctx.channel();
		String channelId = channel.id().asLongText();
		
		AsyncSession client = new AsyncSession(channel);
		client.setQueue(new ActionQueue(rpcServer.getAsyncActionExecutor()));
		client.setRpcServer(rpcServer);
		asyncClients.put(channelId, client);
		channel.attr(AsyncSession.SESSION).set(client);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		try {
			rpcServer.getRpcSessionListener().onActive(asyncClients.get(ctx.channel().id().asLongText()));
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}
    
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		Channel channel = ctx.channel();
		String channelId = channel.id().asLongText();
		AsyncSession client = asyncClients.remove(channelId);
		try {
			rpcServer.getRpcSessionListener().onInactive(client);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
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
    	try {
			rpcServer.getRpcSessionListener().onReceive(asyncClients.get(ctx.channel().id().asLongText()), msg);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
    }

	private void handAsyncMessage(final ChannelHandlerContext ctx, AsyncMessage asyncMsg) {
		Channel channel = ctx.channel();
		String channelId = channel.id().asLongText();
		AsyncSession client = asyncClients.get(channelId);
		client.getQueue().enqueue(new AsyncAction(client.getQueue(), asyncMsg, client));
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
	
	public void send2AllAsyncClient(AsyncMessage message) {
		for(AsyncSession client:asyncClients.values()) {
			client.sendMessge(message);
		}
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("server caught exception {} {}", cause.getMessage(), ctx.channel());
        ctx.close();
    }
}
