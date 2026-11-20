package lifeline.api.dto;

import java.time.Instant;

public record BloodRequestResponseDto(
    Long id,
    String patientReference,
    String bloodGroup,
    int unitsRequested,
    int unitsFulfilled,
    String urgency,
    String zone,
    String status,
    String source,
    Instant deadline,
    Instant createdAt,
    String contactNumber,
    String unparsedText,
    Double extractionConfidence
) {
    public BloodRequestResponseDto(
        Long id,
        String patientReference,
        String bloodGroup,
        int unitsRequested,
        int unitsFulfilled,
        String urgency,
        String zone,
        String status,
        String source,
        Instant deadline,
        Instant createdAt
    ) {
        this(id, patientReference, bloodGroup, unitsRequested, unitsFulfilled, urgency, zone, status, source, deadline, createdAt, null, null, null);
    }
}
