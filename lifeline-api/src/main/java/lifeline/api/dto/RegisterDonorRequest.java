package lifeline.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDonorRequest(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank @Pattern(regexp = "^(A|B|AB|O)_(POS|NEG)$", message = "Invalid blood group") String bloodGroup,
    @NotBlank @Pattern(regexp = "^(MALE|FEMALE)$", message = "Sex must be MALE or FEMALE") String sex,
    @NotBlank String homeZone
) {}
