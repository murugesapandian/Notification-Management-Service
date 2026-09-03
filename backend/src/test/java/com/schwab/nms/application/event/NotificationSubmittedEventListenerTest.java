package com.schwab.nms.application.event;

import com.schwab.nms.application.DeliveryOrchestrationService;
import com.schwab.nms.domain.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSubmittedEventListenerTest {

    @Mock
    private DeliveryOrchestrationService orchestrationService;

    private NotificationSubmittedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationSubmittedEventListener(orchestrationService);
    }

    @Test
    void orchestratesOnceOnTheHappyPath() {
        UUID id = UUID.randomUUID();

        listener.onNotificationSubmitted(new NotificationSubmittedEvent(id));

        verify(orchestrationService, times(1)).orchestrate(id);
    }

    @Test
    void retriesOnceAfterLosingAConcurrentUpdateRaceThenSucceeds() {
        UUID id = UUID.randomUUID();
        doThrow(new ObjectOptimisticLockingFailureException(Notification.class, id))
                .doNothing()
                .when(orchestrationService).orchestrate(id);

        listener.onNotificationSubmitted(new NotificationSubmittedEvent(id));

        verify(orchestrationService, times(2)).orchestrate(id);
    }

    @Test
    void stopsAfterExhaustingRetriesWithoutPropagatingTheException() {
        UUID id = UUID.randomUUID();
        doThrow(new ObjectOptimisticLockingFailureException(Notification.class, id))
                .when(orchestrationService).orchestrate(id);

        listener.onNotificationSubmitted(new NotificationSubmittedEvent(id));

        verify(orchestrationService, times(3)).orchestrate(id);
    }
}
