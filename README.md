# TicketOrchestra: Distributed Ticket Reservation & Saga Symphony

## 1. Project Overview
TicketOrchestra is a high-performance, distributed ticket reservation system designed to handle massive traffic spikes during concert ticket drops. The project demonstrates advanced Java engineering practices, focusing on high concurrency, eventual consistency, and robust error handling in a cloud-native environment.

### Core Value Proposition
- **High Concurrency**: Leveraging Java 25 Virtual Threads to handle thousands of simultaneous reservations.
- **Reliability**: Implementing the Saga Pattern (Choreography) to manage distributed transactions across AWS services.
- **Data Integrity**: Using DynamoDB with Optimistic Locking and Transactional Outbox patterns.
- **Observability**: Full traceability with AWS X-Ray and CloudWatch.

---

## 2. Architecture & Design Decisions (ADRs)

### ADR 1: Saga Choreography over Orchestration
**Decision**: Use an event-driven choreography approach via Amazon SQS.
**Rationale**: In a high-load ticket system, a central orchestrator can become a bottleneck. Choreography allows services (Reservation, Payment, Inventory) to scale independently and reduces tight coupling.

### ADR 2: Java 25 Virtual Threads (Project Loom)
**Decision**: Use Virtual Threads for I/O-bound parallel tasks.
**Rationale**: When a reservation starts, we concurrently check fraud, calculate pricing, and lock seats. Virtual threads allow us to perform these blocking I/O calls to AWS services without exhausting the platform thread pool, maintaining extremely high throughput.

### ADR 3: DynamoDB with Optimistic Locking
**Decision**: Use Amazon DynamoDB as the primary store for reservations and seat inventory.
**Rationale**: DynamoDB provides single-digit millisecond latency at any scale. We use `@Version` fields for optimistic locking to prevent race conditions (double-booking) during peak sales.

### ADR 4: Transactional Outbox Pattern
**Decision**: Ensure atomicity between DynamoDB updates and SQS message emission.
**Rationale**: To prevent data inconsistency (e.g., a seat is locked in DB but the payment event is never sent), we store events in an `Outbox` table/collection within the same transaction as the business state change.

### ADR 5: Modular Monolith (Spring Modulith)
**Decision**: Structure the application as a modular monolith before considering microservices.
**Rationale**: This keeps the deployment simple (one container in ECS) while enforcing strict domain boundaries. Spring Modulith ensures that modules like `Payment` and `Reservation` don't leak internals to each other.

---

## 3. Technical Stack
- **Runtime**: Java 25 (Amazon Corretto)
- **Framework**: Spring Boot 4.0.x
- **Build System**: Gradle (Kotlin DSL)
- **Cloud Infrastructure**:
    - **Amazon DynamoDB**: State management and seat locking.
    - **Amazon SQS**: Distributed messaging for the Saga.
    - **AWS ECS Fargate**: Serverless container execution.
    - **AWS X-Ray**: Distributed tracing.
- **API**: SpringDoc OpenAPI (Swagger UI)
- **Testing & QA**:
    - **Testcontainers**: Integration tests with LocalStack.
    - **ArchUnit**: Architecture compliance enforcement.
    - **Spring Modulith**: Module boundary verification.
    - **SpotBugs/Checkstyle**: Static analysis.

---

## 4. Functional Workflow

### The Reservation Flow (`POST /v1/reservations`)
1. **Parallel Execution (Virtual Threads)**:
    - `AntiFraudService`: Validates user limits and velocity.
    - `PricingService`: Calculates dynamic price based on remaining inventory.
    - `InventoryService`: Attempts to lock the seat using a conditional update in DynamoDB.
2. **Persistence**: Save reservation as `PENDING`.
3. **Outbox**: Record a `RESERVATION_CREATED` event.
4. **Messaging**: SQS carries the event to the `PaymentService`.

### The Saga Compensation
- If `PaymentService` fails (insufficient funds, timeout):
    - Emit `PAYMENT_FAILED` event.
    - `ReservationService` consumes the event, reverts the seat lock in DynamoDB, and marks the reservation as `CANCELLED`.

---

## 5. CI/CD & Deployment
- **GitHub Actions**:
    - Automated OIDC-based authentication with AWS.
    - Runs full test suite (Unit, Integration, Architecture).
    - Checks modularity with Spring Modulith.
    - Builds and pushes Docker image to Amazon ECR.
    - Triggers Rolling Update in ECS Fargate.

---

## 6. Development Guide
### Prerequisites
- Java 25
- Docker (for Testcontainers/LocalStack)
- AWS CLI (for local configuration)

### Running Locally
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
Access Swagger UI at: `http://localhost:8080/swagger-ui.html`

### Running Tests
```bash
./gradlew test # Includes Unit, ArchUnit, and Modulith tests
./gradlew integrationTest # Requires Docker for LocalStack
```
