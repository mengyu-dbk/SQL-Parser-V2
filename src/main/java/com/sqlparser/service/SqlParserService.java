package com.sqlparser.service;

import com.sqlparser.visitor.TableNameExtractor;
import com.sqlparser.visitor.TableNameReplacer;
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
        // First validate input SQL and get the AST
        Statement statement = sqlParser.createStatement(sql);

        // Use improved context-aware replacement
        TableNameReplacer replacer = new TableNameReplacer(tableMapping);
        String result = replacer.replaceTableNames(sql, statement);

        // Validate the result by parsing it
        sqlParser.createStatement(result);

        return result;
    }
}
