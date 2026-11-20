package lifeline.api.dto;

public record Tier0CheckDto(
    boolean resolved,
    Long bankId,
    String bankName,
    String bankZone,
    int unitsAvailable,
    String message
) {}
