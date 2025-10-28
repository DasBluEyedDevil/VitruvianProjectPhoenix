# Automated Test Runner for Vitruvian Redux (PowerShell)
# This script runs all automated tests and generates reports

Write-Host "========================================" -ForegroundColor Green
Write-Host "Vitruvian Redux - Automated Test Suite" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

Write-Host "[1/5] Cleaning previous build artifacts..." -ForegroundColor Cyan
.\gradlew clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Clean failed" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host ""

Write-Host "[2/5] Running all unit tests..." -ForegroundColor Cyan
.\gradlew test
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Tests failed" -ForegroundColor Red
    Write-Host "Check test reports at: app\build\reports\tests\testDebugUnitTest\index.html" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host ""

Write-Host "[3/5] Generating coverage report..." -ForegroundColor Cyan
.\gradlew jacocoTestReport
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: Coverage report generation failed" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "[4/5] Running lint checks..." -ForegroundColor Cyan
.\gradlew lint
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: Lint checks found issues" -ForegroundColor Yellow
    Write-Host "Check lint report at: app\build\reports\lint-results.html" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "[5/5] Test execution complete!" -ForegroundColor Green
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Test Results Summary" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Test Report:      app\build\reports\tests\testDebugUnitTest\index.html" -ForegroundColor White
Write-Host "Coverage Report:  app\build\reports\jacoco\jacocoTestReport\html\index.html" -ForegroundColor White
Write-Host "Lint Report:      app\build\reports\lint-results.html" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Opening test report in browser..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green

# Open the test report in default browser
$testReport = "app\build\reports\tests\testDebugUnitTest\index.html"
if (Test-Path $testReport) {
    Start-Process $testReport
} else {
    Write-Host "Test report not found at expected location" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Press Enter to exit..." -ForegroundColor Gray
Read-Host

