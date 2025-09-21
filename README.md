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

## 表名解析覆盖范围

- 已支持：
  - DML：`SELECT`、`INSERT`、`UPDATE`（赋值与 WHERE 子查询）、`DELETE`、`MERGE INTO … USING …`
  - DDL（表）：`CREATE TABLE`、`CREATE TABLE AS SELECT`、`DROP TABLE`、`TRUNCATE TABLE`
  - ALTER：`ADD/DROP/RENAME COLUMN`、`SET COLUMN TYPE`、`DROP NOT NULL`、`RENAME TABLE`、`ALTER TABLE … EXECUTE …`、`SET PROPERTIES`
  - 视图/物化视图：`CREATE VIEW/MATERIALIZED VIEW … AS SELECT …`、`DROP/RENAME VIEW`、`DROP/RENAME MATERIALIZED VIEW`、`REFRESH MATERIALIZED VIEW`
  - 其他：`ANALYZE <table>`、`SHOW COLUMNS FROM <table>`、`SHOW CREATE TABLE/MATERIALIZED VIEW`、`SHOW STATS FOR <relation>`、`COMMENT ON TABLE/VIEW`、`GRANT/REVOKE/SHOW GRANTS`（当对象类型为 TABLE 时）

实现要点：以 Trino AST 为准，使用 `DefaultTraversalVisitor` 覆盖会出现表/对象名的语法节点，并对可能包含子查询的字段（如 `WHERE`、`SET` 赋值、`AS SELECT`、`MERGE` 条件等）递归处理。

## Dependencies

- Trino Parser & Grammar
- Spring Boot Web
- Jackson for JSON processing
