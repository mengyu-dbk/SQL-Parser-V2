package com.sqlparser.controller;

import com.sqlparser.model.ExtractTablesRequest;
import com.sqlparser.model.ExtractTablesResponse;
import com.sqlparser.model.ReplaceTablesRequest;
import com.sqlparser.model.ReplaceTablesResponse;
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
 * API-level tests to verify symmetry between extract-tables and replace-tables endpoints
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserSymmetryApiTest {

    @Autowired
    private SqlParserController controller;

    @Test
    public void testApiSymmetryWithQuotedIdentifier() {
        String sql = "DELETE FROM \"chaintable.token.eth\" WHERE id = ?";

        // Step 1: Extract table names via API
        ExtractTablesRequest extractReq = new ExtractTablesRequest();
        extractReq.setSql(sql);

        ExtractTablesResponse extractRes = controller.extractTables(extractReq).getBody();

        assertNotNull(extractRes);
        assertTrue(extractRes.isSuccess());
        assertNotNull(extractRes.getTableNames());
        assertEquals(1, extractRes.getTableNames().size());

        String extractedTableName = extractRes.getTableNames().iterator().next();
        System.out.println("API extracted: '" + extractedTableName + "'");
        assertEquals("Should extract unquoted name", "chaintable.token.eth", extractedTableName);

        // Step 2: Use extracted name directly in replace API
        ReplaceTablesRequest replaceReq = new ReplaceTablesRequest();
        replaceReq.setSql(sql);
        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTableName, "new_table");
        replaceReq.setTableMapping(mapping);

        ReplaceTablesResponse replaceRes = controller.replaceTables(replaceReq).getBody();

        assertNotNull(replaceRes);
        assertTrue(replaceRes.isSuccess());
        assertEquals("DELETE FROM new_table WHERE id = ?", replaceRes.getSql());
    }

    @Test
    public void testApiSymmetryWithMultipleTables() {
        String sql = "SELECT * FROM users u JOIN \"order.table\" o ON u.id = o.user_id";

        // Extract
        ExtractTablesRequest extractReq = new ExtractTablesRequest();
        extractReq.setSql(sql);
        ExtractTablesResponse extractRes = controller.extractTables(extractReq).getBody();

        assertNotNull(extractRes);
        assertTrue(extractRes.isSuccess());
        Set<String> extractedTables = extractRes.getTableNames();
        assertEquals(2, extractedTables.size());
        System.out.println("API extracted tables: " + extractedTables);

        // Replace using extracted names
        ReplaceTablesRequest replaceReq = new ReplaceTablesRequest();
        replaceReq.setSql(sql);
        Map<String, String> mapping = new HashMap<>();
        for (String tableName : extractedTables) {
            if (tableName.equals("users")) {
                mapping.put(tableName, "user_accounts");
            } else if (tableName.equals("order.table")) {
                mapping.put(tableName, "order_records");
            }
        }
        replaceReq.setTableMapping(mapping);

        ReplaceTablesResponse replaceRes = controller.replaceTables(replaceReq).getBody();

        assertNotNull(replaceRes);
        assertTrue(replaceRes.isSuccess());
        String expected = "SELECT * FROM user_accounts u JOIN order_records o ON u.id = o.user_id";
        assertEquals(expected, replaceRes.getSql());
    }

    @Test
    public void testApiSymmetryRoundTrip() {
        // Test that we can extract and replace in a round-trip
        String originalSql = "SELECT * FROM \"db.schema.users\" WHERE active = true";

        // Extract
        ExtractTablesRequest extractReq = new ExtractTablesRequest();
        extractReq.setSql(originalSql);
        ExtractTablesResponse extractRes = controller.extractTables(extractReq).getBody();

        assertNotNull(extractRes);
        assertTrue(extractRes.isSuccess());
        String extractedTable = extractRes.getTableNames().iterator().next();

        // Replace with same name (should produce identical SQL except quotes might be removed)
        ReplaceTablesRequest replaceReq = new ReplaceTablesRequest();
        replaceReq.setSql(originalSql);
        Map<String, String> mapping = new HashMap<>();
        mapping.put(extractedTable, extractedTable);
        replaceReq.setTableMapping(mapping);

        ReplaceTablesResponse replaceRes = controller.replaceTables(replaceReq).getBody();

        assertNotNull(replaceRes);
        assertTrue(replaceRes.isSuccess());
        // After replacement, the table name should be the same (unquoted)
        assertEquals("SELECT * FROM db.schema.users WHERE active = true", replaceRes.getSql());
    }
}
