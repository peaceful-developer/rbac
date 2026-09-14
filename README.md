# IAM Service

A Spring Boot Identity & Access Management system: authentication (JWT access
tokens + opaque rotating refresh tokens) plus full role-based access control
(users, roles, permissions) with an admin API to manage all three.

## Table of contents

- [Stack](#stack)
- [Project structure](#project-structure)
- [Data model](#data-model)
- [Master Admin & Projects](#master-admin--projects)
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
  - [Projects](#projects)
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

- **User** — account + credentials (`enabled`, `accountNonLocked` flags), plus a
  platform-level `masterAdmin` flag (see [Master Admin & Projects](#master-admin--projects)).
  Still many-to-many with Role for the legacy global admin-panel capabilities below.
- **Role** — named group of permissions, many-to-many with Permission. Carries an
  `editable` flag: a role created by a Master Admin is locked (`editable=false`) and
  can only be modified by a Master Admin, even by someone else holding `ROLE_WRITE`.
- **Permission** — a single grantable action, e.g. `USER_WRITE`, `ROLE_READ`. Roles
  and permissions are a single global catalog, shared across every project.
- **RefreshToken** — SHA-256 hash of an opaque token, single-use, revocable
- **Project** — a tenant. Created/managed only by a Master Admin.
- **ProjectMembership** — one user's role(s) *within one project* — see below.

`V2__seed_data.sql` seeds a starter RBAC setup for the legacy global admin panel:

| Role      | Permissions                                                        |
|-----------|---------------------------------------------------------------------|
| `ADMIN`   | every permission (locked; only a Master Admin can redefine it)     |
| `MANAGER` | `USER_READ`, `USER_WRITE`, `ROLE_READ`, `PERMISSION_READ`           |
| `USER`    | none — default role given to self-registered accounts               |

`V4__project_permissions_and_super_admin_role.sql` adds the project layer's
permissions (`PROJECT_READ/WRITE/DELETE`, `PROJECT_MEMBER_READ/WRITE/DELETE`) and a
locked `SUPER_ADMIN` role (`PROJECT_MEMBER_READ/WRITE/DELETE`, `ROLE_READ`,
`ROLE_WRITE`, `PERMISSION_READ`) — assigned to a user *within a project*, never globally.

A default admin account is seeded — **username `admin`, password `Admin@12345`** —
and is also the platform's first Master Admin. **Change this password immediately**
in any shared or non-local environment.

## Master Admin & Projects

On top of the RBAC system above sits a second, simpler hierarchy for multi-tenancy:

```
Master Admin (platform-level, not a Role — a flag on User)
  └─ creates/manages Projects (tenants) and the permission/role catalog
     └─ each Project has one or more Super Admins (assigned only by a Master Admin)
        └─ a Super Admin adds other users to their project on any existing role
           (or a new role they build from the permission catalog)
```

- **Master Admin** is deliberately a boolean flag on `User`, not a `Role` row — so it
  can never be granted through the normal role-assignment endpoints, only via
  `PATCH /api/users/{id}/master-admin`, itself Master-Admin-only (so only a Master
  Admin can create another one). It's embedded as a literal `MASTER_ADMIN` authority
  in the JWT alongside the usual role/permission authorities.
- Only a Master Admin can create permissions, and only a Master Admin's roles come
  out locked (`editable=false`) — see the `Role` entity above.
- Only a Master Admin can create/update/delete a `Project`, and only a Master Admin
  can assign or remove the `SUPER_ADMIN` role on a project membership — a project's
  own Super Admin can add other members on any other role, but can't mint a rival
  Super Admin, demote one, or be removed by one.
- Roles/permissions stay a single global catalog (not project-scoped) — a Super
  Admin builds new roles from whatever permissions a Master Admin has defined, and
  those roles are then usable across any project, same as the legacy `ADMIN`/`MANAGER`/`USER` roles.
  This is why the seeded `SUPER_ADMIN` role carries `ROLE_READ`/`ROLE_WRITE`/
  `PERMISSION_READ` alongside its `PROJECT_MEMBER_*` permissions - but a real Super
  Admin only ever holds `SUPER_ADMIN` via a *project membership* row, never a global
  `User.roles` entry, so those permissions carry no JWT authority to check against a
  plain `hasAuthority(...)` gate. `RoleController`/`PermissionController`'s read
  (and, for roles, write) endpoints therefore also accept
  `@projectAuthorizationService.isSuperAdminOfAnyProject()` - true for a Master Admin
  or anyone who is `SUPER_ADMIN` on at least one project - as an explicit third way
  through the gate, alongside the flat authority and the `MASTER_ADMIN` bypass.
  Locked roles remain out of reach either way: `RoleService`'s editable check only
  ever lets a Master Admin (not a Super Admin) touch them.
- A per-project permission check (e.g. "can this caller manage *this* project's
  members") can't be expressed as a flat JWT authority, since holding it in one
  project must not imply holding it in another — see `ProjectAuthorizationService`,
  which checks a Master-Admin bypass first, then that user's actual
  `ProjectMembership` row for the specific project in the request.
- A Super Admin never needs, and never gets, `USER_READ`/`USER_WRITE`/`USER_DELETE`
  — global user administration (`GET/POST/PUT/DELETE /api/users`) stays entirely a
  Master Admin/legacy-`ADMIN`-role affair. Everything a Super Admin does with users
  is therefore project-scoped and routed through `/api/projects/{id}/...` instead:
  `GET .../candidate-users` to pick an existing account, `POST .../users` to create
  a brand-new one straight into their project, and `GET/POST/PUT/DELETE .../members`
  to manage who's in it. None of those can reach beyond the one project in the path,
  which is exactly why they exist rather than relaxing the global `/api/users` gates.

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
| `iam.cors.allowed-origins`        | `CORS_ALLOWED_ORIGINS`    | `http://localhost:4200`                    | comma-separated; matches the rbac-ui companion frontend's dev-server port |

> **Note on CORS with a frontend dev-server proxy:** rbac-ui's `ng serve` uses a
> proxy (`proxy.conf.json`) so the *browser* only ever talks to `localhost:4200`,
> avoiding browser-side CORS preflight entirely. But the proxy still forwards your
> original `Origin: http://localhost:4200` header through to this backend when it
> relays the request — and this backend's own CORS filter checks that header on
> every request it receives, proxied or not. So `iam.cors.allowed-origins` still
> needs to include the frontend's actual origin even when you're only ever testing
> through the dev-server proxy, or you'll see requests rejected with
> `Invalid CORS request` despite the browser never making a real cross-origin call.

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

#### `PATCH /api/users/{id}/master-admin` — requires `MASTER_ADMIN`

Grants or revokes the platform-level Master Admin flag. Only an existing Master
Admin can call this — it's the only way this flag ever changes (there's no
`MASTER_ADMIN` role row for `PUT /api/users/{id}/roles` to touch).

```bash
curl -X PATCH http://localhost:8080/api/users/5/master-admin \
  -H "Authorization: Bearer $MASTER_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"masterAdmin": true}'
```

**200 OK** — `UserResponse` (now including `masterAdmin`). Errors: `403` if the
caller isn't a Master Admin, `404` unknown user.

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

#### `POST /api/permissions` — requires `MASTER_ADMIN`

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

#### `DELETE /api/permissions/{id}` — requires `MASTER_ADMIN`

Also removes the permission from any role currently carrying it, and clears
the authorization cache so the change applies immediately.

```bash
curl -X DELETE http://localhost:8080/api/permissions/10 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**204 No Content**. Errors: `404`.

### Projects

Base path: `/api/projects`. Project CRUD is Master-Admin-only; membership endpoints
are project-scoped — a caller needs either `MASTER_ADMIN` or the relevant
`PROJECT_MEMBER_*` permission *within that specific project* (via a `SUPER_ADMIN` or
custom role assigned on their `ProjectMembership` for it — see
[Master Admin & Projects](#master-admin--projects)). `GET /api/projects` itself needs
no permission — every authenticated caller gets back only the projects they're
allowed to see (all of them for a Master Admin, just their own memberships otherwise).

#### `GET /api/projects`

```bash
curl http://localhost:8080/api/projects -H "Authorization: Bearer $TOKEN"
```

**200 OK** — `ProjectResponse[]`, each with `myRoles` set to the caller's own roles
in that project (empty if none):

```json
[
  {
    "id": 1, "name": "Acme Corp", "description": "First tenant",
    "myRoles": ["SUPER_ADMIN"],
    "createdAt": "2026-09-14T10:20:24Z", "updatedAt": "2026-09-14T10:20:24Z"
  }
]
```

#### `GET /api/projects/{id}` — Master Admin or a member of this project

**200 OK** — `ProjectResponse`. Errors: `403`, `404`.

#### `POST /api/projects` — requires `MASTER_ADMIN`

```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $MASTER_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "Acme Corp", "description": "First tenant"}'
```

**201 Created** — `ProjectResponse`. Errors: `409` name taken.

#### `PUT /api/projects/{id}` — requires `MASTER_ADMIN`

Partial update (name/description), same optional-field pattern as `UpdateUserRequest`.

**200 OK** — `ProjectResponse`. Errors: `404`, `409`.

#### `DELETE /api/projects/{id}` — requires `MASTER_ADMIN`

Cascades: deletes every membership (and the members' project-scoped roles) along with it.

**204 No Content**. Errors: `404`.

#### `GET /api/projects/{id}/members` — requires `PROJECT_MEMBER_READ` within this project (or Master Admin)

```bash
curl http://localhost:8080/api/projects/1/members -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

**200 OK** — `ProjectMemberResponse[]`:

```json
[ { "userId": 2, "username": "projsuper", "email": "projsuper@example.com", "roles": ["SUPER_ADMIN"] } ]
```

#### `GET /api/projects/{id}/candidate-users` — requires `PROJECT_MEMBER_WRITE` within this project (or Master Admin)

Existing users **not yet a member of this project**, as a deliberately minimal
`{id, username, email}` projection — no roles, no enabled/locked status, no
`masterAdmin` flag. This is what an "add member" picker calls; it exists as a
separate, narrower endpoint from `GET /api/users` specifically because that one
requires `USER_READ`, an authority a project's Super Admin does not (and should
not need to) hold just to add someone to their own project.

```bash
curl http://localhost:8080/api/projects/1/candidate-users -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

**200 OK** — `CandidateUserResponse[]`:

```json
[ { "id": 7, "username": "newhire", "email": "newhire@example.com" } ]
```

#### `POST /api/projects/{id}/users` — requires `PROJECT_MEMBER_WRITE` within this project (or Master Admin)

Creates a **brand-new account** and adds it to this project in one call — the
tenant-onboarding counterpart to the endpoint below, which can only pick someone
who already has an account. A Super Admin holds no `USER_WRITE`, so without this
they could only ever add staff who had already self-registered.

`roles` are the new user's roles *within this project*. The account itself is
created with the same permission-less baseline `USER` global role that
self-registration grants, so everything it can do comes from its project
membership — which is what keeps a Super Admin's reach inside their own project.
Assigning `SUPER_ADMIN` here is Master-Admin-only, same as everywhere else.

```bash
curl -X POST http://localhost:8080/api/projects/1/users \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"username": "newstaff", "email": "newstaff@acme.com", "password": "SuperSecret1",
       "firstName": "New", "lastName": "Staff", "roles": ["MANAGER"]}'
```

**201 Created** — `ProjectMemberResponse`. Errors: `403` (insufficient project
authority, or a non-Master-Admin attempting to assign `SUPER_ADMIN`), `404` unknown
role, `409` username/email already taken.

#### `POST /api/projects/{id}/members` — requires `PROJECT_MEMBER_WRITE` within this project (or Master Admin)

Adds an existing user (see `/api/auth/register` or `POST /api/users` to create the
account first) to the project with the given role(s). **Assigning `SUPER_ADMIN`
here requires the caller to be a Master Admin** — a project's own Super Admin gets
`403` if they try, even though they otherwise pass the `PROJECT_MEMBER_WRITE` check.

```bash
curl -X POST http://localhost:8080/api/projects/1/members \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"userId": 7, "roles": ["MANAGER"]}'
```

**201 Created** — `ProjectMemberResponse`. Errors: `403` (insufficient project
authority, or a non-Master-Admin attempting to assign `SUPER_ADMIN`), `404` unknown
user/role, `409` already a member (use the roles-update endpoint instead).

#### `PUT /api/projects/{id}/members/{userId}/roles` — requires `PROJECT_MEMBER_WRITE` within this project (or Master Admin)

Replaces (not merges with) the member's role set. Adding *or removing*
`SUPER_ADMIN` is Master-Admin-only, same restriction as above — so a Super Admin
can't demote themselves or another Super Admin out of the role either.

**200 OK** — `ProjectMemberResponse`. Errors: `403`, `404`.

#### `DELETE /api/projects/{id}/members/{userId}` — requires `PROJECT_MEMBER_DELETE` within this project (or Master Admin)

Removing a member who holds `SUPER_ADMIN` is Master-Admin-only.

**204 No Content**. Errors: `403`, `404`.

## Interactive API docs (Swagger)

With the app running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI 3 spec: `http://localhost:8080/v3/api-docs`

Click **Authorize** in Swagger UI and paste an access token (no `Bearer `
prefix needed there — it's added for you) to call protected endpoints
directly from the browser.
