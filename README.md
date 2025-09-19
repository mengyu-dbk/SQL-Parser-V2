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

## Dependencies

- Trino Parser & Grammar
- Spring Boot Web
- Jackson for JSON processing