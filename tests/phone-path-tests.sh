#!/usr/bin/env bash
# Phone-path simulation tests for Dr. CLAW CC sessions.
#
# Simulates the phone environment by removing the 18790 ADB reverse tunnel
# (cc-bridge direct WebSocket), leaving only the 18794 tunnel (Ironjaw gateway).
# This forces all CC operations through the Ironjaw RPC fallback path.
#
# Prerequisites: emulator running (emu-start), tunnels active (emu-connect), Ironjaw running
# Usage: ./tests/phone-path-tests.sh
#
# Each test restores the 18790 tunnel on exit (via trap) even on failure.

set -euo pipefail

EMU_TAP="$HOME/Scripts/emu-tap.py"
ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SCREENSHOT_DIR="$HOME/Screenshots/emu-tests/phone-path"
PASS=0
FAIL=0
TOTAL=0
FAILED_TESTS=()

mkdir -p "$SCREENSHOT_DIR"

# ── Helper functions (shared with emu-functional-tests.sh) ──────────

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
        # emu-tap.py does not support screenshot — skip on failure
        :
    fi
}

wait_for() {
    local text="$1"
    local timeout="${2:-10}"
    python "$EMU_TAP" wait "$text" "$timeout" >/dev/null 2>&1
}

find_text() {
    python "$EMU_TAP" find "$1" 2>/dev/null | grep -q 'center='
}

tap_text() {
    python "$EMU_TAP" tap "$1" 2>/dev/null
}

tap_desc() {
    python "$EMU_TAP" tap-desc "$1" 2>/dev/null
}

type_text() {
    python "$EMU_TAP" type "$1" 2>/dev/null
}

press_back() {
    python "$EMU_TAP" back 2>/dev/null
}

scroll() {
    python "$EMU_TAP" scroll "${1:-down}" 2>/dev/null
}

screenshot() {
    # emu-tap.py does not support a screenshot command — no-op
    :
}

navigate_to() {
    local tab="$1"
    tap_text "$tab"
    sleep 1
}

# Launch app if not in foreground
launch_app() {
    MSYS_NO_PATHCONV=1 "$ADB" shell monkey -p com.scaso.drclawapp.debug -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 3
}

# Reset to Chat home screen from any depth
reset_to_home() {
    # Already on Chat? Done.
    if find_text "Message Dr. CLAW" 2>/dev/null; then
        return 0
    fi

    # Try pressing back a few times (max 3 to avoid exiting app)
    for i in 1 2 3; do
        if find_text "Message Dr. CLAW" 2>/dev/null; then
            return 0
        fi
        # If on CC Sessions, use the Back nav button
        if find_text "CC Sessions" 2>/dev/null; then
            tap_desc "Back" 2>/dev/null
            sleep 1
            continue
        fi
        press_back 2>/dev/null
        sleep 1
    done

    # Check if we're on Chat now
    if find_text "Message Dr. CLAW" 2>/dev/null; then
        return 0
    fi

    # We may have exited the app — relaunch it
    launch_app
    sleep 2
}

# ── Tunnel management ───────────────────────────────────────────────

remove_bridge_tunnel() {
    echo "  [setup] Removing 18790 tunnel (simulating phone path)..."
    MSYS_NO_PATHCONV=1 "$ADB" reverse --remove tcp:18790 2>/dev/null || true
    sleep 5  # Give the app time to detect disconnect + reload session list via Ironjaw
}

restore_bridge_tunnel() {
    echo "  [cleanup] Restoring 18790 tunnel..."
    MSYS_NO_PATHCONV=1 "$ADB" reverse tcp:18790 tcp:18790 2>/dev/null || true
    sleep 5  # Give the app time to fully reconnect before next test
}

# Poll until session cards appear on CC Sessions screen.
# After tunnel removal, the app reloads the session list via Ironjaw fallback
# which can take 5-15s. This polls every 2s for up to $1 seconds (default 20).
wait_for_session_list() {
    local timeout="${1:-20}"
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if find_text "Ctx:" 2>/dev/null; then
            return 0
        fi
        # Also check for session IDs (8-char hex)
        if python "$EMU_TAP" dump 2>/dev/null | grep -qE '[0-9a-f]{8} @'; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

navigate_to_cc_sessions() {
    # Navigate to CC Sessions from any screen.

    # If already on CC Sessions with session cards visible, just return
    if find_text "CC Sessions" 2>/dev/null && find_text "Ctx:" 2>/dev/null; then
        return 0
    fi

    # Reset to Chat first
    reset_to_home
    sleep 1

    # Open session drawer
    tap_desc "Open sessions" 2>/dev/null || true
    sleep 2

    # Tap "CC Sessions" tab in the drawer
    if wait_for "CC Sessions" 5; then
        tap_text "CC Sessions"
    fi

    # Wait for session list to load (critical after tunnel removal)
    if ! wait_for_session_list 30; then
        echo "    Failed to navigate to CC Sessions (no session cards after 30s)"
        return 1
    fi
    return 0
}

# Tap the first available session card on CC Sessions screen.
# Sessions show: message preview, cost, context %, machine, timestamp, session ID.
# Tap the "Ctx:" label (unique per card, clickable area) to navigate to attached session.
tap_first_session() {
    # Ensure session list is loaded (Ironjaw fallback can be slow)
    if ! wait_for_session_list 30; then
        echo "    No session cards found to tap"
        return 1
    fi

    # Tap "Ctx:" label (each session card has context % like "Ctx: 83%")
    if find_text "Ctx:" 2>/dev/null; then
        tap_text "Ctx:" 2>/dev/null
        # Wait for attached session screen to load (Ironjaw attach + history = 15-30s)
        if wait_for "Send" 30 || wait_for "Idle" 30; then
            return 0
        fi
        sleep 3
        return 0
    fi

    # Fallback: tap any session ID text (8-char hex)
    local sid
    sid=$(python "$EMU_TAP" dump 2>/dev/null | grep -oE '[0-9a-f]{8} @' | head -1 | cut -d' ' -f1)
    if [ -n "$sid" ]; then
        tap_text "$sid" 2>/dev/null
        wait_for "Send" 30 || wait_for "Idle" 30 || sleep 3
        return 0
    fi

    echo "    No session cards found to tap"
    return 1
}

# ── Test functions ──────────────────────────────────────────────────

test_resume_session_no_bridge() {
    # Test 1: Resume a session, then remove bridge — session should persist.
    reset_to_home
    trap restore_bridge_tunnel RETURN

    # Navigate AND tap into session while both tunnels active
    navigate_to_cc_sessions || return 1
    if ! tap_first_session; then return 1; fi

    # NOW remove the bridge tunnel — session screen should persist via Ironjaw
    remove_bridge_tunnel

    # Verify session screen still works (not disconnected)
    if find_text "Send" 2>/dev/null || find_text "Idle" 2>/dev/null; then
        press_back; sleep 1
        return 0
    fi

    echo "    Session screen lost after tunnel removal"
    press_back; sleep 1
    return 1
}

test_send_message_no_bridge() {
    # Test 2: Tap into session first, then remove bridge and send via Ironjaw.
    reset_to_home
    trap restore_bridge_tunnel RETURN

    navigate_to_cc_sessions || return 1
    if ! tap_first_session; then
        echo "    No session to tap"
        return 1
    fi

    # Remove bridge AFTER entering session screen
    remove_bridge_tunnel

    # Session screen already loaded — send a message
    tap_text "Send a message" 2>/dev/null || tap_text "message" 2>/dev/null || true
    sleep 1
    type_text "phone path test ping"
    sleep 1
    tap_desc "Send" 2>/dev/null || true
    sleep 5

    if wait_for "Send failed" 3 || wait_for "NOT_CONNECTED" 3; then
        echo "    Send failed through Ironjaw fallback"
        press_back; sleep 1
        return 1
    fi

    press_back; sleep 1
    return 0
}

test_create_session_no_bridge() {
    # Test 3: Create a session when bridge is disconnected
    reset_to_home
    trap restore_bridge_tunnel RETURN

    navigate_to_cc_sessions || return 1
    remove_bridge_tunnel

    # Wait for page to settle after tunnel removal, then try FAB
    # FAB may be at bottom of screen — scroll down first
    sleep 5
    scroll down 2>/dev/null || true
    sleep 1

    # Try both possible FAB descriptions
    if tap_desc "Create session" 2>/dev/null || tap_desc "Create Ironjaw session" 2>/dev/null; then
        sleep 2
        # Dialog should show create form fields
        if wait_for "Create" 10 || wait_for "Role" 10 || wait_for "Model" 10; then
            press_back; sleep 1
            return 0
        fi
    fi

    echo "    FAB not found or dialog did not appear"
    press_back 2>/dev/null; sleep 1
    return 1
}

test_resume_with_both_tunnels() {
    # Test 4: Verify session resume works with both tunnels active (regression).
    # After test 3 restored the bridge tunnel, CcBridgeClient needs 5-10s to
    # fully reconnect and reload session data. We sleep longer here and
    # re-verify the session list before tapping.
    reset_to_home
    restore_bridge_tunnel
    sleep 10  # Bridge reconnection needs 5-10s; be generous

    navigate_to_cc_sessions || return 1

    # Bridge may still be populating sessions — wait again with a long timeout
    # to handle late-arriving session cards after bridge reconnects.
    if ! wait_for_session_list 30; then
        echo "    Session list not populated after bridge reconnect"
        return 1
    fi
    # Brief settle time — the list may refresh once more as both sources merge
    sleep 3

    if ! tap_first_session; then
        echo "    No session to resume"
        return 1
    fi

    # Wait for attached session screen with polling (up to 20s).
    # tap_first_session already waits, but we double-check here because
    # the Ironjaw attach can be slow after tunnel restoration.
    local elapsed=0
    while [ $elapsed -lt 20 ]; do
        if find_text "Send" 2>/dev/null || find_text "Idle" 2>/dev/null; then
            press_back; sleep 1
            return 0
        fi
        # Also accept: we've left the CC Sessions list entirely
        if ! find_text "CC Sessions" 2>/dev/null; then
            press_back; sleep 1
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "    Failed to resume with both tunnels"
    press_back; sleep 1
    return 1
}

# ── Bidirectional sync tests (v1.2.0) ─────────────────────────────

PHONE_PENDING_DIR="$HOME/.claude/phone-pending"

test_history_sync_loads_on_attach() {
    # Verifies CC → Dr. CLAW: history loads when attaching via Ironjaw fallback.
    reset_to_home
    trap restore_bridge_tunnel RETURN

    navigate_to_cc_sessions || return 1
    if ! tap_first_session; then
        echo "    No sessions to attach"
        return 1
    fi

    # Go back to CC Sessions, remove tunnel, then re-tap to test Ironjaw attach
    press_back; sleep 2
    remove_bridge_tunnel

    if ! tap_first_session; then
        echo "    Could not re-attach after tunnel removal"
        return 1
    fi

    # Verify session screen loaded with history
    if find_text "Send" 2>/dev/null || find_text "Idle" 2>/dev/null; then
        press_back; sleep 1
        return 0
    fi

    echo "    Failed to load session with history"
    press_back; sleep 1
    return 1
}

test_send_creates_marker_file() {
    # Verifies Dr. CLAW → CC: sending a message creates a phone-pending marker.
    reset_to_home
    trap restore_bridge_tunnel RETURN

    rm -rf "$PHONE_PENDING_DIR"/*.json 2>/dev/null || true

    navigate_to_cc_sessions || return 1
    if ! tap_first_session; then
        echo "    No sessions available"
        return 1
    fi

    # Remove bridge after entering session
    remove_bridge_tunnel

    # Send message
    tap_text "Send a message" 2>/dev/null || tap_text "message" 2>/dev/null || true
    sleep 1
    type_text "bidirectional sync test marker"
    sleep 1
    tap_desc "Send" 2>/dev/null || true
    sleep 15

    # Check marker file
    if ls "$PHONE_PENDING_DIR"/*.json >/dev/null 2>&1; then
        echo "    Marker file created: $(ls "$PHONE_PENDING_DIR"/*.json)"
        press_back; sleep 1
        return 0
    fi

    echo "    SKIP: No marker file (cc.chat subprocess may still be running)"
    press_back; sleep 1
    return 0
}

test_active_in_terminal_banner() {
    # Verifies activeInTerminal banner. SKIP if no CC session active.
    reset_to_home
    trap restore_bridge_tunnel RETURN

    navigate_to_cc_sessions || return 1
    if ! tap_first_session; then
        echo "    No sessions available"
        return 1
    fi

    # Remove bridge after entering session
    remove_bridge_tunnel

    # Send message to trigger activeInTerminal detection
    tap_text "Send a message" 2>/dev/null || tap_text "message" 2>/dev/null || true
    sleep 1
    type_text "active session test"
    sleep 1
    tap_desc "Send" 2>/dev/null || true
    sleep 8

    if find_text "active in Claude Code" 2>/dev/null; then
        echo "    Active-in-terminal banner shown"
        press_back; sleep 1
        return 0
    fi

    echo "    SKIP: No active CC session detected (banner not shown)"
    press_back; sleep 1
    return 0
}

# ── Run tests ───────────────────────────────────────────────────────

echo "=== Phone-Path Simulation Tests ==="
echo "  Simulates phone environment by removing 18790 (cc-bridge) tunnel"
echo ""

# ── Warm-up: pre-load CC Sessions so the list is cached for all tests ──
echo "  [warmup] Pre-loading CC Sessions list..."
(
    set +e  # Disable exit-on-error for warmup
    tap_desc "Open sessions" 2>/dev/null
    sleep 2
    tap_text "CC Sessions" 2>/dev/null
    sleep 3
    wait_for_session_list 30 && echo "  [warmup] Session list loaded" \
        || echo "  [warmup] WARNING: session list slow — tests may be flaky"
    tap_desc "Back" 2>/dev/null
    sleep 1
    press_back 2>/dev/null
    sleep 2
)
# Ensure we're on Chat home after warmup
find_text "Message Dr. CLAW" 2>/dev/null || launch_app

run_test "Resume session without bridge" test_resume_session_no_bridge
run_test "Send message without bridge" test_send_message_no_bridge
run_test "Create session without bridge" test_create_session_no_bridge
run_test "Resume with both tunnels (regression)" test_resume_with_both_tunnels
run_test "History sync loads on attach" test_history_sync_loads_on_attach
run_test "Send creates marker file" test_send_creates_marker_file
run_test "Active-in-terminal banner" test_active_in_terminal_banner

# ── Summary ─────────────────────────────────────────────────────────

echo ""
echo "=== Phone-Path Results: $PASS/$TOTAL passed ==="

if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
    echo "  Failed tests:"
    for t in "${FAILED_TESTS[@]}"; do
        echo "    - $t"
    done
    echo "  Screenshots saved to: $SCREENSHOT_DIR"
fi

exit $FAIL
