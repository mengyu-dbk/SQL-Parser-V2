package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Set<String> aliases = new HashSet<>();

    // Ensure we traverse through typical places where table references appear
    // including DML/DDL constructs and subqueries. We deliberately do not add
    // target objects of DDL (e.g., CREATE TABLE <target>) because the current
    // rewriter is meant to update source table references only.

    @Override
    protected Void visitInsert(Insert node, Void context) {
        // Target table is a definition; we keep it untouched
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDelete(Delete node, Void context) {
        process(node.getTable(), null);
        node.getWhere().ifPresent(expr -> process(expr, null));
        return null;
    }

    @Override
    protected Void visitUpdate(Update node, Void context) {
        process(node.getTable(), null);
        node.getAssignments().forEach(a -> process(a.getValue(), null));
        node.getWhere().ifPresent(expr -> process(expr, null));
        return null;
    }

    @Override
    protected Void visitCreateTableAsSelect(CreateTableAsSelect node, Void context) {
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitCreateTable(CreateTable node, Void context) {
        // Capture target table position so DDL targets can be rewritten
        var parts = node.getName().getOriginalParts();
        if (!parts.isEmpty()) {
            Identifier last = parts.get(parts.size() - 1);
            if (last.getLocation().isPresent()) {
                NodeLocation location = last.getLocation().get();
                String name = last.getValue();
                int startLine = location.getLineNumber();
                int startColumn = location.getColumnNumber();
                tablePositions.add(new TablePosition(
                    name,
                    startLine * 1000 + startColumn,
                    startLine * 1000 + startColumn + name.length()
                ));
            }
        }
        return null;
    }

    @Override
    protected Void visitSubqueryExpression(SubqueryExpression node, Void context) {
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitMerge(Merge node, Void context) {
        process(node.getTarget(), null);
        process(node.getSource(), null);
        process(node.getPredicate(), null);
        node.getMergeCases().forEach(c -> {
            c.getExpression().ifPresent(expr -> process(expr, null));
            c.getSetExpressions().forEach(expr -> process(expr, null));
        });
        return null;
    }

    @Override
    protected Void visitWithQuery(WithQuery node, Void context) {
        // Intentionally do not traverse into CTE definitions to preserve them
        // unchanged for now (behavior expected by tests).
        return null;
    }

    @Override
    protected Void visitShowStats(ShowStats node, Void context) {
        process(node.getRelation(), null);
        return null;
    }

    @Override
    protected Void visitAddColumn(AddColumn node, Void context) {
        var parts = node.getName().getOriginalParts();
        if (!parts.isEmpty()) {
            Identifier last = parts.get(parts.size() - 1);
            if (last.getLocation().isPresent()) {
                NodeLocation location = last.getLocation().get();
                String name = last.getValue();
                int startLine = location.getLineNumber();
                int startColumn = location.getColumnNumber();
                tablePositions.add(new TablePosition(
                    name,
                    startLine * 1000 + startColumn,
                    startLine * 1000 + startColumn + name.length()
                ));
            }
        }
        return null;
    }

    @Override
    protected Void visitTable(Table table, Void context) {
        QualifiedName tableName = table.getName();
        String tableNameStr = tableName.toString();

        // Get position information from the table node
        if (table.getLocation().isPresent()) {
            NodeLocation location = table.getLocation().get();

            // Get line and column information (1-based from Trino)
            int startLine = location.getLineNumber();
            int startColumn = location.getColumnNumber();

            // Estimate end position based on table name token length
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
        aliases.clear();
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

    @Override
    protected Void visitAliasedRelation(AliasedRelation node, Void context) {
        Identifier alias = node.getAlias();
        if (alias != null) {
            aliases.add(alias.getValue());
        }
        process(node.getRelation(), null);
        return null;
    }

    @Override
    protected Void visitDereferenceExpression(DereferenceExpression node, Void context) {
        // Capture positions for unaliased qualifiers like orders.user_id
        Expression base = node.getBase();
        if (base instanceof Identifier) {
            Identifier id = (Identifier) base;
            String name = id.getValue();
            if (!aliases.contains(name) && id.getLocation().isPresent()) {
                NodeLocation location = id.getLocation().get();
                int startLine = location.getLineNumber();
                int startColumn = location.getColumnNumber();
                tablePositions.add(new TablePosition(
                    name,
                    startLine * 1000 + startColumn,
                    startLine * 1000 + startColumn + name.length()
                ));
            }
        }
        return super.visitDereferenceExpression(node, context);
    }
}
