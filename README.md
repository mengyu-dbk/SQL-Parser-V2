# SQL Parser Server

A lightweight Java SQL Parser Server built with Trino's Parser for SQL parsing and table name replacement.

## Features

1. **Extract Table Names**: Extract all table names from SQL queries
2. **Replace Table Names**: Replace table names in SQL queries based on a mapping

## 当前的问题
1. 在表名中有大小写的时候，这个服务返回的表名目前都是小写的表名。（feature or bug?）
2. insert, update 等情况中目标表没有解析出来。


## Quick Start

### Build and Run

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080`

### API Endpoints

#### 1. Health Check
```bash
curl "http://localhost:8080/api/sql/health"
```

#### 2. Extract Table Names
```bash
curl -X POST "http://localhost:8080/api/sql/extract-tables" \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM users JOIN orders ON users.id = orders.user_id"
  }'
```

Response:
```json
{
  "tableNames": ["users", "orders"],
  "success": true,
  "message": "Success"
}
```

#### 3. Replace Table Names
```bash
curl -X POST "http://localhost:8080/api/sql/replace-tables" \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM users JOIN orders ON users.id = orders.user_id",
    "tableMapping": {
      "users": "user_table",
      "orders": "order_table"
    }
  }'
```

Response:
```json
{
  "sql": "SELECT * FROM user_table JOIN order_table ON user_table.id = order_table.user_id",
  "success": true,
  "message": "Success"
}
```

## Testing

Run tests with:
```bash
./mvnw test
```

## Architecture

- **Entry Layer**: Spring Boot REST API
- **Parsing Layer**: Trino SqlParser
- **Visitor Layer**: Custom AstVisitor implementations
- **Output Layer**: SqlFormatter for SQL generation

## 表名解析覆盖范围与建议

- 已支持：`SELECT`、`INSERT`、`UPDATE`（含赋值与 WHERE 子查询）、`DELETE`、`CREATE TABLE`、`CREATE TABLE AS SELECT`、`DROP TABLE`、`TRUNCATE TABLE`、以及常见 `ALTER TABLE` 变体（`ADD/DROP/RENAME COLUMN`、`SET COLUMN TYPE`、`DROP NOT NULL`、`RENAME TABLE`）。
- 建议补充/可选：
  - `MERGE INTO … USING …`（目标/来源表及分支条件中的子查询）
  - `ANALYZE <table>`、`TABLE <table> EXECUTE …`（含 WHERE 子查询）
  - 表级 `SET PROPERTIES`、`COMMENT ON TABLE`（包含表名）
  - `SHOW COLUMNS FROM <table>`、`SHOW CREATE TABLE <table>`、`SHOW STATS FOR <table>`（如需计入“表引用”）
  - 视图/物化视图：`CREATE VIEW/MATERIALIZED VIEW … AS SELECT …` 建议仅解析 AS SELECT 中的底层表；是否将视图名计为“表”，请按业务决定。
- 是否存在“通用”方案？
  - 目前没有单一 API 可在所有 DML/DDL 中通用提取表名。推荐做法是基于 Trino AST，使用 `DefaultTraversalVisitor` 精确覆盖含有表引用的节点（如 `Table`、`Insert/Update/Delete`、`Create/Drop/Truncate`、`Alter` 等），并对可能嵌套子查询的字段递归 `process`。

## Dependencies

- Trino Parser & Grammar
- Spring Boot Web
- Jackson for JSON processing
