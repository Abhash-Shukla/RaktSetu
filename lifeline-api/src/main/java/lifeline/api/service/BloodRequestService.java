package lifeline.api.service;

import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.CreateBloodRequestDto;
import lifeline.api.dto.ReviewRequestDto;
import lifeline.api.model.*;
import lifeline.api.repository.BloodRequestRepository;
import lifeline.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BloodRequestService {

    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final int rateLimitPerMinute;

    // In-memory sliding window rate-limiter for public intake
    private final Map<String, SlidingRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public BloodRequestService(
        BloodRequestRepository bloodRequestRepository,
        UserRepository userRepository,
        AuditService auditService,
        @Value("${lifeline.security.public-intake-rate-limit-per-minute:30}") int rateLimitPerMinute
    ) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Transactional
    public BloodRequestResponseDto createPublicRequest(CreateBloodRequestDto dto, String ipAddress) {
        checkRateLimit(ipAddress);

        BloodRequestEntity request = new BloodRequestEntity();
        request.setPatientReference(dto.patientReference());
        request.setBloodGroup(dto.bloodGroup());
        request.setUnitsRequested(dto.unitsRequested());
        request.setUnitsFulfilled(0);
        request.setUrgency(dto.urgency());
        request.setZone(dto.zone());

        // Strict Requirement: Public intake MUST ALWAYS enter PENDING_REVIEW
        request.setStatus(RequestStatus.PENDING_REVIEW);
        request.setSource(RequestSource.PUBLIC);

        int deadlineHours = dto.deadlineHours() != null ? dto.deadlineHours() : resolveDefaultDeadlineHours(dto.urgency());
        request.setDeadline(Instant.now().plus(Duration.ofHours(deadlineHours)));

        request = bloodRequestRepository.save(request);

        auditService.log("PUBLIC_ANONYMOUS", "PUBLIC", "PUBLIC_REQUEST_CREATED", "BLOOD_REQUEST",
            request.getId().toString(), "Request queued in PENDING_REVIEW", ipAddress);

        return mapToDto(request);
    }

    @Transactional
    public BloodRequestResponseDto createStaffRequest(CreateBloodRequestDto dto, String staffUsername, String ipAddress) {
        UserEntity staff = userRepository.findByUsername(staffUsername)
            .orElseThrow(() -> new IllegalArgumentException("Staff user not found: " + staffUsername));

        BloodRequestEntity request = new BloodRequestEntity();
        request.setPatientReference(dto.patientReference());
        request.setBloodGroup(dto.bloodGroup());
        request.setUnitsRequested(dto.unitsRequested());
        request.setUnitsFulfilled(0);
        request.setUrgency(dto.urgency());
        request.setZone(dto.zone());

        // Staff-created requests are directly OPEN for matching
        request.setStatus(RequestStatus.OPEN);
        request.setSource(RequestSource.STAFF);
        request.setReviewer(staff);
        request.setReviewedAt(Instant.now());

        int deadlineHours = dto.deadlineHours() != null ? dto.deadlineHours() : resolveDefaultDeadlineHours(dto.urgency());
        request.setDeadline(Instant.now().plus(Duration.ofHours(deadlineHours)));

        request = bloodRequestRepository.save(request);

        auditService.log(staffUsername, staff.getRole().name(), "STAFF_REQUEST_CREATED", "BLOOD_REQUEST",
            request.getId().toString(), "Staff created OPEN blood request", ipAddress);

        return mapToDto(request);
    }

    @Transactional(readOnly = true)
    public List<BloodRequestResponseDto> getReviewQueue() {
        return bloodRequestRepository.findByStatusOrderByCreatedAtDesc(RequestStatus.PENDING_REVIEW)
            .stream()
            .map(this::mapToDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BloodRequestResponseDto> getAllRequests() {
        return bloodRequestRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::mapToDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public BloodRequestResponseDto getRequestById(Long id) {
        BloodRequestEntity entity = bloodRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found with id: " + id));
        return mapToDto(entity);
    }

    @Transactional
    public BloodRequestResponseDto reviewRequest(Long id, ReviewRequestDto dto, String staffUsername, String ipAddress) {
        BloodRequestEntity request = bloodRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found with id: " + id));

        if (request.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Cannot review request in status: " + request.getStatus());
        }

        UserEntity reviewer = userRepository.findByUsername(staffUsername)
            .orElseThrow(() -> new IllegalArgumentException("Reviewer user not found: " + staffUsername));

        request.setReviewer(reviewer);
        request.setReviewedAt(Instant.now());

        if (Boolean.TRUE.equals(dto.approve())) {
            request.setStatus(RequestStatus.OPEN);
            if (dto.verifiedUrgency() != null && !dto.verifiedUrgency().isBlank()) {
                request.setUrgency(dto.verifiedUrgency());
            }
            if (dto.verifiedUnits() != null && dto.verifiedUnits() > 0) {
                request.setUnitsRequested(dto.verifiedUnits());
            }
            if (dto.verifiedBloodGroup() != null && !dto.verifiedBloodGroup().isBlank()) {
                request.setBloodGroup(dto.verifiedBloodGroup());
            }
            if (dto.verifiedZone() != null && !dto.verifiedZone().isBlank()) {
                request.setZone(dto.verifiedZone());
            }
            if (dto.verifiedPatientReference() != null && !dto.verifiedPatientReference().isBlank()) {
                request.setPatientReference(dto.verifiedPatientReference());
            }
            auditService.log(staffUsername, reviewer.getRole().name(), "REQUEST_APPROVED", "BLOOD_REQUEST",
                request.getId().toString(), "Request approved by staff; moved to OPEN", ipAddress);
        } else {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectionReason(dto.rejectionReason() != null ? dto.rejectionReason() : "Rejected by staff review");
            auditService.log(staffUsername, reviewer.getRole().name(), "REQUEST_REJECTED", "BLOOD_REQUEST",
                request.getId().toString(), "Request rejected: " + request.getRejectionReason(), ipAddress);
        }

        request = bloodRequestRepository.save(request);
        return mapToDto(request);
    }

    private void checkRateLimit(String ipAddress) {
        String key = (ipAddress == null || ipAddress.isBlank()) ? "DEFAULT" : ipAddress;
        SlidingRateLimiter limiter = rateLimiters.computeIfAbsent(key, k -> new SlidingRateLimiter(rateLimitPerMinute));
        if (!limiter.tryAcquire()) {
            throw new IllegalStateException("Rate limit exceeded for public intake. Please try again in 1 minute.");
        }
    }

    private int resolveDefaultDeadlineHours(String urgency) {
        return switch (urgency.toUpperCase()) {
            case "CRITICAL" -> 2;   // P-07: 2 hours
            case "URGENT" -> 6;     // P-07: 6 hours
            default -> 24;          // P-07: 24 hours
        };
    }

    public BloodRequestResponseDto mapToDto(BloodRequestEntity entity) {
        return new BloodRequestResponseDto(
            entity.getId(),
            entity.getPatientReference(),
            entity.getBloodGroup(),
            entity.getUnitsRequested(),
            entity.getUnitsFulfilled(),
            entity.getUrgency(),
            entity.getZone(),
            entity.getStatus().name(),
            entity.getSource().name(),
            entity.getDeadline(),
            entity.getCreatedAt(),
            entity.getContactNumber(),
            entity.getUnparsedText(),
            entity.getExtractionConfidence()
        );
    }

    private static class SlidingRateLimiter {
        private final int limit;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        SlidingRateLimiter(int limit) {
            this.limit = limit;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                counter.set(0);
                windowStart = now;
            }
            return counter.incrementAndGet() <= limit;
        }
    }
}
