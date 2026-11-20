package lifeline.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lifeline.api.dto.*;
import lifeline.api.model.MatchOutcome;
import lifeline.api.service.BloodRequestService;
import lifeline.api.service.IntakeService;
import lifeline.api.service.MatchingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requests")
public class BloodRequestController {

    private final BloodRequestService bloodRequestService;
    private final MatchingService matchingService;
    private final IntakeService intakeService;

    public BloodRequestController(
        BloodRequestService bloodRequestService,
        MatchingService matchingService,
        IntakeService intakeService
    ) {
        this.bloodRequestService = bloodRequestService;
        this.matchingService = matchingService;
        this.intakeService = intakeService;
    }

    /**
     * Staff/Admin creates an urgent or routine blood request via structured form.
     * Enters OPEN status directly.
     */
    @PostMapping
    public ResponseEntity<BloodRequestResponseDto> createStaffRequest(
        @Valid @RequestBody CreateBloodRequestDto dto,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        BloodRequestResponseDto response = bloodRequestService.createStaffRequest(dto, username, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Staff pastes a forwarded informal blood-request message (Use case 1).
     * Returns extracted structured fields with confidence flags.
     * Staff inspects and edits fields before final submission/approval.
     */
    @PostMapping("/parse-text")
    public ResponseEntity<ParsedIntakeDto> parseForwardedText(
        @Valid @RequestBody ParseTextRequestDto dto
    ) {
        ParsedIntakeDto parsed = intakeService.parseIntake(dto.text());
        return ResponseEntity.ok(parsed);
    }

    /**
     * Staff submits a parsed blood-request message.
     * HARD RULE: All parsed requests enter PENDING_REVIEW; staff must review and approve.
     */
    @PostMapping("/parsed")
    public ResponseEntity<BloodRequestResponseDto> submitStaffParsedRequest(
        @Valid @RequestBody ParseTextRequestDto dto,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        BloodRequestResponseDto response = intakeService.createStaffParsedRequest(dto, username, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BloodRequestResponseDto>> getAllRequests() {
        return ResponseEntity.ok(bloodRequestService.getAllRequests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BloodRequestResponseDto> getRequestById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(bloodRequestService.getRequestById(id));
    }

    @GetMapping("/queue/pending-review")
    public ResponseEntity<List<BloodRequestResponseDto>> getReviewQueue() {
        return ResponseEntity.ok(bloodRequestService.getReviewQueue());
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<BloodRequestResponseDto> reviewRequest(
        @PathVariable("id") Long id,
        @Valid @RequestBody ReviewRequestDto dto,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        BloodRequestResponseDto response = bloodRequestService.reviewRequest(id, dto, username, ip);
        return ResponseEntity.ok(response);
    }

    /**
     * Tier-0 Check: Checks nearby blood bank inventories before donor mobilisation.
     */
    @GetMapping("/{id}/tier0-check")
    public ResponseEntity<Tier0CheckDto> checkTier0(@PathVariable("id") Long id) {
        Tier0CheckDto result = matchingService.checkTier0(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Tier-0 Fulfill: Allocates compatible units from selected bank inventory.
     */
    @PostMapping("/{id}/tier0-fulfill/{bankId}")
    public ResponseEntity<BloodRequestResponseDto> fulfillViaTier0(
        @PathVariable("id") Long id,
        @PathVariable("bankId") Long bankId,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        BloodRequestResponseDto response = matchingService.fulfillViaTier0(id, bankId, username, ip);
        return ResponseEntity.ok(response);
    }

    /**
     * Greedy Top-N donor ranker with multi-factor scoring and deterministic explanations.
     * Default: S6 locked-spec baseline (Proximity + Rarity, responsiveness weight = 0).
     * Optional: s7=true enables responsiveness factor.
     */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<RankedMatchDto>> getRankedMatches(
        @PathVariable("id") Long id,
        @RequestParam(name = "topN", defaultValue = "5") int topN,
        @RequestParam(name = "s7", defaultValue = "false") boolean s7
    ) {
        List<RankedMatchDto> matches = matchingService.rankDonors(id, topN, s7);
        return ResponseEntity.ok(matches);
    }

    /**
     * Mobilises a candidate donor from the ranked list.
     */
    @PostMapping("/{id}/notify-donor/{donorId}")
    public ResponseEntity<NotificationResponseDto> notifyDonor(
        @PathVariable("id") Long id,
        @PathVariable("donorId") Long donorId,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        NotificationResponseDto response = matchingService.notifyDonor(id, donorId, username, ip);
        return ResponseEntity.ok(response);
    }

    /**
     * Records donor response on behalf of donor or through staff workflow.
     */
    @PostMapping("/match-logs/{matchLogId}/record-response")
    public ResponseEntity<NotificationResponseDto> recordResponse(
        @PathVariable("matchLogId") Long matchLogId,
        @RequestParam("outcome") MatchOutcome outcome,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        NotificationResponseDto response = matchingService.recordResponse(matchLogId, outcome, username, ip);
        return ResponseEntity.ok(response);
    }
}
