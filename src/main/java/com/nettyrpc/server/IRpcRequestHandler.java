package com.nettyrpc.server;

import com.nettyrpc.protocol.RpcRequest;

import io.netty.channel.Channel;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

public interface IRpcRequestHandler {
	IRpcRequestHandler DEFAULT = new IRpcRequestHandler() {
		
		@Override
		public Object handle(Object serviceBean, RpcRequest request, Channel channel) throws Throwable {
	        Class<?> serviceClass = serviceBean.getClass();
	        String methodName = request.getMethodName();
	        Class<?>[] parameterTypes = request.getParameterTypes();
	        Object[] parameters = request.getParameters();

			return serviceClass.getMethod(methodName, parameterTypes).invoke(serviceBean, parameters);
	        // Cglib reflect
	        // FastClass serviceFastClass = FastClass.create(serviceClass);
	        // FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
	        // return serviceFastMethod.invoke(serviceBean, parameters);
		}
	};
	
	Object handle(Object serviceBean, RpcRequest request, Channel channel) throws Throwable;
}
