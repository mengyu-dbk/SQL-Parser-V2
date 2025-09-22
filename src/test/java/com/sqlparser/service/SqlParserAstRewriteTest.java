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
        assertTrue("Should contain new table name", result.contains("user_accounts"));
        assertFalse("Should not contain old table name", result.contains("FROM users"));

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
        assertTrue("Should contain user_accounts", result.contains("user_accounts"));
        assertTrue("Should contain order_records", result.contains("order_records"));
        assertTrue("Should preserve aliases", result.contains(" u ") || result.contains(" u\n"));
        assertTrue("Should preserve aliases", result.contains(" o ") || result.contains(" o\n"));
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
        assertTrue("Should rewrite main table", result.contains("user_accounts"));
        assertTrue("Should rewrite subquery table", result.contains("order_records"));
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
        assertTrue("Should rewrite table in CTE", result.contains("user_accounts"));
        assertTrue("Should rewrite table in main query", result.contains("order_records"));
        assertTrue("Should preserve CTE name", result.contains("active_users"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === DML operation tests ===

    @Test
    public void testInsertRewrite() throws Exception {
        String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite target table", result.contains("user_accounts"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testInsertFromSelectRewrite() throws Exception {
        String sql = "INSERT INTO user_backup SELECT * FROM users WHERE created_date < '2023-01-01'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("user_backup", "user_backup_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite target table", result.contains("user_backup_table"));
        assertTrue("Should rewrite source table", result.contains("user_accounts"));
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
        assertTrue("Should rewrite main table", result.contains("user_accounts"));
        assertTrue("Should rewrite subquery table", result.contains("order_records"));
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
        assertTrue("Should rewrite main table", result.contains("user_accounts"));
        assertTrue("Should rewrite subquery table", result.contains("inactive_user_list"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === DDL operation tests ===

    @Test
    public void testCreateTableRewrite() throws Exception {
        String sql = "CREATE TABLE user_backup AS SELECT * FROM users WHERE status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("user_backup", "user_backup_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite new table name", result.contains("user_backup_table"));
        assertTrue("Should rewrite source table", result.contains("user_accounts"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDropTableRewrite() throws Exception {
        String sql = "DROP TABLE users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite table name", result.contains("user_accounts"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testCreateViewRewrite() throws Exception {
        String sql = "CREATE VIEW active_users_view AS SELECT * FROM users WHERE status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("active_users_view", "active_users_v");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite view name", result.contains("active_users_v"));
        assertTrue("Should rewrite source table", result.contains("user_accounts"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === Edge case tests ===

    @Test
    public void testTableNameInStringsAndComments() throws Exception {
        String sql = "SELECT * FROM users /* users table */ WHERE description = 'users data'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite table reference", result.contains("user_accounts"));
        // Comments and string literals behavior depends on SqlFormatter implementation
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQualifiedColumnReferences() throws Exception {
        String sql = "SELECT users.id, users.name FROM users WHERE users.status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite table in FROM", result.contains("user_accounts"));
        // Column qualifiers should be updated to match new table name
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testAliasesPreservation() throws Exception {
        String sql = "SELECT * FROM customers c JOIN orders users ON c.id = users.customer_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should rewrite table name", result.contains("order_records"));
        assertTrue("Should preserve alias 'users'", result.contains("users"));
        assertTrue("Should preserve alias 'c'", result.contains("c"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testNoRewriteWhenTableNotInMapping() throws Exception {
        String sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("products", "product_catalog"); // Different table

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue("Should preserve original table names", result.contains("users"));
        assertTrue("Should preserve original table names", result.contains("orders"));
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
        assertTrue("Should rewrite all tables", result.contains("order_records"));
        assertTrue("Should rewrite all tables", result.contains("user_accounts"));
        assertTrue("Should rewrite all tables", result.contains("product_catalog"));
        assertTrue("Should rewrite all tables", result.contains("user_prefs"));
        assertTrue("Should preserve CTE name", result.contains("recent_orders"));
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }
}
