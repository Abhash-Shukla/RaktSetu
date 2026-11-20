package lifeline.api.dto;

import java.time.Instant;

public record EngineConfigDto(
    int versionNumber,
    String description,
    String configJson,
    String author,
    boolean isActive,
    Instant createdAt
) {}
