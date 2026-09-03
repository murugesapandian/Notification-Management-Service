package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.RoutingDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoutingDecisionRepository extends JpaRepository<RoutingDecision, UUID> {
    List<RoutingDecision> findByNotificationId(UUID notificationId);
}
