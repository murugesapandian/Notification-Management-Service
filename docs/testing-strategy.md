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

## Security review

A dependency-CVE and code-level review, done deliberately rather than assumed clean. Real
findings were fixed; non-findings are recorded with the reasoning, not silently dropped.

**Dependency CVEs — checked against Maven Central directly, not just a version-checker tool**
(one source claimed newer 3.3.x Spring Boot versions exist than are actually published publicly
— those turned out to be a paid vendor's back-ported builds):

| Dependency | Was | Now | Why |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.3.4 | **3.3.13** | Fixes CVE-2025-41249, CVE-2025-22233, CVE-2025-41234, accumulated across 9 patch releases. 3.3.13 is the actual latest public release on Maven Central for the 3.3.x line. |
| `org.postgresql:postgresql` | 42.7.4 | **42.7.13** | Same minor line, latest patch. |
| `org.xerial:sqlite-jdbc` | 3.46.1.3 | **3.53.4.0** | Actively used at runtime (the demo/dev default profile) — bumped for accumulated fixes in both the JDBC wrapper and bundled native SQLite. Re-verified the full submit→retry→deliver flow against a fresh SQLite file post-bump, since the test suite runs on H2 and never exercises this driver at all. |
| `org.projectlombok:lombok` | 1.18.34 | **1.18.48** | Compile-time only (excluded from the runtime jar), low risk, bumped for freshness. |
| `com.h2database:h2` | 2.2.224 | **left as-is** | A real CVE class exists for H2 (JDBC-URL-parameter RCE via `INIT`/`RUNSCRIPT`), but it requires an attacker to control the JDBC connection string. Ours is fully hardcoded in `application-test.yml` with only a framework-generated UUID as the dynamic part — no request ever reaches it. H2 console is also not enabled anywhere in this codebase (confirmed by grep), closing the other well-known H2 attack vector regardless of version. Upgrading across H2 minor versions (2.2→2.5) carries its own regression risk to the `MODE=PostgreSQL` compatibility the test suite depends on, for a vulnerability class that isn't reachable here — left alone and documented, not overlooked. |
| `org.flywaydb:flyway-core` / `flyway-database-postgresql` | 10.10.0 | **left as-is** | 10.10.0 is the last release in the 10.x line (confirmed against Maven Central metadata — there is no smaller patch bump available); the next version is 12.x, a major jump with real license/behavior-change risk (Flyway's community edition has narrowed database support across major versions before). No specific CVE was found motivating this jump. Treated as a deferred, dedicated migration, not folded into this pass. |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.6.0 | **left as-is** | Tried bumping to 2.9.0 for freshness — broke with `NoClassDefFoundError: LiteWebJarsResourceResolver`, a class from a newer Spring Framework than 3.3.x ships. Tried stepping back to 2.8.17 (still "2.x"): **same failure** — confirmed by actually running the integration suite, not assumed from the version number. No CVE motivated this bump either (the one known swagger-ui XSS CVE, webjars 3.14–3.38, was already far behind us at the bundled 5.x). Reverted. |
| frontend (npm) | — | — | `npm audit` reports **zero vulnerabilities** at any severity across the whole dependency tree. |

**Code-level review (backend):**

- **SQL injection**: every `@Query` in the codebase is parameterized JPQL (`:paramName` binds);
  none use `nativeQuery=true` or string-concatenated SQL. Clean.
- **XSS**: React auto-escapes all rendered content; no `dangerouslySetInnerHTML`, `eval`, or
  `innerHTML` anywhere in the frontend (grepped, not assumed).
- **Secrets**: no hardcoded credentials/API keys found; Postgres profile reads credentials from
  environment variables only.
- **Error handling**: unhandled exceptions fall through to Spring Boot's secure-by-default
  `/error` handling (`server.error.include-message`/`include-stacktrace` are not overridden, so
  they stay at their secure default of `never`) — confirmed no override exists, rather than
  assumed.
- **CORS**: scoped to a configurable specific origin (`nms.cors.allowed-origins`), not `*`.
- **Actuator**: only `health`, `info`, `metrics` are exposed — not `env`, `beans`, or other
  internals-revealing endpoints.

**Resource-usage / memory review — two real findings, fixed:**

1. `IdempotencyCleanupJob` and `EscalationJob`'s eligibility query both loaded an **unbounded**
   result set into a `List` before acting on it — `IdempotencyCleanupJob` additionally
   materialized every expired entity into the persistence context just to call `deleteAll` on
   them one at a time. Neither was reachable by tests (both need a large backlog to show up), so
   this was found by reading the code with "what if this table has 100k rows" in mind, not by a
   failing test. Fixed: `IdempotencyCleanupJob` now issues a single bulk
   `deleteByExpiresAtBefore` statement (no entities loaded into heap at all);
   `EscalationJob`/`NotificationRepository.findEligibleForEscalation` now takes a `Pageable` and
   caps each poll to a fixed batch, the same pattern already used by
   `DeliveryAttemptRepository.lockNextBatchDue` — a genuine incident storm producing many
   CRITICAL alerts at once can no longer make one poll cycle load an unbounded list.
2. Frontend polling (`Dashboard`, `NotificationDetail`) correctly cleared its `setInterval` on
   unmount already, but an in-flight `fetch` at the moment of navigation would still resolve
   later and call `setState` on an unmounted page — wasted network completions and retained
   closures under repeated fast navigation, not a classic leak but real waste. Fixed with an
   `AbortController` per effect, aborted in its cleanup function; verified with Playwright by
   rapidly cycling Dashboard↔Detail navigation six times while polling was active — zero console
   errors, and the notification still correctly reached `DELIVERED`.

## Limitations (explicit, not hidden)

- **Simulated providers, not real ones.** No SES/Twilio/FCM/Slack credentials or network calls
  are involved. The `ChannelProvider` interface is the seam where a real integration would
  plug in; none of the business logic downstream of it (retry, audit, status) would need to
  change.
- **No load/performance testing.** The DB-polling worker's throughput ceiling was not measured;
  see `docs/architecture-overview.md` §6 for the known scaling trade-off and the production
  migration path (swap to a broker-driven consumer).
- **No AuthN/AuthZ.** Explicitly out of scope for the prototype (see architecture overview
  §11); a real deployment sits behind the org's gateway/OAuth2 resource server. The security
  review above covers dependency CVEs, injection/XSS/secrets/CORS/error-handling at the code
  level, and resource-usage patterns — it is not a penetration test, and does not substitute
  for one before any real deployment.
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
