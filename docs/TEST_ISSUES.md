# Dr. CLAW Instrumented Test Issues

## Current state — 2026-07-07 (RESOLVED, verified full run on emulator-5554)

**320/320 passing, 0 failures.** Full `connectedDebugAndroidTest` run to completion,
animations disabled, single gradle daemon (`tests=320 failures=0 errors=0`, BUILD SUCCESSFUL).
This is the first time green has been *verified* — the earlier "320/320 passing" note was
never actually run.

### The bug that was fixed (test-side, NOT an app defect)

Earlier the suite was 292/320 with 28 deterministic failures, all routing through the Brain
tab. Investigation (manual emulator inspection + semantics-tree dump) proved the Brain screen
renders correctly at runtime — the failures were a **test navigation-selector bug**.

Every Brain test navigated with `onAllNodes(hasText("Brain")).onFirst().performClick()`.
The semantics tree contains **two** nodes with text "Brain":

| Node | What it is | Position |
|------|-----------|----------|
| ghost | a stray off-screen `Role=Tab` "Brain" | `x = -776` (off-screen), `y = 1308` |
| real | the bottom-nav "Brain" item | on-screen, `y = 2253` |

`onFirst()` sorts top-to-bottom → it selected the **off-screen ghost** (y=1308 < 2253) and
clicked at negative coordinates → a no-op. Navigation never fired, so every test that needed
the Brain screen timed out on Chat. The lone "passing" test only checked that a "Brain" label
existed (true even on Chat).

**Fix:** navigate via `onNodeWithContentDescription("Brain", useUnmergedTree = true)` instead.
- The bottom-nav item's icon carries `contentDescription = "Brain"`; the ghost has none, so
  content-description is unambiguous.
- `useUnmergedTree = true` is required because Material3 `NavigationBarItem` drops the icon's
  content-description in the *merged* tree once a text label is present (merged tree has 0
  cd="Brain" nodes; unmerged has exactly 1).

Applied across 8 androidTest files (BrainScreenExhaustiveTest, NavigationTest,
NavigationExhaustiveTest, EdgeCaseStressTest, ChatExhaustiveTest, ConnectionExhaustiveTest,
ConnectionStateTest, SystemScreenExhaustiveTest). **No production code changed.**

### Known-but-benign (not fixed — zero user impact)

- The off-screen ghost "[Brain]" `Role=Tab` node sitting in the Chat message-list region
  (x=-776, y=1308) is invisible to users and harmless. Not chased. If it ever needs killing,
  start from what renders a `Role=Tab` "Brain" inside the Chat content tree.

### Environment gotchas (still relevant for future runs)

- Restrict to the emulator: `ANDROID_SERIAL=emulator-5554` before the run (never install the
  test APK on real devices over the LAN — the Fire Stick shows up in `adb devices`).
- Disable animations first: `adb shell settings put global {window,transition,animator}_*_scale 0`.
- Single gradle daemon only — concurrent gradle invocations send "stop command received" and
  leave orphaned worker JVMs (looks like OOM but isn't; workstation has 64GB). `./gradlew --stop`
  and confirm one daemon before a run.
- Debug applicationId is `com.scaso.drclawapp.debug`; launcher activity
  `com.scaso.drclawapp.debug/com.scaso.drclawapp.MainActivity`.

---

## Historical (2026-03-24) — SUPERSEDED
27 failures were catalogued (chat-input-disabled ×11, CC-sessions-search ×6, settings ×4,
etc.), all diagnosed as test-side. That category set does not match the 2026-07-07 failures
and the suite has since evolved (screens added, package rename). Do not work from the old table.
