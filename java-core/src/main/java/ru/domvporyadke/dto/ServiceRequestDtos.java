package ru.domvporyadke.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class ServiceRequestDtos {

    public record CreateRequest(
        @NotNull String category,
        String subtype,
        @NotNull Long buildingId,
        String preferredTime,
        String description
    ) {}

    public record Response(
        Long id,
        String category,
        String subtype,
        String status,
        Long buildingId,
        Long userId,
        Long masterId,
        Instant createdAt
    ) {}
}