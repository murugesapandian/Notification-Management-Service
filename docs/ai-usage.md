# AI Usage — Where, Why, and Where It Could Go Next

This assignment's stated principle: *"AI assists the engineer within tasks; the engineer owns
execution and quality."* This document is the accounting for that — concretely, not generically:
where AI was used building this system, why that was the right tool for each job, and (in
§2) specific places the **running system itself** could adopt agentic AI as a real feature,
not just as a build-time accelerant.

## 1. Where AI was used building this system

### 1.1 Requirement decomposition (all three scenarios)

Turning section 4's prose requirements into a concrete component breakdown *before* writing
code — greenfield's six-unit dependency-ordered build plan, brownfield's identification of the
`DeliveryWorker` if/else dispatch as a real Open/Closed violation (not a strawman built to be
knocked down), and the ambiguous scenario's invention of a realistic one-sentence stakeholder
ask ("make sure a critical alert doesn't get missed") with its four hidden decisions enumerated
and resolved on the record.

**Why:** this is the part of the assignment actually graded — "depth of decomposition," "clarity
and defensibility of decisions." AI is fast at systematically surfacing what a vague requirement
leaves open; every resulting policy choice (opt-out routing, CRITICAL severity override, 15-min
escalation threshold, "acknowledge = explicit action, never inferred") is written with its
rationale in `docs/scenarios/`, specifically so it's reviewable and arguable, not asserted.

### 1.2 Code generation — backend and frontend

All Java (entities, services, controllers, Flyway migrations, 76 tests) and all
React/TypeScript/CSS.

**Why:** mechanical translation of an already-decided design into working code is where AI saves
the most real time. The design decisions in §1.1 are what actually mattered; this is
boilerplate-adjacent execution of them.

### 1.3 Finding and fixing real bugs — not just writing code

Two documented in `docs/testing-strategy.md`, "Findings from validation":

- A Spring `@Transactional` self-invocation bug (`this.method()` silently runs with no
  transaction at all) — caught because an integration test's status assertion timed out, not
  because it was reasoned out in advance.
- An optimistic-locking race between concurrent background writers on the `Notification`
  aggregate — caught the same way, by actually running the async pipeline under test timing.

**Why:** this is the highest-value use of AI in the whole build: write the test first, run it for
real, and treat a failure as a signal to fix the *design* (splitting trigger/collaborator beans),
never to weaken the test. That's the difference between "AI wrote code" and "AI validated
behavior."

### 1.4 UI visual debugging

When alignment "wasn't looking good," the fix came from actually screenshotting the running app
at four breakpoints with Playwright, not re-reading CSS. That's what found:

- A `flex-basis` that silently became a *height* once the sidebar collapsed to a mobile top bar
- The identical bug in the recipient-input row
- A table that lost `width: 100%` and bunched left with dead space on wide screens

**Why:** these are exactly the class of bug that isn't visible from source — only from render.
Driving a real headless browser and looking at pixels (and, for the horizontal-scroll question,
querying `scrollWidth`/`clientWidth` directly rather than eyeballing a screenshot) is what caught
them.

### 1.5 Documentation and the slide deck

`docs/architecture-overview.md` (with Mermaid diagrams), the three scenario write-ups,
`docs/testing-strategy.md`, `EXECUTION_GUIDE.md`, and the PPTX (generated from
`scripts/build_pptx.py`, so it's regenerable from source, not a hand-edited binary).

**Why:** compressing a normally multi-day documentation effort, while keeping every claim
traceable to something real — actual test counts, actual coverage numbers pulled from
`target/site/jacoco/jacoco.csv`, actual git history — rather than generic filler.

### 1.6 Where the engineer (not AI) owned the decision

- The SQLite-over-H2 switch was requested and scoped by the engineer; AI implemented and
  validated it (including checking the pessimistic-locking implications didn't silently break
  section 4.4's dedup guarantee).
- Repo visibility (public) and naming were explicit engineer answers to a direct question, not
  AI-chosen defaults.
- Every scope decision in §1.1 was written as a documented, *arguable* choice — the point being
  that a reviewer can disagree with the 15-minute threshold or the opt-out routing default without
  having to reverse-engineer why it was chosen.

## 2. Where agentic AI could extend the running system

The system as built is deliberately deterministic on its critical path — routing, retry,
dedup, and escalation all need to be reliable and boring under audit, and section 4.9 already
exists precisely to make every decision reconstructable. So the right place for agentic AI in
*this* system is additive and non-blocking: it should make the human-facing edges smarter without
becoming a dependency the core pipeline needs to succeed. Three concrete candidates, in order of
how ready each is to build:

### 2.1 Escalation summarization agent (best next step)

**Where it hooks in:** `EscalationExecutor.escalateOne()` — the exact point that already creates
the escalation `DeliveryAttempt` (`backend/src/main/java/com/schwab/nms/infrastructure/worker/EscalationExecutor.java`).

**What it would do:** before creating that delivery unit, call an agent that reads the
notification (`sourceSystem`, `notificationType`, `severity`, `subject`/`body`), its full
`AuditEvent` history (routing decisions, every attempt/failure/retry), and optionally recent
notifications sharing the same `sourceSystem`/`eventId` in a lookback window — then produces a
2–3 sentence natural-language brief instead of a generic templated page. E.g.: *"CRITICAL fraud
alert from fraud-detection has failed delivery via EMAIL and SMS for 15 minutes (TIMEOUT, 2
attempts each). No related alerts from this system in the last hour — looks provider-specific,
not a broader outage."* That brief becomes the escalation message body.

**Why this one first:** it slots into an already-designed hook without touching routing, retry,
or dedup logic at all — `EscalationExecutor`'s job stays "create a `DeliveryAttempt`," just with
a smarter message. It directly answers the alert-fatigue problem the ambiguous-requirement
scenario already flagged as a real concern (`docs/scenarios/03-ambiguous-requirements.md`).

**Guardrail:** additive only. If the agent call fails or times out, escalation proceeds with the
existing generic message — a safety-critical page must never be blocked on an LLM being
available. This constraint is the actual engineering judgment call here, not the summarization
itself.

### 2.2 Natural-language submission agent (agentic front door)

**Where it hooks in:** nothing new in the backend — it's a client of the existing
`POST /api/v1/notifications` API, using the OpenAPI schema the service already publishes at
`/v3/api-docs` (springdoc) as its tool definition. No hand-authored tool schema needed.

**What it would do:** an on-call engineer types something like *"page payments on-call, DB
failover is happening now, critical"* and a tool-using agent resolves that into a valid
`NotificationRequest` (source system, type, severity, priority, recipients, channels), calls the
API, and — this is the part that makes it a real agentic loop rather than a form-filler —
inspects the response: a 400 with validation `details` (missing recipient, unknown channel) is
something the agent can recover from by asking a clarifying question, rather than just failing.

**Why it's genuinely agentic:** it's a tool-use loop against this system's own contract, not a
static mapping. It would fit naturally either as a small CLI or as a "quick submit" box in the
React console (`notification-management-ui`).

**Guardrail:** the agent never bypasses Bean Validation, idempotency, or dedup — it's a smarter
way to construct a request for the same trusted, already-tested API surface, not a new parsing
path with its own rules.

### 2.3 Failure-pattern triage digest (read-only, safest to build)

**Where it hooks in:** a new scheduled job alongside the existing ones in
`infrastructure.worker` (`IdempotencyCleanupJob`, `EscalationJob`) — e.g.
`FailureTriageDigestJob`, read-only against `DeliveryAttemptRepository`.

**What it would do:** periodically group `ABANDONED` attempts and retry bursts by
`channel`/`lastFailureCategory`/recipient pattern over a lookback window, and turn a metrics
question into a narrative one ops can act on immediately: *"SLACK deliveries to the fin-team
channel have failed 12/12 times in the last hour, all AUTH_ERROR — looks like a rotated bot
token, not a per-notification problem."*

**Why it's the safest to build first if minimizing risk is the priority:** purely observational.
It reads `DeliveryAttempt`/`AuditEvent` rows and writes nowhere — it cannot affect delivery,
retry, or escalation correctness even if the agent output is wrong, which makes it the lowest-risk
way to introduce an LLM call into this codebase at all.

### What ties all three together

In every case, the agent sits **beside** the deterministic pipeline, consuming its structured,
already-audited output (`AuditEvent`, `DeliveryAttempt`, `Notification`) rather than sitting
**inside** a decision the system needs to be correct and explainable without an LLM in the loop.
That's a deliberate posture, not a limitation: routing, retry, and dedup are exactly the kind of
logic section 4 asks to be testable and defensible, and an LLM call is neither deterministic nor
free of failure modes a reviewer can't unit-test. The agentic opportunities are real, but they
belong at the edges — summarizing, front-ending, and triaging — not replacing the core.
