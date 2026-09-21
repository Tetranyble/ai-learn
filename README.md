# AI Learn Service

AI Learn is a Spring Boot reference application for building durable,
streaming AI conversations with LangChain4j and OpenAI.

It is intentionally structured as more than a request-in/response-out demo. Chat
turns are persisted before model execution, survive process failures, can be
queued or interrupted, and can be delivered to the UI over WebSocket or SSE.

## What is implemented

- Spring Boot 4.1, Java 25, Jersey/JAX-RS, Hibernate/JPA, and manual
  `EntityManager` repositories
- LangChain4j streaming integration with persistent conversation memory
- MySQL as the authoritative store for conversations, messages, runs, and the
  transactional outbox
- Redis Streams for bounded replay after a client disconnects
- Redis Pub/Sub for low-latency multi-node dispatch, cancellation, and fanout
- WebSocket commands and live events at `ws://localhost:8080/ws/chat`
- SSE streaming as a read-only alternative
- Queue and interrupt/steering modes
- Explicit cancellation without submitting another message
- Message IDs, run IDs, `replyToId`, idempotency keys, event IDs, and replay
  cursors
- Consistent REST response envelopes, validation, global exception handling,
  request IDs, and structured production logging
- Flyway migrations, development factories/seeding, and automated tests

## Documentation map

- [Architecture and end-to-end flows](docs/CHAT_ARCHITECTURE.md)
- [REST, SSE, and WebSocket reference](docs/CHAT_PROTOCOL.md)
- [Configuration, deployment, recovery, and operations](docs/OPERATIONS.md)

Start with the architecture guide if you are studying how all components fit
together. Use the protocol guide while implementing a frontend.

## Requirements

- Java 25
- Maven 3.9+
- MySQL 8+
- Redis 7+ for multi-node fanout and durable event replay
- An OpenAI API key

Redis can be disabled for single-process development. In that mode, live events
and replay are held only in application memory and disappear on restart.

## Local setup

1. Create the database:

   ```sql
   CREATE DATABASE ai_learn;
   ```

2. Copy the environment template:

   ```bash
   cp .env.example .env
   ```

3. Set `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY` in `.env`.

4. Enable Redis when you want to exercise the distributed flow:

   ```properties
   CHAT_REDIS_ENABLED=true
   REDIS_URL=redis://localhost:6379/0
   ```

5. Start the development profile:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   If the Maven wrapper is unavailable, use:

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

Flyway applies the schema automatically. The `dev` profile seeds 20 courses only
when the courses table is empty; chat data is never automatically seeded.

## First chat

Start a durable conversation:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Request-ID: tutorial-001' \
  -d '{"message":"Explain dependency injection"}'
```

The response is `202 Accepted` and contains:

- the new `conversationId`
- a durable queued run
- the user message and placeholder assistant message
- the HTTP `requestId` in `meta`

The model executes asynchronously. Use either:

```text
ws://localhost:8080/ws/chat
```

or:

```text
GET /api/v1/chat/{conversationId}/runs/{runId}/stream
```

Reload the complete UI state with:

```text
GET /api/v1/chat/{conversationId}?after=0&limit=100
```

## Folder structure

```text
src/main/java/com/tetranyble/ailearn
├── api/          response envelope, request IDs, HTTP logging
├── chat/         conversations, messages, runs, memory, outbox, Redis,
│                 WebSocket, SSE, worker coordination
├── config/       Jersey registration
├── course/       example CRUD resource and manual repository
├── database/     development seeder and factories
├── exception/    domain exceptions and global JAX-RS exception mappers
└── validation/   validation exception and constraint mappers

src/main/resources
├── application.properties
├── application-dev.properties
├── application-prod.properties
├── application-test.properties
└── db/migration/ Flyway migrations V1 through V5

docs/
├── CHAT_ARCHITECTURE.md
├── CHAT_PROTOCOL.md
└── OPERATIONS.md
```

## Tests

```bash
mvn test
```

The suite covers persistence, validation, chat state transitions, event encoding,
in-memory replay/deduplication, and a real WebSocket handshake alongside Jersey.

## Important production boundary

The architecture supports horizontal application scaling, durable runs, and
resumable delivery. Authentication, conversation ownership, tenant isolation,
rate limiting, moderation, and provider quotas require the product's real
identity and policy model and are therefore documented integration boundaries,
not placeholder security code.
