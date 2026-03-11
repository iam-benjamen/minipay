# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build entire monorepo
mvn clean install

# Build a specific module
mvn clean install -pl auth-service -am

# Run a service (from its directory)
cd auth-service && mvn spring-boot:run

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl auth-service

# Run a single test class
mvn test -pl auth-service -Dtest=AuthServiceTest

# Start infrastructure (PostgreSQL + Redis)
docker compose up -d

# Reset databases (drops all data)
docker compose down -v && docker compose up -d
```

## Architecture

Maven monorepo with microservices deployed independently. Each service is a Spring Boot app; `common` is a shared library bundled inside each service JAR (not deployed standalone).

### Services

| Service | Port | DB |
|---|---|---|
| api-gateway | 8080 | none |
| auth-service | 8081 | minipay_auth |
| wallet-service | 8082 | minipay_wallet (not started) |
| transfer-service | 8083 | TBD (not started) |

### Request Flow

Client → api-gateway (8080) → downstream services

The gateway validates JWTs locally (shared secret), injects `X-User-Id` and `X-User-Role` headers, and applies Redis rate limiting (10 req/s, 20 burst). Downstream services trust these headers — they do not re-validate JWTs.

### Common Module (`com.minipay.common`)

- `ApiResponse<T>` — standard JSON envelope for all endpoints
- `MiniPayException` (abstract) → `ResourceNotFoundException` (404), `ConflictException` (409), `UnauthorizedException` (401)
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — centralized exception handling; all services inherit this

### Auth Service

- JWT: access token 15 min, refresh token 7 days (stored in Redis as `refresh_token:{userId}`)
- Refresh token rotation: single-use, revoked on use
- Roles: `CUSTOMER`, `MERCHANT`, `ADMIN`
- Flyway migrations in `src/main/resources/db/migration/`
- Swagger: `http://localhost:8081/swagger-ui/index.html`

### Maven Structure

- Parent POM manages all versions via `<dependencyManagement>` (BOM pattern)
- Compiler plugin in parent `<build><plugins>` — auto-applies to all modules; do NOT add compiler config to child POMs
- Spring Boot plugin in parent `<pluginManagement>` — child service POMs only need to declare it with the `repackage` goal
- `common/pom.xml` has no `<build>` section

## Known Gotchas

**Lombok on Java 25**: requires `--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED` (already in parent POM). IntelliJ requires annotation processing enabled: Settings → Compiler → Annotation Processors.

**PostgreSQL custom enum types**: Hibernate rejects plain VARCHAR for custom PG enums. Use:
```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "role", columnDefinition = "user_role")
private Role role;
```

**JPA**: `ddl-auto: validate` — schema is managed by Flyway only. Never use `create` or `update`.

## Coding Standards

- No comments in code unless logic is non-obvious or explicitly requested
- No emojis
- DTOs as Java Records (`AuthDtos.java` groups all DTOs for a service in one file)
- Tests use Mockito (`@ExtendWith(MockitoExtension.class)`) with nested `@Nested` classes per feature
- Testcontainers for integration tests (PostgreSQL container)

## Infrastructure

Docker Compose provides PostgreSQL 17 and Redis 7. Init script at `infrastructure/postgres/init.sql` creates all databases (`minipay_auth`, `minipay_wallet`, `minipay_transaction`).

HTTP test files (IntelliJ HTTP client): `api-requests/auth.http`
