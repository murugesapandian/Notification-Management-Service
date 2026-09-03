package com.schwab.nms.infrastructure.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Scheduling trigger only (section 4.1 "Delivery attempts" / async processing).
 * All transactional work — claiming due attempts under a pessimistic lock and
 * dispatching each to its provider — lives in {@link DeliveryDispatcher}, which
 * this class calls as an external Spring bean so {@code @Transactional} actually
 * applies (see DeliveryDispatcher's javadoc for why that split exists).
 */
@Component
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryDispatcher dispatcher;

    public DeliveryWorker(DeliveryDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${nms.worker.poll-interval-ms:2000}")
    public void pollAndDispatch() {
        List<UUID> claimed = dispatcher.claimDueBatch();
        for (UUID attemptId : claimed) {
            try {
                dispatcher.dispatch(attemptId);
            } catch (Exception e) {
                log.error("Unhandled error dispatching delivery attempt {}", attemptId, e);
            }
        }
    }
}
