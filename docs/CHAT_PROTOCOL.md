# Chat protocol reference

This document defines the contracts a web or mobile client uses. For internal
processing, see [CHAT_ARCHITECTURE.md](CHAT_ARCHITECTURE.md).

## REST response envelope

Normal JSON REST responses are wrapped by `ApiResponseFilter`:

```json
{
  "success": true,
  "message": "Request successful.",
  "data": {},
  "errors": null,
  "meta": {
    "requestId": "req_01J8A9B2"
  },
  "timestamp": "2026-09-21T11:01:00Z"
}
```

Send `X-Request-ID` to retain a client-generated correlation ID. The service
accepts 1-100 letters, numbers, `.`, `_`, or `-`; otherwise it generates one.
The same ID is returned in the response header and `meta.requestId`.

SSE and WebSocket frames are not wrapped in this REST envelope because both are
long-lived protocols with their own event envelopes.

## Chat REST endpoints

### Start a conversation and first run

```http
POST /api/v1/chat
Content-Type: application/json
```

```json
{
  "message": "Teach me dependency injection"
}
```

Returns `202 Accepted`. `replyToId` is invalid for a brand-new conversation.

### Submit another message

```http
POST /api/v1/chat/{conversationId}
Content-Type: application/json
Idempotency-Key: ui-message-019
```

```json
{
  "message": "Use a practical example",
  "replyToId": "OPTIONAL_MESSAGE_UUID",
  "mode": "queue"
}
```

`mode` values:

- `queue`: allow the active run to finish, then execute this run.
- `interrupt`: request cancellation of the active run and execute this message
  next.

`Idempotency-Key` is optional but strongly recommended for browser retries. It is
unique within the conversation and must match `[A-Za-z0-9._:-]{1,100}`.

### Get reloadable chat state

```http
GET /api/v1/chat/{conversationId}?after=0&limit=100
```

`after` is the last message sequence already held by the client. The response
contains:

```json
{
  "conversation": {},
  "messages": [],
  "runs": [],
  "nextCursor": null,
  "hasMore": false
}
```

Use `nextCursor` as the next `after` value while `hasMore` is true. This endpoint,
not Redis, is the authoritative UI reload source.

### Cancel the active run

```http
POST /api/v1/chat/{conversationId}/cancel
```

### Cancel a specific run

```http
POST /api/v1/chat/{conversationId}/runs/{runId}/cancel
```

Cancellation is idempotent from the UI's perspective. The response indicates
whether a non-terminal run accepted the cancellation.

## Conversation CRUD

Conversation metadata is deliberately separate from active chat commands:

```text
GET    /api/v1/conversations?limit=50
POST   /api/v1/conversations
GET    /api/v1/conversations/{conversationId}
PUT    /api/v1/conversations/{conversationId}
DELETE /api/v1/conversations/{conversationId}
```

Create body:

```json
{
  "title": "Spring study session"
}
```

Update body:

```json
{
  "title": "Advanced Spring study session"
}
```

Deleting a conversation first requests cancellation of its active run, deletes
the relational aggregate through database cascades, and evicts LangChain4j's
cached memory instance.

## Message and run fields

Every persisted message contains:

- `id`: stable UUID used by the UI and `replyToId`
- `sequence`: monotonic position within a conversation
- `role`: `user` or `assistant`
- `content`: full content accumulated so far
- `replyToId`: message relationship used to render replies/branches
- `runId`: the generation that owns this user/assistant pair
- `status`: `queued`, `processing`, `completed`, `interrupted`, `cancelled`, or
  `failed`
- `createdAt` and `updatedAt`

Every run contains its user and assistant message IDs, mode, status,
cancellation reason, error code, and lifecycle timestamps.

## SSE

Connect with:

```http
GET /api/v1/chat/{conversationId}/runs/{runId}/stream
Accept: text/event-stream
```

Events named `chat` contain a `ChatStreamEvent`. Events named `heartbeat` keep
idle proxies and clients aware that the connection is alive.

On reconnect, send the last Redis Stream cursor:

```http
Last-Event-ID: 1726948800123-0
```

The server subscribes to live events, replays events after that cursor, and
finally compares the stream with MySQL state to close connection races. Terminal
events close the SSE response.

## WebSocket

Connect to:

```text
ws://localhost:8080/ws/chat
```

In production use TLS:

```text
wss://api.example.com/ws/chat
```

### Client commands

Start a conversation:

```json
{
  "type": "message",
  "requestId": "ui-001",
  "message": "Explain JPA"
}
```

Queue a follow-up:

```json
{
  "type": "message",
  "requestId": "ui-002",
  "conversationId": "CONVERSATION_UUID",
  "idempotencyKey": "ui-retry-key-002",
  "mode": "queue",
  "replyToId": "OPTIONAL_MESSAGE_UUID",
  "message": "Show the repository code"
}
```

Steer the current answer:

```json
{
  "type": "message",
  "requestId": "ui-003",
  "conversationId": "CONVERSATION_UUID",
  "idempotencyKey": "ui-retry-key-003",
  "mode": "interrupt",
  "replyToId": "MESSAGE_UUID",
  "message": "Use EntityManager rather than Spring Data repositories"
}
```

Subscribe or resume:

```json
{
  "type": "subscribe",
  "requestId": "ui-004",
  "conversationId": "CONVERSATION_UUID",
  "runId": "RUN_UUID",
  "after": "1726948800123-0"
}
```

Cancel:

```json
{
  "type": "cancel",
  "requestId": "ui-005",
  "conversationId": "CONVERSATION_UUID",
  "runId": "OPTIONAL_RUN_UUID"
}
```

Other commands:

```json
{"type":"unsubscribe","requestId":"ui-006","runId":"RUN_UUID"}
```

```json
{"type":"ping","requestId":"ui-007"}
```

### Server frames

The server can emit:

- `ready`: connection established and protocol version announced
- `accepted`: durable message and run were created
- `subscribed`: run subscription is active
- `chat`: snapshot, partial update, or terminal run state
- `cancel_accepted`: cancellation command result
- `unsubscribed`
- `pong`
- `error`

Command acknowledgement example:

```json
{
  "type": "accepted",
  "requestId": "ui-003",
  "data": {
    "conversationId": "CONVERSATION_UUID",
    "run": {},
    "userMessage": {},
    "assistantMessage": {}
  },
  "error": null,
  "timestamp": "2026-09-21T11:01:00Z"
}
```

Live chat event example:

```json
{
  "type": "chat",
  "requestId": null,
  "data": {
    "eventId": "EVENT_UUID",
    "cursor": "1726948800123-0",
    "type": "update",
    "conversationId": "CONVERSATION_UUID",
    "runId": "RUN_UUID",
    "messageId": "ASSISTANT_MESSAGE_UUID",
    "content": "The complete answer accumulated so far",
    "status": "processing",
    "cancelReason": null,
    "errorCode": null,
    "occurredAt": "2026-09-21T11:01:00Z",
    "sourceId": null
  },
  "error": null,
  "timestamp": "2026-09-21T11:01:00Z"
}
```

The UI should:

1. correlate acknowledgements with `requestId`;
2. deduplicate live events by `eventId`;
3. save the newest `cursor` for reconnect;
4. replace message content with the event's full `content` value;
5. reload authoritative state over REST after a page refresh or uncertain error.

## Validation and errors

Bean Validation handles request records, route parameters, and query parameters.
Domain rules throw custom exceptions such as `ResourceNotFoundException` or
`RequestValidationException`; global exception mappers create the standard error
envelope. Resource methods therefore do not need repetitive `try/catch` blocks.

Typical HTTP statuses:

- `400`: malformed JSON or route input
- `404`: conversation, run, message, or course not found
- `409`: conflicting resource state
- `410`: resource no longer available
- `422`: field validation failure
- `502`: upstream AI provider failure
- `503`: temporarily unavailable dependency

WebSocket validation failures use an `error` frame because an already-upgraded
socket cannot return an HTTP response for each command.

## Course CRUD example

The course resource demonstrates conventional CRUD through the same response,
validation, exception, and manual `EntityManager` patterns:

```text
GET    /api/v1/courses
POST   /api/v1/courses
GET    /api/v1/courses/{id}
PUT    /api/v1/courses/{id}
DELETE /api/v1/courses/{id}
```

Create/update body:

```json
{
  "title": "Spring Persistence",
  "slug": "spring-persistence",
  "description": "EntityManager, transactions, locking, and migrations"
}
```

Titles are required and limited to 160 characters. Slugs are required, unique,
limited to 180 characters, and contain lowercase letters, numbers, and hyphens.
Descriptions are optional and limited to 10,000 characters.
