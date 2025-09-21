package com.sqlparser.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SqlParserExtendedDdlDmlTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testMergeExtraction() throws Exception {
        String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                "WHEN MATCHED THEN UPDATE SET value = s.value " +
                "WHEN NOT MATCHED THEN INSERT (id, value) VALUES (s.id, s.value)";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("target"));
        assertTrue(tables.contains("source"));
    }

    @Test
    public void testAnalyzeExtraction() throws Exception {
        String sql = "ANALYZE users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testTableExecuteExtraction() throws Exception {
        String sql = "ALTER TABLE orders EXECUTE optimize WHERE order_date < DATE '2024-01-01'";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("orders"));
    }

    @Test
    public void testSetPropertiesExtraction() throws Exception {
        String sql = "ALTER TABLE users SET PROPERTIES retention = '7d'";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testCommentOnTableExtraction() throws Exception {
        String sql = "COMMENT ON TABLE users IS 'user table'";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testShowColumnsExtraction() throws Exception {
        String sql = "SHOW COLUMNS FROM users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testShowCreateTableExtraction() throws Exception {
        String sql = "SHOW CREATE TABLE users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testShowStatsForTableExtraction() throws Exception {
        String sql = "SHOW STATS FOR users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testCreateViewExtraction() throws Exception {
        String sql = "CREATE VIEW v AS SELECT * FROM users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("v")); // view name collected as object
    }

    @Test
    public void testRefreshMaterializedViewExtraction() throws Exception {
        String sql = "REFRESH MATERIALIZED VIEW mv_orders";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("mv_orders"));
    }

    @Test
    public void testGrantOnTableExtraction() throws Exception {
        String sql = "GRANT SELECT ON TABLE users TO ROLE analyst";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testRevokeOnTableExtraction() throws Exception {
        String sql = "REVOKE SELECT ON TABLE users FROM ROLE analyst";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }

    @Test
    public void testShowGrantsOnTableExtraction() throws Exception {
        String sql = "SHOW GRANTS ON TABLE users";
        Set<String> tables = sqlParserService.extractTableNames(sql);
        assertTrue(tables.contains("users"));
    }
}
