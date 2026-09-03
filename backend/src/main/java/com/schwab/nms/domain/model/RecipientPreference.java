package com.schwab.nms.domain.model;

import com.schwab.nms.domain.enums.Channel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Reference data: a recipient's opt-in/rank for a given channel, used as one input
 * to routing (section 4.3). Seeded via Flyway for the demo; in production this
 * would be owned by a preference-center service.
 */
@Entity
@Table(name = "recipient_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_recipient_channel", columnNames = {"recipient_id", "channel"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientPreference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "recipient_id", nullable = false, length = 200)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Lower number = more preferred; used to pick a fallback channel ordering. */
    @Column(name = "rank", nullable = false)
    private int rank;
}
