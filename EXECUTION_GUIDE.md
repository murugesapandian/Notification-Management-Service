# Execution Guide — Notification Management Service

This is a two-project system: a Spring Boot backend (this repository) and a React UI in a
**separate** sibling repository, `../notification-management-ui`. This guide walks through
running both end-to-end, plus the automated test suite.

## Tech stack

| Layer | Technology |
|---|---|
| Backend language/runtime | Java 17 |
| Backend framework | Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator) |
| Database (demo/dev default) | SQLite (file-based, `backend/data/nms.db`) |
| Database (test profile) | H2 (in-memory, `MODE=PostgreSQL`) |
| Database (prod profile) | PostgreSQL |
| Schema migrations | Flyway |
| Boilerplate reduction | Lombok |
| API documentation | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Backend testing | JUnit 5, Mockito, AssertJ, Awaitility, Spring Boot Test, JaCoCo (coverage gate), Maven Failsafe (integration tests) |
| Frontend | React 19, TypeScript, Vite 8, React Router 7 |
| Frontend package manager | npm |

## Prerequisites

- **Java 17** — must be the actual JDK used to run Maven. On this machine, multiple JDKs were
  installed and the shell's default `JAVA_HOME` pointed at a newer JDK (25) that Lombok's
  annotation processor did not run cleanly under; every Maven command below pins `JAVA_HOME`
  explicitly to a known-good Java 17 install. Adjust the path for your machine (`java -version`
  to check what you have; on macOS, `/usr/libexec/java_home -V` lists installed JDKs).
- **Maven 3.9+** (`mvn -version`)
- **Node.js 20+** and **npm** (`node -v`, `npm -v`) — for the frontend
- **Nothing else required for local/dev**: SQLite is embedded (file-based, no server process),
  so there's nothing extra to install or start.
- **PostgreSQL 14+** — only needed if you run the backend with the `postgres` Spring profile
  instead of the default SQLite setup.

## 1. Run the backend

```bash
cd backend

# Point JAVA_HOME at a Java 17 install (adjust path for your machine):
export JAVA_HOME=/path/to/your/jdk-17

mvn spring-boot:run
```

The service starts on **http://localhost:8080**, applies Flyway migrations to
`backend/data/nms.db` (created automatically on first run) via SQLite, and starts the scheduled
delivery/escalation/cleanup workers. Unlike an in-memory database, this file **persists across
restarts** — stop and re-run `mvn spring-boot:run` and your previously submitted notifications
are still there (see `docs/architecture-overview.md` §2a for why SQLite was chosen for this
profile, and what's traded off to get it).

Useful endpoints once running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs
- Health check: http://localhost:8080/actuator/health
- Inspect the database directly: `sqlite3 backend/data/nms.db` (CLI), or open the file in any
  SQLite GUI browser (e.g. "DB Browser for SQLite") — the app does not need to be running to do
  this, though writes will conflict if you write to it while the app is also running.

### Smoke-test it with curl

```bash
# Submit a notification (severity CRITICAL fans out to every channel automatically)
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "trading-platform",
    "notificationType": "TRADE_ALERT",
    "severity": "HIGH",
    "priority": "HIGH",
    "recipients": [{"recipientId": "flaky-user1", "recipientType": "USER_ID"}],
    "requestedChannels": ["EMAIL"]
  }'
# => {"notificationId":"...","status":"VALIDATED","duplicate":false,"createdAt":"..."}

# Poll its status (recipientId "flaky-user1" fails once, then succeeds on retry — ~30s apart in
# the default profile; see "Demo recipient-id prefixes" below to see it resolve faster)
curl -s http://localhost:8080/api/v1/notifications/<notificationId> | python3 -m json.tool

# List recent notifications
curl -s "http://localhost:8080/api/v1/notifications?page=0&size=20" | python3 -m json.tool

# Acknowledge a notification (relevant for CRITICAL escalation — see docs/scenarios/03-*.md)
curl -s -X POST http://localhost:8080/api/v1/notifications/<notificationId>/acknowledge \
  -H "Content-Type: application/json" \
  -d '{"acknowledgedBy": "oncall.jane"}'
```

### Demo recipient-id prefixes (no real email/SMS/Slack provider needed)

The channel providers are deterministic simulations keyed off the `recipientId` you submit —
useful for demoing retry/failure behavior on demand:

| Prefix | Behavior |
|---|---|
| `flaky-*` | Fails once (transient), then succeeds on the next retry |
| `timeout-*` | Times out every attempt (retries, eventually abandons) |
| `ratelimit-*` | Rate-limited every attempt (retries honoring a provider `Retry-After`) |
| `invalid-*` | Invalid recipient — abandons immediately, no retry |
| `authfail-*` | Auth error — abandons immediately, no retry |
| `reject-*` | Permanently rejected — abandons immediately |
| anything else | Succeeds on the first attempt |

### Run with PostgreSQL instead of SQLite

```bash
createdb nms   # or your usual Postgres provisioning
export NMS_DB_URL=jdbc:postgresql://localhost:5432/nms
export NMS_DB_USERNAME=nms_app
export NMS_DB_PASSWORD=your_password
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Start from a clean database

Delete the SQLite file and restart — Flyway recreates the schema from scratch:

```bash
rm -f backend/data/nms.db backend/data/nms.db-*
mvn spring-boot:run
```

## 2. Run the tests

```bash
cd backend
export JAVA_HOME=/path/to/your/jdk-17
mvn verify
```

This runs, in order: unit tests (Surefire) → packages the JAR → integration tests (Failsafe,
real HTTP + H2 + the actual scheduled workers) → JaCoCo coverage report + gate (fails the build
if `application`/`domain` package line coverage drops below 90%). See
`docs/testing-strategy.md` for what's covered, the two real bugs this suite caught, and
explicit limitations.

To run only unit tests: `mvn test`. Coverage report (HTML) after any run:
`open target/site/jacoco/index.html`.

## 3. Run the frontend

In a **second terminal**, from the sibling `notification-management-ui` repository (make sure
the backend from step 1 is already running):

```bash
cd ../notification-management-ui
npm install
cp .env.example .env   # defaults to http://localhost:8080
npm run dev
```

Open **http://localhost:5173**. You should see the dashboard; use "Submit notification" to
create one (try a `flaky-` recipient id to watch a retry happen live), then click into its
detail page to watch delivery status update in real time.

## 4. Repository layout

```
Notification-Management-Service/    (this repo — backend + docs)
├── backend/                        Spring Boot service (see its own layout below)
├── docs/
│   ├── architecture-overview.md    Component/ER/state/sequence diagrams, key trade-offs
│   ├── testing-strategy.md         Approach, coverage numbers, limitations, bugs found
│   ├── scenarios/
│   │   ├── 01-greenfield.md
│   │   ├── 02-brownfield.md
│   │   └── 03-ambiguous-requirements.md
│   └── Notification-Management-Service-Overview.pptx
└── EXECUTION_GUIDE.md               (this file)

notification-management-ui/         (sibling repo — separate git history, see its README.md)
```

Backend package layout (`backend/src/main/java/com/schwab/nms/`):

```
api/             REST controllers, request/response DTOs, global exception handling
application/     Use cases: submission, routing, retry policy, orchestration, status
                 aggregation, acknowledgement, audit
domain/          JPA entities and enums — the persistent model
infrastructure/
  persistence/   Spring Data repositories
  provider/      Channel provider Strategy pattern (Email/SMS/Push/Slack) + registry
  worker/        Scheduled delivery/escalation/cleanup jobs
config/          Spring configuration (async executor, CORS, OpenAPI, escalation properties)
```

## 5. Troubleshooting

- **`mvn spring-boot:run` fails with Lombok "cannot find symbol getX()" errors** — you're
  running under a JDK Lombok's annotation processor doesn't fully support yet. Point
  `JAVA_HOME` at a Java 17 install as shown above.
- **Frontend shows a network error / CORS error in the console** — confirm the backend is
  running on the port your `.env`'s `VITE_API_BASE_URL` points at, and that you're accessing
  the frontend at `http://localhost:5173` (that's the origin `WebConfig` allows by default;
  override via `nms.cors.allowed-origins` if you serve the frontend elsewhere).
- **Port already in use** — `lsof -ti:8080 -sTCP:LISTEN | xargs kill` (backend) or
  `lsof -ti:5173 -sTCP:LISTEN | xargs kill` (frontend) before restarting.
- **`SQLITE_BUSY` / "database is locked"** — only one process can write to `backend/data/nms.db`
  at a time (see architecture doc §2a). Don't run a second `mvn spring-boot:run` against the
  same file, and close any SQLite GUI browser's write connection before restarting the app.
