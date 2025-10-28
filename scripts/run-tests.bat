@echo off
REM Automated Test Runner for Vitruvian Redux
REM This script runs all automated tests and generates reports

echo ========================================
echo Vitruvian Redux - Automated Test Suite
echo ========================================
echo.

REM Set colors (if supported)
color 0A

echo [1/5] Cleaning previous build artifacts...
call gradlew clean
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Clean failed
    pause
    exit /b 1
)
echo.

echo [2/5] Running all unit tests...
call gradlew test
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Tests failed
    echo Check test reports at: app\build\reports\tests\testDebugUnitTest\index.html
    pause
    exit /b 1
)
echo.

echo [3/5] Generating coverage report...
call gradlew jacocoTestReport
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Coverage report generation failed
)
echo.

echo [4/5] Running lint checks...
call gradlew lint
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Lint checks found issues
    echo Check lint report at: app\build\reports\lint-results.html
)
echo.

echo [5/5] Test execution complete!
echo.
echo ========================================
echo Test Results Summary
echo ========================================
echo.
echo Test Report:      app\build\reports\tests\testDebugUnitTest\index.html
echo Coverage Report:  app\build\reports\jacoco\jacocoTestReport\html\index.html
echo Lint Report:      app\build\reports\lint-results.html
echo.
echo ========================================
echo Opening test report in browser...
echo ========================================

REM Open the test report in default browser
start "" "app\build\reports\tests\testDebugUnitTest\index.html"

echo.
echo Press any key to exit...
pause >nul

