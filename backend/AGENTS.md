# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/movie/backend`: Spring Boot application code.
- `controller/`: REST endpoints (includes `controller/admin/`).
- `service/` and `service/impl/`: service interfaces and implementations.
- `mapper/`: MyBatis mappers; SQL XML files live under `src/main/resources/mapper`.
- `entity/`, `dto/`, `common/`, `config/`, `exception/`, `utils/`, `messaging/`.
- `src/main/resources`: configuration (`application.yml`, tracked `application-dev.example.yml`, ignored local `application-dev.yml`) and mapper XMLs.
- `src/test/java/com/movie/backend`: tests for controllers/services; see `controller/README_MOVIE_TEST.md`.
- `sql/`: schema and migration scripts (e.g., `sql/movie_db.sql`).

## Build, Test, and Development Commands
- `mvn clean package`: build the JAR.
- `mvn test`: run all tests.
- `mvn test -Dtest=MovieControllerIntegrationTest`: run a single test class.
- `mvn spring-boot:run`: run the application locally.

## Coding Style & Naming Conventions
- Java 17, Spring Boot 3.x, MyBatis.
- Indentation: 4 spaces. Keep imports sorted and remove unused imports.
- Naming patterns used in code:
  - Controllers: `*Controller`
  - Services: `*Service`, implementations `*ServiceImpl`
  - Mappers: `*Mapper`
  - Entities: domain nouns (e.g., `Movie`, `User`)
  - DTO/VO classes: `*DTO`, `*VO`
- Use Lombok where already present; avoid mixing Lombok and manual boilerplate in the same class.

## Testing Guidelines
- Framework: JUnit via `spring-boot-starter-test` with MockMvc for controller tests.
- Test naming: `*Test` or `*IntegrationTest` (see `MovieControllerIntegrationTest`).
- Prefer isolated controller tests with mocked services unless integration behavior is required.

## Commit & Pull Request Guidelines
- Commit history uses concise, free-form summaries (often sentence-style, sometimes Chinese).
- Use a short, descriptive subject line; add details in the body if needed.
- PRs should include:
  - Summary of changes and affected endpoints/modules.
  - Steps to test (commands + any data setup).
  - Screenshots only if UI/API output is user-facing.

## Security & Configuration Tips
- Copy `src/main/resources/application-dev.example.yml` to `src/main/resources/application-dev.yml` for local secrets and machine-specific overrides.
- Avoid committing credentials or tokens; keep `application-dev.yml` local-only.
