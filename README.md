# Movie System

Movie System is a monorepo that combines a Spring Boot backend, a Vue 3 frontend, and a Spark + Hive analytics pipeline.

## Workspace Layout

- `backend/`: Spring Boot 3 + MyBatis API, SQL migrations, and backend tests.
- `frontend/`: Vue 3 + TypeScript + Vite application.
- `spark/`: Spark ETL jobs, wrapper scripts, and local runtime config examples.
- `hive/`: Hive DDLs, with the active compact 4-table warehouse under `hive/compact/` and archived layered DDLs under `hive/legacy/`.
- `dolphinscheduler/`: exported workflow definitions for scheduling.

## Quick Start

1. Prepare backend local config:

```bash
cp backend/src/main/resources/application-dev.example.yml backend/src/main/resources/application-dev.yml
```

2. Start the backend:

```bash
cd backend && mvn spring-boot:run
```

3. Install frontend dependencies:

```bash
cd frontend && pnpm install
```

4. Generate frontend API bindings when needed:

```bash
cd frontend && pnpm run api:generate
```

5. Start the frontend:

```bash
cd frontend && pnpm dev
```

6. Prepare Spark local config when running ETL jobs:

```bash
cd spark && cp conf/etl_config.example.json conf/etl_config.json
```

## Local-Only Files

These files are intentionally ignored and should stay local to each developer:

- `backend/src/main/resources/application-dev.yml`
- `spark/conf/etl_config.json`
- `spark/.venv/`
- generated frontend API client files under `frontend/src/api/**`

Tracked generated TypeScript declaration files such as `frontend/auto-imports.d.ts` and `frontend/components.d.ts` are kept because the current frontend type-checking setup consumes them directly.

## Repo Guides

- root conventions: `AGENTS.md`
- backend conventions: `backend/AGENTS.md`
- frontend conventions: `frontend/AGENTS.md`
- frontend API generation details: `frontend/README.md`
- Spark and Hive runbook: `spark/README.md`
- event contract summary: `message-events.md`
