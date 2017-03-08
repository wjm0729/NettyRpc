package com.nettyrpc.test.server;

import com.nettyrpc.server.RpcService;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by luxiaoxun on 2016-03-10.
 */
@RpcService(HelloPersonService.class)
public class HelloPersonServiceImpl implements HelloPersonService {

    @Override
    public List<Person> GetTestPerson(String name, int num) {
        List<Person> persons = new ArrayList<>(num);
        for (int i = 0; i < num; ++i) {
            persons.add(new Person(Integer.toString(i), name));
        }
        System.err.println("~~~~~~~~~~~~~~~~~~~~~"+name);
        return persons;
    }
}
