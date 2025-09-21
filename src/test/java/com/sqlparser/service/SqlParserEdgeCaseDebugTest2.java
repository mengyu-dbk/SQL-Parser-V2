package com.sqlparser.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserEdgeCaseDebugTest2 {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void debugColumnNamesTest() throws Exception {
        String sql = "SELECT users.users_id, users.users_name FROM users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
    }

    @Test
    public void debugCommentsTest() throws Exception {
        String sql = "SELECT * FROM users /* users table comment */ WHERE description = 'users data'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
    }

    @Test
    public void debugShortTableNameTest() throws Exception {
        String sql = "SELECT * FROM a JOIN b ON a.id = b.a_id WHERE a.status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("a", "table_a");
        mapping.put("b", "table_b");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
    }
}