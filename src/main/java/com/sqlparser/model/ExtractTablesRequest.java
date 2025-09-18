package com.sqlparser.model;

public class ExtractTablesRequest {
    private String sql;

    public ExtractTablesRequest() {}

    public ExtractTablesRequest(String sql) {
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}