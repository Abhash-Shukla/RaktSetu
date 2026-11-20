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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    public AuthService(
        UserRepository userRepository,
        DonorProfileRepository donorProfileRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(AuthRequest request, String ipAddress) {
        UserEntity user = userRepository.findByUsername(request.username())
            .or(() -> userRepository.findByEmail(request.username()))
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(request.username(), "UNKNOWN", "LOGIN_FAILED", "USER", user.getId().toString(), "Bad credentials", ipAddress);
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole().name());
        auditService.log(user.getUsername(), user.getRole().name(), "LOGIN_SUCCESS", "USER", user.getId().toString(), "Successful login", ipAddress);

        return new AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole().name());
    }

    @Transactional
    public AuthResponse registerDonor(RegisterDonorRequest request, String ipAddress) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already in use: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        UserEntity user = new UserEntity(
            request.username(),
            request.email(),
            passwordEncoder.encode(request.password()),
            Role.DONOR
        );
        user = userRepository.save(user);

        DonorProfileEntity profile = new DonorProfileEntity();
        profile.setUser(user);
        profile.setBloodGroup(request.bloodGroup());
        profile.setSex(request.sex());
        profile.setHomeZone(request.homeZone());
        profile.setCurrentZone(request.homeZone());
        profile.setAvailable(true);
        profile.setPaused(false);
        profile = donorProfileRepository.save(profile);

        String profileIdStr = profile.getId() != null ? profile.getId().toString() : String.valueOf(user.getId());
        auditService.log(user.getUsername(), Role.DONOR.name(), "DONOR_REGISTERED", "DONOR_PROFILE", profileIdStr, "New donor registered", ipAddress);

        String token = tokenProvider.generateToken(user.getUsername(), Role.DONOR.name());
        return new AuthResponse(token, user.getUsername(), user.getEmail(), Role.DONOR.name());
    }
}
