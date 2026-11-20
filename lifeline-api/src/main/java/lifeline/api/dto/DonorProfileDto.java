package lifeline.api.dto;

import java.time.LocalDate;

public record DonorProfileDto(
    Long id,
    String username,
    String email,
    String bloodGroup,
    String sex,
    String homeZone,
    LocalDate lastDonationDate,
    boolean isAvailable,
    boolean isPaused,
    String currentZone,
    int totalContacts,
    int totalAccepts,
    int totalDeclines,
    int totalNoResponses
) {}
