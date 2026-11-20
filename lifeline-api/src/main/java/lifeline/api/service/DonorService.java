package lifeline.api.service;

import lifeline.api.dto.DonorProfileDto;
import lifeline.api.dto.NotificationResponseDto;
import lifeline.api.dto.UpdateDonorProfileDto;
import lifeline.api.model.*;
import lifeline.api.repository.DonorProfileRepository;
import lifeline.api.repository.MatchLogRepository;
import lifeline.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DonorService {

    private final DonorProfileRepository donorProfileRepository;
    private final UserRepository userRepository;
    private final MatchLogRepository matchLogRepository;
    private final MatchingService matchingService;
    private final AuditService auditService;

    public DonorService(
        DonorProfileRepository donorProfileRepository,
        UserRepository userRepository,
        MatchLogRepository matchLogRepository,
        MatchingService matchingService,
        AuditService auditService
    ) {
        this.donorProfileRepository = donorProfileRepository;
        this.userRepository = userRepository;
        this.matchLogRepository = matchLogRepository;
        this.matchingService = matchingService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public DonorProfileDto getProfile(String username) {
        DonorProfileEntity donor = getDonorByUsername(username);
        return mapToDto(donor);
    }

    @Transactional
    public DonorProfileDto updateProfile(String username, UpdateDonorProfileDto dto, String ipAddress) {
        DonorProfileEntity donor = getDonorByUsername(username);

        if (dto.isAvailable() != null) {
            donor.setAvailable(dto.isAvailable());
        }
        if (dto.isPaused() != null) {
            donor.setPaused(dto.isPaused());
        }
        if (dto.currentZone() != null && !dto.currentZone().isBlank()) {
            donor.setCurrentZone(dto.currentZone());
            donor.setLocationConfirmedAt(Instant.now());
        }

        donor = donorProfileRepository.save(donor);

        auditService.log(username, "DONOR", "DONOR_PROFILE_UPDATED", "DONOR_PROFILE",
            donor.getId().toString(), "Updated availability/location", ipAddress);

        return mapToDto(donor);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotifications(String username) {
        DonorProfileEntity donor = getDonorByUsername(username);
        return matchLogRepository.findByDonorIdOrderByNotifiedAtDesc(donor.getId())
            .stream()
            .map(log -> new NotificationResponseDto(
                log.getId(),
                log.getRequest().getId(),
                log.getRequest().getPatientReference(),
                log.getRequest().getBloodGroup(),
                log.getRequest().getUrgency(),
                log.getRequest().getZone(),
                log.getRequest().getDeadline(),
                log.getOutcome().name(),
                log.getNotifiedAt()
            ))
            .toList();
    }

    @Transactional
    public NotificationResponseDto respondToNotification(
        String username,
        Long matchLogId,
        boolean accept,
        String ipAddress
    ) {
        DonorProfileEntity donor = getDonorByUsername(username);
        MatchLogEntity matchLog = matchLogRepository.findById(matchLogId)
            .orElseThrow(() -> new IllegalArgumentException("Match log not found: " + matchLogId));

        if (!matchLog.getDonor().getId().equals(donor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Match log does not belong to the authenticated donor.");
        }

        MatchOutcome outcome = accept ? MatchOutcome.ACCEPTED : MatchOutcome.DECLINED;
        return matchingService.recordResponse(matchLogId, outcome, username, ipAddress);
    }

    private DonorProfileEntity getDonorByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return donorProfileRepository.findByUserId(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Donor profile not found for user: " + username));
    }

    private DonorProfileDto mapToDto(DonorProfileEntity entity) {
        return new DonorProfileDto(
            entity.getId(),
            entity.getUser().getUsername(),
            entity.getUser().getEmail(),
            entity.getBloodGroup(),
            entity.getSex(),
            entity.getHomeZone(),
            entity.getLastDonationDate(),
            entity.isAvailable(),
            entity.isPaused(),
            entity.getCurrentZone(),
            entity.getTotalContacts(),
            entity.getTotalAccepts(),
            entity.getTotalDeclines(),
            entity.getTotalNoResponses()
        );
    }
}
