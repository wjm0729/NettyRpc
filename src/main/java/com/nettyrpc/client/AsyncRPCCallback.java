package com.nettyrpc.client;

/**
 * Created by luxiaoxun on 2016-03-17.
 * @author jiangmin.wu
 */
public interface AsyncRPCCallback {

    void success(Object result);

    void fail(Exception e);

}
