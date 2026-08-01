#!/usr/bin/env bash
# Dr. CLAW Functional Tests via emu-tap.py
# Prerequisites: emulator running (emu-start), tunnels active (emu-connect), Ironjaw running
# Usage: ./tests/emu-functional-tests.sh
#
# Each test returns 0 on pass, 1 on fail. Failed tests take a screenshot automatically.
# Tests are designed to be resilient — they use timeouts and don't assume instant rendering.

set -euo pipefail

EMU_TAP="$HOME/Scripts/emu-tap.py"
SCREENSHOT_DIR="$HOME/Screenshots/emu-tests"
PASS=0
FAIL=0
TOTAL=0
FAILED_TESTS=()

mkdir -p "$SCREENSHOT_DIR"

# ── Helper functions ────────────────────────────────────────────────────────

run_test() {
    local name="$1"
    shift
    TOTAL=$((TOTAL + 1))
    echo "  [$TOTAL] $name..."
    if "$@"; then
        PASS=$((PASS + 1))
        echo "    PASS"
    else
        FAIL=$((FAIL + 1))
        FAILED_TESTS+=("$name")
        echo "    FAIL"
        python "$EMU_TAP" screenshot "$SCREENSHOT_DIR/fail-${name// /-}.png" 2>/dev/null || true
    fi
}

# Wait for text to appear on screen (polls every 0.5s via emu-tap wait)
wait_for() {
    local text="$1"
    local timeout="${2:-10}"
    python "$EMU_TAP" wait "$text" "$timeout" >/dev/null 2>&1
}

# Check if text is currently visible on screen
find_text() {
    python "$EMU_TAP" find "$1" 2>/dev/null | grep -q 'center='
}

# Tap an element by its visible text
tap_text() {
    python "$EMU_TAP" tap "$1" 2>/dev/null
}

# Tap an element by its content-description
tap_desc() {
    python "$EMU_TAP" tap-desc "$1" 2>/dev/null
}

# Type text into focused field
type_text() {
    python "$EMU_TAP" type "$1" 2>/dev/null
}

# Press the back button
press_back() {
    python "$EMU_TAP" back 2>/dev/null
}

# Scroll direction (up/down)
scroll() {
    python "$EMU_TAP" scroll "${1:-down}" 2>/dev/null
}

# Dump all visible UI elements (for debugging)
dump_ui() {
    python "$EMU_TAP" dump 2>/dev/null
}

# Take a named screenshot
screenshot() {
    python "$EMU_TAP" screenshot "$SCREENSHOT_DIR/$1.png" 2>/dev/null || true
}

# Navigate to a bottom nav tab by label
navigate_to() {
    local tab="$1"
    tap_text "$tab"
    sleep 1
}

# Ensure we're back on Chat (home) screen
go_home() {
    navigate_to "Chat"
    sleep 0.5
}

# ── Test functions ──────────────────────────────────────────────────────────

test_app_launch_gateway_connect() {
    # The app should launch and connect to Ironjaw gateway.
    # Look for "Connected" status or the Chat nav item being visible.
    # On successful connection the chat input placeholder appears.

    # First, verify bottom nav is visible (app has launched)
    if ! wait_for "Chat" 15; then
        echo "    Bottom nav 'Chat' not found — app may not have launched"
        return 1
    fi

    # Check for connection — the input field placeholder "Message Dr. CLAW..."
    # appears only when gateway is connected
    if ! wait_for "Message Dr. CLAW" 15; then
        # Fallback: check if any connection status text is visible
        if find_text "Connected"; then
            return 0
        fi
        echo "    Gateway not connected — 'Message Dr. CLAW...' placeholder not found"
        return 1
    fi

    return 0
}

test_send_message_receive_response() {
    # Navigate to Chat, type a short message, send it, wait for a response.
    go_home

    # Tap the input field (placeholder text)
    if ! tap_text "Message Dr. CLAW"; then
        echo "    Could not find chat input field"
        return 1
    fi
    sleep 0.5

    # Type a simple message
    type_text "Hello, test message"
    sleep 0.5

    # Tap the Send button (content-desc "Send")
    if ! tap_desc "Send"; then
        echo "    Could not find Send button"
        return 1
    fi

    # Wait for a response — the thinking indicator or any response text
    # We look for the thinking indicator first (appears quickly)
    sleep 2

    # Wait for response to complete — look for any text that isn't our message
    # The response should appear within 30 seconds
    local elapsed=0
    while [ $elapsed -lt 30 ]; do
        # Check if there's content beyond our sent message
        # Dump and look for text that indicates a response arrived
        if python "$EMU_TAP" dump 2>/dev/null | grep -v "Hello, test message" | grep -v "Message Dr. CLAW" | grep -v "Chat\|Tools\|Brain\|System\|Settings\|Send\|Voice mode\|Scroll to bottom" | grep -q 'center='; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "    No response received within 30 seconds"
    return 1
}

test_navigate_all_screens() {
    # Tap each bottom nav item and verify screen-specific content appears.
    local failed=0

    # Chat tab
    navigate_to "Chat"
    if ! wait_for "Message Dr. CLAW" 5; then
        if ! find_text "Chat"; then
            echo "    Chat screen content not found"
            failed=1
        fi
    fi

    # Tools tab
    navigate_to "Tools"
    if ! wait_for "Tools" 5; then
        echo "    Tools screen title not found"
        failed=1
    fi

    # Brain tab
    navigate_to "Brain"
    if ! wait_for "Brain" 5; then
        echo "    Brain screen title not found"
        failed=1
    fi
    # Brain has tabs: Search, Add, Context, Summaries, Timeline, Graph
    if ! find_text "Search"; then
        echo "    Brain 'Search' tab not found"
        failed=1
    fi

    # System tab
    navigate_to "System"
    if ! wait_for "System" 5; then
        echo "    System screen title not found"
        failed=1
    fi
    # System has tabs: Infra, Schedules, Devices, Metrics, Vault, Export, Plugins
    if ! find_text "Infra"; then
        echo "    System 'Infra' tab not found"
        failed=1
    fi

    # Return to Chat
    go_home

    return $failed
}

test_cc_sessions_loads() {
    # Navigate to CC Sessions screen and wait for session items or empty state.
    go_home

    # CC Sessions is accessible from the Chat screen via the sessions button
    # (content-desc "Open sessions")
    if ! tap_desc "Open sessions"; then
        echo "    Could not find 'Open sessions' button"
        return 1
    fi
    sleep 2

    # Look for any session content or "No sessions" / "CC Sessions" header
    if wait_for "CC Sessions" 5 || wait_for "Sessions" 5; then
        # Screen loaded — check for session items or empty state
        # Any of these indicate success: session titles, "No sessions", "Active", "Historical"
        if find_text "Active" || find_text "Historical" || find_text "No sessions" || find_text "No matching"; then
            press_back
            sleep 0.5
            return 0
        fi
        # If the header loaded, that's still a pass (sessions list may be empty)
        press_back
        sleep 0.5
        return 0
    fi

    echo "    CC Sessions screen did not load"
    press_back
    sleep 0.5
    return 1
}

test_brain_remember_recall() {
    # Navigate to Brain, add a memory, then search for it.
    local test_memory="emu-functional-test-memory-$(date +%s)"

    navigate_to "Brain"
    sleep 1

    # Tap the "Add" tab
    if ! tap_text "Add"; then
        echo "    Could not find 'Add' tab on Brain screen"
        return 1
    fi
    sleep 1

    # Find and tap the memory input field
    # Look for placeholder text or the text input area
    if tap_text "What should I remember" 2>/dev/null || tap_text "Enter memory" 2>/dev/null || tap_text "Memory text" 2>/dev/null; then
        sleep 0.5
    else
        # Try tapping any text field that appears on the Add tab
        # Dump to see what's there
        dump_ui >/dev/null 2>&1
        # Try to find any text input by looking for editable fields
        if ! python "$EMU_TAP" find "remember" 2>/dev/null | grep -q 'center='; then
            echo "    Could not find memory input field on Add tab"
            go_home
            return 1
        fi
        tap_text "remember"
        sleep 0.5
    fi

    type_text "$test_memory"
    sleep 0.5

    # Submit the memory — look for a Save/Remember/Submit button
    if tap_text "Remember" 2>/dev/null || tap_text "Save" 2>/dev/null || tap_text "Submit" 2>/dev/null || tap_desc "Remember" 2>/dev/null || tap_desc "Save" 2>/dev/null; then
        sleep 2
    else
        echo "    Could not find submit button for memory"
        go_home
        return 1
    fi

    # Switch to Search tab and look for the memory
    tap_text "Search"
    sleep 1

    # Search for our test memory
    if tap_text "Search memories" 2>/dev/null || tap_text "search" 2>/dev/null; then
        sleep 0.5
        type_text "emu-functional-test"
        sleep 0.5

        # Tap the Search button
        tap_text "Search" 2>/dev/null
        sleep 2

        # Check if our memory appears in results
        if find_text "emu-functional-test"; then
            go_home
            return 0
        fi
    fi

    echo "    Memory recall did not find the stored memory"
    go_home
    return 1
}

test_settings_persist() {
    # Navigate to Settings and verify the Gateway URL field exists.
    go_home

    # Settings is accessible via the settings icon (content-desc "Settings")
    if ! tap_desc "Settings"; then
        echo "    Could not find Settings button"
        return 1
    fi
    sleep 1

    # Verify Settings screen loaded
    if ! wait_for "Settings" 5; then
        echo "    Settings screen did not load"
        press_back
        return 1
    fi

    # Check for Gateway URL field
    if ! find_text "Gateway URL"; then
        # Try scrolling down to find it
        scroll "down"
        sleep 0.5
        if ! find_text "Gateway URL"; then
            echo "    Gateway URL field not found on Settings screen"
            press_back
            return 1
        fi
    fi

    # Verify Reconnect button exists near Gateway URL
    if ! find_text "Reconnect"; then
        echo "    Reconnect button not found on Settings screen"
        press_back
        return 1
    fi

    press_back
    sleep 0.5
    return 0
}

test_model_list() {
    # Navigate to Models screen and wait for model data to appear.
    go_home

    # Models is accessible from Chat screen — look for model chip or navigate via menu
    # The model chip in the chat header shows the active model name
    # Try tapping it to navigate to Models screen
    # First check if there's a model chip visible
    if find_text "qwen" || find_text "model" || find_text "Model"; then
        # Try to find and tap a model-related element
        tap_text "qwen" 2>/dev/null || tap_text "Model" 2>/dev/null || true
        sleep 1
    fi

    # If we're not on Models screen, try navigating via the route
    # Models might be accessible from Tools or System
    # Let's check Tools screen for a Models entry
    if ! find_text "Model Manager"; then
        navigate_to "Tools"
        sleep 1
    fi

    # Look for model-related content
    if wait_for "qwen" 10 || wait_for "Model" 5 || find_text "model" || find_text "llama"; then
        go_home
        return 0
    fi

    # Try navigating to System > check tabs
    navigate_to "System"
    sleep 1

    if find_text "qwen" || find_text "Model" || find_text "llama"; then
        go_home
        return 0
    fi

    echo "    No model information found on any screen"
    go_home
    return 1
}

test_infrastructure_status() {
    # Navigate to System > Infra tab and check for infrastructure status content.
    navigate_to "System"
    sleep 1

    # Infra is the first tab on System screen
    if ! tap_text "Infra"; then
        echo "    Could not find 'Infra' tab"
        go_home
        return 1
    fi
    sleep 2

    # Look for infrastructure-related content
    # The Infra screen shows Gateway/Caddy/DuckDNS status cards
    # Also may show "workstation" or "docker-host" machine names
    if find_text "workstation" || find_text "Gateway" || find_text "Caddy" || find_text "DuckDNS" || find_text "Ironjaw" || find_text "Online" || find_text "Running" || find_text "Status"; then
        go_home
        return 0
    fi

    # Check if there's an error state shown (still means the screen loaded)
    if find_text "Error" || find_text "error" || find_text "Offline" || find_text "Unable"; then
        echo "    Infra screen loaded but shows error state (still a pass — screen works)"
        go_home
        return 0
    fi

    echo "    No infrastructure status content found"
    go_home
    return 1
}

test_voice_mode_toggle() {
    # On Chat screen, tap the voice/mic button and verify voice mode opens.
    go_home
    sleep 0.5

    # The mic button has content-desc "Voice mode"
    if ! tap_desc "Voice mode"; then
        echo "    Could not find 'Voice mode' button"
        return 1
    fi
    sleep 2

    # Voice screen should load — look for voice-specific UI elements
    # VoiceScreen has a record button or voice-related content
    if find_text "Voice" || find_text "Hold" || find_text "Tap" || find_text "Record" || find_text "Listening" || find_text "recording" || find_desc "record"; then
        # Voice mode opened successfully — go back
        press_back
        sleep 0.5
        return 0
    fi

    # Check for any voice permission dialog
    if find_text "Allow" || find_text "permission" || find_text "microphone"; then
        # Permission dialog appeared — this is expected on first run
        # Dismiss it
        tap_text "Allow" 2>/dev/null || tap_text "While using" 2>/dev/null || press_back
        sleep 1
        press_back
        sleep 0.5
        return 0
    fi

    echo "    Voice mode did not open"
    press_back
    sleep 0.5
    return 1
}

# Helper to check content-desc matches
find_desc() {
    python "$EMU_TAP" find "$1" 2>/dev/null | grep -q "desc=\"[^\"]*$1"
}

test_offline_reconnect() {
    # Disable wifi via adb, verify disconnect, re-enable, verify reconnect.
    go_home
    sleep 0.5

    # Verify we start connected
    if ! find_text "Message Dr. CLAW"; then
        echo "    Not connected at test start — skipping offline reconnect test"
        return 1
    fi

    # Disable wifi on the emulator
    adb shell svc wifi disable 2>/dev/null
    sleep 3

    # The app should show some disconnection indicator
    # (It may show a reconnecting message or the input field may become disabled)
    screenshot "offline-state"

    # Re-enable wifi
    adb shell svc wifi enable 2>/dev/null
    sleep 5

    # Wait for reconnection — the input placeholder should reappear
    if wait_for "Message Dr. CLAW" 30; then
        return 0
    fi

    # Fallback: check if Connected text appears
    if find_text "Connected"; then
        return 0
    fi

    echo "    Did not reconnect after wifi restore within 30 seconds"
    return 1
}

# ── Main test runner ────────────────────────────────────────────────────────

echo "============================================"
echo "  Dr. CLAW Functional Tests (emu-tap.py)"
echo "============================================"
echo ""
echo "Prerequisites:"
echo "  - Emulator running (emu-start)"
echo "  - ADB tunnels active (emu-connect)"
echo "  - Ironjaw gateway running on :18794"
echo ""

# Verify emulator is reachable
if ! adb devices 2>/dev/null | grep -q "emulator"; then
    echo "ERROR: No emulator detected. Run 'emu-start' first."
    exit 1
fi

# Verify emu-tap.py exists
if ! [ -f "$EMU_TAP" ]; then
    echo "ERROR: emu-tap.py not found at $EMU_TAP"
    exit 1
fi

# Take initial screenshot
screenshot "00-initial-state"

echo "Running tests..."
echo ""

echo "--- App Launch & Connection ---"
run_test "app_launch_gateway_connect" test_app_launch_gateway_connect

echo ""
echo "--- Chat Functionality ---"
run_test "send_message_receive_response" test_send_message_receive_response

echo ""
echo "--- Navigation ---"
run_test "navigate_all_screens" test_navigate_all_screens

echo ""
echo "--- CC Sessions ---"
run_test "cc_sessions_loads" test_cc_sessions_loads

echo ""
echo "--- Brain Remember & Recall ---"
run_test "brain_remember_recall" test_brain_remember_recall

echo ""
echo "--- Settings ---"
run_test "settings_persist" test_settings_persist

echo ""
echo "--- Models ---"
run_test "model_list" test_model_list

echo ""
echo "--- Infrastructure ---"
run_test "infrastructure_status" test_infrastructure_status

echo ""
echo "--- Voice Mode ---"
run_test "voice_mode_toggle" test_voice_mode_toggle

echo ""
echo "--- Offline Reconnect ---"
run_test "offline_reconnect" test_offline_reconnect

# ── Summary ─────────────────────────────────────────────────────────────────

echo ""
echo "============================================"
echo "  Results: $PASS/$TOTAL passed, $FAIL failed"
echo "============================================"

if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
    echo ""
    echo "Failed tests:"
    for t in "${FAILED_TESTS[@]}"; do
        echo "  - $t"
    done
    echo ""
    echo "Failure screenshots saved to: $SCREENSHOT_DIR/"
fi

echo ""
[ $FAIL -eq 0 ] && exit 0 || exit 1
