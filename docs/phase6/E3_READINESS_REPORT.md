# E3 Experiment — Specification and Readiness Audit Report

**Date**: 2026-11-13  
**Auditor**: Evaluation Framework Auditor (Antigravity)  
**Target Experiment**: Experiment E3 — Scarcity Sweep & Rarity Weighting Evaluation (Claim C3)  
**Target Output**: `docs/phase6/E3_READINESS_REPORT.md`  
**Repository Branch**: `dev_abhash` (Commit `92ce970e486d73242bb56006e3b8b50a9914d497`)  
**Runtime**: Oracle Java 25.0.2 LTS on Windows 11

---

## 1. Executive Summary & Verdict

### Final Readiness Verdict: **`READY WITH HUMAN DECISIONS REQUIRED`**

Experiment E3 evaluates **Claim C3**: *"Rarity weighting conserves scarce types without hurting common-request outcomes"*. 

The core architectural prerequisites in `lifeline-core` and `lifeline-sim` are fully implemented and verified:
- The soft rarity factor [`RarityFactor`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-core/src/main/java/lifeline/core/factor/RarityFactor.java) applies a negative conservation penalty exclusively to non-exact compatible allocations, down-ranking rare donors when a common compatible donor could serve the recipient.
- The ablated matcher `S6_NO_RARITY` is already operational in [`BaselineMatcherFactory`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-eval/src/main/java/lifeline/eval/baseline/BaselineMatcherFactory.java) and tested in E2.
- The request generator [`RequestGenerator`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-sim/src/main/java/lifeline/sim/request/RequestGenerator.java) natively accepts arbitrary blood-group frequency distributions.
- Metric calculation for `rare_type_waste_rate` is implemented in [`MetricCalculator`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-eval/src/main/java/lifeline/eval/metric/MetricCalculator.java).

Implementation cannot proceed immediately because three human engineering choices must be confirmed:
1. **Decision D-E3-1 (System Scope)**: Whether to compare exclusively `S6` vs `S6_NO_RARITY` (720 runs) or also include `S7` vs `S7_NO_RARITY` (1,440 runs).
2. **Decision D-E3-2 (Scarcity Multiplier Request-Mix Rescaling Math)**: Formal sign-off on the proportional re-normalization formula for common groups when rare groups are multiplied by 1.0x, 1.5x, 2.0x, 3.0x.
3. **Decision D-E3-3 (Data Model Representation of Multipliers)**: Whether to slice multipliers via sub-experiments (`E3_1.0x`, `E3_1.5x`, etc.), via system labels (`S6_1.0x`, etc.), or via separate sub-directories.

---

## 2. Research Objective and Protocol-Defined Design

### 2.1 Research Objective (Claim C3)
To establish whether the LifeLine engine's soft rarity weighting mechanism achieves its intended dual objective under increasing blood supply scarcity:
1. **Conservation**: Significantly reduces `rare_type_waste_rate` and improves `rare_request_fulfillment` as rare blood group demand surges.
2. **Non-Inferiority on Common Demand**: Does not statistically or clinically harm `common_request_fulfillment` or overall `fulfillment_within_deadline`.

### 2.2 Protocol Mapping (`docs/EVALUATION_PROTOCOL.md`)
- **Claim**: C3 (`EVALUATION_PROTOCOL.md` Section 1).
- **Experiment Entry**: E3 — *"Scarcity sweep (rare-type share and request mix); rarity on vs off"* (`EVALUATION_PROTOCOL.md` Section 5).
- **Core Metrics**:
  - `rare_type_waste_rate`: share of rare-type donor allocations to requests that a more common compatible type could have served (`EVALUATION_PROTOCOL.md` Section 4).
  - `rare_request_fulfillment`: fraction of rare requests ($O^-, B^-, A^-, AB^-$) fulfilled before deadline.
  - `common_request_fulfillment`: fraction of common requests ($O^+, B^+, A^+, AB^+$) fulfilled before deadline.
- **Reporting Seeds**: Seeds 1–30 inclusive ($N = 30$ independent paired replications per cell).
- **Response Models**: All three response models: `M1` (Baseline logistic), `M2` (Fatigue + time of day), `M3` (Mis-specified zone affinity).

---

## 3. Approved Parameter Mapping

All baseline simulation and factor parameters are pre-registered in `docs/PARAMETERS.md`:

| Parameter ID | Description | Approved Value / Range | Role in E3 |
|---|---|---|---|
| **P-01** | Population blood-group frequencies | Indian national frequencies (Patidar & Dhiman 2021): $O^+: 32.53\%$, $B^+: 32.10\%$, $A^+: 21.80\%$, $AB^+: 7.70\%$, $O^-: 2.03\%$, $B^-: 2.00\%$, $A^-: 1.36\%$, $AB^-: 0.48\%$ | Governs donor pool generation (fixed across all E3 runs). |
| **P-02** | Request blood-group mix | Sourced from P-01; scarcity sweep multiplies rare-type request share by **1.0x, 1.5x, 2.0x, 3.0x** | **Primary Independent Variable** in E3. |
| **P-03** | Donor pool size | 20,000 donors | Fixed across all E3 runs. |
| **P-04** | Zone geography | 40 zones, uneven density (lognormal $\sigma = 0.8$), road factor 1.3 | Fixed across all E3 runs. |
| **P-05** | Request arrivals | Poisson process per zone over 30-day horizon | Fixed arrival rate parameter. |
| **P-06** | Urgency mix | CRITICAL 20%, URGENT 40%, ROUTINE 40% | S6 urgency profile weights applied. |
| **P-07** | Request deadlines | CRITICAL 2h, URGENT 6h, ROUTINE 24h | Governs fulfillment censorship. |
| **P-10** | Cooldown exclusion | 90 days (men) / 120 days (women) | Hard exclusion filter for all systems. |
| **P-24** | Attempt budget | 10 contacts per request | Fixed across all runs. |
| **P-26** | Seed allocation | Reporting seeds 1–30; tuning seeds 1000+ | All paper results use seeds 1–30. |
| **P-28** | Urgency-profile weights | S6: CRITICAL (0.85 prox / 0.15 rar), URGENT (0.65 / 0.35), ROUTINE (0.40 / 0.60) | Dictates soft conservation penalty weight. |

---

## 4. Missing Decisions and `TODO(human)` Items

The following three engineering decisions require explicit human confirmation before implementing E3:

### 1. `TODO(human)` D-E3-1: System Comparison Scope
- **Issue**: `EVALUATION_PROTOCOL.md` specifies "rarity on vs off".
  - *Option 1 (Recommended — Minimalist & Standard)*: Compare `S6` (Rarity ON) vs `S6_NO_RARITY` (Rarity OFF). Total runs = $4 \text{ multipliers} \times 2 \text{ systems} \times 3 \text{ models} \times 30 \text{ seeds} = \mathbf{720\text{ runs}}$.
  - *Option 2 (Comprehensive)*: Compare `S6` vs `S6_NO_RARITY` AND `S7` vs `S7_NO_RARITY`. Total runs = $4 \times 4 \times 3 \times 30 = \mathbf{1,440\text{ runs}}$.
  - *Option 3 (With Baseline Reference)*: Include `S2` (Nearest Donor) to demonstrate how naive distance routing behaves under scarcity. Total runs = $4 \times 3 \times 3 \times 30 = \mathbf{1,080\text{ runs}}$.
- **Auditor Recommendation**: **Option 1**. S6 is the locked product specification and primary claim target for C3.

### 2. `TODO(human)` D-E3-2: Request-Mix Re-normalization Formula
- **Issue**: Multiplying rare-type request share by $\alpha \in \{1.0, 1.5, 2.0, 3.0\}$ expands the rare-type probability mass from 5.87% to up to 17.61%. Common-type demand must be rescaled so total arrival probability equals 1.0.
- **Proposed Mathematical Formulation**:
  Let rare groups $R = \{O^-, B^-, A^-, AB^-\}$ and common groups $C = \{O^+, B^+, A^+, AB^+\}$.
  Baseline frequencies $f_g$ satisfy $\sum_{g \in R} f_g = f_R \approx 0.058701$ and $\sum_{g \in C} f_g = f_C \approx 0.941299$.
  For multiplier $\alpha$:
  $$f'_g = \alpha \cdot f_g \quad (\forall g \in R)$$
  Total rescaled rare mass:
  $$f'_R = \sum_{g \in R} f'_g = \alpha \cdot f_R$$
  Remaining common mass:
  $$f'_C = 1.0 - f'_R$$
  Proportional common scaling:
  $$f'_g = f_g \cdot \frac{f'_C}{f_C} \quad (\forall g \in C)$$
- **Resulting Request Mix by Multiplier**:
  - **1.0x**: Rare 5.87%, Common 94.13%
  - **1.5x**: Rare 8.81%, Common 91.19%
  - **2.0x**: Rare 11.74%, Common 88.26%
  - **3.0x**: Rare 17.61%, Common 82.39%
- **Auditor Recommendation**: Adopt this exact proportional formula as it perfectly preserves intra-group demographic ratios for both rare and common groups.

### 3. `TODO(human)` D-E3-3: Multiplier Representation in Tidy Data
- **Issue**: `TidyMetricRow` has the schema `(experiment, system, response_model, seed, metric, value)`.
- **Options**:
  - *Option A (Experiment Sub-Tagging — Recommended)*: Record `experiment` as `E3_1.0X`, `E3_1.5X`, `E3_2.0X`, `E3_3.0X`. `system` remains `S6` and `S6_NO_RARITY`. Output files: `results/e3-final/e3_results.csv`, plus slice summaries.
  - *Option B (System Sub-Tagging)*: Record `system` as `S6_1.0X`, `S6_NO_RARITY_1.0X`, etc., in a single experiment `E3`. Requires updating `StatisticalAnalyzer` candidate-to-baseline mappings.
  - *Option C (Directory Slicing)*: Separate runs into `results/e3-final/mult_1.0x/`, etc.
- **Auditor Recommendation**: **Option A**. It allows [`StatisticalAnalyzer`](file:///c:/Users/asraj/OneDrive/Desktop/RaktSetu/lifeline-eval/src/main/java/lifeline/eval/stats/StatisticalAnalyzer.java) to evaluate paired comparisons (`S6` vs `S6_NO_RARITY`) within each scarcity condition without breaking existing statistical analysis code.

---

## 5. Existing Reusable Components vs Required Work

### 5.1 Fully Reusable Existing Infrastructure

| Component | Location | Status |
|---|---|---|
| Synthetic World Generator | `lifeline-sim/src/main/java/lifeline/sim/world/WorldGenerator.java` | Reusable as-is (generates identical 20,000-donor worlds per seed). |
| Poisson Request Generator | `lifeline-sim/src/main/java/lifeline/sim/request/RequestGenerator.java` | Reusable as-is (accepts any custom frequency map). |
| Response Models | `lifeline-sim/src/main/java/lifeline/sim/response/` (M1, M2, M3) | Reusable as-is. |
| Production Matching Engine | `lifeline-core/src/main/java/lifeline/core/rank/Ranker.java` | Reusable as-is. |
| Soft Rarity Conservation Factor | `lifeline-core/src/main/java/lifeline/core/factor/RarityFactor.java` | Reusable as-is. |
| Baseline Matchers | `lifeline-eval/src/main/java/lifeline/eval/baseline/` (`S6`, `S6_NO_RARITY`) | Reusable as-is. |
| Statistical Engine | `lifeline-eval/src/main/java/lifeline/eval/stats/` (Wilcoxon, Holm, Bootstrap CI, Hodges-Lehmann) | Reusable as-is. |
| Metric Calculator (Waste Rate) | `lifeline-eval/src/main/java/lifeline/eval/metric/MetricCalculator.java` (`rareTypeWasteRate`) | Reusable as-is. |

### 5.2 Required Implementation Work
1. **Scarcity Frequency Generator (`ScarcityRequestMixScaler.java`)**:
   - Implement the approved re-normalization formula to produce scaled `Map<BloodGroup, Double>` for each multiplier $\alpha \in \{1.0, 1.5, 2.0, 3.0\}$.
2. **Metric Extensions (`MetricCalculator.java` & `EvaluationMetrics.java`)**:
   - Add explicit metrics:
     - `rare_request_fulfillment`: $\frac{\text{fulfilled rare requests}}{\text{total rare requests}}$.
     - `common_request_fulfillment`: $\frac{\text{fulfilled common requests}}{\text{total common requests}}$.
   - Update `MetricDirection.java` to classify both as `HIGHER_IS_BETTER`.
3. **E3 Study Runner (`E3ScarcityStudy.java` or `E3ExperimentRunner`)**:
   - Orchestrates the scarcity sweep across the 4 multipliers, generating requests with the scaled mix while holding donor worlds constant.
   - Dispatches parallel execution across available CPU threads (32 cores).
4. **Configuration (`configs/e3.yaml` & `configs/e3-smoke.yaml`)**:
   - Pre-register systems, models, reporting seeds 1–30, and scarcity multipliers `[1.0, 1.5, 2.0, 3.0]`.
5. **CLI Integration (`EvalCli.java`)**:
   - Add `run E3` dispatch.

---

## 6. Statistical Methodology for Claim C3

E3 requires inferential hypothesis testing across the 30 paired seeds per model and scarcity level:

### 6.1 Hypotheses & Directional Tests

1. **Hypothesis $H_{1}$ (Rare Waste Reduction)**:
   - Metric: `rare_type_waste_rate` (`LOWER_IS_BETTER`).
   - Comparison: $S6$ vs $S6\_NO\_RARITY$.
   - Test: Two-sided paired Wilcoxon signed-rank test across 30 seeds.
   - Decision Rule: Rejection of $H_0$ with Holm-adjusted $p < 0.05$ and negative Hodges-Lehmann shift demonstrates statistically significant waste conservation.
2. **Hypothesis $H_{2}$ (Rare Request Fulfillment Under Scarcity)**:
   - Metric: `rare_request_fulfillment` (`HIGHER_IS_BETTER`).
   - Comparison: $S6$ vs $S6\_NO\_RARITY$.
   - Expectation: Under baseline (1.0x), rare requests are infrequent and difference may be modest; under high scarcity (2.0x, 3.0x), $S6$ should achieve statistically superior rare fulfillment as common requests are prevented from exhausting the scarce O-/B-/A-/AB- donors.
3. **Hypothesis $H_{3}$ (Common Request Non-Inferiority)**:
   - Metric: `common_request_fulfillment` (`HIGHER_IS_BETTER`).
   - Comparison: $S6$ vs $S6\_NO\_RARITY$.
   - Decision Rule: Rarity weighting must not induce clinical harm on common-type recipients (Hodges-Lehmann median difference should be near zero / negligible effect size).

---

## 7. Workload and Runtime Estimates

- **Simulation Grid**:
  - 4 Scarcity Multipliers: `1.0x`, `1.5x`, `2.0x`, `3.0x`
  - 2 Systems: `S6`, `S6_NO_RARITY`
  - 3 Response Models: `M1`, `M2`, `M3`
  - 30 Reporting Seeds: `1` through `30`
  - **Total Simulation Runs**: $4 \times 2 \times 3 \times 30 = \mathbf{720\text{ runs}}$.
- **Hardware Profile**: 32 logical CPU cores on Windows 11.
- **Estimated Runtime**:
  - Each simulation run processes ~580–650 requests over 20,000 donors (~0.35–0.5 seconds per run on Java 25).
  - With 32-thread parallelism, expected total execution time: **8 to 12 minutes**.
- **Memory Footprint**:
  - Worlds are generated once per seed and shared across systems/models within that seed. Peak heap usage will remain under 2–3 GB (well within the 8,124 MB JVM limit).

---

## 8. Testing Strategy

Prior to full execution, the implementation must pass four levels of automated tests:
1. **Mathematical Unit Tests**:
   - Test `ScarcityRequestMixScaler` to assert that for all multipliers, probabilities sum to $1.0 \pm 10^{-12}$, rare probabilities scale by exactly $\alpha$, and common ratios remain invariant.
2. **Metric Unit Tests**:
   - Hand-computed test cases for `rare_request_fulfillment` and `common_request_fulfillment` in `MetricCalculatorTest`.
3. **Property-Based Tests (jqwik)**:
   - Property asserting that for any positive multiplier $\alpha \in [0.5, 5.0]$, generated frequencies remain strictly in $(0, 1)$ and sum to 1.0.
4. **End-to-End Smoke Test**:
   - `e3-smoke.yaml` executing 1 seed across 2 multipliers and 2 systems, verifying that `e3_results.csv` and statistical reports are exported with valid schemas.

---

## 9. Expected Artifacts

When E3 execution completes, it will generate the following directory structure:

```
results/e3-final/
├── e3_results.csv                   # Tidy observations (experiment, system, model, seed, metric, value)
├── e3_statistical_summary.csv       # Paired Wilcoxon, Holm p-values, CI, Hodges-Lehmann shift
├── e3_statistical_report.md         # Formatted Markdown report with comparisons across multipliers
└── run_manifest.json                # Execution metadata, JVM version (Java 25), git commit, timestamps
```

---

## 10. Risks and Blockers

| Risk | Severity | Mitigation |
|---|---|---|
| Common fulfillment degradation under 3.0x | Medium | This is a research finding, not a bug. If extreme rare demand exhausts all donors, common fulfillment drop must be reported transparently as a trade-off. |
| Mixed seed types | Low | Enforce `classifyAndValidateSeeds` to reject any tuning seeds (1000+) in final reporting runs. |
| Breaking changes to E1/E2 results | Critical | Strict output isolation: E3 outputs exclusively to `results/e3-final/`. All previous results directories remain untouched. |
| MetricDirection misclassification | Medium | Ensure `MetricDirection.java` explicitly maps `rare_request_fulfillment` and `common_request_fulfillment` to `HIGHER_IS_BETTER`. |

---

## 11. Next Steps

1. Human approves or refines decisions **D-E3-1**, **D-E3-2**, and **D-E3-3**.
2. Proceed to implement `ScarcityRequestMixScaler`, metric extensions, and `E3ScarcityStudy`.
3. Run unit and property tests.
4. Execute `configs/e3-smoke.yaml`.
5. Execute official 30-seed E3 run on Java 25.

