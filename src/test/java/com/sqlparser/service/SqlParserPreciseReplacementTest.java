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
        // Table name in FROM clause should be replaced
        assertTrue(result.contains("user_accounts"));
        // But table names in string literals should remain unchanged
        assertTrue(result.contains("'This mentions users table and orders table'"));
        assertFalse(result.contains("user_accounts table"));
        assertFalse(result.contains("order_records table"));
    }

    @Test
    public void testPartialMatchProtection() throws Exception {
        String sql = "SELECT * FROM user_data ud, user_profiles up, users u WHERE ud.user_id = u.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Should not replace partial matches
        assertTrue(result.contains("user_data"));
        assertTrue(result.contains("user_profiles"));
        assertTrue(result.contains("users"));
        // "user" should not be replaced because it's not a complete word match
        assertFalse(result.contains("person_data"));
        assertFalse(result.contains("person_profiles"));
        assertFalse(result.contains("persons"));
    }

    @Test
    public void testCompleteWordMatching() throws Exception {
        String sql = "SELECT * FROM user u JOIN users_archive ua ON u.id = ua.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // "user" should be replaced as it's a complete word
        assertTrue(result.contains("person"));
        // "users_archive" should not be affected
        assertTrue(result.contains("users_archive"));
        assertFalse(result.contains("persons_archive"));
    }

    @Test
    public void testCaseSensitiveReplacement() throws Exception {
        String sql = "SELECT * FROM Users u JOIN USERS U2 ON u.id = U2.parent_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Users", "UserAccounts");
        mapping.put("USERS", "USER_RECORDS");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("UserAccounts"));
        assertTrue(result.contains("USER_RECORDS"));
        assertFalse(result.contains("Users"));
        assertFalse(result.contains("USERS"));
    }

    @Test
    public void testQuotedIdentifiers() throws Exception {
        String sql = "SELECT * FROM \"orders\" o JOIN \"products\" p ON o.product_id = p.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");
        mapping.put("products", "product_catalog");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Quoted identifiers should be preserved and not replaced
        assertTrue(result.contains("\"orders\""));
        assertTrue(result.contains("\"products\""));
        assertFalse(result.contains("order_records"));
        assertFalse(result.contains("product_catalog"));
    }

    @Test
    public void testSpecialCharactersInTableNames() throws Exception {
        String sql = "SELECT * FROM table_with_underscores t JOIN \"table-with-dashes\" d ON t.id = d.ref_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("table_with_underscores", "new_table_with_underscores");
        mapping.put("table-with-dashes", "new-table-with-dashes");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("new_table_with_underscores"));
        // Quoted identifiers should not be replaced
        assertTrue(result.contains("\"table-with-dashes\""));
        assertFalse(result.contains(" table_with_underscores "));
        assertFalse(result.contains("new-table-with-dashes"));
    }

    @Test
    public void testNumbersInTableNames() throws Exception {
        String sql = "SELECT * FROM table1 t1 JOIN table123 t2 ON t1.id = t2.ref_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("table1", "new_table_1");
        mapping.put("table123", "new_table_123");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("new_table_1"));
        assertTrue(result.contains("new_table_123"));
        assertFalse(result.contains("table1"));
        assertFalse(result.contains("table123"));
    }

    @Test
    public void testComplexQueryWithSubqueries() throws Exception {
        String sql = "SELECT u.name FROM users u WHERE u.id IN (SELECT user_id FROM orders WHERE total > 100)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("user_accounts"));
        assertTrue(result.contains("order_records"));
        assertFalse(result.contains("users"));
        assertFalse(result.contains("orders"));
    }

    @Test
    public void testDMLOperations() throws Exception {
        String sql1 = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
        String sql2 = "UPDATE users SET email = 'new@example.com' WHERE id = 1";
        String sql3 = "DELETE FROM users WHERE id = 1";

        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result1 = sqlParserService.replaceTableNames(sql1, mapping);
        String result2 = sqlParserService.replaceTableNames(sql2, mapping);
        String result3 = sqlParserService.replaceTableNames(sql3, mapping);

        assertTrue(result1.contains("user_accounts"));
        assertTrue(result2.contains("user_accounts"));
        assertTrue(result3.contains("user_accounts"));

        assertFalse(result1.contains("users"));
        assertFalse(result2.contains("users"));
        assertFalse(result3.contains("users"));
    }

    @Test
    public void testDDLOperations() throws Exception {
        String sql1 = "CREATE TABLE users (id INT, name VARCHAR(100))";
        String sql2 = "DROP TABLE users";
        String sql3 = "CREATE TABLE new_users AS SELECT * FROM users";

        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result1 = sqlParserService.replaceTableNames(sql1, mapping);
        String result2 = sqlParserService.replaceTableNames(sql2, mapping);
        String result3 = sqlParserService.replaceTableNames(sql3, mapping);

        assertTrue(result1.contains("user_accounts"));
        assertTrue(result2.contains("user_accounts"));
        assertTrue(result3.contains("user_accounts"));

        assertFalse(result1.contains("users"));
        assertFalse(result2.contains("users"));
        // result3 should have new_users unchanged and users replaced
        assertTrue(result3.contains("new_users"));
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
        assertTrue(result.contains("user_accounts"));
        assertTrue(result.contains("order_records"));
        assertTrue(result.contains("product_catalog"));

        assertFalse(result.contains("users"));
        assertFalse(result.contains("orders"));
        assertFalse(result.contains("products"));
    }
}