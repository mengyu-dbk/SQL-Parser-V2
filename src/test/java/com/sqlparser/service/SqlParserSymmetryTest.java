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

/**
 * Test cases to verify symmetry between extract-tables and replace-tables:
 * Table names extracted from extract-tables should be directly usable as keys in replace-tables.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserSymmetryTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testExtractAndReplaceSymmetryWithQuotedIdentifier() throws Exception {
        // Step 1: Extract table names from SQL with quoted identifier
        String sql = "DELETE FROM \"chaintable.token.eth\" WHERE id = ?";
        
        Set<String> extractedTables = sqlParserService.extractTableNames(sql);
        
        assertNotNull(extractedTables);
        assertEquals("Should extract 1 table", 1, extractedTables.size());
        
        String extractedTableName = extractedTables.iterator().next();
        System.out.println("Extracted table name: '" + extractedTableName + "'");
        
        // Step 2: Use extracted table name directly in replace mapping
        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTableName, "new_table");
        
        String result = sqlParserService.replaceTableNames(sql, mapping);
        
        assertNotNull(result);
        String expected = "DELETE FROM new_table WHERE id = ?";
        assertEquals("Should replace using extracted table name", expected, result);
    }

    @Test
    public void testExtractAndReplaceSymmetryWithUnquotedIdentifier() throws Exception {
        String sql = "SELECT * FROM users WHERE id = 1";
        
        Set<String> extractedTables = sqlParserService.extractTableNames(sql);
        
        assertNotNull(extractedTables);
        assertEquals(1, extractedTables.size());
        
        String extractedTableName = extractedTables.iterator().next();
        System.out.println("Extracted table name: '" + extractedTableName + "'");
        
        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTableName, "user_accounts");
        
        String result = sqlParserService.replaceTableNames(sql, mapping);
        
        assertNotNull(result);
        assertEquals("SELECT * FROM user_accounts WHERE id = 1", result);
    }

    @Test
    public void testExtractAndReplaceSymmetryWithMultipleTables() throws Exception {
        String sql = "SELECT * FROM users u JOIN \"chaintable.token.eth\" t ON u.token_id = t.id";
        
        Set<String> extractedTables = sqlParserService.extractTableNames(sql);
        
        assertNotNull(extractedTables);
        assertEquals("Should extract 2 tables", 2, extractedTables.size());
        
        System.out.println("Extracted tables: " + extractedTables);
        
        // Build mapping using all extracted table names
        Map<String, String> mapping = new HashMap<>();
        for (String tableName : extractedTables) {
            if (tableName.equals("users")) {
                mapping.put(tableName, "user_accounts");
            } else if (tableName.equals("chaintable.token.eth")) {
                mapping.put(tableName, "token_table");
            }
        }
        
        String result = sqlParserService.replaceTableNames(sql, mapping);
        
        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts u JOIN token_table t ON u.token_id = t.id";
        assertEquals("Should replace all extracted tables", expected, result);
    }

    @Test
    public void testExtractAndReplaceSymmetryWithQualifiedName() throws Exception {
        String sql = "SELECT * FROM catalog.schema.products WHERE id = 1";

        Set<String> extractedTables = sqlParserService.extractTableNames(sql);

        assertNotNull(extractedTables);
        assertEquals(1, extractedTables.size());

        String extractedTableName = extractedTables.iterator().next();
        System.out.println("Extracted qualified table name: '" + extractedTableName + "'");

        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTableName, "new_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertEquals("SELECT * FROM new_table WHERE id = 1", result);
    }

    @Test
    public void testExtractAndReplaceSymmetryWithSpecialChars() throws Exception {
        String sql = "SELECT * FROM \"user-table\" WHERE id = 1";
        
        Set<String> extractedTables = sqlParserService.extractTableNames(sql);
        
        assertNotNull(extractedTables);
        assertEquals(1, extractedTables.size());
        
        String extractedTableName = extractedTables.iterator().next();
        System.out.println("Extracted table with special chars: '" + extractedTableName + "'");
        
        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTableName, "user_table");
        
        String result = sqlParserService.replaceTableNames(sql, mapping);
        
        assertNotNull(result);
        assertEquals("SELECT * FROM user_table WHERE id = 1", result);
    }

    @Test
    public void testExtractAndReplaceSymmetryComplexQuery() throws Exception {
        // Complex query with multiple table types
        // Note: CTE内的表不会被提取（按设计，CTEs 被跳过以保持不变）
        String sql = "WITH cte AS (SELECT * FROM \"chaintable.token.eth\" WHERE status = 'active') " +
                    "SELECT u.*, t.* FROM users u " +
                    "JOIN cte t ON u.token_id = t.id " +
                    "LEFT JOIN orders o ON u.id = o.user_id";

        Set<String> extractedTables = sqlParserService.extractTableNames(sql);

        assertNotNull(extractedTables);
        System.out.println("Extracted tables from complex query: " + extractedTables);

        // CTE内的表不会被提取，只提取主查询中的表
        assertTrue("Should extract users", extractedTables.contains("users"));
        assertTrue("Should extract orders", extractedTables.contains("orders"));
        // CTE 内的 chaintable.token.eth 不会被提取（因为 CTE 被跳过）

        // Build mapping for all extracted real tables (excluding CTE)
        Map<String, String> mapping = new HashMap<>();
        for (String tableName : extractedTables) {
            switch (tableName) {
                case "users":
                    mapping.put(tableName, "user_accounts");
                    break;
                case "orders":
                    mapping.put(tableName, "order_records");
                    break;
                // Skip CTEs like "cte" - they won't be replaced
            }
        }

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Verify that extracted table names work correctly
        assertTrue("Should replace users", result.contains("FROM user_accounts u"));
        assertTrue("Should replace orders", result.contains("JOIN order_records o"));
        // CTE content and CTE reference should remain unchanged
        assertTrue("CTE content should remain unchanged",
            result.contains("WITH cte AS (SELECT * FROM \"chaintable.token.eth\""));
        assertTrue("CTE reference should remain unchanged", result.contains("JOIN cte t"));
    }

    @Test
    public void testExtractReturnsUnquotedNames() throws Exception {
        // Verify that extract always returns unquoted names
        String sql1 = "SELECT * FROM \"users\"";
        String sql2 = "SELECT * FROM users";
        
        Set<String> tables1 = sqlParserService.extractTableNames(sql1);
        Set<String> tables2 = sqlParserService.extractTableNames(sql2);
        
        // Both should return the same unquoted name
        assertEquals("Quoted and unquoted should extract same name", tables1, tables2);
        assertEquals("Should extract 'users' without quotes", "users", tables1.iterator().next());
    }
}
