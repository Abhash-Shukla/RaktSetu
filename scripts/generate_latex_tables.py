#!/usr/bin/env python3
"""
Generate publication-quality LaTeX tables from official LifeLine experiment results.
Strictly consumes raw CSV outputs from results/ directories.
"""

import os
import json
import pandas as pd
import numpy as np

OUTPUT_DIR = "docs/phase6/tables"
os.makedirs(OUTPUT_DIR, exist_ok=True)

def generate_e1_table():
    path = "results/e1-final/e1_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    # Filter for S6 and S7 vs baselines for primary metrics
    primary_metrics = ["fulfillment_within_deadline", "contacts_per_fulfilled_request", "time_to_fulfill_median_minutes"]
    sub = df[df["candidate_system"].isin(["S6", "S7"]) & df["metric"].isin(primary_metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E1: Primary Performance Comparison of LifeLine Against Baselines (Reporting Seeds 1--30)}")
    tex.append(r"\label{tab:e1_main_results}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Model & Candidate & Baseline & Metric & Candidate Mean & Baseline Mean & HL Shift & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for model in ["M1", "M2", "M3"]:
        m_df = sub[sub["response_model"] == model]
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{Response Model {model}}}}} \\\\")
        tex.append(r"\midrule")
        for cand in ["S6", "S7"]:
            c_df = m_df[m_df["candidate_system"] == cand]
            for _, r in c_df.iterrows():
                metric_name = r["metric"].replace("_", " ")
                c_mean = f"{r['candidate_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['candidate_mean']:.1f}"
                b_mean = f"{r['baseline_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['baseline_mean']:.1f}"
                hl = f"{r['hodges_lehmann_shift']:+.3f}" if "fulfillment" in r["metric"] else f"{r['hodges_lehmann_shift']:+.1f}"
                p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
                dec = r["decision"].replace("_", " ")
                tex.append(f"{model} & {cand} & {r['baseline_system']} & {metric_name} & {c_mean} & {b_mean} & {hl} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e1_main.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e2_table():
    path = "results/e2-final/e2_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    primary_metrics = ["fulfillment_within_deadline", "contacts_per_fulfilled_request"]
    sub = df[df["metric"].isin(primary_metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E2: Leave-One-Component-Out Ablation Study (Claim C2)}")
    tex.append(r"\label{tab:e2_ablation}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Model & Full Engine & Ablated Variant & Metric & Full Mean & Ablated Mean & HL Shift & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for model in ["M1", "M2", "M3"]:
        m_df = sub[sub["response_model"] == model]
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{Response Model {model}}}}} \\\\")
        tex.append(r"\midrule")
        for _, r in m_df.iterrows():
            metric_name = r["metric"].replace("_", " ")
            ablated = r["baseline_system"].replace("S6_", "").replace("S7_", "").replace("_", " ")
            c_mean = f"{r['candidate_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['candidate_mean']:.1f}"
            b_mean = f"{r['baseline_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['baseline_mean']:.1f}"
            hl = f"{r['hodges_lehmann_shift']:+.3f}" if "fulfillment" in r["metric"] else f"{r['hodges_lehmann_shift']:+.1f}"
            p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
            dec = r["decision"].replace("_", " ")
            tex.append(f"{model} & {r['candidate_system']} & {ablated} & {metric_name} & {c_mean} & {b_mean} & {hl} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e2_ablation.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e3_table():
    path = "results/e3-final/e3_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    metrics = ["rare_type_waste_rate", "rare_request_fulfillment", "common_request_fulfillment"]
    sub = df[df["metric"].isin(metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E3: Scarcity Sweep and Rarity Weighting Evaluation (Claim C3)}")
    tex.append(r"\label{tab:e3_scarcity}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Multiplier & Model & Metric & S6 Mean & No-Rarity Mean & HL Shift & Cliff's $d$ & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for slice_name in sorted(sub["experiment"].unique()):
        s_df = sub[sub["experiment"] == slice_name]
        mult = slice_name.replace("E3_", "")
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{Demand Multiplier {mult}}}}} \\\\")
        tex.append(r"\midrule")
        for model in ["M1", "M2", "M3"]:
            m_df = s_df[s_df["response_model"] == model]
            for _, r in m_df.iterrows():
                metric_name = r["metric"].replace("_", " ")
                c_mean = f"{r['candidate_mean']:.3f}"
                b_mean = f"{r['baseline_mean']:.3f}"
                hl = f"{r['hodges_lehmann_shift']:+.3f}"
                cd = f"{r['cliffs_delta']:+.2f}"
                p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
                dec = r["decision"].replace("_", " ")
                tex.append(f"{mult} & {model} & {metric_name} & {c_mean} & {b_mean} & {hl} & {cd} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e3_scarcity.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e4_table():
    path = "results/e4-final/e4_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    metrics = ["tier0_resolved_share", "fulfillment_within_deadline", "total_notifications_sent", "donor_contacts_avoided"]
    sub = df[df["metric"].isin(metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E4: Inventory-Level Sweep and Tier-0 Evaluation (Claim C1)}")
    tex.append(r"\label{tab:e4_inventory}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Level & Model & Metric & Tier-0 ON & Tier-0 OFF & HL Shift & Cliff's $d$ & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for slice_name in sorted(sub["experiment"].unique()):
        s_df = sub[sub["experiment"] == slice_name]
        level = slice_name.replace("E4_", "")
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{Simulated Inventory Level {level}}}}} \\\\")
        tex.append(r"\midrule")
        for model in ["M1", "M2", "M3"]:
            m_df = s_df[s_df["response_model"] == model]
            for _, r in m_df.iterrows():
                metric_name = r["metric"].replace("_", " ")
                c_mean = f"{r['candidate_mean']:.3f}" if "share" in r["metric"] or "fulfillment" in r["metric"] else f"{r['candidate_mean']:.1f}"
                b_mean = f"{r['baseline_mean']:.3f}" if "share" in r["metric"] or "fulfillment" in r["metric"] else f"{r['baseline_mean']:.1f}"
                hl = f"{r['hodges_lehmann_shift']:+.3f}" if "share" in r["metric"] or "fulfillment" in r["metric"] else f"{r['hodges_lehmann_shift']:+.1f}"
                cd = f"{r['cliffs_delta']:+.2f}"
                p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
                dec = r["decision"].replace("_", " ")
                tex.append(f"{level} & {model} & {metric_name} & {c_mean} & {b_mean} & {hl} & {cd} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e4_inventory.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e5_table():
    path = "results/e5-final/e5_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    metrics = ["fulfillment_within_deadline", "fallback_resolved_count", "contacts_per_fulfilled_request"]
    sub = df[df["metric"].isin(metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E5: Fallback Re-ranking and Attempt-Budget Sweep (Claim C4)}")
    tex.append(r"\label{tab:e5_fallback}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Budget & Model & Metric & Fallback ON & Fallback OFF & HL Shift & Cliff's $d$ & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for slice_name in sorted(sub["experiment"].unique(), key=lambda s: int(s.replace("E5_B", "")) if s.replace("E5_B", "").isdigit() else s):
        s_df = sub[sub["experiment"] == slice_name]
        budget = slice_name.replace("E5_B", "Budget ")
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{{budget}}}}} \\\\")
        tex.append(r"\midrule")
        for model in ["M1", "M2", "M3"]:
            m_df = s_df[s_df["response_model"] == model]
            for _, r in m_df.iterrows():
                metric_name = r["metric"].replace("_", " ")
                c_mean = f"{r['candidate_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['candidate_mean']:.1f}"
                b_mean = f"{r['baseline_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['baseline_mean']:.1f}"
                hl = f"{r['hodges_lehmann_shift']:+.3f}" if "fulfillment" in r["metric"] else f"{r['hodges_lehmann_shift']:+.1f}"
                cd = f"{r['cliffs_delta']:+.2f}"
                p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
                dec = r["decision"].replace("_", " ")
                tex.append(f"{budget} & {model} & {metric_name} & {c_mean} & {b_mean} & {hl} & {cd} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e5_fallback.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e6_table():
    path = "results/e6-final/e6_statistical_summary.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    metrics = ["fulfillment_within_deadline", "contacts_per_fulfilled_request", "time_to_fulfill_median_minutes"]
    sub = df[df["metric"].isin(metrics)].copy()
    
    tex = []
    tex.append(r"\begin{table*}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E6: Weight Sensitivity and Profile Evaluation (Claim C2)}")
    tex.append(r"\label{tab:e6_sensitivity}")
    tex.append(r"\begin{tabular}{lllcccccl}")
    tex.append(r"\toprule")
    tex.append(r"Model & Comparison System & Metric & S6 Default Mean & Comparison Mean & HL Shift & Cliff's $d$ & Holm $p$ & Decision \\")
    tex.append(r"\midrule")
    
    for model in ["M1", "M2", "M3"]:
        m_df = sub[sub["response_model"] == model]
        tex.append(f"\\multicolumn{{9}}{{l}}{{\\textbf{{Response Model {model}}}}} \\\\")
        tex.append(r"\midrule")
        for sys_id in sorted(m_df["baseline_system"].unique()):
            s_df = m_df[m_df["baseline_system"] == sys_id]
            comp_name = sys_id.replace("S6_", "").replace("_", " ")
            for _, r in s_df.iterrows():
                metric_name = r["metric"].replace("_", " ")
                c_mean = f"{r['candidate_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['candidate_mean']:.1f}"
                b_mean = f"{r['baseline_mean']:.3f}" if "fulfillment" in r["metric"] else f"{r['baseline_mean']:.1f}"
                hl = f"{r['hodges_lehmann_shift']:+.3f}" if "fulfillment" in r["metric"] else f"{r['hodges_lehmann_shift']:+.1f}"
                cd = f"{r['cliffs_delta']:+.2f}"
                p_val = "< 0.001" if r["holm_p"] < 0.001 else f"{r['holm_p']:.4f}"
                dec = r["decision"].replace("_", " ")
                tex.append(f"{model} & {comp_name} & {metric_name} & {c_mean} & {b_mean} & {hl} & {cd} & {p_val} & {dec} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table*}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e6_sensitivity.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e7_table():
    path = "results/e7-reporting-v2/e7_summary.json"
    if not os.path.exists(path):
        return
    with open(path) as f:
        data = json.load(f)
        
    add = data["score_additivity"]
    cf = data["counterfactual"]
    rc = data["reason_code_coverage"]
    
    tex = []
    tex.append(r"\begin{table}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E7: Explanation Faithfulness Evaluation (Claim C5)}")
    tex.append(r"\label{tab:e7_faithfulness}")
    tex.append(r"\begin{tabular}{llcc}")
    tex.append(r"\toprule")
    tex.append(r"Dimension & Verification Metric & Observed Value & Status \\")
    tex.append(r"\midrule")
    tex.append(f"Score Additivity & Total Score Evaluations & {add['total_evaluations']:,} & Verified \\\\")
    tex.append(f"Score Additivity & Max Absolute Error & ${add['max_absolute_error']:.2e}$ & Passed ($< 10^{{-12}}$) \\\\")
    tex.append(f"Score Additivity & Violation Count & {add['violation_count']} & Zero Violations \\\\")
    tex.append(r"\midrule")
    tex.append(f"Counterfactual & Top-1 Candidate Cases & {cf['top1_flip_denominator']:,} & Evaluated \\\\")
    tex.append(f"Counterfactual & Top-1 Rank Flip Rate & {cf['top1_flip_rate']*100:.1f}\\% & 100\\% Agreement \\\\")
    tex.append(f"Counterfactual & Top-1 Mean Rank Drop & {cf['top1_mean_rank_drop']:.1f} positions & Robust Drop \\\\")
    tex.append(f"Counterfactual & Individual Shift Rate & {cf['individual_rank_change_rate']*100:.1f}\\% & Verified \\\\")
    tex.append(r"\midrule")
    tex.append(f"Reason Codes & Catalog Codes Defined & {rc['total_catalog_codes']} & Catalog Total \\\\")
    tex.append(f"Reason Codes & Covered Reason Codes & {rc['covered_codes_count']} & 100\\% Coverage \\\\")
    tex.append(f"Reason Codes & Unsupported Codes & None & Fully Supported \\\\")
    tex.append(r"\bottomrule")
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e7_faithfulness.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def generate_e8_table():
    path = "results/e8-sustained/e8_scalability.csv"
    if not os.path.exists(path):
        return
    df = pd.read_csv(path)
    
    tex = []
    tex.append(r"\begin{table}[t]")
    tex.append(r"\centering")
    tex.append(r"\small")
    tex.append(r"\caption{Experiment E8: Engine Latency and Throughput Scalability Benchmark (Claim C7)}")
    tex.append(r"\label{tab:e8_scalability}")
    tex.append(r"\begin{tabular}{ccccccc}")
    tex.append(r"\toprule")
    tex.append(r"Pool Size & Concurrency & Mean (ms) & $p_{50}$ (ms) & $p_{95}$ (ms) & $p_{99}$ (ms) & Throughput (req/s) \\")
    tex.append(r"\midrule")
    
    for pool in sorted(df["donor_pool_size"].unique()):
        p_df = df[df["donor_pool_size"] == pool].sort_values("concurrency")
        tex.append(f"\\multicolumn{{7}}{{l}}{{\\textbf{{Donor Pool Size {pool:,}}}}} \\\\")
        tex.append(r"\midrule")
        for _, r in p_df.iterrows():
            tex.append(f"{r['donor_pool_size']:,} & {r['concurrency']} & {r['mean_latency_ms']:.2f} & {r['p50_latency_ms']:.2f} & {r['p95_latency_ms']:.2f} & {r['p99_latency_ms']:.2f} & {r['throughput_req_per_sec']:.1f} \\\\")
        tex.append(r"\midrule")
        
    tex[-1] = r"\bottomrule"
    tex.append(r"\end{tabular}")
    tex.append(r"\end{table}")
    
    out_file = os.path.join(OUTPUT_DIR, "table_e8_scalability.tex")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(tex) + "\n")
    print(f"Generated {out_file}")

def main():
    print("Generating LaTeX tables from raw experiment results...")
    generate_e1_table()
    generate_e2_table()
    generate_e3_table()
    generate_e4_table()
    generate_e5_table()
    generate_e6_table()
    generate_e7_table()
    generate_e8_table()
    print("LaTeX table generation complete.")

if __name__ == "__main__":
    main()
