package lifeline.api.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewRequestDto(
    @NotNull Boolean approve,
    String rejectionReason,
    String verifiedUrgency,
    Integer verifiedUnits,
    String verifiedBloodGroup,
    String verifiedZone,
    String verifiedPatientReference
) {
    public ReviewRequestDto(Boolean approve, String rejectionReason, String verifiedUrgency, Integer verifiedUnits) {
        this(approve, rejectionReason, verifiedUrgency, verifiedUnits, null, null, null);
    }
}
