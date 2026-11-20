package lifeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicFreeTextIntakeDto(
    @NotBlank(message = "Message text cannot be blank")
    @Size(max = 5000, message = "Text exceeds maximum allowed length of 5000 characters")
    String text,

    @Size(max = 100, message = "Contact info cannot exceed 100 characters")
    String contact
) {}
