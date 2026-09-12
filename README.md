# IAM Service

A Spring Boot Identity & Access Management system: authentication (JWT access +
opaque rotating refresh tokens) plus role-based access control (users, roles,
permissions).

## Stack

- Java 21, Spring Boot 3.3, Gradle
- Spring Security 6 (stateless JWT auth, method-level `@PreAuthorize`)
- Spring Data JPA + PostgreSQL, Flyway migrations
- Caffeine cache in front of role/permission resolution (see "How authorization works" below)
- springdoc-openapi (Swagger UI)

## Data model

- **User** — account + credentials, `enabled` / `accountNonLocked` flags
- **Role** — named group of permissions (`ADMIN`, `MANAGER`, `USER` seeded by default)
- **Permission** — a single grantable action (e.g. `USER_WRITE`, `ROLE_READ`)
- **RefreshToken** — hashed, rotated on use, revocable

Seeded by `V2__seed_data.sql`:

| Role    | Permissions                                    |
|---------|-------------------------------------------------|
| ADMIN   | every permission (the "super admin" role)       |
| MANAGER | `USER_READ`, `USER_WRITE`, `ROLE_READ`, `PERMISSION_READ` |
| USER    | none (default role for self-registered accounts) |

A default admin account is seeded: **username `admin`, password `Admin@12345`**.
**Change this password immediately** outside of local development.

## Running locally

```bash
docker compose up --build
```

This starts Postgres and the service (Flyway migrates the schema automatically).
The API is at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

To run against your own Postgres instead:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/iamdb
export DB_USERNAME=iam
export DB_PASSWORD=iam
export JWT_SECRET=some-secret-at-least-32-bytes-long
./gradlew bootRun
```

## Tests

```bash
./gradlew test
```

Tests run against an in-memory H2 database (Flyway disabled, `ddl-auto=create-drop`)
so they need no external services.

## API overview

All endpoints are under `/api`. Bearer JWT auth via `Authorization: Bearer <token>`.

### Auth (public)
- `POST /api/auth/register` — self-register (gets the `USER` role)
- `POST /api/auth/login`
- `POST /api/auth/refresh` — rotates the refresh token; the old one is invalidated
- `POST /api/auth/logout` — revokes a refresh token

### Users
- `GET  /api/users/me` — current user
- `PATCH /api/users/me/password` — change own password (revokes existing refresh tokens)
- `GET  /api/users` — `USER_READ` — paginated list
- `GET  /api/users/{id}` — `USER_READ`
- `POST /api/users` — `USER_WRITE` — admin-created user, optional role set
- `PUT  /api/users/{id}` — `USER_WRITE` — update profile / enabled / locked
- `PUT  /api/users/{id}/roles` — `USER_WRITE` — replace a user's roles
- `DELETE /api/users/{id}` — `USER_DELETE`

### Roles
- `GET  /api/roles`, `GET /api/roles/{id}` — `ROLE_READ`
- `POST /api/roles` — `ROLE_WRITE` — create, optionally with a permission set
- `PUT  /api/roles/{id}` — `ROLE_WRITE` — update description
- `PUT  /api/roles/{id}/permissions` — `ROLE_WRITE` — replace a role's permissions
- `DELETE /api/roles/{id}` — `ROLE_DELETE`

### Permissions
- `GET  /api/permissions` — `PERMISSION_READ`
- `POST /api/permissions` — `PERMISSION_WRITE`
- `DELETE /api/permissions/{id}` — `PERMISSION_DELETE`

Since `ADMIN` holds every permission, an admin can create new permissions, create
roles built from them, and assign those roles to any user — the full super-admin
workflow — through the endpoints above.

## How authorization works

Every authenticated request passes through `JwtAuthenticationFilter`:

1. **Signature/expiry check** — the JWT is verified in memory (HS-signed, no I/O).
   Access tokens are short-lived (15 min by default).
2. **Authority resolution** — the filter then asks `CustomUserDetailsService` for the
   user's *current* roles/permissions, rather than trusting a claim embedded in the
   token. This is what enforces "revoked access takes effect quickly" even though the
   token itself hasn't expired yet.

Step 2 would mean a database round-trip on every request, so it's backed by a
short-TTL (60s) Caffeine cache (`CacheConfig`). To avoid waiting out that TTL for
security-relevant changes, `UserService`/`RoleService`/`PermissionService` evict the
affected cache entries the moment an admin changes a user's roles/enabled/locked
state, or a role's/permission's definition — so those changes are enforced on the
very next request, not just "eventually".

Refresh tokens are opaque random values; only their SHA-256 hash is stored, and each
one is single-use (rotated on refresh, revoked on logout or password change).

## Configuration

See `application.yml` for all properties (`iam.jwt.*`, `iam.cors.*`, `spring.datasource.*`);
everything is overridable via environment variables (`JWT_SECRET`, `DB_URL`, etc).
