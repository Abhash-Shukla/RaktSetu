package lifeline.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lifeline.api.dto.AuditLogDto;
import lifeline.api.dto.EngineConfigDto;
import lifeline.api.dto.UserSummaryDto;
import lifeline.api.model.Role;
import lifeline.api.model.UserStatus;
import lifeline.api.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserSummaryDto> updateUserStatus(
        @PathVariable("id") Long id,
        @RequestParam("status") UserStatus status,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(adminService.updateUserStatus(id, status, username, ip));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserSummaryDto> updateUserRole(
        @PathVariable("id") Long id,
        @RequestParam("role") Role role,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(adminService.updateUserRole(id, role, username, ip));
    }

    @GetMapping("/config")
    public ResponseEntity<List<EngineConfigDto>> getAllConfigVersions() {
        return ResponseEntity.ok(adminService.getAllConfigVersions());
    }

    @GetMapping("/config/active")
    public ResponseEntity<EngineConfigDto> getActiveConfigVersion() {
        return ResponseEntity.ok(adminService.getActiveConfigVersion());
    }

    @PostMapping("/config")
    public ResponseEntity<EngineConfigDto> createConfigVersion(
        @RequestBody EngineConfigDto dto,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        EngineConfigDto created = adminService.createConfigVersion(
            dto.description(),
            dto.configJson(),
            username,
            ip
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogDto>> getAuditLogs(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(adminService.getAuditLogs(limit));
    }
}
