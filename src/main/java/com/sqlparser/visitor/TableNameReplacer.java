package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

/**
 * Context-aware table name replacer that performs precise replacement
 * by understanding SQL syntax structure and only replacing actual table references.
 */
public class TableNameReplacer extends DefaultTraversalVisitor<Void> {

    private final Map<String, String> tableMapping;
    private final Set<String> extractedTableNames;

    public TableNameReplacer(Map<String, String> tableMapping) {
        this.tableMapping = tableMapping;
        this.extractedTableNames = new HashSet<>();
    }

    /**
     * Context-aware replacement that only replaces actual table references
     */
    public String replaceTableNames(String sql, Statement statement) {
        // First, extract actual table names from the AST to know what should be replaced
        TableNameExtractor extractor = new TableNameExtractor();
        extractor.process(statement);
        extractedTableNames.addAll(extractor.getTableNames());

        return replaceTableNamesSafely(sql);
    }

    /**
     * Safely replaces table names using regex with SQL context awareness
     */
    private String replaceTableNamesSafely(String sql) {
        String result = sql;

        // For each table mapping, perform careful replacement
        for (Map.Entry<String, String> entry : tableMapping.entrySet()) {
            String oldTable = entry.getKey();
            String newTable = entry.getValue();

            // Only replace if this table was actually found in the SQL
            if (extractedTableNames.contains(oldTable)) {
                result = replaceTableNameInContext(result, oldTable, newTable);
            }
        }

        return result;
    }

    /**
     * Replace table name only in valid table reference contexts
     */
    private String replaceTableNameInContext(String sql, String oldTable, String newTable) {
        StringBuilder result = new StringBuilder();
        int length = sql.length();
        int i = 0;

        while (i < length) {
            char c = sql.charAt(i);

            // Handle string literals (single quotes)
            if (c == '\'') {
                int endQuote = findMatchingQuote(sql, i, '\'');
                result.append(sql, i, endQuote + 1);
                i = endQuote + 1;
                continue;
            }

            // Handle quoted identifiers (double quotes)
            if (c == '"') {
                int endQuote = findMatchingQuote(sql, i, '"');
                result.append(sql, i, endQuote + 1);
                i = endQuote + 1;
                continue;
            }

            // Handle line comments (-- comment)
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int endLine = sql.indexOf('\n', i);
                if (endLine == -1) {
                    result.append(sql.substring(i));
                    break;
                } else {
                    result.append(sql, i, endLine + 1);
                    i = endLine + 1;
                    continue;
                }
            }

            // Handle block comments (/* comment */)
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int endComment = sql.indexOf("*/", i + 2);
                if (endComment == -1) {
                    result.append(sql.substring(i));
                    break;
                } else {
                    result.append(sql, i, endComment + 2);
                    i = endComment + 2;
                    continue;
                }
            }

            // Check if we're at the start of the target table name
            if (matchesTableNameAt(sql, i, oldTable)) {
                // Verify word boundaries
                boolean validStart = (i == 0) || !Character.isJavaIdentifierPart(sql.charAt(i - 1));
                boolean validEnd = (i + oldTable.length() == length) ||
                                   !Character.isJavaIdentifierPart(sql.charAt(i + oldTable.length()));

                if (validStart && validEnd && isValidTableContext(sql, i, oldTable)) {
                    result.append(newTable);
                    i += oldTable.length();
                    continue;
                }
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Determines if the table name at the given position is in a valid table reference context
     */
    private boolean isValidTableContext(String sql, int position, String tableName) {
        // Get context before the table name (previous 50 characters or start of string)
        int contextStart = Math.max(0, position - 50);
        String beforeContext = sql.substring(contextStart, position).toUpperCase();

        // Get context after the table name
        int tableEnd = position + tableName.length();
        int contextEnd = Math.min(sql.length(), tableEnd + 100); // Increased from 20 to 100
        String afterContext = sql.substring(tableEnd, contextEnd).toUpperCase();

        // Remove comments from context for better matching
        beforeContext = removeComments(beforeContext);

        // Check for table reference keywords before the table name
        boolean isTableReference =
            beforeContext.matches(".*\\b(FROM|JOIN|INTO|UPDATE|DELETE\\s+FROM|TABLE|REFERENCES)\\s*$") ||
            beforeContext.matches(".*\\b(CREATE\\s+TABLE|DROP\\s+TABLE|TRUNCATE\\s+TABLE|ALTER\\s+TABLE)\\s*$") ||
            beforeContext.matches(".*\\b(CREATE\\s+MATERIALIZED\\s+VIEW|DROP\\s+VIEW|CREATE\\s+VIEW)\\s*$") ||
            beforeContext.matches(".*\\b(CREATE\\s+VIEW)\\s*$") ||
            // Handle comma-separated table lists
            beforeContext.matches(".*,\\s*$");

        // Additional checks for CREATE TABLE AS SELECT, INSERT INTO, etc.
        if (!isTableReference) {
            isTableReference = beforeContext.matches(".*\\b(AS\\s+SELECT\\s+.*\\s+FROM|INSERT\\s+INTO)\\s*$");
        }

        // Special case: table.column references (qualified column names)
        boolean isColumnReference = afterContext.matches("^\\.[A-Z_][A-Z0-9_]*.*");
        if (isColumnReference) {
            isTableReference = true; // Treat qualified column references as table references
        }

        // Check if this is followed by an alias or other table indicators
        boolean hasTableIndicators =
            afterContext.matches("^\\s+[A-Z_][A-Z0-9_]*\\s*.*") || // followed by alias
            afterContext.matches("^\\s*\\(.*") || // followed by column list
            afterContext.matches("^\\s*[,;].*") || // followed by comma or semicolon
            afterContext.matches("^\\s*(ON|WHERE|SET|VALUES|JOIN)\\b.*") || // followed by SQL keywords
            afterContext.matches("^\\s*(/\\*.*?\\*/)+\\s*(WHERE|ON|SET|VALUES|JOIN)\\b.*") || // SQL keywords after comments
            afterContext.matches("^\\s*$") || // end of statement
            afterContext.matches("^\\.[A-Z_][A-Z0-9_]*.*"); // followed by column reference (table.column)

        return isTableReference && hasTableIndicators;
    }

    /**
     * Remove comments from context string for better pattern matching
     */
    private String removeComments(String context) {
        // Remove block comments
        String result = context.replaceAll("/\\*.*?\\*/", " ");
        // Remove line comments (but keep the rest since we're looking at context)
        result = result.replaceAll("--.*", " ");
        return result;
    }

    /**
     * Finds the matching closing quote, handling escape sequences
     */
    private int findMatchingQuote(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == quote) {
                // Check for escaped quote (doubled quote)
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2; // Skip escaped quote
                    continue;
                }
                return i; // Found closing quote
            }
            i++;
        }
        return sql.length() - 1; // No closing quote found, go to end
    }

    /**
     * Checks if the target table name matches at the given position (case-sensitive)
     */
    private boolean matchesTableNameAt(String sql, int position, String tableName) {
        if (position + tableName.length() > sql.length()) {
            return false;
        }

        return sql.substring(position, position + tableName.length()).equals(tableName);
    }
}