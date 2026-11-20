package lifeline.api.dto;

import java.time.Instant;

public record UserSummaryDto(
    Long id,
    String username,
    String email,
    String role,
    String status,
    Instant createdAt
) {}
