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

    // MERGE
    @Override
    protected Void visitMerge(Merge node, Void context) {
        // Target and source relations may be tables or subqueries
        process(node.getTarget(), null);
        process(node.getSource(), null);
        process(node.getPredicate(), null);
        // Traverse cases (conditions and expressions)
        for (MergeCase c : node.getMergeCases()) {
            c.getExpression().ifPresent(expr -> process(expr, null));
            // Set expressions may contain subqueries
            c.getSetExpressions().forEach(expr -> process(expr, null));
        }
        return null;
    }

    // ANALYZE
    @Override
    protected Void visitAnalyze(Analyze node, Void context) {
        tableNames.add(node.getTableName().toString());
        return null;
    }

    // TABLE EXECUTE
    @Override
    protected Void visitTableExecute(TableExecute node, Void context) {
        process(node.getTable(), null);
        node.getWhere().ifPresent(expr -> process(expr, null));
        node.getArguments().forEach(arg -> process(arg.getValue(), null));
        return null;
    }

    // SET PROPERTIES (TABLE/MATERIALIZED_VIEW)
    @Override
    protected Void visitSetProperties(SetProperties node, Void context) {
        SetProperties.Type type = node.getType();
        if (type == SetProperties.Type.TABLE || type == SetProperties.Type.MATERIALIZED_VIEW) {
            tableNames.add(node.getName().toString());
        }
        return null;
    }

    // COMMENT ON TABLE/VIEW
    @Override
    protected Void visitComment(Comment node, Void context) {
        if (node.getType() == Comment.Type.TABLE || node.getType() == Comment.Type.VIEW) {
            tableNames.add(node.getName().toString());
        }
        return null;
    }

    // SHOW statements that specify a single table
    @Override
    protected Void visitShowColumns(ShowColumns node, Void context) {
        tableNames.add(node.getTable().toString());
        return null;
    }

    @Override
    protected Void visitShowCreate(ShowCreate node, Void context) {
        if (node.getType() == ShowCreate.Type.TABLE || node.getType() == ShowCreate.Type.MATERIALIZED_VIEW) {
            tableNames.add(node.getName().toString());
        }
        return null;
    }

    @Override
    protected Void visitShowStats(ShowStats node, Void context) {
        process(node.getRelation(), null);
        return null;
    }

    // Views / Materialized Views
    @Override
    protected Void visitCreateView(CreateView node, Void context) {
        // Include view name as an object reference and traverse underlying query
        tableNames.add(node.getName().toString());
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDropView(DropView node, Void context) {
        tableNames.add(node.getName().toString());
        return null;
    }

    @Override
    protected Void visitRenameView(RenameView node, Void context) {
        tableNames.add(node.getSource().toString());
        tableNames.add(node.getTarget().toString());
        return null;
    }

    @Override
    protected Void visitCreateMaterializedView(CreateMaterializedView node, Void context) {
        tableNames.add(node.getName().toString());
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDropMaterializedView(DropMaterializedView node, Void context) {
        tableNames.add(node.getName().toString());
        return null;
    }

    @Override
    protected Void visitRenameMaterializedView(RenameMaterializedView node, Void context) {
        tableNames.add(node.getSource().toString());
        tableNames.add(node.getTarget().toString());
        return null;
    }

    @Override
    protected Void visitRefreshMaterializedView(RefreshMaterializedView node, Void context) {
        tableNames.add(node.getName().toString());
        return null;
    }

    // GRANT / REVOKE / SHOW GRANTS (only tables)
    @Override
    protected Void visitGrant(Grant node, Void context) {
        GrantObject obj = node.getGrantObject();
        if (obj.getEntityKind().map(kind -> kind.equalsIgnoreCase("TABLE")).orElse(false)) {
            tableNames.add(obj.getName().toString());
        }
        return null;
    }

    @Override
    protected Void visitRevoke(Revoke node, Void context) {
        GrantObject obj = node.getGrantObject();
        if (obj.getEntityKind().map(kind -> kind.equalsIgnoreCase("TABLE")).orElse(false)) {
            tableNames.add(obj.getName().toString());
        }
        return null;
    }

    @Override
    protected Void visitShowGrants(ShowGrants node, Void context) {
        node.getGrantObject().ifPresent(obj -> {
            if (obj.getEntityKind().map(kind -> kind.equalsIgnoreCase("TABLE")).orElse(false)) {
                tableNames.add(obj.getName().toString());
            }
        });
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
