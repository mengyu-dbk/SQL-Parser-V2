package com.sqlparser.service;

import com.sqlparser.model.RewriteInfo;
import com.sqlparser.visitor.PositionBasedTableRewriter;
import com.sqlparser.visitor.TableNameExtractor;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class SqlParserService {

    private final SqlParser sqlParser;
    private final PositionBasedTableRewriter positionRewriter;

    public SqlParserService() {
        this.sqlParser = new SqlParser();
        this.positionRewriter = new PositionBasedTableRewriter();
    }

    public Set<String> extractTableNames(String sql) throws Exception {
        Statement statement = sqlParser.createStatement(sql);

        TableNameExtractor extractor = new TableNameExtractor();
        extractor.process(statement);

        return extractor.getTableNames();
    }

    /**
     * Rewrites table names using a single, canonical implementation:
     * AST parsing + precise position-based replacement.
     */
    public String replaceTableNames(String sql, Map<String, String> tableMapping) throws Exception {
        return positionRewriter.rewriteTableNames(sql, tableMapping);
    }

    /**
     * Analyze which tables would be affected by a rewrite without modifying SQL.
     */
    public RewriteInfo analyzeTableRewrite(String sql, Map<String, String> tableMapping) {
        return positionRewriter.analyzeRewrite(sql, tableMapping);
    }

    /**
     * Validates that the given SQL is syntactically correct and parseable.
     */
    public boolean validateSql(String sql) {
        try {
            sqlParser.createStatement(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
