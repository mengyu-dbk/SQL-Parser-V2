package com.sqlparser.controller;

import com.sqlparser.model.*;
import com.sqlparser.service.SqlParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/sql")
public class SqlParserController {

    private static final Logger logger = LoggerFactory.getLogger(SqlParserController.class);

    @Autowired
    private SqlParserService sqlParserService;

    @PostMapping("/extract-tables")
    public ResponseEntity<ExtractTablesResponse> extractTables(@RequestBody ExtractTablesRequest request) {
        try {
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ExtractTablesResponse(null, false, "SQL cannot be empty"));
            }

            Set<String> tableNames = sqlParserService.extractTableNames(request.getSql());
            return ResponseEntity.ok(new ExtractTablesResponse(tableNames, true, "Success"));

        } catch (Exception e) {
            logger.error("Error extracting table names", e);
            return ResponseEntity.badRequest()
                    .body(new ExtractTablesResponse(null, false, "Error parsing SQL: " + e.getMessage()));
        }
    }

    @PostMapping("/replace-tables")
    public ResponseEntity<ReplaceTablesResponse> replaceTables(@RequestBody ReplaceTablesRequest request) {
        try {
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ReplaceTablesResponse(null, false, "SQL cannot be empty"));
            }

            if (request.getTableMapping() == null || request.getTableMapping().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ReplaceTablesResponse(null, false, "Table mapping cannot be empty"));
            }

            String modifiedSql = sqlParserService.replaceTableNames(request.getSql(), request.getTableMapping());
            return ResponseEntity.ok(new ReplaceTablesResponse(modifiedSql, true, "Success"));

        } catch (Exception e) {
            logger.error("Error replacing table names", e);
            return ResponseEntity.badRequest()
                    .body(new ReplaceTablesResponse(null, false, "Error processing SQL: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SQL Parser Server is running");
    }
}