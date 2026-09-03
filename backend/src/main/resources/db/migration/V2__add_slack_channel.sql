-- Brownfield: add SLACK as a supported channel (see docs/scenarios/02-brownfield.md).
-- No table changes are needed — channel is stored as a plain VARCHAR everywhere
-- (requested_channels.channel, delivery_attempts.channel, routing_decisions.*_channel,
-- recipient_preferences.channel) and validated at the application layer via the
-- Channel enum, so a new channel value never requires a schema migration by itself.
-- This migration only seeds demo reference data for it.

INSERT INTO recipient_preferences (id, recipient_id, channel, enabled, rank) VALUES
    ('a1a1a1a1-0000-0000-0000-000000000004', 'user-sms-optout', 'SLACK', TRUE, 4);
