# Dr. CLAW v1.1.0 — E2E Test Plan

> Paste this entire file into a fresh Claude Code session to run automated E2E testing
> via the android-mcp MCP tools. Prerequisites: emulator running, APK installed, Ironjaw running.

## Setup

Run these first:
```bash
emu-start          # Boot emulator (skip if already running)
emu-build          # Build + install APK + setup tunnels
emu-connect        # ADB reverse tunnels + set gateway URL to ws://localhost:18794
```

Verify Ironjaw is running:
```bash
curl -s http://localhost:18794/health | head -1
```

## How to Use android-mcp

The android-mcp MCP server provides structured UI interaction. Key tools:
- `mcp__android-mcp__State-Tool` — Dump current UI hierarchy as XML (use this instead of screenshots)
- `mcp__android-mcp__Click-Tool` — Tap an element by resource-id, text, or content-desc
- `mcp__android-mcp__Type-Tool` — Type text into focused field
- `mcp__android-mcp__Press-Tool` — Press keys (BACK, HOME, ENTER)
- `mcp__android-mcp__Swipe-Tool` — Scroll gestures
- `mcp__android-mcp__Long-Click-Tool` — Long press for context menus

**Workflow**: Always dump state first, find elements by text/resource-id, then interact.

---

## Test Cases

### Test 1: App Launch + Gateway Connection
**Verify**: App connects to Ironjaw gateway

1. Dump UI state
2. Verify: green connection dot visible (look for connection indicator)
3. Verify: model chip shows (e.g., "Qwen3-30B-A3" or similar)
4. Verify: context % bar visible
5. **PASS** if all three present

### Test 2: Bottom Navigation
**Verify**: All 4 tabs work (Chat, Tools, Brain, System)

1. Click "Tools" tab text
2. Dump state — verify Tools screen content visible
3. Click "Brain" tab text
4. Dump state — verify Brain screen content visible
5. Click "System" tab text
6. Dump state — verify System screen content visible
7. Click "Chat" tab text
8. Dump state — verify chat input bar visible
9. **PASS** if all 4 screens navigated successfully

### Test 3: Settings Screen + Pair Device Button
**Verify**: Settings accessible, new "Pair Device" button present

1. From any screen, find and click the settings gear icon (content-desc "Settings")
2. Dump state — verify Settings screen
3. Scroll down to find "Pair Device" button
4. Verify: "Pair Device" button exists
5. Click "Pair Device"
6. Dump state — verify PairingScreen with 6-digit code input field
7. Press BACK to return
8. **PASS** if pairing screen renders with code input

### Test 4: CC Sessions Screen
**Verify**: CC Sessions loads, shows Ironjaw sessions

1. Navigate to System tab
2. Find and click "CC Sessions" (or equivalent navigation)
3. Dump state — verify CcSessionsScreen content
4. Verify: search bar present (SessionSearchBar)
5. Verify: FAB (create session button) present
6. Verify: session cards visible (or empty state message)
7. **PASS** if screen loads with expected elements

### Test 5: Create Session Dialog (Templates)
**Verify**: Create dialog shows template support

1. From CC Sessions screen, click the FAB (floating action button, "+" icon)
2. Dump state — verify CreateSessionDialog
3. Verify: dialog has fields for project, cwd, backend, model
4. Verify: "Save as Template" section exists (may be collapsed)
5. Press BACK or dismiss
6. **PASS** if dialog renders with template support

### Test 6: Ironjaw Create Session Dialog
**Verify**: Ironjaw-specific create dialog works

1. From CC Sessions screen, long-press the FAB or find the Ironjaw create option
2. If there's a second FAB or menu for Ironjaw sessions, tap it
3. Dump state — verify Ironjaw create dialog with role/model/project/cwd fields
4. Press BACK to dismiss
5. **PASS** if Ironjaw dialog renders

### Test 7: Chat Message Send
**Verify**: Can send a message and receive streaming response

1. Navigate to Chat tab
2. Find the text input field ("Message Dr. CLAW...")
3. Type: "what is 2+2?"
4. Click the send button (arrow icon)
5. Wait 3 seconds
6. Dump state — verify:
   - User message "what is 2+2?" appears in chat
   - Assistant response is streaming or complete
   - Response contains "4"
7. **PASS** if message sent and response received

### Test 8: Null Project Display Fix
**Verify**: No literal "null" text appears as project name

1. Navigate to CC Sessions screen
2. Dump state
3. Search the UI hierarchy for any element with text exactly "null"
4. **PASS** if no literal "null" project names found (should show "~" instead)

### Test 9: Scroll-to-Bottom FAB
**Verify**: FAB appears when scrolled up in chat

1. Navigate to Chat tab
2. If there are enough messages, swipe up to scroll away from bottom
3. Dump state — look for SmallFloatingActionButton with down arrow
4. If FAB visible, click it
5. Dump state — verify scrolled to bottom, FAB hidden
6. **PASS** if FAB appears on scroll-up and scrolls to bottom on tap
7. **SKIP** if not enough messages to trigger scroll

### Test 10: Voice Input Button
**Verify**: Mic button appears in attached session input bar

1. If an Ironjaw session is attachable, navigate to it
2. Otherwise, from main Chat screen, verify input bar
3. Dump state — look for mic/voice button (VoiceRecordButton)
4. **PASS** if mic button present when text field is empty

### Test 11: Chronicle Screen (5 tabs)
**Verify**: Chronicle screen loads with all 5 tabs

1. Navigate to System tab, find Chronicle entry
2. Click Chronicle
3. Dump state — verify ChronicleScreen
4. Verify: 5 tabs visible: Sessions, Search, Topics, Insights, Stats
5. Click "Stats" tab
6. Dump state — verify stats content (total sessions, avg length, etc.)
7. Click "Insights" tab
8. Dump state — verify insights content loads
9. **PASS** if all 5 tabs navigable

### Test 12: Model Manager
**Verify**: Model Manager screen accessible

1. Navigate to System tab, find Models entry
2. Click Models
3. Dump state — verify ModelManagerScreen
4. Verify: current model shown, model list present
5. Press BACK
6. **PASS** if model manager renders

### Test 13: Infrastructure Screen
**Verify**: Infrastructure status loads

1. Navigate to System tab, find Infrastructure entry
2. Click Infrastructure
3. Dump state — verify InfraScreen with health cards
4. **PASS** if infra screen renders with status cards

---

## Bug Report Template

If a test FAILS, document:
```
### FAIL: Test N — [Test Name]
**Expected**: [what should happen]
**Actual**: [what happened]
**UI State**: [relevant XML elements from dump]
**Fix needed in**: [file path if obvious]
```

## Summary

After running all tests, report:
- Tests passed: N/13
- Tests failed: N/13 (with bug reports)
- Tests skipped: N/13 (with reason)

If any test fails, attempt to fix the code and re-test. The key files are:
- `app/src/main/java/com/scaso/drclawapp/ui/ccbridge/` — CC Sessions, Attached Session
- `app/src/main/java/com/scaso/drclawapp/ui/navigation/NavGraph.kt` — routing
- `app/src/main/java/com/scaso/drclawapp/ui/settings/` — Settings, Pairing
- `app/src/main/java/com/scaso/drclawapp/ui/chronicle/` — Chronicle
- `app/src/main/java/com/scaso/drclawapp/data/websocket/GatewayClient.kt` — protocol
