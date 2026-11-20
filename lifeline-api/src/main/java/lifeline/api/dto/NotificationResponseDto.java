package lifeline.api.dto;

import java.time.Instant;

public record NotificationResponseDto(
    Long matchLogId,
    Long requestId,
    String patientReference,
    String bloodGroup,
    String urgency,
    String zone,
    Instant deadline,
    String outcome,
    Instant notifiedAt
) {}
