# LifeLine (RaktSetu): Business and Software Requirements Specification

An explainable, urgency-aware blood donor discovery and ranking platform

Version 1.1 | Data: synthetic only

## 1. Document control

| Item | Detail |
|---|---|
| Project | LifeLine / RaktSetu: AI-assisted, explainable blood donor discovery and ranking |
| Type | 3rd-year B.Tech CSE mini project and IEEE student-conference paper (resubmission) |
| Owners | Backend, auth, matching engine: Abhimanyu. React frontend: Teammate A. NLP intake and paper support: Teammate B |
| Data policy | Synthetic data only. No real donor, patient or hospital data is collected or stored. |
| Basis | Locked decisions: greedy Top-N selection, DONOR / BANK_STAFF / ADMIN roles, public intake into a review queue, soft configurable rarity weighting, NLP for two use cases, cooldown as a hard exclusion (90 days men / 120 days women). Items marked PROPOSED need team approval. |
| Keywords | SHALL = Must, SHOULD = Should, MAY = Could (MoSCoW) |

## 2. Executive summary

When a hospital or family needs blood urgently, the usual process is manual: phone calls, forwarded WhatsApp messages and unorganised donor lists. Nobody can tell quickly who is compatible, nearby, eligible to donate and worth contacting first, and the choice is never explained.

LifeLine is a web platform where blood bank staff submit a request (structured form, pasted informal message, or a public intake form reviewed by staff). The system first checks simulated bank inventory (Tier 0). If stock is insufficient it finds compatible, eligible donors, ranks them with a transparent weighted score, and shows a plain-language reason for every rank. If a donor declines or does not respond, the system automatically surfaces the next-best match.

The four differentiators are: (1) a per-match explanation traceable to the factors that produced it, (2) LLM-assisted free-text intake with mandatory human review, (3) rarity weighting that protects scarce blood types, and (4) a two-tier flow that avoids mobilising donors when a bank already has stock. Unregistered-donor outreach (WhatsApp/Instagram) is future scope and is not built.

The system is a decision-support and workflow tool. It is not a medical authority.

## 3. Business requirements

### 3.1 Problem statement

Blood banks and families lack a fast, transparent way to find, prioritise and contact suitable donors for an active requirement. Existing registries and apps usually stop at filtering by blood group and location. They do not combine urgency, donor cooldown, rare-type conservation, automatic fallback and explanations.

### 3.2 Business objectives

| ID | Objective | Measure of success |
|---|---|---|
| BO-1 | Reduce wasted donor outreach | Fewer donor contacts per fulfilled request than naive and simple baselines (simulation) |
| BO-2 | Make every ranking explainable | 100% of ranked matches carry a faithful per-factor breakdown |
| BO-3 | Conserve scarce blood types | Lower rare-type waste with rarity weighting on vs off |
| BO-4 | Avoid unnecessary mobilisation | Share of requests resolved at Tier 0 when stock exists |
| BO-5 | Lower the barrier to submitting a request | Free-text intake with field-level extraction accuracy measured on a labeled set |
| BO-6 | Produce a defensible research contribution | Pre-registered evaluation, 30 seeds, three response models, losses reported |

### 3.3 Stakeholders

| Stakeholder | Interest |
|---|---|
| Blood bank staff (BANK_STAFF) | Fast, trustworthy donor shortlist; control over every decision |
| Donors (DONOR) | Be contacted only when compatible, eligible and relevant; control their availability |
| Families / recipients (no account) | Submit a request quickly without registering |
| Administrators (ADMIN) | Manage users, engine configuration, audit |
| Faculty / reviewers | Working demo; paper and implementation aligned |
| Project team | Deliverable, publishable paper |

### 3.4 Scope (MoSCoW)

| Priority | Items |
|---|---|
| Must | Registration and login (JWT, RBAC); structured and free-text request intake; public intake into PENDING_REVIEW; Tier-0 inventory check; eligibility filter (ABO/Rh compatibility, availability, cooldown exclusion) and weighted ranking (urgency profile, proximity, rarity); explanation per match; Top-N display; automatic fallback; DB schema with match logs; synthetic data generator; baseline-comparison evaluation; research paper |
| Should | Location-freshness handling; donor dashboard; audit log UI; false-urgency verification for public CRITICAL requests; demo data profile |
| Could | Responsiveness factor (PROPOSED, off by default); confirm-arrival step; richer admin analytics |
| Won't (now) | Unregistered-donor outreach (WhatsApp / Instagram), SMS and push notifications, live GPS tracking, integration with real registries (Red Cross, e-RaktKosh), any real personal or medical data, global optimal (Hungarian) matching in the product |

### 3.5 Assumptions, constraints, dependencies

- Evaluation uses synthetic data only; real-world effectiveness is not claimed.
- Tech stack is fixed: Java 21, Spring Boot, Spring Security, JWT, React, PostgreSQL, a custom Java matching engine (no matching library), an LLM API for parsing.
- Blood type, sex, last donation date and location are self-reported and assumed accurate in the prototype.
- Final clinical eligibility, cross-matching and component choice are always done by qualified blood bank personnel.
- The LLM API requires network access and a key held outside the repository.

### 3.6 Key risks

| Risk | Mitigation |
|---|---|
| Evaluation leakage (simulator and ranker share signals) | Latent traits hidden from the engine; three structurally different response models; module-boundary enforcement; protocol fixed before runs |
| LLM misparses a request | Strict schema validation, confidence flags, mandatory staff review, manual fallback |
| False urgency reporting | Public requests always enter review; CRITICAL from public intake needs staff verification |
| Scope creep | MoSCoW tiering; new scope needs explicit approval |
| Paper and build drift apart | Claims-to-requirements traceability (section 14); all result numbers generated from code |

## 4. Users and roles

| Role | Authentication | Can do | Cannot do |
|---|---|---|---|
| Public visitor (family / recipient) | None | Submit intake form or free text (creates a PENDING_REVIEW request only) | See donors, trigger matching, view any request list |
| DONOR | JWT | Manage own profile and availability; view and respond to own notifications | See other donors, requests beyond those addressed to them, admin functions |
| BANK_STAFF | JWT | Create and review requests; run Tier 0 and matching; view ranked list and explanations; record decline / accept; manage inventory | Change engine configuration; manage users |
| ADMIN | JWT | Manage users and roles; edit engine configuration with version history; view audit log | Bypass the audit log |

There is no RECIPIENT account role. All authorisation is enforced on the server for every endpoint.

## 5. System overview

### 5.1 Architecture

| Layer | Component | Responsibility |
|---|---|---|
| Presentation | React web app | Screens for all roles; shows explanations; no matching logic |
| API | Spring Boot + Spring Security | REST endpoints, JWT, RBAC, validation, persistence, concurrency control |
| Engine | lifeline-core (pure Java) | Eligibility filter, factors, ranker, explanations, fallback, Tier 0 |
| NLP | Intake parser (LLM adapter) | Free text to structured JSON; never trusted without review |
| Data | PostgreSQL | Users, profiles, requests, inventory, match logs, audit, config versions |
| Evaluation | lifeline-sim and lifeline-eval | Synthetic world, baselines, experiments; never imported by the product |

### 5.2 Two-tier workflow

1. Tier 0: check simulated bank inventory. If enough compatible stock is nearby, route the requester to the bank. No donor is contacted.
2. Tier 1: otherwise filter compatible and eligible donors, rank them, show explanations, notify the top matches in-app, and fall back automatically on decline or timeout.
3. If all registered donors are exhausted, staff are told. (Broadcast to unregistered donors is future scope.)

## 6. User flows

### UF-1: Staff creates a structured request

1. Staff logs in (role BANK_STAFF) and opens New Request.
2. Enters blood group, units, urgency, zone / pincode and a non-identifying patient reference.
3. System validates input and stores the request as OPEN.
4. System runs Tier 0, then (if needed) matching, and shows the result.

### UF-2: Staff pastes an informal message

1. Staff pastes a forwarded message into the free-text box.
2. The parser returns structured fields with confidence flags.
3. Staff sees editable fields, corrects them, and approves. Nothing proceeds without approval.
4. The request becomes OPEN and continues as UF-1 step 4.

### UF-3: Family submits a public intake

1. A visitor opens the public form (structured or free text). No login.
2. The system stores a request as PENDING_REVIEW. No matching runs and no donor is contacted.
3. Staff reviews the queue, edits, verifies urgency, and approves (OPEN) or rejects with a reason.
4. Approved requests continue as UF-1 step 4.

### UF-4: Tier-0 inventory check

1. For each OPEN request the system looks for compatible units within the configured radius.
2. If enough units exist, the result is ROUTE_TO_BANK with bank name, zone and contact. The request is marked resolved at Tier 0.
3. Otherwise the flow falls through to donor matching.

### UF-5: Matching, ranking and explanation

1. The eligibility filter removes incompatible, unavailable, paused and in-cooldown donors (exclusion reasons are recorded).
2. Each remaining donor is scored by the weighted factors using the weight profile for the request's urgency.
3. The top N are shown to staff. Each row expands to a per-factor breakdown and a plain-language reason.
4. Staff notifies selected donors (in-app notification).

### UF-6: Donor response and fallback

1. The donor sees the notification and accepts or declines. No response within the window counts as EXPIRED.
2. On decline or expiry, the system recomputes over remaining candidates and surfaces the next-best match.
3. This repeats until the units are confirmed, the attempt budget is reached, or candidates are exhausted (EXHAUSTED shown to staff).
4. Reservation is atomic, so two requests cannot confirm the same donor.

### UF-7: Donor self-service

1. Donor registers, sets blood group, sex, home zone, last donation date and availability.
2. Optionally updates current location (timestamped). A stale location is flagged and the home zone is used.
3. Views notifications and responds.

### UF-8: Donor becomes a patient

If a registered donor is the subject of a request, the donor is automatically paused and is never ranked until staff reactivates them.

### UF-9: Admin

Admin manages users and roles, edits engine configuration (weights, urgency profiles, cooldown days, freshness window, N), and reads the audit log. Every configuration change creates a new version.

### Worked example (illustrative)

Request: B+, 2 units, URGENT, zone Z3. Tier 0 finds only 1 compatible unit nearby, so donor matching runs. Candidates after filtering: D1 (B+, Z3, location fresh), D3 (O+, Z4), D2 (O-, Z3). A fourth donor, D4, donated 40 days ago and is excluded by cooldown; the exclusion reason is recorded.

| Rank | Donor | Reason shown to staff |
|---|---|---|
| 1 | D1 | Closest compatible match, exact blood group, eligible |
| 2 | D3 | Compatible, nearby zone, common type |
| 3 | D2 | Rare type (O-): reserved unless no common-type match is available |

If D1 declines, D3 is surfaced automatically.

## 7. Functional requirements

### 7.1 Authentication and access (AUTH)

| ID | Requirement | Priority |
|---|---|---|
| AUTH-1 | Donors self-register; ADMIN creates BANK_STAFF accounts and the first ADMIN is seeded. | Must |
| AUTH-2 | Login returns a short-lived JWT; passwords are stored with BCrypt. | Must |
| AUTH-3 | Every endpoint enforces the role matrix in section 4 on the server. | Must |
| AUTH-4 | The public intake endpoint is unauthenticated, rate limited, and can only create PENDING_REVIEW requests. | Must |
| AUTH-5 | Login attempts are rate limited. | Should |

### 7.2 Donor management (DON)

| ID | Requirement | Priority |
|---|---|---|
| DON-1 | Donor profile: blood group, sex (used only to select the cooldown interval), home zone / pincode, last donation date, availability. | Must |
| DON-2 | Optional current location with timestamp; trusted only within the freshness window, otherwise flagged stale. | Should |
| DON-3 | Donor can toggle availability. | Must |
| DON-4 | System maintains history statistics (contacts, accepts, declines, no-responses) from recorded outcomes. | Must |
| DON-5 | A donor who is the subject of a request is paused automatically. | Must |

### 7.3 Request intake (REQ)

| ID | Requirement | Priority |
|---|---|---|
| REQ-1 | Staff can create a request with blood group, units, urgency, zone / pincode, non-identifying reference. | Must |
| REQ-2 | Public intake creates a PENDING_REVIEW request. | Must |
| REQ-3 | Staff review queue: edit, approve (OPEN) or reject with a reason. | Must |
| REQ-4 | Urgency levels CRITICAL, URGENT, ROUTINE, each with a configurable response deadline. | Must |
| REQ-5 | CRITICAL requests from public intake require staff verification before they are treated as critical. | Should |
| REQ-6 | Request lifecycle: PENDING_REVIEW, OPEN, RESOLVED_TIER0, FULFILLED, EXHAUSTED, CANCELLED, REJECTED. | Must |

### 7.4 Free-text intake (NLP)

| ID | Requirement | Priority |
|---|---|---|
| NLP-1 | Parse a pasted informal message into blood group, urgency, location, units, contact, confidence. | Must |
| NLP-2 | Parse public free text the same way. | Must |
| NLP-3 | Validate output against a strict schema; flag invalid or low-confidence fields; never auto-approve. | Must |
| NLP-4 | Treat input as data only; embedded instructions must not alter behaviour. | Must |
| NLP-5 | On timeout or failure, fall back to the manual form. | Must |

### 7.5 Tier-0 inventory (T0)

| ID | Requirement | Priority |
|---|---|---|
| T0-1 | Maintain simulated bank inventory: bank, zone, blood group, units. | Must |
| T0-2 | Check compatible stock within a configurable radius before any donor matching; if sufficient, return ROUTE_TO_BANK with details. | Must |
| T0-3 | If insufficient, proceed to donor matching. | Must |
| T0-4 | Staff can update inventory. | Should |

### 7.6 Matching engine (MATCH)

| ID | Requirement | Priority |
|---|---|---|
| MATCH-1 | Eligibility filter: ABO/Rh compatibility (8x8 table), availability, not paused, and outside the cooldown window (90 days men / 120 days women). Excluded donors are never ranked; exclusion reasons are recorded. | Must |
| MATCH-2 | Weighted ranking among eligible donors using proximity and rarity (soft); location freshness is handled inside proximity. | Must |
| MATCH-3 | The request's urgency selects a weight profile. PROPOSED | Must |
| MATCH-4 | Greedy Top-N selection with deterministic tie-break (donor id). | Must |
| MATCH-5 | Weights, urgency profiles, cooldown days (men / women), freshness window, N and decay are configuration, versioned, editable only by ADMIN. | Must |
| MATCH-6 | Optional responsiveness factor from history statistics, off by default. PROPOSED | Could |
| MATCH-7 | Same input and configuration always produce identical output. | Must |

### 7.7 Explainability (EXP)

| ID | Requirement | Priority |
|---|---|---|
| EXP-1 | Every ranked match carries a per-factor breakdown (factor, raw value, weight, contribution, reason code). | Must |
| EXP-2 | A plain-language explanation is generated from the breakdown by template logic (not by an LLM). | Must |
| EXP-3 | Contributions sum exactly to the score. | Must |
| EXP-4 | Flags are shown for stale location and rare type conserved. | Must |
| EXP-5 | The breakdown and explanation are persisted in the match log. | Must |

### 7.8 Donor state, fallback and notifications (FB)

| ID | Requirement | Priority |
|---|---|---|
| FB-1 | Donor states: AVAILABLE, NOTIFIED, ACCEPTED, DECLINED, EXPIRED, DONATED, PAUSED; illegal transitions are rejected. | Must |
| FB-2 | On decline or expiry, recompute and surface the next-best donor. | Must |
| FB-3 | A donor with an outstanding notification is not eligible for another request. | Must |
| FB-4 | An attempt budget applies; on exhaustion the request shows EXHAUSTED to staff. | Must |
| FB-5 | Reservation uses optimistic locking so a donor cannot be confirmed twice. | Must |
| FB-6 | Notifications are in-app only. | Must |

### 7.9 Administration and audit (ADM)

| ID | Requirement | Priority |
|---|---|---|
| ADM-1 | ADMIN manages users and roles. | Must |
| ADM-2 | ADMIN edits engine configuration; each change creates a new version with author and time. | Must |
| ADM-3 | Audit log records logins, request transitions, matching runs and configuration changes; viewable by ADMIN. | Must |
| ADM-4 | A demo profile loads synthetic seed data. | Should |

## 8. Matching engine specification

### 8.1 Compatibility (hard filter)

| Donor | Can donate to |
|---|---|
| O- | All 8 groups |
| O+ | O+, A+, B+, AB+ |
| A- | A-, A+, AB-, AB+ |
| A+ | A+, AB+ |
| B- | B-, B+, AB-, AB+ |
| B+ | B+, AB+ |
| AB- | AB-, AB+ |
| AB+ | AB+ |

This routes and allocates only. It is not clinical cross-matching.

### 8.2 Eligibility filter

A donor enters the pool only if compatible, available, not paused, and outside the cooldown window. The cooldown interval depends on donor sex: 90 days for men and 120 days for women (NBTC guidance, verified by the team). Donors inside the window are excluded and are not ranked until it ends. Both values are configuration.

### 8.3 Factors

| Factor | Raw value | Notes |
|---|---|---|
| Proximity | decay of zone distance (road-factor adjusted) | Uses current location only if confirmed within the freshness window; otherwise the home zone and a STALE_LOCATION flag |
| Rarity conservation | minus rarity score of the donor group, applied only when the donor group is not the exact group requested | rarity score derived from population frequency; soft and configurable |
| Responsiveness (optional) | smoothed accept rate from history, with a prior | Off by default (PROPOSED) |

Score = sum over factors of (weight x raw value). Each term is stored as a contribution.

### 8.4 Urgency profiles (PROPOSED defaults, ASSUMPTION; tuned only on separate tuning seeds)

| Urgency | Locked spec: proximity / rarity | Extension: proximity / rarity / responsiveness |
|---|---|---|
| CRITICAL | 0.85 / 0.15 | 0.60 / 0.10 / 0.30 |
| URGENT | 0.65 / 0.35 | 0.45 / 0.25 / 0.30 |
| ROUTINE | 0.40 / 0.60 | 0.30 / 0.45 / 0.25 |

Each profile sums to 1. Urgency changes the weight mix because it is the same for every donor on a request and cannot rank donors against each other as an additive term.

### 8.5 Selection and fallback

Donors are sorted by score, ties broken by donor id, and the top N (configurable) are returned. This is a deliberate greedy heuristic suited to one-at-a-time urgent requests and to explainability. It does not claim global optimality. On decline or expiry, already-contacted donors are excluded and the ranking is recomputed.

### 8.6 Explanation

The text is built from the largest contributions and flags, for example: 'Closest compatible match, eligible, common blood type' or 'Rare type: reserved unless no common-type match is available'. It is deterministic Java string logic.

### 8.7 Donor state machine

AVAILABLE to NOTIFIED; NOTIFIED to ACCEPTED, DECLINED or EXPIRED; ACCEPTED to DONATED; any state to PAUSED when the donor becomes a patient; PAUSED back to AVAILABLE only by staff action. DECLINED and EXPIRED return the donor to AVAILABLE for later requests. A donor who has donated is excluded by the cooldown rule until the interval ends.

## 9. Data requirements

| Entity | Key fields |
|---|---|
| User | id, role, credentials (hashed), status |
| DonorProfile | user id, blood group, sex, home zone / pincode, last donation date, availability, current zone, location confirmed at, history stats |
| BloodRequest | id, blood group, units, urgency, zone, status, created at, deadline, source (staff / public / parsed), reviewer |
| Bank and Inventory | bank id, zone, blood group, units, updated at |
| MatchLog | request id, step, donor id, score, factor breakdown, explanation, outcome, config version |
| EngineConfigVersion | id, weights, profiles, cooldown days (men / women), freshness window, N, author, created at |
| AuditLog | actor, action, entity, timestamp, detail |

Personal data is minimised: no real identities, and patient references must be non-identifying.

## 10. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-1 | Security | BCrypt passwords; JWT with expiry; server-side RBAC; secrets from environment; input validation; rate limiting on public and login endpoints |
| NFR-2 | Privacy | Synthetic data only; contact details visible only to authorised staff; audit log of access to sensitive actions |
| NFR-3 | Explainability | 100% of ranked matches have a breakdown and explanation; contributions sum to the score |
| NFR-4 | Reproducibility | Seeded randomness; same seed and configuration give identical results; run manifest records commit, config hash and seeds |
| NFR-5 | Performance | Ranking for one request over a 20,000-donor pool within about one second on a development machine (PROPOSED target, verified by the scalability experiment) |
| NFR-6 | Reliability | Atomic donor reservation; no donor confirmed twice under concurrent requests |
| NFR-7 | Usability | Staff reach a ranked list in a few steps; explanations readable without training |
| NFR-8 | Maintainability | Module boundaries enforced by tests; core unit and property test coverage PROPOSED at 80% or more |
| NFR-9 | Safety | Clinical caveat shown in the UI and README on every relevant screen |

## 11. Interfaces

### 11.1 User interface screens

| Screen | Role |
|---|---|
| Login and role navigation | All |
| Public intake (form and free text) | Public |
| Review queue and request detail | BANK_STAFF |
| Tier-0 result | BANK_STAFF |
| Ranked donor list with expandable factor breakdown and explanation | BANK_STAFF |
| Decline / no-response and fallback view | BANK_STAFF |
| Inventory management | BANK_STAFF |
| Profile, availability, notifications | DONOR |
| User management, engine configuration with versions, audit log | ADMIN |

### 11.2 External interfaces

| Interface | Detail |
|---|---|
| REST API | Documented in an OpenAPI specification; consumed by the React app |
| LLM API | Called only by the intake parser; key from environment; timeouts and fallback |
| Database | PostgreSQL with versioned migrations |

## 12. Evaluation requirements

The evaluation runs headless in separate modules and never ships in the product. Details: `docs/EVALUATION_PROTOCOL.md`; parameters: `docs/PARAMETERS.md`.

| ID | Requirement |
|---|---|
| EVAL-1 | A synthetic world with all 8 ABO/Rh groups (distribution from `docs/population.yaml`), zone geography, bank inventory and Poisson request arrivals. |
| EVAL-2 | Donors have latent traits hidden from the engine; the engine sees only observable fields and history derived from simulated events. |
| EVAL-3 | Three structurally different response models, including one mis-specified stress test. |
| EVAL-4 | Baselines: FCFS / broadcast, random, nearest, rule-based, response-history-only, learned ranker, and an oracle upper bound. |
| EVAL-5 | Metrics: fulfillment within deadline, contacts per fulfilled request, time-to-fulfill, unfulfilled rate by blood group, rare-type waste, per-donor load, ranking regret. |
| EVAL-6 | 30 reporting seeds; paired Wilcoxon with Holm correction, effect sizes and confidence intervals; losses are reported. |
| EVAL-7 | Experiments: main comparison, ablation, rarity, Tier 0, fallback, weight sensitivity, explanation faithfulness, scalability, and free-text extraction accuracy. |
| EVAL-8 | The protocol is committed before any experiment runs; later changes are logged as deviations. |

## 13. Limitations, ethics and disclaimer

- Synthetic evaluation cannot establish real-world effectiveness; this is stated explicitly in the paper.
- Zone-based distance is coarse, and self-reported data is unverified.
- The 8-group blood-type split assumes ABO and Rh are independent.
- Silent no-shows after acceptance and false urgency are only partly addressed (verification step; confirm-arrival is future work).
- Reaching donors who are not registered is future scope.
- Clinical disclaimer: LifeLine performs routing and ranking using standard ABO/Rh rules and a configured donation interval. It does not give medical clearance. Eligibility, cross-matching and component decisions belong to qualified blood bank staff.
- Any real deployment would require data-protection compliance and clinical governance, which are outside this project.

## 14. Traceability

| Paper claim | Requirements | Evaluation |
|---|---|---|
| C1 Tier 0 avoids unnecessary mobilisation | T0-1 to T0-3 | Inventory sweep |
| C2 Weighted ranking mobilises donors more efficiently | MATCH-1 to MATCH-5, MATCH-7 | Main comparison, ablation, sensitivity |
| C3 Rarity weighting conserves scarce types | MATCH-2 | Scarcity sweep |
| C4 Fallback improves fulfillment | FB-1 to FB-4 | Fallback on vs off |
| C5 Explanations are faithful | EXP-1 to EXP-5 | Additivity and counterfactual checks |
| C6 Free-text intake is accurate | NLP-1 to NLP-5 | Labeled-set field accuracy |
| C7 Latency is practical | NFR-5 | Scalability experiment |

## 15. Open items for team decision

- Approve PROPOSED items: urgency as a weight-profile selector (MATCH-3) and the optional responsiveness factor (MATCH-6).
- Confirm the blood-group distribution against the full text of the source, and source the donor sex split.
- Approve default weights and urgency deadlines (tuned on separate tuning seeds only).
- Confirm the author list against the three-author policy.

Decided: cooldown is 90 days (men) / 120 days (women), verified, as a hard exclusion. The greedy-vs-Hungarian comparison is not run.

## 16. Glossary

| Term | Meaning |
|---|---|
| ABO/Rh | Blood group system: A, B, AB, O with Rh positive or negative |
| Tier 0 | Inventory check before any donor mobilisation |
| Cooldown | Minimum interval since the last donation (90 days men, 120 days women) before a donor is eligible again; donors inside it are excluded |
| Rarity conservation | Down-ranking rare-type donors for requests a common type could serve |
| Greedy Top-N | Rank by score and take the first N, without global optimisation |
| Latent trait | A simulator-only donor property hidden from the engine |
| Oracle | Upper-bound baseline that ranks by true acceptance probability |
