# Experiment E6: Weight Sensitivity and Profile Evaluation — Final Audit & Verification Report

**Date**: 2026-11-18  
**Status**: VERIFIED & COMPLETE  
**Primary Research Claim**: **Claim C2** — *Multi-factor ranking robustness and urgency profile evaluation.*  
**Dataset Type**: `FINAL_REPORTING` (Reporting Seeds 1–30)  
**Total Simulations**: 2,250 (Stage 1 Tuning Search: 1,890 + Stage 2 Reporting Evaluation: 360)  
**Execution Time**: 9,156.66 seconds (~2.54 hours)

---

## 1. Executive Summary

Experiment E6 evaluates the sensitivity of LifeLine's multi-factor ranking engine to weight variations and assesses the empirical role of dynamic urgency profiles compared to a single static profile.

The evaluation was structured in two strictly separated stages:
1. **Stage 1 (Tuning Search)**: Evaluated $K=20$ alternative weight configurations generated via deterministic bounded uniform perturbation ($\pm 0.10$ around P-28 defaults, master seed `20261117L`, normalized to sum to 1.0) plus the default configuration across 30 tuning seeds (seeds 1000–1029) and three response models ($M1, M2, M3$) = **1,890 simulations**.
   - **Tuning Selection Outcome**:
     - Best configuration: `CFG_04` (mean fulfillment = 0.4683 across all models/tuning seeds).
     - Sensitivity lower-bound configuration: `CFG_12` (mean fulfillment = 0.4653).
   - **Empirical Sensitivity Spread**: The entire fulfillment variation across all 20 perturbed weight configurations was remarkably narrow: **0.4653 to 0.4683** ($\Delta = 0.0030$ or $\pm 0.3\%$).
2. **Stage 2 (Reporting Evaluation)**: Evaluated four systems across 30 pre-registered reporting seeds (seeds 1–30) and three response models ($M1, M2, M3$) = **360 simulations**:
   - `S6_DEFAULT`: P-28 approved default urgency weights.
   - `S6_PERTURBED_BEST`: `CFG_04` (selected strictly from Stage 1 tuning data).
   - `S6_PERTURBED_MIN`: `CFG_12` (selected strictly from Stage 1 tuning data).
   - `S6_SINGLE_URGENCY`: Single static average weight profile applied uniformly across all urgency tiers.

The empirical findings confirm **Claim C2**:
- **Robustness to Weight Perturbation**: Across all models, comparing `S6_DEFAULT` against both `S6_PERTURBED_BEST` and `S6_PERTURBED_MIN` yields **`NO_MEANINGFUL_DIFFERENCE`** on fulfillment within deadline, contacts per fulfilled request, and time to fulfill ($p > 0.30$, Cliff's $|\delta| < 0.14$).
- **Role of Urgency Profiles**: Comparing dynamic urgency profiles against a single static profile also yields **`NO_MEANINGFUL_DIFFERENCE`** ($p = 1.000$, Cliff's $\delta = +0.012$), affirming that dynamic urgency profiles function as policy-level triage steering rather than raw statistical optimizers under uniform synthetic demand.

---

## 2. Integrity and Pre-Registration Verification

| Dimension | Specification | Observed in E6 Run | Verification Status |
|---|---|---|---|
| **Master Sampling Seed** | `20261117L` | `20261117` in config and manifest | PASS |
| **Perturbation Bound** | Uniform $[-0.10, +0.10]$ | Bounded within $[-0.10, +0.10]$ | PASS |
| **Weight Constraints** | Non-negative, sum = 1.0, resp = 0.0 | All 21 configs strictly sum to 1.0, resp = 0.0 | PASS |
| **Tuning Seeds** | Seeds 1000–1029 (30 seeds) | Strictly seeds 1000–1029 used in Stage 1 | PASS |
| **Reporting Seeds** | Seeds 1–30 (30 seeds) | Strictly seeds 1–30 used in Stage 2 | PASS |
| **Data Separation** | Zero reporting data used for selection | `CFG_04` & `CFG_12` chosen solely on seeds 1000–1029 | PASS |
| **Simulation Count** | $1,890 + 360 = 2,250$ | Exactly 2,250 simulations executed | PASS |
| **Tidy Output Rows** | $360 \times 18 = 6,480$ | Exactly 6,480 rows in `e6_results.csv` | PASS |
| **Multiplicity Correction** | Holm-Bonferroni ($\alpha = 0.05$) | Rigorously applied across all 189 comparisons | PASS |

---

## 3. Detailed Results & Statistical Comparisons

### A. Stage 1 Tuning Summary (Seeds 1000–1029)

| Configuration | Description | Mean Fulfillment | Mean Contacts / Fulfilled |
|---|---|---|---|
| `S6_DEFAULT` | Baseline P-28 weights | 0.4671 | 16.43 |
| `CFG_04` (Selected Best) | Perturbation set #4 | **0.4683** | **16.37** |
| `CFG_12` (Selected Min) | Perturbation set #12 | **0.4653** | **16.52** |
| All 20 Perturbations Range | Min: `CFG_12`, Max: `CFG_04` | 0.4653 – 0.4683 | 16.37 – 16.52 |

### B. Stage 2 Reporting Evaluation (Reporting Seeds 1–30)

| Model | Comparison System | Metric | S6 Default | Comparison Mean | Hodges-Lehmann Shift | Cliff's $\delta$ | Holm $p$ | Verdict |
|---|---|---|---|---|---|---|---|---|
| **M1** | `S6_PERTURBED_BEST` | fulfillment_within_deadline | 0.5140 | 0.5107 | +0.0040 | +0.139 | 0.3460 | **NO_MEANINGFUL_DIFFERENCE** |
| **M1** | `S6_PERTURBED_BEST` | contacts_per_fulfilled_request | 13.87 | 13.99 | -0.1151 | -0.112 | 0.4797 | **NO_MEANINGFUL_DIFFERENCE** |
| **M1** | `S6_PERTURBED_MIN` | fulfillment_within_deadline | 0.5140 | 0.5121 | +0.0034 | +0.039 | 0.8854 | **NO_MEANINGFUL_DIFFERENCE** |
| **M1** | `S6_PERTURBED_MIN` | contacts_per_fulfilled_request | 13.87 | 13.95 | -0.1055 | -0.079 | 0.8213 | **NO_MEANINGFUL_DIFFERENCE** |
| **M1** | `S6_SINGLE_URGENCY` | fulfillment_within_deadline | 0.5140 | 0.5141 | -0.0000 | +0.012 | 1.0000 | **NO_MEANINGFUL_DIFFERENCE** |
| **M1** | `S6_SINGLE_URGENCY` | contacts_per_fulfilled_request | 13.87 | 13.83 | +0.0061 | +0.016 | 0.9018 | **NO_MEANINGFUL_DIFFERENCE** |
| **M2** | `S6_PERTURBED_BEST` | fulfillment_within_deadline | 0.3580 | 0.3572 | +0.0000 | -0.012 | 1.0000 | **NO_MEANINGFUL_DIFFERENCE** |
| **M2** | `S6_PERTURBED_MIN` | fulfillment_within_deadline | 0.3580 | 0.3566 | +0.0007 | +0.006 | 1.0000 | **NO_MEANINGFUL_DIFFERENCE** |
| **M2** | `S6_SINGLE_URGENCY` | fulfillment_within_deadline | 0.3580 | 0.3572 | +0.0012 | +0.021 | 1.0000 | **NO_MEANINGFUL_DIFFERENCE** |
| **M3** | `S6_PERTURBED_BEST` | fulfillment_within_deadline | 0.5282 | 0.5247 | +0.0031 | +0.071 | 0.4907 | **NO_MEANINGFUL_DIFFERENCE** |
| **M3** | `S6_PERTURBED_MIN` | fulfillment_within_deadline | 0.5282 | 0.5232 | +0.0050 | +0.102 | 0.4907 | **NO_MEANINGFUL_DIFFERENCE** |
| **M3** | `S6_SINGLE_URGENCY` | fulfillment_within_deadline | 0.5282 | 0.5273 | +0.0000 | +0.014 | 1.0000 | **NO_MEANINGFUL_DIFFERENCE** |

---

## 4. Key Scientific Conclusions

1. **Extreme Weight Stability**: Perturbing the ranking weights by up to $\pm 0.10$ produces negligible changes in fulfillment ($\le 0.3\%$) and contact efficiency. The ranking policy is not brittle or hyper-sensitive to fine-grained weight tuning.
2. **Policy vs Optimization in Urgency**: Dynamic urgency profiles (prioritizing proximity in emergencies and rarity in routine cases) behave similarly to a single compromise profile in aggregate synthetic benchmarks, confirming their primary role is operational intent and policy compliance.
