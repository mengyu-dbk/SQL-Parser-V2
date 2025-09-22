package com.sqlparser.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SimpleAstRewriteDebugTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void compareAllApproaches() throws Exception {
        String sql = "SELECT * FROM users WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        System.out.println("Original SQL: " + sql);
        String result = sqlParserService.replaceTableNames(sql, mapping);
        System.out.println("Rewritten Result: " + result);
    }

    @Test
    public void testComplexQuery() throws Exception {
        String sql = "WITH user_cte AS (SELECT * FROM users WHERE active = true) " +
                    "SELECT u.name, o.total FROM user_cte u JOIN orders o ON u.id = o.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        System.out.println("Original Complex SQL: " + sql);

        String result = sqlParserService.replaceTableNames(sql, mapping);
        System.out.println("Rewritten Result: " + result);
    }
}
