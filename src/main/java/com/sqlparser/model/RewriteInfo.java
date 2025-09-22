package com.sqlparser.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Information about a prospective table name rewrite operation.
 * Captures all referenced tables, which ones would change, and the mapping used.
 */
public class RewriteInfo {
    private final Set<String> allTables;
    private final List<String> affectedTables;
    private final Map<String, String> tableMapping;

    public RewriteInfo(Set<String> allTables, List<String> affectedTables, Map<String, String> tableMapping) {
        this.allTables = allTables;
        this.affectedTables = affectedTables;
        this.tableMapping = tableMapping;
    }

    public Set<String> getAllTables() { return allTables; }
    public List<String> getAffectedTables() { return affectedTables; }
    public Map<String, String> getTableMapping() { return tableMapping; }

    public boolean hasChanges() { return affectedTables != null && !affectedTables.isEmpty(); }

    @Override
    public String toString() {
        return "RewriteInfo{" +
            "allTables=" + allTables +
            ", affectedTables=" + affectedTables +
            ", tableMapping=" + tableMapping +
            '}';
    }
}

