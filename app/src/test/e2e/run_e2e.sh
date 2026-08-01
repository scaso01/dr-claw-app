#!/bin/bash
# Run Dr. CLAW E2E tests using uiautomator2 + pytest.
#
# Prerequisites:
#   - Emulator running (emu-start)
#   - App installed (emu-build or gradlew installDebug)
#   - pip install uiautomator2 pytest
#
# Usage:
#   ./run_e2e.sh                          # run all tests
#   ./run_e2e.sh -k "TestBrainScreen"     # run one class
#   ./run_e2e.sh -k "test_app_launches"   # run one test
#   ./run_e2e.sh --maxfail=3              # stop after 3 failures
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Verify emulator is connected
if ! adb devices 2>/dev/null | grep -q "device$"; then
    echo "ERROR: No emulator/device connected. Run 'emu-start' first."
    exit 1
fi

# Verify app is installed
if ! adb shell pm list packages 2>/dev/null | grep -q "com.scaso.drclawapp"; then
    echo "ERROR: Dr. CLAW app not installed. Run 'emu-build' first."
    exit 1
fi

# Verify Python dependencies
python -c "import uiautomator2" 2>/dev/null || {
    echo "ERROR: uiautomator2 not installed. Run: pip install uiautomator2"
    exit 1
}
python -c "import pytest" 2>/dev/null || {
    echo "ERROR: pytest not installed. Run: pip install pytest"
    exit 1
}

# Create screenshots directory
mkdir -p "$SCRIPT_DIR/screenshots"

echo "=== Dr. CLAW E2E Tests ==="
echo "  Device: $(adb devices | grep 'device$' | head -1 | cut -f1)"
echo "  Screenshots: $SCRIPT_DIR/screenshots/"
echo ""

cd "$SCRIPT_DIR"
python -m pytest test_drclaw_e2e.py "$@" -v --tb=short
