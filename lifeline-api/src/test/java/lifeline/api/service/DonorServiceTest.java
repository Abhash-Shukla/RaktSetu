package lifeline.api.service;

import lifeline.api.dto.DonorProfileDto;
import lifeline.api.dto.NotificationResponseDto;
import lifeline.api.dto.UpdateDonorProfileDto;
import lifeline.api.model.*;
import lifeline.api.repository.DonorProfileRepository;
import lifeline.api.repository.MatchLogRepository;
import lifeline.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonorServiceTest {

    @Mock
    private DonorProfileRepository donorProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchLogRepository matchLogRepository;

    @Mock
    private MatchingService matchingService;

    @Mock
    private AuditService auditService;

    private DonorService donorService;
    private UserEntity user;
    private DonorProfileEntity donor;

    @BeforeEach
    void setUp() {
        donorService = new DonorService(
            donorProfileRepository,
            userRepository,
            matchLogRepository,
            matchingService,
            auditService
        );

        user = new UserEntity("donor_arjun", "donor1@test.com", "hash", Role.DONOR);
        user.setId(4L);

        donor = new DonorProfileEntity(user, "O_POS", "MALE", "Z1");
        donor.setId(1L);
        donor.setAvailable(true);
        donor.setPaused(false);
    }

    @Test
    @DisplayName("Get Profile: Returns donor profile details")
    void getProfile_returnsDonorProfile() {
        when(userRepository.findByUsername("donor_arjun")).thenReturn(Optional.of(user));
        when(donorProfileRepository.findByUserId(4L)).thenReturn(Optional.of(donor));

        DonorProfileDto profile = donorService.getProfile("donor_arjun");

        assertThat(profile.username()).isEqualTo("donor_arjun");
        assertThat(profile.bloodGroup()).isEqualTo("O_POS");
        assertThat(profile.isAvailable()).isTrue();
        assertThat(profile.isPaused()).isFalse();
    }

    @Test
    @DisplayName("Update Profile: Updates availability, paused status, and location")
    void updateProfile_updatesFields() {
        when(userRepository.findByUsername("donor_arjun")).thenReturn(Optional.of(user));
        when(donorProfileRepository.findByUserId(4L)).thenReturn(Optional.of(donor));
        when(donorProfileRepository.save(any(DonorProfileEntity.class))).thenAnswer(i -> i.getArgument(0));

        UpdateDonorProfileDto updateDto = new UpdateDonorProfileDto(false, true, "Z2");
        DonorProfileDto result = donorService.updateProfile("donor_arjun", updateDto, "127.0.0.1");

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.isPaused()).isTrue();
        assertThat(result.currentZone()).isEqualTo("Z2");
        verify(donorProfileRepository).save(donor);
    }

    @Test
    @DisplayName("Respond To Notification: Rejects response if match log belongs to different donor")
    void respondToNotification_wrongDonor_throwsAccessDenied() {
        when(userRepository.findByUsername("donor_arjun")).thenReturn(Optional.of(user));
        when(donorProfileRepository.findByUserId(4L)).thenReturn(Optional.of(donor));

        // Different donor
        UserEntity otherUser = new UserEntity("donor_priya", "priya@test.com", "hash", Role.DONOR);
        otherUser.setId(5L);
        DonorProfileEntity otherDonor = new DonorProfileEntity(otherUser, "A_POS", "FEMALE", "Z1");
        otherDonor.setId(2L);

        MatchLogEntity matchLog = new MatchLogEntity();
        matchLog.setId(50L);
        matchLog.setDonor(otherDonor);

        when(matchLogRepository.findById(50L)).thenReturn(Optional.of(matchLog));

        assertThatThrownBy(() -> donorService.respondToNotification("donor_arjun", 50L, true, "127.0.0.1"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("does not belong to the authenticated donor");
    }

    @Test
    @DisplayName("Respond To Notification: Successfully records acceptance")
    void respondToNotification_validDonor_delegatesToMatchingService() {
        when(userRepository.findByUsername("donor_arjun")).thenReturn(Optional.of(user));
        when(donorProfileRepository.findByUserId(4L)).thenReturn(Optional.of(donor));

        MatchLogEntity matchLog = new MatchLogEntity();
        matchLog.setId(50L);
        matchLog.setDonor(donor);

        when(matchLogRepository.findById(50L)).thenReturn(Optional.of(matchLog));

        NotificationResponseDto expectedResponse = new NotificationResponseDto(
            50L, 10L, "PAT-01", "O_POS", "URGENT", "Z1", Instant.now().plusSeconds(3600), "ACCEPTED", Instant.now()
        );
        when(matchingService.recordResponse(50L, MatchOutcome.ACCEPTED, "donor_arjun", "127.0.0.1"))
            .thenReturn(expectedResponse);

        NotificationResponseDto result = donorService.respondToNotification("donor_arjun", 50L, true, "127.0.0.1");

        assertThat(result.outcome()).isEqualTo("ACCEPTED");
        verify(matchingService).recordResponse(50L, MatchOutcome.ACCEPTED, "donor_arjun", "127.0.0.1");
    }
}
