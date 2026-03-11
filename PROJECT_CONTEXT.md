# MiniPay - Project Context

## Purpose of This File
Paste this file at the start of any new chat session to restore full project context.

---

## Project Goal
Build a minified production-grade payment platform to learn distributed systems, financial engineering, and DevOps practices. The goal is learning through building — not just making it work, but understanding why.

---

## Developer Profile
- Intermediate Java/Spring Boot experience
- Using IntelliJ on Linux
- Java 25 (located at `/home/benjie/.jdks/openjdk-25.0.2/`), system default is Java 21
- Maven build system
- Learning-oriented: wants explanations before code, asks questions, makes final decisions

---

## Architecture Decisions

### Structure
- Full microservices from day one (monorepo, deployed separately)
- Monorepo at `/minipay/` with Maven multi-module setup

### Services
| Service | Port | DB | Status |
|---|---|---|---|
| api-gateway | 8080 | none | Implemented |
| auth-service | 8081 | minipay_auth (PostgreSQL) | Complete |
| wallet-service | 8082 | minipay_wallet (PostgreSQL) | Not started |
| transfer-service | 8083 | TBD | Not started |
| payment-gateway-service | 8084 | TBD | Not started |
| notification-service | 8085 | TBD | Not started |
| transaction-service | 8086 | TBD | Not started |

### Dropped / Deferred
- analytics-service — Prometheus/Grafana sufficient for now
- reconciliation-service — will merge into transaction-service later
- fraud-service — starting as a library inside transfer-service, not a standalone service

### Tech Stack
- Spring Boot 3.5.9, Spring Cloud 2025.0.0
- Java 25
- PostgreSQL 17, Redis 7, Kafka (later)
- Micrometer Tracing for observability
- Prometheus + Grafana for metrics
- GitLab CI/CD
- GKE Standard (not Autopilot — more learning value)
- GCP Secret Manager with Workload Identity

### Communication
- REST between services (gRPC only if absolutely necessary)
- Kafka for async events (wallet creation on user registration, notifications, etc.)

### RBAC
Three roles: CUSTOMER, MERCHANT, ADMIN

### Payment Integration
- Paystack (test mode)

---

## Maven Structure

### Parent POM (`/minipay/pom.xml`)
- `<packaging>pom</packaging>`
- BOM pattern via `<dependencyManagement>` — single source of truth for all versions
- Key versions: Spring Boot 3.5.9, Spring Cloud 2025.0.0, JWT 0.12.6, MapStruct 1.6.3, Testcontainers 1.20.4, Lombok 1.18.42, Springdoc 2.8.9
- Compiler plugin in `<build><plugins>` — auto-applies to all child modules; never repeat in child POMs
- Lombok + MapStruct annotation processors configured at parent level
- `--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED` required for Lombok on Java 25
- Spring Boot plugin in `<pluginManagement>` with Lombok exclusion

### Module POMs
- `common/pom.xml` — no build section, inherits everything from parent
- Service POMs — only declare the Spring Boot plugin with the `repackage` goal

### Build Command (Java 25 required)
```bash
export JAVA_HOME=/home/benjie/.jdks/openjdk-25.0.2
export PATH=$JAVA_HOME/bin:$PATH
mvn clean install -pl <module> -am -DskipTests
```

---

## Common Module (`/minipay/common/`)
Shared library bundled inside each service JAR. Not deployed separately.

### Contents
- `ApiResponse<T>` — standard JSON envelope: `{ success, message, data }`
- `MiniPayException` (abstract) — base exception with HttpStatus
- `ResourceNotFoundException` — 404
- `ConflictException` — 409
- `UnauthorizedException` — 401
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — centralized exception handling for MVC services

### Note on WebFlux services
`GlobalExceptionHandler` uses Spring MVC annotations. In the api-gateway (WebFlux), `spring-boot-starter-web` is excluded from the common dependency so Spring MVC is not auto-configured. The gateway uses its own `GatewayErrorHandler` instead. All other non-gateway classes from common (`ApiResponse`, exceptions) are still usable.

---

## API Gateway (`/minipay/api-gateway/`)
Spring Cloud Gateway (WebFlux/Netty) on port 8080. Single entry point for all clients.

### Why WebFlux?
Spring Cloud Gateway is built on Project Reactor / Netty. It is non-blocking and event-loop based — the right model for a proxy that just moves bytes around and does lightweight work (JWT validation, rate limiting) without blocking on DB or heavy computation. Spring MVC (servlet-based, one-thread-per-request) cannot be on the classpath at the same time.

### Key Behaviour
- JWT validated once at the gateway using the shared secret — downstream services never see raw JWTs
- Injects `X-User-Id` and `X-User-Role` headers; downstream services trust these unconditionally
- Redis-backed token bucket rate limiter: 10 req/s replenish, 20 burst capacity, keyed by client IP
- Generates `X-Request-ID` (UUID) if not present — forwarded downstream, logged on every request
- Errors returned as `ApiResponse` JSON with correct HTTP status codes

### Filter Chain (order matters — lower number = runs first)
| Filter | Order | Role |
|---|---|---|
| `RequestLoggingFilter` | -2 | Outermost wrapper: generates X-Request-ID, times the request, logs on completion |
| `JwtAuthenticationFilter` | -1 | Validates Bearer token, injects identity headers, short-circuits with 401 on failure |
| Gateway routing | (internal) | Forwards to upstream service |

### Public Paths (no JWT required)
```java
Set.of(
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh"
)
```
When a new public path is needed, add it to `JwtAuthenticationFilter.PUBLIC_PATHS`.

### Routes
| Route ID | Predicate | Upstream |
|---|---|---|
| auth-register | POST /api/v1/auth/register | http://localhost:8081 |
| auth-login | POST /api/v1/auth/login | http://localhost:8081 |
| auth-refresh | POST /api/v1/auth/refresh | http://localhost:8081 |
| auth-logout | POST /api/v1/auth/logout | http://localhost:8081 |
| auth-catchall | /api/v1/auth/** | http://localhost:8081 |
| wallets | /api/v1/wallets/** | http://localhost:8082 |

Actuator endpoints (`/actuator/health`, etc.) are served by the gateway itself — no route needed.

### Error Handling
`GatewayErrorHandler` implements `ErrorWebExceptionHandler` (WebFlux equivalent of `@ControllerAdvice`). It handles errors from the full filter chain — connection refused, no route found, unhandled exceptions — and writes them as `ApiResponse` JSON. Registered at `@Order(-1)`.

### Key Files
- `config/RouteConfig.java` — Java-based routes (RouteLocatorBuilder)
- `config/RateLimiterConfig.java` — `ipKeyResolver` bean
- `filter/JwtAuthenticationFilter.java` — GlobalFilter, order -1
- `filter/RequestLoggingFilter.java` — GlobalFilter, order -2
- `exception/GatewayErrorHandler.java` — ErrorWebExceptionHandler
- `service/JwtService.java` — JWT validation only (no Redis, no User entity)

---

## Auth Service (`/minipay/auth-service/`)
Port 8081, database `minipay_auth`.

### Features Implemented
- User registration (email + phone, BCrypt password)
- Login with JWT access + refresh token
- Refresh token rotation (single-use, stored in Redis with TTL)
- Logout (deletes refresh token from Redis)
- RBAC: CUSTOMER, MERCHANT, ADMIN

### JWT
- Access token: 15 min, contains `sub` (userId), `role`, `email`
- Refresh token: 7 days, stored in Redis as `refresh_token:{userId}`
- Both signed with HMAC-SHA256 using shared `jwt.secret`
- Rotation: using a refresh token invalidates it immediately and issues a new pair

### Key Files
- `V1__create_users_table.sql` — Flyway migration with PostgreSQL enums, trigger for `updated_at`
- `User.java` — JPA entity with `@PrePersist` / `@PreUpdate`
- `AuthDtos.java` — Java Records for all request/response DTOs
- `JwtService.java` — token generation, validation, Redis refresh token management
- `AuthService.java` — registration, login, refresh, logout
- `SecurityConfig.java` — stateless, CSRF disabled, public endpoints whitelisted
- `AuthController.java` — POST register (201), login (200), refresh, logout

### Swagger
`http://localhost:8081/swagger-ui/index.html`

---

## Infrastructure

### Docker Compose (`/minipay/docker-compose.yml`)
- PostgreSQL 17-alpine (port 5432)
- Redis 7-alpine (port 6379)
- Init script (`infrastructure/postgres/init.sql`) creates: `minipay_auth`, `minipay_wallet`, `minipay_transaction`
- Named volumes: `postgres_data`, `redis_data`

### Reset DB
```bash
docker compose down -v && docker compose up -d
```

---

## Known Issues / Fixes Applied

### Lombok on Java 25
- Lombok 1.18.36 incompatible with Java 25 — upgrade to 1.18.42
- Requires `--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED`
- IntelliJ: Settings → Compiler → Annotation Processors → Enable

### Hibernate + PostgreSQL Custom Enum
- PostgreSQL `user_role` / `user_status` custom enum types rejected plain VARCHAR from Hibernate
- Fix:
```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "role", columnDefinition = "user_role")
private Role role;
```

### Maven Compiler Plugin
- Parent `<build><plugins>` applies to all modules automatically
- Never add compiler plugin config to a child POM

### Spring MVC + WebFlux Conflict
- `spring-cloud-starter-gateway` requires WebFlux. Having `spring-boot-starter-web` on the classpath alongside it breaks the app.
- In api-gateway's pom.xml, `spring-boot-starter-web` is excluded from the `common` dependency.

---

## API Requests
HTTP files in `/minipay/api-requests/` (IntelliJ HTTP client format).
- `auth.http` — register, login (saves tokens to `client.global`), refresh, logout
- Variables: `@baseUrl = http://localhost:8081`, `@gatewayUrl = http://localhost:8080`

---

## Coding Standards
- No comments in code unless logic is non-obvious or explicitly requested
- No emojis
- DTOs as Java Records, grouped per-service in one file (e.g., `AuthDtos.java`)
- Mockito unit tests with `@ExtendWith(MockitoExtension.class)` and `@Nested` classes per feature
- Testcontainers for integration tests
- `ddl-auto: validate` — schema managed by Flyway only, never by Hibernate
- Think in systems — understand the why before writing code

---

## Concepts Being Learned (Running List)

### API Gateway Pattern
A single entry point that handles cross-cutting concerns (auth, rate limiting, logging, routing) so individual services stay focused on business logic. The tradeoff: it's a potential single point of failure and must be stateless and fast.

### Token Bucket Rate Limiting
Each client gets a "bucket" that fills at the replenish rate (10 tokens/s here) up to the burst capacity (20). Each request costs 1 token. If the bucket is empty, the request is rejected with 429. Redis stores the bucket state so it works across multiple gateway instances.

### JWT at the Gateway
Validating JWTs at the gateway (not per-service) means:
- Auth logic lives in one place
- Downstream services receive trusted headers (`X-User-Id`, `X-User-Role`) — they never touch JWTs
- The shared secret must be kept consistent across gateway and auth-service (environment variable)
- Refresh token validation still happens in auth-service (only it can check Redis)

### Refresh Token Rotation
After a refresh, the old token is immediately deleted from Redis. If an attacker steals and uses a refresh token before the legitimate user does, the next legitimate refresh will fail (token not found), alerting to a potential compromise. This is single-use rotation.

### Distributed Tracing Foundation
`X-Request-ID` is the first step toward distributed tracing. A single UUID follows a request across the gateway and all downstream services. Searching logs for one ID reconstructs the full request journey. The next step (Micrometer Tracing + Zipkin/Tempo) automates propagation and visualisation.

### Reactive Programming (WebFlux)
Instead of blocking threads waiting for I/O, reactive code chains operations as a pipeline (`Mono`, `Flux`) that executes when data is ready. The event loop handles many concurrent connections with a small thread pool. Correct for a gateway; overkill for a typical CRUD service.

### Flyway Migrations
Schema changes are versioned SQL files (`V1__`, `V2__`, etc.) applied in order. `ddl-auto: validate` means Hibernate checks the schema matches the entity model at startup but never modifies it. This gives full control over schema evolution and makes it safe to run in production.

### BOM (Bill of Materials) Pattern
The parent POM imports Spring Boot's and Spring Cloud's dependency BOMs inside `<dependencyManagement>`. This means child modules declare a dependency without a version — the BOM provides the correct, compatible version. Prevents version mismatch bugs across a large dependency tree.

---

## Current State
- auth-service: fully implemented, tested, working
- api-gateway: implemented and compiles, not yet end-to-end tested
- wallet-service: not started (next)
- All other services: not started

## Next Up
- End-to-end test api-gateway with auth-service running
- wallet-service:
  - Wallet creation (triggered by user registration — sync call or Kafka event, decision pending)
  - Balance inquiry with Redis caching
  - Transaction history
  - Wallet freeze/unfreeze (ADMIN)
- Kafka setup for async events between services
