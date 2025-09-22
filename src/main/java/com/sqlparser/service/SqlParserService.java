package com.sqlparser.service;

import com.sqlparser.model.RewriteInfo;
import com.sqlparser.visitor.TableNameExtractor;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SqlParserService {

    private final SqlParser sqlParser;
    private final TableNameExtractor extractor;

    public SqlParserService() {
        this.sqlParser = new SqlParser();
        this.extractor = new TableNameExtractor();
    }

    public Set<String> extractTableNames(String sql) throws Exception {
        Statement statement = sqlParser.createStatement(sql);
        extractor.collect(statement, sql);
        return extractor.getTableNames();
    }

    /**
     * Rewrites table names using AST + precise token positions captured by TableNameExtractor.
     * - Right-to-left replacements keep offsets stable
     * - Case-aware mapping: prefer exact-token mapping, fallback to lower-cased key
     * - Fallback anchored patterns for UPDATE/DELETE/MERGE targets when AST positions don't match
     */
    public String replaceTableNames(String sql, Map<String, String> tableMapping) throws Exception {
        Statement statement = sqlParser.createStatement(sql);
        extractor.collect(statement, sql);

        // Copy tokens and sort right-to-left
        List<TableNameExtractor.TableToken> tokens = new ArrayList<>(extractor.getTableTokens());
        tokens.sort(Comparator.comparingInt(TableNameExtractor.TableToken::getStart).reversed());

        // no-op
        StringBuilder result = new StringBuilder(sql);
        for (TableNameExtractor.TableToken tok : tokens) {
            String oldText = tok.getText();
            int start = tok.getStart();
            int end = Math.min(tok.getEnd(), result.length());
            if (start < 0 || start >= end) continue;

            String actual = result.substring(start, end);
            String replacement = tableMapping.getOrDefault(actual, tableMapping.get(oldText));

            if (replacement != null && actual.equalsIgnoreCase(oldText)) {
                result.replace(start, end, replacement);
            } else if (replacement != null) {
                // Try anchored fallback for DML targets (UPDATE/DELETE/MERGE)
                String updated = tryDmlAnchoredReplacement(result.toString(), oldText, replacement);
                if (!updated.equals(result.toString())) {
                    result.setLength(0);
                    result.append(updated);
                }
            }
        }

        // Validate SQL is still parseable
        sqlParser.createStatement(result.toString());
        return result.toString();
    }

    /**
     * Analyze which tables would be affected by a rewrite without modifying SQL.
     */
    public RewriteInfo analyzeTableRewrite(String sql, Map<String, String> tableMapping) {
        try {
            Statement statement = sqlParser.createStatement(sql);
            extractor.collect(statement, sql);
            Set<String> all = extractor.getTableNames();
            List<String> affected = all.stream().filter(tableMapping::containsKey).toList();
            return new RewriteInfo(all, affected, tableMapping);
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze SQL for rewrite: " + sql, e);
        }
    }

    /**
     * Validates that the given SQL is syntactically correct and parseable.
     */
    public boolean validateSql(String sql) {
        try {
            sqlParser.createStatement(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Attempt to replace DML target tables with strict, anchored patterns while preserving formatting
    private String tryDmlAnchoredReplacement(String sql, String oldTable, String newTable) {
        String updated = replaceFirstAnchored(sql, "\\bUPDATE\\s+", oldTable, newTable);
        if (!updated.equals(sql)) return updated;

        updated = replaceFirstAnchored(sql, "\\bDELETE\\s+FROM\\s+", oldTable, newTable);
        if (!updated.equals(sql)) return updated;

        updated = replaceFirstAnchored(sql, "\\bMERGE\\s+INTO\\s+", oldTable, newTable);
        return updated;
    }

    private String replaceFirstAnchored(String sql, String anchorRegex, String oldTable, String newTable) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(anchorRegex + java.util.regex.Pattern.quote(oldTable) + "\\b");
        java.util.regex.Matcher m = p.matcher(sql);
        if (!m.find()) return sql;
        int tableStart = m.start() + m.group(0).length() - oldTable.length();
        int tableEnd = tableStart + oldTable.length();
        StringBuilder sb = new StringBuilder(sql);
        sb.replace(tableStart, tableEnd, newTable);
        return sb.toString();
    }
}
