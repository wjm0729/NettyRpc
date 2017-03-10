package com.nettyrpc.protocol;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtobufIOUtil;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.runtime.RuntimeSchema;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

/**
 * Serialization Util（Based on Protostuff）
 * 
 * @author huangyong
 * @author jiangmin.wu
 */
public class SerializationUtil {
	// 默认使用 protostuff
	private static SerializeType SERIALIZE_TYPE = SerializeType.valueOf(System.getProperty("rpc.serialize.type", "PROTOSTUFF"));

	public enum SerializeType {
		PROTOSTUFF, PROTOBUF
	}

	private static Map<Class<?>, Schema<?>> cachedSchema = new ConcurrentHashMap<>();

	private static ThreadLocal<LinkedBuffer> linkedBufferTLocal = new ThreadLocal<LinkedBuffer>() {
		protected LinkedBuffer initialValue() {
			return LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
		}
	};

	private static Objenesis objenesis = new ObjenesisStd(true);

	private SerializationUtil() {
	}

	@SuppressWarnings("unchecked")
	static <T> Schema<T> getSchema(Class<T> cls) {
		Schema<T> schema = (Schema<T>) cachedSchema.get(cls);
		if (schema == null) {
			schema = RuntimeSchema.createFrom(cls);
			if (schema != null) {
				cachedSchema.put(cls, schema);
			}
		}
		return schema;
	}

	/**
	 * 序列化（对象 -> 字节数组）
	 * 
	 * 不推荐此方法, 会多new一次byte[]
	 */
	@SuppressWarnings("unchecked")
	public static <T> byte[] serialize(T obj) {
		Class<T> cls = (Class<T>) obj.getClass();
		LinkedBuffer buffer = linkedBufferTLocal.get();
		try {
			Schema<T> schema = getSchema(cls);
			if (SERIALIZE_TYPE == SerializeType.PROTOBUF) {
				return ProtobufIOUtil.toByteArray(obj, schema, buffer);
			} else {
				return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
			}
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		} finally {
			buffer.clear();
		}
	}

	/**
	 * 序列化（对象 -> ByteBuf）
	 */
	@SuppressWarnings("unchecked")
	public static <T> void serialize(T obj, ByteBuf buff) {
		Class<T> cls = (Class<T>) obj.getClass();
		LinkedBuffer buffer = linkedBufferTLocal.get();
		try {
			Schema<T> schema = getSchema(cls);
			ByteBufOutputStream out = new ByteBufOutputStream(buff);
			if (SERIALIZE_TYPE == SerializeType.PROTOBUF) {
				ProtobufIOUtil.writeTo(out, obj, schema, buffer);
			} else {
				ProtostuffIOUtil.writeTo(out, obj, schema, buffer);
			}
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		} finally {
			buffer.clear();
		}
	}

	/**
	 * 反序列化（字节数组 -> 对象）
	 */
	public static <T> T deserialize(byte[] data, Class<T> cls) {
		try {
			T message = (T) objenesis.newInstance(cls);
			Schema<T> schema = getSchema(cls);
			if (SERIALIZE_TYPE == SerializeType.PROTOBUF) {
				ProtobufIOUtil.mergeFrom(data, message, schema);
			} else {
				ProtostuffIOUtil.mergeFrom(data, message, schema);
			}
			return message;
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	/**
	 * 反序列化（字节数组 -> 对象）
	 */
	public static <T> T deserialize(InputStream data, Class<T> cls) {
		try {
			T message = (T) objenesis.newInstance(cls);
			Schema<T> schema = getSchema(cls);
			if (SERIALIZE_TYPE == SerializeType.PROTOBUF) {
				ProtobufIOUtil.mergeFrom(data, message, schema);
			} else {
				ProtostuffIOUtil.mergeFrom(data, message, schema);
			}
			return message;
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
}
