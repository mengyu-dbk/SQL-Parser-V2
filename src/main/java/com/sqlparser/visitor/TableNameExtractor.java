package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Traverses Trino AST to collect all table identifiers referenced by a statement.
 * Supports SELECT along with DML/DDL: INSERT, UPDATE, DELETE, CREATE TABLE, CTAS, DROP TABLE, TRUNCATE.
 */
public class TableNameExtractor extends DefaultTraversalVisitor<Void> {

    private final Set<String> tableNames = new HashSet<>();

    @Override
    protected Void visitTable(Table table, Void context) {
        QualifiedName tableName = table.getName();
        tableNames.add(tableName.toString());
        return null;
    }

    @Override
    protected Void visitQuerySpecification(QuerySpecification node, Void context) {
        node.getFrom().ifPresent(from -> process(from, null));
        return null; // We only care about FROM traversal
    }

    @Override
    protected Void visitQuery(Query node, Void context) {
        process(node.getQueryBody(), null);
        return null;
    }

    @Override
    protected Void visitJoin(Join node, Void context) {
        process(node.getLeft(), null);
        process(node.getRight(), null);
        return null;
    }

    @Override
    protected Void visitAliasedRelation(AliasedRelation node, Void context) {
        process(node.getRelation(), null);
        return null;
    }

    // DML
    @Override
    protected Void visitInsert(Insert node, Void context) {
        // Target table
        tableNames.add(node.getTarget().toString());
        // Source query (may be a VALUES or SELECT)
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDelete(Delete node, Void context) {
        // DELETE FROM <table>
        process(node.getTable(), null);
        // WHERE may contain subqueries
        node.getWhere().ifPresent(expr -> process(expr, null));
        return null;
    }

    @Override
    protected Void visitUpdate(Update node, Void context) {
        // UPDATE <table>
        process(node.getTable(), null);
        // Assignments may contain subqueries
        node.getAssignments().forEach(a -> process(a.getValue(), null));
        // WHERE may contain subqueries
        node.getWhere().ifPresent(expr -> process(expr, null));
        return null;
    }

    // DDL
    @Override
    protected Void visitCreateTable(CreateTable node, Void context) {
        tableNames.add(node.getName().toString());
        return null;
    }

    @Override
    protected Void visitCreateTableAsSelect(CreateTableAsSelect node, Void context) {
        tableNames.add(node.getName().toString());
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDropTable(DropTable node, Void context) {
        tableNames.add(node.getTableName().toString());
        return null;
    }

    @Override
    protected Void visitTruncateTable(TruncateTable node, Void context) {
        tableNames.add(node.getTableName().toString());
        return null;
    }

    // ALTER TABLE variants
    @Override
    protected Void visitAddColumn(AddColumn node, Void context) {
        tableNames.add(node.getName().toString());
        return null;
    }

    @Override
    protected Void visitDropColumn(DropColumn node, Void context) {
        tableNames.add(node.getTable().toString());
        return null;
    }

    @Override
    protected Void visitRenameColumn(RenameColumn node, Void context) {
        tableNames.add(node.getTable().toString());
        return null;
    }

    @Override
    protected Void visitSetColumnType(SetColumnType node, Void context) {
        tableNames.add(node.getTableName().toString());
        return null;
    }

    @Override
    protected Void visitDropNotNullConstraint(DropNotNullConstraint node, Void context) {
        tableNames.add(node.getTable().toString());
        return null;
    }

    @Override
    protected Void visitRenameTable(RenameTable node, Void context) {
        tableNames.add(node.getSource().toString());
        // Also include target table name; useful for auditing
        tableNames.add(node.getTarget().toString());
        return null;
    }

    @Override
    protected Void visitSubqueryExpression(SubqueryExpression node, Void context) {
        process(node.getQuery(), null);
        return null;
    }

    public Set<String> getTableNames() {
        return new HashSet<>(tableNames);
    }

    public void reset() {
        tableNames.clear();
    }

    public Void process(Node node) {
        return process(node, null);
    }
}
