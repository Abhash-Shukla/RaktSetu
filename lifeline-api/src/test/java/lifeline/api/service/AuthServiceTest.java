package lifeline.api.service;

import lifeline.api.dto.AuthRequest;
import lifeline.api.dto.AuthResponse;
import lifeline.api.dto.RegisterDonorRequest;
import lifeline.api.model.DonorProfileEntity;
import lifeline.api.model.Role;
import lifeline.api.model.UserEntity;
import lifeline.api.repository.DonorProfileRepository;
import lifeline.api.repository.UserRepository;
import lifeline.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DonorProfileRepository donorProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            donorProfileRepository,
            passwordEncoder,
            tokenProvider,
            auditService
        );
    }

    @Test
    @DisplayName("registerDonor persists user and donor profile, logs audit event, and returns JWT")
    void registerDonorSuccess() {
        RegisterDonorRequest req = new RegisterDonorRequest(
            "new_donor",
            "new_donor@test.com",
            "ValidPass123!",
            "O_POS",
            "MALE",
            "Z01"
        );

        when(userRepository.existsByUsername("new_donor")).thenReturn(false);
        when(userRepository.existsByEmail("new_donor@test.com")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass123!")).thenReturn("hashed_pass");

        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(101L);
            return u;
        });

        when(donorProfileRepository.save(any(DonorProfileEntity.class))).thenAnswer(inv -> {
            DonorProfileEntity p = inv.getArgument(0);
            p.setId(202L);
            return p;
        });

        when(tokenProvider.generateToken("new_donor", "DONOR")).thenReturn("mocked-jwt-token");

        AuthResponse resp = authService.registerDonor(req, "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.token()).isEqualTo("mocked-jwt-token");
        assertThat(resp.username()).isEqualTo("new_donor");
        assertThat(resp.role()).isEqualTo("DONOR");

        verify(auditService).log(
            eq("new_donor"),
            eq("DONOR"),
            eq("DONOR_REGISTERED"),
            eq("DONOR_PROFILE"),
            eq("202"),
            eq("New donor registered"),
            eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("registerDonor rejects duplicate username")
    void registerDonorDuplicateUsername() {
        RegisterDonorRequest req = new RegisterDonorRequest(
            "existing_user",
            "test@test.com",
            "ValidPass123!",
            "O_POS",
            "MALE",
            "Z01"
        );

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerDonor(req, "127.0.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already in use");
    }
}
