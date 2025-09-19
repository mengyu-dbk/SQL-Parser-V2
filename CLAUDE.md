# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Essential Commands

### Build and Run
```bash
./mvnw clean compile              # Build the project
./mvnw spring-boot:run            # Run on default port 8080
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"  # Run on custom port
./mvnw test                       # Run all tests
./mvnw test -Dtest=SqlParserServiceTest  # Run specific test class
```

### Development
The application runs on port 8081 by default in this setup to avoid conflicts. The server provides:
- Health check: `GET /api/sql/health`
- Table extraction: `POST /api/sql/extract-tables`
- Table replacement: `POST /api/sql/replace-tables`

## Architecture

This is a Spring Boot REST API that provides SQL parsing capabilities using Trino's SQL parser.

### Key Technology Stack
- **Trino SQL Parser v476**: Core SQL parsing engine (upgraded from v432)
- **Spring Boot 3.3.4**: Web framework and dependency injection (upgraded from 2.7.14)
- **Java 21**: Target runtime version (upgraded from Java 11)
- **Maven**: Build tool with wrapper

### Core Components

**SqlParserService** (`src/main/java/com/sqlparser/service/`)
- Central service managing Trino's `SqlParser` instance
- `extractTableNames()`: Uses `TableNameExtractor` visitor to traverse AST and collect table names
- `replaceTableNames()`: Uses regex-based string replacement with word boundaries for table name substitution, followed by SQL validation

**TableNameExtractor** (`src/main/java/com/sqlparser/visitor/`)
- Extends `DefaultExpressionTraversalVisitor<Void>` to traverse Trino AST
- Implements visitor methods for SQL constructs: `visitTable()`, `visitQuerySpecification()`, `visitQuery()`, `visitJoin()`, `visitAliasedRelation()`
- Accumulates table names in a `HashSet<String>`

**SqlParserController** (`src/main/java/com/sqlparser/controller/`)
- REST endpoints at `/api/sql/*`
- Request/response models in `com.sqlparser.model` package
- Comprehensive error handling and validation

### Trino Parser Integration Notes

**Critical**: This project uses **Trino v476** which has modern API characteristics:
- Enhanced SQL parsing capabilities with better error handling
- Improved AST node construction and traversal
- Complex AST rewriting requires careful handling of constructor parameters
- `DefaultExpressionTraversalVisitor` is preferred for read-only AST traversal

**Table Name Replacement Strategy**:
The current implementation uses regex-based string replacement (`\b` word boundaries) rather than AST rewriting due to complexity of Trino constructor signatures. This approach:
- Validates input SQL by parsing with Trino
- Performs regex replacement with word boundaries
- Validates output SQL by parsing again
- Works reliably for table name substitution including column references

### Model Classes
Request/response DTOs in `src/main/java/com/sqlparser/model/`:
- `ExtractTablesRequest/Response`
- `ReplaceTablesRequest/Response`

### Testing
- JUnit 5.10.3 for unit tests (upgraded from JUnit 4)
- Spring Boot Test 3.3.4 for integration tests
- Test classes in `src/test/java/com/sqlparser/`
- **56 comprehensive unit tests** covering core functionality, advanced SQL patterns, and edge cases