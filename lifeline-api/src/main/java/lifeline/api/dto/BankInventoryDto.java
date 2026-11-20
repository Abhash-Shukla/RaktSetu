package lifeline.api.dto;

import java.time.Instant;

public record BankInventoryDto(
    Long id,
    Long bankId,
    String bankName,
    String bankZone,
    String bloodGroup,
    int unitsAvailable,
    Instant updatedAt
) {}
