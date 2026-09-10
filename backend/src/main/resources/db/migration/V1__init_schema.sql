-- Greenfield schema (see docs/scenarios/01-greenfield.md)

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY,
    source_system       VARCHAR(100)  NOT NULL,
    event_id            VARCHAR(200),
    notification_type   VARCHAR(100)  NOT NULL,
    severity            VARCHAR(20)   NOT NULL,
    priority            VARCHAR(20)   NOT NULL,
    subject             VARCHAR(500),
    body                VARCHAR(4000),
    idempotency_key     VARCHAR(200),
    overall_status      VARCHAR(30)   NOT NULL,
    created_at          VARCHAR(30)   NOT NULL,
    updated_at          VARCHAR(30)   NOT NULL,
    scheduled_at        VARCHAR(30),
    expires_at          VARCHAR(30),
    acknowledged_at     VARCHAR(30),
    acknowledged_by     VARCHAR(100),
    escalated_at        VARCHAR(30),
    version             BIGINT
);

CREATE INDEX idx_notification_source_idem ON notifications (source_system, idempotency_key);
CREATE INDEX idx_notification_status ON notifications (overall_status);
CREATE INDEX idx_notification_event_id ON notifications (event_id);

CREATE TABLE notification_recipients (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    recipient_id    VARCHAR(200) NOT NULL,
    recipient_type  VARCHAR(20)  NOT NULL
);

CREATE INDEX idx_recipient_notification ON notification_recipients (notification_id);

CREATE TABLE requested_channels (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL
);

CREATE INDEX idx_requested_channel_notification ON requested_channels (notification_id);

CREATE TABLE delivery_attempts (
    id                      UUID PRIMARY KEY,
    notification_id         UUID NOT NULL,
    recipient_id             VARCHAR(200) NOT NULL,
    recipient_type            VARCHAR(20)  NOT NULL,
    channel                  VARCHAR(20)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    attempt_count             INT NOT NULL DEFAULT 0,
    max_attempts              INT NOT NULL DEFAULT 5,
    last_failure_category      VARCHAR(40),
    last_error_message         VARCHAR(500),
    provider_message_id        VARCHAR(200),
    next_retry_at              VARCHAR(30),
    created_at                VARCHAR(30) NOT NULL,
    updated_at                VARCHAR(30) NOT NULL,
    version                  BIGINT,
    CONSTRAINT uq_delivery_unit UNIQUE (notification_id, recipient_id, channel)
);

CREATE INDEX idx_delivery_status ON delivery_attempts (status);
CREATE INDEX idx_delivery_next_retry ON delivery_attempts (status, next_retry_at);
CREATE INDEX idx_delivery_notification ON delivery_attempts (notification_id);

CREATE TABLE routing_decisions (
    id                UUID PRIMARY KEY,
    notification_id    UUID NOT NULL,
    recipient_id        VARCHAR(200) NOT NULL,
    requested_channel    VARCHAR(20),
    resolved_channel     VARCHAR(20) NOT NULL,
    reason             VARCHAR(300) NOT NULL,
    decided_at          VARCHAR(30) NOT NULL
);

CREATE INDEX idx_routing_notification ON routing_decisions (notification_id);

CREATE TABLE audit_events (
    id                UUID PRIMARY KEY,
    notification_id    UUID NOT NULL,
    action            VARCHAR(40) NOT NULL,
    detail            VARCHAR(1000),
    actor             VARCHAR(100),
    occurred_at        VARCHAR(30) NOT NULL
);

CREATE INDEX idx_audit_notification ON audit_events (notification_id);

CREATE TABLE idempotency_records (
    id                UUID PRIMARY KEY,
    source_system      VARCHAR(100) NOT NULL,
    idempotency_key     VARCHAR(200) NOT NULL,
    notification_id     UUID NOT NULL,
    created_at         VARCHAR(30) NOT NULL,
    expires_at         VARCHAR(30) NOT NULL,
    CONSTRAINT uq_idem_source_key UNIQUE (source_system, idempotency_key)
);

CREATE INDEX idx_idem_expires ON idempotency_records (expires_at);

CREATE TABLE recipient_preferences (
    id            UUID PRIMARY KEY,
    recipient_id   VARCHAR(200) NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN NOT NULL,
    rank          INT NOT NULL,
    CONSTRAINT uq_recipient_channel UNIQUE (recipient_id, channel)
);

-- Demo reference data: a recipient who has opted out of SMS to exercise the
-- "fallback / respect preference" branch of RoutingService for non-critical alerts.
-- UUID literals are hardcoded (rather than a DB-generated random UUID) so this
-- migration runs identically on both H2 and PostgreSQL.
INSERT INTO recipient_preferences (id, recipient_id, channel, enabled, rank) VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', 'user-sms-optout', 'EMAIL', TRUE, 1),
    ('a1a1a1a1-0000-0000-0000-000000000002', 'user-sms-optout', 'SMS', FALSE, 2),
    ('a1a1a1a1-0000-0000-0000-000000000003', 'user-sms-optout', 'PUSH', TRUE, 3);
