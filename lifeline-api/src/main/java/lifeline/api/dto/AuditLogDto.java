package lifeline.api.dto;

import java.time.Instant;

public record AuditLogDto(
    Long id,
    String actorUsername,
    String actorRole,
    String action,
    String entityType,
    String entityId,
    String details,
    String ipAddress,
    Instant createdAt
) {}
