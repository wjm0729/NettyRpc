package com.nettyrpc.test.app;

import com.nettyrpc.client.AsyncRPCCallback;
import com.nettyrpc.client.RPCFuture;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.client.proxy.IAsyncObjectProxy;
import com.nettyrpc.registry.ServiceDiscovery;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.Person;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by luxiaoxun on 2016/3/17.
 */
public class HelloPersonCallbackTest {
    public static void main(String[] args) throws Throwable {
        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("10.1.6.72:2181", 10);
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);
        final CountDownLatch countDownLatch = new CountDownLatch(5);

        try {
            IAsyncObjectProxy client = rpcClient.createAsync(HelloPersonService.class);
            int num = 5;
            RPCFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
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
