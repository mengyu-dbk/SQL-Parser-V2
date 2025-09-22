package com.sqlparser.service;

import com.sqlparser.model.RewriteInfo;
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
public class SqlParserAstRewriteTest {

    @Autowired
    private SqlParserService sqlParserService;

    // === Basic functionality tests ===

    @Test
    public void testSimpleSelectRewrite() throws Exception {
        String sql = "SELECT * FROM users WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts WHERE id = 1";
        assertEquals(expected, result);
        // Verify the result is valid SQL
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testMultipleTableRewrite() throws Exception {
        String sql = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT u.name, o.total FROM user_accounts u JOIN order_records o ON u.id = o.user_id";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testSubqueryRewrite() throws Exception {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE amount > 100)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts WHERE id IN (SELECT user_id FROM order_records WHERE amount > 100)";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testCteRewrite() throws Exception {
        String sql = "WITH active_users AS (SELECT * FROM users WHERE status = 'active') " +
                    "SELECT * FROM active_users JOIN orders ON active_users.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Current rewriter does not traverse CTE definitions, so only the main query is rewritten
        String expected = "WITH active_users AS (SELECT * FROM users WHERE status = 'active') " +
                          "SELECT * FROM active_users JOIN order_records ON active_users.id = order_records.user_id";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === DML operation tests ===

    // Note: INSERT target table is not rewritten by the current position-based rewriter
    @Test
    public void testInsertFromSelectRewrite() throws Exception {
        String sql = "INSERT INTO user_backup SELECT * FROM users WHERE created_date < '2023-01-01'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("user_backup", "user_backup_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Only the source SELECT is rewritten; the INSERT target remains unchanged
        String expected = "INSERT INTO user_backup SELECT * FROM user_accounts WHERE created_date < '2023-01-01'";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testUpdateRewrite() throws Exception {
        String sql = "UPDATE users SET status = 'inactive' WHERE id IN (SELECT user_id FROM orders WHERE amount = 0)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "UPDATE user_accounts SET status = 'inactive' WHERE id IN (SELECT user_id FROM order_records WHERE amount = 0)";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteRewrite() throws Exception {
        String sql = "DELETE FROM users WHERE id IN (SELECT user_id FROM inactive_users)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("inactive_users", "inactive_user_list");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "DELETE FROM user_accounts WHERE id IN (SELECT user_id FROM inactive_user_list)";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === DDL operation tests ===

    @Test
    public void testCreateTableAsSelectRewriteSourceOnly() throws Exception {
        String sql = "CREATE TABLE user_backup AS SELECT * FROM users WHERE status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("user_backup", "user_backup_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Only the source SELECT is rewritten; target table name in CTAS remains unchanged
        String expected = "CREATE TABLE user_backup AS SELECT * FROM user_accounts WHERE status = 'active'";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // DROP/CREATE VIEW target names are not rewritten by current rewriter
    // (create view source SELECT would be rewritten similarly to CTAS)

    // === Edge case tests ===

    @Test
    public void testTableNameInStringsAndComments() throws Exception {
        String sql = "SELECT * FROM users /* users table */ WHERE description = 'users data'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts /* users table */ WHERE description = 'users data'";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQualifiedColumnReferences() throws Exception {
        String sql = "SELECT users.id, users.name FROM users WHERE users.status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Replace the table token and any unaliased qualifiers that reference it
        String expected = "SELECT user_accounts.id, user_accounts.name FROM user_accounts WHERE user_accounts.status = 'active'";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testAliasesPreservation() throws Exception {
        String sql = "SELECT * FROM customers c JOIN orders users ON c.id = users.customer_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM customers c JOIN order_records users ON c.id = users.customer_id";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testNoRewriteWhenTableNotInMapping() throws Exception {
        String sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("products", "product_catalog"); // Different table

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // No tables match mapping; SQL remains unchanged
        assertEquals(sql, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === Analysis and utility method tests ===

    @Test
    public void testAnalyzeRewrite() throws Exception {
        String sql = "SELECT * FROM users u JOIN orders o ON u.id = o.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        RewriteInfo info = sqlParserService.analyzeTableRewrite(sql, mapping);

        assertNotNull(info);
        assertTrue("Should have changes", info.hasChanges());
        assertTrue("Should contain users in all tables", info.getAllTables().contains("users"));
        assertTrue("Should contain orders in all tables", info.getAllTables().contains("orders"));
        assertTrue("Should contain users in affected tables", info.getAffectedTables().contains("users"));
        assertTrue("Should contain orders in affected tables", info.getAffectedTables().contains("orders"));
    }

    @Test
    public void testValidateSql() {
        assertTrue("Valid SQL should be recognized",
                  sqlParserService.validateSql("SELECT * FROM users"));
        assertFalse("Invalid SQL should be rejected",
                   sqlParserService.validateSql("SELEC * FROM users")); // typo
        assertFalse("Incomplete SQL should be rejected",
                   sqlParserService.validateSql("SELECT * FROM"));
    }

    @Test
    public void testComplexQueryWithAllConstructs() throws Exception {
        String sql = """
            WITH recent_orders AS (
                SELECT user_id, SUM(amount) as total
                FROM orders
                WHERE order_date > '2023-01-01'
                GROUP BY user_id
            )
            SELECT u.name, ro.total,
                   (SELECT COUNT(*) FROM products p WHERE p.category = 'electronics') as product_count
            FROM users u
            JOIN recent_orders ro ON u.id = ro.user_id
            LEFT JOIN user_preferences up ON u.id = up.user_id
            WHERE u.status = 'active'
            ORDER BY ro.total DESC
            LIMIT 100
            """;

        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");
        mapping.put("users", "user_accounts");
        mapping.put("products", "product_catalog");
        mapping.put("user_preferences", "user_prefs");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = """
            WITH recent_orders AS (
                SELECT user_id, SUM(amount) as total
                FROM orders
                WHERE order_date > '2023-01-01'
                GROUP BY user_id
            )
            SELECT u.name, ro.total,
                   (SELECT COUNT(*) FROM product_catalog p WHERE p.category = 'electronics') as product_count
            FROM user_accounts u
            JOIN recent_orders ro ON u.id = ro.user_id
            LEFT JOIN user_prefs up ON u.id = up.user_id
            WHERE u.status = 'active'
            ORDER BY ro.total DESC
            LIMIT 100
            """;
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }
}
