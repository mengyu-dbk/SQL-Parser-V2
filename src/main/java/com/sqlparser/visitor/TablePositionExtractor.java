package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts table name positions from AST for precise character-level replacement.
 * This visitor collects the exact source positions of table references.
 */
public class TablePositionExtractor extends DefaultTraversalVisitor<Void> {

    public static class TablePosition {
        private final String tableName;
        private final int startPosition;
        private final int endPosition;

        public TablePosition(String tableName, int startPosition, int endPosition) {
            this.tableName = tableName;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        public String getTableName() { return tableName; }
        public int getStartPosition() { return startPosition; }
        public int getEndPosition() { return endPosition; }

        @Override
        public String toString() {
            return String.format("TablePosition{table='%s', start=%d, end=%d}",
                               tableName, startPosition, endPosition);
        }
    }

    private final List<TablePosition> tablePositions = new ArrayList<>();

    @Override
    protected Void visitTable(Table table, Void context) {
        QualifiedName tableName = table.getName();
        String tableNameStr = tableName.toString();

        // Get position information from the table node
        if (table.getLocation().isPresent()) {
            NodeLocation location = table.getLocation().get();

            // Get line and column information
            int startLine = location.getLineNumber();
            int startColumn = location.getColumnNumber();

            // For now, estimate end position based on table name length
            TablePosition position = new TablePosition(
                tableNameStr,
                startLine * 1000 + startColumn, // Temporary encoding
                startLine * 1000 + startColumn + tableNameStr.length() // Estimated end
            );

            tablePositions.add(position);
        }

        return null;
    }

    public List<TablePosition> getTablePositions() {
        return new ArrayList<>(tablePositions);
    }

    public void reset() {
        tablePositions.clear();
    }

    /**
     * Calculates actual character positions from line/column information.
     * This method maps Trino's line/column positions to character offsets in the original SQL.
     */
    public static List<TablePosition> calculateCharacterPositions(String sql, List<TablePosition> lineColumnPositions) {
        List<TablePosition> result = new ArrayList<>();
        String[] lines = sql.split("\n", -1); // Keep empty strings

        for (TablePosition pos : lineColumnPositions) {
            // Decode line/column from our temporary encoding
            int startLine = pos.getStartPosition() / 1000;
            int startColumn = pos.getStartPosition() % 1000;
            int endColumn = pos.getEndPosition() % 1000;

            // Convert to character positions
            int startCharPos = 0;
            for (int i = 0; i < startLine - 1 && i < lines.length; i++) {
                startCharPos += lines[i].length() + 1; // +1 for newline
            }
            startCharPos += Math.max(0, startColumn - 1);

            // End position is estimated based on table name length
            int endCharPos = startCharPos + pos.getTableName().length();

            result.add(new TablePosition(pos.getTableName(), startCharPos, endCharPos));
        }

        return result;
    }
}