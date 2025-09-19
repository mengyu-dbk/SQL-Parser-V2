package com.sqlparser.service;

import com.sqlparser.visitor.TableNameExtractor;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class SqlParserService {

    private final SqlParser sqlParser;

    public SqlParserService() {
        this.sqlParser = new SqlParser();
    }

    public Set<String> extractTableNames(String sql) throws Exception {
        Statement statement = sqlParser.createStatement(sql);

        TableNameExtractor extractor = new TableNameExtractor();
        extractor.process(statement);

        return extractor.getTableNames();
    }

    public String replaceTableNames(String sql, Map<String, String> tableMapping) throws Exception {
        Statement statement = sqlParser.createStatement(sql);

        // Use simple string replacement as fallback for complex AST construction issues
        String result = sql;
        for (Map.Entry<String, String> entry : tableMapping.entrySet()) {
            String oldTable = entry.getKey();
            String newTable = entry.getValue();

            // Replace table names with word boundaries to avoid partial matches
            result = result.replaceAll("\\b" + oldTable + "\\b", newTable);
        }

        // Validate the result by parsing it
        sqlParser.createStatement(result);

        return result;
    }
}
