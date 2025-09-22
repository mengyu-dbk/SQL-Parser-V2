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
public class PositionBasedTableRewriter {

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

            // Extract table names to validate they exist
            nameExtractor.reset();
            nameExtractor.process(statement);
            var actualTableNames = nameExtractor.getTableNames();

            // Extract table positions from AST
            positionExtractor.reset();
            positionExtractor.process(statement);
            var lineColumnPositions = positionExtractor.getTablePositions();

            // Convert line/column positions to character positions
            var characterPositions = TablePositionExtractor.calculateCharacterPositions(sql, lineColumnPositions);

            // Filter positions to only include tables we want to replace
            var filteredPositions = characterPositions.stream()
                .filter(pos -> actualTableNames.contains(pos.getTableName()))
                .filter(pos -> tableMapping.containsKey(pos.getTableName()))
                .collect(Collectors.toList());

            // Perform replacements from right to left to maintain position accuracy
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
            String newTable = tableMapping.get(oldTable);

            if (newTable != null) {
                int start = pos.getStartPosition();
                int end = Math.min(start + oldTable.length(), result.length());

                // Verify the content at this position matches our expectation
                if (start >= 0 && end <= result.length()) {
                    String actualContent = result.substring(start, end);
                    if (actualContent.equals(oldTable)) {
                        result.replace(start, end, newTable);
                    }
                }
            }
        }

        return result.toString();
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
