package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
