# Repository Guidelines

## Project Structure & Module Organization
`backend/` contains the Spring Boot 3 + MyBatis API: Java code in `backend/src/main/java/com/movie/backend`, tests in `backend/src/test/java`, and SQL in `backend/sql`. `frontend/` is the Vue 3 + TypeScript app with source in `frontend/src`; Orval-generated files under `frontend/src/api/**` are ignored. Analytics code lives in `spark/jobs`, shared helpers in `spark/utils`, Hive DDLs in `hive/{ods,dwd,dws,ads}`, and scheduler exports in `dolphinscheduler/workflows`. See `backend/AGENTS.md` and `frontend/AGENTS.md` for deeper module rules.

## Build, Test, and Development Commands
- `cd backend && mvn spring-boot:run` starts the backend locally.
- `cd backend && mvn test` runs JUnit and MockMvc tests.
- `cd backend && mvn clean package` builds the backend JAR.
- `cd frontend && pnpm install` installs frontend dependencies.
- `cd frontend && pnpm dev` starts Vite after checking generated API files.
- `cd frontend && pnpm build` runs `vue-tsc` and creates a production bundle.
- `cd frontend && pnpm run api:generate` regenerates the ignored API client from `http://localhost:8080/v3/api-docs`.
- `cd spark && cp conf/etl_config.example.json conf/etl_config.json` prepares ETL config; run jobs with the `spark-submit` examples in `spark/README.md`.

## Coding Style & Naming Conventions
Use 4-space indentation in Java and Python, and match nearby formatting in Vue and TypeScript files. Java classes follow existing suffixes: `*Controller`, `*Service`, `*Mapper`, `*DTO`, and `*VO`. Vue SFCs should use `<script setup lang="ts">`, PascalCase filenames such as `MovieDetailView.vue`, and lowercase feature folders such as `components/comment`. No repo-wide formatter or lint script is enforced, so avoid broad reformatting.

## Testing Guidelines
Backend changes should include or update tests named `*Test` or `*IntegrationTest`; prefer MockMvc-based controller coverage unless true integration behavior is required. Frontend currently has no automated test script, so `pnpm build` is the minimum verification step for UI changes. Spark and Hive changes should include a reproducible command or output check in the PR.

## Commit & Pull Request Guidelines
Recent history mixes Conventional Commits and plain imperative subjects. Prefer `feat(frontend): ...`, `fix(backend): ...`, or `chore: ...` so scope is obvious. Keep each commit focused. PRs should summarize affected modules, list verification commands, note schema or config changes, and include screenshots for UI updates. If backend API contracts change, say whether contributors must rerun `pnpm run api:generate`.

## Security & Configuration Tips
Never commit secrets or machine-specific config. Use `backend/src/main/resources/application-dev.yml` for local backend overrides and `VITE_`-prefixed variables for frontend env values. Do not commit generated frontend API artifacts or local runtime data under `data/`.
