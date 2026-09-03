# Scenario 1 — Greenfield: initial notification-management capability

## Requirement (section 4.1–4.5, 4.9 of the assignment)

Build submission, recipient/channel selection, asynchronous processing, delivery attempts,
status retrieval, deduplication/idempotency, retry/failure handling, and audit history.

## Decomposition

The requirement was broken into six independently testable units, in dependency order:

1. **Domain model** — what does a Notification, a delivery unit, and an audit event actually
   need to hold? (`domain.model`, `domain.enums`)
2. **Submission + request-level idempotency** — accept a request, decide new-vs-duplicate,
   persist, respond fast (`NotificationSubmissionService`)
3. **Routing** — turn (notification, recipients, requested channels) into concrete
   (recipient, channel) delivery units (`RoutingService`)
4. **Async queueing** — decouple "accepted" from "routed and queued" so the submit API never
   blocks on routing work (`NotificationSubmittedEventListener` → `DeliveryOrchestrationService`)
5. **Delivery + retry** — pick up queued units, call a provider, classify failures, back off
   (`DeliveryWorker` → `DeliveryDispatcher`, `RetryPolicy`, `infrastructure.provider.*`)
6. **Status + audit** — answer "what happened to this notification" from the same data the
   worker produces (`NotificationStatusService`, `AuditService`)

## Key design decisions and why

| Decision | Rationale |
|---|---|
| Notification's UUID **is** the external "notification identifier" | Avoids a second surrogate id with no independent meaning; simpler API. |
| Asynchronous processing via a Spring `ApplicationEvent` published **after commit**, not before | If routing were triggered before the submission transaction commits, a listener could see a notification that then fails to persist (e.g. constraint violation) — `AFTER_COMMIT` guarantees the row exists when routing starts. |
| Routing policy: opt-out model, CRITICAL overrides opt-out and fans out to every channel | Section 4.3 leaves the exact algorithm open ("factors such as..."); an opt-out (not opt-in) default means a brand-new channel reaches users without requiring every recipient to explicitly enable it, and a documented safety exception for CRITICAL prevents a stale preference from silently swallowing a safety-critical alert. This is a judgment call, not the only defensible one — documented here so it can be challenged. |
| Retry: classify failures into 6 categories, only 3 retryable | Matches section 4.5's required distinctions exactly (transient/timeout/rate-limit are retryable; permanent rejection/invalid recipient/auth error are not) and keeps `ABANDONED` attempts surfaced for manual remediation rather than retried forever. |
| Delivery-level dedup via a DB unique constraint, not just application logic | A unique constraint on `(notification_id, recipient_id, channel)` is enforced even if two application instances race, which pure in-memory/application-level checks cannot guarantee. |
| DB-backed polling worker instead of a message broker | See `docs/architecture-overview.md` §6 — explicit trade-off for prototype scope vs. production. |

## Execution

Implemented in dependency order (enums → entities → repositories → application services →
providers → worker → API → tests), each layer compiled and unit-tested before the next was
built on top of it. Full commit: see git history, "Greenfield: notification submission,
routing, async delivery, retry, status API".

A genuine bug was found and fixed during this phase, not left to be discovered later — see
`docs/testing-strategy.md`, "Findings from validation": a `@Transactional` method invoked via
`this.method()` from inside the same Spring bean silently runs with **no transaction at all**,
because Spring's proxy-based AOP only intercepts calls that arrive from *outside* the bean. It
surfaced as `TransactionRequiredException` from the pessimistic-lock delivery query in
`NotificationApiIT`, and was fixed by splitting the scheduled trigger (`DeliveryWorker`) from
its transactional collaborator (`DeliveryDispatcher`), and equivalently for the submission
event listener (`NotificationSubmittedEventListener` / `DeliveryOrchestrationService`).

## Validation

- 49 unit tests (Mockito, no Spring context) covering `RoutingService`, `RetryPolicy`,
  `AuditService`, `NotificationSubmissionService` (including the idempotency and concurrent-race
  paths), `NotificationStatusAggregator`, `DeliveryDispatcher`, and all channel-provider
  simulation rules.
- 7 end-to-end integration tests (`NotificationApiIT`, real HTTP + H2 + Flyway + the actual
  scheduled worker) covering: happy path to `DELIVERED`, idempotent duplicate submission,
  `INVALID_RECIPIENT` abandoning without retry, a transient failure recovering after retry,
  `CRITICAL` severity fanning out to every channel, request validation (400), and unknown-id
  (404).
- Manual verification via `curl` against the running app (see `EXECUTION_GUIDE.md`) and
  Swagger UI at `/swagger-ui.html`.
- `mvn verify` gate: JaCoCo enforces ≥90% line coverage on `application`/`domain` packages;
  combined (unit + integration) overall line coverage is 96.4% — see
  `docs/testing-strategy.md` for what the remaining gap is and why it's not chased further.
