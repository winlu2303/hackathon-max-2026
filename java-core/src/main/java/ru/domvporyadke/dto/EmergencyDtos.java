package ru.domvporyadke.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class EmergencyDtos {

    public record CreateRequest(
        @NotNull String type,
        @NotNull Long buildingId,
        @Size(max = 500) String description
    ) {}

    public record Response(
        Long id,
        String type,
        String status,
        Long buildingId,
        Long userId,
        String description,
        Instant createdAt,
        Instant ackAt,
        Instant dispatchedAt
    ) {}
}