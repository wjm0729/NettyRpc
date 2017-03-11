package com.nettyrpc.server;

import com.nettyrpc.protocol.RpcRequest;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

public interface IRpcHandlerDelegate {
	
	IRpcHandlerDelegate DEFAULT = new IRpcHandlerDelegate() {
		
		@Override
		public Object handle(Object serviceBean, RpcRequest request) throws Throwable {
	        Class<?> serviceClass = serviceBean.getClass();
	        String methodName = request.getMethodName();
	        Class<?>[] parameterTypes = request.getParameterTypes();
	        Object[] parameters = request.getParameters();

	        // Cglib reflect
	        FastClass serviceFastClass = FastClass.create(serviceClass);
	        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
	        return serviceFastMethod.invoke(serviceBean, parameters);
		}
	};
	
	Object handle(Object serviceBean, RpcRequest request) throws Throwable;
}
