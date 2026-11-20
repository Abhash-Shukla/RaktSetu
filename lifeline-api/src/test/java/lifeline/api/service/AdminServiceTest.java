package lifeline.api.service;

import lifeline.api.dto.EngineConfigDto;
import lifeline.api.dto.UserSummaryDto;
import lifeline.api.model.*;
import lifeline.api.repository.AuditLogRepository;
import lifeline.api.repository.EngineConfigVersionRepository;
import lifeline.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EngineConfigVersionRepository engineConfigVersionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditService auditService;

    private AdminService adminService;
    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
            userRepository,
            engineConfigVersionRepository,
            auditLogRepository,
            auditService
        );

        adminUser = new UserEntity("admin", "admin@test.com", "hash", Role.ADMIN);
        adminUser.setId(1L);
    }

    @Test
    @DisplayName("User Management: Updates user status and role")
    void updateUserStatusAndRole() {
        UserEntity target = new UserEntity("staff_user", "staff@test.com", "hash", Role.BANK_STAFF);
        target.setId(2L);
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryDto updatedStatus = adminService.updateUserStatus(2L, UserStatus.SUSPENDED, "admin", "127.0.0.1");
        assertThat(updatedStatus.status()).isEqualTo("SUSPENDED");

        UserSummaryDto updatedRole = adminService.updateUserRole(2L, Role.ADMIN, "admin", "127.0.0.1");
        assertThat(updatedRole.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Config Versioning: Creates new immutable version and deactivates prior")
    void createConfigVersion_incrementsVersionAndDeactivatesPrevious() {
        EngineConfigVersionEntity v1 = new EngineConfigVersionEntity(1, "V1", "{}", adminUser, true);
        v1.setId(10L);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(engineConfigVersionRepository.findAllByOrderByVersionNumberDesc()).thenReturn(List.of(v1));
        when(engineConfigVersionRepository.findByActiveTrue()).thenReturn(Optional.of(v1));
        when(engineConfigVersionRepository.save(any(EngineConfigVersionEntity.class))).thenAnswer(i -> {
            EngineConfigVersionEntity e = i.getArgument(0);
            e.setId(11L);
            return e;
        });

        EngineConfigDto created = adminService.createConfigVersion("Version 2 test", "{\"weights\": {}}", "admin", "127.0.0.1");

        assertThat(created.versionNumber()).isEqualTo(2);
        assertThat(created.isActive()).isTrue();
        assertThat(v1.isActive()).isFalse(); // Previous version deactivated
        verify(engineConfigVersionRepository, times(2)).save(any(EngineConfigVersionEntity.class));
    }
}
