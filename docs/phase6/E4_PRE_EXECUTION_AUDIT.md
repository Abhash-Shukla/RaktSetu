# LifeLine / RaktSetu — Experiment E4 Pre-Execution Verification Audit Report

**Date:** 2026-11-14  
**Audit Scope:** Independent Pre-Execution Verification of Experiment E4 (Claim C1)  
**Audited Commit / Working Tree:** `92ce970e486d73242bb56006e3b8b50a9914d497` on branch `dev_abhash`  
**Java Runtime:** Oracle JDK 25.0.2 on Windows 11 AMD64  
**Final Audit Verdict:** **READY FOR OFFICIAL E4 EXECUTION**

---

## 1. Executive Summary

This independent pre-execution audit evaluates the readiness of Experiment E4 (Inventory-Level Sweep & Tier-0 Routing Evaluation — Claim C1) across five core verification dimensions:
1. **Tier-0 routing versus actual fulfillment semantics.**
2. **Full regression verification of the Maven test suite across all 6 modules on Java 25.**
3. **Paired-run reproducibility and exogenous random-number generation analysis.**
4. **Metrics and statistical pipeline verification.**
5. **System integrity, protection of historical experimental outputs, and Git hygiene.**

---

## 2. Task-by-Task Audit Findings

### Task 1: Tier-0 Routing Versus Actual Fulfillment

**Audit Verdict:** **PASS (Methodological Clarity Documented)**

- **Inspection Scope:**
  - `docs/SRS.md` (§3.5, §7.5, UF-4)
  - `SimulationRun.java` (lines 121–126, 207–240)
  - `SimulationEventType.java` (`REQUEST_RESOLVED_TIER0`, `REQUEST_FULFILLED`)
  - `SimulationResult.java`
  - `EvaluationMetrics.java`
  - `MetricCalculator.java`
  - `E4InventoryStudy.java`
- **Determination:**
  - `REQUEST_RESOLVED_TIER0` represents **B. Routing to a bank with sufficient simulated inventory.**
  - **Does the simulation model physical collection/transfusion?** **No.** The discrete-event simulator verifies that within the configured search radius ($25.0 \text{ km}$), an eligible blood bank has sufficient compatible stock ($\ge \text{requested units}$). If so, it dynamically decrements the bank inventory, emits `REQUEST_RESOLVED_TIER0`, marks the request resolved at Tier-0, and bypasses donor dispatching completely.
  - The simulator does not model physical logistics (e.g., patient transit delay to the bank, hospital operating hours, bedside cross-match rejection, or post-collection expiration).
- **Impact on Existing Fulfillment Metrics:**
  - In `SimulationRun.java`, Tier-0 resolution emits both `REQUEST_RESOLVED_TIER0` and `REQUEST_FULFILLED` with `timestamp = currentTime` (elapsed fulfillment time $= 0.0 \text{ minutes}$).
  - `request_fulfillment_rate`: Counts Tier-0 resolved requests as fulfilled, reflecting the clinical/operational objective that patient blood demand was met from existing stock without donor outreach.
  - `mean_time_to_fulfill_minutes`: Tier-0 resolutions resolve instantaneously ($t = 0.0$), causing average fulfillment time to decrease monotonically as inventory levels increase.
  - `cost_per_fulfilled_request`: Tier-0 resolutions require 0 donor contacts, significantly lowering notification costs per fulfilled request.
  - `tier0_resolved_share`: Specifically isolates the fraction of total demand satisfied via inventory routing ($N_{\text{tier0}} / N_{\text{requests}}$), allowing researchers to decouple inventory fulfillment from donor-matching fulfillment.
- **Recommended Terminology for Paper & Reports:**
  - Explicitly describe Tier-0 outcomes as **"inventory-routed demand"** or **"stock-resolved requests"** rather than "verified physical transfusions."

---

### Task 2: Full Regression Verification

**Audit Verdict:** **PASS (100% Pass Rate Across All Modules)**

The full Maven reactor test suite was executed uninterrupted on Oracle JDK 25.0.2:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
mvn test
```

- **Execution Duration:** 13 minutes 00 seconds
- **Reactor Summary:**

| Module | Build Status | Duration | Tests Run | Failures | Errors | Skipped | Status |
|---|---|---|---|---|---|---|---|
| `lifeline` (parent) | SUCCESS | 0.003s | 0 | 0 | 0 | 0 | **PASS** |
| `lifeline-core` | SUCCESS | 2.177s | 36 | 0 | 0 | 0 | **PASS** |
| `lifeline-sim` | SUCCESS | 1.812s | 27 | 0 | 0 | 0 | **PASS** |
| `lifeline-eval` | SUCCESS | 12m 54s | 94 | 0 | 0 | 0 | **PASS** |
| `lifeline-api` | SUCCESS | 0.169s | 0 | 0 | 0 | 0 | **PASS** |
| `lifeline-arch-tests`| SUCCESS | 2.054s | 2 | 0 | 0 | 0 | **PASS** |
| **Total Reactor** | **SUCCESS** | **13m 00s**| **159** | **0** | **0** | **0** | **100% PASS** |

- **Key Verified Test Suites:**
  - `Tier0InventoryCheckerTest`: 5 / 5 passed (including multi-bank radius selection).
  - `InventoryScalerTest`: 4 / 4 passed (P-05 expected local demand scaling).
  - `InventoryDepletionManagerTest`: 6 / 6 passed (exact-match, $O^-$ reservation, atomic check).
  - `E4ConfigTest`: 3 / 3 passed (YAML loading and parameter validation).
  - `E4InventoryStudyTest`: 1 / 1 passed (end-to-end multi-slice simulation and monotonicity assertions).
  - `ModuleBoundaryTest`: 2 / 2 passed (ArchUnit: zero architectural boundary violations).
- **Integrity Guarantee:** Zero tests were weakened, skipped, loosened, or deleted.

---

### Task 3: Paired-Run Reproducibility & RNG Stream Analysis

**Audit Verdict:** **PASS (Methodologically Valid Under Approved Architecture)**

- **Inspection Scope:**
  - `WorldGenerator.java`
  - `RequestGenerator.java`
  - `SimulationRun.java`
  - `BaselineMatcherFactory.java`
  - `ExperimentRunner.java`
  - `E4InventoryStudy.java`
- **Exogenous Randomness Matching (Macro-Level):**
  - **World Generation:** For each seed $s \in \{1 \dots 30\}$, `WorldGenerator.generate(s)` is called once using `new SplittableRandom(s)`. Both `S6_TIER0_ON` and `S6_TIER0_OFF` across all 4 inventory levels operate on identical donor populations, latent willingness traits, zone maps, and hospital locations.
  - **Request Generation:** Generated via `reqGen.generateRequests(..., new SplittableRandom(s + 100L))`. The sequence of blood requests (arrival timestamps, requesting facilities, blood phenotypes, requested units, and urgency levels) is strictly identical between ON and OFF across all inventory slices.
  - **Inventory Scaling:** `InventoryScaler.scale(...)` is purely deterministic ($\text{round}(\beta \times 30.0 \times P(g))$), consuming zero pseudo-random draws.
  - **Matcher Ranking:** Matchers are deterministic greedy rankers (`S6LifeLineLockedMatcher`), initialized with `new SplittableRandom(s + 200L)`.
- **Investigation of Downstream Random-Number Consumption (Micro-Level):**
  - In `SimulationRun.java`, stochastic donor response delays (`drawResponseDelay`) and acceptance decisions (`rng.nextDouble() < p`) are drawn from a single `RandomGenerator rng = new SplittableRandom(seed)`.
  - When request $R_i$ is resolved at Tier-0 in `S6_TIER0_ON`, donor dispatch is bypassed, consuming 0 draws from `rng`.
  - In `S6_TIER0_OFF`, $R_i$ triggers donor outreach, consuming draws from `rng`.
  - Consequently, for any later request $R_j$ ($j > i$) that falls through to donor outreach in both systems, `rng` is at a different stream index in ON versus OFF. The stochastic donor response draws for $R_j$ will therefore not be identical between the two runs.
- **Methodological Evaluation:**
  - This is the standard Common Random Numbers (CRN) de-synchronization property inherent to discrete-event simulations where an intervention skips downstream events.
  - **Is the paired comparison valid?** **Yes.** Both systems are evaluated on identical macro-exogenous conditions (identical worlds and identical request streams) for each reporting seed $s \in \{1 \dots 30\}$. The paired seed test remains valid because the seed controls for exogenous demographic and demand shocks.
  - **Are separate random streams required?** **No.** LifeLine's approved discrete-event simulator architecture (`SimulationRun.java`) consistently uses `new SplittableRandom(seed)` across all experiments (E1, E2, E3, E7, E8). Altering `SimulationRun` to introduce request-level or donor-level decoupled RNG streams would constitute a breaking architectural change to the baseline simulator and invalidate historical comparisons.
  - **Conclusion:** The experiment design is sound and approved as-is. The de-synchronization of downstream donor responses is an expected consequence of trajectory divergence and must be documented as an architectural feature.

---

### Task 4: Metrics and Statistical Verification

**Audit Verdict:** **PASS (Strict Protocol Compliance)**

- **`tier0_resolved_share`:**
  - Calculated as:
    $$\text{tier0\_resolved\_share} = \frac{\sum_{r \in \text{requests}} \mathbb{I}(r \in \text{tier0ResolvedRequests})}{|\text{requests}|}$$
  - Correctly mapped to `MetricDirection.HIGHER_IS_BETTER`.
  - Emitted as an observed tidy row in `e4_results.csv` for every simulation run.
- **`total_notifications_sent`:**
  - Evaluated as the exact sum of `requestAttempts.values()`, matching physical `DONOR_NOTIFIED` simulation events.
  - Correctly mapped to `MetricDirection.LOWER_IS_BETTER`.
  - Emitted as an observed tidy row in `e4_results.csv` for every simulation run.
- **`donor_contacts_avoided` (Paired Derived Metric):**
  - Computed per seed $s$ and response model $m$ as:
    $$\Delta_{\text{contacts}}(s, m, \beta) = \text{Contacts}_{\text{OFF}}(s, m, \beta) - \text{Contacts}_{\text{ON}}(s, m, \beta)$$
  - Strictly evaluated across all 30 reporting seeds and tested against zero via `StatisticalAnalyzer.comparePairedDerivedMetric(...)` using the two-sided Wilcoxon signed-rank test.
  - Not included in `e4_results.csv` (tidy per-run observations), strictly honoring Implementation Clarification #6.
  - Emitted in `e4_statistical_summary.csv` and `e4_statistical_report.md`.
- **Artifact Alignment (720-run Official Experiment):**
  - Tidy observations: $4 \text{ slices} \times 2 \text{ systems} \times 3 \text{ models} \times 30 \text{ seeds} \times 20 \text{ metrics} = 14,400 \text{ rows}$ (+1 header row $= 14,401 \text{ lines}$).
  - Statistical comparisons: $4 \text{ slices} \times 3 \text{ models} \times 21 \text{ metrics} = 252 \text{ paired comparisons}$.

---

### Task 5: Integrity Checks

**Audit Verdict:** **PASS (Zero Safety Violations)**

1. **Official E4 Output Directory:** `results/e4-final/` **does not exist**. No official simulation runs have been started.
2. **Prior Results Immutability:**
   - `results/e1-final/` — Untouched and unmodified.
   - `results/e2-final/` — Untouched and unmodified.
   - `results/e3-final/` — Untouched and unmodified.
   - `results/e7-final/` — Untouched and unmodified.
   - `results/e7-reporting-v2/` — Untouched and unmodified.
   - `results/e8-final/` — Untouched and unmodified.
   - `results/e8-sustained/` — Untouched and unmodified.
   - `protocol-v1/` — Untouched and unmodified.
3. **Git Working-Tree Hygiene:**
   - No destructive Git commands (`git reset --hard`, `git clean`) executed.
   - No commits or pushes performed.
   - No unrelated files modified.

---

## 3. Comprehensive Audit Summary & Verdicts

| Verification Dimension | Focus | Audit Result | Detailed Status |
|---|---|---|---|
| **Task 1** | Tier-0 Routing Semantics | **PASS** | Validated as stock-available routing; impact on metrics documented. |
| **Task 2** | Full Regression Suite | **PASS** | 159 / 159 tests passed across all 6 modules on Java 25 (0 failures). |
| **Task 3** | Paired-Run Reproducibility | **PASS** | Exogenous initial conditions strictly matched; CRN trajectory documented. |
| **Task 4** | Metrics & Statistical Engine | **PASS** | Tidy (20) and derived (1) metrics strictly conform to protocol specs. |
| **Task 5** | Integrity & Safeguards | **PASS** | Historical results immutable; official E4 not run; 0 git violations. |

---

## 4. Final Verdict

$$\mathbf{READY\ FOR\ OFFICIAL\ E4\ EXECUTION}$$

All pre-execution verification gates have been satisfied. When human authorization is provided, the official 720-run Experiment E4 can be executed using Java 25:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
mvn exec:java -pl lifeline-eval "-Dexec.mainClass=lifeline.eval.cli.EvalCli" "-Dexec.args=run E4 --config configs/e4.yaml"
```

