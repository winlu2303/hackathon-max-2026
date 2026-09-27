package ru.domvporyadke.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "service_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "category")
    private String category;

    @Column(name = "subtype")
    private String subtype;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "master_id")
    private Long masterId;

    @Column(name = "address")
    private String address;

    @Column(name = "apartment")
    private String apartment;

    @Column(name = "caller_name")
    private String callerName;

    @Column(name = "preferred_time")
    private String preferredTime;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "uk_id")
    private Long ukId;
}