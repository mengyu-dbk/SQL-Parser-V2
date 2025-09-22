# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot app (`com.sqlparser.SqlParserServerApplication`), REST controller (`controller.SqlParserController`), service (`service.SqlParserService`), DTOs (`model.*`), and Trino AST visitors (`visitor.TableNameExtractor`, `visitor.TablePositionExtractor`).
- `src/main/resources`: Spring configuration/resources.
- `src/test/java`: JUnit tests mirroring main packages; classes end with `*Test.java`.
- `pom.xml`: Maven build (Java 21, Spring Boot 3, Trino parser).
- `target/`: Build outputs.

## Build, Test, and Development Commands
- `./mvnw clean verify` - compile and run the full test suite.
- `./mvnw test` - run unit/integration tests only.
- `./mvnw spring-boot:run` - launch locally at `http://localhost:8080`.
- `./mvnw package` - build a runnable jar at `target/sql-parser-server-1.0.0.jar`.
- `java -jar target/sql-parser-server-1.0.0.jar` - run the packaged app.
- Smoke check: `curl http://localhost:8080/api/sql/health`.

## Coding Style & Naming Conventions
- Java 21; 4-space indent; UTF-8; keep lines ~120 cols.
- Packages: `com.sqlparser.*`; classes UpperCamelCase; methods/fields lowerCamelCase.
- Use `slf4j` logging; avoid `System.out` (OK in tests). Keep controllers thin; push logic into services.
- DTOs are simple POJOs; avoid Lombok unless added to `pom.xml`.

## Testing Guidelines
- Spring Boot Test + JUnit (Vintage engine enabled). Use `@SpringBootTest` when wiring Spring.
- Place tests in `src/test/java` mirroring packages; suffix with `Test`.
- Focus on parsing edge cases: joins, subqueries, schemas, unions, DDL/DML, CTE-like patterns, precise replacements.
- Run: `./mvnw test`.

## Commit & Pull Request Guidelines
- Commit style: `feat|fix|docs|test|refactor: short present-tense summary`. Mixed English/Chinese is fine.
- Reference issues in the body (e.g., `Fixes #12`). Note API or behavior changes.
- PRs should include purpose, linked issues, how to reproduce or verify, and sample SQL or request/response snippets.

## Security & Configuration Tips
- Prefer logger levels over debug prints in visitors/services.
- Change port via `-Dserver.port=8081` or Spring config in `src/main/resources`.
- Validate input; return errors with `message` and non-200 status.
