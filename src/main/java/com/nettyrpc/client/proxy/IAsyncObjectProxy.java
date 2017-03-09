package com.nettyrpc.client.proxy;

import com.nettyrpc.client.RPCFuture;

/**
 * Created by luxiaoxun on 2016/3/16.
 * @author jiangmin.wu
 */
public interface IAsyncObjectProxy {
    public RPCFuture call(String funcName, Object... args) throws InterruptedException;
}