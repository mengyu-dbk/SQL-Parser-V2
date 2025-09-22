package com.sqlparser.visitor;

import com.sqlparser.model.RewriteInfo;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Position-based table name rewriter that uses AST to get exact character positions
 * of table references and performs precise replacements. The original SQL text
 * (including comments and formatting) is preserved except for the exact table tokens.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionBasedTableRewriter {

    private static final Logger log = LoggerFactory.getLogger(PositionBasedTableRewriter.class);

    private final SqlParser sqlParser;
    private final TablePositionExtractor positionExtractor;
    private final TableNameExtractor nameExtractor;

    public PositionBasedTableRewriter() {
        this.sqlParser = new SqlParser();
        this.positionExtractor = new TablePositionExtractor();
        this.nameExtractor = new TableNameExtractor();
    }

    /**
     * Rewrites table names using precise position-based replacement.
     */
    public String rewriteTableNames(String sql, Map<String, String> tableMapping) {
        try {
            // Parse SQL to get AST
            Statement statement = sqlParser.createStatement(sql);

            // Extract table positions from AST
            positionExtractor.reset();
            positionExtractor.process(statement);
            var lineColumnPositions = positionExtractor.getTablePositions();
            if (log.isDebugEnabled()) {
                log.debug("Raw positions: {}", lineColumnPositions);
            }

            // Convert line/column positions to character positions
            var characterPositions = TablePositionExtractor.calculateCharacterPositions(sql, lineColumnPositions);

            // Filter positions to only include tables we want to replace
            // Keep only the tables that have a mapping. Positions come from AST,
            // so there's no risk of touching strings/comments.
            var filteredPositions = characterPositions; // decide per-position during replacement

            // Perform replacements from right to left to maintain position accuracy
            if (log.isDebugEnabled()) {
                log.debug("Rewrite candidates: {}", filteredPositions);
            }

            String result = replaceByPositions(sql, filteredPositions, tableMapping);

            // Validate the result by parsing it (but preserve original formatting in output)
            sqlParser.createStatement(result);
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite table names using position-based approach: " + sql, e);
        }
    }

    /**
     * Performs the actual replacements using character positions.
     * Replacements are done from right to left to maintain position accuracy.
     */
    private String replaceByPositions(String sql, List<TablePositionExtractor.TablePosition> positions,
                                    Map<String, String> tableMapping) {
        // Sort positions by start position in descending order (right to left)
        positions.sort((a, b) -> Integer.compare(b.getStartPosition(), a.getStartPosition()));

        StringBuilder result = new StringBuilder(sql);

        for (TablePositionExtractor.TablePosition pos : positions) {
            String oldTable = pos.getTableName();

            int start = pos.getStartPosition();
            int end = Math.min(start + oldTable.length(), result.length());

                // Verify the content at this position matches our expectation
                if (start >= 0 && end <= result.length()) {
                    String actualContent = result.substring(start, end);
                    // Prefer exact-case mapping when available, otherwise fall back to normalized key
                    String replacement = tableMapping.getOrDefault(actualContent, tableMapping.get(oldTable));
                    if (replacement != null && actualContent.equalsIgnoreCase(oldTable)) {
                        result.replace(start, end, replacement);
                        log.debug("Replaced '{}' -> '{}' at [{}:{}]", actualContent, replacement, start, end);
                    } else {
                        // Log a hint to help diagnose position mismatches during tests
                        log.debug("Skip replacement: expected '{}' (or case variant) at [{}:{}], found '{}'", oldTable, start, end, actualContent);
                        // Fallback for DML targets (UPDATE/DELETE) where Trino may not attach
                        // precise locations to the table token. Try to replace using an anchored pattern.
                        String updated = tryDmlAnchoredReplacement(result.toString(), oldTable, replacement);
                        if (!updated.equals(result.toString())) {
                            result.setLength(0);
                            result.append(updated);
                }
            }
        }
        }

        return result.toString();
    }

    // Attempt to replace DML target tables with strict, anchored patterns while preserving formatting
    private String tryDmlAnchoredReplacement(String sql, String oldTable, String newTable) {
        // UPDATE <table>
        String updated = replaceFirstAnchored(sql, "\\bUPDATE\\s+", oldTable, newTable);
        if (!updated.equals(sql)) {
            log.debug("Applied fallback UPDATE replacement for table '{}'", oldTable);
            return updated;
        }
        // DELETE FROM <table>
        updated = replaceFirstAnchored(sql, "\\bDELETE\\s+FROM\\s+", oldTable, newTable);
        if (!updated.equals(sql)) {
            log.debug("Applied fallback DELETE replacement for table '{}'", oldTable);
            return updated;
        }
        // MERGE INTO <table>
        updated = replaceFirstAnchored(sql, "\\bMERGE\\s+INTO\\s+", oldTable, newTable);
        if (!updated.equals(sql)) {
            log.debug("Applied fallback MERGE replacement for table '{}'", oldTable);
        }
        return updated;
    }

    private String replaceFirstAnchored(String sql, String anchorRegex, String oldTable, String newTable) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(anchorRegex + java.util.regex.Pattern.quote(oldTable) + "\\b");
        java.util.regex.Matcher m = p.matcher(sql);
        if (!m.find()) {
            return sql;
        }
        // Replace just the table token within the match region, preserving spaces and case around it
        int tableStart = m.start() + m.group(0).length() - oldTable.length();
        int tableEnd = tableStart + oldTable.length();
        StringBuilder sb = new StringBuilder(sql);
        sb.replace(tableStart, tableEnd, newTable);
        return sb.toString();
    }

    /**
     * Validates that the input SQL is parseable.
     */
    public boolean validateSql(String sql) {
        try {
            sqlParser.createStatement(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Analyzes what tables would be affected by a rewrite operation.
     */
    public RewriteInfo analyzeRewrite(String sql, Map<String, String> tableMapping) {
        try {
            // Extract existing table names
            Statement statement = sqlParser.createStatement(sql);
            nameExtractor.reset();
            nameExtractor.process(statement);

            var extractedTables = nameExtractor.getTableNames();
            var affectedTables = extractedTables.stream()
                .filter(tableMapping::containsKey)
                .toList();

            return new RewriteInfo(extractedTables, affectedTables, tableMapping);

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze SQL for rewrite: " + sql, e);
        }
    }
}
