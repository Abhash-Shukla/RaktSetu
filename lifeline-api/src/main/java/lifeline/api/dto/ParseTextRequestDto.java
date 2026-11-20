package lifeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseTextRequestDto(
    @NotBlank(message = "Request text cannot be blank")
    @Size(max = 5000, message = "Text exceeds maximum allowed length of 5000 characters")
    String text
) {}
