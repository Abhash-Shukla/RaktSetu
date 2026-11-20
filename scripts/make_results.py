#!/usr/bin/env python3
"""
Master reproduction script for LifeLine / RaktSetu Phase 6 Research Artifacts.
Regenerates results_summary.md, LaTeX tables, and publication-quality PDF figures
strictly from raw experiment CSV outputs in results/.
"""

import sys
import subprocess

def run_step(desc, cmd):
    print(f"\n========================================================")
    print(f"--> {desc}")
    print(f"========================================================")
    res = subprocess.run(cmd, shell=True)
    if res.returncode != 0:
        print(f"ERROR: Step failed with return code {res.returncode}")
        sys.exit(res.returncode)

def main():
    print("LifeLine Research Artifacts Reproduction Pipeline")
    print("Operating on validated raw outputs in results/ ...")
    
    run_step("Generating Consolidated Results Summary", "python scripts/analyze_results.py")
    run_step("Generating LaTeX Tables", "python scripts/generate_latex_tables.py")
    run_step("Generating Publication-Quality PDF Figures", "python scripts/generate_pdf_figures.py")
    
    print("\n========================================================")
    print("SUCCESS: All Phase 6 research artifacts regenerated successfully!")
    print("Artifacts located at:")
    print("  - docs/phase6/results_summary.md")
    print("  - docs/phase6/tables/*.tex")
    print("  - docs/phase6/figures/*.pdf")
    print("========================================================\n")

if __name__ == "__main__":
    main()
