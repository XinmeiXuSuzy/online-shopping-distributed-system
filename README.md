# Online Shopping Distributed System

A production-grade distributed e-commerce backend built with **Java 17**, **Spring Boot 3**, **MyBatis**, and **MySQL**, featuring Redis distributed locking, TCC inventory control, and AWS SQS async order processing — deployed on AWS ECS via a fully automated CDK pipeline.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Key Features](#key-features)
- [Flash Sale Flow](#flash-sale-flow)
- [Project Structure](#project-structure)
- [Getting Started (Local)](#getting-started-local)
- [API Reference](#api-reference)
- [TCC Pattern](#tcc-pattern)
- [Redis Distributed Lock](#redis-distributed-lock)
- [AWS Infrastructure](#aws-infrastructure)
- [CI/CD Pipeline](#cicd-pipeline)
- [Configuration](#configuration)

---

## Architecture Overview

```
                          ┌──────────────────────────────────────────────┐
                          │               AWS Cloud (ECS Fargate)        │
                          │                                              │
    Client ──► ALB ──► ┌──┴───────────────────┐                          │
                       │   Shopping API       │                          │
                       │   (Spring Boot 3)    │                          │
                       │                      │                          │
                       │  ┌────────────────┐  │   ┌──────────────────┐   │
                       │  │ OrderService   │──┼──►│  AWS SQS Queue   │   │
                       │  │ (Flash Sale)   │  │   │  (Async Orders)  │   │
                       │  └───────┬────────┘  │   └────────┬─────────┘   │
                       │          │           │            │             │
                       │  ┌───────▼────────┐  │   ┌───────▼──────────┐   │
                       │  │ Redis Lock     │  │   │ SQS Consumer     │   │
                       │  │ (Lua Scripts)  │  │   │ (TCC Confirm /   │   │
                       │  └───────┬────────┘  │   │  Cancel)         │   │
                       │          │           │   └──────────────────┘   │
                       │  ┌───────▼────────┐  │                          │
                       │  │ TCC Inventory  │  │                          │
                       │  │ (Try/Confirm/  │  │                          │
                       │  │  Cancel)       │  │                          │
                       │  └───────┬────────┘  │                          │
                       └──────────┼───────────┘                          │
                                  │                                      │
               ┌──────────────────┼──────────────────┐                   │
               │                  │                  │                   │
        ┌──────▼──────┐   ┌───────▼──────┐   ┌──────▼──────┐             │
        │  MySQL 8    │   │  Redis 7     │   │  AWS SQS    │             │
        │  (RDS)      │   │ (ElastiCache)│   │  (VPC EP)   │             │
        └─────────────┘   └──────────────┘   └─────────────┘             │
                           └─────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer            | Technology                              |
|------------------|-----------------------------------------|
| Language         | Java 17                                 |
| Framework        | Spring Boot 3.2                         |
| ORM              | MyBatis 3 (XML mappers)                 |
| Database         | MySQL 8.0                               |
| Cache / Lock     | Redis 7 (Lettuce client, Lua scripts)   |
| Message Queue    | AWS SQS (SDK v2, async client)          |
| DB Migration     | Flyway                                  |
| ID Generation    | Snowflake algorithm                     |
| Containerization | Docker (multi-stage, layered JAR)       |
| Orchestration    | AWS ECS Fargate                         |
| Infrastructure   | AWS CDK (TypeScript)                    |
| CI/CD            | AWS CodePipeline + CodeBuild            |
| Monitoring       | CloudWatch Metrics (via Micrometer)     |

---

## Key Features

### CRUD Operations
Full Create / Read / Update / Delete APIs for **Users**, **Products**, **Orders**, and **Inventory**, implemented with MyBatis XML mappers against MySQL. Soft-delete pattern on users and products; Snowflake IDs as primary keys to avoid hotspot inserts and expose no business volume.

### Peak Load Shifting with AWS SQS
Flash sale and Prime Day traffic is decoupled from synchronous request handling. When an order is placed, the HTTP handler:
1. Validates and reserves inventory (TCC Try phase)
2. Persists the order as `PENDING`
3. Sends a lightweight message to SQS
4. Returns `202 Accepted` immediately

A background SQS consumer (long-poll, up to 10 messages at a time) processes orders asynchronously, handling burst traffic without overwhelming the database.

### Redis Distributed Locking
Prevents inventory race conditions during concurrent flash sale requests. Two Lua scripts execute atomically on Redis:

- **Acquire** — `SET key ownerToken NX PX ttlMs`: grants the lock only if the key does not exist.
- **Release** — checks the stored owner token before `DEL`: ensures a slow thread cannot release another thread's lock after TTL expiry.

Retry logic uses exponential backoff (configurable retries and interval). All lock keys are centralized in `LockKeyBuilder`.

### TCC Pattern for Inventory Control
The **Try-Confirm-Cancel** pattern provides distributed transaction semantics without a coordinator framework:

| Phase       | Action on `inventory`                              | Action on `tcc_transactions` |
|-------------|---------------------------------------------------|------------------------------|
| **Try**     | `available_stock -= qty` (guarded: `WHERE available_stock >= qty`) | Insert `TRYING` row          |
| **Confirm** | `total_stock -= qty`, `locked_quantity -= qty`    | Update to `CONFIRMED`        |
| **Cancel**  | `available_stock += qty`, `locked_quantity -= qty`| Update to `CANCELLED`        |

The `WHERE available_stock >= qty` clause in the Try UPDATE is the **oversell guard** — if it returns 0 affected rows, no stock was reserved and the request is rejected with `409 Conflict`. A background scheduler auto-cancels any `TRYING` rows that exceed a 5-minute TTL, guarding against SQS consumer crashes.

### Automated CI/CD
CodePipeline runs `mvn test` → Docker build → ECR push → ECS rolling deploy on every push to `main`. Build time reduced ~30% via layered JAR Docker caching (dependencies layer rarely changes between builds).

---

## Flash Sale Flow

```
POST /api/v1/orders
        │
        ▼
 [OrderController] ──► [OrderServiceImpl.createOrder]
                                │
                    ┌───────────▼────────────┐
                    │ Redis lock on          │
                    │ lock:inventory:{id}    │  ← exponential backoff, max 3 retries
                    │ (Lua SET NX PX 5000ms) │
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │ @Transactional         │
                    │                        │
                    │ TCC.tryLock()          │  ← INSERT tcc_transactions (TRYING)
                    │  UPDATE inventory      │  ← WHERE available_stock >= qty
                    │  (affectedRows == 0?)  │  ← throw InsufficientInventoryException
                    │                        │
                    │ INSERT orders (PENDING)│
                    │ INSERT order_items     │
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │ SQS.sendMessage(async) │  ← fire and forget
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │ Redis lock release     │
                    │ (Lua owner-safe DEL)   │
                    └───────────┬────────────┘
                                │
                    ◄── 202 Accepted {orderId, status: PENDING}

═══════════════════ Async (SQS Consumer) ════════════════════

  poll() [@Scheduled every 1s, long-poll 20s]
        │
        ▼
  parseMessage → check TccStatus (idempotency guard)
        │
        ├── TRYING? → validateOrder()
        │                   │
        │            ┌──────┴──────┐
        │          valid?        invalid?
        │            │               │
        │     TCC.confirm()    TCC.cancel()
        │     status=CONFIRMED  status=CANCELLED
        │
        └── deleteMessage(receiptHandle)   ← only after success
```

---

## Project Structure

```
online-shopping-distributed-system/
│
├── pom.xml                               # Root Maven POM (multi-module)
│
├── shopping-api/                         # Spring Boot application module
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/shop/
│       │   ├── ShoppingApplication.java
│       │   ├── config/
│       │   │   ├── RedisConfig.java      # RedisTemplate + Lua script beans
│       │   │   ├── SqsConfig.java        # SqsAsyncClient (LocalStack-aware)
│       │   │   └── WebConfig.java        # CORS + Jackson ObjectMapper
│       │   ├── domain/                   # Pure Java entities
│       │   │   ├── User / Product / Inventory / Order / OrderItem / TccTransaction
│       │   │   └── enums/  OrderStatus · TccStatus
│       │   ├── dto/
│       │   │   ├── request/  CreateUser · CreateProduct · CreateOrder · UpdateInventory
│       │   │   └── response/ ApiResponse<T> · UserResponse · ProductResponse · OrderResponse · InventoryResponse
│       │   ├── mapper/                   # MyBatis @Mapper interfaces (6 files)
│       │   ├── service/
│       │   │   ├── UserServiceImpl       # Standard CRUD
│       │   │   ├── ProductServiceImpl    # Standard CRUD
│       │   │   ├── InventoryServiceImpl  # Replenish / init stock
│       │   │   ├── OrderServiceImpl      # Flash sale orchestration (lock → TCC → SQS)
│       │   │   ├── tcc/
│       │   │   │   └── TccInventoryServiceImpl  # Try / Confirm / Cancel + cleanup job
│       │   │   └── sqs/
│       │   │       ├── OrderMessageProducer     # Async SQS send
│       │   │       └── OrderMessageConsumer     # @Scheduled poll + TCC dispatch
│       │   ├── lock/
│       │   │   ├── RedisDistributedLock         # Interface
│       │   │   ├── RedisDistributedLockImpl     # Lua script execution + backoff
│       │   │   └── LockKeyBuilder               # Key naming: lock:inventory:{id}
│       │   ├── controller/               # REST endpoints (4 controllers)
│       │   ├── exception/                # GlobalExceptionHandler + typed exceptions
│       │   └── util/
│       │       ├── SnowflakeIdGenerator  # 64-bit distributed IDs
│       │       └── JsonUtil
│       └── resources/
│           ├── application.yml
│           ├── mapper/                   # MyBatis XML files (6 files)
│           ├── scripts/
│           │   ├── redis-lock-acquire.lua
│           │   └── redis-lock-release.lua
│           └── db/migration/
│               ├── V1__init_schema.sql   # All 6 tables
│               └── V2__seed_data.sql     # Sample products + inventory
│
├── docker/
│   ├── Dockerfile                        # Multi-stage layered JAR build
│   ├── docker-compose.yml                # MySQL + Redis + LocalStack + App
│   └── localstack-init.sh                # Creates SQS queues in LocalStack
│
└── infra/                                # AWS CDK (TypeScript)
    ├── bin/infra.ts                      # CDK app entry point (5 stacks)
    └── lib/stacks/
        ├── network-stack.ts              # VPC, subnets, security groups, SQS VPC endpoint
        ├── data-stack.ts                 # RDS MySQL 8, ElastiCache Redis 7, Secrets Manager
        ├── messaging-stack.ts            # SQS queue + DLQ + CloudWatch alarms
        ├── compute-stack.ts              # ECS Fargate, ALB, ECR, auto-scaling
        └── pipeline-stack.ts            # CodePipeline (GitHub → CodeBuild → ECS)
```

---

## Getting Started (Local)

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- (Optional) AWS CLI for CDK deployment

### 1 — Start infrastructure

```bash
cd docker
chmod +x localstack-init.sh
docker-compose up -d
```

This starts MySQL 8, Redis 7, and LocalStack (SQS) and waits for all health checks to pass.

### 2 — Run the application

```bash
# From the project root
mvn -pl shopping-api spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-DSQS_QUEUE_URL=http://localhost:4566/000000000000/order-processing-queue -DSQS_ENDPOINT=http://localhost:4566 -DAWS_ACCESS_KEY_ID=test -DAWS_SECRET_ACCESS_KEY=test"
```

Or build and run the JAR:

```bash
mvn -pl shopping-api package -DskipTests
java -jar shopping-api/target/shopping-api-*.jar \
  --spring.profiles.active=local \
  --SQS_ENDPOINT=http://localhost:4566 \
  --SQS_QUEUE_URL=http://localhost:4566/000000000000/order-processing-queue \
  --AWS_ACCESS_KEY_ID=test \
  --AWS_SECRET_ACCESS_KEY=test
```

The API is available at **http://localhost:8080**.

### 3 — Run tests

```bash
mvn -pl shopping-api test
```

---

## API Reference

All responses follow the envelope: `{ "code": int, "message": string, "data": T }`.

### Users — `/api/v1/users`

| Method | Path          | Description      |
|--------|---------------|------------------|
| POST   | `/`           | Create user      |
| GET    | `/{id}`       | Get user by ID   |
| GET    | `/`           | List all users   |
| PUT    | `/{id}`       | Update user      |
| DELETE | `/{id}`       | Soft-delete user |

**Create user request:**
```json
{ "username": "alice", "email": "alice@example.com", "password": "secret123" }
```

---

### Products — `/api/v1/products`

| Method | Path              | Description              |
|--------|-------------------|--------------------------|
| POST   | `/`               | Create product           |
| GET    | `/{id}`           | Get product by ID        |
| GET    | `/?category=...`  | List (optionally filtered)|
| PUT    | `/{id}`           | Update product           |
| DELETE | `/{id}`           | Soft-delete product      |

---

### Orders — `/api/v1/orders`

| Method | Path              | Description                          |
|--------|-------------------|--------------------------------------|
| POST   | `/`               | Place order (returns 202 PENDING)    |
| GET    | `/{id}`           | Poll order status                    |
| GET    | `/user/{userId}`  | Get all orders for a user            |

**Place order request:**
```json
{
  "userId": 3000000000001,
  "items": [
    { "productId": 1000000000004, "quantity": 1 }
  ],
  "note": "Flash sale order"
}
```

**Response (202 Accepted):**
```json
{
  "code": 202,
  "message": "Order accepted for processing",
  "data": {
    "id": 7318492847362,
    "userId": 3000000000001,
    "status": "PENDING",
    "totalAmount": 99.00,
    "currency": "USD",
    "createdAt": "2026-03-29T10:15:30.123"
  }
}
```

Poll `GET /api/v1/orders/{id}` to check when status transitions to `CONFIRMED` or `CANCELLED`.

---

### Inventory — `/api/v1/inventory`

| Method | Path                             | Description              |
|--------|----------------------------------|--------------------------|
| POST   | `/product/{productId}/init`      | Initialize stock         |
| GET    | `/product/{productId}`           | Get stock levels         |
| PUT    | `/product/{productId}/stock`     | Set total stock          |
| POST   | `/product/{productId}/replenish` | Add stock                |

---

## TCC Pattern

The `tcc_transactions` table acts as the distributed transaction journal.

```
┌──────────────────────────────────────────────────────────────────────┐
│  tcc_transactions                                                     │
│                                                                      │
│  id · order_id · product_id · quantity · status · try_expire_at      │
└──────────────────────────────────────────────────────────────────────┘

          Try                   Confirm                  Cancel
           │                      │                        │
   INSERT (TRYING)         UPDATE (CONFIRMED)       UPDATE (CANCELLED)
   available -= qty        total_stock -= qty       available += qty
   locked   += qty         locked      -= qty       locked   -= qty
           │                      │                        │
   TTL: now + 5 min      ← SQS consumer drives →  (payment fail / TTL)
           │                      │                        │
   if TTL expires with no confirm/cancel:
   @Scheduled cleanup job auto-cancels → returns stock
```

**Oversell prevention SQL** (the key guard):
```sql
UPDATE inventory
SET available_stock = available_stock - #{quantity},
    locked_quantity = locked_quantity  + #{quantity}
WHERE product_id = #{productId}
  AND available_stock >= #{quantity}   -- ← 0 rows = stock insufficient
```

If `affectedRows == 0`, `InsufficientInventoryException` is thrown and the transaction rolls back. No stock is ever over-committed.

---

## Redis Distributed Lock

### Acquire (`redis-lock-acquire.lua`)
```lua
-- Atomic: no race between existence check and set
local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', tonumber(ARGV[2]))
return result and 1 or 0
```

### Release (`redis-lock-release.lua`)
```lua
-- Owner-safe: only the caller who acquired can release
if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
return 0
```

### Configuration

```yaml
app:
  lock:
    ttl-ms:            5000   # Lock auto-expires after 5 seconds
    retry-times:          3   # Max retry attempts on contention
    retry-interval-ms:  100   # Backoff base (doubles each retry: 100, 200, 400ms)
```

The `ownerToken` is `UUID + threadId`, making each lock attempt uniquely identifiable. Exponential backoff prevents thundering-herd on highly contended keys (e.g., flash sale product ID).

---

## AWS Infrastructure

Five CDK stacks deployed in dependency order:

```
NetworkStack  →  DataStack  →  MessagingStack  →  ComputeStack  →  PipelineStack
```

| Stack          | Resources                                                                 |
|----------------|---------------------------------------------------------------------------|
| NetworkStack   | VPC (2 AZs, 3 subnet tiers), Security Groups, SQS VPC Interface Endpoint |
| DataStack      | RDS MySQL 8 (Multi-AZ in prod), ElastiCache Redis 7, Secrets Manager     |
| MessagingStack | SQS main queue + DLQ (maxReceiveCount=3), CloudWatch alarms               |
| ComputeStack   | ECS Fargate (1 vCPU / 2 GB), ALB, ECR, CPU + SQS-depth auto-scaling      |
| PipelineStack  | CodePipeline (GitHub → CodeBuild → ECS rolling deploy), S3 artifacts      |

### Deploy

```bash
cd infra
npm install

# Bootstrap (first time only)
cdk bootstrap --context stage=dev

# Deploy all stacks
cdk deploy --all \
  --context stage=dev \
  --context githubOwner=your-org \
  --context githubRepo=online-shopping-distributed-system
```

Prod deployment:
```bash
cdk deploy --all --context stage=prod --context githubOwner=your-org --context githubRepo=...
```

Prod differences: Multi-AZ RDS, 2 Redis replicas, `cache.r6g.large`, 2–10 ECS tasks, 7-day backup retention, deletion protection.

---

## CI/CD Pipeline

```
GitHub push to main
        │
        ▼
  [Source Stage]
  CodePipeline detects push via GitHub webhook
        │
        ▼
  [Build Stage — CodeBuild]
  1. mvn test                    ← unit + integration tests (JUnit 5)
  2. mvn package -DskipTests     ← produces layered JAR
  3. docker build                ← multi-stage Dockerfile
  4. docker push → ECR           ← tagged with commit SHA
  5. write imagedefinitions.json ← consumed by deploy stage
        │
        ▼
  [Deploy Stage — ECS]
  Rolling update: new task starts → health check passes → old task stops
  Circuit breaker: automatic rollback if health checks fail
        │
        ▼
  ~30% faster than naive single-stage builds
  (Docker layer cache: dependencies layer cached between commits)
```

### Build spec highlights

```yaml
pre_build:
  - aws ecr get-login-password | docker login ...
  - COMMIT_HASH=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c 1-7)

build:
  - mvn -B test --no-transfer-progress
  - mvn -B package -DskipTests --no-transfer-progress
  - docker build -t $ECR_REPO_URI:$COMMIT_HASH -f docker/Dockerfile .

post_build:
  - docker push $ECR_REPO_URI:$COMMIT_HASH
  - docker push $ECR_REPO_URI:latest
  - printf '[{"name":"shopping-api","imageUri":"%s"}]' "$ECR_REPO_URI:$COMMIT_HASH" > imagedefinitions.json
```

---

## Configuration

All sensitive values are injected via environment variables (locally via docker-compose; in ECS via Secrets Manager).

| Variable                   | Description                               | Default (local)                                              |
|----------------------------|-------------------------------------------|--------------------------------------------------------------|
| `DB_HOST`                  | MySQL hostname                            | `localhost`                                                  |
| `DB_PORT`                  | MySQL port                                | `3306`                                                       |
| `DB_NAME`                  | Database name                             | `shopping`                                                   |
| `DB_USER`                  | MySQL username                            | `root`                                                       |
| `DB_PASSWORD`              | MySQL password                            | `root`                                                       |
| `REDIS_HOST`               | Redis hostname                            | `localhost`                                                  |
| `REDIS_PORT`               | Redis port                                | `6379`                                                       |
| `REDIS_PASSWORD`           | Redis auth password                       | _(empty)_                                                    |
| `AWS_REGION`               | AWS region                                | `us-east-1`                                                  |
| `SQS_ENDPOINT`             | Override SQS endpoint (LocalStack)        | _(empty = real AWS)_                                         |
| `SQS_QUEUE_URL`            | SQS order queue URL                       | `http://localhost:4566/000000000000/order-processing-queue`  |
| `CLOUDWATCH_METRICS_ENABLED` | Enable CloudWatch metric publishing     | `false`                                                      |
| `MACHINE_ID`               | Snowflake worker ID (0–1023)              | `1`                                                          |

---

## Database Schema

Six tables managed by Flyway migrations:

| Table              | Purpose                                                   |
|--------------------|-----------------------------------------------------------|
| `users`            | Customer accounts (soft-delete)                           |
| `products`         | Product catalogue (soft-delete)                           |
| `inventory`        | Stock levels: `total_stock`, `available_stock`, `locked_quantity` |
| `orders`           | Order header with status lifecycle                        |
| `order_items`      | Line items with price snapshot at order time              |
| `tcc_transactions` | TCC coordinator journal; indexed by `(status, try_expire_at)` for cleanup job |

Migrations are applied automatically on startup via Flyway. Sample data (4 products, 1 user) is loaded by `V2__seed_data.sql`.
