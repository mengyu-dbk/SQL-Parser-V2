package com.sqlparser.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test cases specifically for verifying correct position-based table replacement
 * in statements where table.getLocation() returns incorrect positions.
 *
 * This addresses the issue where UPDATE, DELETE, and potentially MERGE statements
 * report table locations that point to statement keywords instead of actual table names.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserPositionFixTest {

    @Autowired
    private SqlParserService sqlParserService;

    // === UPDATE statement tests ===

    @Test
    public void testUpdateSimple() throws Exception {
        String sql = "UPDATE users SET status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("UPDATE user_accounts SET status = 'active'", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testUpdateWithWhere() throws Exception {
        String sql = "UPDATE orders SET status = 'cancelled' WHERE amount = 0";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("UPDATE order_records SET status = 'cancelled' WHERE amount = 0", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testUpdateWithSubquery() throws Exception {
        String sql = "UPDATE users SET status = 'inactive' WHERE id IN (SELECT user_id FROM orders WHERE amount = 0)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("UPDATE user_accounts SET status = 'inactive' WHERE id IN (SELECT user_id FROM order_records WHERE amount = 0)", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testUpdateMultipleTables() throws Exception {
        String sql = "UPDATE products SET available = false WHERE id IN (SELECT product_id FROM inventory WHERE warehouse = 'closed')";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("products", "product_catalog");
        mapping.put("inventory", "stock_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("UPDATE product_catalog SET available = false WHERE id IN (SELECT product_id FROM stock_records WHERE warehouse = 'closed')", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === DELETE statement tests ===

    @Test
    public void testDeleteSimple() throws Exception {
        String sql = "DELETE FROM users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("DELETE FROM user_accounts", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteWithWhere() throws Exception {
        String sql = "DELETE FROM orders WHERE status = 'cancelled'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("DELETE FROM order_records WHERE status = 'cancelled'", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteWithSubquery() throws Exception {
        String sql = "DELETE FROM users WHERE id IN (SELECT user_id FROM inactive_users)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("inactive_users", "inactive_user_list");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("DELETE FROM user_accounts WHERE id IN (SELECT user_id FROM inactive_user_list)", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteMultipleTables() throws Exception {
        String sql = "DELETE FROM products WHERE id IN (SELECT product_id FROM discontinued WHERE year < 2020)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("products", "product_catalog");
        mapping.put("discontinued", "discontinued_items");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("DELETE FROM product_catalog WHERE id IN (SELECT product_id FROM discontinued_items WHERE year < 2020)", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === MERGE statement tests ===

    @Test
    public void testMergeSimple() throws Exception {
        String sql = "MERGE INTO target USING source ON target.id = source.id " +
                    "WHEN MATCHED THEN UPDATE SET value = source.value";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("target", "target_table");
        mapping.put("source", "source_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // MERGE target should be replaced, as well as source
        String expected = "MERGE INTO target_table USING source_table ON target_table.id = source_table.id " +
                         "WHEN MATCHED THEN UPDATE SET value = source_table.value";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testMergeWithAlias() throws Exception {
        String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET value = s.value";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("target", "target_table");
        mapping.put("source", "source_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Aliases should be preserved
        String expected = "MERGE INTO target_table t USING source_table s ON t.id = s.id " +
                         "WHEN MATCHED THEN UPDATE SET value = s.value";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testMergeWithInsert() throws Exception {
        String sql = "MERGE INTO customers USING updates ON customers.id = updates.id " +
                    "WHEN MATCHED THEN UPDATE SET name = updates.name " +
                    "WHEN NOT MATCHED THEN INSERT (id, name) VALUES (updates.id, updates.name)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("customers", "customer_records");
        mapping.put("updates", "customer_updates");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "MERGE INTO customer_records USING customer_updates ON customer_records.id = customer_updates.id " +
                         "WHEN MATCHED THEN UPDATE SET name = customer_updates.name " +
                         "WHEN NOT MATCHED THEN INSERT (id, name) VALUES (customer_updates.id, customer_updates.name)";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testMergeWithSubquery() throws Exception {
        String sql = "MERGE INTO inventory USING (SELECT * FROM shipments WHERE status = 'received') s " +
                    "ON inventory.product_id = s.product_id " +
                    "WHEN MATCHED THEN UPDATE SET quantity = inventory.quantity + s.quantity";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("inventory", "stock_inventory");
        mapping.put("shipments", "incoming_shipments");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "MERGE INTO stock_inventory USING (SELECT * FROM incoming_shipments WHERE status = 'received') s " +
                         "ON stock_inventory.product_id = s.product_id " +
                         "WHEN MATCHED THEN UPDATE SET quantity = stock_inventory.quantity + s.quantity";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === TABLE EXECUTE tests ===

    @Test
    public void testTableExecuteSimple() throws Exception {
        String sql = "ALTER TABLE orders EXECUTE optimize";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("ALTER TABLE order_records EXECUTE optimize", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testTableExecuteWithWhere() throws Exception {
        String sql = "ALTER TABLE products EXECUTE optimize WHERE category = 'electronics'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("products", "product_catalog");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("ALTER TABLE product_catalog EXECUTE optimize WHERE category = 'electronics'", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testTableExecuteWithArguments() throws Exception {
        String sql = "ALTER TABLE logs EXECUTE expire_snapshots(retention_days => 7)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("logs", "system_logs");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("ALTER TABLE system_logs EXECUTE expire_snapshots(retention_days => 7)", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    // === Edge cases ===

    @Test
    public void testUpdateWithQualifiedTableName() throws Exception {
        String sql = "UPDATE schema.users SET status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("schema.users", "schema.user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Should handle qualified names
        assertEquals("UPDATE schema.user_accounts SET status = 'active'", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteWithQualifiedTableName() throws Exception {
        String sql = "DELETE FROM schema.orders WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("schema.orders", "schema.order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("DELETE FROM schema.order_records WHERE id = 1", result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testUpdateNoMapping() throws Exception {
        String sql = "UPDATE users SET status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        // No mapping for 'users'

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Should remain unchanged
        assertEquals(sql, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testDeleteNoMapping() throws Exception {
        String sql = "DELETE FROM users WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        // No mapping for 'users'

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Should remain unchanged
        assertEquals(sql, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }
}
