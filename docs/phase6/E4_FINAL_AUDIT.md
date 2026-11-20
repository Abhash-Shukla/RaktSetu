# LifeLine / RaktSetu — Experiment E4 Final Independent Audit Report

**Experiment ID**: E4 — Inventory-Level Sweep & Tier-0 Evaluation  
**Research Claim**: **Claim C1** ("Tier-0 inventory check avoids unnecessary donor mobilization")  
**Evaluation Dataset**: `FINAL_REPORTING` (Reporting Seeds 1–30)  
**Execution Timestamp**: 2026-11-17T15:06:15Z to 2026-11-17T16:13:41Z  
**Total Runtime**: 4,045.85 seconds (~67.4 minutes)  
**Java Runtime**: OpenJDK 25 LTS (`Oracle Corporation 25.0.2`)  
**Git Commit**: `92ce970e486d73242bb56006e3b8b50a9914d497`  
**Output Directory**: `results/e4-final/`  
**Auditor**: Independent Evaluation Agent (Turbo Mode)  

---

## 1. Executive Summary & Verdict

Experiment E4 has officially completed all 720 pre-registered simulation runs across 30 reporting seeds, 4 inventory levels (0%, 25%, 50%, 100%), 2 systems (`S6_TIER0_ON`, `S6_TIER0_OFF`), and 3 response models (M1, M2, M3).

### Final Audit Verdict: **PASSED (UNANIMOUS CLAIM SUPPORT)**

**Claim C1 is conclusively supported by the empirical data**:
1. **Donor Mobilization Relief**: Stock routing via Tier-0 eliminates **1,056 donor contacts** at 25% inventory, **2,037 contacts** at 50% inventory, and **3,987 contacts** at 100% inventory per 30-day period under Model M1 ($p = 0.000002$, Cliff's $\delta = +1.0$, Hodges-Lehmann shift $+3,992$ contacts avoided).
2. **Stock Resolution Share**: Simulated bank inventory resolves **26.1%** of requests at 25% capacity, **48.4%** at 50% capacity, and **93.8%** at 100% capacity without dispatching a single donor outreach notification.
3. **Fulfillment Within Deadline**: Request fulfillment rises from $51.4\%$ (Tier-0 OFF) to **$62.4\%$** (25% inventory), **$74.2\%$** (50% inventory), and **$96.1\%$** (100% inventory) under Model M1 ($p < 0.00001$).
4. **Zero-Inventory Baseline Invariance**: At 0% inventory (`E4_0PCT`), `S6_TIER0_ON` produces results identical to `S6_TIER0_OFF` across all metrics ($0.514024$ fulfillment, 4,285.47 notifications, 0.000 avoided contacts, $p = 1.000000$, `NO_MEANINGFUL_DIFFERENCE`), proving engine safety and zero inventory phantom leaks.

---

## 2. Experimental Verification & Data Integrity

### 2.1 File & Record Counts

| File | Status | Expected Rows / Records | Observed Rows / Records | Integrity Check |
|---|---|---|---|---|
| `e4_results.csv` | Present | 14,401 lines (1 header + 14,400 rows) | 14,401 lines (14,400 records) | **PERFECT MATCH** |
| `e4_statistical_summary.csv` | Present | 253 lines (1 header + 252 comparisons) | 253 lines (252 comparisons) | **PERFECT MATCH** |
| `e4_statistical_report.md` | Present | Complete statistical markdown | 1,600+ lines generated | **VALID** |
| `run_manifest.json` | Present | JSON metadata with 720 runs, 30 seeds | Valid JSON, 30 seeds, 720 runs | **VALID** |

### 2.2 Factorial Completeness

- **Inventory Levels**: Exactly 4 levels (`[0.0, 0.25, 0.5, 1.0]`) evaluated.
- **Systems**: Exactly 2 systems (`S6_TIER0_ON`, `S6_TIER0_OFF`).
- **Response Models**: Exactly 3 models (M1, M2, M3).
- **Seeds**: Exactly 30 reporting seeds (1 through 30).
- **Total Executions**: $4 \times 2 \times 3 \times 30 = 720$ simulations.
- **Tidy Metric Records**: $720 \text{ runs} \times 20 \text{ metrics} = 14,400 \text{ records}$.
- **Statistical Comparisons**: 4 experiment slices (`E4_0PCT`, `E4_25PCT`, `E4_50PCT`, `E4_100PCT`) $\times$ 3 models $\times$ 21 metrics (including paired derived metric `donor_contacts_avoided`) = 252 comparisons.

---

## 3. Empirical Results Analysis

### 3.1 Primary Inventory Metrics Across Slices

#### Model M1 (Standard Realistic Response Model)
| Inventory Level | Tier-0 Resolved Share | Total Notifications Sent (ON vs OFF) | Donor Contacts Avoided | Fulfillment Within Deadline (ON vs OFF) | Holm $p$ | Decision |
|---|---|---|---|---|---|---|
| **0%** (`E4_0PCT`) | $0.000$ ($0.0\%$) | $4,285.5$ vs $4,285.5$ | $0.0$ | $0.514$ vs $0.514$ | $1.000000$ | NO MEANINGFUL DIFFERENCE |
| **25%** (`E4_25PCT`) | $0.261$ ($26.1\%$) | $3,228.8$ vs $4,285.5$ | **$+1,056.6$** | $0.624$ vs $0.514$ | $0.000002$ | **IMPROVEMENT** |
| **50%** (`E4_50PCT`) | $0.484$ ($48.4\%$) | $2,247.8$ vs $4,285.5$ | **$+2,037.6$** | $0.742$ vs $0.514$ | $0.000002$ | **IMPROVEMENT** |
| **100%** (`E4_100PCT`) | $0.938$ ($93.8\%$) | **$298.5$** vs $4,285.5$ | **$+3,987.0$** | **$0.961$** vs $0.514$ | $0.000002$ | **IMPROVEMENT** |

#### Model M2 (Fatigue & Non-Linear Availability)
| Inventory Level | Tier-0 Resolved Share | Total Notifications Sent (ON vs OFF) | Donor Contacts Avoided | Fulfillment Within Deadline (ON vs OFF) | Holm $p$ | Decision |
|---|---|---|---|---|---|---|
| **0%** (`E4_0PCT`) | $0.000$ ($0.0\%$) | $4,780.5$ vs $4,780.5$ | $0.0$ | $0.358$ vs $0.358$ | $1.000000$ | NO MEANINGFUL DIFFERENCE |
| **25%** (`E4_25PCT`) | $0.261$ ($26.1\%$) | $3,576.3$ vs $4,780.5$ | **$+1,204.2$** | $0.513$ vs $0.358$ | $0.000002$ | **IMPROVEMENT** |
| **50%** (`E4_50PCT`) | $0.484$ ($48.4\%$) | $2,468.6$ vs $4,780.5$ | **$+2,311.9$** | $0.675$ vs $0.358$ | $0.000002$ | **IMPROVEMENT** |
| **100%** (`E4_100PCT`) | $0.938$ ($93.8\%$) | **$318.4$** vs $4,780.5$ | **$+4,462.1$** | **$0.956$** vs $0.358$ | $0.000002$ | **IMPROVEMENT** |

#### Model M3 (Mis-specified Stress Test)
| Inventory Level | Tier-0 Resolved Share | Total Notifications Sent (ON vs OFF) | Donor Contacts Avoided | Fulfillment Within Deadline (ON vs OFF) | Holm $p$ | Decision |
|---|---|---|---|---|---|---|
| **0%** (`E4_0PCT`) | $0.000$ ($0.0\%$) | $4,238.4$ vs $4,238.4$ | $0.0$ | $0.528$ vs $0.528$ | $1.000000$ | NO MEANINGFUL DIFFERENCE |
| **25%** (`E4_25PCT`) | $0.261$ ($26.1\%$) | $3,200.1$ vs $4,238.4$ | **$+1,038.3$** | $0.636$ vs $0.528$ | $0.000002$ | **IMPROVEMENT** |
| **50%** (`E4_50PCT`) | $0.484$ ($48.4\%$) | $2,205.2$ vs $4,238.4$ | **$+2,033.2$** | $0.756$ vs $0.528$ | $0.000002$ | **IMPROVEMENT** |
| **100%** (`E4_100PCT`) | $0.938$ ($93.8\%$) | **$294.1$** vs $4,238.4$ | **$+3,944.3$** | **$0.965$** vs $0.528$ | $0.000002$ | **IMPROVEMENT** |

---

## 4. Methodological & Pre-Registration Compliance

1. **Common Random Numbers (CRN) Integrity**:
   - Initial worlds and request arrivals are strictly identical across pairs.
   - At 0% inventory, where 0 Tier-0 checks resolve requests, random draws remain 100% synchronized, producing zero numerical divergence between ON and OFF.
   - As documented in `E4_PRE_EXECUTION_AUDIT.md`, Tier-0 resolution naturally bypasses donor draws; this represents true physical branch avoidance rather than an artifact.
2. **Clinical Routing Semantics**:
   - As required by Task 1 audit findings, `REQUEST_RESOLVED_TIER0` is reported strictly as **simulated stock routing / stock resolution**, not as guaranteed verified physical transfusions.
   - No clinical effectiveness claims are made.
3. **Pre-Registration Decision Rule**:
   - Across all positive inventory levels ($25\%, 50\%, 100\%$), Holm-adjusted $p$-values are $0.000002 \ll 0.05$ and Cliff's $\delta = +1.0$ (large magnitude). All improvements are pre-registered and statistically significant.

---

## 5. Conclusion

Experiment E4 is fully verified, reproducible, and compliant with all project and evaluation protocol rules. Official results are accepted into the Phase 6 corpus.
