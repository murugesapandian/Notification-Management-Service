package com.schwab.nms.api.controller;

import com.schwab.nms.api.dto.AcknowledgeRequest;
import com.schwab.nms.api.dto.NotificationRequest;
import com.schwab.nms.api.dto.NotificationResponse;
import com.schwab.nms.api.dto.NotificationStatusResponse;
import com.schwab.nms.application.AcknowledgementService;
import com.schwab.nms.application.NotificationStatusService;
import com.schwab.nms.application.NotificationSubmissionResult;
import com.schwab.nms.application.NotificationSubmissionService;
import com.schwab.nms.application.command.SubmitNotificationCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Submission and status retrieval")
public class NotificationController {

    private final NotificationSubmissionService submissionService;
    private final NotificationStatusService statusService;
    private final AcknowledgementService acknowledgementService;

    public NotificationController(NotificationSubmissionService submissionService,
                                   NotificationStatusService statusService,
                                   AcknowledgementService acknowledgementService) {
        this.submissionService = submissionService;
        this.statusService = statusService;
        this.acknowledgementService = acknowledgementService;
    }

    @PostMapping
    @Operation(summary = "Submit a notification request",
            description = "Accepts a notification for asynchronous routing and delivery. "
                    + "Supplying the same idempotencyKey twice for the same sourceSystem returns the original notification.")
    public ResponseEntity<NotificationResponse> submit(@Valid @RequestBody NotificationRequest request) {
        SubmitNotificationCommand command = new SubmitNotificationCommand(
                request.sourceSystem(),
                request.eventId(),
                request.notificationType(),
                request.severity(),
                request.priority(),
                request.subject(),
                request.body(),
                request.recipients().stream()
                        .map(r -> new SubmitNotificationCommand.RecipientInput(r.recipientId(), r.recipientType()))
                        .toList(),
                request.requestedChannels() == null ? List.of() : request.requestedChannels(),
                request.idempotencyKey(),
                request.scheduledAt(),
                request.expiresAt()
        );

        NotificationSubmissionResult result = submissionService.submit(command);
        NotificationResponse body = NotificationResponse.from(result.notification(), result.duplicate());

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/v1/notifications/" + result.notification().getId()))
                .body(body);
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Retrieve notification status",
            description = "Returns overall status, selected channels, and per-recipient/per-channel delivery status.")
    public ResponseEntity<NotificationStatusResponse> getStatus(@PathVariable UUID notificationId) {
        NotificationStatusService.NotificationStatusView view = statusService.getStatus(notificationId);
        return ResponseEntity.ok(NotificationStatusResponse.from(view.notification(), view.deliveryAttempts()));
    }

    private static final int ACKNOWLEDGE_MAX_ATTEMPTS = 3;

    @PostMapping("/{notificationId}/acknowledge")
    @Operation(summary = "Acknowledge a notification",
            description = "Ambiguous-requirement scenario (docs/scenarios/03-ambiguous-requirements.md): "
                    + "explicit human/on-call acknowledgement, distinct from delivery success. First "
                    + "acknowledgement wins and prevents escalation of a CRITICAL notification.")
    public ResponseEntity<NotificationStatusResponse> acknowledge(@PathVariable UUID notificationId,
                                                                    @Valid @RequestBody AcknowledgeRequest request) {
        // The Notification row is also written by the async routing orchestration
        // (NotificationSubmittedEventListener) and, for CRITICAL notifications, by
        // EscalationJob. An acknowledge call landing in that same narrow window can
        // lose the optimistic-locking (@Version) race. Retrying a few times at this
        // API boundary is safe because acknowledge() is naturally idempotent — see
        // AcknowledgementService's "first acknowledgement wins" javadoc.
        ObjectOptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= ACKNOWLEDGE_MAX_ATTEMPTS; attempt++) {
            try {
                acknowledgementService.acknowledge(notificationId, request.acknowledgedBy());
                NotificationStatusService.NotificationStatusView view = statusService.getStatus(notificationId);
                return ResponseEntity.ok(NotificationStatusResponse.from(view.notification(), view.deliveryAttempts()));
            } catch (ObjectOptimisticLockingFailureException e) {
                lastConflict = e;
            }
        }
        throw lastConflict;
    }
}
