package com.schwab.nms.application;

import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.model.AuditEvent;
import com.schwab.nms.infrastructure.persistence.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Central write path for the audit trail (section 4.9). {@code detail} must only
 * ever contain structured, non-sensitive metadata — never message bodies or
 * credentials — enforced here rather than left to each call site.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    public void record(UUID notificationId, AuditAction action, String detail) {
        record(notificationId, action, detail, "system");
    }

    public void record(UUID notificationId, AuditAction action, String detail, String actor) {
        AuditEvent event = AuditEvent.builder()
                .notificationId(notificationId)
                .action(action)
                .detail(truncate(detail))
                .actor(actor)
                .occurredAt(Instant.now(clock))
                .build();
        auditEventRepository.save(event);
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() > 1000 ? detail.substring(0, 1000) : detail;
    }
}
