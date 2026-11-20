package lifeline.api.dto;

public record UpdateDonorProfileDto(
    Boolean isAvailable,
    Boolean isPaused,
    String currentZone
) {}
