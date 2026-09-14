# IAM Service

A Spring Boot Identity & Access Management system: authentication (JWT access
tokens + opaque rotating refresh tokens) plus full role-based access control
(users, roles, permissions) with an admin API to manage all three.

## Table of contents

- [Stack](#stack)
- [Project structure](#project-structure)
- [Data model](#data-model)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
  - [Option A: Docker Compose](#option-a-docker-compose-recommended)
  - [Option B: run locally against your own MySQL](#option-b-run-locally-against-your-own-mysql)
  - [Running tests](#running-tests)
- [Configuration reference](#configuration-reference)
- [How authorization works](#how-authorization-works)
- [Error response format](#error-response-format)
- [API documentation](#api-documentation)
  - [Authentication](#authentication)
  - [Users](#users)
  - [Roles](#roles)
  - [Permissions](#permissions)
- [Interactive API docs (Swagger)](#interactive-api-docs-swagger)

## Stack

- Java 21, Spring Boot 3.3, Gradle (wrapper included, no local install needed)
- Spring Security 6 — stateless JWT authentication, method-level `@PreAuthorize`
- Spring Data JPA + MySQL, Flyway migrations
- Caffeine cache in front of role/permission resolution (see [How authorization works](#how-authorization-works))
- springdoc-openapi (Swagger UI / OpenAPI 3 spec)
- JUnit 5, Spring MockMvc, H2 (test-only)

## Project structure

```
src/main/java/com/iam/
  IamApplication.java          entry point
  config/                      SecurityConfig, CacheConfig, JwtProperties, CorsProperties, OpenApiConfig
  domain/                      JPA entities: User, Role, Permission, RefreshToken
  repository/                  Spring Data repositories
  security/                    JwtService, JwtAuthenticationFilter, UserPrincipal,
                                CustomUserDetailsService, RefreshTokenGenerator,
                                AuthEntryPointJwt, AccessDeniedHandlerImpl
  service/                     AuthService, UserService, RoleService, PermissionService
  controller/                  AuthController, UserController, RoleController, PermissionController
  dto/request|response/        request/response records
  exception/                   GlobalExceptionHandler + typed exceptions

src/main/resources/
  application.yml              base config (env-var overridable)
  application-dev.yml          dev profile (verbose logging)
  application-prod.yml         prod profile
  db/migration/                Flyway SQL migrations (schema + seed data)

src/test/java/com/iam/         MockMvc integration tests + unit tests (H2, Flyway disabled)
```

## Data model

- **User** — account + credentials (`enabled`, `accountNonLocked` flags), many-to-many with Role
- **Role** — named group of permissions, many-to-many with Permission
- **Permission** — a single grantable action, e.g. `USER_WRITE`, `ROLE_READ`
- **RefreshToken** — SHA-256 hash of an opaque token, single-use, revocable

`V2__seed_data.sql` seeds a starter RBAC setup:

| Role      | Permissions                                                        |
|-----------|---------------------------------------------------------------------|
| `ADMIN`   | every permission (the "super admin" role)                          |
| `MANAGER` | `USER_READ`, `USER_WRITE`, `ROLE_READ`, `PERMISSION_READ`           |
| `USER`    | none — default role given to self-registered accounts               |

Seeded permissions: `USER_READ`, `USER_WRITE`, `USER_DELETE`, `ROLE_READ`,
`ROLE_WRITE`, `ROLE_DELETE`, `PERMISSION_READ`, `PERMISSION_WRITE`,
`PERMISSION_DELETE`.

A default admin account is seeded — **username `admin`, password `Admin@12345`**.
**Change this password immediately** in any shared or non-local environment.

Because `ADMIN` holds every permission, that account can create new
permissions, build new roles out of them, and assign any role to any user —
the full super-admin workflow — purely through the HTTP API (see below).

## Prerequisites

- JDK 21 (only needed if not using Docker — the Gradle wrapper handles Gradle itself)
- Docker + Docker Compose (for the easiest path), **or**
- A local MySQL 8+ instance if running outside Docker

## Setup

### Option A: Docker Compose (recommended)

Starts MySQL and the service together; Flyway migrates the schema on boot.

```bash
git clone <this-repo>
cd rbac
docker compose up --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Override the JWT secret used by the compose stack:

```bash
JWT_SECRET=$(openssl rand -base64 48) docker compose up --build
```

Stop and remove containers (keeps the named MySQL volume):

```bash
docker compose down
```

### Option B: run locally against your own MySQL

1. Create the database and user:

   ```sql
   CREATE DATABASE iamdb CHARACTER SET utf8mb4;
   CREATE USER 'iam'@'%' IDENTIFIED BY 'iam';
   GRANT ALL PRIVILEGES ON iamdb.* TO 'iam'@'%';
   FLUSH PRIVILEGES;
   ```

2. Export configuration (or copy `application.yml` and edit it directly):

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/iamdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   export DB_USERNAME=iam
   export DB_PASSWORD=iam
   export JWT_SECRET=some-secret-at-least-32-bytes-long
   export SPRING_PROFILES_ACTIVE=dev
   ```

3. Run it:

   ```bash
   ./gradlew bootRun
   ```

   Flyway applies `V1__init_schema.sql` and `V2__seed_data.sql` automatically
   on startup — no manual migration step needed.

4. Build a runnable jar instead, if you prefer:

   ```bash
   ./gradlew bootJar
   java -jar build/libs/iam-service-0.0.1-SNAPSHOT.jar
   ```

### Running tests

```bash
./gradlew test
```

Tests run against an in-memory H2 database with `ddl-auto=create-drop` and
Flyway disabled (`src/test/resources/application-test.yml`), so no external
services are required. Baseline roles/permissions are seeded per-test by
`TestDataSeeder` in place of the Flyway migration.

## Configuration reference

All settings live in `src/main/resources/application.yml` and are overridable
by environment variable:

| Property                          | Env var                  | Default                                   | Notes |
|------------------------------------|---------------------------|--------------------------------------------|-------|
| `spring.datasource.url`           | `DB_URL`                 | `jdbc:mysql://localhost:3306/iamdb?...`   | |
| `spring.datasource.username`      | `DB_USERNAME`             | `iam`                                      | |
| `spring.datasource.password`      | `DB_PASSWORD`             | `iam`                                      | |
| `server.port`                     | `SERVER_PORT`             | `8080`                                     | |
| `spring.profiles.active`          | `SPRING_PROFILES_ACTIVE`  | `dev`                                      | `dev` or `prod` |
| `iam.jwt.secret`                  | `JWT_SECRET`              | *(dev-only placeholder — change this)*     | HS-SHA secret, ≥32 bytes recommended |
| `iam.jwt.access-token-ttl-minutes`| `JWT_ACCESS_TTL_MINUTES`  | `15`                                       | |
| `iam.jwt.refresh-token-ttl-days`  | `JWT_REFRESH_TTL_DAYS`    | `7`                                        | |
| `iam.cors.allowed-origins`        | `CORS_ALLOWED_ORIGINS`    | `http://localhost:3000`                    | comma-separated |

## How authorization works

Every authenticated request passes through `JwtAuthenticationFilter`:

1. **Signature/expiry check** — the JWT is verified in memory (HS-signed, no
   I/O). Access tokens are short-lived (15 minutes by default).
2. **Authority resolution** — the filter then asks `CustomUserDetailsService`
   for the user's *current* roles/permissions rather than trusting a claim
   embedded in the token. This is what makes a revoked role or a disabled
   account take effect quickly, even before the access token itself expires.

Step 2 would otherwise mean a database round-trip on every request, so it's
backed by a short-TTL (60s) Caffeine cache (`CacheConfig`). To avoid waiting
out that TTL for security-relevant changes, `UserService` / `RoleService` /
`PermissionService` evict the affected cache entries the instant an admin
changes a user's roles or enabled/locked state, or a role's/permission's
definition — so those changes apply on the very next request, not just
"eventually."

Refresh tokens are opaque random values; only their SHA-256 hash is ever
stored. Each one is single-use: refreshing rotates it (the old one is
immediately invalidated), and logout or a password change revokes all of a
user's outstanding refresh tokens.

## Error response format

All error responses share one JSON shape:

```json
{
  "timestamp": "2026-09-12T20:34:10.498Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found: 42",
  "path": "/api/users/42",
  "fieldErrors": null
}
```

`fieldErrors` is populated only for request-validation failures (HTTP 400),
mapping field name to the validation message, e.g.:

```json
{
  "timestamp": "2026-09-12T20:34:10.498Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 100"
  }
}
```

| Status | Meaning |
|--------|---------|
| 400 | Request validation failed, or a business-rule bad request (e.g. wrong current password) |
| 401 | Missing/invalid/expired token, bad login credentials, or an invalid/expired/revoked refresh token |
| 403 | Authenticated, but missing the required permission; or account disabled/locked |
| 404 | Referenced resource (user/role/permission) does not exist |
| 409 | Duplicate resource (username, email, role name, or permission name already exists) |
| 500 | Unexpected server error |

## API documentation

All endpoints are rooted at `/api`. Protected endpoints require
`Authorization: Bearer <accessToken>`. Endpoints under **Users / Roles /
Permissions** additionally require the specific permission noted per
endpoint — see [Data model](#data-model) for which roles carry which
permissions.

### Authentication

Base path: `/api/auth` — all endpoints are public (no token required).

#### `POST /api/auth/register`

Self-registers a new account; it is granted the `USER` role. Returns tokens
immediately, same as login.

Request body (`RegisterRequest`):

| Field | Type | Rules |
|-------|------|-------|
| `username` | string | required, 3–50 chars, `[a-zA-Z0-9._-]+` |
| `email` | string | required, valid email, ≤255 chars |
| `password` | string | required, 8–100 chars |
| `firstName` | string | optional, ≤100 chars |
| `lastName` | string | optional, ≤100 chars |

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
        "username": "jdoe",
        "email": "jdoe@example.com",
        "password": "SuperSecret1",
        "firstName": "John",
        "lastName": "Doe"
      }'
```

**201 Created** (`AuthResponse`):

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "9Alp0MibjGGqWpCzRER8Iu3SXO3fcGFTFSh6mtl4Vofv1vroRstT6Xq5fin4JwkpL-heQYzdJt6yG1NXcSEBhg",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

Errors: `400` validation, `409` username or email already taken.

#### `POST /api/auth/login`

Request body (`LoginRequest`): `username`, `password` (both required).

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "Admin@12345"}'
```

**200 OK** — same `AuthResponse` shape as register.

Errors: `401` bad credentials, `403` account disabled or locked.

#### `POST /api/auth/refresh`

Rotates a refresh token: the token supplied is invalidated and a new
access/refresh pair is issued. Reusing an already-rotated or expired token
fails.

Request body (`RefreshTokenRequest`): `refreshToken` (required).

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "9Alp0MibjGGqWpCzRER8Iu3SXO3fcGFTFSh6mtl4Vofv1vroRstT6Xq5fin4JwkpL-heQYzdJt6yG1NXcSEBhg"}'
```

**200 OK** — new `AuthResponse`.

Errors: `401` invalid, expired, revoked, or already-used token.

#### `POST /api/auth/logout`

Revokes a single refresh token (e.g. on sign-out). Does not require a bearer
access token — pass the refresh token to invalidate.

Request body (`RefreshTokenRequest`): `refreshToken` (required).

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "9Alp0MibjGGqWpCzRER8Iu3SXO3fcGFTFSh6mtl4Vofv1vroRstT6Xq5fin4JwkpL-heQYzdJt6yG1NXcSEBhg"}'
```

**204 No Content**.

### Users

Base path: `/api/users` — all require a valid access token.

#### `GET /api/users/me`

Returns the caller's own profile. No special permission required.

```bash
curl http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

**200 OK** (`UserResponse`):

```json
{
  "id": 2,
  "username": "jdoe",
  "email": "jdoe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "accountNonLocked": true,
  "roles": ["USER"],
  "createdAt": "2026-09-12T20:34:30.386929Z",
  "updatedAt": "2026-09-12T20:34:30.386929Z"
}
```

#### `PATCH /api/users/me/password`

Changes the caller's own password. Revokes all of the caller's existing
refresh tokens (they must log in again on other devices/sessions).

Request body (`ChangePasswordRequest`):

| Field | Type | Rules |
|-------|------|-------|
| `currentPassword` | string | required |
| `newPassword` | string | required, 8–100 chars |

```bash
curl -X PATCH http://localhost:8080/api/users/me/password \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"currentPassword": "SuperSecret1", "newPassword": "EvenBetter2"}'
```

**204 No Content**. Errors: `400` current password incorrect.

#### `GET /api/users` — requires `USER_READ`

Paginated list. Standard Spring `Pageable` query params: `page` (0-based,
default 0), `size` (default 20), `sort` (e.g. `sort=username,asc`).

```bash
curl "http://localhost:8080/api/users?page=0&size=20&sort=username,asc" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**200 OK** — a Spring `Page<UserResponse>`:

```json
{
  "content": [ { "id": 1, "username": "admin", "...": "..." } ],
  "pageable": { "pageNumber": 0, "pageSize": 20, "...": "..." },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "numberOfElements": 1
}
```

#### `GET /api/users/{id}` — requires `USER_READ`

```bash
curl http://localhost:8080/api/users/2 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**200 OK** — `UserResponse`. Errors: `404` no such user.

#### `POST /api/users` — requires `USER_WRITE`

Admin-creates a user with an explicit role set (defaults to `USER` if
`roles` is omitted/empty).

Request body (`CreateUserRequest`):

| Field | Type | Rules |
|-------|------|-------|
| `username` | string | required, 3–50 chars, `[a-zA-Z0-9._-]+` |
| `email` | string | required, valid email, ≤255 chars |
| `password` | string | required, 8–100 chars |
| `firstName` | string | optional, ≤100 chars |
| `lastName` | string | optional, ≤100 chars |
| `roles` | string[] | optional; each must name an existing role |

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{
        "username": "manager1",
        "email": "manager1@example.com",
        "password": "SuperSecret1",
        "firstName": "Mia",
        "lastName": "Manager",
        "roles": ["MANAGER"]
      }'
```

**201 Created** — `UserResponse`. Errors: `409` duplicate username/email,
`404` unknown role name.

#### `PUT /api/users/{id}` — requires `USER_WRITE`

Partial update — omit any field to leave it unchanged.

Request body (`UpdateUserRequest`, all fields optional):

| Field | Type | Rules |
|-------|------|-------|
| `email` | string | valid email, ≤255 chars |
| `firstName` | string | ≤100 chars |
| `lastName` | string | ≤100 chars |
| `enabled` | boolean | disable to block login/authentication |
| `accountNonLocked` | boolean | set `false` to lock the account |

```bash
curl -X PUT http://localhost:8080/api/users/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

**200 OK** — `UserResponse`. Errors: `404`, `409` (email taken by someone else).

#### `PUT /api/users/{id}/roles` — requires `USER_WRITE`

Replaces the user's entire role set (not additive).

Request body (`AssignRolesRequest`): `roles` (string[], required, non-empty;
each must name an existing role).

```bash
curl -X PUT http://localhost:8080/api/users/2/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"roles": ["MANAGER"]}'
```

**200 OK** — `UserResponse` with the updated `roles`. Errors: `404` unknown
user or role name.

#### `DELETE /api/users/{id}` — requires `USER_DELETE`

Deletes the user and revokes all of their refresh tokens.

```bash
curl -X DELETE http://localhost:8080/api/users/2 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**204 No Content**. Errors: `404`.

### Roles

Base path: `/api/roles`.

#### `GET /api/roles` — requires `ROLE_READ`

```bash
curl http://localhost:8080/api/roles -H "Authorization: Bearer $ADMIN_TOKEN"
```

**200 OK** — `RoleResponse[]`:

```json
[
  {
    "id": 1,
    "name": "ADMIN",
    "description": "Full administrative access to the IAM system",
    "permissions": [ { "id": 1, "name": "USER_READ", "description": "View user accounts" } ]
  }
]
```

#### `GET /api/roles/{id}` — requires `ROLE_READ`

**200 OK** — `RoleResponse`. Errors: `404`.

#### `POST /api/roles` — requires `ROLE_WRITE`

Request body (`CreateRoleRequest`):

| Field | Type | Rules |
|-------|------|-------|
| `name` | string | required, ≤100 chars, must be unique |
| `description` | string | optional, ≤255 chars |
| `permissions` | string[] | optional; each must name an existing permission |

```bash
curl -X POST http://localhost:8080/api/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "REPORTER", "description": "Can view reports", "permissions": ["REPORT_VIEW"]}'
```

**201 Created** — `RoleResponse`. Errors: `409` role name exists, `404`
unknown permission name.

#### `PUT /api/roles/{id}` — requires `ROLE_WRITE`

Request body (`UpdateRoleRequest`): `description` (optional, ≤255 chars) —
currently the only mutable field besides permissions (see below).

```bash
curl -X PUT http://localhost:8080/api/roles/4 \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"description": "Can view and export reports"}'
```

**200 OK** — `RoleResponse`. Errors: `404`.

#### `PUT /api/roles/{id}/permissions` — requires `ROLE_WRITE`

Replaces the role's entire permission set (not additive) — this immediately
affects every user holding that role (see [How authorization works](#how-authorization-works)).

Request body (`AssignPermissionsRequest`): `permissions` (string[], required,
non-empty; each must name an existing permission).

```bash
curl -X PUT http://localhost:8080/api/roles/4/permissions \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"permissions": ["REPORT_VIEW", "REPORT_EXPORT"]}'
```

**200 OK** — `RoleResponse`. Errors: `404` unknown role or permission name.

#### `DELETE /api/roles/{id}` — requires `ROLE_DELETE`

```bash
curl -X DELETE http://localhost:8080/api/roles/4 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**204 No Content**. Errors: `404`.

### Permissions

Base path: `/api/permissions`.

#### `GET /api/permissions` — requires `PERMISSION_READ`

```bash
curl http://localhost:8080/api/permissions -H "Authorization: Bearer $ADMIN_TOKEN"
```

**200 OK** — `PermissionResponse[]`:

```json
[ { "id": 1, "name": "USER_READ", "description": "View user accounts" } ]
```

#### `POST /api/permissions` — requires `PERMISSION_WRITE`

Request body (`CreatePermissionRequest`):

| Field | Type | Rules |
|-------|------|-------|
| `name` | string | required, ≤100 chars, must be unique |
| `description` | string | optional, ≤255 chars |

```bash
curl -X POST http://localhost:8080/api/permissions \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "REPORT_VIEW", "description": "View reports"}'
```

**201 Created** — `PermissionResponse`. Errors: `409` permission name exists.

#### `DELETE /api/permissions/{id}` — requires `PERMISSION_DELETE`

Also removes the permission from any role currently carrying it, and clears
the authorization cache so the change applies immediately.

```bash
curl -X DELETE http://localhost:8080/api/permissions/10 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**204 No Content**. Errors: `404`.

## Interactive API docs (Swagger)

With the app running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI 3 spec: `http://localhost:8080/v3/api-docs`

Click **Authorize** in Swagger UI and paste an access token (no `Bearer `
prefix needed there — it's added for you) to call protected endpoints
directly from the browser.
