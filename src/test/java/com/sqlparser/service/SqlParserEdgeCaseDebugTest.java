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
public class SqlParserEdgeCaseDebugTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void debugTableNameInAliases() throws Exception {
        String sql = "SELECT * FROM customers c JOIN orders users ON c.id = users.customer_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);

        // 实际分析结果
        assertTrue("Should contain order_records", result.contains("order_records"));
        assertTrue("Should contain alias users", result.contains("users"));
    }

    @Test
    public void debugTableNameInWindowFunctions() throws Exception {
        String sql = "SELECT *, ROW_NUMBER() OVER (PARTITION BY users ORDER BY created_at) " +
                    "FROM users ORDER BY users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
    }

    @Test
    public void debugTableNameInCaseStatements() throws Exception {
        String sql = "SELECT CASE WHEN users = 'admin' THEN 'Administrator' ELSE users END " +
                    "FROM users WHERE users_type = 'regular'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        System.out.println("Original: " + sql);
        System.out.println("Result:   " + result);
    }
}