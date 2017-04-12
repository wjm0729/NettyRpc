package com.nettyrpc.client;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.protocol.IMessage;
import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Created by luxiaoxun on 2016-03-14.
 * @author jiangmin.wu
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<IMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClientHandler.class);

    private ConcurrentHashMap<String, RpcFuture> pendingRPC = new ConcurrentHashMap<>();
    private RpcClient rpcClient;
    private volatile ClientSession session;

    public ClientSession getSession() {
        return session;
    }

    public SocketAddress getRemotePeer() {
        return session.getRemotePeer();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        
        LOGGER.info("client inactive cancel all request {} channel {}", pendingRPC.size(), ctx.channel());
        
        for(RpcFuture f : pendingRPC.values()) {
            f.cancel(false);
        }
        pendingRPC.clear();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.session = new ClientSession(ctx.channel());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, IMessage msg) throws Exception {
    	if(msg instanceof RpcResponse) {
    		RpcResponse response = (RpcResponse) msg;
    		String requestId = response.getRequestId();
            RpcFuture rpcFuture = pendingRPC.get(requestId);
            if (rpcFuture != null) {
                pendingRPC.remove(requestId);
                rpcFuture.done(response);
            } else {
            	LOGGER.info("can't find RpcFuture of {}", response);
            }
		} else if (msg instanceof AsyncMessage) {
			String id = msg.getRequestId();
			AsyncMessage message = (AsyncMessage) msg;
			AsyncClientHandler handler = rpcClient.getAsyncHandlerMap().get(id);
			if (handler != null) {
//				handler = SerializationUtil.objenesis.newInstance(handler.getClass());
				handler.handMessage(message, session);
			} else {
				LOGGER.error("async message handler {} not found", id);
				AsyncClientHandler.DEFAULT.handMessage(message, session);
			}
		}
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("client caught exception", cause);
        ctx.close();
    }
    
    @Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent state = (IdleStateEvent) evt;
			switch (state.state()) {
			case ALL_IDLE:
				RpcRequest request = new RpcRequest();
				request.setRequestId("ping");
				ctx.writeAndFlush(request);
				LOGGER.debug("send ping");
			default:
				break;
			}
		}
	}

    public void close() {
        session.getChannel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

	public RpcFuture sendRequest(RpcRequest request, RpcClient rpcClient, SocketAddress socketAddress) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		RpcFuture rpcFuture = new RpcFuture(request, rpcClient, this, socketAddress);
		pendingRPC.put(request.getRequestId(), rpcFuture);
		session.getChannel().writeAndFlush(request).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				latch.countDown();
			}
		});
		latch.await();
		return rpcFuture;
	}
	
	public void sendAsyncMessage(AsyncMessage message) {
		session.sendAsyncMessage(message);
	}
	
    public void checkTimeoutRequest() {
        List<RpcFuture> list = null;
        for(RpcFuture f : pendingRPC.values()) {
            if(f.isTimeout()) {
            	if(list == null) {
            		list = new ArrayList<>();
            	}
                list.add(f);
            }
        }
        
        if(list != null) {
        	for(RpcFuture f : list) {
                if(!f.isDone() && f.cancel(false)) {
                    pendingRPC.remove(f.getRequestId());
                }
            }
        }
    }

	public RpcClient getRpcClient() {
		return rpcClient;
	}

	public void setRpcClient(RpcClient rpcClient) {
		this.rpcClient = rpcClient;
	}

}
