package com.sqlparser.service;

import com.sqlparser.model.RewriteInfo;
import com.sqlparser.visitor.TableNameExtractor;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SqlParserService {

    private static final Logger logger = LoggerFactory.getLogger(SqlParserService.class);

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
     * - Handles qualified names (schema.table) by extracting and replacing the last part
     * - Fallback anchored patterns for UPDATE/DELETE/MERGE targets when AST positions don't match
     */
    public String replaceTableNames(String sql, Map<String, String> tableMapping) throws Exception {
        logger.info("=== Starting table replacement ===");
        logger.info("Input SQL: {}", sql);
        logger.info("Table mapping: {}", tableMapping);

        Statement statement = sqlParser.createStatement(sql);
        extractor.collect(statement, sql);

        // Copy tokens and sort right-to-left
        List<TableNameExtractor.TableToken> tokens = new ArrayList<>(extractor.getTableTokens());
        logger.info("Extracted {} tokens from AST", tokens.size());
        for (TableNameExtractor.TableToken token : tokens) {
            logger.info("  Token: '{}' at position [{}:{}]", token.getText(), token.getStart(), token.getEnd());
        }

        tokens.sort(Comparator.comparingInt(TableNameExtractor.TableToken::getStart).reversed());
        logger.info("Sorted tokens (right-to-left) for stable replacement");

        StringBuilder result = new StringBuilder(sql);
        int replacementCount = 0;
        for (TableNameExtractor.TableToken tok : tokens) {
            String oldText = tok.getText();
            int start = tok.getStart();
            int end = Math.min(tok.getEnd(), result.length());

            logger.info("Processing token: '{}' at [{}:{}]", oldText, start, end);

            if (start < 0 || start >= end) {
                logger.warn("  ⚠️  Invalid position range, skipping");
                continue;
            }

            String actual = result.substring(start, end);
            logger.info("  Actual text at position: '{}'", actual);

            // Strip quotes from actual text if present for comparison
            String actualForComparison = actual;
            boolean isQuoted = false;
            if (actual.startsWith("\"") && actual.endsWith("\"") && actual.length() > 2) {
                actualForComparison = actual.substring(1, actual.length() - 1);
                isQuoted = true;
            }

            // Find replacement in mapping
            // First try exact case match with the actual text (with or without quotes)
            String replacement = tableMapping.get(actual);
            if (replacement == null && isQuoted) {
                // Try matching without quotes
                replacement = tableMapping.get(actualForComparison);
            }

            if (replacement == null) {
                // Then try exact case match with the normalized AST token text
                replacement = tableMapping.get(oldText);
            }

            if (replacement == null) {
                // Finally try case-insensitive lookup
                String finalActualForComparison = actualForComparison;
                replacement = tableMapping.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(finalActualForComparison))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            }

            logger.info("  Replacement lookup: actual='{}', actualForComparison='{}', oldText='{}' -> {}",
                actual, actualForComparison, oldText, replacement);

            if (replacement != null) {
                // Verify the actual text matches what we expect (case-insensitive, ignoring quotes)
                if (!actualForComparison.equalsIgnoreCase(oldText)) {
                    logger.warn("  ⏭️  Skipping: actualForComparison='{}' doesn't match oldText='{}'",
                        actualForComparison, oldText);
                    continue;
                }

                logger.info("  ✅ Replacing '{}' with '{}' at position [{}:{}]",
                    actual, replacement, start, end);
                result.replace(start, end, replacement);
                replacementCount++;
                logger.info("  Result after replacement: {}", result.toString());
            } else {
                logger.info("  ⏭️  Skipping replacement (no mapping found)");
            }
        }

        logger.info("Total replacements made: {}", replacementCount);
        logger.info("Final result: {}", result.toString());
        logger.info("=== Replacement complete ===");

        // Validate SQL is still parseable
        // sqlParser.createStatement(result.toString());
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
