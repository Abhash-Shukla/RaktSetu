import json
import pandas as pd

print('=== E1 Check ===')
df1 = pd.read_csv('results/e1-final/e1_results.csv')
print(f'Rows: {len(df1)}, Systems: {list(df1["system"].unique())}, Models: {list(df1["response_model"].unique())}, Seeds: {len(df1["seed"].unique())}')
with open('results/e1-final/run_manifest.json') as f:
    m1 = json.load(f)
print(f'E1 Manifest: runs={m1.get("total_runs")}, duration={m1.get("duration_seconds")}s')

print('\n=== E2 Check ===')
df2 = pd.read_csv('results/e2-final/e2_results.csv')
print(f'Rows: {len(df2)}, Systems: {list(df2["system"].unique())}, Models: {list(df2["response_model"].unique())}, Seeds: {len(df2["seed"].unique())}')
with open('results/e2-final/run_manifest.json') as f:
    m2 = json.load(f)
print(f'E2 Manifest: runs={m2.get("total_runs")}, duration={m2.get("duration_seconds")}s')

print('\n=== E3 Check ===')
df3 = pd.read_csv('results/e3-final/e3_results.csv')
print(f'Rows: {len(df3)}, Systems: {list(df3["system"].unique())}, Models: {list(df3["response_model"].unique())}, Seeds: {len(df3["seed"].unique())}')
with open('results/e3-final/run_manifest.json') as f:
    m3 = json.load(f)
print(f'E3 Manifest: runs={m3.get("total_runs")}, duration={m3.get("duration_seconds")}s')

print('\n=== E4 Check ===')
if pd.io.common.file_exists('results/e4-final/e4_results.csv'):
    df4 = pd.read_csv('results/e4-final/e4_results.csv')
    print(f'Rows: {len(df4)}, Systems: {list(df4["system"].unique())}, Models: {list(df4["response_model"].unique())}, Seeds: {len(df4["seed"].unique())}')
    with open('results/e4-final/run_manifest.json') as f:
        m4 = json.load(f)
    print(f'E4 Manifest: runs={m4.get("total_runs")}, duration={m4.get("duration_seconds")}s')

print('\n=== E5 Check ===')
if pd.io.common.file_exists('results/e5-final/e5_results.csv'):
    df5 = pd.read_csv('results/e5-final/e5_results.csv')
    print(f'Rows: {len(df5)}, Systems: {list(df5["system"].unique())}, Models: {list(df5["response_model"].unique())}, Seeds: {len(df5["seed"].unique())}')
    with open('results/e5-final/run_manifest.json') as f:
        m5 = json.load(f)
    print(f'E5 Manifest: runs={m5.get("total_runs")}, duration={m5.get("duration_seconds")}s')

print('\n=== E6 Check ===')
if pd.io.common.file_exists('results/e6-final/e6_results.csv'):
    df6 = pd.read_csv('results/e6-final/e6_results.csv')
    print(f'Rows: {len(df6)}, Systems: {list(df6["system"].unique())}, Models: {list(df6["response_model"].unique())}, Seeds: {len(df6["seed"].unique())}')
    with open('results/e6-final/run_manifest.json') as f:
        m6 = json.load(f)
    print(f'E6 Manifest: runs={m6.get("total_runs")}, duration={m6.get("duration_seconds")}s')

print('\n=== E7 Check (reporting-v2) ===')
with open('results/e7-reporting-v2/e7_summary.json') as f:
    m7 = json.load(f)
print(f'E7 Overall Passed: {m7.get("overall_passed")}')
print(f'Additivity: total_evals={m7["score_additivity"].get("total_evaluations")}, violations={m7["score_additivity"].get("violation_count")}, max_err={m7["score_additivity"].get("max_absolute_error")}')
print(f'Counterfactual: total_cases={m7["counterfactual"].get("total_cases")}, flip_rate={m7["counterfactual"].get("flip_rate_pct")}%')
print(f'Reason codes: {m7["reason_code_coverage"].get("observed_reasons_count")}/{m7["reason_code_coverage"].get("catalog_reasons_count")} covered, unobserved={m7["reason_code_coverage"].get("unobserved_reasons")}')

print('\n=== E8 Check (sustained) ===')
df8 = pd.read_csv('results/e8-sustained/e8_scalability.csv')
print(f'E8 Grid cells: {len(df8)}, Pool sizes: {list(df8["donor_pool_size"].unique())}, Concurrency: {list(df8["concurrency"].unique())}')
print(f'Latency range (p95): {df8["p95_latency_ms"].min():.2f} ms to {df8["p95_latency_ms"].max():.2f} ms')
print(f'Throughput range: {df8["throughput_req_per_sec"].min():.1f} to {df8["throughput_req_per_sec"].max():.1f} req/s')
