package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Traverses Trino AST to collect table identifiers and their positions.
 * This integrates table-name extraction and precise token position capture
 * (so we can later do string replacements without a separate rewriter class).
 */
public class TableNameExtractor extends DefaultTraversalVisitor<Void> {

    // One occurrence of a table token or an unaliased qualifier base in the SQL text
    public static final class TableToken {
        private final String text;      // token text as it appears in AST (may contain qualifiers or quotes)
        private final int start;        // 0-based character offset in original SQL
        private final int end;          // exclusive end offset

        public TableToken(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }

        public String getText() { return text; }
        public int getStart() { return start; }
        public int getEnd() { return end; }

        @Override
        public String toString() {
            return "TableToken{" + text + ", " + start + ":" + end + "}";
        }
    }

    private final Set<String> tableNames = new HashSet<>();
    private final List<TableToken> tokens = new ArrayList<>();
    private final Set<String> aliases = new HashSet<>();

    // Precomputed line start offsets for fast NodeLocation -> char offset conversion
    private int[] lineStartOffsets = new int[0];

    // === Public API ===

    public void reset() {
        tableNames.clear();
        tokens.clear();
        aliases.clear();
        lineStartOffsets = new int[0];
    }

    // Entry point that also provides the original SQL for computing character offsets
    public void collect(Statement stmt, String originalSql) {
        reset();
        buildLineStartOffsets(originalSql);
        process(stmt, null);
    }

    public Set<String> getTableNames() {
        return new HashSet<>(tableNames);
    }

    public List<TableToken> getTableTokens() {
        return new ArrayList<>(tokens);
    }

    public Void process(Node node) { // backward-compatible helper used by some tests/services
        return process(node, null);
    }

    // === Core helpers ===

    private void addToken(String text, NodeLocation location) {
        if (location == null) return;
        int start = toCharOffset(location.getLineNumber(), location.getColumnNumber());
        int end = start + (text != null ? text.length() : 0);
        tokens.add(new TableToken(text, start, end));
    }

    private int toCharOffset(int lineNumber1Based, int columnNumber1Based) {
        int lineIdx = Math.max(0, lineNumber1Based - 1);
        int colIdx = Math.max(0, columnNumber1Based - 1);
        if (lineIdx >= lineStartOffsets.length) return 0;
        return lineStartOffsets[lineIdx] + colIdx;
    }

    private void buildLineStartOffsets(String sql) {
        // Compute start offsets for each line (1-based in Trino; we store 0-based indexes)
        // Keep positions consistent for multi-line SQL with various line lengths
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            }
        }
        lineStartOffsets = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    // === Visitor overrides ===

    @Override
    protected Void visitTable(Table table, Void context) {
        QualifiedName name = table.getName();
        String tokenText = name.toString(); // include qualifiers/quotes if present
        tableNames.add(tokenText);
        table.getLocation().ifPresent(loc -> addToken(tokenText, loc));
        return null;
    }

    @Override
    protected Void visitQuerySpecification(QuerySpecification node, Void context) {
        // Traverse entire query spec so we can capture dereference bases in SELECT/WHERE/etc.
        return super.visitQuerySpecification(node, context);
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
        node.getCriteria().ifPresent(criteria -> {
            if (criteria instanceof JoinOn) {
                process(((JoinOn) criteria).getExpression(), null);
            }
            // JoinUsing doesn't contain dereference expressions we want to rewrite
        });
        return null;
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

    // DML
    @Override
    protected Void visitInsert(Insert node, Void context) {
        // Target table (name only; we do NOT capture a token to avoid rewriting INSERT target)
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
        // Capture only the last identifier's position so fully qualified names are handled
        List<Identifier> parts = node.getName().getOriginalParts();
        if (!parts.isEmpty()) {
            Identifier last = parts.get(parts.size() - 1);
            if (last.getLocation().isPresent()) {
                addToken(last.getValue(), last.getLocation().get());
            }
        }
        return null;
    }

    @Override
    protected Void visitCreateTableAsSelect(CreateTableAsSelect node, Void context) {
        tableNames.add(node.getName().toString());
        // Do not capture a token for CTAS target; rewrite only the source SELECT
        process(node.getQuery(), null);
        return null;
    }

    @Override
    protected Void visitDropTable(DropTable node, Void context) {
        tableNames.add(node.getTableName().toString());
        // Intentionally not capturing token to avoid rewriting DROP TABLE in current tests
        return null;
    }

    @Override
    protected Void visitTruncateTable(TruncateTable node, Void context) {
        tableNames.add(node.getTableName().toString());
        // Intentionally not capturing token
        return null;
    }

    // ALTER TABLE variants
    @Override
    protected Void visitAddColumn(AddColumn node, Void context) {
        tableNames.add(node.getName().toString());
        List<Identifier> parts = node.getName().getOriginalParts();
        if (!parts.isEmpty()) {
            Identifier last = parts.get(parts.size() - 1);
            if (last.getLocation().isPresent()) {
                addToken(last.getValue(), last.getLocation().get());
            }
        }
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

    // Skip CTE definitions for token collection to preserve them unchanged
    @Override
    protected Void visitWithQuery(WithQuery node, Void context) {
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

    @Override
    protected Void visitDereferenceExpression(DereferenceExpression node, Void context) {
        // Capture positions for unaliased qualifiers like orders.user_id
        Expression base = node.getBase();
        if (base instanceof Identifier) {
            Identifier id = (Identifier) base;
            String name = id.getValue();
            if (!aliases.contains(name) && id.getLocation().isPresent()) {
                addToken(name, id.getLocation().get());
            }
        }
        return super.visitDereferenceExpression(node, context);
    }
}
