package com.nettyrpc.client.proxy;

import com.nettyrpc.client.RPCFuture;

/**
 * Created by luxiaoxun on 2016/3/16.
 * @author jiangmin.wu
 */
public interface IAsyncObjectProxy {
	/**
	 * 异步调用
	 * 
	 * 请注意：远程服务的方法参数不能区分基础类型和包装类型的
	 * <pre>{@code
	 * public String fun(int p){
	 * 		return xxx;
	 * }
	 * 
	 * public String fun(Integer p){
	 * 		return yyy;
	 * }	 
	 * 
	 * 类似上述两个方法在异步调用时是无法区分的
	 * </pre>
	 * 
	 * @param funcName
	 * @param args
	 * @return
	 * @throws InterruptedException
	 */
    public RPCFuture call(String funcName, Object... args) throws InterruptedException;
}