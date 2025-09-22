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
public class SqlParserEdgeCaseBoundaryTest {

    @Autowired
    private SqlParserService sqlParserService;

    // === 表名与SQL关键字同名的情况 ===

    @Test
    public void testTableNameSameAsKeyword() throws Exception {
        String sql = "SELECT * FROM \"order\" o WHERE o.status = 'PENDING'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("order", "order_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 引用的标识符不应该被替换 -> 结果应与输入完全一致
        assertEquals(sql, result);
    }

    @Test
    public void testKeywordAsTableNameUnquoted() throws Exception {
        // Note: Some keywords like 'select' cannot be used as unquoted table names in Trino
        String sql = "SELECT * FROM user_select us JOIN user_from uf ON us.id = uf.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("select", "select_table");
        mapping.put("from", "from_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 不应发生任何替换 -> 结果应与输入完全一致
        assertEquals(sql, result);
    }

    // === 表名出现在非表名位置的情况 ===

    @Test
    public void testTableNameInColumnNames() throws Exception {
        String sql = "SELECT users.users_id users, users.users_name FROM users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT user_accounts.users_id users, user_accounts.users_name FROM user_accounts";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameInFunctionNames() throws Exception {
        String sql = "SELECT COUNT(*), users_count() FROM users WHERE users_active = true";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT COUNT(*), users_count() FROM user_accounts WHERE users_active = true";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameInAliases() throws Exception {
        String sql = "SELECT * FROM customers c JOIN orders users ON c.id = users.customer_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM customers c JOIN order_records users ON c.id = users.customer_id";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameInStringLiteralsAndComments() throws Exception {
        String sql = "SELECT * FROM users /* users table comment */ WHERE description = 'users data'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts /* users table comment */ WHERE description = 'users data'";
        assertEquals(expected, result);
    }

    // === 复杂的SQL结构边界情况 ===

    @Test
    public void testTableNameInSubqueryAndCTE() throws Exception {
        String sql = "WITH users_cte AS (SELECT * FROM users WHERE active = true) " +
                    "SELECT * FROM users_cte JOIN orders ON users_cte.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        // 使用统一的AST+位置替换实现
        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "WITH users_cte AS (SELECT * FROM users WHERE active = true) " +
                "SELECT * FROM users_cte JOIN order_records ON users_cte.id = order_records.user_id";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameInWindowFunctions() throws Exception {
        String sql = "SELECT *, ROW_NUMBER() OVER (PARTITION BY users ORDER BY created_at) " +
                    "FROM users ORDER BY users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT *, ROW_NUMBER() OVER (PARTITION BY users ORDER BY created_at) " +
                "FROM user_accounts ORDER BY users";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameInCaseStatements() throws Exception {
        String sql = "SELECT CASE WHEN users = 'admin' THEN 'Administrator' ELSE users END " +
                    "FROM users WHERE users_type = 'regular'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT CASE WHEN users = 'admin' THEN 'Administrator' ELSE users END " +
                "FROM user_accounts WHERE users_type = 'regular'";
        assertEquals(expected, result);
    }

    // === 特殊字符和引用边界情况 ===

    @Test
    public void testTableNameWithSpecialCharactersInIdentifiers() throws Exception {
        String sql = "SELECT * FROM \"user-table\" ut JOIN \"user_archive\" ua ON ut.id = ua.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user-table", "new_user_table");
        mapping.put("user_archive", "new_user_archive");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 引用标识符不应该被替换 -> 完整语句不变
        assertEquals(sql, result);
    }

    @Test
    public void testMixedQuotedAndUnquotedTableNames() throws Exception {
        String sql = "SELECT * FROM users u JOIN \"users\" qu ON u.id = qu.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts u JOIN \"users\" qu ON u.id = qu.id";
        assertEquals(expected, result);
    }

    // === 数据类型和约束中的表名 ===

    @Test
    public void testTableNameInDataTypesAndConstraints() throws Exception {
        // Note: Trino doesn't support REFERENCES constraint, so we test DDL differently
        String sql1 = "CREATE TABLE orders (id INT, user_id INT)";
        String sql2 = "ALTER TABLE orders ADD COLUMN status VARCHAR(50)";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("orders", "order_records");

        String result1 = sqlParserService.replaceTableNames(sql1, mapping);
        String result2 = sqlParserService.replaceTableNames(sql2, mapping);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("CREATE TABLE order_records (id INT, user_id INT)", result1);
        assertEquals("ALTER TABLE order_records ADD COLUMN status VARCHAR(50)", result2);
    }

    // === JSON和数组操作中的表名 ===

    @Test
    public void testTableNameInJsonOperations() throws Exception {
        String sql = "SELECT data->'users' as users_data FROM events WHERE table_name = 'users'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("events", "event_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT data->'users' as users_data FROM event_records WHERE table_name = 'users'";
        assertEquals(expected, result);
    }

    // === 递归CTE和复杂查询 ===

    @Test
    public void testTableNameInRecursiveCTE() throws Exception {
        String sql = "WITH RECURSIVE user_hierarchy AS (" +
                    "  SELECT id, name, manager_id FROM users WHERE manager_id IS NULL " +
                    "  UNION ALL " +
                    "  SELECT u.id, u.name, u.manager_id FROM users u " +
                    "  JOIN user_hierarchy uh ON u.manager_id = uh.id" +
                    ") SELECT * FROM user_hierarchy";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        // 使用统一的AST+位置替换实现
        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 递归CTE内部不重写 -> 结果与输入一致
        assertEquals(sql, result);
    }

    // === 表名在不同SQL方言特性中 ===

    @Test
    public void testTableNameInArrayAndMapOperations() throws Exception {
        String sql = "SELECT arr[1] as indexed_value FROM data_table WHERE map_col['users'] = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("data_table", "data_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT arr[1] as indexed_value FROM data_records WHERE map_col['users'] = 'active'";
        assertEquals(expected, result);
    }

    // === 表名长度和特殊情况 ===

    @Test
    public void testVeryShortTableNames() throws Exception {
        String sql = "SELECT * FROM a JOIN b ON a.id = b.a_id WHERE a.status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("a", "table_a");
        mapping.put("b", "table_b");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM table_a JOIN table_b ON table_a.id = table_b.a_id WHERE table_a.status = 'active'";
        assertEquals(expected, result);
    }

    @Test
    public void testTableNameAsSubstringOfAnother() throws Exception {
        String sql = "SELECT * FROM user u JOIN users us ON u.id = us.user_id JOIN user_archive ua ON u.id = ua.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM person u JOIN users us ON u.id = us.user_id JOIN user_archive ua ON u.id = ua.user_id";
        assertEquals(expected, result);
    }

    // === 空白字符和格式边界情况 ===

    @Test
    public void testTableNameWithVariousWhitespaceAndFormatting() throws Exception {
        String sql = "SELECT\n  *\nFROM\n  users\n  u\nJOIN\n  orders    o\nON\n  u.id=o.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT\n  *\nFROM\n  user_accounts\n  u\nJOIN\n  order_records    o\nON\n  u.id=o.user_id";
        assertEquals(expected, result);
    }
}
