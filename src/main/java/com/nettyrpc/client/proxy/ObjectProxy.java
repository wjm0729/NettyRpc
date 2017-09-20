package com.nettyrpc.client.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.RpcClientHandler;
import com.nettyrpc.client.RpcClientHandlerWraper;
import com.nettyrpc.client.RpcFuture;
import com.nettyrpc.protocol.RpcRequest;

/**
 * Created by luxiaoxun on 2016-03-16.
 * @author jiangmin.wu
 */
public class ObjectProxy<T> implements InvocationHandler, IAsyncObjectProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectProxy.class);
    private Class<T> clazz;
    private RpcClient rpcClient;

    public ObjectProxy(Class<T> clazz, RpcClient rpcClient) {
        this.clazz = clazz;
        this.rpcClient = rpcClient;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy.equals(args[0]);
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }

        RpcRequest request = new RpcRequest();
        request.setRequestId(rpcClient.getRequestIDCreater().crreateID());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        // Debug
        LOGGER.debug(method.getDeclaringClass().getName());
        LOGGER.debug(method.getName());
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            LOGGER.debug(method.getParameterTypes()[i].getName());
        }
        if(args != null) {
        	for (int i = 0; i < args.length; ++i) {
                LOGGER.debug(args[i].toString());
            }
        }
        RpcClientHandlerWraper handlerWraper = rpcClient.chooseHandler();
		RpcClientHandler handler = handlerWraper.getHandler();
        RpcFuture rpcFuture = handler.sendRequest(request, rpcClient, handlerWraper.getRemotePeer());
        rpcClient.getChooseAddress().set(null);
        return rpcFuture.get();
    }

    @Override
	public RpcFuture call(String funcName, Object... args) throws InterruptedException {
    	RpcClientHandlerWraper handlerWraper = rpcClient.chooseHandler();
		RpcClientHandler handler = handlerWraper.getHandler();
		RpcRequest request = createRequest(this.clazz.getName(), funcName, args);
		RpcFuture rpcFuture = handler.sendRequest(request, rpcClient, handlerWraper.getRemotePeer());
		rpcClient.getChooseAddress().set(null);
		return rpcFuture;
	}

    private RpcRequest createRequest(String className, String methodName, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(rpcClient.getRequestIDCreater().crreateID());
        request.setClassName(className);
        request.setMethodName(methodName);
        request.setParameters(args);

        Class<?>[] parameterTypes = new Class[args.length];
        // Get the right class type
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = getClassType(args[i]);
        }
        request.setParameterTypes(parameterTypes);
//        Method[] methods = clazz.getDeclaredMethods();
//        for (int i = 0; i < methods.length; ++i) {
//            // Bug: if there are 2 methods have the same name
//            if (methods[i].getName().equals(methodName)) {
//                parameterTypes = methods[i].getParameterTypes();
//                request.setParameterTypes(parameterTypes); // get parameter types
//                break;
//            }
//        }

        LOGGER.debug(className);
        LOGGER.debug(methodName);
        for (int i = 0; i < parameterTypes.length; ++i) {
            LOGGER.debug(parameterTypes[i].getName());
        }
        if(args != null) {
        	for (int i = 0; i < args.length; ++i) {
                LOGGER.debug(args[i].toString());
            }
        }

        return request;
    }

    private Class<?> getClassType(Object obj) {
        Class<?> classType = obj.getClass();
        String typeName = classType.getName();
        switch (typeName){
            case "java.lang.Integer":
                return Integer.TYPE;
            case "java.lang.Long":
                return Long.TYPE;
            case "java.lang.Float":
                return Float.TYPE;
            case "java.lang.Double":
                return Double.TYPE;
            case "java.lang.Character":
                return Character.TYPE;
            case "java.lang.Boolean":
                return Boolean.TYPE;
            case "java.lang.Short":
                return Short.TYPE;
            case "java.lang.Byte":
                return Byte.TYPE;
        }

        return classType;
    }
    
    public String toString()
    {
      return clazz.getName() + " -> " + rpcClient.getConnectManage().getServiceAddress();
    }
}
