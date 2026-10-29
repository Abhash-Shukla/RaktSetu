# LifeLine Evaluation Protocol — v1.0 (for sign-off)

Status: sign-off required, then commit and tag `protocol-v1` **before any experiment is run**. After that, changes go to `docs/DEVIATIONS.md`. Parameter values live in `docs/PARAMETERS.md` and `docs/population.yaml`.

## 1. Claims -> evidence map

| # | Claim | Experiment | Metric(s) |
|---|---|---|---|
| C1 | Tier-0 inventory check avoids unnecessary donor mobilization | E4 | share of requests resolved at Tier 0; donor contacts avoided |
| C2 | Multi-factor weighted ranking mobilizes donors more efficiently than naive and simple baselines | E1, E2, E6 | fulfillment within deadline; contacts per fulfilled request; time-to-fulfill |
| C3 | Rarity weighting conserves scarce types without hurting common-request outcomes | E3 | rare-type waste rate; rare-request fulfillment; common-request fulfillment |
| C4 | Fallback re-ranking improves fulfillment on decline / no-response | E5 | fulfillment within deadline, fallback on vs off |
| C5 | Explanations are faithful to the score that produced the ranking | E7 | additivity error; top-factor counterfactual agreement; reason-code coverage |
| C6 | Free-text intake extracts request fields accurately | E9 | field-level precision/recall/F1 on a labeled set |
| C7 | Engine latency is practical at realistic scale | E8 | runtime vs donors x requests (not an effectiveness claim) |

Claims not listed here are not made in the paper. Unregistered-donor outreach is described as future work only.

## 2. Systems compared

| ID | System | Sees |
|---|---|---|
| S0 | FCFS / broadcast to all eligible compatible donors | eligibility only |
| S1 | Random among eligible compatible | eligibility only |
| S2 | Nearest donor | eligibility + distance |
| S3 | Rule-based (distance, availability, recency of last donation) | observable, no history |
| S4 | Response-history-only | observable history estimate |
| S5 | Learned ranker (logistic regression on simulated history, time-split) | observable |
| S6 | **LifeLine engine, locked spec** (urgency profile, proximity with freshness, rarity) | observable |
| S7 | LifeLine engine + responsiveness extension (toggle) | observable |
| S8 | Oracle (ranks by true acceptance probability) | latent (upper bound only) |

All systems receive the same hard-filtered pool: ABO/Rh compatible, available, not paused, and **outside the cooldown window** (90 days men / 120 days women). Cooldown exclusion is part of the filter, so every baseline respects it.

## 3. Simulator specification (summary; parameters in `docs/PARAMETERS.md`)

**World.** All 8 ABO/Rh groups using `docs/population.yaml`. A zone grid with uneven donor density and a road-distance multiplier. Simulated bank inventory per zone and type. Request arrivals follow a Poisson process per zone over a simulated horizon, with urgency-dependent deadlines.

**Donors.** Each donor has *latent* traits (reliability, willingness, response-delay scale, travel tolerance, fatigue sensitivity) that the engine never sees. The engine sees observable fields: blood group, sex, home zone, last donation date, availability flag, optional current location with timestamp, and **history statistics computed from simulated past outreach events** (smoothed accept rate with a prior; donors with no history get the prior).

**Response models** (every experiment runs under all three):

| Model | Structure | Purpose |
|---|---|---|
| M1 | Logistic: latent reliability + distance + availability + urgency sensitivity | Baseline realism |
| M2 | M1 + interactions, donor fatigue after repeated contact, time-of-day effect | Dynamics the engine does not model |
| M3 | Mis-specified stress test: distance effect near zero; hidden zone-affinity effect not observable | Where the engine's proximity weighting is wrong |

Acceptance is a Bernoulli draw from the model probability; response delay is a donor-specific lognormal. Donors with an outstanding notification are busy. An attempt budget and deadline bound every request, so unfulfilled requests occur.

**Calibration checks (reported, never tuned to favor LifeLine).** (a) The learned ranker S5 should show first-contact acceptance lift over random of roughly 1.25x-2x (Wu et al. 2022). (b) No donor group accepts near 100% (intention-behaviour gap, Ou-Yang et al. 2020).

## 4. Metric definitions

- **Fulfillment within deadline**: fraction of requests with all units confirmed before the deadline.
- **Contacts per fulfilled request**: donor notifications sent / fulfilled requests.
- **Time-to-fulfill**: median and p90 of simulated minutes from request creation to last unit confirmed (fulfilled only; also report the censored count).
- **Unfulfilled rate by blood group**.
- **Rare-type waste rate**: share of rare-type donor allocations (O-, AB-, and any group below a configured frequency threshold) to requests that a more common compatible type could have served.
- **Per-donor contact load**: Gini coefficient and max contacts per donor.
- **Ranking regret**: oracle first-contact acceptance probability minus the system's top-1 acceptance probability; also NDCG gap to the oracle.

Precision@K is not used (it saturates when most candidates are relevant).

## 5. Experiments

| ID | Design |
|---|---|
| E1 | S0-S8 x M1-M3 x 30 seeds, default config. Primary comparison. |
| E2 | Leave-one-component-out ablation of S6/S7: proximity, rarity, responsiveness (S7), location-freshness handling, urgency profiles (single profile instead). Per model. |
| E3 | Scarcity sweep (rare-type share and request mix); rarity on vs off. |
| E4 | Inventory-level sweep; Tier 0 on vs off. |
| E5 | Fallback on vs off; attempt-budget sweep. |
| E6 | Weight sensitivity: random weight sets (tuning seeds only); urgency-profile comparison. |
| E7 | Explanation faithfulness: exact additivity; remove the top-contributing factor and check whether rank changes; reason-code coverage; a generated sample-explanation figure. Optional, human-run: a small rating study (5-10 raters judging whether explanations are sensible), reported as a pilot. |
| E8 | Scalability: donors {1k, 5k, 20k, 50k} x concurrent requests {1, 10, 50, 100}; repeated timings. |
| E9 | NLP extraction on a labeled free-text set (including informal / mixed-language messages), LLM outputs recorded as fixtures for reproducibility. |
| E10 | NOT RUN (greedy vs Hungarian comparison not approved). |

## 6. Seeds and statistics

- Reporting seeds: 1-30. Tuning / sensitivity-search seeds: 1000+. Never mixed. Run seeds in parallel.
- Per metric and model: mean, SD, 95% bootstrap CI.
- Primary comparison: S6 (and S7) vs each of S0, S2, S3, S4, S5 — paired Wilcoxon signed-rank across seeds, Holm correction, effect size (Hodges-Lehmann shift or Cliff's delta).
- **Pre-specified primary metrics:** fulfillment within deadline and contacts per fulfilled request.
- **Decision rule:** an improvement is claimed only if Holm-adjusted p < 0.05 **and** the effect is non-negligible; otherwise it is reported as no meaningful difference. Losses and ties appear in the main text.

## 7. Threats to validity (must appear in the paper)

- All data are synthetic; parameters are literature-informed or labeled assumptions; real-world effectiveness is not established.
- The response models are assumptions. Three structurally different models reduce, but do not remove, dependence on modeling choices.
- The 8-group blood-type split assumes ABO and Rh are independent (the source reports them separately).
- Zone-based geography is coarse; self-reported donor data is assumed accurate.
- Post-acceptance no-shows and false urgency are modeled only if listed in PARAMETERS.md.
- Latency numbers are implementation- and hardware-dependent.

## 8. Open items for a human before sign-off

- [ ] Approve default urgency-profile weights (PARAMETERS P-28); tune only on tuning seeds
- [ ] Confirm population.yaml values against the full text of the source (`human_verified: true`)
- [ ] Source or revise the donor sex split (P-27)
- [x] Cooldown: 90 days (men) / 120 days (women), verified; hard exclusion
