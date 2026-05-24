# Architecture: TicketOrchestra

This document captures the architectural decisions, trade-offs, and technical debt associated with the TicketOrchestra project.

## 1. Architectural Decision Records (ADRs)

### ADR 1: Saga Choreography over Orchestration
**Decision**: Use an event-driven choreography approach via **Amazon SQS**.
**Rationale**: Decouples services (Reservation, Payment, Inventory) and allows them to scale independently, preventing a central orchestrator bottleneck.

### ADR 2: Java 21 Virtual Threads (Project Loom)
**Decision**: Use Virtual Threads for concurrent pre-reservation checks (Anti-fraud, Pricing).
**Rationale**: Enables high-throughput, blocking I/O calls without exhausting platform thread pools.

### ADR 3: DynamoDB with Atomic Transactions
**Decision**: Use `transactWriteItems` for the critical reservation path.
**Rationale**: Ensures that seat locking, reservation persistence, and the Outbox event are committed atomically, preventing inconsistent states.

### ADR 4: Transactional Outbox Pattern
**Decision**: Ensure atomicity between state changes and event emission.
**Rationale**: Guarantees that every reservation change eventually triggers the next step in the Saga, even if the application or messaging system fails momentarily.

### ADR 5: Modular Monolith (Spring Modulith)
**Decision**: Structure the application as a modular monolith.
**Rationale**: Enforces strict domain boundaries, making the code maintainable and ready for future microservices extraction while keeping deployment simple.

### ADR 6: Multi-Table DynamoDB Design
**Decision**: Use separate tables (`Reservations`, `Seats`, `Outbox`) instead of Single Table Design (STD).
**Rationale**: In a Modular Monolith, each module owns its data. STD would create tight coupling at the database level. Using `transactWriteItems` achieves atomicity without sacrificing module independence.

---

## 2. Technical Debt & Planned Improvements

- **Module Decoupling**: Currently, `DynamoDbReservationCreationStore` directly accesses the `Seats` table belonging to the `Inventory` module. This violates modular boundaries. 
  - *Mitigation Path*: Encapsulate seat-locking logic into a dedicated Command API within the `Inventory` module or transition to a fully event-driven flow.
- **Idempotency**: Current listeners use simple state-checks. A production roadmap includes a dedicated `Inbox` table or transaction-guarded status transitions to robustly handle SQS "at-least-once" delivery edge cases.
- **Resilience**: The architecture is ready for **Resilience4j** (Circuit Breakers/Retries). Currently, parallel calls rely on `CompletableFuture` timeouts.
- **Security**: Authentication is intentionally omitted to focus on business logic. In production, we would extract `userId` from a JWT via Spring Security.

---

## 3. Strategic Roadmap

- **API Gateway**: Design for an API Gateway (e.g., Spring Cloud Gateway) to enable the **Strangler Fig Pattern**, allowing seamless module extraction into microservices.
- **High-Contention Scaling**: Implement a **Virtual Waiting Room** (using Redis) for massive "hot-key" events (100k+ users) to prevent DynamoDB throttling.
- **Search & Discovery**: Sync data to **OpenSearch** via **DynamoDB Streams** to support complex search queries (genre, date, location).
- **Global Scalability**: Plan for **Multi-region deployment** with **DynamoDB Global Tables** and **Route 53 Traffic Steering**.
- **Container Orchestration**: Production-grade lifecycle management via **Kubernetes (EKS)** or **ECS Fargate** with Blue/Green deployment and strict resource **Guardrails**.
