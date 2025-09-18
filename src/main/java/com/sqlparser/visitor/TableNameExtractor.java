package com.sqlparser.visitor;

import io.trino.sql.tree.*;

import java.util.HashSet;
import java.util.Set;

public class TableNameExtractor extends DefaultExpressionTraversalVisitor<Void> {

    private final Set<String> tableNames = new HashSet<>();

    @Override
    protected Void visitTable(Table table, Void context) {
        QualifiedName tableName = table.getName();
        String tableNameString = tableName.toString();
        System.out.println("Found table: " + tableNameString);
        tableNames.add(tableNameString);
        return null;
    }

    @Override
    protected Void visitQuerySpecification(QuerySpecification node, Void context) {
        System.out.println("Visiting QuerySpecification");
        if (node.getFrom().isPresent()) {
            process(node.getFrom().get(), context);
        }
        return null;
    }

    @Override
    protected Void visitQuery(Query node, Void context) {
        System.out.println("Visiting Query");
        process(node.getQueryBody(), context);
        return null;
    }

    @Override
    protected Void visitJoin(Join node, Void context) {
        System.out.println("Visiting Join");
        process(node.getLeft(), context);
        process(node.getRight(), context);
        return null;
    }

    @Override
    protected Void visitAliasedRelation(AliasedRelation node, Void context) {
        System.out.println("Visiting AliasedRelation");
        process(node.getRelation(), context);
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