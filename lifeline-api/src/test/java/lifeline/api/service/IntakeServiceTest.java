package lifeline.api.service;

import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.ParseTextRequestDto;
import lifeline.api.dto.ParsedIntakeDto;
import lifeline.api.dto.PublicFreeTextIntakeDto;
import lifeline.api.exception.InvalidIntakeException;
import lifeline.api.model.*;
import lifeline.api.nlp.IntakeParser;
import lifeline.api.repository.BloodRequestRepository;
import lifeline.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntakeServiceTest {

    private IntakeParser intakeParser;
    private BloodRequestRepository bloodRequestRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private BloodRequestService bloodRequestService;
    private IntakeService intakeService;

    private final List<BloodRequestEntity> savedEntities = new ArrayList<>();
    private final List<String> auditLogs = new ArrayList<>();
    private final AtomicReference<ParsedIntakeDto> parserResult = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        savedEntities.clear();
        auditLogs.clear();

        intakeParser = rawText -> parserResult.get();

        bloodRequestRepository = (BloodRequestRepository) Proxy.newProxyInstance(
            BloodRequestRepository.class.getClassLoader(),
            new Class<?>[]{BloodRequestRepository.class},
            (proxy, method, args) -> {
                if ("save".equals(method.getName())) {
                    BloodRequestEntity entity = (BloodRequestEntity) args[0];
                    entity.setId((long) (savedEntities.size() + 1));
                    savedEntities.add(entity);
                    return entity;
                }
                return null;
            }
        );

        userRepository = (UserRepository) Proxy.newProxyInstance(
            UserRepository.class.getClassLoader(),
            new Class<?>[]{UserRepository.class},
            (proxy, method, args) -> {
                if ("findByUsername".equals(method.getName())) {
                    String username = (String) args[0];
                    return Optional.of(new UserEntity(username, "hash", username + "@test.com", Role.BANK_STAFF));
                }
                return Optional.empty();
            }
        );

        lifeline.api.repository.AuditLogRepository auditLogRepository = (lifeline.api.repository.AuditLogRepository) Proxy.newProxyInstance(
            lifeline.api.repository.AuditLogRepository.class.getClassLoader(),
            new Class<?>[]{lifeline.api.repository.AuditLogRepository.class},
            (proxy, method, args) -> {
                if ("save".equals(method.getName())) {
                    AuditLogEntity logEntity = (AuditLogEntity) args[0];
                    auditLogs.add(logEntity.getActorUsername() + ":" + logEntity.getAction());
                    return logEntity;
                }
                return null;
            }
        );

        auditService = new AuditService(auditLogRepository);

        bloodRequestService = new BloodRequestService(bloodRequestRepository, userRepository, auditService, 100);

        intakeService = new IntakeService(
            intakeParser,
            bloodRequestRepository,
            userRepository,
            auditService,
            bloodRequestService,
            10 // Rate limit 10/min
        );
    }

    @Test
    @DisplayName("Public Free-Text: Valid message enters PENDING_REVIEW and preserves unparsed text")
    void publicFreeText_validMessage_entersPendingReview() {
        PublicFreeTextIntakeDto dto = new PublicFreeTextIntakeDto("Urgent need 2 units of B+ at AIIMS 110029", "9876543210");
        ParsedIntakeDto parsed = new ParsedIntakeDto(
            "B_POS", "URGENT", "110029", 2, "9876543210", 0.92, dto.text(),
            Map.of("bloodGroup", 0.95), List.of(), true, false, null
        );
        parserResult.set(parsed);

        BloodRequestResponseDto response = intakeService.createPublicFreeTextRequest(dto, "127.0.0.1");

        assertThat(savedEntities).hasSize(1);
        BloodRequestEntity captured = savedEntities.get(0);

        assertThat(captured.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
        assertThat(captured.getSource()).isEqualTo(RequestSource.PARSED);
        assertThat(captured.getBloodGroup()).isEqualTo("B_POS");
        assertThat(captured.getUnitsRequested()).isEqualTo(2);
        assertThat(captured.getZone()).isEqualTo("110029");
        assertThat(captured.getContactNumber()).isEqualTo("9876543210");
        assertThat(captured.getUnparsedText()).isEqualTo(dto.text());
        assertThat(captured.getExtractionConfidence()).isEqualTo(0.92);

        // Verify response DTO
        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.source()).isEqualTo("PARSED");
        assertThat(response.unparsedText()).isEqualTo(dto.text());
        assertThat(response.contactNumber()).isEqualTo("9876543210");

        // Verify audit logging
        assertThat(auditLogs).contains("PUBLIC_ANONYMOUS:PUBLIC_FREE_TEXT_QUEUED");
    }

    @Test
    @DisplayName("Public Free-Text: Unparseable text throws InvalidIntakeException with manual fallback recommendation")
    void publicFreeText_unparseable_throwsInvalidIntakeException() {
        PublicFreeTextIntakeDto dto = new PublicFreeTextIntakeDto("Hello how are you doing", null);
        ParsedIntakeDto fallback = new ParsedIntakeDto(
            null, null, null, null, null, 0.0, dto.text(),
            Map.of(), List.of("INVALID_BLOOD_GROUP", "MISSING_ZONE"), false, true, "Fallback to manual form."
        );
        parserResult.set(fallback);

        assertThatThrownBy(() -> intakeService.createPublicFreeTextRequest(dto, "127.0.0.1"))
            .isInstanceOf(InvalidIntakeException.class)
            .hasMessageContaining("Fallback to manual form")
            .satisfies(ex -> {
                InvalidIntakeException intakeEx = (InvalidIntakeException) ex;
                assertThat(intakeEx.getFallbackUrl()).isEqualTo("/api/v1/public/requests");
                assertThat(intakeEx.getFlags()).contains("INVALID_BLOOD_GROUP");
            });

        assertThat(savedEntities).isEmpty();
    }

    @Test
    @DisplayName("Staff Forwarded Text: Pasted text creates request strictly in PENDING_REVIEW status")
    void staffParsed_createsPendingReviewRequest() {
        ParseTextRequestDto dto = new ParseTextRequestDto("Forwarded: Need 1 unit AB+ at Safdarjung hosp ph 9988776655");
        ParsedIntakeDto parsed = new ParsedIntakeDto(
            "AB_POS", "URGENT", "Safdarjung", 1, "9988776655", 0.90, dto.text(),
            Map.of("bloodGroup", 0.95), List.of(), true, false, null
        );
        parserResult.set(parsed);

        BloodRequestResponseDto response = intakeService.createStaffParsedRequest(dto, "staff_user", "10.0.0.1");

        assertThat(savedEntities).hasSize(1);
        BloodRequestEntity captured = savedEntities.get(0);

        assertThat(captured.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
        assertThat(captured.getSource()).isEqualTo(RequestSource.PARSED);
        assertThat(captured.getBloodGroup()).isEqualTo("AB_POS");
        assertThat(captured.getUnitsRequested()).isEqualTo(1);
        assertThat(captured.getZone()).isEqualTo("Safdarjung");
        assertThat(response.status()).isEqualTo("PENDING_REVIEW");

        assertThat(auditLogs).contains("staff_user:STAFF_FREE_TEXT_QUEUED");
    }

    @Test
    @DisplayName("Rate Limiting: Exceeding sliding rate limit throws IllegalStateException")
    void rateLimiterEnforcesLimit() {
        PublicFreeTextIntakeDto dto = new PublicFreeTextIntakeDto("Need 2 units A+ at AIIMS", null);
        ParsedIntakeDto parsed = new ParsedIntakeDto(
            "A_POS", "URGENT", "AIIMS", 2, null, 0.9, dto.text(),
            Map.of(), List.of(), true, false, null
        );
        parserResult.set(parsed);

        // Exhaust limit of 10
        for (int i = 0; i < 10; i++) {
            intakeService.createPublicFreeTextRequest(dto, "192.168.1.100");
        }

        // 11th request must fail with rate limit error
        assertThatThrownBy(() -> intakeService.createPublicFreeTextRequest(dto, "192.168.1.100"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Rate limit exceeded");
    }
}
