# Scenario 3 — Ambiguous requirement: "don't let a critical alert get missed"

## The requirement, as given

The assignment's scope explicitly calls for "well-defined and ambiguous requirements" as a
scenario, without specifying either — this scenario invents a realistic ambiguous ask, in the
voice of a stakeholder rather than a spec:

> "For critical alerts, if nobody acknowledges it in time, make sure it gets escalated so it
> doesn't get missed."

This sentence is a single, reasonable-sounding requirement, and it leaves at least four
questions genuinely open. The point of this scenario is showing the resolution process, not
just the resulting code.

## Ambiguities identified, options considered, decision made

### 1. What does "acknowledge" mean?

- *Option A — infer it from delivery success.* Rejected: a message reaching an inbox/queue is
  not evidence a human saw it or is acting on it. This would make "acknowledged" true for
  alerts nobody looked at, defeating the requirement's actual intent.
- *Option B — infer it from a read receipt per channel.* Rejected for this iteration: most
  channels used here (SMS, generic email) don't reliably provide read receipts, and building
  per-provider receipt ingestion is a much larger scope than this ask warrants.
- **Decision (Option C): an explicit action.** `POST /api/v1/notifications/{id}/acknowledge`
  with an `acknowledgedBy` actor. First acknowledgement wins (`AcknowledgementService`); a
  second acknowledger doesn't overwrite who acknowledged first. Simple, unambiguous, and
  matches how on-call/paging tools (PagerDuty, Opsgenie) actually model this.

### 2. How long is "in time"?

- No duration was given. An arbitrary hardcoded value would be indefensible.
- **Decision:** externalized as `nms.escalation.unacknowledged-critical-threshold-minutes`
  (default 15 minutes in production, `0` in the test profile so integration tests observe the
  behavior in seconds instead of real minutes — see `application-test.yml`). Scoped to
  `CRITICAL` severity only for this iteration; extending it to `HIGH` is a one-line config
  change but was deliberately not defaulted on, since escalating non-critical alerts changes the
  on-call load calculus and should be an explicit choice, not a side effect of this feature.

### 3. Escalate to whom, and how?

- *Option A — a bespoke "send a page" integration.* Rejected: duplicates the entire delivery
  pipeline (retry, audit, provider abstraction) for one feature.
- **Decision (Option B): reuse the existing pipeline.** Escalation creates one more
  `DeliveryAttempt` — for a configured recipient/channel
  (`nms.escalation.escalation-recipient-id` / `escalation-channel`) — that flows through the
  same `DeliveryWorker`/`DeliveryDispatcher`/retry/audit machinery as any other delivery. An
  escalated message gets the same reliability guarantees as a normal one, for free.

### 4. Does escalation repeat if still unacknowledged?

- **Decision: no — fires exactly once per notification**, guarded by `escalatedAt`. Repeated
  paging without an operator action is a common source of alert fatigue; if the assignment's
  reviewer wanted continuous re-paging / on-call rotation integration, that is flagged here as
  explicit future scope, not silently omitted.

### 5. Does an escalated notification's status still reflect delivery outcome?

- `NotificationStatus.ESCALATED` is terminal-for-aggregation: once set, `NotificationStatusAggregator`
  stops recomputing `DELIVERED`/`PARTIALLY_DELIVERED`/`FAILED` for that notification (see its
  javadoc), so "this needed human escalation" stays visible in the status API instead of being
  overwritten the moment the escalation's own delivery attempt succeeds.

## Implementation

- `AcknowledgementService` + `NotificationController.acknowledge` — the explicit-action API.
- `EscalationProperties` (`@ConfigurationProperties(prefix = "nms.escalation")`) — the
  externalized threshold/recipient/channel.
- `NotificationRepository.findEligibleForEscalation` — CRITICAL, never acknowledged, never
  already escalated, created before the cutoff, and not in a terminal side-state
  (`EXPIRED`/`REJECTED`/`DUPLICATE_SUPPRESSED`) where escalation would be meaningless.
- `EscalationJob` (thin `@Scheduled` trigger) + `EscalationExecutor` (per-notification
  `@Transactional` collaborator) — split for the same self-invocation-proxy reason as
  `DeliveryWorker`/`DeliveryDispatcher`, **and** so one notification losing a concurrency
  conflict doesn't roll back every other notification's escalation in the same poll cycle.
- `db/migration/V3__escalation_eligibility_index.sql` — a composite index supporting the
  eligibility query, which runs on every poll.

## A real concurrency bug this scenario surfaced

Acknowledging a notification immediately after submitting it — a realistic sequence for a fast
on-call operator — occasionally hit `ObjectOptimisticLockingFailureException`: the async routing
orchestrator (`NotificationSubmittedEventListener`) and the acknowledge/escalate write paths can
all touch the same `Notification` row within milliseconds of submission, and JPA's `@Version`
optimistic lock correctly detects the conflict rather than silently losing an update. This is a
genuine consequence of one mutable aggregate row being written by multiple independent
background processes — not a mistake to hide, a characteristic to handle. Fixed with:

- Bounded retry-with-backoff at the acknowledge API boundary (`NotificationController`) and in
  the submission event listener, both documented at the point of the fix with *why*.
- Per-notification transactions in `EscalationExecutor` so a conflict on one notification is
  isolated and self-heals on the job's next poll, rather than aborting the whole batch.
- A `409 Conflict` mapping in `GlobalExceptionHandler` for the case retries are exhausted,
  instead of leaking a `500`.

See `docs/testing-strategy.md`, "Findings from validation," for how this was caught, and
`docs/architecture-overview.md` §9 for the broader dedup-boundary context.

## Validation

- Unit tests: `AcknowledgementServiceTest` (acknowledges, first-writer-wins, 404 for unknown
  id), `EscalationExecutorTest` (escalates, re-checks eligibility against a freshly loaded row,
  tolerates an already-existing delivery unit), `EscalationJobTest` (delegates per notification;
  one conflict doesn't stop the rest of the batch), `NotificationSubmittedEventListenerTest`
  (retry happy path / retry-then-succeed / retries-exhausted-without-throwing), and a new
  `NotificationStatusAggregatorTest` case proving `ESCALATED` is not overwritten.
- Integration tests: `unacknowledgedCriticalNotificationGetsEscalated` (submits, waits for
  escalation, asserts the escalation delivery unit exists) and
  `acknowledgingACriticalNotificationPreventsEscalation` (acknowledges immediately, waits
  through several poll cycles, asserts it was **not** escalated) — this second test is the one
  that directly exercises `findEligibleForEscalation`'s `acknowledged_at IS NULL` filter, not
  just the escalation path in isolation.
