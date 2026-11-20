package lifeline.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.CreateBloodRequestDto;
import lifeline.api.dto.ParseTextRequestDto;
import lifeline.api.dto.ParsedIntakeDto;
import lifeline.api.dto.PublicFreeTextIntakeDto;
import lifeline.api.service.BloodRequestService;
import lifeline.api.service.IntakeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/requests")
public class PublicIntakeController {

    private final BloodRequestService bloodRequestService;
    private final IntakeService intakeService;

    public PublicIntakeController(
        BloodRequestService bloodRequestService,
        IntakeService intakeService
    ) {
        this.bloodRequestService = bloodRequestService;
        this.intakeService = intakeService;
    }

    /**
     * Public intake for families and patients.
     * Hard rule: Public intake requests strictly enter PENDING_REVIEW and NEVER trigger matching.
     * Rate-limited to prevent abuse.
     */
    @PostMapping
    public ResponseEntity<BloodRequestResponseDto> submitPublicRequest(
        @Valid @RequestBody CreateBloodRequestDto dto,
        HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);
        BloodRequestResponseDto created = bloodRequestService.createPublicRequest(dto, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Parses free-text for public user without immediately creating a request.
     * Allows public users to preview and verify extracted fields before submission.
     */
    @PostMapping("/parse-text")
    public ResponseEntity<ParsedIntakeDto> parseText(
        @Valid @RequestBody ParseTextRequestDto dto
    ) {
        ParsedIntakeDto parsed = intakeService.parseIntake(dto.text());
        return ResponseEntity.ok(parsed);
    }

    /**
     * Direct public free-text blood request intake (SRS NLP-2).
     * Parses free-text, validates schema, and strictly enters PENDING_REVIEW.
     * Never triggers matching or Tier-0.
     */
    @PostMapping("/free-text")
    public ResponseEntity<BloodRequestResponseDto> submitPublicFreeText(
        @Valid @RequestBody PublicFreeTextIntakeDto dto,
        HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);
        BloodRequestResponseDto created = intakeService.createPublicFreeTextRequest(dto, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Public status tracking for submitted blood requests.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<BloodRequestResponseDto> getRequestStatus(@PathVariable("id") Long id) {
        BloodRequestResponseDto response = bloodRequestService.getRequestById(id);
        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }
}
