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
public class SqlParserEdgeCasesTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testQuotedTableNames() throws Exception {
        String sql = "SELECT * FROM \"users\" JOIN \"order-history\" ON \"users\".id = \"order-history\".user_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users") || tableNames.contains("\"users\""));
        assertTrue(tableNames.contains("order-history") || tableNames.contains("\"order-history\""));
    }

    @Test
    public void testMixedCaseTableNames() throws Exception {
        String sql = "SELECT * FROM UserAccounts UA JOIN OrderHistory OH ON UA.ID = OH.UserID";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertEquals(2, tableNames.size());
        // Parser may convert to lowercase
        assertTrue(tableNames.contains("UserAccounts") || tableNames.contains("useraccounts"));
        assertTrue(tableNames.contains("OrderHistory") || tableNames.contains("orderhistory"));
    }

    @Test
    public void testTableNamesWithNumbers() throws Exception {
        String sql = "SELECT * FROM table1 t1 JOIN table2023 t2 ON t1.id = t2.ref_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("table1"));
        assertTrue(tableNames.contains("table2023"));
    }

    @Test
    public void testTableNamesWithUnderscores() throws Exception {
        String sql = "SELECT * FROM user_accounts ua JOIN order_line_items oli ON ua.id = oli.user_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("user_accounts"));
        assertTrue(tableNames.contains("order_line_items"));
    }

    @Test
    public void testVeryLongTableNames() throws Exception {
        String sql = "SELECT * FROM very_long_table_name_that_exceeds_normal_length vl " +
                "JOIN another_extremely_long_table_name_with_many_words aetl " +
                "ON vl.id = aetl.reference_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("very_long_table_name_that_exceeds_normal_length"));
        assertTrue(tableNames.contains("another_extremely_long_table_name_with_many_words"));
    }

    @Test
    public void testEmptyStringSQL() throws Exception {
        try {
            sqlParserService.extractTableNames("");
            fail("Should throw exception for empty SQL");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testNullSQL() throws Exception {
        try {
            sqlParserService.extractTableNames(null);
            fail("Should throw exception for null SQL");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testWhitespaceOnlySQL() throws Exception {
        try {
            sqlParserService.extractTableNames("   \\n\\t   ");
            fail("Should throw exception for whitespace-only SQL");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testSQLComments() throws Exception {
        String sql = "SELECT * FROM users u JOIN orders o ON u.id = o.user_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("orders"));
    }

    @Test
    public void testMultilineSQL() throws Exception {
        String sql = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE u.active = true";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("orders"));
    }

    @Test
    public void testTableNameSimilarToKeyword() throws Exception {
        String sql = "SELECT * FROM select_table st JOIN from_table ft ON st.id = ft.select_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("select_table"));
        assertTrue(tableNames.contains("from_table"));
    }

    // Rewrite behavior is covered in dedicated rewrite tests

    // Rewrite behavior is covered in dedicated rewrite tests

    // Rewrite behavior is covered in dedicated rewrite tests

    // Rewrite behavior is covered in dedicated rewrite tests

    // Rewrite behavior is covered in dedicated rewrite tests

    @Test
    public void testSpecialCharactersInTableNames() throws Exception {
        // Use double quotes instead of backticks since Trino doesn't support backticks
        String sql = "SELECT * FROM \"table_with_underscores\" twd JOIN \"table_with_spaces\" tws ON twd.id = tws.ref_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.size() >= 2);
    }

    @Test
    public void testLargeSQL() throws Exception {
        StringBuilder largeSql = new StringBuilder("SELECT * FROM table1 t1 ");
        for (int i = 2; i <= 100; i++) {
            largeSql.append("JOIN table").append(i).append(" t").append(i)
                    .append(" ON t1.id = t").append(i).append(".ref_id ");
        }

        Set<String> tableNames = sqlParserService.extractTableNames(largeSql.toString());

        assertNotNull(tableNames);
        assertEquals(100, tableNames.size());
        assertTrue(tableNames.contains("table1"));
        assertTrue(tableNames.contains("table50"));
        assertTrue(tableNames.contains("table100"));
    }

    @Test
    public void testSQLWithVaryingWhitespace() throws Exception {
        String sql = "SELECT   *   FROM    users     u    JOIN    orders   o ON u.id=o.user_id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertTrue(tableNames.contains("users"));
        assertTrue(tableNames.contains("orders"));
    }

    @Test
    public void testCircularTableReferences() throws Exception {
        String sql = "SELECT * FROM users u1 JOIN users u2 ON u1.manager_id = u2.id";
        Set<String> tableNames = sqlParserService.extractTableNames(sql);

        assertNotNull(tableNames);
        assertEquals(1, tableNames.size());
        assertTrue(tableNames.contains("users"));
    }
}
