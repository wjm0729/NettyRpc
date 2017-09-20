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
import com.nettyrpc.protocol.PingPongHandler;
import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
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
    private RpcClient rpcClient;
    private volatile AsyncClient client;

    public AsyncClient getSession() {
        return client;
    }

    public SocketAddress getRemotePeer() {
        return client.getRemotePeer();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        
    	try {
			rpcClient.getRpcClientListener().onActive(client);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        
        LOGGER.info("client inactive channel {}", ctx.channel());
        
//        for(RpcFuture f : getPendingRpc().values()) {
//            f.cancel(true);
//        }
//        pendingRPC.clear();

    	try {
			rpcClient.getRpcClientListener().onInactive(client);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
        rpcClient.getConnectManage().removeHandler(this);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        Channel channel = ctx.channel();
        
		this.client = new AsyncClient(channel, this);
        channel.attr(AsyncClient.CLIENT).set(client);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, final IMessage msg) throws Exception {
    	/**
    	 * 客户端处理完成必须扔出Worker, 否则嵌套调用就卡这里了。
    	 */
    	rpcClient.submit(new Runnable() {
			@Override
			public void run() {
				handMessage(msg);
			}
		});
    }

	private void handMessage(IMessage msg) {
    	if(msg instanceof RpcResponse) {
    		RpcResponse response = (RpcResponse) msg;
    		String requestId = response.getRequestId();
            RpcFuture rpcFuture = getPendingRpc().get(requestId);
            if (rpcFuture != null) {
				getPendingRpc().remove(requestId);
                rpcFuture.done(response);
            } else {
            	LOGGER.info("can't find RpcFuture of {}", response);
            }
		} else if (msg instanceof AsyncMessage) {
			String id = msg.getRequestId();
			AsyncMessage message = (AsyncMessage) msg;
			AsyncClientHandler handler = rpcClient.getAsyncHandlerMap().get(id);
			if (handler != null) {
				handler.handMessage(client, message);
			} else {
				AsyncClientHandler.DEFAULT.handMessage(client, message);
				LOGGER.debug("async message handler {} not found", id);
			}
		}
    	
    	try {
			rpcClient.getRpcClientListener().onReceive(client, msg);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("client caught exception {} {}", cause.getMessage(), ctx.channel());
        ctx.close();
    }
    
    @Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent state = (IdleStateEvent) evt;
			switch (state.state()) {
			case READER_IDLE:
			case WRITER_IDLE:
			case ALL_IDLE:
				RpcRequest request = new RpcRequest();
				request.setRequestId(PingPongHandler.PING);
				ctx.writeAndFlush(request);
				LOGGER.debug("send ping");
			default:
				break;
			}
		}
		
		try {
			rpcClient.getRpcClientListener().onEventTriggered(client, evt);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

    public void close() {
        client.getChannel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

	public RpcFuture sendRequest(RpcRequest request, RpcClient rpcClient, SocketAddress socketAddress) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		RpcFuture rpcFuture = new RpcFuture(request, rpcClient, this, socketAddress);
		getPendingRpc().put(request.getRequestId(), rpcFuture);
		client.getChannel().writeAndFlush(request).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				latch.countDown();
			}
		});
		latch.await();
		return rpcFuture;
	}
	
	public void sendAsyncMessage(AsyncMessage message) {
		client.sendAsyncMessage(message);
	}
	
    public ConcurrentHashMap<String, RpcFuture> getPendingRpc() {
    	return rpcClient.getConnectManage().getPendingRPC();
	}

	public RpcClient getRpcClient() {
		return rpcClient;
	}

	public void setRpcClient(RpcClient rpcClient) {
		this.rpcClient = rpcClient;
	}

}
