# TicketOrchestra: Distributed Ticket Reservation Prototype

## 1. Project Overview
TicketOrchestra is a high-performance, distributed ticket reservation system prototype. It focuses on the **core reservation process**, demonstrating advanced Java engineering practices, high concurrency with Virtual Threads, and eventual consistency using the Saga pattern in a cloud-native environment.

For detailed information regarding design decisions, architectural trade-offs, and future evolution (including API Gateways, Global Scalability, and Search Strategies), please refer to the [ARCHITECTURE.md](ARCHITECTURE.md) document.

---

## 2. Technical Stack
- **Runtime**: Java 21 (Virtual Threads / Project Loom)
- **Framework**: Spring Boot 4.0.6
- **Build System**: Gradle
- **Database/Messaging**: Amazon DynamoDB & SQS (via LocalStack for development)
- **Observability**: OpenTelemetry, Jaeger, Micrometer & Prometheus
- **Testing**: Testcontainers (LocalStack), REST Assured, Spring Modulith, ArchUnit

---

## 3. Functional Workflow

### 1. Bootstrap: Event Creation (`POST /v1/inventory/events`)
Before you can reserve a seat, you must create an event in the system:
- **Endpoint**: `POST /v1/inventory/events`
- **Action**: Provides the system with the event details and initial seat inventory.

### 2. The Reservation Flow (`POST /v1/reservations`)
1. **Parallel Pre-checks**: Uses **Virtual Threads** to concurrently call `AntiFraudService` and `PricingService` without blocking platform threads.
2. **Atomic State Change**: Uses DynamoDB **Transactions** to lock seats, save the reservation, and record the Outbox event in a single atomic operation.
3. **Eventual Consistency**: The `OutboxRelay` (Poller with Lease mechanism) ensures at-least-once delivery of events to SQS.
4. **Saga Orchestration**: The system handles both success and failure (compensation) paths to ensure seats are never stuck in a `LOCKED` state.

---

## 4. Development Guide
### Prerequisites
- Java 21
- Docker & Docker Compose

### Running Locally
1. Start infrastructure:
   ```bash
   docker-compose up -d
   ```
2. Run the application:
   - **Via Gradle**: `./gradlew bootRun --args='--spring.profiles.active=local'`
   - **Via Docker**: `docker build -t ticket-orchestra . && docker run -p 8080:8080 ticket-orchestra`

Access Swagger UI at: `http://localhost:8080/swagger-ui.html`
Access Jaeger UI at: `http://localhost:16686`

### Running Tests
```bash
./gradlew test # Unit and Modulith tests
./gradlew integrationTest # Full E2E flows with Testcontainers
```
