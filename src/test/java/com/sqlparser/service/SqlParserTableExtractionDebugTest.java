package com.sqlparser.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserTableExtractionDebugTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void debugTableExtraction() throws Exception {
        String sql = "SELECT * FROM users /* users table comment */ WHERE description = 'users data'";

        // 先看看哪些表名被提取出来了
        Set<String> extractedTables = sqlParserService.extractTableNames(sql);
        System.out.println("Extracted tables: " + extractedTables);

        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
        System.out.println("Contains 'users': " + result.contains("users"));
        System.out.println("Contains 'user_accounts': " + result.contains("user_accounts"));
    }
}