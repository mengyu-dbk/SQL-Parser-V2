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
public class SqlParserDmlDdlTest {

    @Autowired
    private SqlParserService sqlParserService;

    // INSERT
    @Test
    public void testInsertTableExtraction() throws Exception {
        String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
    }

    @Test
    public void testInsertFromSelectTableExtraction() throws Exception {
        String sql = "INSERT INTO archive_users (id, name, email) " +
                "SELECT user_id, username, email_address FROM active_users WHERE status = 'inactive'";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("archive_users"));
        assertTrue(tableNames.contains("active_users"));
    }

    // Note: current position-based rewriter does not rewrite INSERT target table

    // UPDATE
    @Test
    public void testUpdateTableExtraction() throws Exception {
        String sql = "UPDATE users SET email = 'newemail@example.com' WHERE id = 1";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
    }

    @Test
    public void testUpdateWithSubqueryTableExtraction() throws Exception {
        String sql = "UPDATE users SET last_order_date = (" +
                "SELECT MAX(order_date) FROM orders WHERE orders.user_id = users.id" +
                ") WHERE users.status = 'active'";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("orders"));
    }

    // Replacement behavior covered in dedicated rewrite tests

    // DELETE
    @Test
    public void testDeleteTableExtraction() throws Exception {
        String sql = "DELETE FROM users WHERE id = 1";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
    }

    @Test
    public void testDeleteWithSubqueryTableExtraction() throws Exception {
        String sql = "DELETE FROM users WHERE id IN (" +
                "SELECT user_id FROM inactive_accounts WHERE last_login < '2023-01-01'" +
                ")";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("inactive_accounts"));
    }

    // Replacement behavior covered in dedicated rewrite tests

    // CREATE / DROP
    @Test
    public void testCreateTableExtraction() throws Exception {
        String sql = "CREATE TABLE new_users (id INT, name VARCHAR(100), email VARCHAR(255))";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("new_users"));
    }

    @Test
    public void testCreateTableAsSelectExtraction() throws Exception {
        String sql = "CREATE TABLE backup_users AS SELECT * FROM users WHERE status = 'active'";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("backup_users"));
        assertTrue(tableNames.contains("users"));
    }

    // DDL replacement is not supported by current rewriter (target names)

    @Test
    public void testDropTableExtraction() throws Exception {
        String sql = "DROP TABLE old_users";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);
        assertNotNull(tableNames);
        assertTrue(tableNames.contains("old_users"));
    }

    // DDL replacement is not supported by current rewriter (target names)

    // ALTER Statement Tests
    @Test
    public void testAlterTableExtraction() throws Exception {
        String sql = "ALTER TABLE users ADD COLUMN phone VARCHAR(20)";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
    }

    // DDL replacement is not supported by current rewriter (target names)

    // TRUNCATE Statement Tests
    @Test
    public void testTruncateTableExtraction() throws Exception {
        String sql = "TRUNCATE TABLE temp_data";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("temp_data"));
    }

    // DDL replacement is not supported by current rewriter (target names)

    // Complex DML with Multiple Tables
    @Test
    public void testComplexUpdateWithJoinExtraction() throws Exception {
        // Trino doesn't support UPDATE with FROM/JOIN, so test a correlated subquery instead
        String sql = "UPDATE orders SET total_amount = (" +
                "SELECT SUM(price * quantity) FROM order_items " +
                "WHERE order_items.order_id = orders.id" +
                ") WHERE orders.status = 'pending'";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("orders"));
        assertTrue(tableNames.contains("order_items"));
    }

    @Test
    public void testComplexDeleteWithExistsExtraction() throws Exception {
        String sql = "DELETE FROM customers WHERE NOT EXISTS (" +
                "SELECT 1 FROM orders WHERE orders.customer_id = customers.id" +
                ") AND customer_id NOT IN (" +
                "SELECT customer_id FROM prospects WHERE status = 'active'" +
                ")";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("customers"));
        assertTrue(tableNames.contains("orders"));
        assertTrue(tableNames.contains("prospects"));
    }
}
