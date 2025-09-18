package com.sqlparser.model;

import java.util.Map;

public class ReplaceTablesRequest {
    private String sql;
    private Map<String, String> tableMapping;

    public ReplaceTablesRequest() {}

    public ReplaceTablesRequest(String sql, Map<String, String> tableMapping) {
        this.sql = sql;
        this.tableMapping = tableMapping;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, String> getTableMapping() {
        return tableMapping;
    }

    public void setTableMapping(Map<String, String> tableMapping) {
        this.tableMapping = tableMapping;
    }
}