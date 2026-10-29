# docs/PARAMETERS.md

Every simulation parameter, its value (or range), and where it comes from. `SOURCE` means a cited study supports the value. `ASSUMPTION` means no suitable public source was found; the range is explored in the sensitivity experiment (E6). Nothing here is claimed to be a measured Indian emergency-response rate. These values are human-approved inputs: the agent uses them as given.

## A. Literature anchors

| Ref | What it supports | Status |
|---|---|---|
| Patidar GK, Dhiman Y. ISBT Sci Ser 2021;16(1):37-48. doi:10.1111/voxs.12576 | Pan-India ABO and Rh(D) frequencies (A 23.16%, B 34.10%, O 34.56%, AB 8.18%; Rh+ 94.13%, Rh- 5.87%), 112 studies, 1,429,996 donors | Reported marginals verified against publication. 8-group joint distribution is derived under statistical independence assumption, not directly measured. |
| Ou-Yang J, Bei C-H, Liang H-Q, He B, Chen J-Y, Fu Y-S. BMC Public Health 2020;20:475 | Cold re-recruitment of inactive donors (Guangzhou, n=11,880): re-donation 7.4% control, 8.1% phone, 8.4% SMS; mean 1-year return interval about 142-157 days (SD about 100); stated willingness far above actual re-donation | Confirmed from article tables. Context: inactive donors, China, not emergency requests. Motivates intention-behaviour gap for P-16/P-16a. |
| Wu H-y et al. Sci Rep 2022;12:19165 | Relative effects only: high-willingness group hit rate 1.96x the low group; XGBoost 1.25x and combined XGBoost+SVM 1.80x the conventional method | Confirmed from the abstract. Verify the article number. |
| NBTC (India) donor selection guidelines | Whole blood interval 3 months (men), 4 months (women); max 4 per year | Verified by the team |
| Indian blood donor demographics (Kakkar et al. 2008 IJHBT; Agnihotri 2010 AJTS; WHO blood safety report) | Voluntary/replacement blood donor sex split in India (~90-95% male, ~5-10% female; WHO benchmark ~6% female) | Confirmed from Indian blood center studies and WHO reports. Supports P-27 (94% male / 6% female). |

**Citation correction:** the old paper's reference [13] says 'Y. Li et al.'. The study is by Ou-Yang et al. (BMC Public Health 2020).

## B. Calibration targets (sanity checks, not tuning objectives)

- The learned ranker (S5) should lift first-contact acceptance over random by roughly 1.25x-2x (Wu et al. 2022). A much larger lift means the simulated signal is too clean.
- Acceptance probabilities stay well below stated willingness; no group near 100% (Ou-Yang et al. 2020).
- Report the achieved values in the paper. Never tune parameters to make LifeLine win.

## C. Parameters

| ID | Parameter | Value (range) | Source |
|---|---|---|---|
| P-01 | Blood-group frequencies (8 groups) | See `docs/population.yaml`: O+ 0.325313, B+ 0.320983, A+ 0.218005, AB+ 0.076998, O- 0.020287, B- 0.020017, A- 0.013595, AB- 0.004802 | SOURCE: Patidar & Dhiman 2021 for ABO and Rh marginal totals (1.43M donors). The 8-group joint split is DERIVED under an independence assumption, not directly measured. |
| P-02 | Request blood-group mix | Same as P-01; scarcity sweep multiplies rare-type request share 1x-3x | ASSUMPTION |
| P-03 | Donor pool size | 20,000 default; scalability 1k-50k | ASSUMPTION (design choice) |
| P-04 | Zones and density | 40 zones; uneven density (lognormal sigma 0.8); road factor 1.3 (1.2-1.6) | ASSUMPTION |
| P-05 | Request arrivals | Poisson per zone; mean tuned so contention occurs; horizon 30 days | ASSUMPTION |
| P-06 | Urgency mix | CRITICAL 20%, URGENT 40%, ROUTINE 40% | ASSUMPTION |
| P-07 | Deadlines | CRITICAL 2 h, URGENT 6 h, ROUTINE 24 h | ASSUMPTION |
| P-08 | Units per request | 1-4, mean about 2 | ASSUMPTION |
| P-09 | Bank inventory | Per zone, stock as 0%-100% of expected local demand (sweep) | ASSUMPTION |
| P-10 | Cooldown | **90 days (men) / 120 days (women)**, hard exclusion; max 4 whole-blood donations per year | SOURCE: NBTC guidance (verified by the team) |
| P-11 | Days since last donation | Uniform(0, 365) | ASSUMPTION. Context: Ou-Yang 2020 mean return interval about 142-157 days. |
| P-12 | Share of donors with availability flag true | 0.70 (0.5-0.9) | ASSUMPTION |
| P-13 | Location freshness window; share with a current location | 24 h (6-72 h); 30% | ASSUMPTION |
| P-14 | Mean first-contact acceptance probability (active registered donor, emergency request) | 0.30 (0.10-0.50) | ASSUMPTION. Lower-bound context: cold re-recruitment 7.4-8.4% (Ou-Yang 2020). |
| P-15 | Donor heterogeneity | Beta with mean P-14, concentration kappa = 4 (2-10), set so top-vs-bottom willingness groups differ by about 2x | SOURCE-informed: Wu 2022 (1.96x). Calibrate and report. |
| P-16 | Intention-behaviour gap | Acceptance is a noisy function of observables; no group near 100% | SOURCE-informed: Ou-Yang 2020 |
| P-16a | Latent donor reliability | Uniform(0.75, 1.0) implemented in M1/M2/M3 | ASSUMPTION. Literature (Ou-Yang et al. 2020) motivates the general intention-behaviour gap (stated willingness far exceeds actual donation response), but does not establish this exact distribution. Retained as an explicit engineering assumption. |
| P-17 | Response delay | Lognormal, median 30 min, sigma 1.0 (median 10-120 min) | ASSUMPTION |
| P-18 | Travel tolerance (distance at which acceptance halves) | Median 10 km (5-25 km) | ASSUMPTION |
| P-19 | M2 fatigue | Acceptance x 0.8 per contact in the previous 30 days (0.6-0.95) | ASSUMPTION |
| P-20 | M2 time-of-day effect | +/- 20% relative | ASSUMPTION |
| P-21 | Urgency sensitivity (M1, M2) | CRITICAL +15% relative acceptance (0-30%) | ASSUMPTION |
| P-22 | M3 mis-specification | Distance coefficient about 0; hidden zone-affinity effect +/- 0.5 logit, not visible to the engine | ASSUMPTION |
| P-23 | Simulated history | 18 months (6-36); about 4 contacts per donor on average; engine prior Beta(1, 2) | ASSUMPTION |
| P-24 | Attempt budget | 10 contacts per request (5-20) | ASSUMPTION |
| P-25 | Notification expiry | CRITICAL 20 min, URGENT 45 min, ROUTINE 3 h | ASSUMPTION |
| P-26 | Seeds | Reporting 1-30; tuning 1000+ | Design (protocol) |
| P-27 | Donor sex split (used only for the cooldown interval) | 94% male / 6% female (sensitivity range: 70-95% male) | SOURCE-informed: Indian transfusion literature (Agnihotri 2010, Kakkar et al. 2008) and WHO benchmark (~6% female donors). Used strictly to select the cooldown interval (90d men / 120d women); not a ranking feature. |
| P-28 | Default urgency-profile weights (each profile sums to 1) | S6 locked spec (proximity / rarity): CRITICAL 0.85 / 0.15; URGENT 0.65 / 0.35; ROUTINE 0.40 / 0.60. S7 extension (proximity / rarity / responsiveness): CRITICAL 0.60 / 0.10 / 0.30; URGENT 0.45 / 0.25 / 0.30; ROUTINE 0.30 / 0.45 / 0.25 | ASSUMPTION (Human-approved engineering policy choice; not claimed to be empirically optimal weights). Monotonically shifts weight from proximity (CRITICAL, 2h deadline) to rare-type conservation (ROUTINE, 24h deadline). Tuned/sensitivity-swept only on tuning seeds (E6). |

## D. Resolved human items (prior to Phase 5)

- [x] P-01: Retained existing blood-group frequencies. Verified reported ABO and Rh marginal totals against Patidar & Dhiman 2021 (1.43M donors). Documented that the 8-group joint distribution is derived under statistical independence (not directly measured).
- [x] P-27: Revised donor sex split from 85/15 to 94% male / 6% female per Indian blood bank studies (Agnihotri 2010, Kakkar et al. 2008) and WHO national benchmark. Used strictly for cooldown interval assignment; not a ranking feature.
- [x] P-28: Approved S6 and S7 urgency profiles exactly as specified as human-approved engineering policy choices (not empirically optimal weights). Numerical values unchanged.
- [x] P-16a: Approved Uniform(0.75, 1.0) as an explicit engineering assumption representing individual follow-through in the intention-behaviour gap (conceptually motivated by Ou-Yang et al. 2020, but not an empirically established distribution). Removed TODO(human).
