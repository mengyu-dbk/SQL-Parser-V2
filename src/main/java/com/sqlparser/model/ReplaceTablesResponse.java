package com.sqlparser.model;

public class ReplaceTablesResponse {
    private String sql;
    private boolean success;
    private String message;

    public ReplaceTablesResponse() {}

    public ReplaceTablesResponse(String sql, boolean success, String message) {
        this.sql = sql;
        this.success = success;
        this.message = message;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}