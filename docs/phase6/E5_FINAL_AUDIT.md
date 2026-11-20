# Experiment E5: Fallback Re-ranking and Attempt-Budget Sweep — Final Audit & Verification Report

**Date**: 2026-11-17  
**Status**: VERIFIED & COMPLETE  
**Primary Research Claim**: **Claim C4** — *Fallback re-ranking improves fulfillment on decline / no-response.*  
**Dataset Type**: `FINAL_REPORTING` (Reporting Seeds 1–30)  
**Total Simulations**: 900 (5 Attempt Budgets $\times$ 2 Systems $\times$ 3 Response Models $\times$ 30 Reporting Seeds)  
**Execution Time**: 4,510.17 seconds (~75.2 minutes)

---

## 1. Executive Summary

Experiment E5 evaluates the necessity and quantitative impact of sequential fallback re-ranking and attempt-budget constraints ($B \in \{1, 5, 10, 15, 20\}$) across 30 pre-registered reporting seeds and three structurally distinct donor response models ($M1, M2, M3$).

The empirical findings conclusively validate **Claim C4**:
1. **Fallback is Indispensable for System Viability**: Under single-attempt dispatch ($B=1$, Fallback OFF), fulfillment within deadline collapses to **5.45% (M1)**, **3.67% (M2)**, and **5.64% (M3)**. Because bounded donor willingness ($P \approx 0.15\text{--}0.45$, P-14/P-18) prevents single outreach attempts from resolving emergencies, attempting only one donor leaves >94% of blood requests unfulfilled.
2. **Dramatic Fulfillment Lift at Baseline Budget**: Under the pre-specified baseline budget ($B=10$, P-24), sequential fallback re-ranking drives fulfillment to **51.40% (M1)**, **35.80% (M2)**, and **52.82% (M3)**. This represents an absolute fulfillment lift of $+45.95\%$ (HL shift $+0.460$, $p = 0.000002$, Cliff's $\delta = +1.000$).
3. **Diminishing Returns Across Sweep**: As the attempt budget expands from 1 to 20, fulfillment increases monotonically ($5.5\% \to 36.3\% \to 51.4\% \to 60.9\% \to 64.6\%$ under M1), but with diminishing marginal returns. Expanding beyond $B=10$ incurs higher median fulfillment latency ($157.7\text{ min}$ at $B=10$ vs $214.5\text{ min}$ at $B=20$) as donors in deeper fallback tiers take longer to accept or timeout.
4. **Efficiency of Contacts**: Counter-intuitively, `contacts_per_fulfilled_request` improves from $18.79$ at $B=1$ down to $13.87$ at $B=10$ and $13.03$ at $B=20$ under M1, because unfulfilled requests waste single contacts without resolving the demand.

---

## 2. Integrity and Pre-Registration Verification

| Dimension | Specification | Observed in E5 Run | Verification Status |
|---|---|---|---|
| **Attempt Budgets** | $[1, 5, 10, 15, 20]$ | $[1, 5, 10, 15, 20]$ (5 slices: E5_B1, E5_B5, E5_B10, E5_B15, E5_B20) | PASS |
| **Budget 1 Semantics** | Exactly 1 contact attempt, no fallback | $B=1$: 0 fallback attempts, 0 fallback resolutions | PASS |
| **Systems** | `S6_FALLBACK_ON`, `S6_FALLBACK_OFF` | `S6_FALLBACK_ON`, `S6_FALLBACK_OFF` | PASS |
| **Response Models** | $M1, M2, M3$ | $M1, M2, M3$ | PASS |
| **Reporting Seeds** | Seeds 1–30 | Seeds 1–30 (30 seeds) | PASS |
| **Total Simulations** | $5 \times 2 \times 3 \times 30 = 900$ | Exactly 900 simulations | PASS |
| **Tidy Output Rows** | $900 \times 20 = 18,000$ | Exactly 18,000 rows (18,001 lines with header) | PASS |
| **Paired Evaluation** | Identical worlds & requests | Paired across identical seeds per slice | PASS |
| **Multiplicity Correction** | Holm-Bonferroni ($\alpha = 0.05$) | Rigorously applied per slice | PASS |

---

## 3. Key Empirical Findings Across Attempt Budgets

### A. Primary Performance Metrics Across Slices

| Slice | Response Model | Metric | Fallback ON | Fallback OFF | Hodges-Lehmann Shift | Cliff's $\delta$ | Holm $p$ | Verdict |
|---|---|---|---|---|---|---|---|---|
| **E5_B1** | M1 | fulfillment_within_deadline | 0.0545 | 0.0545 | +0.0000 | +0.000 | 1.0000 | NO DIFFERENCE (Identity check) |
| **E5_B1** | M2 | fulfillment_within_deadline | 0.0367 | 0.0367 | +0.0000 | +0.000 | 1.0000 | NO DIFFERENCE (Identity check) |
| **E5_B1** | M3 | fulfillment_within_deadline | 0.0564 | 0.0564 | +0.0000 | +0.000 | 1.0000 | NO DIFFERENCE (Identity check) |
| **E5_B5** | M1 | fulfillment_within_deadline | 0.3630 | 0.0545 | +0.3090 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B5** | M1 | fallback_resolved_count | 321.4 | 0.0 | +321.5 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M1 | fulfillment_within_deadline | 0.5140 | 0.0545 | +0.4600 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M1 | fallback_resolved_count | 425.7 | 0.0 | +426.0 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M2 | fulfillment_within_deadline | 0.3580 | 0.0367 | +0.3213 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M2 | fallback_resolved_count | 348.3 | 0.0 | +348.5 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M3 | fulfillment_within_deadline | 0.5282 | 0.0564 | +0.4729 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B10** (Baseline) | M3 | fallback_resolved_count | 432.9 | 0.0 | +432.0 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B15** | M1 | fulfillment_within_deadline | 0.6090 | 0.0545 | +0.5546 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B15** | M1 | fallback_resolved_count | 453.7 | 0.0 | +452.5 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B20** | M1 | fulfillment_within_deadline | 0.6460 | 0.0545 | +0.5911 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B20** | M1 | fallback_resolved_count | 461.0 | 0.0 | +460.0 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B20** | M2 | fulfillment_within_deadline | 0.5112 | 0.0367 | +0.4756 | +1.000 | 0.000002 | **IMPROVEMENT** |
| **E5_B20** | M3 | fulfillment_within_deadline | 0.6610 | 0.0564 | +0.6055 | +1.000 | 0.000002 | **IMPROVEMENT** |

---

## 4. Key Scientific Conclusions

1. **Claim C4 is Unequivocally Validated**: The hypothesis that fallback re-ranking improves fulfillment is affirmed across all response models with maximal statistical significance ($p = 0.000002$, Cliff's $\delta = +1.0$).
2. **Justification for Default Budget $B=10$**: Setting the baseline attempt budget to 10 captures the vast majority (~80%) of potential fallback fulfillment gains while preventing excessive notification fatigue and latency explosions.
3. **Clinical Recommendation**: Blood-bank coordinators operating the LifeLine platform should maintain the default attempt budget of 10 for urgent/routine requests, with an optional escalation to 15 for critical shortages where donor availability permits.
