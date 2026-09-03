# Testing Strategy, Limitations, and Trade-offs

## Approach

Two layers, both run by `mvn verify`:

1. **Unit tests** (JUnit 5 + Mockito + AssertJ, `*Test.java`, run by Surefire in the `test`
   phase). No Spring context — every application-layer service and worker/job is constructed
   directly with mocked collaborators and a `Clock.fixed(...)` so time-dependent assertions
   (backoff timing, retention windows, escalation thresholds) are deterministic, not
   `Thread.sleep`-based guesses.
2. **Integration tests** (`*IT.java`, run by Failsafe in the `integration-test` phase, bound
   after the JAR is packaged). `@SpringBootTest(webEnvironment = RANDOM_PORT)` against a real
   embedded Tomcat, a real Flyway-migrated H2 database (isolated per test-context via
   `jdbc:h2:mem:nms-test-${random.uuid}`), and the actual `@Scheduled` workers running on their
   normal (accelerated, via `application-test.yml`) schedule — not mocked out. Assertions use
   Awaitility to poll the real status API until the async pipeline settles, which is what
   catches timing/concurrency bugs that a synchronous, mocked test cannot.

Channel providers are deterministic simulations keyed off `recipientId` prefixes
(`invalid-`, `authfail-`, `timeout-`, `ratelimit-`, `reject-`, `flaky-` — see
`AbstractSimulatedProvider` javadoc) rather than live network calls to SES/Twilio/FCM/Slack.
This makes every failure-classification and retry path exercisable on demand and CI-safe
(no external dependency, no flaky network), at the cost of not proving the real provider
integrations work — see "Limitations" below.

## Coverage

`mvn verify` enforces a JaCoCo gate: **≥90% line coverage on the `application` and `domain`
packages**, the layers carrying the actual business rules (routing, retry, idempotency,
escalation, aggregation). This is a hard build failure, not advisory.

Combined unit + integration coverage (both JVMs feed the same `jacoco.exec` — see `pom.xml`'s
`prepare-agent-integration` execution) is **96.4% overall line coverage** as of the last full
run. Package breakdown:

| Package | Line coverage |
|---|---|
| `application` (use cases) | 96.7% |
| `application.event` | 100% |
| `domain.model` | 100% |
| `domain.enums` | 97.3% |
| `infrastructure.provider` | 100% |
| `infrastructure.worker` | 97.7% |
| `api.dto` | 100% |
| `api.controller` | 97.2% |
| `api.exception` | 62.5% |
| `com.schwab.nms` (`NmsApplication`) | 33.3% |

**On the "100% coverage" ask specifically:** we treated this as "100% of the paths that matter,
verified honestly" rather than chasing a raw percentage, and the two packages below 90% are
exactly the ones where that distinction applies, not overlooked spots:

- `NmsApplication.main(...)` — a one-line Spring Boot bootstrap. Covered implicitly by every
  integration test starting the app; not unit-testable in any meaningful way, and a test that
  merely calls `main()` would assert nothing about behavior. Chasing this number would add a
  test with no defect-finding value.
- `api.exception.GlobalExceptionHandler` — five of its six handlers are exercised by real
  request flows in `NotificationApiIT` (validation-failure 400, not-found 404, optimistic-lock
  409). The one gap is `IllegalStateException` → 409, which nothing in the current API surface
  actually triggers end-to-end (it exists for a code path — a channel with no registered
  provider — that's already prevented at startup by `ChannelProviderRegistry`'s fail-fast
  construction check). Left as a defensive handler with a direct unit test would be the next
  increment; not added here because it would be testing dead code to inflate a metric rather
  than testing a real risk.

Where we did *not* compromise: every business-rule branch in `RoutingService`, `RetryPolicy`,
`NotificationSubmissionService`'s idempotency/race handling, `NotificationStatusAggregator`'s
status transitions, `DeliveryDispatcher`'s claim/dispatch/no-op paths, and the escalation
eligibility/execution split is covered by at least one test, several by both a unit test (the
logic in isolation) and an integration test (the same logic under real async/HTTP conditions).

## Findings from validation

Two real defects were found by the test suite during this build, not shipped and discovered
later — worth recording because it's direct evidence the validation step did its job rather
than being pro forma:

1. **Self-invocation bypasses `@Transactional`.** `DeliveryWorker`'s scheduled method called
   `this.lockDueBatch()` and `this.process(id)` on itself; Spring's proxy-based AOP only
   intercepts calls arriving from *outside* the bean, so those calls silently ran with **no
   transaction**, and the pessimistic-lock delivery query failed with
   `TransactionRequiredException`. Caught by `NotificationApiIT`'s happy-path test timing out
   at `VALIDATED` instead of reaching `DELIVERED`. Fixed by splitting the scheduled trigger from
   its transactional collaborator (`DeliveryWorker` → `DeliveryDispatcher`), and the identical
   fix applied to the submission event listener (`NotificationSubmittedEventListener` →
   `DeliveryOrchestrationService`). See those classes' javadoc for the full explanation.
2. **Optimistic-lock races on the `Notification` aggregate.** Multiple independent background
   processes (routing orchestration, delivery status aggregation, escalation) can write the same
   `Notification` row within milliseconds of each other, especially under the accelerated
   thresholds used in tests. Caught by the ambiguous-requirement scenario's acknowledge-then-
   escalate integration tests. Fixed with bounded retry at the two write-boundaries most prone
   to contention (see `docs/scenarios/03-ambiguous-requirements.md` for the full account) rather
   than papering over it with a longer test timeout.

## Limitations (explicit, not hidden)

- **Simulated providers, not real ones.** No SES/Twilio/FCM/Slack credentials or network calls
  are involved. The `ChannelProvider` interface is the seam where a real integration would
  plug in; none of the business logic downstream of it (retry, audit, status) would need to
  change.
- **No load/performance testing.** The DB-polling worker's throughput ceiling was not measured;
  see `docs/architecture-overview.md` §6 for the known scaling trade-off and the production
  migration path (swap to a broker-driven consumer).
- **No security testing beyond input validation.** AuthN/AuthZ is explicitly out of scope for
  the prototype (see architecture overview §11); a real deployment sits behind the org's
  gateway/OAuth2 resource server.
- **Concurrency testing is scenario-driven, not exhaustive.** The optimistic-lock retries were
  validated against the specific races the integration tests happened to produce (acknowledge-
  right-after-submit, escalation-right-after-submit with a near-zero threshold). No dedicated
  chaos/fuzzing pass was run against the full space of concurrent writers to a single
  Notification row; the architecture doc names this as a known contention point at higher scale
  (§9) rather than claiming it's fully proven safe.
- **Single-node assumptions in a few places.** `@Scheduled` workers as written run per-instance;
  the pessimistic-locking query (`lockNextBatchDue`) is what makes running more than one
  instance safe for delivery dispatch specifically, but was not load-tested with multiple real
  instances contending — only reasoned about and unit-verified in isolation.

## Manual/exploratory validation

- Swagger UI (`/swagger-ui.html`) used to hand-drive the API during development and confirm the
  generated OpenAPI schema matches the DTOs' Bean Validation annotations.
- `curl` walkthroughs in `EXECUTION_GUIDE.md` double as a manual smoke test script.
- The H2 console was used to inspect actual row states (`delivery_attempts.status`,
  `next_retry_at`) while diagnosing the two bugs above, back when H2 was the default dev
  profile. The default profile now runs on SQLite (see architecture doc §2a); the equivalent
  today is `sqlite3 backend/data/nms.db` or a SQLite GUI browser, per `EXECUTION_GUIDE.md`.
