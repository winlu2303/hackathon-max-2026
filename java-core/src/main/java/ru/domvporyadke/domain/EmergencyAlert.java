package ru.domvporyadke.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "emergency_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "type")
    private String type;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "address")
    private String address;

    @Column(name = "apartment")
    private String apartment;

    @Column(name = "caller_name")
    private String callerName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "ack_at")
    private Instant ackAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "uk_id")
    private Long ukId;
}