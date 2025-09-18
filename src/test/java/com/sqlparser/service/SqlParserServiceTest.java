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
public class SqlParserServiceTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testExtractTableNames() throws Exception {
        String sql = "SELECT a.id, b.name FROM users a JOIN orders b ON a.id = b.user_id WHERE a.status = 'active'";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertEquals(2, tableNames.size());
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("orders"));
    }

    @Test
    public void testExtractTableNamesWithSubquery() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM users WHERE status = 'active') t1 JOIN products p ON t1.id = p.user_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("products"));
    }

    @Test
    public void testReplaceTableNames() throws Exception {
        String sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_table");
        mapping.put("orders", "order_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("user_table"));
        assertTrue(result.contains("order_table"));
        assertFalse(result.contains("users"));
        assertFalse(result.contains("orders"));
    }

    @Test
    public void testReplaceTableNamesPartial() throws Exception {
        String sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        assertTrue(result.contains("user_table"));
        assertTrue(result.contains("orders"));
        assertFalse(result.contains("users"));
    }
}