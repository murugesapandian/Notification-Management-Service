-- Ambiguous-requirement scenario (see docs/scenarios/03-ambiguous-requirements.md).
-- acknowledged_at/acknowledged_by/escalated_at were already provisioned on
-- `notifications` back in V1, so no column changes are needed here. This index
-- supports EscalationJob's polling query (NotificationRepository.findEligibleForEscalation),
-- which runs on every poll cycle and filters on exactly these columns.

CREATE INDEX idx_notification_escalation_eligibility
    ON notifications (severity, acknowledged_at, escalated_at, created_at);
