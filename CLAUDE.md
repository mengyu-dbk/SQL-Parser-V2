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
The application runs on port 8080 by default (configurable via `application.properties`). The server provides:
- Health check: `GET /api/sql/health`
- Table extraction: `POST /api/sql/extract-tables`
- Table replacement: `POST /api/sql/replace-tables`

## Architecture

This is a Spring Boot REST API that provides SQL parsing capabilities using Trino's SQL parser.

### Key Technology Stack
- **Trino SQL Parser v476**: Core SQL parsing engine
- **Spring Boot 3.3.13**: Web framework and dependency injection
- **Java 21**: Target runtime version
- **Maven**: Build tool with wrapper
- **Jackson 2.17.2**: JSON processing

### Core Components

**SqlParserService** (`src/main/java/com/sqlparser/service/`)
- Central service managing Trino's `SqlParser` instance
- `extractTableNames()`: Uses `TableNameExtractor` visitor to traverse AST and collect table names
- `replaceTableNames()`: Uses AST-based precise position replacement approach:
  - Parses SQL to build AST and capture exact character positions of table identifiers
  - Performs right-to-left string replacement to preserve character offsets
  - Validates resulting SQL by re-parsing with Trino
- `analyzeTableRewrite()`: Analyzes which tables would be affected without modifying SQL
- `validateSql()`: Validates SQL syntax

**TableNameExtractor** (`src/main/java/com/sqlparser/visitor/`)
- Extends `DefaultTraversalVisitor<Void>` to traverse Trino AST
- Collects both table names and their precise token positions (start/end character offsets)
- Tracks aliases to avoid replacing aliased references (e.g., `JOIN orders o` where `o.id` should not be replaced)
- Implements visitor methods for comprehensive SQL construct coverage:
  - DML: `visitSelect`, `visitInsert`, `visitUpdate`, `visitDelete`, `visitMerge`
  - DDL: `visitCreateTable`, `visitCreateTableAsSelect`, `visitDropTable`, `visitTruncateTable`
  - ALTER: `visitAddColumn`, `visitDropColumn`, `visitRenameColumn`, `visitSetColumnType`, `visitRenameTable`
  - Views: `visitCreateView`, `visitDropView`, `visitRenameView`, `visitCreateMaterializedView`, `visitRefreshMaterializedView`
  - Other: `visitAnalyze`, `visitTableExecute`, `visitSetProperties`, `visitComment`, `visitShowColumns`, `visitShowStats`, `visitGrant`, `visitRevoke`
- Captures `DereferenceExpression` positions for unaliased column qualifiers:
  - Single-part: `users.id` where `users` is not an alias
  - Multi-part: `catalog.schema.table.id` extracts and tracks `catalog.schema.table`
  - Uses `addQualifiedToken()` to calculate correct position for entire qualified name

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
- **Note**: Trino only supports double quotes (`"`) for identifiers, not backticks (`` ` ``)

**Table Name Replacement Strategy**:
The implementation uses AST-based position tracking with direct string replacement to preserve original SQL formatting:
1. Parse SQL with Trino to build AST
2. Extract table identifiers and their exact character positions using `TableNameExtractor`
3. Sort tokens right-to-left to ensure stable offsets during replacement
4. Replace each token at its precise position with case-aware mapping (exact match preferred, case-insensitive fallback)
5. Fallback to anchored regex patterns for DML targets (UPDATE/DELETE/MERGE) when position-based replacement fails
6. Validate resulting SQL by re-parsing with Trino

**Quoted Identifier Handling**:
- **Detection**: The `TableNameExtractor` detects quoted identifiers by checking if the original SQL starts with `"` at the calculated position
- **Position Adjustment**: For quoted identifiers, the end position includes the closing quote (position + text.length + 2)
- **Extraction Format**: `extract-tables` always returns **unquoted** table names (e.g., `"users"` → `users`)
- **Replacement Matching**: `replace-tables` strips quotes from actual SQL text for comparison with mapping keys
- **API Symmetry**: Table names extracted by `extract-tables` can be directly used as keys in `replace-tables` mapping

**Replacement Behavior**:
- **Quoted identifiers**: Now replaced if present in mapping (e.g., `"chaintable.token.eth"` → `token_table`)
- **Aliases**: Tracked and alias references are not replaced (e.g., in `JOIN orders o ON o.id = ...`, `o.id` is not replaced)
- **Unaliased column qualifiers**: Replaced along with table (e.g., `users.id` becomes `user_accounts.id` when `users` → `user_accounts`)
- **Multi-part qualified names**: Fully supported - entire qualified name is replaced (e.g., `catalog.schema.table` → `newtable`, including in column references like `catalog.schema.table.id` → `newtable.id`)
- **INSERT/CTAS targets**: Not replaced (only source tables in SELECT are replaced)
- **CREATE/ALTER TABLE targets**: Replaced (e.g., `CREATE TABLE orders` → `CREATE TABLE order_records`)
- **String literals and comments**: Not replaced (preserved as-is)
- **Case preservation**: Attempts to preserve original SQL casing style

### Model Classes
Request/response DTOs in `src/main/java/com/sqlparser/model/`:
- `ExtractTablesRequest/Response`: For table name extraction
- `ReplaceTablesRequest/Response`: For table name replacement
- `RewriteInfo`: Analysis result showing all tables, affected tables, and mapping

### Source Code Reference
- Trino source code is available in the `trino/` folder at repository root for reference when working with Trino AST classes

### Testing
- JUnit 5.10.3 for unit tests
- Spring Boot Test 3.3.13 for integration tests
- Maven Surefire plugin configured with `-Dnet.bytebuddy.experimental=true` for compatibility
- Test structure in `src/test/java/com/sqlparser/`:
  - `controller/SqlParserControllerTest.java`: REST API integration tests
  - `controller/SqlParserSymmetryApiTest.java`: API-level symmetry tests (extract → replace workflow)
  - `service/SqlParserServiceTest.java`: Core service tests
  - `service/SqlParserAdvancedTest.java`: Advanced SQL patterns (subqueries, CTEs, complex joins)
  - `service/SqlParserDmlDdlTest.java`: DML and DDL statement tests
  - `service/SqlParserExtendedDdlDmlTest.java`: Extended DDL/DML coverage (views, materialized views, grants)
  - `service/SqlParserEdgeCasesTest.java`: Edge cases and error handling
  - `service/SqlParserEdgeCaseBoundaryTest.java`: Boundary conditions
  - `service/SqlParserPreciseReplacementTest.java`: Position-based replacement validation
  - `service/SqlParserAstRewriteTest.java`: AST rewrite behavior tests
  - `service/SqlParserPositionFixTest.java`: Position calculation validation (MERGE, UPDATE, DELETE)
  - `service/SqlParserQualifiedNameReplacementTest.java`: Multi-part qualified name replacement (catalog.schema.table)
  - `service/SqlParserQuotedIdentifierTest.java`: Quoted identifier replacement tests (special characters, dots)
  - `service/SqlParserSymmetryTest.java`: Extract/replace API symmetry tests (service-level)