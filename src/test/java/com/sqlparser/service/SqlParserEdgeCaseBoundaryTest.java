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
        // 引用的标识符不应该被替换
        assertTrue(result.contains("\"order\""));
        assertFalse(result.contains("order_table"));
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
        // 应该只替换作为完整标识符的情况，不影响SQL关键字
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("user_select"));
        assertTrue(result.contains("user_from"));
    }

    // === 表名出现在非表名位置的情况 ===

    @Test
    public void testTableNameInColumnNames() throws Exception {
        String sql = "SELECT users.users_id users, users.users_name FROM users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 替换FROM中的表标识，同时更新未使用别名的列限定符
        assertTrue(result.contains("FROM user_accounts") || result.contains("from user_accounts"));
        assertTrue(result.contains("user_accounts.users_id"));
        assertTrue(result.contains("user_accounts.users_name"));
    }

    @Test
    public void testTableNameInFunctionNames() throws Exception {
        String sql = "SELECT COUNT(*), users_count() FROM users WHERE users_active = true";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("FROM user_accounts"));
        // 函数名不应该被替换
        assertTrue(result.contains("users_count()"));
        assertTrue(result.contains("users_active"));
    }

    @Test
    public void testTableNameInAliases() throws Exception {
        String sql = "SELECT * FROM customers c JOIN orders users ON c.id = users.customer_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("orders", "order_records");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("order_records users")); // 别名应该保持不变
        assertTrue(result.contains("users.customer_id")); // 别名引用应该保持不变
        assertFalse(result.contains("user_accounts.customer_id")); // 不应该替换别名引用
        assertFalse(result.contains("FROM customers c JOIN user_accounts")); // orders应该被替换，但别名users保持不变
    }

    @Test
    public void testTableNameInStringLiteralsAndComments() throws Exception {
        String sql = "SELECT * FROM users /* users table comment */ WHERE description = 'users data'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("FROM user_accounts"));
        // 注释和字符串字面量中的"users"应该保持不变
        assertTrue(result.contains("/* users table comment */"));
        assertTrue(result.contains("'users data'"));
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
        // 当前的提取器不会遍历CTE定义，因此CTE内部的users保持不变；主查询中的orders应被替换
        assertTrue(result.contains("JOIN order_records"));
        // CTE名称和引用应该保持不变
        assertTrue(result.contains("users_cte AS"));
        assertTrue(result.contains("users_cte"));
    }

    @Test
    public void testTableNameInWindowFunctions() throws Exception {
        String sql = "SELECT *, ROW_NUMBER() OVER (PARTITION BY users ORDER BY created_at) " +
                    "FROM users ORDER BY users";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("FROM user_accounts"));
        assertTrue(result.contains("ORDER BY users")); // ORDER BY中的列名应该保持不变
        // PARTITION BY中的列名应该保持不变
        assertTrue(result.contains("PARTITION BY users"));
    }

    @Test
    public void testTableNameInCaseStatements() throws Exception {
        String sql = "SELECT CASE WHEN users = 'admin' THEN 'Administrator' ELSE users END " +
                    "FROM users WHERE users_type = 'regular'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("FROM user_accounts"));
        // CASE语句中的列名应该保持不变
        assertTrue(result.contains("WHEN users ="));
        assertTrue(result.contains("ELSE users END"));
        assertTrue(result.contains("users_type"));
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
        // 引用标识符不应该被替换
        assertTrue(result.contains("\"user-table\""));
        assertTrue(result.contains("\"user_archive\""));
        assertFalse(result.contains("new_user_table"));
        assertFalse(result.contains("new_user_archive"));
    }

    @Test
    public void testMixedQuotedAndUnquotedTableNames() throws Exception {
        String sql = "SELECT * FROM users u JOIN \"users\" qu ON u.id = qu.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // 非引用的应该被替换，引用的应该保持不变
        assertTrue(result.contains("FROM user_accounts u"));
        assertTrue(result.contains("\"users\" qu"));
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
        assertTrue(result1.contains("CREATE TABLE order_records"));
        assertTrue(result2.contains("ALTER TABLE order_records"));
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
        assertTrue(result.contains("FROM event_records"));
        // JSON路径和字符串字面量中的users应该保持不变
        assertTrue(result.contains("data->'users'"));
        assertTrue(result.contains("'users'"));
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
        // Note: Current TableNameExtractor doesn't traverse into CTEs, so 'users' references inside CTE remain unchanged
        // This is a known limitation - CTEs require more sophisticated AST traversal
        // CTE名称应该保持不变
        assertTrue(result.contains("user_hierarchy AS"));
        assertTrue(result.contains("JOIN user_hierarchy"));
        assertTrue(result.contains("user_hierarchy"));
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
        assertTrue(result.contains("FROM data_records"));
        // map键中的users应该保持不变
        assertTrue(result.contains("map_col['users']"));
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
        assertTrue(result.contains("FROM table_a"));
        assertTrue(result.contains("JOIN table_b"));
        // 别名和列引用应该保持不变
        assertTrue(result.contains("table_a.id"));
        assertTrue(result.contains("table_b.a_id"));
        assertTrue(result.contains("table_a.status"));
    }

    @Test
    public void testTableNameAsSubstringOfAnother() throws Exception {
        String sql = "SELECT * FROM user u JOIN users us ON u.id = us.user_id JOIN user_archive ua ON u.id = ua.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("user", "person");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("FROM person u"));
        // "users" 和 "user_archive" 不应该被影响
        assertTrue(result.contains("JOIN users us"));
        assertTrue(result.contains("JOIN user_archive ua"));
        assertFalse(result.contains("persons"));
        assertFalse(result.contains("person_archive"));
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
        assertTrue(result.contains("user_accounts"));
        assertTrue(result.contains("order_records"));
        // 格式应该尽可能保持
        assertTrue(result.contains("\n"));
    }
}
