package com.schwab.nms.api;

import com.schwab.nms.api.dto.NotificationRequest;
import com.schwab.nms.api.dto.NotificationResponse;
import com.schwab.nms.api.dto.NotificationStatusResponse;
import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests exercising the real HTTP surface against an in-memory H2
 * database with Flyway-managed schema and the actual async worker running on
 * its normal schedule (accelerated via application-test.yml). See
 * docs/scenarios/01-greenfield.md for the scenario this validates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/notifications";
    }

    private NotificationRequest baseRequest(String recipientId, List<Channel> channels, Severity severity,
                                             String idempotencyKey) {
        return new NotificationRequest(
                "trading-platform", "evt-" + UUID.randomUUID(), "TRADE_ALERT", severity, Priority.HIGH,
                "Large order filled", "Order 12345 filled at $101.20",
                List.of(new NotificationRequest.RecipientDto(recipientId, RecipientType.USER_ID)),
                channels, idempotencyKey, null, null);
    }

    @Test
    void submittedNotificationEventuallyReachesDeliveredStatus() {
        NotificationRequest request = baseRequest("user-happy-path", List.of(Channel.EMAIL), Severity.MEDIUM, null);

        ResponseEntity<NotificationResponse> submitResponse = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);

        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID notificationId = submitResponse.getBody().notificationId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<NotificationStatusResponse> statusResponse =
                    restTemplate.getForEntity(baseUrl() + "/" + notificationId, NotificationStatusResponse.class);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statusResponse.getBody().overallStatus()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(statusResponse.getBody().deliveries()).hasSize(1);
            assertThat(statusResponse.getBody().deliveries().get(0).status()).isEqualTo(DeliveryStatus.SUCCESS);
        });
    }

    @Test
    void repeatingSameIdempotencyKeyDoesNotCreateASecondNotification() {
        String key = "idem-" + UUID.randomUUID();
        NotificationRequest request = baseRequest("user-dedup", List.of(Channel.EMAIL), Severity.LOW, key);

        ResponseEntity<NotificationResponse> first = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);
        ResponseEntity<NotificationResponse> second = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().duplicate()).isTrue();
        assertThat(second.getBody().notificationId()).isEqualTo(first.getBody().notificationId());
    }

    @Test
    void invalidRecipientAbandonsWithoutEndlessRetry() {
        NotificationRequest request = baseRequest("invalid-user", List.of(Channel.EMAIL), Severity.LOW, null);

        ResponseEntity<NotificationResponse> submitResponse = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);
        UUID notificationId = submitResponse.getBody().notificationId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<NotificationStatusResponse> statusResponse =
                    restTemplate.getForEntity(baseUrl() + "/" + notificationId, NotificationStatusResponse.class);
            assertThat(statusResponse.getBody().overallStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(statusResponse.getBody().deliveries().get(0).status()).isEqualTo(DeliveryStatus.ABANDONED);
        });
    }

    @Test
    void transientFailureRecoversAfterARetry() {
        NotificationRequest request = baseRequest("flaky-user", List.of(Channel.SMS), Severity.LOW, null);

        ResponseEntity<NotificationResponse> submitResponse = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);
        UUID notificationId = submitResponse.getBody().notificationId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<NotificationStatusResponse> statusResponse =
                    restTemplate.getForEntity(baseUrl() + "/" + notificationId, NotificationStatusResponse.class);
            assertThat(statusResponse.getBody().overallStatus()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(statusResponse.getBody().deliveries().get(0).attemptCount()).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void criticalSeverityFansOutToAllChannelsEvenWithoutRequestingThem() {
        NotificationRequest request = baseRequest("user-critical", List.of(), Severity.CRITICAL, null);

        ResponseEntity<NotificationResponse> submitResponse = restTemplate.postForEntity(baseUrl(), request, NotificationResponse.class);
        UUID notificationId = submitResponse.getBody().notificationId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<NotificationStatusResponse> statusResponse =
                    restTemplate.getForEntity(baseUrl() + "/" + notificationId, NotificationStatusResponse.class);
            assertThat(statusResponse.getBody().selectedChannels()).hasSizeGreaterThanOrEqualTo(3);
        });
    }

    @Test
    void rejectsSubmissionMissingRequiredFields() {
        NotificationRequest invalid = new NotificationRequest(
                null, null, null, null, null, null, null, List.of(), List.of(), null, null, null);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), invalid, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsNotFoundForUnknownNotificationId() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
