package lifeline.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lifeline.api.controller.*;
import lifeline.api.dto.*;
import lifeline.api.model.Role;
import lifeline.api.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
    AuthController.class,
    PublicIntakeController.class,
    BloodRequestController.class,
    DonorController.class,
    InventoryController.class,
    AdminController.class
})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthFilter.class})
class RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private BloodRequestService bloodRequestService;

    @MockBean
    private MatchingService matchingService;

    @MockBean
    private DonorService donorService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private lifeline.api.service.IntakeService intakeService;

    private BloodRequestResponseDto dummyResponse;

    @BeforeEach
    void setUp() {
        dummyResponse = new BloodRequestResponseDto(
            1L, "PAT-001", "O_POS", 2, 0, "CRITICAL", "Z1", "OPEN", "STAFF", Instant.now().plusSeconds(7200), Instant.now()
        );
    }

    // =========================================================================
    // 1. PUBLIC & AUTHENTICATION ENDPOINTS (Anonymous Access Allowed)
    // =========================================================================

    @Test
    @DisplayName("Public Intake: Anonymous user can submit a public blood request")
    void publicIntake_anonymousAllowed() throws Exception {
        CreateBloodRequestDto dto = new CreateBloodRequestDto("PAT-PUB", "A_POS", 2, "URGENT", "Z1", 6);
        when(bloodRequestService.createPublicRequest(any(), any())).thenReturn(
            new BloodRequestResponseDto(1L, "PAT-PUB", "A_POS", 2, 0, "URGENT", "Z1", "PENDING_REVIEW", "PUBLIC", Instant.now().plusSeconds(21600), Instant.now())
        );

        mockMvc.perform(post("/api/v1/public/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.source").value("PUBLIC"));
    }

    @Test
    @DisplayName("Public Intake: Anonymous user can parse free-text")
    void publicParseText_anonymousAllowed() throws Exception {
        lifeline.api.dto.ParseTextRequestDto dto = new lifeline.api.dto.ParseTextRequestDto("Need B+ blood at AIIMS");
        lifeline.api.dto.ParsedIntakeDto parsed = new lifeline.api.dto.ParsedIntakeDto(
            "B_POS", "URGENT", "AIIMS", 1, null, 0.9, "Need B+ blood at AIIMS",
            Map.of("bloodGroup", 0.95), List.of(), true, false, null
        );
        when(intakeService.parseIntake(any())).thenReturn(parsed);

        mockMvc.perform(post("/api/v1/public/requests/parse-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bloodGroup").value("B_POS"))
            .andExpect(jsonPath("$.isValid").value(true));
    }

    @Test
    @DisplayName("Public Intake: Anonymous user can submit free-text request into PENDING_REVIEW")
    void publicFreeText_anonymousCreatesPendingReview() throws Exception {
        lifeline.api.dto.PublicFreeTextIntakeDto dto = new lifeline.api.dto.PublicFreeTextIntakeDto("Urgent 2 units O+ at AIIMS", "9876543210");
        BloodRequestResponseDto parsedResp = new BloodRequestResponseDto(
            2L, "PARSED-1234", "O_POS", 2, 0, "URGENT", "AIIMS", "PENDING_REVIEW", "PARSED",
            Instant.now().plusSeconds(21600), Instant.now(), "9876543210", dto.text(), 0.92
        );
        when(intakeService.createPublicFreeTextRequest(any(), any())).thenReturn(parsedResp);

        mockMvc.perform(post("/api/v1/public/requests/free-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.source").value("PARSED"));
    }

    @Test
    @DisplayName("Auth: Anonymous user can login")
    void authLogin_anonymousAllowed() throws Exception {
        AuthRequest authReq = new AuthRequest("staff_central", "password123");
        when(authService.login(any(), any())).thenReturn(new AuthResponse("fake-jwt-token", "staff_central", "staff@test.com", "BANK_STAFF"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("fake-jwt-token"));
    }

    // =========================================================================
    // 2. DONOR ENDPOINTS RBAC MATRIX
    // =========================================================================

    @Test
    @DisplayName("Donor Profile: Anonymous user gets 401 Unauthorized")
    void donorProfile_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/donor/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Donor Profile: DONOR role gets 200 OK")
    void donorProfile_donorRole_returns200() throws Exception {
        DonorProfileDto profile = new DonorProfileDto(
            1L, "donor_user", "donor@test.com", "O_POS", "MALE", "Z1", null, true, false, "Z1", 0, 0, 0, 0
        );
        when(donorService.getProfile("donor_user")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/donor/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("donor_user"));
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Donor Profile: BANK_STAFF role gets 403 Forbidden")
    void donorProfile_bankStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donor/profile"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Donor Profile: ADMIN role gets 403 Forbidden")
    void donorProfile_admin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donor/profile"))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. STAFF & REQUESTS ENDPOINTS RBAC MATRIX
    // =========================================================================

    @Test
    @DisplayName("Requests: Anonymous user gets 401 Unauthorized")
    void requests_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Requests: DONOR role gets 403 Forbidden")
    void requests_donorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Requests: BANK_STAFF role gets 200 OK")
    void requests_bankStaffRole_returns200() throws Exception {
        when(bloodRequestService.getAllRequests()).thenReturn(List.of(dummyResponse));

        mockMvc.perform(get("/api/v1/requests"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Requests: ADMIN role gets 200 OK")
    void requests_adminRole_returns200() throws Exception {
        when(bloodRequestService.getAllRequests()).thenReturn(List.of(dummyResponse));

        mockMvc.perform(get("/api/v1/requests"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Matching: BANK_STAFF can trigger greedy Top-N matching")
    void matching_bankStaffRole_returnsRankedMatches() throws Exception {
        RankedMatchDto match = new RankedMatchDto(
            1, 4L, "O_POS", "Z1", 0.95, "Top factor: PROXIMITY", "PROXIMITY_DOMINANT", Map.of("PROXIMITY", 0.80, "RARITY", 0.15)
        );
        when(matchingService.rankDonors(any(), any(), any())).thenReturn(List.of(match));

        mockMvc.perform(get("/api/v1/requests/1/matches?topN=5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rankPosition").value(1))
            .andExpect(jsonPath("$[0].donorId").value(4))
            .andExpect(jsonPath("$[0].factorContributions.PROXIMITY").value(0.80));
    }

    @Test
    @DisplayName("Staff Parse Text: Anonymous user gets 401 Unauthorized")
    void staffParseText_anonymous_returns401() throws Exception {
        lifeline.api.dto.ParseTextRequestDto dto = new lifeline.api.dto.ParseTextRequestDto("Need B+ at AIIMS");
        mockMvc.perform(post("/api/v1/requests/parse-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Staff Parse Text: DONOR role gets 403 Forbidden")
    void staffParseText_donorRole_returns403() throws Exception {
        lifeline.api.dto.ParseTextRequestDto dto = new lifeline.api.dto.ParseTextRequestDto("Need B+ at AIIMS");
        mockMvc.perform(post("/api/v1/requests/parse-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Staff Parse Text: BANK_STAFF role gets 200 OK")
    void staffParseText_bankStaffRole_returns200() throws Exception {
        lifeline.api.dto.ParseTextRequestDto dto = new lifeline.api.dto.ParseTextRequestDto("Need B+ at AIIMS");
        lifeline.api.dto.ParsedIntakeDto parsed = new lifeline.api.dto.ParsedIntakeDto(
            "B_POS", "URGENT", "AIIMS", 1, null, 0.9, dto.text(), Map.of(), List.of(), true, false, null
        );
        when(intakeService.parseIntake(any())).thenReturn(parsed);

        mockMvc.perform(post("/api/v1/requests/parse-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bloodGroup").value("B_POS"));
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Staff Submit Parsed: BANK_STAFF creates request in PENDING_REVIEW")
    void staffSubmitParsed_bankStaffRole_createsPendingReview() throws Exception {
        lifeline.api.dto.ParseTextRequestDto dto = new lifeline.api.dto.ParseTextRequestDto("Need 2 units A+ at Safdarjung");
        BloodRequestResponseDto parsedResp = new BloodRequestResponseDto(
            3L, "STAFF-PARSED-1", "A_POS", 2, 0, "URGENT", "Safdarjung", "PENDING_REVIEW", "PARSED",
            Instant.now().plusSeconds(21600), Instant.now(), null, dto.text(), 0.95
        );
        when(intakeService.createStaffParsedRequest(any(), any(), any())).thenReturn(parsedResp);

        mockMvc.perform(post("/api/v1/requests/parsed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.source").value("PARSED"));
    }

    // =========================================================================
    // 4. INVENTORY ENDPOINTS RBAC MATRIX
    // =========================================================================

    @Test
    @DisplayName("Inventory: Anonymous user gets 401 Unauthorized")
    void inventory_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Inventory: DONOR role gets 403 Forbidden")
    void inventory_donorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/inventory"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Inventory: BANK_STAFF role gets 200 OK")
    void inventory_bankStaffRole_returns200() throws Exception {
        when(inventoryService.getAllInventories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/inventory"))
            .andExpect(status().isOk());
    }

    // =========================================================================
    // 5. ADMIN ENDPOINTS RBAC MATRIX
    // =========================================================================

    @Test
    @DisplayName("Admin Users: Anonymous user gets 401 Unauthorized")
    void adminUsers_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Admin Users: DONOR role gets 403 Forbidden")
    void adminUsers_donorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"BANK_STAFF"})
    @DisplayName("Admin Users: BANK_STAFF role gets 403 Forbidden")
    void adminUsers_bankStaffRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Admin Users: ADMIN role gets 200 OK")
    void adminUsers_adminRole_returns200() throws Exception {
        when(adminService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk());
    }
}
