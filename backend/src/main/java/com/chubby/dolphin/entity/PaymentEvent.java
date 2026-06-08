package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @Column(name = "payment_event_id")
    private String paymentEventId;

    @Column(nullable = false)
    private String provider; // RAZORPAY, STRIPE

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSED, FAILED
}
