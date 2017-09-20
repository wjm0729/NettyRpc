package com.nettyrpc.client;

import java.util.concurrent.TimeUnit;

import com.nettyrpc.protocol.PingPongHandler;
import com.nettyrpc.protocol.RpcDecoder;
import com.nettyrpc.protocol.RpcEncoder;
import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Created by luxiaoxun on 2016-03-16.
 * @author jiangmin.wu
 */
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline cp = socketChannel.pipeline();
        cp.addLast(new RpcEncoder(RpcRequest.class));
        cp.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024 * 64, 1, 4, 0, 0));	
        cp.addLast(new RpcDecoder(RpcResponse.class));
        cp.addLast(new IdleStateHandler(60, 60, 60, TimeUnit.SECONDS));
        cp.addLast(new PingPongHandler());
        cp.addLast(new RpcClientHandler());
    }
}
