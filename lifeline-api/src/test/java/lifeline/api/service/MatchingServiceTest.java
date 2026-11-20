package lifeline.api.service;

import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.RankedMatchDto;
import lifeline.api.dto.Tier0CheckDto;
import lifeline.api.model.*;
import lifeline.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private BloodRequestRepository bloodRequestRepository;

    @Mock
    private DonorProfileRepository donorProfileRepository;

    @Mock
    private BankRepository bankRepository;

    @Mock
    private BankInventoryRepository bankInventoryRepository;

    @Mock
    private MatchLogRepository matchLogRepository;

    @Mock
    private EngineConfigVersionRepository engineConfigVersionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private BloodRequestService bloodRequestService;

    private MatchingService matchingService;

    private BankEntity bank1;
    private BankEntity bank2;
    private BloodRequestEntity openRequest;
    private BloodRequestEntity pendingRequest;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(
            bloodRequestRepository,
            donorProfileRepository,
            bankRepository,
            bankInventoryRepository,
            matchLogRepository,
            engineConfigVersionRepository,
            auditService,
            bloodRequestService
        );

        bank1 = new BankEntity("Central Blood Bank", "Z1", "+91-111", "b1@test.com", 28.6139, 77.2090);
        bank1.setId(1L);

        bank2 = new BankEntity("Metro Blood Bank", "Z2", "+91-222", "b2@test.com", 28.7041, 77.1025);
        bank2.setId(2L);

        openRequest = new BloodRequestEntity();
        openRequest.setId(10L);
        openRequest.setPatientReference("PAT-OPEN");
        openRequest.setBloodGroup("O_POS");
        openRequest.setUnitsRequested(2);
        openRequest.setUnitsFulfilled(0);
        openRequest.setUrgency("URGENT");
        openRequest.setZone("Z1");
        openRequest.setStatus(RequestStatus.OPEN);
        openRequest.setDeadline(Instant.now().plusSeconds(21600));

        pendingRequest = new BloodRequestEntity();
        pendingRequest.setId(11L);
        pendingRequest.setPatientReference("PAT-PENDING");
        pendingRequest.setBloodGroup("O_POS");
        pendingRequest.setUnitsRequested(2);
        pendingRequest.setUnitsFulfilled(0);
        pendingRequest.setUrgency("URGENT");
        pendingRequest.setZone("Z1");
        pendingRequest.setStatus(RequestStatus.PENDING_REVIEW);
        pendingRequest.setDeadline(Instant.now().plusSeconds(21600));
    }

    // =========================================================================
    // 1. PUBLIC INTAKE INVARIANCE & TIER-0 GATING
    // =========================================================================

    @Test
    @DisplayName("Matching Gating: Matching cannot be triggered for request in PENDING_REVIEW")
    void rankDonors_pendingReviewRequest_throwsIllegalStateException() {
        when(bloodRequestRepository.findById(11L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> matchingService.rankDonors(11L, 5, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING_REVIEW; staff review and approval required");
    }

    @Test
    @DisplayName("Tier-0 Gating: Tier-0 check cannot be triggered for request in PENDING_REVIEW")
    void checkTier0_pendingReviewRequest_throwsIllegalStateException() {
        when(bloodRequestRepository.findById(11L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> matchingService.checkTier0(11L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING_REVIEW; staff review and approval required");
    }

    // =========================================================================
    // 2. TIER-0 INVENTORY CHECKS & FULFILLMENT
    // =========================================================================

    @Test
    @DisplayName("Tier-0 Check: Routes to bank when nearby stock is sufficient")
    void checkTier0_sufficientStockNearby_routesToBank() {
        when(bloodRequestRepository.findById(10L)).thenReturn(Optional.of(openRequest));
        when(bankRepository.findAll()).thenReturn(List.of(bank1, bank2));

        BankInventoryEntity inv1 = new BankInventoryEntity(bank1, "O_POS", 10);
        when(bankInventoryRepository.findAll()).thenReturn(List.of(inv1));

        Tier0CheckDto result = matchingService.checkTier0(10L);

        assertThat(result.resolved()).isTrue();
        assertThat(result.bankId()).isEqualTo(1L);
        assertThat(result.unitsAvailable()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Tier-0 Check: Falls through to donor matching when nearby stock is insufficient")
    void checkTier0_insufficientStock_fallsThrough() {
        when(bloodRequestRepository.findById(10L)).thenReturn(Optional.of(openRequest));
        when(bankRepository.findAll()).thenReturn(List.of(bank1, bank2));

        BankInventoryEntity inv1 = new BankInventoryEntity(bank1, "O_POS", 0); // Out of stock
        when(bankInventoryRepository.findAll()).thenReturn(List.of(inv1));

        Tier0CheckDto result = matchingService.checkTier0(10L);

        assertThat(result.resolved()).isFalse();
        assertThat(result.bankId()).isNull();
        assertThat(result.message()).contains("Falling through to Tier-1 donor matching");
    }

    @Test
    @DisplayName("Tier-0 Fulfill: Depletes stock and sets status to RESOLVED_TIER0")
    void fulfillViaTier0_depletesInventoryAndResolvesRequest() {
        when(bloodRequestRepository.findById(10L)).thenReturn(Optional.of(openRequest));
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank1));

        BankInventoryEntity inv = new BankInventoryEntity(bank1, "O_POS", 5);
        when(bankInventoryRepository.findByBankId(1L)).thenReturn(List.of(inv));
        when(bloodRequestRepository.save(any(BloodRequestEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(bloodRequestService.mapToDto(any(BloodRequestEntity.class))).thenAnswer(i -> {
            BloodRequestEntity e = i.getArgument(0);
            return new BloodRequestResponseDto(e.getId(), e.getPatientReference(), e.getBloodGroup(), e.getUnitsRequested(), e.getUnitsFulfilled(), e.getUrgency(), e.getZone(), e.getStatus().name(), e.getSource().name(), e.getDeadline(), e.getCreatedAt());
        });

        BloodRequestResponseDto response = matchingService.fulfillViaTier0(10L, 1L, "staff_central", "127.0.0.1");

        assertThat(response.status()).isEqualTo(RequestStatus.RESOLVED_TIER0.name());
        assertThat(response.unitsFulfilled()).isEqualTo(2);
        assertThat(inv.getUnitsAvailable()).isEqualTo(3); // 5 - 2 = 3
        verify(bankInventoryRepository).save(inv);
    }

    // =========================================================================
    // 3. GREEDY TOP-N DONOR MATCHING & EXPLANATIONS
    // =========================================================================

    @Test
    @DisplayName("Greedy Top-N: Locked S6 ranking with Proximity ranking and Cooldown exclusion")
    void rankDonors_lockedS6Baseline_enforcesCooldownAndRanksByProximity() {
        when(bloodRequestRepository.findById(10L)).thenReturn(Optional.of(openRequest));
        when(bankRepository.findAll()).thenReturn(List.of(bank1, bank2));

        // Donor 1: Eligible, close (Z1), never donated
        UserEntity u1 = new UserEntity("d1", "d1@test.com", "h", Role.DONOR);
        u1.setId(101L);
        DonorProfileEntity d1 = new DonorProfileEntity(u1, "O_POS", "MALE", "Z1");
        d1.setId(1L);
        d1.setAvailable(true);
        d1.setPaused(false);

        // Donor 2: INELIGIBLE due to statutory cooldown (male donated 30 days ago, requires 90 days)
        UserEntity u2 = new UserEntity("d2", "d2@test.com", "h", Role.DONOR);
        u2.setId(102L);
        DonorProfileEntity d2 = new DonorProfileEntity(u2, "O_POS", "MALE", "Z1");
        d2.setId(2L);
        d2.setAvailable(true);
        d2.setPaused(false);
        d2.setLastDonationDate(LocalDate.now().minusDays(30)); // IN COOLDOWN!

        // Donor 3: Eligible, but in distant zone (Z2) -> PROXIMITY IS A RANKING FACTOR, NOT EXCLUSION
        UserEntity u3 = new UserEntity("d3", "d3@test.com", "h", Role.DONOR);
        u3.setId(103L);
        DonorProfileEntity d3 = new DonorProfileEntity(u3, "O_POS", "MALE", "Z2");
        d3.setId(3L);
        d3.setAvailable(true);
        d3.setPaused(false);

        // Donor 4: Incompatible blood group (AB_POS for O_POS recipient) -> EXCLUDED by compatibility
        UserEntity u4 = new UserEntity("d4", "d4@test.com", "h", Role.DONOR);
        u4.setId(104L);
        DonorProfileEntity d4 = new DonorProfileEntity(u4, "AB_POS", "MALE", "Z1");
        d4.setId(4L);
        d4.setAvailable(true);
        d4.setPaused(false);

        when(donorProfileRepository.findAll()).thenReturn(List.of(d1, d2, d3, d4));

        List<RankedMatchDto> matches = matchingService.rankDonors(10L, 5, false);

        // Donor 2 (cooldown) and Donor 4 (incompatible) MUST BE EXCLUDED!
        // Only Donor 1 and Donor 3 should be ranked!
        assertThat(matches).hasSize(2);

        // Donor 1 (Z1, distance = 0) must be ranked ahead of Donor 3 (Z2, distance > 0)
        assertThat(matches.get(0).donorId()).isEqualTo(1L);
        assertThat(matches.get(0).rankPosition()).isEqualTo(1);
        assertThat(matches.get(1).donorId()).isEqualTo(3L);
        assertThat(matches.get(1).rankPosition()).isEqualTo(2);

        // Verify score monotonicity: Donor 1 score >= Donor 3 score
        assertThat(matches.get(0).totalScore()).isGreaterThanOrEqualTo(matches.get(1).totalScore());

        // Verify explanations are present and traceable
        assertThat(matches.get(0).plainLanguageExplanation()).isNotBlank();
        assertThat(matches.get(0).factorContributions()).containsKey("PROXIMITY");
        assertThat(matches.get(0).factorContributions()).containsKey("RARITY");

        // Locked-spec S6 baseline: Responsiveness factor is NOT active (weight = 0)
        assertThat(matches.get(0).factorContributions()).doesNotContainKey("RESPONSIVENESS");
    }

    @Test
    @DisplayName("S7 Optional Extension: Responsiveness factor included when s7=true")
    void rankDonors_s7Option_includesResponsiveness() {
        when(bloodRequestRepository.findById(10L)).thenReturn(Optional.of(openRequest));
        when(bankRepository.findAll()).thenReturn(List.of(bank1, bank2));

        UserEntity u1 = new UserEntity("d1", "d1@test.com", "h", Role.DONOR);
        u1.setId(101L);
        DonorProfileEntity d1 = new DonorProfileEntity(u1, "O_POS", "MALE", "Z1");
        d1.setId(1L);
        d1.setAvailable(true);
        d1.setPaused(false);

        when(donorProfileRepository.findAll()).thenReturn(List.of(d1));

        List<RankedMatchDto> matches = matchingService.rankDonors(10L, 5, true);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).factorContributions()).containsKey("RESPONSIVENESS");
    }
}
