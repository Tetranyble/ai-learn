# Configuration and operations

This is the deployment and failure-handling companion to
[CHAT_ARCHITECTURE.md](CHAT_ARCHITECTURE.md).

## Profiles

| Profile | Database | Flyway | Redis | Logging | Intended use |
|---|---|---|---|---|---|
| default | `.env` MySQL | enabled | configurable | normal | local/base configuration |
| `dev` | `.env` MySQL | enabled | configurable | readable DEBUG, optional SQL, model request/response logging | development only |
| `prod` | environment MySQL | enabled | should be enabled | JSON/logstash console, no SQL/model payloads | deployment |
| `test` | in-memory H2 | disabled | disabled | test context | automated tests |

Do not enable model request/response logging in production: prompts and responses
can contain personal or confidential data.

## Environment reference

### Database

| Variable | Required | Example | Purpose |
|---|---:|---|---|
| `DB_URL` | yes | `jdbc:mysql://localhost:3306/ai_learn` | JDBC connection URL |
| `DB_USERNAME` | yes | `ai_learn` | Database user |
| `DB_PASSWORD` | yes | secret | Database password |

The production schema mode is `validate`; Flyway owns schema changes. Never use
Hibernate auto-update for production migrations.

### OpenAI and LangChain4j

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | none | Provider credential |
| `OPENAI_MODEL` | `gpt-4o` | Streaming chat model |
| `OPENAI_TIMEOUT` | `60s` | Provider request timeout |
| `OPENAI_MAX_COMPLETION_TOKENS` | `1000` | Maximum generated tokens |
| `CHAT_MEMORY_MAX_MESSAGES` | `20` | LangChain4j message-window capacity |

`CHAT_MEMORY_MAX_MESSAGES` limits the active model context managed by
`MessageWindowChatMemory`. The persisted transcript remains available even when
older entries leave this window.

### Durable work and outbox

| Variable | Default | Purpose |
|---|---|---|
| `CHAT_RECOVERY_SCAN_INTERVAL` | `10s` | Scan queued or lease-expired runs |
| `CHAT_OUTBOX_RECOVERY_INTERVAL` | `10s` | Safety scan for unpublished outbox rows |
| `CHAT_OUTBOX_BATCH_SIZE` | `100` | Maximum events claimed per batch |
| `CHAT_OUTBOX_RETENTION` | `7d` | Keep published outbox audit rows |
| `CHAT_OUTBOX_CLEANUP_INTERVAL` | `1h` | Published-row cleanup cadence |

Normal outbox publication is event-driven immediately after transaction commit.
The recovery interval is not the normal delivery latency and should not be set to
milliseconds.

### Redis

| Variable | Default | Purpose |
|---|---|---|
| `CHAT_REDIS_ENABLED` | `false` | Enable distributed signals and durable replay |
| `REDIS_URL` | `redis://localhost:6379/0` | Redis connection URL; may point to managed Redis |
| `REDIS_CLIENT_NAME` | `ai-learn` | Connection identity in Redis diagnostics |
| `REDIS_CONNECT_TIMEOUT` | `2s` | Connection establishment timeout |
| `REDIS_READ_TIMEOUT` | `5s` | Redis operation timeout |
| `CHAT_EVENT_RETENTION` | `24h` | Redis Stream key expiry after latest event |
| `CHAT_EVENT_MAX_PER_RUN` | `2000` | Approximate maximum records retained per run |

Use TLS credentials in `REDIS_URL` when required by the provider. Secret-bearing
URLs belong in the deployment secret store, not Git.

### Streaming transports

| Variable | Default | Purpose |
|---|---|---|
| `CHAT_SSE_HEARTBEAT_INTERVAL_MS` | `15000` | SSE heartbeat period |
| `CHAT_WEBSOCKET_SEND_TIME_LIMIT_MS` | `10000` | Maximum buffered send duration |
| `CHAT_WEBSOCKET_BUFFER_SIZE_LIMIT_BYTES` | `1048576` | Per-socket outbound buffer limit |
| `CHAT_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` | `*` locally; required in prod | Permitted browser origins |

Production example:

```properties
CHAT_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://app.example.com
```

## Flyway policy

Migrations are immutable after being applied. Current migrations are:

| Version | Purpose |
|---|---|
| V1 | Course table |
| V2 | Conversations, messages, and memory |
| V3 | Message reply relationships |
| V4 | Durable run fields and `chat_runs` |
| V5 | Transactional chat outbox |

When a schema changes, create V6 or later. Do not edit V1-V5 after shared
environments have applied them; doing so produces checksum mismatches. `flyway
repair` is only appropriate when you have verified that the database schema and
the intended migration are already equivalent.

## Deployment topology

For one application instance:

```text
UI -> Spring Boot -> MySQL -> OpenAI
                    optional Redis
```

For horizontal scaling:

```text
                         +-> node A --+
UI -> TLS load balancer -+-> node B --+-> MySQL
                         +-> node C --+-> Redis
                                      +-> OpenAI
```

All nodes must share MySQL and Redis. They should use the same application
version while a migration is rolling out. A WebSocket remains attached to one
node for its lifetime, but sticky sessions are not required for reconnect:
Redis replay and MySQL state let another node resume it.

Run Flyway once as a release job, or ensure only one instance migrates while
others wait for schema readiness. Back up MySQL before destructive migrations.

## Reverse proxy requirements

The load balancer or ingress must:

- support HTTP/1.1 WebSocket upgrades for `/ws/chat`;
- disable response buffering for the SSE route;
- allow SSE/WebSocket idle timeouts longer than the heartbeat interval;
- pass `X-Request-ID` or allow the service to generate it;
- terminate TLS and use `wss://`/HTTPS externally;
- apply appropriate request/body size and connection limits.

## Logging and traceability

For HTTP, `RequestLoggingFilter`:

1. validates or generates `X-Request-ID`;
2. places it in SLF4J MDC;
3. adds it to the response header and JSON `meta`;
4. logs method, path, status, and duration.

For WebSocket commands, the client sends `requestId`. The handler places it in
MDC while processing and echoes it in acknowledgement/error frames. Asynchronous
chat events additionally have `eventId`, `conversationId`, `runId`, and
`messageId` for correlation.

Production structured logs should be sent to a central log system and indexed by
these fields. Never log API keys, authorization headers, or full prompts by
default.

## Metrics and alerts to add

The project does not yet include Spring Boot Actuator or a metrics backend. A
production deployment should instrument:

- queued runs and oldest queued age;
- processing runs with expired leases;
- generation latency and time to first token;
- provider failures, timeouts, and cancellations;
- outbox unpublished count, oldest age, attempts, and publish latency;
- Redis operation latency/failures and Pub/Sub reconnects;
- active WebSocket/SSE connections and rejected/buffer-exceeded sends;
- token usage and estimated provider cost by tenant/model;
- database pool utilization and lock wait time.

Alert on age, not only count. One old queued run or outbox event can expose a
stuck workflow even when overall volume is low.

## Operational diagnostics

### Queued or expired work

```sql
SELECT id, conversation_id, status, worker_id, lease_expires_at, created_at
FROM chat_runs
WHERE status = 'QUEUED'
   OR (status = 'PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP(6))
ORDER BY created_at;
```

### Unpublished outbox records

```sql
SELECT id, run_id, attempts, claimed_by, claimed_until, created_at
FROM chat_outbox_events
WHERE published_at IS NULL
ORDER BY created_at;
```

### Redis run event stream

```bash
redis-cli XRANGE ai-learn:chat:run:RUN_UUID:events - + COUNT 20
```

These checks are diagnostic. Avoid manually modifying states while workers are
active unless an incident procedure explicitly defines the transition.

## Dependency failure behavior

### Redis unavailable

- MySQL commands can still commit durable runs and outbox rows.
- The local node can still attempt local execution.
- Cross-node wakeup, low-latency cancellation, and live fanout are degraded.
- Outbox events remain pending and retry after Redis recovers.
- Clients should reload MySQL-backed state if real-time delivery is uncertain.

### OpenAI unavailable

The active run and assistant message transition to `failed` with
`chat_provider_error`. Global exception handling protects synchronous endpoints;
asynchronous failures are represented in durable run state and terminal events.

### MySQL unavailable

New commands cannot be durably accepted and must fail. Do not silently place them
only in Redis: that would violate MySQL's source-of-truth guarantee. Clients may
retry with the same idempotency key after recovery.

### Application node terminated

In-flight WebSocket/SSE connections close. Processing run leases expire, another
node restarts the run, pending outbox rows are reclaimed, and clients reconnect
using their last cursor before reconciling against REST state.

## Capacity and evolution

The current model is appropriate before the database/outbox publisher becomes a
bottleneck. At higher scale, preserve the domain protocol while evolving
infrastructure:

- use dialect-specific `SKIP LOCKED` for many competing outbox publishers;
- use CDC such as Debezium and Kafka instead of polling/claiming the outbox;
- partition work by conversation ID to retain ordering;
- isolate model workers from API/socket nodes;
- use a managed Redis cluster and a durable event platform where retention must
  exceed the bounded Redis window;
- apply admission control and per-tenant quotas before provider calls.

Do not introduce Kafka, Kubernetes, or another queue merely to appear
enterprise-grade. Add each component when a measured reliability, throughput, or
operational requirement justifies it.

## Security boundaries before public deployment

The current project intentionally has no fake identity system. Before exposing it
publicly, add:

- authentication for REST and the WebSocket handshake;
- authorization/ownership checks for every conversation and run;
- tenant ID propagation and tenant-scoped uniqueness/queries;
- trusted WebSocket origins rather than `*`;
- API/provider secrets from a secret manager;
- rate limiting, quotas, abuse prevention, and prompt/content policy;
- retention/deletion policies for user messages and provider data;
- audit events for sensitive administrative actions.

Authorization must be checked during subscription as well as connection. A valid
socket must not be allowed to guess another user's conversation or run ID.

## Release checklist

- Run `mvn test`.
- Verify Flyway against a production-like database.
- Confirm Redis TLS/authentication and timeouts.
- Set trusted WebSocket origins.
- Disable SQL and model-payload logging.
- Confirm proxy WebSocket upgrade and SSE buffering settings.
- Verify reconnect from a different node.
- Test Redis/provider/node failure and recovery.
- Monitor queue age, lease expiry, and outbox age.
- Confirm the process shuts down gracefully and is not left serving during a
  failed release.
