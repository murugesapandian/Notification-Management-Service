package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByNotificationIdOrderByOccurredAtAsc(UUID notificationId);
}
