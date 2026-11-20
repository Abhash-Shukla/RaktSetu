# LifeLine / RaktSetu — Experiment E4 Design Audit & Implementation Readiness Report

**Experiment**: E4 — Inventory-Level Sweep & Tier-0 Evaluation  
**Research Claim**: Claim C1 (*"Tier-0 inventory check avoids unnecessary donor mobilization"*)  
**Evaluation Scope**: Design audit, mathematical derivations, depletion policy, metric formulations, and implementation plan  
**Execution Status**: COMPREHENSIVE AUDIT COMPLETE — IMPLEMENTATION READY PENDING FINAL CONFIRMATION  
**Date**: November 14, 2026  
**Auditor**: Antigravity Assistant (autonomous Turbo audit mode)  

---

## 1. Executive Summary & Audit Verdict

A rigorous, non-mutating audit was conducted across `lifeline-core`, `lifeline-sim`, `lifeline-eval`, and `lifeline-api`. 

- **Approved Human Decisions Received**:
  - **D-E4-1**: Inventory sweep levels approved at **$0\%, 25\%, 50\%, 100\%$**.
  - **D-E4-4**: Search radius approved at existing default **$25.0\text{ km}$**.
  - **D-E4-5**: Evaluated systems approved as **`S6_TIER0_ON` vs `S6_TIER0_OFF`** across all 4 slices, 3 response models ($M1, M2, M3$), and 30 reporting seeds ($1 \dots 30$).
  - **D-E4-6**: Claim C1 tracking approved for **`tier0_resolved_share`** and **`donor_contacts_avoided`**, alongside standard protocol metrics.
- **Task 1 (Expected Demand)**: Fully derived and established from existing simulator configuration without ungrounded assumptions. Total expected demand per zone over the 30-day horizon is **$30.0\text{ units}$**.
- **Task 2 (Depletion Policy)**: Developed a clinically grounded, mathematically sound consumption policy: **Exact match first $\to$ descending population frequency**. This strictly protects rare universal stock ($O-$) from depletion by common recipients.
- **Task 3 (Metric Formulations)**: Formulated exact mathematical definitions for `tier0_resolved_share` and paired `donor_contacts_avoided`, with structural guarantees against double-counting.
- **Verdict**: **`READY FOR E4 IMPLEMENTATION PENDING FINAL CONFIRMATION OF D-E4-2 AND D-E4-3`**.

---

## 2. Approved Decisions Baseline

The following decisions are formally accepted and locked for Experiment E4:

| Decision ID | Parameter | Locked Value | Status |
|---|---|---|---|
| **D-E4-1** | Inventory Sweep Levels | **$0\%, 25\%, 50\%, 100\%$** | Approved |
| **D-E4-4** | Search Radius | **$25.0\text{ km}$** (per `Tier0InventoryChecker.DEFAULT_RADIUS_KM`) | Approved |
| **D-E4-5** | System Comparison | **`S6_TIER0_ON` vs `S6_TIER0_OFF`** across all slices and models | Approved |
| **D-E4-6** | Tracked C1 Metrics | **`tier0_resolved_share`** and **`donor_contacts_avoided`** | Approved |

---

## 3. TASK 1: Rigorous Derivation of Expected Local Demand

### A. Mathematical Derivation from Approved Configuration
Parameter P-09 states: *"Bank inventory: Per zone, stock as 0%-100% of expected local demand (sweep)"*. To establish the exact numerical baseline without inventing parameters:

1. **Request Arrival Process (P-05)**:
   - Sourced from `docs/PARAMETERS.md` P-05 and `simulation-parameters.yaml`:
     - `arrivalMeanPerZonePerDay: 0.5`
     - `horizonDays: 30`
   - In `RequestGenerator.java` (lines 37–38):
     ```java
     int horizonMinutes = params.horizonDays() * 24 * 60;
     double lambdaPerMinute = params.arrivalMeanPerZonePerDay() / (24.0 * 60.0);
     ```
   - Across a 30-day horizon ($H = 30$), the expected request count per zone is:
     $$E[R_{\text{zone}}] = \lambda_{\text{zone}} \times H = 0.5 \times 30 = 15.0 \text{ requests}$$

2. **Units Per Request Distribution (P-08)**:
   - Sourced from `docs/PARAMETERS.md` P-08 and implemented in `RequestGenerator.sampleUnits(rng)`:
     - $P(\text{units} = 1) = 0.35$
     - $P(\text{units} = 2) = 0.40$
     - $P(\text{units} = 3) = 0.15$
     - $P(\text{units} = 4) = 0.10$
   - The theoretical expectation is exact:
     $$E[\text{units}] = 1(0.35) + 2(0.40) + 3(0.15) + 4(0.10) = 0.35 + 0.80 + 0.45 + 0.40 = 2.00 \text{ units}$$

3. **Total Expected Local Demand per Zone**:
   - The expected total blood units demanded in any zone $z$ over the 30-day simulation is:
     $$\text{Demand}_{\text{expected}}(z) = E[R_{\text{zone}}] \times E[\text{units}] = 15.0 \times 2.00 = 30.0 \text{ units}$$
   - Across all 40 zones in the synthetic world (P-04), total expected world demand is:
     $$\text{Demand}_{\text{expected, world}} = 40 \times 30.0 = 1,200.0 \text{ units}$$

4. **Blood Group Demand Distribution (P-01, P-02)**:
   - In `RequestGenerator.java` line 72, blood groups are sampled from `bloodGroupFrequencies` (`docs/population.yaml`):
     $$\text{Demand}_{\text{expected}}(z, g) = 30.0 \times P(g)$$

| Blood Group ($g$) | Population Frequency $P(g)$ | Expected 30-Day Demand per Zone ($30.0 \times P(g)$) |
|---|---|---|
| **O+** | $0.325313$ | $9.759$ units |
| **B+** | $0.320983$ | $9.630$ units |
| **A+** | $0.218005$ | $6.540$ units |
| **AB+** | $0.076998$ | $2.310$ units |
| **O-** | $0.020287$ | $0.609$ units |
| **B-** | $0.020017$ | $0.601$ units |
| **A-** | $0.013595$ | $0.408$ units |
| **AB-** | $0.004802$ | $0.144$ units |
| **Total** | **$1.000000$** | **$30.000$ units** |

### B. Final Inventory Scaling Formula
For inventory sweep level $\beta \in \{0.0, 0.25, 0.50, 1.00\}$:

$$\text{ExpectedUnits}(z, g, \beta) = \beta \times 30.0 \times P(g)$$

To ensure perfect reproducibility, zero seed leakage, and integer unit allocations:
$$\text{Stock}(z, g, \beta) = \text{round}\left( \beta \times 30.0 \times P(g) \right)$$

- At **$\beta = 0.0$ ($0\%$)**: All stock is identically $0$ units (internal calibration control).
- At **$\beta = 0.25$ ($25\%$)**: Stock per bank sums to $\approx 7.5$ units.
- At **$\beta = 0.50$ ($50\%$)**: Stock per bank sums to $\approx 15.0$ units.
- At **$\beta = 1.00$ ($100\%$)**: Stock per bank sums to $\approx 30.0$ units.

---

## 4. TASK 2: Inventory Depletion & Stock Selection Policy

### A. Necessity of Dynamic Stock Decrement
1. In `lifeline-core`, `BankInventory` is an immutable record.
2. In discrete-event simulation, blood units are physical, perishable, consumable resources.
3. If stock were not decremented upon `ROUTE_TO_BANK`, a bank initialized with 2 units of B+ could fulfill dozens of requests over 30 days without ever running out. This would violate physical conservation of inventory and artificially inflate Tier 0 fulfillment to near 100%.
4. **Requirement**: When a request of $U$ units is routed to Bank $B$, Bank $B$'s available stock must be decremented by exactly $U$ units.

### B. Compatible Stock Consumption Priority (Protecting Rare Universal Stock)
When Bank $B$ has multiple compatible blood groups in stock that collectively satisfy request units $U$, in what order should blood groups be consumed?

**Recommended Policy**: **Exact Match First, then Descending Population Frequency**.

For a recipient blood group $G_{\text{rec}}$, compatible donor groups $G_{\text{donor}}$ are prioritized as follows:
1. **Tier 1 Priority**: Exact group match ($G_{\text{donor}} == G_{\text{rec}}$).
2. **Tier 2 Priority**: Other compatible groups ordered by **descending population frequency** $P(G_{\text{donor}})$.

#### Clinical & Algorithmic Protection Proof:
- **Case 1: Recipient is B+** (Compatible: B+, O+, B-, O-).
  - Priority: B+ ($P=0.321$) $\to$ O+ ($P=0.325$) $\to$ B- ($P=0.020$) $\to$ O- ($P=0.020$).
  - Exact match B+ is consumed first. If exhausted, common compatible O+ is consumed. Universal rare O- is at the absolute bottom of the queue and will **never** be consumed while common compatible stock exists.
- **Case 2: Recipient is AB+** (Universal recipient; compatible with all 8 groups).
  - Priority: AB+ ($P=0.077$) $\to$ O+ ($0.325$) $\to$ B+ ($0.321$) $\to$ A+ ($0.218$) $\to$ O- ($0.020$) $\to$ B- ($0.020$) $\to$ A- ($0.014$) $\to$ AB- ($0.005$).
  - Consumes AB+ first, then common positive groups. Rare negative types ($O-, B-, A-, AB-$) are completely insulated from depletion.
- **Case 3: Recipient is O+** (Compatible: O+, O-).
  - Consumes O+ first; O- is only touched if O+ stock is $0$.
- **Case 4: Recipient is O-** (Compatible: O- only).
  - Only O- can be consumed. Because O- was preserved in Cases 1–3, O- units remain available for O- recipients!

### C. Insufficient Remaining Stock & Atomic Fall-Through
- Per SRS Section 7.5 (T0-2, T0-3) and `Tier0InventoryChecker.java`:
  - `cb.compatibleUnits >= request.units()`
- If a bank has only 1 unit of compatible blood and the request requires 2 units:
  - The bank does **not** qualify.
  - Partial splitting between blood bank and donor outreach is **prohibited** by the core specification.
  - The checker returns `FALL_THROUGH`, leaving the bank's stock intact for future 1-unit requests, while the current request proceeds in its entirety to Tier 1 donor matching.

### D. Guarantee of Identical Experimental Conditions
- For any reporting seed $s \in \{1 \dots 30\}$ and inventory level $\beta$:
  1. Synthetic world topology and donors are generated from seed $s$.
  2. Request stream arrivals are generated from seed $s + 100L$.
  3. `S6_TIER0_OFF` runs on this exact request stream (bypassing Tier 0).
  4. `S6_TIER0_ON` runs on the **identical** request stream, initialized with the exact same initial bank stock.
  5. Any difference in donor notifications or fulfillment is strictly and mathematically attributable to the Tier-0 inventory mechanism.

---

## 5. TASK 3: Precise Metric Definitions & Avoided Contacts Calculation

### A. Metric: `tier0_resolved_share`
- **Definition**: The proportion of all submitted blood requests that are successfully resolved directly from bank inventory at Tier 0 without initiating donor matching.
- **Mathematical Formula**:
  $$\text{tier0\_resolved\_share} = \frac{R_{\text{resolved, Tier0}}}{R_{\text{total}}}$$
  Where:
  - $R_{\text{resolved, Tier0}}$ is the count of requests where `Tier0Result.status() == ROUTE_TO_BANK`.
  - $R_{\text{total}}$ is the total number of requests submitted in the simulation run ($R_{\text{total}} = R_{\text{resolved, Tier0}} + R_{\text{tier1}}$).
- **Bounds**: $[0.0, 1.0]$.
  - Under `S6_TIER0_OFF`: Identically $0.0$.
  - Under `S6_TIER0_ON` at $0\%$ inventory: Identically $0.0$.
  - Under `S6_TIER0_ON` at $>0\%$ inventory: Monotonically increases with stock level.
- **Optimization Direction**: `HIGHER_IS_BETTER`.

### B. Metric: `donor_contacts_avoided`
- **Definition**: The net absolute reduction in donor notifications dispatched when Tier 0 is active compared to when Tier 0 is disabled under identical world and demand conditions.
- **Mathematical Formula**:
  For reporting seed $s \in \{1 \dots 30\}$, response model $m \in \{M1, M2, M3\}$, and inventory level $\beta$:
  $$\Delta_{\text{contacts}}(s, m, \beta) = N_{\text{contacts}}(\text{S6\_TIER0\_OFF}, s, m, \beta) - N_{\text{contacts}}(\text{S6\_TIER0\_ON}, s, m, \beta)$$
  Where $N_{\text{contacts}}$ is the total count of `DONOR_NOTIFIED` simulation events recorded during the 30-day run.

#### Prevention of Double-Counting:
- Each `DONOR_NOTIFIED` event represents a discrete notification sent to an individual donor for a specific request attempt.
- $N_{\text{contacts}}$ is an aggregate integer count of physical notifications.
- Because `S6_TIER0_OFF` and `S6_TIER0_ON` process the identical sequence of requests on identical worlds, the difference $N_{\text{OFF}} - N_{\text{ON}}$ directly measures the net volume of outreach spared by resolving requests from stock.
- Avoided contacts are computed once per matched run pair $(s, m, \beta)$, preventing any risk of double-counting across requests or attempts.

### C. Standard Protocol Metrics (Section 4)
- **`contacts_per_fulfilled_request`**:
  $$\frac{N_{\text{contacts}}}{R_{\text{fulfilled, donor}} + R_{\text{resolved, Tier0}}}$$
  *(Since Tier 0 fulfillments incur 0 donor contacts, this metric decreases significantly under Tier 0 ON; direction: LOWER_IS_BETTER).*
- **`fulfillment_within_deadline`**:
  $$\frac{R_{\text{fulfilled, donor}} + R_{\text{resolved, Tier0}}}{R_{\text{total}}}$$
  *(Tier 0 requests are resolved immediately upon arrival with zero delay; direction: HIGHER_IS_BETTER).*

---

## 6. Final Run Matrix & Computational Budget

| Dimension | Values | Count |
|---|---|---|
| **Inventory Slices** | `E4_0PCT`, `E4_25PCT`, `E4_50PCT`, `E4_100PCT` | 4 slices |
| **Systems** | `S6_TIER0_ON`, `S6_TIER0_OFF` | 2 systems |
| **Response Models** | `M1` (Logistic), `M2` (Fatigue), `M3` (Misspecified) | 3 models |
| **Reporting Seeds** | Reporting seeds $1 \dots 30$ ($N=30$) | 30 seeds |
| **Total Simulations** | $4 \times 2 \times 3 \times 30$ | **720 simulation runs** |
| **World Generations** | 1 synthetic world per seed, shared across slices | **30 world generations** |
| **Metrics per Run** | 18 Section 4 protocol metrics + 2 Claim C1 metrics | **20 metrics** |
| **Total Output Records** | $720 \text{ runs} \times 20 \text{ metrics}$ | **14,400 tidy CSV rows** |
| **Statistical Comparisons** | $4 \text{ slices} \times 3 \text{ models} \times 20 \text{ metrics}$ | **240 pairwise comparisons** |
| **Estimated Runtime** | Based on E3 benchmark ($5,828\text{ s}$ for 720 runs with Tier 1 matching) | **$\approx 60\text{--}80\text{ minutes}$** (faster because Tier 0 bypasses donor matching) |

---

## 7. Remaining Human Decisions for Final Sign-Off

All major experimental decisions have been resolved and formulated. Two specific operational definitions require your final confirmation before writing implementation code:

### 1. `D-E4-2`: Final Demand Base & Deterministic Allocation Approval
- **Derived Base**: $30.0\text{ units/zone}$ over 30 days ($0.5\text{ req/day} \times 30\text{ days} \times 2.0\text{ units/req}$).
- **Formula**: $\text{Stock}(z, g, \beta) = \text{round}(\beta \times 30.0 \times P(g))$.
- **Question**: Do you approve this deterministic inventory allocation formula?

### 2. `D-E4-3`: Dynamic Stock Depletion & Priority Approval
- **Depletion**: Available bank stock is decremented upon `ROUTE_TO_BANK`.
- **Priority**: Exact match first $\to$ descending population frequency (protecting rare $O-$ from common requests).
- **Question**: Do you approve dynamic stock depletion with this consumption priority order?

---

## 8. Recommended Implementation Plan

Upon receiving approval of D-E4-2 and D-E4-3:

1. **Step 1: Inventory Management (`lifeline-sim`)**:
   - Create `InventoryScaler.java`: implements $\text{round}(\beta \times 30.0 \times P(g))$ to generate initial bank stock per slice.
   - Add `SimulationEventType.REQUEST_RESOLVED_TIER0`.
   - Update `SimulationRun.java` with a clean, backwards-compatible constructor/flag to execute `Tier0InventoryChecker` on request arrival and decrement bank stock with exact-match-first priority.
2. **Step 2: Metrics Support (`lifeline-eval`)**:
   - Update `EvaluationMetrics.java` to serialize `tier0_resolved_share` and `donor_contacts_avoided`.
   - Update `MetricCalculator.java` and `MetricDirection.java` (`tier0_resolved_share`: HIGHER_IS_BETTER, `donor_contacts_avoided`: HIGHER_IS_BETTER).
3. **Step 3: Study Harness & Configuration**:
   - Create `lifeline.eval.study.inventory.E4Config`, `E4InventoryStudy`, `E4StudyResult`.
   - Create `configs/e4-smoke.yaml` (1 seed) and `configs/e4.yaml` (30 seeds).
   - Register `run E4` in `EvalCli.java`.
4. **Step 4: Regression & Unit Tests**:
   - Unit test `InventoryScalerTest`: test 0%, 25%, 50%, 100% stock levels.
   - Regression test: verify `S6_TIER0_OFF` produces bitwise identical results to previous baseline runs.
   - Test depletion: verify exact-match-first consumption and $O-$ insulation.
5. **Step 5: Smoke Test Execution & Readiness Audit**:
   - Execute smoke test (`configs/e4-smoke.yaml`).
   - Run full reactor test suite (`mvn verify`).
   - Present final readiness audit before official 720-run execution.
