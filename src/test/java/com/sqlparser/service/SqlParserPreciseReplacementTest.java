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
public class SqlParserPreciseReplacementTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testStringLiteralProtection() throws Exception {
        String sql = "SELECT * FROM users WHERE description = 'This mentions users table and orders table'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts WHERE description = 'This mentions users table and orders table'";
        assertEquals(expected, result);
    }

    @Test
    public void testPartialMatchProtection() throws Exception {
        String sql = "SELECT * FROM user_data ud, user_profiles up, users u WHERE ud.user_id = u.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // No replacements expected
        assertEquals(sql, result);
    }

    @Test
    public void testCompleteWordMatching() throws Exception {
        String sql = "SELECT * FROM user u JOIN users_archive ua ON u.id = ua.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM person u JOIN users_archive ua ON u.id = ua.user_id";
        assertEquals(expected, result);
    }

    @Test
    public void testCaseSensitiveReplacement() throws Exception {
        String sql = "SELECT * FROM Users u JOIN USERS U2 ON u.id = U2.parent_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Users", "UserAccounts");
        mapping.put("USERS", "USER_RECORDS");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM UserAccounts u JOIN USER_RECORDS U2 ON u.id = U2.parent_id";
        assertEquals(expected, result);
    }

    @Test
    public void testQuotedIdentifiers() throws Exception {
        String sql = "SELECT * FROM \"orders\" o JOIN \"products\" p ON o.product_id = p.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");
        mapping.put("products", "product_catalog");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Quoted identifiers should now be replaced if they're in the mapping
        String expected = "SELECT * FROM order_records o JOIN product_catalog p ON o.product_id = p.id";
        assertEquals(expected, result);
    }

    @Test
    public void testSpecialCharactersInTableNames() throws Exception {
        String sql = "SELECT * FROM table_with_underscores t JOIN \"table-with-dashes\" d ON t.id = d.ref_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("table_with_underscores", "new_table_with_underscores");
        mapping.put("table-with-dashes", "new-table-with-dashes");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Both unquoted and quoted identifiers should be replaced
        String expected = "SELECT * FROM new_table_with_underscores t JOIN new-table-with-dashes d ON t.id = d.ref_id";
        assertEquals(expected, result);
    }

    @Test
    public void testNumbersInTableNames() throws Exception {
        String sql = "SELECT * FROM table1 t1 JOIN table123 t2 ON t1.id = t2.ref_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("table1", "new_table_1");
        mapping.put("table123", "new_table_123");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM new_table_1 t1 JOIN new_table_123 t2 ON t1.id = t2.ref_id";
        assertEquals(expected, result);
    }

    @Test
    public void testComplexQueryWithSubqueries() throws Exception {
        String sql = "SELECT u.name FROM users u WHERE u.id IN (SELECT user_id FROM orders WHERE total > 100)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT u.name FROM user_accounts u WHERE u.id IN (SELECT user_id FROM order_records WHERE total > 100)";
        assertEquals(expected, result);
    }


    @Test
    public void testMultipleReplacements() throws Exception {
        String sql = "SELECT u.name, o.total, p.name FROM users u JOIN orders o ON u.id = o.user_id JOIN products p ON o.product_id = p.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");
        mapping.put("products", "product_catalog");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT u.name, o.total, p.name FROM user_accounts u JOIN order_records o ON u.id = o.user_id JOIN product_catalog p ON o.product_id = p.id";
        assertEquals(expected, result);
    }
}
