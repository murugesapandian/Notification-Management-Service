package com.schwab.nms.application;

import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.domain.model.RoutingDecision;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import com.schwab.nms.infrastructure.persistence.repository.RoutingDecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Use case: retrieve notification status (section 4.2). Read-only, no side effects. */
@Service
@Transactional(readOnly = true)
public class NotificationStatusService {

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RoutingDecisionRepository routingDecisionRepository;

    public NotificationStatusService(NotificationRepository notificationRepository,
                                      DeliveryAttemptRepository deliveryAttemptRepository,
                                      RoutingDecisionRepository routingDecisionRepository) {
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.routingDecisionRepository = routingDecisionRepository;
    }

    public record NotificationStatusView(Notification notification, List<DeliveryAttempt> deliveryAttempts) {
    }

    public NotificationStatusView getStatus(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationId(notificationId);
        return new NotificationStatusView(notification, attempts);
    }

    public List<RoutingDecision> getRoutingHistory(UUID notificationId) {
        return routingDecisionRepository.findByNotificationId(notificationId);
    }
}
