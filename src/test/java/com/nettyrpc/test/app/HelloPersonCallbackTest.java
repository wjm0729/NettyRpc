package com.nettyrpc.test.app;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.nettyrpc.client.AsyncRPCCallback;
import com.nettyrpc.client.ConnectManage;
import com.nettyrpc.client.RpcFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.Person;

/**
 * Created by luxiaoxun on 2016/3/17.
 */
public class HelloPersonCallbackTest {
    public static void main(String[] args) throws Throwable {
    	ConnectManage connectManage = new ConnectManage("192.168.1.105:4180", true);
		final RpcClient rpcClient = new RpcClient(connectManage);
		rpcClient.afterPropertiesSet();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        try {
            IAsyncObjectProxy client = rpcClient.createAsync(HelloPersonService.class);
            int num = 5;
            RpcFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
            helloPersonFuture.addCallback(new AsyncRPCCallback() {
                @Override
                public void success(Object result) {
                    List<Person> persons = (List<Person>) result;
                    for (int i = 0; i < persons.size(); ++i) {
                        System.out.println(persons.get(i));
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void fail(Exception e) {
                    System.out.println(e);
                    countDownLatch.countDown();
                }
            });

        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        rpcClient.stop();

        System.out.println("End");
    }
}
