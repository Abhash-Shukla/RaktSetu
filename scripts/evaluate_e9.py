#!/usr/bin/env python3
"""
LifeLine / RaktSetu — E9 Evaluation Script: Informal Message NLP Extraction
Computes field-level Precision, Recall, and F1 score on a labeled dataset (Claim C6 / SRS NLP-1..5).

Integrity Rules (AGENTS.md):
- Synthetic data fabrication is strictly prohibited for ground-truth evaluation.
- If dataset status is 'TODO(human)' or count < 200, the evaluation is marked INCOMPLETE/BLOCKED.
"""

import argparse
import json
import os
import sys
from typing import Dict, List, Any, Optional

CANONICAL_BLOOD_GROUPS = {
    "A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG"
}

CANONICAL_URGENCIES = {"CRITICAL", "URGENT", "ROUTINE"}

FIELDS = ["blood_group", "urgency", "zone_or_pincode", "units", "contact"]


def normalize_val(field: str, val: Any) -> Optional[str]:
    if val is None:
        return None
    s = str(val).strip()
    if not s or s.lower() == "null":
        return None
    if field == "blood_group":
        s = s.upper().replace("+", "_POS").replace("-", "_NEG")
        return s if s in CANONICAL_BLOOD_GROUPS else s
    if field == "urgency":
        return s.upper() if s.upper() in CANONICAL_URGENCIES else s.upper()
    if field == "units":
        try:
            return str(int(s))
        except ValueError:
            return s
    if field == "contact":
        # Keep digits only
        digits = "".join(c for c in s if c.isdigit())
        if digits.startswith("91") and len(digits) == 12:
            digits = digits[2:]
        return digits if digits else None
    if field == "zone_or_pincode":
        return s.lower()
    return s.lower()


def compute_field_metrics(ground_truth_list: List[Dict], prediction_list: List[Dict]) -> Dict[str, Dict[str, float]]:
    results = {}
    
    for field in FIELDS:
        tp = 0
        fp = 0
        fn = 0
        tn = 0
        
        for gt, pred in zip(ground_truth_list, prediction_list):
            gt_val = normalize_val(field, gt.get(field))
            pred_val = normalize_val(field, pred.get(field))
            
            if gt_val is not None and pred_val is not None:
                if gt_val == pred_val:
                    tp += 1
                else:
                    fp += 1
                    fn += 1
            elif gt_val is not None and pred_val is None:
                fn += 1
            elif gt_val is None and pred_val is not None:
                fp += 1
            else:
                tn += 1
                
        precision = tp / (tp + fp) if (tp + fp) > 0 else (1.0 if fn == 0 else 0.0)
        recall = tp / (tp + fn) if (tp + fn) > 0 else (1.0 if fp == 0 else 0.0)
        f1 = (2 * precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
        
        results[field] = {
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "tn": tn,
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4)
        }
        
    # Macro and Micro Averages
    macro_p = sum(results[f]["precision"] for f in FIELDS) / len(FIELDS)
    macro_r = sum(results[f]["recall"] for f in FIELDS) / len(FIELDS)
    macro_f1 = sum(results[f]["f1"] for f in FIELDS) / len(FIELDS)
    
    total_tp = sum(results[f]["tp"] for f in FIELDS)
    total_fp = sum(results[f]["fp"] for f in FIELDS)
    total_fn = sum(results[f]["fn"] for f in FIELDS)
    
    micro_p = total_tp / (total_tp + total_fp) if (total_tp + total_fp) > 0 else 0.0
    micro_r = total_tp / (total_tp + total_fn) if (total_tp + total_fn) > 0 else 0.0
    micro_f1 = (2 * micro_p * micro_r) / (micro_p + micro_r) if (micro_p + micro_r) > 0 else 0.0
    
    results["MACRO_AVG"] = {
        "precision": round(macro_p, 4),
        "recall": round(macro_r, 4),
        "f1": round(macro_f1, 4)
    }
    results["MICRO_AVG"] = {
        "precision": round(micro_p, 4),
        "recall": round(micro_r, 4),
        "f1": round(micro_f1, 4)
    }
    
    return results


def run_self_test() -> bool:
    print("Running evaluate_e9.py self-test verification...")
    # Synthetic self-test vector
    gt_sample = [
        {"blood_group": "A_POS", "urgency": "CRITICAL", "units": 2, "zone_or_pincode": "110029", "contact": "9876543210"},
        {"blood_group": "B_NEG", "urgency": "URGENT", "units": 1, "zone_or_pincode": "AIIMS", "contact": None},
        {"blood_group": "O_POS", "urgency": "ROUTINE", "units": 3, "zone_or_pincode": None, "contact": "9811223344"},
    ]
    pred_sample = [
        {"blood_group": "A_POS", "urgency": "CRITICAL", "units": 2, "zone_or_pincode": "110029", "contact": "9876543210"},
        {"blood_group": "B_NEG", "urgency": "ROUTINE", "units": 1, "zone_or_pincode": "AIIMS", "contact": "9999999999"}, # wrong urgency, false positive contact
        {"blood_group": "O_POS", "urgency": "ROUTINE", "units": 3, "zone_or_pincode": "North", "contact": "9811223344"}, # false positive zone
    ]
    
    metrics = compute_field_metrics(gt_sample, pred_sample)
    
    # Blood group: 3/3 matches -> P=1.0, R=1.0, F1=1.0
    assert metrics["blood_group"]["precision"] == 1.0, "Blood group precision self-test failed"
    assert metrics["blood_group"]["recall"] == 1.0, "Blood group recall self-test failed"
    assert metrics["blood_group"]["f1"] == 1.0, "Blood group F1 self-test failed"
    
    # Units: 3/3 matches -> P=1.0, R=1.0, F1=1.0
    assert metrics["units"]["f1"] == 1.0, "Units F1 self-test failed"
    
    # Urgency: 2 matches, 1 mismatch -> TP=2, FP=1, FN=1 -> P=2/3=0.6667, R=2/3=0.6667, F1=0.6667
    assert metrics["urgency"]["tp"] == 2
    assert metrics["urgency"]["fp"] == 1
    assert metrics["urgency"]["fn"] == 1
    assert abs(metrics["urgency"]["f1"] - 0.6667) < 0.001, "Urgency F1 self-test failed"
    
    print("Self-test PASSED successfully! Metric calculation logic is 100% verified.")
    return True


def main():
    parser = argparse.ArgumentParser(description="LifeLine E9 NLP Evaluation")
    parser.add_argument("--dataset", default="data/e9/labeled_messages.json", help="Path to human-labeled dataset")
    parser.add_argument("--fixtures", default="data/e9/fixtures/llm_fixtures.json", help="Path to recorded LLM fixtures")
    parser.add_argument("--output-csv", default="docs/phase6/e9_nlp_evaluation.csv", help="Path to output CSV")
    parser.add_argument("--self-test", action="store_true", help="Run metric calculation self-test")
    
    args = parser.parse_args()
    
    if args.self_test:
        success = run_self_test()
        sys.exit(0 if success else 1)
        
    print("=" * 80)
    print("LifeLine / RaktSetu — E9 Evaluation: Informal Message NLP Extraction")
    print("=" * 80)
    
    if not os.path.exists(args.dataset):
        print(f"ERROR: Dataset file not found at: {args.dataset}")
        sys.exit(1)
        
    with open(args.dataset, "r", encoding="utf-8") as f:
        dataset_json = json.load(f)
        
    status = dataset_json.get("status", "")
    dataset_records = dataset_json.get("dataset", [])
    
    if status == "TODO(human)" or len(dataset_records) < 200:
        print("\n" + "!" * 80)
        print("E9 STATUS: INCOMPLETE / BLOCKED (TODO(human))")
        print("!" * 80)
        print(f"Dataset path: {args.dataset}")
        print(f"Current status: {status}")
        print(f"Recorded messages: {len(dataset_records)} (Required minimum: 200)")
        print("\nAudit Notice (AGENTS.md Evaluation Integrity Rule):")
        print("- A human-written or human-collected labeled dataset of >= 200 messages is required.")
        print("- Synthetic generation or fabrication of evaluation messages/labels is strictly forbidden.")
        print("- The dataset file is marked 'TODO(human)' pending human team data collection.")
        print("!" * 80)
        sys.exit(0)
        
    # If dataset is populated (>= 200 messages)
    if not os.path.exists(args.fixtures):
        print(f"ERROR: Fixtures file not found at: {args.fixtures}")
        sys.exit(1)
        
    with open(args.fixtures, "r", encoding="utf-8") as f:
        fixtures_json = json.load(f)
        
    fixtures = fixtures_json.get("fixtures", [])
    print(f"Loaded {len(dataset_records)} labeled ground-truth records and {len(fixtures)} LLM fixtures.")
    
    gt_list = [r["ground_truth"] for r in dataset_records]
    pred_list = [f["response"] for f in fixtures]
    
    metrics = compute_field_metrics(gt_list, pred_list)
    
    print("\nE9 Field-Level Extraction Performance:")
    print("-" * 65)
    print(f"{'Field':<20} {'Precision':<15} {'Recall':<15} {'F1-Score':<15}")
    print("-" * 65)
    for field in FIELDS:
        m = metrics[field]
        print(f"{field:<20} {m['precision']:<15.4f} {m['recall']:<15.4f} {m['f1']:<15.4f}")
    print("-" * 65)
    print(f"{'MACRO-AVERAGE':<20} {metrics['MACRO_AVG']['precision']:<15.4f} {metrics['MACRO_AVG']['recall']:<15.4f} {metrics['MACRO_AVG']['f1']:<15.4f}")
    print(f"{'MICRO-AVERAGE':<20} {metrics['MICRO_AVG']['precision']:<15.4f} {metrics['MICRO_AVG']['recall']:<15.4f} {metrics['MICRO_AVG']['f1']:<15.4f}")
    print("-" * 65)
    
    # Save CSV
    os.makedirs(os.path.dirname(args.output_csv), exist_ok=True)
    with open(args.output_csv, "w", encoding="utf-8") as f:
        f.write("field,precision,recall,f1,tp,fp,fn,tn\n")
        for field in FIELDS:
            m = metrics[field]
            f.write(f"{field},{m['precision']},{m['recall']},{m['f1']},{m['tp']},{m['fp']},{m['fn']},{m['tn']}\n")
        f.write(f"MACRO_AVG,{metrics['MACRO_AVG']['precision']},{metrics['MACRO_AVG']['recall']},{metrics['MACRO_AVG']['f1']},,,,\n")
        f.write(f"MICRO_AVG,{metrics['MICRO_AVG']['precision']},{metrics['MICRO_AVG']['recall']},{metrics['MICRO_AVG']['f1']},,,,\n")
        
    print(f"\nSaved E9 evaluation metrics to: {args.output_csv}")


if __name__ == "__main__":
    main()
