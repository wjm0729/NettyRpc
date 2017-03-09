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
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        
        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
//        byte[] data = new byte[dataLength];
//        in.readBytes(data);
        
        ByteBufferInputStream data = new ByteBufferInputStream(in.nioBuffer(in.readerIndex(), dataLength));
        
        Object obj = SerializationUtil.deserialize(data, genericClass);
        
        in.readerIndex(in.readerIndex() + dataLength);
        
        out.add(obj);
    }
}
