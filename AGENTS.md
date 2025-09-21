# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot app (`com.sqlparser.SqlParserServerApplication`), REST controller (`controller.SqlParserController`), service (`service.SqlParserService`), DTOs (`model.*`), and Trino visitor (`visitor.TableNameExtractor`).
- `src/main/resources`: Spring configuration/resources.
- `src/test/java`: JUnit tests; classes end with `*Test.java` mirroring main packages.
- `pom.xml`: Maven build; Java 21, Spring Boot 3, Trino parser.
- `target/`: Build outputs.

## Build, Test, and Development Commands
- `./mvnw clean verify` — compile and run tests.
- `./mvnw spring-boot:run` — start locally at `http://localhost:8080`.
- `./mvnw package` — build runnable jar at `target/sql-parser-server-1.0.0.jar`.
- `java -jar target/sql-parser-server-1.0.0.jar` — run packaged app.
- Quick smoke: `curl http://localhost:8080/api/sql/health`.

## Coding Style & Naming Conventions
- Java 21; 4-space indent, UTF-8; keep lines reasonably short (~120 cols).
- Packages: `com.sqlparser.*`; classes UpperCamelCase; methods/fields lowerCamelCase.
- Use `slf4j` logger; avoid `System.out` (okay in tests). Keep controllers thin; push logic to services.
- DTOs are simple POJOs; avoid Lombok unless added to `pom.xml`.

## Testing Guidelines
- Frameworks: Spring Boot Test + JUnit (Vintage engine enabled). Use `@SpringBootTest` when wiring Spring.
- Location/naming: `src/test/java/...` with `*Test.java`.
- Run: `./mvnw test`. Focus on parsing edge cases (joins, subqueries, schemas, unions, CTE-like patterns).

## Commit & Pull Request Guidelines
- Current history uses short prefixes (e.g., `fea:`, `init`). Prefer `feat|fix|docs|test|refactor: short summary` (present tense). Mixed English/Chinese is fine.
- Reference issues in the body (e.g., `Fixes #12`). Note API or behavior changes.
- PRs should include: purpose, linked issues, how to reproduce/verify, and sample SQL or request/response snippets.

## Security & Configuration Tips (Optional)
- Remove debug prints from visitors/services before release; rely on `slf4j` levels.
- Change port with `-Dserver.port=8081` or Spring config in `src/main/resources`.
- Validate input; surface errors via response `message` with non-200 statuses.
