package lifeline.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lifeline.api.dto.DonorDecisionDto;
import lifeline.api.dto.DonorProfileDto;
import lifeline.api.dto.NotificationResponseDto;
import lifeline.api.dto.UpdateDonorProfileDto;
import lifeline.api.service.DonorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/donor")
public class DonorController {

    private final DonorService donorService;

    public DonorController(DonorService donorService) {
        this.donorService = donorService;
    }

    @GetMapping("/profile")
    public ResponseEntity<DonorProfileDto> getProfile(Authentication auth) {
        return ResponseEntity.ok(donorService.getProfile(auth.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<DonorProfileDto> updateProfile(
        @RequestBody UpdateDonorProfileDto dto,
        Authentication auth,
        HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        DonorProfileDto updated = donorService.updateProfile(auth.getName(), dto, ip);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponseDto>> getNotifications(Authentication auth) {
        return ResponseEntity.ok(donorService.getNotifications(auth.getName()));
    }

    @PostMapping("/notifications/{matchLogId}/respond")
    public ResponseEntity<NotificationResponseDto> respondToNotification(
        @PathVariable("matchLogId") Long matchLogId,
        @Valid @RequestBody DonorDecisionDto decision,
        Authentication auth,
        HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        NotificationResponseDto response = donorService.respondToNotification(
            auth.getName(),
            matchLogId,
            decision.accept(),
            ip
        );
        return ResponseEntity.ok(response);
    }
}
