# MiniPay

A production-grade payment platform built as a learning exercise in distributed systems, financial engineering, and DevOps. The emphasis is on understanding the _why_ behind every architectural decision, not just making it work.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 3.5.9, Spring Cloud 2025.0.0 |
| API Gateway | Spring Cloud Gateway (WebFlux/Netty) |
| Database | PostgreSQL 17 |
| Cache / Session | Redis 7 |
| Messaging | Apache Kafka 3.9.0 (KRaft mode) |
| Schema Migrations | Flyway |
| Build | Maven (multi-module monorepo) |
| Containerisation | Docker Compose |

## Architecture

MiniPay is a microservices monorepo. Each service is a self-contained Spring Boot application with its own database. The `common` module is a shared library bundled into each service JAR — it is not deployed independently.

```text
Client
  │
  ▼
api-gateway :8080   ← JWT validation, rate limiting, routing
  │
  ├─► auth-service   :8081   ← registration, login, token refresh
  └─► wallet-service :8082   ← wallet CRUD, credit/debit
```

### Request Flow

All client traffic enters through the API gateway on port 8080. The gateway:

1. Validates the Bearer JWT locally using the shared secret.
2. Injects `X-User-Id` and `X-User-Role` headers into the forwarded request.
3. Applies Redis-backed token bucket rate limiting (10 req/s, 20 burst, keyed by IP).
4. Generates an `X-Request-ID` (UUID) for request tracing.

Downstream services trust the injected headers unconditionally and never re-validate JWTs.

### Event Flow

```text
auth-service  ──► outbox_events table ──► OutboxRelayService (polls 5s) ──► Kafka
                                                                               │
                                          wallet-service ◄── WalletConsumer ──┘
                                          (creates default wallet on registration)
```

User registration publishes a `minipay.user.registered` event via the transactional outbox pattern. The wallet service consumes this event to create a default wallet without a synchronous service-to-service call.

### Services

| Service | Port | Database | Status |
| --- | --- | --- | --- |
| api-gateway | 8080 | — | Complete |
| auth-service | 8081 | minipay_auth | Complete |
| wallet-service | 8082 | minipay_wallet | Complete |
| transfer-service | 8083 | minipay_transaction | Not started |

### Internal vs. Public Endpoints

Wallet credit and debit are internal-only endpoints (`/internal/wallets/{id}/credit`, `/internal/wallets/{id}/debit`). They are never exposed by the gateway and are protected by the `X-Internal-Secret` header, validated by `InternalApiInterceptor` against the `INTERNAL_API_SECRET` environment variable.

---

## Getting Started

### Prerequisites

- Java 25 (`/home/benjie/.jdks/openjdk-25.0.2/` or equivalent)
- Maven 3.9+
- Docker + Docker Compose

### 1. Environment

```bash
cp .env.example .env
# Fill in real values before continuing
```

Required variables:

| Variable | Used by |
| --- | --- |
| `JWT_SECRET` | auth-service, api-gateway |
| `REDIS_HOST`, `REDIS_PORT` | auth-service, api-gateway |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | auth-service |
| `WALLET_DB_URL` | wallet-service (shares `DB_USERNAME`/`DB_PASSWORD`) |
| `KAFKA_BOOTSTRAP_SERVERS` | auth-service (producer), wallet-service (consumer) |
| `INTERNAL_API_SECRET` | wallet-service — validates `X-Internal-Secret` on `/internal/**` |
| `AUTH_SERVICE_URL`, `WALLET_SERVICE_URL` | api-gateway |

### 2. Run Everything with Docker Compose

```bash
docker compose up --build
```

This starts PostgreSQL, Redis, Kafka, and all three Spring Boot services with dependency health checks. On first run it builds all images; subsequent runs can skip the build:

```bash
docker compose up
```

To start only the infrastructure (useful when running services locally via Maven):

```bash
docker compose up -d postgres redis kafka
```

### 3. Run a Service Locally (Maven)

Always run from the repo root so `spring-dotenv` finds `.env` relative to the JVM working directory.

```bash
export JAVA_HOME=/home/benjie/.jdks/openjdk-25.0.2
export PATH=$JAVA_HOME/bin:$PATH

mvn spring-boot:run -pl auth-service -am
mvn spring-boot:run -pl wallet-service -am
mvn spring-boot:run -pl api-gateway -am
```

### 4. Build

```bash
# Full monorepo
JAVA_HOME=/home/benjie/.jdks/openjdk-25.0.2 PATH=$JAVA_HOME/bin:$PATH mvn clean install

# Single module (skip tests)
JAVA_HOME=/home/benjie/.jdks/openjdk-25.0.2 PATH=$JAVA_HOME/bin:$PATH mvn clean install -pl wallet-service -am -DskipTests
```

### 5. Tests

```bash
# All tests
mvn test

# Single module
mvn test -pl auth-service

# Single test class
mvn test -pl auth-service -Dtest=AuthServiceTest
```

### Reset Databases

```bash
docker compose down -v && docker compose up
```

> Use `down -v`, not just `down`. Plain `down` preserves volumes and can leave the database inconsistent with Flyway's migration history.

---

## API Reference

All responses use the standard envelope:

```json
{
  "success": true,
  "message": "...",
  "data": { ... }
}
```

### Auth Service — `POST /api/v1/auth/*`

All auth endpoints are public (no JWT required) except `/logout`.

#### Register

```http
POST /api/v1/auth/register
```

```json
{
  "email": "user@example.com",
  "phoneNumber": "+2348012345678",
  "password": "secret123",
  "role": "CUSTOMER"
}
```

Roles: `CUSTOMER`, `MERCHANT`, `ADMIN`. Returns 201 with `accessToken`, `refreshToken`, and user details. Also triggers creation of a default wallet via Kafka.

#### Login

```http
POST /api/v1/auth/login
```

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

Returns 200 with a new token pair. Access token expires in 15 minutes; refresh token expires in 7 days.

#### Refresh

```http
POST /api/v1/auth/refresh
X-Refresh-Token: <refreshToken>
```

Refresh tokens are single-use. Using one invalidates it and issues a new pair.

#### Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

Deletes the refresh token from Redis immediately.

---

### Wallet Service — `GET|POST /api/v1/wallets/*`

All wallet endpoints require authentication. The gateway injects `X-User-Id` and `X-User-Role`.

#### List Wallets

```http
GET /api/v1/wallets
```

Returns all wallets owned by the authenticated user.

#### Get Wallet

```http
GET /api/v1/wallets/{id}
```

Returns a single wallet. Returns 401 if the wallet belongs to a different user.

#### Create Wallet

```http
POST /api/v1/wallets
```

```json
{
  "type": "SAVINGS",
  "currency": "USD"
}
```

Wallet types: `PERSONAL`, `SAVINGS`, `BUSINESS`. Currencies: `NGN`, `USD`, `GBP`, `EUR`. Defaults to `NGN` if `currency` is omitted. Business wallets are restricted to `MERCHANT` users; customers cannot create them. Admins cannot have wallets.

---

### Internal Wallet Endpoints — `POST /internal/wallets/*`

Not routed by the gateway. For service-to-service calls only. Requires `X-Internal-Secret` header.

#### Credit

```http
POST /internal/wallets/{id}/credit
X-Internal-Secret: <secret>
```

```json
{
  "amount": "500.00",
  "reference": "TXN-12345"
}
```

#### Debit

```http
POST /internal/wallets/{id}/debit
X-Internal-Secret: <secret>
```

Balance updates use atomic JPQL `UPDATE` queries with overdraft protection in the `WHERE` clause — no separate read before write. Returns 409 on insufficient funds.

---

## Database Schema

### minipay_auth

```sql
users (
  id            UUID PRIMARY KEY,
  email         VARCHAR UNIQUE,
  phone_number  VARCHAR UNIQUE,
  password_hash VARCHAR,
  role          user_role  -- CUSTOMER | MERCHANT | ADMIN
  status        user_status -- ACTIVE | INACTIVE | SUSPENDED
  created_at    TIMESTAMP,
  updated_at    TIMESTAMP  -- maintained by trigger
)

outbox_events (
  id         UUID PRIMARY KEY,
  topic      VARCHAR,
  payload    TEXT,
  published  BOOLEAN,
  created_at TIMESTAMP
)
```

### minipay_wallet

```sql
wallets (
  id         UUID PRIMARY KEY,
  user_id    UUID,
  type       wallet_type    -- PERSONAL | SAVINGS | BUSINESS
  currency   currency_code  -- NGN | USD | GBP | EUR
  balance    NUMERIC(19,4),
  status     wallet_status  -- ACTIVE | FROZEN | CLOSED
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  UNIQUE (user_id, type, currency)
)

wallet_transactions (
  id            UUID PRIMARY KEY,
  wallet_id     UUID REFERENCES wallets(id),
  type          transaction_type  -- CREDIT | DEBIT
  amount        NUMERIC(19,4),
  balance_after NUMERIC(19,4),
  reference     VARCHAR,
  created_at    TIMESTAMP
)
```

---

## Development Notes

### Module Structure

```text
minipay/
├── pom.xml               ← parent POM: version management (BOM), compiler config
├── common/               ← shared library (ApiResponse, exceptions, GlobalExceptionHandler)
├── api-gateway/          ← Spring Cloud Gateway (WebFlux)
├── auth-service/         ← Spring MVC
├── wallet-service/       ← Spring MVC
└── infrastructure/
    └── postgres/init.sql ← creates minipay_auth, minipay_wallet, minipay_transaction
```

The parent POM manages all dependency versions via `<dependencyManagement>` (BOM pattern) and applies the Maven compiler plugin globally. Never add compiler plugin configuration to a child POM.

### API Documentation (Swagger)

- Auth service: `http://localhost:8081/swagger-ui/index.html`
- Wallet service: `http://localhost:8082/swagger-ui/index.html`

### HTTP Test Files

IntelliJ HTTP Client files are in `api-requests/`:

- `auth.http` — register, login (saves tokens to `client.global`), refresh, logout

### Coding Conventions

- DTOs are Java Records, grouped per-service in one file (e.g., `AuthDtos.java`, `WalletDtos.java`).
- Unit tests use Mockito (`@ExtendWith(MockitoExtension.class)`) with `@Nested` classes per feature.
- Integration tests use Testcontainers.
- Schema is managed by Flyway only. Hibernate runs with `ddl-auto: validate`.

### IntelliJ Setup

Enable annotation processing: **Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing**.

---

## Planned Services

| Service | Port | Purpose |
| --- | --- | --- |
| transfer-service | 8083 | Debit source + credit destination atomically; publish `minipay.transfer.completed` |
| payment-gateway-service | 8084 | Paystack integration (test mode) |
| notification-service | 8085 | Async notifications from Kafka events |
