# Evented MSA with RabbitMQ (PoC)

[한국어로 보기](docs/README_KR.md)

## Exchange and queue strategies

### 1. Exclusive exchange per service + service-specific routing prefix

- What: Each microservice publishes to its own exchange (e.g., serviceA.events, serviceB.events), and uses a consistent
  prefix (serviceA.*). Consumers of that service bind to its exchange.
- Pros: Strong isolation (policies/permissions per service), predictable naming, easy to decommission a service, reduced
  blast radius on misconfiguration.
- Cons: More exchanges/bindings to manage; cross-service routing requires extra bindings or publisher awareness of
  multiple exchanges.

### 2. Central shared exchange with namespaced routing keys

- What: One exchange for all services (e.g., app.events), routing keys are namespaced (serviceA.\*,
  serviceB.\*). Consumers bind to the shared exchange with their namespace patterns.
- Pros: Simplest operationally (one exchange), easy to add consumers; minimal broker objects.
- Cons: Weaker isolation—policy/permission and DLX/TTL tuning are harder per service; potential
  routing-key collisions if conventions slip; noisy shared space.

### 3. Per-service exchange + inter-exchange (“bridge”) bindings

- What: Each service has its own exchange, but exchanges can bind to each other (or to a hub
  exchange) so messages can be fanned out across services without publishers knowing all targets.
- Pros: Keeps service isolation while enabling cross-service fan-out; publishers stay simple (emit to
  their own exchange), and routing logic can be centrally managed via bindings.
- Cons: More complex topology to reason about; bridge misconfigurations can create loops or
  unexpected fan-out; higher operational overhead for binding maintenance and debugging.

## Reliability patterns for messaging

### RabbitMQ baked-in reliability levers

- Durable topology and persistent messages: declare exchanges/queues as durable and use `deliveryMode=PERSISTENT`.
- Publisher confirms and returns: keep `mandatory=true`, handle returns, and alert on NACK/return anomalies.
- Dead-lettering and alternate exchanges: route failures/unroutables to DLQ/AE for visibility instead of silent drop.
- Retry via delayed/TTL routing (not hot requeue): use per-queue TTL or delayed exchange to implement backoff.
- Quorum queues / HA policies: prefer quorum queues for higher availability and recovery characteristics.
- Observability hooks: enable health checks, log publish/consume/ack/nack with `messageId`/`traceId`, and emit metrics (
  publish/consume/DLQ).

### Application/infra reliability levers (outside the broker defaults)

- Idempotent consumers: deduplicate by `messageId` to make at-least-once delivery safe.
- Outbox pattern: DB-transactional event persistence + relay to broker for “no data/message split-brain.”
- Schema/version validation: include `schema-version` and validate; park or reject incompatible payloads.
- Operational hygiene: monitor DLQ/returns, tune cluster sizing/disk/network, and run backups/upgrades safely.

### Outbox pattern (RabbitMQ + per-service exchange/prefix)

- Strengths: Guarantees “data saved but message not sent” cannot happen by persisting the event in the same DB
  transaction; reuse outbox PK/UUID as `messageId` to enable tracing/idempotency; enables replay/monitoring from the
  outbox table.
- Costs: Extra table storage/maintenance (partitioning/TTL), added latency from polling/CDC, DB load from polling, and
  operational overhead (CDC/batch, cleanup). Idempotency is needed in both the relay (outbox→broker) and consumer.
- RabbitMQ integration: Store routing key, headers, and payload in the outbox so the relay can emit exactly what
  will be sent; define backoff/retry policy for relay failures; set DLQ and outbox state together to build
  “sent/consumed” visibility; use outbox PK/UUID as `messageId` and propagate `traceId`. If the system is simple and
  latency-sensitive, starting with direct publish + `messageId` logging/metrics can be sufficient before introducing
  outbox.
- Decision points:
    - Is added latency from polling/CDC acceptable?
    - Does the DB have headroom for outbox writes/reads plus indexing?
    - Is there operational capacity to run CDC/relay/cleanup?
    - Are downstream consumers prepared for idempotent handling keyed by `messageId`?
    - Are compliance/traceability requirements strict enough to justify the extra plumbing?

## How to run and test this PoC

- Start RabbitMQ (Docker): `cd docker && docker compose up -d` (management UI: http://localhost:15672, guest/guest).
- Run the app with a profile:
    - Service A only: `./gradlew bootRun --args='--spring.profiles.active=service-a'`
    - Service B only: `./gradlew bootRun --args='--spring.profiles.active=service-b'`
    - Both (A+B): `./gradlew bootRun --args='--spring.profiles.active=both'`
    - Cross (B events consumed by A and B): `./gradlew bootRun --args='--spring.profiles.active=cross'`
- Publish (Service A):
    - Custom JSON:
      `curl -X POST http://localhost:8080/api/service-a/events -H "Content-Type: application/json" -d '{"eventType":"order.created","payload":{"orderId":"ORD-123","customerId":"C-1","totalAmount":12000,"currency":"KRW","items":[{"sku":"SKU-1","qty":1,"price":12000}]}}'`
    - Simple trigger (HEAD, random payload):
      `curl -I http://localhost:8080/api/service-a/events`
- Publish (Service B):
    - Custom JSON:
      `curl -X POST http://localhost:8080/api/service-b/events -H "Content-Type: application/json" -d '{"eventType":"user.created","payload":{"userId":"USER-1","email":"user1@example.com","name":"Sample User","status":"ACTIVE"}}'`
    - Simple trigger (HEAD, random payload):
      `curl -I http://localhost:8080/api/service-b/events`
- IDE HTTP client: see `requests.http` for ready-to-run requests (POST/HEAD for Service A/B).

### Message Header Sample

```json
{
  "messageId": "c3f9f7f4-5d2c-4d91-a18e-56e2b7a90b8e",
  "correlationId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
  "type": "order.created",
  "appId": "serviceA",
  "timestamp": "2025-02-12T09:15:27.123Z",
  "contentType": "application/json",
  "contentEncoding": "utf-8",
  "deliveryMode": "PERSISTENT",
  "headers": {
    "traceId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
    "schema-version": "1.0"
  }
}
```

### Message Body Sample

```json
{
  "eventType": "order.created",
  "occurredAt": "2025-02-12T09:15:27.123Z",
  "publishedAt": "2025-02-12T09:15:27.123Z",
  "payload": {
    "orderId": "ORD-12345",
    "customerId": "C-999",
    "totalAmount": 12000,
    "currency": "KRW",
    "items": [
      {
        "sku": "SKU-1",
        "qty": 1,
        "price": 8000
      },
      {
        "sku": "SKU-2",
        "qty": 2,
        "price": 2000
      }
    ]
  },
  "metadata": {
    "sourceService": "serviceA",
    "traceId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
    "messageId": "c3f9f7f4-5d2c-4d91-a18e-56e2b7a90b8e",
    "schemaVersion": "1.0",
    "priority": "normal",
    "sensitivity": "internal"
  }
}
```

---

## Interacting with the AI Agent

This project includes an AI agent that acts as a **Technical Guide** to help developers learn RabbitMQ through this
PoC (Proof of Concept). You can ask the agent questions about the project's code, architecture, and the RabbitMQ
concepts implemented within it.

The agent's primary role is to analyze the project's code and explain how things work. It can help you understand
messaging patterns, MSA integration, and the specific implementation details of this PoC.

### How to use AI Agent

1. To interact with the AI agent, execute the following command from the project root directory.
2. To make the agent read the `AGENTS.md` file as its first command, you can typically instruct it to do so after
   invocation.
