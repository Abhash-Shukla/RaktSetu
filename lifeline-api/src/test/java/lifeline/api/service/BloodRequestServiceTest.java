package lifeline.api.service;

import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.CreateBloodRequestDto;
import lifeline.api.dto.ReviewRequestDto;
import lifeline.api.model.BloodRequestEntity;
import lifeline.api.model.RequestSource;
import lifeline.api.model.RequestStatus;
import lifeline.api.model.Role;
import lifeline.api.model.UserEntity;
import lifeline.api.repository.BloodRequestRepository;
import lifeline.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodRequestServiceTest {

    @Mock
    private BloodRequestRepository bloodRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private BloodRequestService bloodRequestService;

    @BeforeEach
    void setUp() {
        // Set low rate limit of 3 requests/minute to test throttling easily
        bloodRequestService = new BloodRequestService(bloodRequestRepository, userRepository, auditService, 3);
    }

    @Test
    @DisplayName("Public Intake: Always creates request in PENDING_REVIEW status with PUBLIC source")
    void publicIntake_alwaysEntersPendingReview() {
        CreateBloodRequestDto dto = new CreateBloodRequestDto("PAT-999", "B_POS", 2, "URGENT", "Z1", 6);

        when(bloodRequestRepository.save(any(BloodRequestEntity.class))).thenAnswer(invocation -> {
            BloodRequestEntity saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        BloodRequestResponseDto response = bloodRequestService.createPublicRequest(dto, "192.168.1.10");

        assertThat(response.status()).isEqualTo(RequestStatus.PENDING_REVIEW.name());
        assertThat(response.source()).isEqualTo(RequestSource.PUBLIC.name());
        assertThat(response.patientReference()).isEqualTo("PAT-999");
        assertThat(response.bloodGroup()).isEqualTo("B_POS");
        assertThat(response.unitsRequested()).isEqualTo(2);

        ArgumentCaptor<BloodRequestEntity> captor = ArgumentCaptor.forClass(BloodRequestEntity.class);
        verify(bloodRequestRepository).save(captor.capture());
        BloodRequestEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
        assertThat(captured.getSource()).isEqualTo(RequestSource.PUBLIC);
    }

    @Test
    @DisplayName("Public Intake Rate Limiting: Blocks IP exceeding limit")
    void publicIntake_rateLimitingEnforced() {
        CreateBloodRequestDto dto = new CreateBloodRequestDto("PAT-RATE", "O_POS", 1, "ROUTINE", "Z1", 24);

        when(bloodRequestRepository.save(any(BloodRequestEntity.class))).thenAnswer(invocation -> {
            BloodRequestEntity saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        // 3 requests allowed within window
        bloodRequestService.createPublicRequest(dto, "10.0.0.5");
        bloodRequestService.createPublicRequest(dto, "10.0.0.5");
        bloodRequestService.createPublicRequest(dto, "10.0.0.5");

        // 4th request must throw IllegalStateException with rate limit message
        assertThatThrownBy(() -> bloodRequestService.createPublicRequest(dto, "10.0.0.5"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("Staff Request: Directly enters OPEN status")
    void staffRequest_directlyEntersOpen() {
        CreateBloodRequestDto dto = new CreateBloodRequestDto("PAT-STAFF", "A_NEG", 3, "CRITICAL", "Z2", 2);
        UserEntity staff = new UserEntity("staff_central", "staff@test.com", "hash", Role.BANK_STAFF);
        staff.setId(2L);

        when(userRepository.findByUsername("staff_central")).thenReturn(Optional.of(staff));
        when(bloodRequestRepository.save(any(BloodRequestEntity.class))).thenAnswer(invocation -> {
            BloodRequestEntity saved = invocation.getArgument(0);
            saved.setId(102L);
            return saved;
        });

        BloodRequestResponseDto response = bloodRequestService.createStaffRequest(dto, "staff_central", "127.0.0.1");

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN.name());
        assertThat(response.source()).isEqualTo(RequestSource.STAFF.name());
    }

    @Test
    @DisplayName("Review Request: Approves PENDING_REVIEW request into OPEN status")
    void reviewRequest_approval_transitionsToOpen() {
        BloodRequestEntity request = new BloodRequestEntity();
        request.setId(200L);
        request.setStatus(RequestStatus.PENDING_REVIEW);
        request.setSource(RequestSource.PUBLIC);
        request.setBloodGroup("O_POS");
        request.setUnitsRequested(2);
        request.setUrgency("ROUTINE");
        request.setZone("Z1");

        UserEntity reviewer = new UserEntity("staff_central", "staff@test.com", "hash", Role.BANK_STAFF);
        reviewer.setId(2L);

        when(bloodRequestRepository.findById(200L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("staff_central")).thenReturn(Optional.of(reviewer));
        when(bloodRequestRepository.save(any(BloodRequestEntity.class))).thenAnswer(i -> i.getArgument(0));

        ReviewRequestDto reviewDto = new ReviewRequestDto(true, null, "URGENT", 2);
        BloodRequestResponseDto result = bloodRequestService.reviewRequest(200L, reviewDto, "staff_central", "127.0.0.1");

        assertThat(result.status()).isEqualTo(RequestStatus.OPEN.name());
        assertThat(result.urgency()).isEqualTo("URGENT");
    }

    @Test
    @DisplayName("Review Request: Cannot review request that is not in PENDING_REVIEW")
    void reviewRequest_cannotReviewOpenRequest() {
        BloodRequestEntity request = new BloodRequestEntity();
        request.setId(201L);
        request.setStatus(RequestStatus.OPEN);

        when(bloodRequestRepository.findById(201L)).thenReturn(Optional.of(request));

        ReviewRequestDto reviewDto = new ReviewRequestDto(true, null, null, null);
        assertThatThrownBy(() -> bloodRequestService.reviewRequest(201L, reviewDto, "staff_central", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot review request in status: OPEN");
    }
}
