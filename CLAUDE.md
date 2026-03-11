# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build entire monorepo
mvn clean install

# Build a specific module
mvn clean install -pl auth-service -am

# Run a service — must run from repo root so spring-dotenv finds .env
mvn spring-boot:run -pl auth-service -am

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl auth-service

# Run a single test class
mvn test -pl auth-service -Dtest=AuthServiceTest

# Start everything (infrastructure + services) via Docker Compose
cp .env.example .env   # once; fill in real values
docker compose up --build

# Start without rebuilding images
docker compose up

# Start infrastructure only (for local Maven dev)
docker compose up -d postgres redis

# Reset databases (drops all data)
docker compose down -v && docker compose up
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

**Docker — all module POMs must be copied in every Dockerfile**: the parent `pom.xml` declares all modules; Maven validates their existence even when building a single module with `-pl`. Copy all `*/pom.xml` stubs before running any Maven command.

**Docker — Alpine JRE has no `curl`**: `eclipse-temurin:25-jre-alpine` ships without curl. Use `wget -qO-` in healthchecks instead.

**Docker — TLS 1.2 in build stage**: JDK 25 defaults to TLS 1.3, which some network middleboxes mishandle during Maven dependency downloads (`bad_record_mac` error). Set `ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2"` in the builder stage.

**spring-dotenv working directory**: spring-dotenv finds `.env` relative to the JVM working directory. When running via `mvn spring-boot:run -pl <service> -am`, run from the repo root so the process working directory is the project root where `.env` lives.

## Coding Standards

- No comments in code unless logic is non-obvious or explicitly requested
- No emojis
- DTOs as Java Records (`AuthDtos.java` groups all DTOs for a service in one file)
- Tests use Mockito (`@ExtendWith(MockitoExtension.class)`) with nested `@Nested` classes per feature
- Testcontainers for integration tests (PostgreSQL container)

## Infrastructure

Docker Compose provides PostgreSQL 17, Redis 7, and all Spring Boot services. Init script at `infrastructure/postgres/init.sql` creates all databases (`minipay_auth`, `minipay_wallet`, `minipay_transaction`).

Environment variables are required — no defaults in `application.yaml`. Copy `.env.example` → `.env` and fill in values. Docker Compose loads `.env` via `env_file:`; local Maven dev uses spring-dotenv to load it automatically (run from repo root).

Dockerfiles use multi-stage builds: `maven:3.9-eclipse-temurin-25` for compilation, `eclipse-temurin:25-jre-alpine` for the runtime image.

HTTP test files (IntelliJ HTTP client): `api-requests/auth.http`
