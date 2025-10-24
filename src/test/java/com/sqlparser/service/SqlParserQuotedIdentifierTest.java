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
 * Test cases for quoted identifiers with special characters (dots, etc.)
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserQuotedIdentifierTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testQuotedIdentifierWithDots() throws Exception {
        String sql = "DELETE FROM \"chaintable.token.eth\" WHERE id = ? AND status = ?";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("chaintable.token.eth", "abc.\"123\"");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "DELETE FROM abc.\"123\" WHERE id = ? AND status = ?";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQuotedIdentifierWithDotsInSelect() throws Exception {
        String sql = "SELECT * FROM \"chaintable.token.eth\" WHERE status = 'active'";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("chaintable.token.eth", "new_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM new_table WHERE status = 'active'";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQuotedIdentifierWithDotsInUpdate() throws Exception {
        String sql = "UPDATE \"chaintable.token.eth\" SET status = 'inactive' WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("chaintable.token.eth", "updated_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "UPDATE updated_table SET status = 'inactive' WHERE id = 1";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQuotedIdentifierWithDotsInJoin() throws Exception {
        String sql = "SELECT * FROM \"chaintable.token.eth\" t1 JOIN \"other.table\" t2 ON t1.id = t2.ref_id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("chaintable.token.eth", "token_table");
        mapping.put("other.table", "other_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM token_table t1 JOIN other_table t2 ON t1.id = t2.ref_id";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testMixedQuotedAndUnquotedIdentifiers() throws Exception {
        String sql = "SELECT * FROM users u JOIN \"chaintable.token.eth\" t ON u.token_id = t.id";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("users", "user_accounts");
        mapping.put("chaintable.token.eth", "token_table");

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        String expected = "SELECT * FROM user_accounts u JOIN token_table t ON u.token_id = t.id";
        assertEquals(expected, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }

    @Test
    public void testQuotedIdentifierNotInMapping() throws Exception {
        String sql = "SELECT * FROM \"chaintable.token.eth\" WHERE id = 1";
        Map<String, String> mapping = new HashMap<>();
        // No mapping for this table

        String result = sqlParserService.replaceTableNames(sql, mapping);

        assertNotNull(result);
        // Should remain unchanged
        assertEquals(sql, result);
        assertTrue("Result should be valid SQL", sqlParserService.validateSql(result));
    }
}
