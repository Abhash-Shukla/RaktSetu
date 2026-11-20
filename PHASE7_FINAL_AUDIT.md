# LifeLine (RaktSetu) — Phase 7: Independent Audit Report

**Date of Audit:** November 18, 2026  
**Auditor:** Independent System Verification Agent (Antigravity)  
**Scope:** Phase 7 Implementation & PostgreSQL Verification (Spring Boot 3.4.1, PostgreSQL 16 Alpine, Testcontainers 1.21.4, Flyway 10.x, JWT Authentication, RBAC, Greedy Top-N Matching Integration, Optimistic Locking, OpenAPI 3.0 Contract)  
**Final Verdict:** **COMPLETE** (Fully Verified & Passing)

---

## 1. PostgreSQL Integration & Testcontainers Resolution

### 1.1 Root Cause of Previous Abort & Upgrade Resolution
- **Root Cause:** Testcontainers 1.20.4 (inherited from Spring Boot 3.4.1 BOM) was incompatible with Docker Engine 29.9.3 running via Docker Desktop 4.99.0 due to Docker API version negotiation (Docker Engine 29 requires Docker API 1.44+; older Testcontainers versions failed environment detection with HTTP 400).
- **Upgrade Performed:**
  - Pinned `<testcontainers.version>1.21.4</testcontainers.version>` in the parent [`pom.xml`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/pom.xml).
  - Imported `org.testcontainers:testcontainers-bom:1.21.4` in `<dependencyManagement>` before the Spring Boot BOM, ensuring all Testcontainers modules (`testcontainers`, `postgresql`, `junit-jupiter`, `database-commons`) are synchronized on version 1.21.4.
  - No changes were made to application domain logic or Docker daemon settings.

### 1.2 Test Execution & Container Verification
- **Test Class:** [`lifeline-api/src/test/java/lifeline/api/integration/PostgreSqlTestcontainersIntegrationTest.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/test/java/lifeline/api/integration/PostgreSqlTestcontainersIntegrationTest.java)
- **Execution Command:** `mvn test -pl lifeline-api -Dtest=PostgreSqlTestcontainersIntegrationTest`
- **Result:** **PASSED** (Duration: 5.01s).
- **Verification Details:**
  1. **Docker Environment Detection:** Connected successfully via Windows local named pipe (`npipe:////./pipe/docker_engine`). Detected Docker Server Version: `29.9.3`, API Version: `1.56`.
  2. **PostgreSQL Container Startup:** Started `postgres:16-alpine` container (`PostgreSQL 16.16 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit`).
  3. **Flyway Migrations:** Applied migrations `V1__init_schema.sql` and `V2__seed_demo_data.sql` cleanly against the live PostgreSQL database. Both migrations executed successfully (`MigrateResult.success == true`, 2 migrations applied).
  4. **Schema Structure & Table Existence:** Verified the existence of all 8 core tables in PostgreSQL's `public` schema (`users`, `donor_profiles`, `blood_requests`, `banks`, `bank_inventory`, `engine_config_versions`, `match_logs`, `audit_log`).
  5. **Demo Seed Data:** Verified the presence of synthetic seed records (8 users, 3 blood banks, active engine configuration version 1).
  6. **Database CHECK Constraints:** Tested the `chk_units_available_non_negative` constraint on `bank_inventory`. Inserting negative units was rejected by PostgreSQL with a `SQLException` citing `chk_units_available_non_negative`.
  7. **Optimistic Locking Schema:** Verified that `@Version` columns (`version BIGINT NOT NULL DEFAULT 0`) exist on `donor_profiles`, `blood_requests`, and `bank_inventory`.

---

## 2. Security Audit

### 2.1 JWT Signature Validation & Expiration
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/security/JwtTokenProvider.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/security/JwtTokenProvider.java)
- **Signature Algorithm:** Lines 24 and 37 use HMAC-SHA256 (`Keys.hmacShaKeyFor`) with a 256-bit secret key.
- **Expiration:** Line 36 sets token expiration (`expiration(expiryDate)`), defaulting to 24 hours (86,400,000 ms).
- **Validation:** Lines 49–56 validate tokens via `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`. Expired, tampered, or malformed tokens throw `JwtException`, returning `false` and causing `JwtAuthFilter` (Line 32) to reject authentication.

### 2.2 BCrypt Password Hashing
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/security/SecurityConfig.java#L29-L31`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/security/SecurityConfig.java#L29-L31)
- **Implementation:** `passwordEncoder()` bean instantiates `BCryptPasswordEncoder()`.
- **Usage:** In [`AuthService.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/AuthService.java):
  - Registration (Line 66): `passwordEncoder.encode(request.password())` hashes passwords prior to database insertion.
  - Authentication (Line 46): `passwordEncoder.matches(request.password(), user.getPasswordHash())` verifies plaintext passwords against stored hashes. Plaintext passwords are never stored.

### 2.3 Role-Based Access Control (RBAC) Matrix
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/security/SecurityConfig.java#L51-L70`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/security/SecurityConfig.java#L51-L70)
- Endpoints are partitioned into distinct authorization layers:
  - Anonymous permitted: `/api/v1/auth/**`, `/api/v1/public/**`.
  - DONOR only: `/api/v1/donor/**` requires `ROLE_DONOR`.
  - BANK_STAFF and ADMIN: `/api/v1/requests/**`, `/api/v1/inventory/**` require `ROLE_BANK_STAFF` or `ROLE_ADMIN`.
  - ADMIN only: `/api/v1/admin/**` requires `ROLE_ADMIN`.
  - Catch-all: `.anyRequest().authenticated()`.
- Verified by 18 test cases in [`RbacSecurityTest.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/test/java/lifeline/api/security/RbacSecurityTest.java), confirming 401 for anonymous access to protected endpoints and 403 for unauthorized cross-role access.

### 2.4 Donor Self-Data Isolation
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/controller/DonorController.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/controller/DonorController.java) and [`DonorService.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/DonorService.java)
- In `DonorController.java` (Lines 27, 39, 44, 57), all methods extract the authenticated user from `Authentication auth` and pass `auth.getName()` directly to the service. There is no client-supplied donor ID in the path or body for profile access or notification listing.
- In `DonorService.java` (Lines 87–93), when responding to a notification, the service validates ownership:
  ```java
  if (!matchLog.getDonor().getId().equals(donor.getId())) {
      throw new AccessDeniedException("Match log does not belong to the authenticated donor.");
  }
  ```
  Verified by `DonorServiceTest.respondToNotification_wrongDonor_throwsAccessDenied()`.

### 2.5 Separation of BANK_STAFF and ADMIN Permissions
- `BANK_STAFF` cannot access administrative endpoints under `/api/v1/admin/**` (verified: returns HTTP 403 in `RbacSecurityTest.adminUsers_bankStaffRole_returns403`).
- `ADMIN` cannot access donor private endpoints under `/api/v1/donor/**` (verified: returns HTTP 403 in `RbacSecurityTest.donorProfile_admin_returns403`).
- `BANK_STAFF` has access to operational workflows (`/api/v1/requests/**`, `/api/v1/inventory/**`).

---

## 3. Public Intake Audit

### 3.1 Strict Status Assignment
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/service/BloodRequestService.java#L55-L58`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/BloodRequestService.java#L55-L58)
- In `createPublicRequest`:
  ```java
  request.setStatus(RequestStatus.PENDING_REVIEW);
  request.setSource(RequestSource.PUBLIC);
  ```
  Anonymous submissions are unconditionally forced into `PENDING_REVIEW`.

### 3.2 Public Gating Against Matching & Tier-0
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L77-L80`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L77-L80) and [`#L187-L193`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L187-L193)
- Tier-0 check guard:
  ```java
  if (requestEntity.getStatus() == RequestStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Request is in PENDING_REVIEW; staff review and approval required before matching.");
  }
  ```
- Greedy ranker guard:
  ```java
  if (requestEntity.getStatus() == RequestStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Request is in PENDING_REVIEW; staff review and approval required before matching.");
  }
  if (requestEntity.getStatus() != RequestStatus.OPEN) {
      throw new IllegalStateException("Cannot match request in status: " + requestEntity.getStatus());
  }
  ```
  Verified by `MatchingServiceTest.rankDonors_pendingReviewRequest_throwsIllegalStateException()` and `checkTier0_pendingReviewRequest_throwsIllegalStateException()`.

### 3.3 Authorization for Review and Approval
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/controller/BloodRequestController.java#L64-L75`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/controller/BloodRequestController.java#L64-L75)
- Endpoint `POST /api/v1/requests/{id}/review` requires `ROLE_BANK_STAFF` or `ROLE_ADMIN`.
- Anonymous users receive 401; donors receive 403. Only authorized staff can transition a request from `PENDING_REVIEW` to `OPEN`.

---

## 4. Matching Integration Audit

### 4.1 S6 Baseline Default & Responsiveness Inactive
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L201-L219`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L201-L219)
- Active factor initialization:
  ```java
  Set<String> activeFactors = new HashSet<>(Context.DEFAULT_ACTIVE_FACTORS);
  if (Boolean.TRUE.equals(includeResponsiveness)) {
      activeFactors.add(ResponsivenessFactor.NAME);
  }
  ```
- `Context.DEFAULT_ACTIVE_FACTORS` in `lifeline-core` is locked to `PROXIMITY` and `RARITY`.
- In `EngineConfig.defaults()`, responsiveness weight is `0.0` across all urgency tiers (`CRITICAL`: 0.85/0.15/0.0; `URGENT`: 0.65/0.35/0.0; `ROUTINE`: 0.40/0.60/0.0).
- In `BloodRequestController.java` (Line 104), `@RequestParam(name = "s7", defaultValue = "false") boolean s7` defaults to `false`. Production matching runs locked-spec S6 unless explicitly requested.

### 4.2 Proximity as a Scoring Factor (Not Hard Exclusion)
- **File Reference:** [`lifeline-api/src/test/java/lifeline/api/service/MatchingServiceTest.java#L190-L245`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/test/java/lifeline/api/service/MatchingServiceTest.java#L190-L245)
- In `MatchingServiceTest.rankDonors_lockedS6Baseline_enforcesCooldownAndRanksByProximity`:
  - Donor 1 is in zone `Z1` (same as hospital, distance = 0 km).
  - Donor 3 is in zone `Z2` (distance > 0 km).
  - Both donors pass candidate filtering and are ranked.
  - Donor 3 is assigned a lower proximity score and ranked below Donor 1, but is **never excluded**.
  - Confirmed: Proximity is purely a ranking term.

### 4.3 Compatibility and Cooldown Hard Exclusions
- **File Reference:** [`lifeline-core/src/main/java/lifeline/core/filter/CandidateFilter.java#L120-L160`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-core/src/main/java/lifeline/core/filter/CandidateFilter.java#L120-L160)
- In `MatchingService.java` (Line 198), `CandidateFilter.filter(snapshots, coreRequest)` is invoked before ranking:
  - Incompatible ABO/Rh donors receive `ExclusionReason.INCOMPATIBLE_BLOOD_GROUP` and are pruned from the pool.
  - Donors inside the cooldown window (last donation within 90 days for males or 120 days for females) receive `ExclusionReason.COOLDOWN_ACTIVE` and are pruned from the pool.
  - Verified by `MatchingServiceTest`: Donor 2 (donated 30 days prior) and Donor 4 (AB+ for O+ recipient) are excluded before ranking.

### 4.4 Non-Duplication of Core Algorithms
- The API introduces zero duplicate scoring, filtering, or routing logic. It instantiates:
  - `lifeline.core.filter.CandidateFilter`
  - `lifeline.core.rank.Ranker`
  - `lifeline.core.inventory.Tier0InventoryChecker`
  - `lifeline.core.explain.ExplanationGenerator`
  - `lifeline.core.compatibility.CompatibilityTable`
  - `lifeline.core.domain.ZoneMap`

---

## 5. Tier-0 and Concurrency Audit

### 5.1 Transaction Boundaries & Non-Negative Stock
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L118-L175`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L118-L175)
- Method `fulfillViaTier0` is annotated with Spring `@Transactional`.
- Inventory consumption logic:
  ```java
  int consume = Math.min(remaining, inv.getUnitsAvailable());
  inv.setUnitsAvailable(inv.getUnitsAvailable() - consume);
  ```
  Since `consume <= inv.getUnitsAvailable()`, `inv.getUnitsAvailable() - consume >= 0` is guaranteed in application logic.
- Schema safety: `V1__init_schema.sql` (Line 93) defines:
  ```sql
  CONSTRAINT chk_units_available_non_negative CHECK (units_available >= 0)
  ```
  A database check constraint prevents negative stock at the engine level, verified against actual PostgreSQL 16 in `PostgreSqlTestcontainersIntegrationTest`.

### 5.2 Optimistic Locking & HTTP 409 Handling
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/model/BankInventoryEntity.java#L26-L28`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/model/BankInventoryEntity.java#L26-L28)
- Column definition:
  ```java
  @Version
  @Column(nullable = false)
  private Long version = 0L;
  ```
  Also applied to `DonorProfileEntity` (Line 55) and `BloodRequestEntity` (Line 53).
- When concurrent requests modify the same inventory row, Hibernate emits version checks in the `UPDATE` statement. Conflicting updates fail with `OptimisticLockException` or `ObjectOptimisticLockingFailureException`.
- In [`GlobalExceptionHandler.java#L29-L32`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/exception/GlobalExceptionHandler.java#L29-L32):
  ```java
  @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
  public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception ex, HttpServletRequest request) {
      return buildErrorResponse(HttpStatus.CONFLICT, "Concurrent modification conflict...", request.getRequestURI());
  }
  ```
  Conflicts are translated into HTTP 409 Conflict.

---

## 6. Explainability and Auditability Audit

### 6.1 Match Log Traceability
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L296-L318`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/service/MatchingService.java#L296-L318)
- When a donor is notified (`notifyDonor`), `MatchLogEntity` records:
  - `request`: ID of target request.
  - `donor`: ID of notified donor.
  - `stepNumber`: Sequential outreach index.
  - `rankPosition`: 1-based rank position.
  - `totalScore`: Exact score produced by the ranker.
  - `factorBreakdown`: Serialized JSON of individual factor contributions (e.g. `{"PROXIMITY": 0.80, "RARITY": 0.15}`).
  - `explanationText`: Human-readable summary generated by `ExplanationGenerator`.
  - `reasonCode`: Traceability code (e.g. `PROXIMITY_DOMINANT`).
  - `outcome`: Initialized to `NOTIFIED`.
  - `configVersion`: Foreign key to `EngineConfigVersionEntity`.

### 6.2 Configuration Version Reconstruction
- **File Reference:** [`lifeline-api/src/main/java/lifeline/api/model/EngineConfigVersionEntity.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-api/src/main/java/lifeline/api/model/EngineConfigVersionEntity.java)
- Configurations are never updated in-place. Line 47 of `AdminService.java` deactivates the prior version and persists a new immutable version record containing `configJson`.
- Because each `MatchLogEntity` references its corresponding `EngineConfigVersionEntity`, the exact weights, parameters, and rules used for any historical matching decision can be reconstructed at any future date.

---

## 7. Architecture & Phase Boundaries Audit

### 7.1 Module Dependencies
- Inspection of `lifeline-api/pom.xml` confirms zero dependencies on `lifeline-sim` or `lifeline-eval`.
- Grep of all `.java` files under `lifeline-api/src/main/java/` for `lifeline.sim` and `lifeline.eval` returned zero occurrences.

### 7.2 ArchUnit Rule Verification
- **File Reference:** [`lifeline-arch-tests/src/test/java/lifeline/arch/ModuleBoundaryTest.java`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-arch-tests/src/test/java/lifeline/arch/ModuleBoundaryTest.java)
- Rules:
  1. `coreAndApiMustNotDependOnSimOrEval`: Verified by ArchUnit (0 violations).
  2. `coreMustNotDependOnSpring`: Verified by ArchUnit (0 violations).
- Test execution output:
  ```
  [INFO] Running lifeline.arch.ModuleBoundaryTest
  [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.146 s -- in lifeline.arch.ModuleBoundaryTest
  ```

### 7.3 Boundary Against Phases 8 and 9
- **Phase 8 (NLP Intake):** No NLP parsing, tokenization, or unstructured text extraction logic has been introduced. The string `"NLP"` appears only in package documentation comments.
- **Phase 9 (React Frontend):** The `frontend/` directory contains exclusively the existing `.gitkeep` file. No React components, package scripts, or UI views have been written.

---

## 8. Final Build & Test Counts

The full Maven reactor verification (`mvn -q verify`) succeeded with exit code 0 (`BUILD SUCCESS`).

### Detailed Module-by-Module Test Statistics

| Module | Test File Count | Tests Run | Passed | Failed | Errors | Skipped | Blocked | Status |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `lifeline-core` | 11 | 37 | 37 | 0 | 0 | 0 | 0 | **PASSED** |
| `lifeline-sim` | 7 | 27 | 27 | 0 | 0 | 0 | 0 | **PASSED** |
| `lifeline-eval` | 27 | 99 | 99 | 0 | 0 | 0 | 0 | **PASSED** |
| `lifeline-api` | 7 | 40 | 40 | 0 | 0 | 0 | 0 | **PASSED** |
| `lifeline-arch-tests` | 1 | 2 | 2 | 0 | 0 | 0 | 0 | **PASSED** |
| **Total Reactor** | **53** | **205** | **205** | **0** | **0** | **0** | **0** | **BUILD SUCCESS** |

*(Note: In `lifeline-api`, all 40 tests passed, including `PostgreSqlTestcontainersIntegrationTest` against PostgreSQL 16 Alpine via Testcontainers 1.21.4. Zero tests were skipped, aborted, or blocked).*

---

## 9. Final Verdict

### **Verdict: COMPLETE**

- All Phase 7 functional requirements, database schema migrations, JWT security, RBAC rules, greedy Top-N matching integrations, explainability logs, optimistic locking safeguards, OpenAPI 3.0 contracts, and **live PostgreSQL 16 Testcontainers integration tests** are fully implemented, executed, and verified.
- The build passed with **205 tests run, 205 passed, 0 failures, 0 errors, 0 skipped, and 0 blocked** across the Maven reactor.

---
*Phase 7 is officially complete. Awaiting human instructions before proceeding to Phase 8.*
