# Frontend

Vue 3 + TypeScript + Vite frontend for the movie system.

## Prerequisites

- Node.js with `pnpm`
- Backend running locally at `http://localhost:8080`
- OpenAPI document available at `http://localhost:8080/v3/api-docs`

## API Client Generation

This project does not commit Orval-generated API client files.

Generated artifacts include:

- `src/api/endpoints/**`
- `src/api/model/**`
- `src/api/index.ts`
- `src/api/types.ts`

Generate the client after cloning the repo, after backend API changes, or whenever the generated files are missing:

```bash
pnpm run api:generate
```

Check whether generated files are present:

```bash
pnpm run api:check
```

If `api:check` fails, start the backend first and confirm `http://localhost:8080/v3/api-docs` is reachable.

## Development Commands

Install dependencies:

```bash
pnpm install
```

Start development server:

```bash
pnpm run dev
```

Build for production:

```bash
pnpm run build
```

Preview the production build:

```bash
pnpm run preview
```
