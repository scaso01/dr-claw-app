# Dr. CLAW v1.1.0 — E2E Test Plan v2

> Paste into a fresh Claude Code session:
> Use the `/android-testing` skill and the emu-mcp MCP tools (emu_dump, emu_tap, emu_type, emu_press, emu_scroll, emu_wait, emu_screenshot) to run the E2E test plan at `C:\Users\deploy\Projects\re-lab\android\dr-claw-app\tests\e2e-test-plan-v2.md`. Execute all tests. Use emu_dump before every interaction — never blind-tap coordinates. Fix any failures in the code, rebuild with `./gradlew assembleDebug`, reinstall with `emu-build --skip-build`, and re-test.

## Setup

```bash
emu-start          # Boot emulator (skip if already running)
emu-build          # Build + install APK + setup tunnels
emu-connect        # ADB reverse tunnels + set gateway URL
```

Verify services:
```bash
curl -s http://localhost:18794/health   # Ironjaw
curl -s http://localhost:8090/health    # Chatterbox TTS
curl -s http://localhost:18793/api/health  # Chronicle
```

---

## PART 1: Core Navigation (from v1 — re-verify)

### Test 1: App Launch + Gateway Connection
1. emu_dump → verify green connection dot, model chip, context % bar
2. **PASS** if all three present

### Test 2: Bottom Navigation (4 tabs)
1. emu_tap "Tools" → emu_dump → verify Tools screen
2. emu_tap "Brain" → emu_dump → verify Brain screen
3. emu_tap "System" → emu_dump → verify System screen
4. emu_tap "Chat" → emu_dump → verify chat input bar
5. **PASS** if all 4 navigate

### Test 3: Chat Message Send
1. emu_tap the text input → emu_type "what is 2+2?" → emu_tap send button
2. emu_wait for response (3-5s)
3. emu_dump → verify user message + assistant response with "4"
4. **PASS** if response received

---

## PART 2: New Features (Wave 1-5)

### Test 4: Settings Screen + Voice Picker
1. Navigate to Settings (gear icon or via System tab)
2. emu_dump → verify Settings screen
3. emu_scroll down to find "Voice" section
4. Verify: Voice dropdown present with current selection (default: "KITT")
5. Tap the voice dropdown
6. emu_dump → verify 21 voice options visible (scroll if needed)
7. Select "Dr. Claw"
8. emu_dump → verify selection changed to "Dr. Claw"
9. **PASS** if voice picker works with 21 options

### Test 5: Settings — Pair Device Button
1. From Settings, scroll to find "Pair Device" button
2. Tap "Pair Device"
3. emu_dump → verify PairingScreen with 6-digit code input field
4. emu_press back
5. **PASS** if pairing screen renders

### Test 6: CC Sessions Screen
1. Navigate: System tab → find "CC Sessions" → tap
2. emu_dump → verify CcSessionsScreen
3. Verify: search bar + FAB present
4. **PASS** if screen loads

### Test 7: Create Session Dialog + Templates
1. From CC Sessions, tap the FAB (+)
2. emu_dump → verify CreateSessionDialog
3. Verify: project/cwd/backend/model fields present
4. Verify: "Save as Template" section exists
5. emu_press back to dismiss
6. **PASS** if template support visible

### Test 8: Null Project Display Fix
1. From CC Sessions screen
2. emu_dump → search for any element with text exactly "null"
3. **PASS** if no literal "null" project names (should show "~" or actual project name)

### Test 9: Scroll-to-Bottom FAB
1. Navigate to Chat tab
2. If enough messages exist, emu_scroll up several times
3. emu_dump → look for down-arrow FAB (SmallFloatingActionButton)
4. If visible, tap it → emu_dump → verify scrolled to bottom
5. **PASS** if FAB appears on scroll-up
6. **SKIP** if not enough messages

### Test 10: Voice Input Button (Attached Session)
1. Navigate to CC Sessions → find an active Ironjaw session → tap to attach
2. emu_dump → look for mic button (VoiceRecordButton) in the input bar
3. Verify: mic icon visible when text field is empty
4. **PASS** if mic button present
5. **SKIP** if no Ironjaw sessions available to attach

### Test 11: Chronicle Screen (5 tabs + Stats)
1. Navigate to System → Chronicle
2. emu_dump → verify 5 tabs: Sessions, Search, Topics, Insights, Stats
3. emu_tap "Stats" tab
4. emu_dump → verify stats data (total sessions, avg length should show ~59 min)
5. emu_tap "Insights" tab
6. emu_dump → verify insights content loads (activeProjects, peakHours, etc.)
7. **PASS** if all tabs work and data is present

### Test 12: Model Manager
1. Navigate to System → Models
2. emu_dump → verify ModelManagerScreen with model list
3. **PASS** if renders

### Test 13: Infrastructure Screen
1. Navigate to System → Infrastructure
2. emu_dump → verify health cards
3. **PASS** if renders

---

## PART 3: Advanced Features

### Test 14: Split View Selection (Long-Press)
1. Navigate to CC Sessions
2. If Ironjaw session cards are visible, emu_long_click on one
3. emu_dump → look for "split selection" banner or highlight
4. **PASS** if split selection activates
5. **SKIP** if no Ironjaw sessions

### Test 15: Reconnect Resilience
1. From Chat screen, note the connection state (green dot)
2. Kill Ironjaw: run `taskkill /F /IM ironjaw.exe` via emu_shell or Bash
3. emu_wait 3s → emu_dump → verify disconnect indicator (red/yellow)
4. Restart Ironjaw: `schtasks /Run /TN "Ironjaw Gateway"`
5. emu_wait 10s → emu_dump → verify reconnected (green dot returns)
6. **PASS** if auto-reconnect works
7. **CAUTION**: This disrupts the gateway connection temporarily

### Test 16: Canvas/WebView Card
1. Navigate to Chat tab
2. Send: "Generate a simple HTML page with the text 'Hello World'"
3. Wait for response
4. emu_dump → look for rendered HTML content or code block
5. **PASS** if response contains formatted content

### Test 17: Session Templates Save/Load
1. Navigate to CC Sessions → tap FAB → Create dialog
2. Fill in: project="test", cwd=".", backend="ironjaw"
3. Expand "Save as Template" → enter name "test-template" → tap Save
4. emu_press back to dismiss
5. Tap FAB again → look for "Load Template" dropdown
6. emu_dump → verify "test-template" appears in dropdown
7. **PASS** if template saved and loads

### Test 18: Completion Push Notification
1. Verify ntfy is enabled in Settings
2. emu_shell "dumpsys notification" → check Dr. CLAW notification channels exist
3. Verify ChatService notification channels (service, chat, ntfy) are registered
4. **PASS** if all 3 notification channels present

---

## Bug Report Template

```
### FAIL: Test N — [Test Name]
**Expected**: [what should happen]
**Actual**: [what happened]
**UI State**: [relevant elements from emu_dump]
**Fix needed in**: [file path]
```

## Summary

After all tests, report:
```
Tests passed:  N/18
Tests failed:  N/18
Tests skipped: N/18
```

Fix any failures, rebuild, reinstall, re-test until green.

Key source files:
- `ui/ccbridge/` — CC Sessions, Attached Session, Split View
- `ui/settings/` — Settings, Pairing, Voice Picker
- `ui/chronicle/` — Chronicle tabs
- `ui/navigation/NavGraph.kt` — routing
- `data/websocket/GatewayClient.kt` — protocol
- `data/tts/` — Piper + TtsManager
- `data/preferences/AppPreferences.kt` — voice preference
