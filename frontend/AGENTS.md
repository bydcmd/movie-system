# Repository Guidelines

## Project Structure & Module Organization
This is a Vue 3 + TypeScript app built with Vite.
- `src/` contains application code.
- `src/views/` holds page-level Vue SFCs (e.g., `HomeView.vue`, `MoviesView.vue`).
- `src/components/` contains reusable UI components, grouped by feature (e.g., `components/movie/`).
- `src/router/` defines routes, `src/stores/` contains state stores, and `src/api/` contains API helpers.
- `src/assets/` holds bundled assets, while `public/` serves static files as-is.
- Entry points and config live at the root: `index.html`, `vite.config.ts`, `tsconfig*.json`.

## Build, Test, and Development Commands
Use `pnpm` (lockfile present).
- `pnpm install` installs dependencies.
- `pnpm dev` starts the Vite dev server.
- `pnpm build` runs type-checking (`vue-tsc -b`) and creates a production build.
- `pnpm preview` serves the built app for local preview.

## Coding Style & Naming Conventions
- Vue SFCs use `<script setup lang="ts">`.
- Prefer single quotes in TypeScript/JavaScript imports and strings (see `src/App.vue`).
- Component and view filenames use PascalCase (`MovieDetailView.vue`).
- Keep folders lowercase and feature-based (`components/auth`, `components/layout`).
- Follow existing formatting in the file you touch; no formatter is configured yet.

## Testing Guidelines
No test framework or `test` script is currently configured. If you add tests, also add a script in `package.json` and document how to run them here.

## Commit & Pull Request Guidelines
Use Conventional Commits (e.g., `feat: add movie filters`, `fix: handle empty search`, `chore: update deps`).
- PRs should include a clear summary of changes.
- Include screenshots or a short clip for UI changes.
- Link any related issues or tickets.

## Configuration Notes
This is a Vite project; if you introduce environment variables, use the `VITE_` prefix and document them in the README.
