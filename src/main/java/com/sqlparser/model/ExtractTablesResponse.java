package com.sqlparser.model;

import java.util.Set;

public class ExtractTablesResponse {
    private Set<String> tableNames;
    private boolean success;
    private String message;

    public ExtractTablesResponse() {}

    public ExtractTablesResponse(Set<String> tableNames, boolean success, String message) {
        this.tableNames = tableNames;
        this.success = success;
        this.message = message;
    }

    public Set<String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(Set<String> tableNames) {
        this.tableNames = tableNames;
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