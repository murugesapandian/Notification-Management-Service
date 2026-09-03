package com.schwab.nms.api.controller;

import com.schwab.nms.api.dto.NotificationRequest;
import com.schwab.nms.api.dto.NotificationResponse;
import com.schwab.nms.api.dto.NotificationStatusResponse;
import com.schwab.nms.application.NotificationStatusService;
import com.schwab.nms.application.NotificationSubmissionResult;
import com.schwab.nms.application.NotificationSubmissionService;
import com.schwab.nms.application.command.SubmitNotificationCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public NotificationController(NotificationSubmissionService submissionService,
                                   NotificationStatusService statusService) {
        this.submissionService = submissionService;
        this.statusService = statusService;
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
}
