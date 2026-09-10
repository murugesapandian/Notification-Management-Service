# Notification Management Service

A prototype notification-management platform: receives alert requests from source systems and
delivers them to recipients through configurable channels (email, SMS, push, Slack), with
asynchronous processing, bounded retry, deduplication, status retrieval, and an audit trail —
built as three progressively harder AI-assisted engineering scenarios: greenfield, brownfield,
and an ambiguous requirement.

## Start here

| I want to... | Go to |
|---|---|
| Run it | [`EXECUTION_GUIDE.md`](EXECUTION_GUIDE.md) |
| Understand the architecture | [`docs/architecture-overview.md`](docs/architecture-overview.md) (or the formatted [PDF](docs/NMS-Architecture-Overview.pdf)) |
| See how each scenario was decomposed/executed/validated | [`docs/scenarios/`](docs/scenarios/) |
| Understand testing approach, coverage, and limitations | [`docs/testing-strategy.md`](docs/testing-strategy.md) |
| See exactly where/why AI was used, and where it could extend the system next | [`docs/ai-usage.md`](docs/ai-usage.md) |
| See the UI | [`../notification-management-ui`](../notification-management-ui) (separate repo) |
| See the slide overview | [`docs/Notification-Management-Service-Overview.pptx`](docs/Notification-Management-Service-Overview.pptx) |

## The three scenarios, in one paragraph each

**[Greenfield](docs/scenarios/01-greenfield.md)** — the initial capability: submission,
recipient/channel routing, asynchronous processing, delivery attempts with bounded
exponential-backoff retry, request-level idempotency, status retrieval, and an audit trail.

**[Brownfield](docs/scenarios/02-brownfield.md)** — treats the greenfield build as an existing
system and lands a Slack channel, a Strategy/Registry refactor of provider dispatch (so adding
that channel required zero changes to the dispatch method itself), and active enforcement of the
idempotency retention policy that greenfield had only documented.

**[Ambiguous requirement](docs/scenarios/03-ambiguous-requirements.md)** — "make sure a critical
alert doesn't get missed if nobody acknowledges it," a realistic single-sentence stakeholder ask
that leaves at least four real decisions open (what counts as acknowledged, how long is "in
time," escalate to whom/how, does it repeat). Each is resolved and documented, not guessed
silently. Building it also surfaced a genuine concurrency bug (multiple background processes
racing on the same database row), which was fixed and is documented as a finding, not hidden.

## Technology stack

**Backend**

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) |
| Database (demo/dev) | SQLite, file-based (`backend/data/nms.db`) |
| Database (test) | H2, in-memory (`MODE=PostgreSQL`) |
| Database (prod) | PostgreSQL |
| ORM | Hibernate 6, via Spring Data JPA |
| Schema migrations | Flyway |
| Boilerplate reduction | Lombok |
| API documentation | springdoc-openapi (Swagger UI) |
| Build tool | Maven |
| Testing | JUnit 5, Mockito, AssertJ, Awaitility, Spring Boot Test, Maven Failsafe (integration tests), JaCoCo (coverage gate) |

**Frontend** ([`notification-management-ui`](../notification-management-ui), separate repo)

| Layer | Technology |
|---|---|
| Library | React 19 |
| Language | TypeScript |
| Build tool | Vite 8 |
| Routing | React Router 7 |
| Package manager | npm |
| Linting | oxlint |

**Architecture patterns**

- Layered backend: `api` → `application` → `domain` → `infrastructure` (persistence, provider, worker)
- Strategy/Registry pattern for channel providers (Email/SMS/Push/Slack) — see the
  [brownfield scenario](docs/scenarios/02-brownfield.md)
- Scheduled workers for delivery retry, escalation, and idempotency cleanup
- Hibernate `@JdbcTypeCode`/`AttributeConverter` mappings so UUID and timestamp columns are
  stored as human-readable text rather than SQLite's default binary/epoch-millis representation

See [`EXECUTION_GUIDE.md`](EXECUTION_GUIDE.md) for exact versions, prerequisites, and how to run
each profile.

## Repository layout

This repo holds the backend service and documentation. The UI is intentionally a **separate
git repository** (`../notification-management-ui`), per the assignment's requirement — see its
own README for details.

```
backend/    Spring Boot service — see EXECUTION_GUIDE.md to run it
docs/       Architecture, scenario write-ups, testing strategy, slide deck
```

## Quick run

```bash
cd backend
export JAVA_HOME=/path/to/your/jdk-17   # see EXECUTION_GUIDE.md
mvn spring-boot:run
```

Then, in a second terminal, from the sibling UI repo: `npm install && npm run dev`. Full
details, curl examples, and troubleshooting are in [`EXECUTION_GUIDE.md`](EXECUTION_GUIDE.md).
