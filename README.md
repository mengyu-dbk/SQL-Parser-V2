# SQL Parser Server

A lightweight Java SQL Parser Server built with Trino's Parser for SQL parsing and table name replacement.

## Features

1. **Extract Table Names**: Extract all table names from SQL queries
2. **Replace Table Names**: Replace table names in SQL queries based on a mapping

## 行为说明（表名替换语义）

基于 Trino AST 的“精确位置替换”，仅替换真正的表标识符 token，保留原 SQL 的注释与格式。

- 引号标识符不替换；别名引用不替换；未加别名的列限定符会随表替换而更新
  - 输入：`SELECT users.id, users.name FROM users WHERE users.status = 'active'`
  - 映射：`{"users": "user_accounts"}`
  - 输出：`SELECT user_accounts.id, user_accounts.name FROM user_accounts WHERE user_accounts.status = 'active'`
  - 引号示例：`SELECT * FROM "users" u` 中的 `"users"` 不会被替换
  - 别名示例：`JOIN orders users ON users.customer_id = ...` 中 `users.customer_id` 不会被替换（users 是别名）

- CTAS 仅替换源 SELECT；INSERT 目标不替换；DDL 的 CREATE TABLE、ALTER TABLE 目标会被替换
  - CTAS：
    - 输入：`CREATE TABLE t AS SELECT * FROM users`
    - 映射：`{"users": "user_accounts", "t": "t_new"}`
    - 输出：`CREATE TABLE t AS SELECT * FROM user_accounts`（目标 t 不变）
  - INSERT：
    - 输入：`INSERT INTO t SELECT * FROM users`
    - 输出：`INSERT INTO t SELECT * FROM user_accounts`（目标 t 不变）
  - CREATE TABLE：
    - 输入：`CREATE TABLE orders (id INT)`，映射 `{"orders": "order_records"}`
    - 输出：`CREATE TABLE order_records (id INT)`
  - ALTER TABLE：
    - 输入：`ALTER TABLE orders ADD COLUMN status VARCHAR(50)`，映射 `{"orders": "order_records"}`
    - 输出：`ALTER TABLE order_records ADD COLUMN status VARCHAR(50)`

- 精度与安全
  - 替换顺序为“从右到左”，保证多处替换时字符偏移不被破坏
  - 替换后会再次用 Trino 解析校验，确保语法正确
  - 关键字、字符串字面量、注释中的文本不会被替换
    - 字符串示例：`WHERE description = 'users data'` 中 `'users data'` 不变
    - 注释示例：`/* users table */` 中 `users` 不变

提示：大小写映射采用“优先按实际 token 原样匹配，其次忽略大小写匹配”的策略，尽量保持原 SQL 的大小写风格。


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
- **Visitor Layer**: Custom AstVisitor (TableNameExtractor) 收集表名与精确位置
- **Rewrite**: 基于精确 token 位置进行“就地替换”，不重建 AST，不使用格式化器；替换后做一次解析校验

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
