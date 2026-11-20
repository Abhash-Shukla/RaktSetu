package lifeline.api.service;

import lifeline.api.dto.AuditLogDto;
import lifeline.api.dto.EngineConfigDto;
import lifeline.api.dto.UserSummaryDto;
import lifeline.api.model.*;
import lifeline.api.repository.AuditLogRepository;
import lifeline.api.repository.EngineConfigVersionRepository;
import lifeline.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EngineConfigVersionRepository engineConfigVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AdminService(
        UserRepository userRepository,
        EngineConfigVersionRepository engineConfigVersionRepository,
        AuditLogRepository auditLogRepository,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.engineConfigVersionRepository = engineConfigVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getAllUsers() {
        return userRepository.findAll()
            .stream()
            .map(this::mapUserToDto)
            .toList();
    }

    @Transactional
    public UserSummaryDto updateUserStatus(Long userId, UserStatus newStatus, String adminUsername, String ipAddress) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(newStatus);
        user = userRepository.save(user);

        auditService.log(adminUsername, "ADMIN", "USER_STATUS_UPDATED", "USER",
            user.getId().toString(), String.format("Status changed from %s to %s", oldStatus, newStatus), ipAddress);

        return mapUserToDto(user);
    }

    @Transactional
    public UserSummaryDto updateUserRole(Long userId, Role newRole, String adminUsername, String ipAddress) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        Role oldRole = user.getRole();
        user.setRole(newRole);
        user = userRepository.save(user);

        auditService.log(adminUsername, "ADMIN", "USER_ROLE_UPDATED", "USER",
            user.getId().toString(), String.format("Role changed from %s to %s", oldRole, newRole), ipAddress);

        return mapUserToDto(user);
    }

    @Transactional(readOnly = true)
    public List<EngineConfigDto> getAllConfigVersions() {
        return engineConfigVersionRepository.findAllByOrderByVersionNumberDesc()
            .stream()
            .map(this::mapConfigToDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public EngineConfigDto getActiveConfigVersion() {
        EngineConfigVersionEntity entity = engineConfigVersionRepository.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("No active engine configuration version found."));
        return mapConfigToDto(entity);
    }

    @Transactional
    public EngineConfigDto createConfigVersion(
        String description,
        String configJson,
        String adminUsername,
        String ipAddress
    ) {
        UserEntity admin = userRepository.findByUsername(adminUsername)
            .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminUsername));

        int nextVersion = engineConfigVersionRepository.findAllByOrderByVersionNumberDesc()
            .stream()
            .mapToInt(EngineConfigVersionEntity::getVersionNumber)
            .max()
            .orElse(0) + 1;

        // Deactivate existing active versions
        engineConfigVersionRepository.findByActiveTrue().ifPresent(curr -> {
            curr.setActive(false);
            engineConfigVersionRepository.save(curr);
        });

        EngineConfigVersionEntity newVersion = new EngineConfigVersionEntity(
            nextVersion,
            description != null ? description : "Configuration Version " + nextVersion,
            configJson,
            admin,
            true
        );
        newVersion = engineConfigVersionRepository.save(newVersion);

        auditService.log(adminUsername, "ADMIN", "CONFIG_VERSION_CREATED", "ENGINE_CONFIG_VERSION",
            newVersion.getId().toString(), "Created and activated config version #" + nextVersion, ipAddress);

        return mapConfigToDto(newVersion);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> getAuditLogs(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .limit(limit > 0 ? limit : 100)
            .map(this::mapAuditToDto)
            .toList();
    }

    private UserSummaryDto mapUserToDto(UserEntity entity) {
        return new UserSummaryDto(
            entity.getId(),
            entity.getUsername(),
            entity.getEmail(),
            entity.getRole().name(),
            entity.getStatus().name(),
            entity.getCreatedAt()
        );
    }

    private EngineConfigDto mapConfigToDto(EngineConfigVersionEntity entity) {
        return new EngineConfigDto(
            entity.getVersionNumber(),
            entity.getDescription(),
            entity.getConfigJson(),
            entity.getAuthor() != null ? entity.getAuthor().getUsername() : "SYSTEM",
            entity.isActive(),
            entity.getCreatedAt()
        );
    }

    private AuditLogDto mapAuditToDto(AuditLogEntity entity) {
        return new AuditLogDto(
            entity.getId(),
            entity.getActorUsername(),
            entity.getActorRole(),
            entity.getAction(),
            entity.getEntityType(),
            entity.getEntityId(),
            entity.getDetails(),
            entity.getIpAddress(),
            entity.getCreatedAt()
        );
    }
}
