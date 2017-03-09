package com.nettyrpc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC Encoder
 * @author huangyong
 * @author jiangmin.wu
 */
public class RpcEncoder extends MessageToByteEncoder {

    private Class<?> genericClass;

    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if (genericClass.isInstance(in)) {
//            byte[] data = SerializationUtil.serialize(in);
//            out.writeInt(data.length);
//            out.writeBytes(data);
        	
        	ByteBuf buff = ctx.alloc().buffer();
        	SerializationUtil.serialize(in, buff);
        	out.writeInt(buff.readableBytes());
        	out.writeBytes(buff);
        	buff.release();
        }
    }
}
