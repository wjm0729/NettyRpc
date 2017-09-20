package com.nettyrpc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC Encoder
 * 
 * @author huangyong
 * @author jiangmin.wu
 */
public class RpcEncoder extends MessageToByteEncoder<Object> {

	private Class<?> genericClass;

	public RpcEncoder(Class<?> genericClass) {
		this.genericClass = genericClass;
	}

	@Override
	public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {

		ByteBuf buff = ctx.alloc().buffer();
		SerializationUtil.serialize(in, buff);

		if (AsyncMessage.class.isInstance(in)) {
			out.writeByte(0x80);// head
		} else if (genericClass.isInstance(in)) {
			out.writeByte(0x00);// head
		}

		out.writeInt(buff.readableBytes());
		out.writeBytes(buff);
		buff.release();

	}
}
