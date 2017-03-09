package com.nettyrpc.server;

import java.util.Map;

import com.nettyrpc.protocol.RpcRequest;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

public interface IRpcHandlerProxy {
	
	IRpcHandlerProxy DEFAULT = new IRpcHandlerProxy() {
		
		@Override
		public Object handle(Map<String, Object> handlerMap, RpcRequest request) throws Throwable {
			String className = request.getClassName();
	        Object serviceBean = handlerMap.get(className);

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
	
	Object handle(Map<String, Object> handlerMap, RpcRequest request) throws Throwable;
}
