package lifeline.api.dto;

import jakarta.validation.constraints.NotNull;

public record DonorDecisionDto(
    @NotNull Boolean accept
) {}
