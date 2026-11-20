package lifeline.api.service;

import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.ParseTextRequestDto;
import lifeline.api.dto.ParsedIntakeDto;
import lifeline.api.dto.PublicFreeTextIntakeDto;
import lifeline.api.exception.InvalidIntakeException;
import lifeline.api.model.BloodRequestEntity;
import lifeline.api.model.RequestSource;
import lifeline.api.model.RequestStatus;
import lifeline.api.model.UserEntity;
import lifeline.api.nlp.IntakeParser;
import lifeline.api.repository.BloodRequestRepository;
import lifeline.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service managing LLM and NLP-assisted blood request intake workflows.
 * Enforces strict safety rules:
 * 1. Output schema strictly validated before entity creation.
 * 2. All parsed requests remain in PENDING_REVIEW; never auto-approved.
 * 3. Parsed fields are editable by staff prior to approval.
 * 4. Fallback to manual form is provided when parsing fails or confidence is low.
 */
@Service
public class IntakeService {

    private final IntakeParser intakeParser;
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BloodRequestService bloodRequestService;
    private final int rateLimitPerMinute;

    private final Map<String, SlidingRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public IntakeService(
        IntakeParser intakeParser,
        BloodRequestRepository bloodRequestRepository,
        UserRepository userRepository,
        AuditService auditService,
        BloodRequestService bloodRequestService,
        @Value("${lifeline.security.public-intake-rate-limit-per-minute:30}") int rateLimitPerMinute
    ) {
        this.intakeParser = intakeParser;
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.bloodRequestService = bloodRequestService;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    /**
     * Parses informal or forwarded message text into structured fields without creating an entity.
     * Used by staff to inspect and edit extracted fields, and by public UI to preview extracted fields.
     */
    public ParsedIntakeDto parseIntake(String rawText) {
        return intakeParser.parse(rawText);
    }

    /**
     * Public free-text intake (Use case 2):
     * Parses free-text, validates strictly, and stores request in PENDING_REVIEW.
     * Never triggers matching or Tier-0.
     */
    @Transactional
    public BloodRequestResponseDto createPublicFreeTextRequest(PublicFreeTextIntakeDto dto, String ipAddress) {
        checkRateLimit(ipAddress);

        ParsedIntakeDto parsed = intakeParser.parse(dto.text());

        // Validate extraction and handle fallback
        if (!parsed.isValid() || parsed.fallbackRecommended()) {
            String reason = parsed.fallbackReason() != null
                ? parsed.fallbackReason()
                : "Automatic parsing incomplete or below confidence threshold. Please use the manual intake form.";
            throw new InvalidIntakeException(reason, parsed.flags(), "/api/v1/public/requests");
        }

        BloodRequestEntity entity = new BloodRequestEntity();
        String generatedRef = "PARSED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.setPatientReference(generatedRef);
        entity.setBloodGroup(parsed.bloodGroup());
        entity.setUnitsRequested(parsed.units() != null ? parsed.units() : 1);
        entity.setUnitsFulfilled(0);
        entity.setUrgency(parsed.urgency() != null ? parsed.urgency() : "URGENT");
        entity.setZone(parsed.zoneOrPincode() != null ? parsed.zoneOrPincode() : "UNKNOWN");

        // Strict Requirement: All parsed requests remain in PENDING_REVIEW
        entity.setStatus(RequestStatus.PENDING_REVIEW);
        entity.setSource(RequestSource.PARSED);

        String contact = (dto.contact() != null && !dto.contact().isBlank()) ? dto.contact() : parsed.contact();
        entity.setContactNumber(contact);
        entity.setUnparsedText(dto.text());
        entity.setExtractionConfidence(parsed.confidence());

        int deadlineHours = resolveDefaultDeadlineHours(entity.getUrgency());
        entity.setDeadline(Instant.now().plus(Duration.ofHours(deadlineHours)));

        entity = bloodRequestRepository.save(entity);

        auditService.log("PUBLIC_ANONYMOUS", "PUBLIC", "PUBLIC_FREE_TEXT_QUEUED", "BLOOD_REQUEST",
            entity.getId().toString(), "Public free-text parsed with confidence " + parsed.confidence() + " and queued in PENDING_REVIEW", ipAddress);

        return bloodRequestService.mapToDto(entity);
    }

    /**
     * Staff forwarded message intake (Use case 1):
     * Staff pastes forwarded message. System parses and queues request in PENDING_REVIEW.
     * Staff must inspect, edit, and approve via standard review workflow.
     */
    @Transactional
    public BloodRequestResponseDto createStaffParsedRequest(ParseTextRequestDto dto, String staffUsername, String ipAddress) {
        UserEntity staff = userRepository.findByUsername(staffUsername)
            .orElseThrow(() -> new IllegalArgumentException("Staff user not found: " + staffUsername));

        ParsedIntakeDto parsed = intakeParser.parse(dto.text());

        if (!parsed.isValid() || parsed.fallbackRecommended()) {
            String reason = parsed.fallbackReason() != null
                ? parsed.fallbackReason()
                : "Automatic parsing incomplete or below confidence threshold. Please use manual form.";
            throw new InvalidIntakeException(reason, parsed.flags(), "/api/v1/requests");
        }

        BloodRequestEntity entity = new BloodRequestEntity();
        String generatedRef = "STAFF-PARSED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.setPatientReference(generatedRef);
        entity.setBloodGroup(parsed.bloodGroup());
        entity.setUnitsRequested(parsed.units() != null ? parsed.units() : 1);
        entity.setUnitsFulfilled(0);
        entity.setUrgency(parsed.urgency() != null ? parsed.urgency() : "URGENT");
        entity.setZone(parsed.zoneOrPincode() != null ? parsed.zoneOrPincode() : "UNKNOWN");

        // Strict Requirement: All parsed requests remain in PENDING_REVIEW
        entity.setStatus(RequestStatus.PENDING_REVIEW);
        entity.setSource(RequestSource.PARSED);
        entity.setContactNumber(parsed.contact());
        entity.setUnparsedText(dto.text());
        entity.setExtractionConfidence(parsed.confidence());

        int deadlineHours = resolveDefaultDeadlineHours(entity.getUrgency());
        entity.setDeadline(Instant.now().plus(Duration.ofHours(deadlineHours)));

        entity = bloodRequestRepository.save(entity);

        auditService.log(staffUsername, staff.getRole().name(), "STAFF_FREE_TEXT_QUEUED", "BLOOD_REQUEST",
            entity.getId().toString(), "Staff pasted free-text; parsed with confidence " + parsed.confidence() + " and queued in PENDING_REVIEW", ipAddress);

        return bloodRequestService.mapToDto(entity);
    }

    private int resolveDefaultDeadlineHours(String urgency) {
        if (urgency == null) return 24;
        return switch (urgency.toUpperCase()) {
            case "CRITICAL" -> 2;   // P-07: 2 hours
            case "URGENT" -> 6;     // P-07: 6 hours
            default -> 24;          // P-07: 24 hours
        };
    }

    private void checkRateLimit(String ipAddress) {
        String key = (ipAddress == null || ipAddress.isBlank()) ? "DEFAULT" : ipAddress;
        SlidingRateLimiter limiter = rateLimiters.computeIfAbsent(key, k -> new SlidingRateLimiter(rateLimitPerMinute));
        if (!limiter.tryAcquire()) {
            throw new IllegalStateException("Rate limit exceeded for public intake. Maximum " + rateLimitPerMinute + " requests per minute allowed.");
        }
    }

    private static class SlidingRateLimiter {
        private final int limit;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        public SlidingRateLimiter(int limit) {
            this.limit = limit;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60000) {
                windowStart = now;
                counter.set(0);
            }
            return counter.incrementAndGet() <= limit;
        }
    }
}
