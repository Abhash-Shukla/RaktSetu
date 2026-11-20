# PowerShell script to reproduce Phase 6 Research Artifacts
Write-Host "Running LifeLine Research Artifacts Pipeline..." -ForegroundColor Cyan
python scripts/make_results.py
if ($LASTEXITCODE -ne 0) {
    Write-Error "Artifact generation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
Write-Host "Artifact generation succeeded." -ForegroundColor Green
