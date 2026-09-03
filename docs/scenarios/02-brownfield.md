# Scenario 2 — Brownfield: enhance the existing system across multiple layers

## Framing

The greenfield service from Scenario 1 is treated as an existing, already-shipped system. The
assignment asks for "a new notification channel / deduplication / refactor provider-specific
logic" — this scenario delivers all three as one coherent change, because in this codebase they
are genuinely coupled: adding a channel is what made the old dispatch logic worth refactoring,
and the refactor is what made adding the channel a small diff instead of a scattered one.

## Decomposition

1. **What does "add a channel" actually touch?** Enumerated every layer a new channel crosses:
   domain enum, a provider implementation, dispatch selection, DB reference/seed data, routing
   (does CRITICAL fan-out need to know about it explicitly? — no, it iterates `Channel.values()`,
   so it doesn't), and tests at each of those layers.
2. **Was the existing dispatch logic actually a problem, or just different?** `DeliveryWorker`
   selected a provider via an if/else chain over 3 channels — a reasonable starting point, but
   adding a 4th channel means editing that method again, and a 5th, and so on: it violates the
   Open/Closed Principle. That's the concrete, falsifiable case for refactoring it, not "registries
   are cleaner" as an abstract preference.
3. **Was the stated dedup requirement ("must be documented") actually satisfied?** Re-read
   section 4.4 literally: "the deduplication boundary and retention policy must be documented."
   The greenfield build documented a 7-day retention policy in `IdempotencyRecord`'s javadoc but
   never enforced it — nothing ever deleted an expired record. Treated as a real gap, not
   a stretch goal.

## Changes

| Layer | Change |
|---|---|
| `domain.enums.Channel` | Added `SLACK`. |
| `infrastructure.provider` | Added `SlackChannelProvider` (extends the same `AbstractSimulatedProvider` the others do — zero duplicated simulation logic). |
| `infrastructure.provider.ChannelProviderRegistry` | **New.** Strategy/Registry: builds a `Map<Channel, ChannelProvider>` from whatever `ChannelProvider` beans Spring finds at startup; fails fast at construction if two providers claim the same channel. |
| `infrastructure.worker.DeliveryDispatcher` | Refactored: the if/else chain (`resolveProvider`) is gone, replaced by `channelProviderRegistry.resolve(channel)`. This method did not need to change again when Slack was added — that absence of a diff *is* the evidence the refactor achieved its goal. |
| `db/migration/V2__add_slack_channel.sql` | Seeds demo recipient-preference data for Slack. No table changes were needed — channel is a `VARCHAR` everywhere it's stored, validated at the application layer by the `Channel` enum, which is itself part of the point: a new channel is additive, not a schema migration. |
| `infrastructure.worker.IdempotencyCleanupJob` | **New.** `@Scheduled` job that actually purges `idempotency_records` past `expires_at`, making the documented retention policy true rather than aspirational. |

## Validation

- New unit tests: `ChannelProviderRegistryTest` (resolves each registered channel; throws for an
  unregistered one; rejects two providers claiming the same channel at startup),
  `IdempotencyCleanupJobTest` (purges expired, no-ops when nothing is expired), plus Slack cases
  added to `SimulatedProviderBehaviorTest`.
- `DeliveryDispatcherTest` updated to construct its `ChannelProviderRegistry` from a `List.of(...)`
  of providers instead of three individually-injected fields — the test diff itself shows the
  seam moved cleanly.
- New end-to-end test: `NotificationApiIT.deliversToSlackChannelAddedByTheBrownfieldScenario` —
  submits a request explicitly on the new channel and asserts it reaches `DELIVERED`.
- Full regression: all 8 greenfield integration tests plus the new ones pass unchanged — the
  refactor did not require touching `RoutingService`, `NotificationSubmissionService`, or any
  existing test's assertions.
- `mvn verify` — 63 tests green at this point in the build (see git history for the exact
  count at each commit), JaCoCo gate still enforced.

## Trade-off explicitly accepted

`ChannelProviderRegistry` fails fast (throws `IllegalStateException`) if two `ChannelProvider`
beans register for the same `Channel` — a deliberate choice to surface a misconfiguration at
startup rather than silently letting one provider shadow another.
