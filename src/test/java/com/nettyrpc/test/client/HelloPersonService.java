package com.nettyrpc.test.client;

import java.util.List;
import java.util.Map;

/**
 * Created by luxiaoxun on 2016-03-10.
 */
public interface HelloPersonService {
    List<Person> GetTestPerson(String name,int num);
    Map<String, Person> GetTestPerson2(String name, int num);
}
