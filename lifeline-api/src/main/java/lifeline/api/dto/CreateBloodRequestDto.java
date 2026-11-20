package lifeline.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateBloodRequestDto(
    @NotBlank String patientReference,
    @NotBlank @Pattern(regexp = "^(A|B|AB|O)_(POS|NEG)$", message = "Invalid blood group") String bloodGroup,
    @NotNull @Min(1) Integer unitsRequested,
    @NotBlank @Pattern(regexp = "^(CRITICAL|URGENT|ROUTINE)$", message = "Urgency must be CRITICAL, URGENT, or ROUTINE") String urgency,
    @NotBlank String zone,
    Integer deadlineHours
) {}
