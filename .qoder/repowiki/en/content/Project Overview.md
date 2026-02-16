# Project Overview

<cite>
**Referenced Files in This Document**
- [BackendApplication.java](file://backend/src/main/java/com/movie/backend/BackendApplication.java)
- [pom.xml](file://backend/pom.xml)
- [application.yml](file://backend/src/main/resources/application.yml)
- [SecurityConfig.java](file://backend/src/main/java/com/movie/backend/config/SecurityConfig.java)
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java)
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java)
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java)
- [User.java](file://backend/src/main/java/com/movie/backend/entity/User.java)
- [movie_db.sql](file://backend/sql/movie_db.sql)
- [App.tsx](file://movie-review-web/src/App.tsx)
- [package.json](file://movie-review-web/package.json)
- [user.ts](file://movie-review-web/src/api/user.ts)
- [movie.ts](file://movie-review-web/src/api/movie.ts)
- [Home.tsx](file://movie-review-web/src/pages/Home.tsx)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx)
</cite>

## Table of Contents
1. [Introduction](#introduction)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Architecture Overview](#architecture-overview)
5. [Detailed Component Analysis](#detailed-component-analysis)
6. [Dependency Analysis](#dependency-analysis)
7. [Performance Considerations](#performance-considerations)
8. [Troubleshooting Guide](#troubleshooting-guide)
9. [Conclusion](#conclusion)
10. [Appendices](#appendices)

## Introduction
Movie System is a full-stack movie review and discovery platform designed to help users browse, discover, rate, and review movies. It emphasizes a modern, responsive web experience powered by a React TypeScript frontend and a robust Java Spring Boot backend. The platform supports user registration and authentication, a comprehensive movie catalog with advanced search and filtering, a rating and review system, and social features such as favorites and viewing history.

Core value proposition:
- Discover movies efficiently via hot/recommended lists, genres, years, and keyword search.
- Build a personalized collection with favorites and custom folders.
- Engage with the community through reviews and likes.
- Maintain a seamless experience with secure authentication and responsive UI.

Key features:
- User management: registration, login/logout, profile updates, and token refresh.
- Movie catalog: detailed views, hot/recommended lists, genre/year filters, and search.
- Review and rating system: submit, update, and manage personal ratings.
- Social features: favorites, custom folders, browsing history, and public profiles.

Technology stack:
- Backend: Java Spring Boot (Web, Security, Validation, MyBatis, Redis, Swagger/OpenAPI, PageHelper).
- Frontend: React 19 with TypeScript, React Router 7, TanStack React Query 5, Axios, TailwindCSS.
- Database: MySQL 8.0 with JSON fields for actors/directors/writers.
- Additional: JWT for stateless authentication, Redis for caching, Druid for connection pooling.

System boundaries:
- Backend exposes REST APIs under /user, /movie, /rating, /favorite, /comment, and related endpoints.
- Frontend consumes these APIs and renders pages for browsing, searching, reviewing, and managing user data.

## Project Structure
The repository is organized into two primary modules:
- backend: Spring Boot application with controllers, services, mappers, entities, configurations, and database scripts.
- movie-review-web: React SPA with routing, API clients, pages, components, and shared types.

```mermaid
graph TB
subgraph "Backend (Spring Boot)"
BA["BackendApplication.java"]
CFG["SecurityConfig.java"]
INT["JwtInterceptor.java"]
UC["UserController.java"]
MC["MovieController.java"]
ENT["User.java"]
DB["MySQL schema (movie_db.sql)"]
end
subgraph "Frontend (React TypeScript)"
APP["App.tsx"]
HOME["Home.tsx"]
AUTH["AuthContext.tsx"]
UAPI["user.ts"]
MAPI["movie.ts"]
PKG["package.json"]
end
APP --> UAPI
APP --> MAPI
HOME --> MAPI
AUTH --> UAPI
UAPI --> UC
MAPI --> MC
UC --> CFG
MC --> CFG
CFG --> INT
INT --> DB
DB --> ENT
```

**Diagram sources**
- [BackendApplication.java](file://backend/src/main/java/com/movie/backend/BackendApplication.java#L1-L17)
- [SecurityConfig.java](file://backend/src/main/java/com/movie/backend/config/SecurityConfig.java#L1-L51)
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java#L1-L105)
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java#L1-L130)
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java#L1-L209)
- [User.java](file://backend/src/main/java/com/movie/backend/entity/User.java#L1-L46)
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)
- [App.tsx](file://movie-review-web/src/App.tsx#L1-L50)
- [Home.tsx](file://movie-review-web/src/pages/Home.tsx#L1-L65)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)
- [user.ts](file://movie-review-web/src/api/user.ts#L1-L36)
- [movie.ts](file://movie-review-web/src/api/movie.ts#L1-L65)
- [package.json](file://movie-review-web/package.json#L1-L42)

**Section sources**
- [BackendApplication.java](file://backend/src/main/java/com/movie/backend/BackendApplication.java#L1-L17)
- [pom.xml](file://backend/pom.xml#L1-L300)
- [application.yml](file://backend/src/main/resources/application.yml#L1-L4)
- [App.tsx](file://movie-review-web/src/App.tsx#L1-L50)
- [package.json](file://movie-review-web/package.json#L1-L42)

## Core Components
- Backend entrypoint and configuration:
  - Application bootstrap and scheduling enablement.
  - Security configuration enabling method-level authorization and stateless JWT handling.
  - JWT interceptor validating tokens, setting Spring Security context, and populating thread-local user context.
- Controllers:
  - User management endpoints for login, register, info retrieval, avatar update, token refresh, logout, and password change.
  - Movie catalog endpoints for detail, search, hot/recommended lists, genre/year filters, latest releases, and filter metadata.
- Entities and persistence:
  - User entity with role, status, avatar, and password versioning for token invalidation upon password changes.
  - Database schema supporting movies, persons, ratings, comments, favorites, favorite folders, and view history.
- Frontend:
  - Routing with public and protected routes.
  - API clients for user and movie operations.
  - Pages for home, search, movie detail, favorites, ratings, and profile.
  - Authentication context managing tokens and user state with localStorage persistence.

Practical examples:
- Movie browsing:
  - Fetch hot and recommended movies on the home page.
  - Navigate to movie detail and record view history for logged-in users.
- User registration:
  - Submit registration form and automatically log in the new user.
- Review submission:
  - Submit or update a rating for a movie via the movie detail page.

**Section sources**
- [SecurityConfig.java](file://backend/src/main/java/com/movie/backend/config/SecurityConfig.java#L1-L51)
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java#L1-L105)
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java#L1-L130)
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java#L1-L209)
- [User.java](file://backend/src/main/java/com/movie/backend/entity/User.java#L1-L46)
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)
- [Home.tsx](file://movie-review-web/src/pages/Home.tsx#L1-L65)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)
- [user.ts](file://movie-review-web/src/api/user.ts#L1-L36)
- [movie.ts](file://movie-review-web/src/api/movie.ts#L1-L65)

## Architecture Overview
The system follows a clean separation of concerns:
- Frontend (React) handles UI, routing, state, and API interactions.
- Backend (Spring Boot) provides REST APIs, business logic, security, and persistence.
- Database stores structured and semi-structured data (JSON for cast/crew).
- Caching and session management leverage Redis and JWT respectively.

```mermaid
graph TB
FE["React Frontend<br/>Routing + Queries + Auth"]
BE["Spring Boot Backend<br/>Controllers + Services + Security"]
DB["MySQL Database<br/>Tables: users, movies, ratings, comments, favorites, view_history"]
REDIS["Redis<br/>Caching & Blacklist"]
FE --> |HTTP/HTTPS| BE
BE --> |MyBatis| DB
BE --> |Redis| REDIS
BE --> |JWT| FE
```

**Diagram sources**
- [App.tsx](file://movie-review-web/src/App.tsx#L1-L50)
- [pom.xml](file://backend/pom.xml#L1-L300)
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)

## Detailed Component Analysis

### Authentication and Authorization Flow
End-to-end flow for login and protected access:
```mermaid
sequenceDiagram
participant Client as "Browser"
participant Frontend as "React App"
participant API as "UserController"
participant Sec as "SecurityConfig"
participant Interc as "JwtInterceptor"
participant DB as "MySQL"
Client->>Frontend : "User submits login form"
Frontend->>API : "POST /user/login"
API->>DB : "Validate credentials"
DB-->>API : "User exists"
API-->>Frontend : "Return tokens and user info"
Frontend->>Frontend : "Persist tokens in localStorage"
Note over Frontend : "Subsequent requests include Authorization header"
Client->>Frontend : "Navigate to protected route"
Frontend->>Sec : "Route resolves"
Sec->>Interc : "Invoke JWT interceptor"
Interc->>DB : "Verify token and blacklist"
DB-->>Interc : "Valid token"
Interc-->>Frontend : "Set SecurityContext + UserContext"
Frontend-->>Client : "Render protected content"
```

**Diagram sources**
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java#L1-L130)
- [SecurityConfig.java](file://backend/src/main/java/com/movie/backend/config/SecurityConfig.java#L1-L51)
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java#L1-L105)
- [user.ts](file://movie-review-web/src/api/user.ts#L1-L36)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)

**Section sources**
- [SecurityConfig.java](file://backend/src/main/java/com/movie/backend/config/SecurityConfig.java#L1-L51)
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java#L1-L105)
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java#L1-L130)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)
- [user.ts](file://movie-review-web/src/api/user.ts#L1-L36)

### Movie Catalog and Search Workflow
End-to-end flow for browsing and searching movies:
```mermaid
sequenceDiagram
participant Client as "Browser"
participant Frontend as "React Home Page"
participant API as "MovieController"
participant DB as "MySQL"
Client->>Frontend : "Open home page"
Frontend->>API : "GET /movie/hot?limit=12"
API->>DB : "Fetch top movies by votes"
DB-->>API : "Page of movies"
API-->>Frontend : "Hot movies data"
Frontend->>API : "GET /movie/recommended?limit=12"
API->>DB : "Fetch top movies by score"
DB-->>API : "Page of movies"
API-->>Frontend : "Recommended movies data"
Frontend-->>Client : "Render movie grids"
Client->>Frontend : "Search movies"
Frontend->>API : "POST /movie/search {keywords, filters}"
API->>DB : "Advanced search with pagination"
DB-->>API : "PageInfo<Movie>"
API-->>Frontend : "Search results"
Frontend-->>Client : "Display results"
```

**Diagram sources**
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java#L1-L209)
- [Home.tsx](file://movie-review-web/src/pages/Home.tsx#L1-L65)
- [movie.ts](file://movie-review-web/src/api/movie.ts#L1-L65)
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)

**Section sources**
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java#L1-L209)
- [Home.tsx](file://movie-review-web/src/pages/Home.tsx#L1-L65)
- [movie.ts](file://movie-review-web/src/api/movie.ts#L1-L65)
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)

### Data Model Overview
Core entities and relationships:
```mermaid
erDiagram
USERS {
varchar user_id PK
varchar user_nickname
varchar user_password
varchar user_avatar
varchar user_url
tinyint role
varchar email
timestamp create_time
timestamp update_time
}
MOVIES {
bigint movie_id PK
varchar name
text alias
json actors
varchar cover
json directors
decimal douban_score
int douban_votes
varchar genres
varchar imdb_id
varchar languages
varchar mins
varchar regions
varchar release_date
text storyline
int year
json writers
}
RATINGS {
varchar rating_id PK
varchar user_id FK
bigint movie_id FK
int rating
varchar rating_time
}
COMMENTS {
bigint comment_id PK
varchar user_id FK
bigint movie_id FK
text content
int votes
datetime comment_time
}
COMMENT_LIKES {
bigint id PK
bigint comment_id FK
varchar user_id FK
datetime create_time
}
FAVORITES {
varchar user_id PK,FK
bigint movie_id PK,FK
bigint folder_id
datetime create_time
}
FAVORITE_FOLDERS {
bigint id PK
varchar user_id FK
varchar name
varchar description
tinyint is_public
int movie_count
datetime create_time
datetime update_time
}
VIEW_HISTORY {
bigint history_id PK
varchar user_id FK
bigint movie_id FK
datetime view_time
}
USERS ||--o{ RATINGS : "rates"
USERS ||--o{ COMMENTS : "writes"
USERS ||--o{ COMMENT_LIKES : "likes"
MOVIES ||--o{ RATINGS : "rated_by"
MOVIES ||--o{ COMMENTS : "commented_on"
MOVIES ||--o{ FAVORITES : "favorited_by"
USERS ||--o{ FAVORITE_FOLDERS : "owns"
FAVORITE_FOLDERS ||--o{ FAVORITES : "contains"
USERS ||--o{ VIEW_HISTORY : "views"
```

**Diagram sources**
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)

**Section sources**
- [movie_db.sql](file://backend/sql/movie_db.sql#L1-L164)

### Frontend Routing and Protected Access
Frontend routing structure and protected route enforcement:
```mermaid
flowchart TD
Start(["App mounts"]) --> Routes["Define routes"]
Routes --> Public["Public routes:<br/>/, login, register, search, latest, person/:id, movie/:id"]
Routes --> Protected["Protected routes:<br/>profile, my-ratings, favorites, my-reviews, browsing-history"]
Protected --> Guard["ProtectedRoute checks AuthContext.isAuthenticated"]
Guard --> |True| Render["Render requested page"]
Guard --> |False| Redirect["Redirect to login"]
Public --> Render
Render --> End(["User navigates"])
Redirect --> End
```

**Diagram sources**
- [App.tsx](file://movie-review-web/src/App.tsx#L1-L50)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)

**Section sources**
- [App.tsx](file://movie-review-web/src/App.tsx#L1-L50)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)

## Dependency Analysis
Backend dependencies and their roles:
- Spring Boot starters: web, validation, security, data-redis, openapi/ui.
- MyBatis Spring Boot starter for ORM.
- MySQL connector and Druid for connection pooling.
- Redis for caching and token blacklist storage.
- JWT libraries for token generation/refresh/validation.
- PageHelper for pagination support.
- Hive JDBC included with exclusions and replacements to avoid conflicts.

Frontend dependencies and their roles:
- React 19, React Router 7 for routing.
- TanStack React Query 5 for server state management.
- Axios for HTTP client.
- TailwindCSS and related plugins for styling.

```mermaid
graph LR
subgraph "Backend Dependencies"
SWeb["spring-boot-starter-web"]
SSec["spring-boot-starter-security"]
SVal["spring-boot-starter-validation"]
SRedis["spring-boot-starter-data-redis"]
OpenAPI["springdoc-openapi-ui"]
MyBatis["mybatis-spring-boot-starter"]
MySQL["mysql-connector-java"]
Druid["druid-spring-boot-starter"]
JWT["jjwt-api/jjwt-impl/jjwt-jackson"]
PageHelper["pagehelper-spring-boot-starter"]
Netty["netty-all"]
end
subgraph "Frontend Dependencies"
R["react + react-dom"]
RR["react-router-dom"]
TQuery["@tanstack/react-query"]
Axios["axios"]
Tailwind["tailwindcss + plugins"]
end
```

**Diagram sources**
- [pom.xml](file://backend/pom.xml#L1-L300)
- [package.json](file://movie-review-web/package.json#L1-L42)

**Section sources**
- [pom.xml](file://backend/pom.xml#L1-L300)
- [package.json](file://movie-review-web/package.json#L1-L42)

## Performance Considerations
- Pagination: Use page and size parameters for list endpoints to avoid large payloads.
- Caching: Leverage Redis for frequently accessed metadata and reduce DB load.
- Token management: Stateless JWT eliminates session overhead; maintain blacklist for revoked tokens.
- Database indexing: Ensure proper indexes on foreign keys and frequently filtered columns (e.g., movie_id, user_id).
- Frontend caching: React Query’s built-in caching reduces redundant network calls.

## Troubleshooting Guide
Common issues and resolutions:
- Unauthorized access:
  - Verify Authorization header presence and validity.
  - Confirm token is not blacklisted and matches the user context.
- Token refresh failures:
  - Ensure refresh token is provided and valid; backend returns a new access token on success.
- Registration/login errors:
  - Check DTO validation and backend error responses.
  - Confirm localStorage persistence for tokens and user data.
- Search/filter anomalies:
  - Validate search DTO payload and pagination parameters.
  - Confirm database filter metadata endpoints return expected segments.

**Section sources**
- [JwtInterceptor.java](file://backend/src/main/java/com/movie/backend/config/JwtInterceptor.java#L1-L105)
- [UserController.java](file://backend/src/main/java/com/movie/backend/controller/UserController.java#L1-L130)
- [MovieController.java](file://backend/src/main/java/com/movie/backend/controller/MovieController.java#L1-L209)
- [AuthContext.tsx](file://movie-review-web/src/context/AuthContext.tsx#L1-L123)

## Conclusion
Movie System delivers a modern, scalable platform for movie discovery and engagement. Its clean separation of concerns, robust authentication with JWT, and comprehensive catalog APIs enable a smooth user experience. The React frontend integrates seamlessly with Spring Boot backend services, supported by a relational database with JSON fields for flexible casting/crew data. By following the outlined patterns and best practices, teams can extend functionality while maintaining performance and reliability.

## Appendices
- Environment configuration:
  - Active profile is dev, enabling development-specific settings.
- API exposure:
  - OpenAPI/Swagger UI available for endpoint exploration and testing.

**Section sources**
- [application.yml](file://backend/src/main/resources/application.yml#L1-L4)