package com.nettyrpc.protocol;

import java.util.List;

import org.apache.zookeeper.server.ByteBufferInputStream;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * RPC Decoder
 * @author huangyong
 * @author jiangmin.wu
 */
public class RpcDecoder extends ByteToMessageDecoder {

    private Class<?> genericClass;

    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4 + 2) {
            return;
        }
        in.markReaderIndex();
        
        // 0000,0000 rpc 协议
        // 1000,0000 异步协议
        byte head = in.readByte();
        
        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        
        ByteBufferInputStream data = new ByteBufferInputStream(in.nioBuffer(in.readerIndex(), dataLength));
        
        Object obj = null;
        if((head & 0x80) == 0) {
        	obj = SerializationUtil.deserialize(data, genericClass);
        } else {
        	obj = SerializationUtil.deserialize(data, AsyncMessage.class);
        }
        in.readerIndex(in.readerIndex() + dataLength);
        out.add(obj);
    }
}
