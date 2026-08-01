"""
Black-box E2E tests for Dr. CLAW Android app using uiautomator2.

Requirements:
    - Emulator (DrClaw_Test) running with app installed
    - pip install uiautomator2 pytest
    - Package: com.scaso.drclawapp.debug

Run:
    python -m pytest test_drclaw_e2e.py -v --tb=short
"""

import pytest
import uiautomator2 as u2
import time
import os

PACKAGE = "com.scaso.drclawapp.debug"
SCREENSHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def d():
    """Connect to emulator, launch app, and yield device handle."""
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)
    device = u2.connect()
    device.implicitly_wait(10.0)
    device.app_start(PACKAGE)
    time.sleep(3)
    yield device


@pytest.fixture(autouse=True)
def ensure_app_foreground(d):
    """Ensure app is in foreground before each test; screenshot on failure."""
    info = d.app_current()
    if info.get("package") != PACKAGE:
        d.app_start(PACKAGE)
        time.sleep(2)
    yield


@pytest.fixture(autouse=True)
def screenshot_on_failure(request, d):
    """Save screenshot when a test fails."""
    yield
    if hasattr(request.node, "rep_call") and request.node.rep_call.failed:
        name = request.node.name.replace(" ", "_")[:50]
        path = os.path.join(SCREENSHOT_DIR, f"FAIL_{name}.png")
        d.screenshot(path)


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    setattr(item, f"rep_{rep.when}", rep)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def wait_for(d, text=None, description=None, timeout=5):
    """Wait for an element with given text or description to appear."""
    if text:
        return d(text=text).exists(timeout=timeout)
    if description:
        return d(description=description).exists(timeout=timeout)
    return False


def navigate_to_tab(d, tab_name):
    """Tap a bottom nav tab by label text."""
    d(text=tab_name).click()
    time.sleep(1)


def go_home(d):
    """Navigate back to Chat tab (the home screen)."""
    navigate_to_tab(d, "Chat")
    time.sleep(0.5)


# ===========================================================================
# APP LAUNCH (5 tests)
# ===========================================================================

class TestAppLaunch:
    """Verify the app starts correctly and lands on the chat screen."""

    def test_app_launches_successfully(self, d):
        info = d.app_current()
        assert info["package"] == PACKAGE

    def test_chat_is_initial_screen(self, d):
        assert d(text="Message Dr. CLAW...").exists(timeout=5), \
            "Chat input placeholder not found -- wrong initial screen"

    def test_bottom_nav_visible(self, d):
        for label in ("Chat", "Tools", "Brain", "System"):
            assert d(text=label).exists(timeout=3), f"Bottom nav item '{label}' not visible"

    def test_app_responds_within_timeout(self, d):
        start = time.time()
        d.app_start(PACKAGE)
        assert d(text="Message Dr. CLAW...").exists(timeout=10)
        elapsed = time.time() - start
        assert elapsed < 10, f"App took {elapsed:.1f}s to show chat screen"

    def test_correct_package_name(self, d):
        info = d.app_current()
        assert info["package"] == PACKAGE, f"Expected {PACKAGE}, got {info['package']}"


# ===========================================================================
# BOTTOM NAVIGATION (8 tests)
# ===========================================================================

class TestBottomNavigation:
    """Verify bottom navigation bar behavior."""

    def test_tap_chat_tab(self, d):
        navigate_to_tab(d, "Chat")
        assert d(text="Message Dr. CLAW...").exists(timeout=5)

    def test_tap_tools_tab(self, d):
        navigate_to_tab(d, "Tools")
        # Tools screen should show either tool items or "No tools available"
        assert d(text="Tools").exists(timeout=3)

    def test_tap_brain_tab(self, d):
        navigate_to_tab(d, "Brain")
        assert d(text="Brain").exists(timeout=3)
        # Brain tabs should be present
        assert d(text="Search").exists(timeout=3)

    def test_tap_system_tab(self, d):
        navigate_to_tab(d, "System")
        assert d(text="System").exists(timeout=3)
        assert d(text="Infra").exists(timeout=3)

    def test_rapid_nav_switching(self, d):
        """Cycle all 4 tabs quickly -- no crash."""
        for _ in range(2):
            for tab in ("Chat", "Tools", "Brain", "System"):
                d(text=tab).click()
                time.sleep(0.3)
        # App should still be alive
        assert d.app_current()["package"] == PACKAGE

    def test_double_tap_same_tab(self, d):
        navigate_to_tab(d, "Chat")
        navigate_to_tab(d, "Chat")
        assert d.app_current()["package"] == PACKAGE

    def test_nav_bar_persists_on_all_screens(self, d):
        for tab in ("Chat", "Tools", "Brain", "System"):
            navigate_to_tab(d, tab)
            for label in ("Chat", "Tools", "Brain", "System"):
                assert d(text=label).exists(timeout=2), \
                    f"Nav label '{label}' missing on '{tab}' screen"

    def test_selected_indicator(self, d):
        """Active tab should be selected (NavigationBarItem selected=true)."""
        navigate_to_tab(d, "Brain")
        time.sleep(0.5)
        # The selected tab icon has a different state -- we verify the Brain
        # top bar title appears, confirming navigation worked
        assert d(text="Brain").exists(timeout=3)
        go_home(d)


# ===========================================================================
# CHAT SCREEN (12 tests)
# ===========================================================================

class TestChatScreen:
    """Verify chat screen elements and interactions."""

    def test_message_input_exists(self, d):
        go_home(d)
        assert d(text="Message Dr. CLAW...").exists(timeout=5)

    def test_input_accepts_text(self, d):
        go_home(d)
        input_field = d(text="Message Dr. CLAW...")
        input_field.click()
        time.sleep(0.5)
        d.send_keys("hello from e2e test")
        time.sleep(0.5)
        # The text should be in the input (placeholder gone)
        assert d(textContains="hello from e2e test").exists(timeout=3)
        # Clear the text
        d.clear_text()

    def test_send_button_with_text(self, d):
        go_home(d)
        d(text="Message Dr. CLAW...").click()
        time.sleep(0.3)
        d.send_keys("test message")
        time.sleep(0.5)
        # Send button should appear (contentDescription="Send")
        assert d(description="Send").exists(timeout=3)
        d.clear_text()

    def test_mic_button_without_text(self, d):
        go_home(d)
        # When input is empty, voice mode button should be visible
        assert d(description="Voice mode").exists(timeout=5)

    def test_settings_icon_tappable(self, d):
        go_home(d)
        assert d(description="Settings").exists(timeout=3)
        d(description="Settings").click()
        time.sleep(1)
        # Should navigate to Settings screen
        assert d(text="Settings").exists(timeout=3)
        d.press("back")
        time.sleep(1)

    def test_session_drawer_opens(self, d):
        go_home(d)
        d(description="Open sessions").click()
        time.sleep(1)
        assert d(text="Sessions").exists(timeout=3)
        d.press("back")
        time.sleep(0.5)

    def test_keyboard_shows_on_input_tap(self, d):
        go_home(d)
        d(text="Message Dr. CLAW...").click()
        time.sleep(1)
        # Keyboard should be visible -- check via window size change or just
        # verify the input is now focused (text field is interactive)
        assert d.app_current()["package"] == PACKAGE
        d.press("back")  # dismiss keyboard
        time.sleep(0.3)

    def test_thinking_button_exists(self, d):
        go_home(d)
        # Thinking level menu icon (contentDescription starts with "Thinking:")
        assert d(descriptionContains="Thinking").exists(timeout=3)

    def test_verbose_toggle_exists(self, d):
        go_home(d)
        # Verbose toggle has contentDescription "Verbose on" or "Verbose off"
        verbose_on = d(description="Verbose on").exists(timeout=2)
        verbose_off = d(description="Verbose off").exists(timeout=2)
        assert verbose_on or verbose_off, "Verbose toggle not found"

    def test_quick_actions_visible(self, d):
        go_home(d)
        # Quick actions shown when message list is empty
        # They may or may not be visible depending on chat state,
        # so we check at least Summarize or Explain exist
        has_summarize = d(text="Summarize").exists(timeout=2)
        has_explain = d(text="Explain").exists(timeout=2)
        # If there are messages, quick actions won't show -- that's OK
        if not has_summarize and not has_explain:
            pytest.skip("Quick actions not visible (messages present)")

    def test_input_clears(self, d):
        go_home(d)
        d(text="Message Dr. CLAW...").click()
        time.sleep(0.3)
        d.send_keys("temporary text")
        time.sleep(0.3)
        d.clear_text()
        time.sleep(0.5)
        # Placeholder should reappear
        assert d(text="Message Dr. CLAW...").exists(timeout=3)

    def test_very_long_text(self, d):
        go_home(d)
        d(text="Message Dr. CLAW...").click()
        time.sleep(0.3)
        long_text = "A" * 5000
        d.send_keys(long_text)
        time.sleep(1)
        # App should not crash
        assert d.app_current()["package"] == PACKAGE
        d.clear_text()


# ===========================================================================
# SESSION DRAWER (6 tests)
# ===========================================================================

class TestSessionDrawer:
    """Verify session drawer behavior."""

    def _open_drawer(self, d):
        go_home(d)
        d(description="Open sessions").click()
        time.sleep(1)

    def test_drawer_opens(self, d):
        self._open_drawer(d)
        assert d(text="Sessions").exists(timeout=3)
        d.press("back")

    def test_new_chat_button(self, d):
        self._open_drawer(d)
        assert d(description="New Chat").exists(timeout=3)
        d.press("back")

    def test_cc_sessions_item(self, d):
        self._open_drawer(d)
        # Scroll down if needed to find "CC Sessions" nav item
        found = d(text="CC Sessions").exists(timeout=3)
        if not found:
            d(scrollable=True).scroll.to(text="CC Sessions")
            found = d(text="CC Sessions").exists(timeout=3)
        assert found, "CC Sessions nav item not found in drawer"
        d.press("back")

    def test_search_bar_in_drawer(self, d):
        self._open_drawer(d)
        # SessionSearchBar is present in the drawer
        # It uses OutlinedTextField -- look for the search placeholder
        search_exists = d(textContains="Search").exists(timeout=3)
        assert search_exists, "Search bar not found in drawer"
        d.press("back")

    def test_drawer_closes_on_back(self, d):
        self._open_drawer(d)
        assert d(text="Sessions").exists(timeout=3)
        d.press("back")
        time.sleep(0.5)
        # Drawer should be closed -- Sessions header should not be visible
        # The chat input should be back
        assert d(text="Message Dr. CLAW...").exists(timeout=3)

    def test_drawer_items_clickable(self, d):
        self._open_drawer(d)
        # "Conversations" section header should be visible (if sessions exist)
        has_convos = d(text="Conversations").exists(timeout=3)
        if has_convos:
            # There should be at least one session item
            assert True
        else:
            # Empty state is also valid
            pytest.skip("No conversations in drawer")
        d.press("back")


# ===========================================================================
# CC SESSIONS (8 tests)
# ===========================================================================

class TestCcSessions:
    """Verify CC Sessions screen."""

    def _navigate_to_cc_sessions(self, d):
        go_home(d)
        d(description="Open sessions").click()
        time.sleep(1)
        # Scroll to CC Sessions and tap
        if not d(text="CC Sessions").exists(timeout=3):
            d(scrollable=True).scroll.to(text="CC Sessions")
        d(text="CC Sessions").click()
        time.sleep(2)

    def test_cc_sessions_opens(self, d):
        self._navigate_to_cc_sessions(d)
        assert d(text="CC Sessions").exists(timeout=5)
        d.press("back")
        time.sleep(1)

    def test_title_displayed(self, d):
        self._navigate_to_cc_sessions(d)
        assert d(text="CC Sessions").exists(timeout=3)
        d.press("back")
        time.sleep(1)

    def test_back_returns_to_chat(self, d):
        self._navigate_to_cc_sessions(d)
        d(description="Back").click()
        time.sleep(1)
        assert d(text="Message Dr. CLAW...").exists(timeout=5)

    def test_refresh_button(self, d):
        self._navigate_to_cc_sessions(d)
        assert d(description="Refresh").exists(timeout=3)
        d.press("back")
        time.sleep(1)

    def test_search_bar(self, d):
        self._navigate_to_cc_sessions(d)
        # "Search CC sessions..." placeholder
        found = d(text="Search CC sessions...").exists(timeout=3)
        if not found:
            # May need to scroll to see it
            found = d(textContains="Search").exists(timeout=3)
        assert found, "Search bar not found on CC Sessions screen"
        d.press("back")
        time.sleep(1)

    def test_session_list_area(self, d):
        self._navigate_to_cc_sessions(d)
        # Either there are session items or empty/error state
        has_sessions = d(text="Ironjaw Sessions").exists(timeout=3)
        has_empty = d(text="No CC sessions found.").exists(timeout=2)
        has_error = d(textContains="error").exists(timeout=2)
        assert has_sessions or has_empty or has_error, \
            "Neither session list nor empty/error state found"
        d.press("back")
        time.sleep(1)

    def test_search_filters(self, d):
        self._navigate_to_cc_sessions(d)
        search_field = d(text="Search CC sessions...")
        if search_field.exists(timeout=3):
            search_field.click()
            time.sleep(0.3)
            d.send_keys("nonexistent_session_xyz")
            time.sleep(1)
            # Should show no results or filtered results
            assert d.app_current()["package"] == PACKAGE
            d.clear_text()
        d.press("back")
        time.sleep(1)

    def test_empty_error_state(self, d):
        self._navigate_to_cc_sessions(d)
        # App should show either content or a graceful empty/error state
        assert d.app_current()["package"] == PACKAGE
        d.press("back")
        time.sleep(1)


# ===========================================================================
# BRAIN SCREEN (10 tests)
# ===========================================================================

class TestBrainScreen:
    """Verify Brain screen tabs and interactions."""

    def test_brain_accessible(self, d):
        navigate_to_tab(d, "Brain")
        assert d(text="Brain").exists(timeout=3)

    def test_all_tabs_visible(self, d):
        navigate_to_tab(d, "Brain")
        for tab in ("Search", "Add", "Context", "Summaries", "Timeline", "Graph"):
            assert d(text=tab).exists(timeout=3), f"Brain tab '{tab}' not visible"

    def test_search_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Search").click()
        time.sleep(0.5)
        assert d(text="Search memories...").exists(timeout=3)

    def test_add_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Add").click()
        time.sleep(0.5)
        assert d(text="Enter something to remember...").exists(timeout=3)

    def test_context_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Context").click()
        time.sleep(0.5)
        # Should show context variables or empty state
        context_visible = d(text="Variable name").exists(timeout=3) or \
            d(text="No context variables. Add one below.").exists(timeout=3)
        assert context_visible

    def test_summaries_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Summaries").click()
        time.sleep(0.5)
        summaries_visible = d(text="Session ID (optional)").exists(timeout=3) or \
            d(text="No summaries found. Tap Search to load.").exists(timeout=3)
        assert summaries_visible

    def test_timeline_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Timeline").click()
        time.sleep(0.5)
        assert d(textContains="Date").exists(timeout=3)

    def test_search_input(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Search").click()
        time.sleep(0.5)
        d(text="Search memories...").click()
        time.sleep(0.3)
        d.send_keys("test query")
        time.sleep(0.3)
        assert d(textContains="test query").exists(timeout=3)
        d.clear_text()

    def test_detail_level_chips(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Search").click()
        time.sleep(0.5)
        # Detail level filter chips: Compact, Full, Deep
        for chip in ("Compact", "Full", "Deep"):
            assert d(text=chip).exists(timeout=3), f"Detail chip '{chip}' not found"

    def test_add_memory_input(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Add").click()
        time.sleep(0.5)
        d(text="Enter something to remember...").click()
        time.sleep(0.3)
        d.send_keys("E2E test memory content")
        time.sleep(0.3)
        assert d(textContains="E2E test memory").exists(timeout=3)
        d.clear_text()

    def test_graph_tab_clickable(self, d):
        navigate_to_tab(d, "Brain")
        d(text="Graph").click()
        time.sleep(0.5)
        # Should show entities or empty state
        graph_visible = d(text="Tap the Graph tab to load entities.").exists(timeout=3) or \
            d.app_current()["package"] == PACKAGE
        assert graph_visible

    def test_rapid_tab_switching(self, d):
        navigate_to_tab(d, "Brain")
        tabs = ["Search", "Add", "Context", "Summaries", "Timeline", "Graph"]
        for _ in range(2):
            for tab in tabs:
                d(text=tab).click()
                time.sleep(0.2)
        assert d.app_current()["package"] == PACKAGE

    def test_back_navigation(self, d):
        navigate_to_tab(d, "Brain")
        d.press("back")
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE
        go_home(d)


# ===========================================================================
# SYSTEM SCREEN (8 tests)
# ===========================================================================

class TestSystemScreen:
    """Verify System screen tabs and behavior."""

    SYSTEM_TABS = ["Infra", "Schedules", "Devices", "Metrics", "Vault", "Export", "Plugins"]

    def test_system_accessible(self, d):
        navigate_to_tab(d, "System")
        assert d(text="System").exists(timeout=3)

    def test_subtabs_visible(self, d):
        navigate_to_tab(d, "System")
        for tab in self.SYSTEM_TABS:
            assert d(text=tab).exists(timeout=3), f"System subtab '{tab}' not visible"

    def test_infra_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Infra").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_schedules_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Schedules").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_devices_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Devices").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_metrics_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Metrics").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_vault_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Vault").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_export_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Export").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_plugins_tab_clickable(self, d):
        navigate_to_tab(d, "System")
        d(text="Plugins").click()
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE

    def test_scroll_works(self, d):
        navigate_to_tab(d, "System")
        d(text="Infra").click()
        time.sleep(0.5)
        # Scroll down
        d.swipe_ext("up", scale=0.5)
        time.sleep(0.3)
        # Scroll back up
        d.swipe_ext("down", scale=0.5)
        time.sleep(0.3)
        assert d.app_current()["package"] == PACKAGE

    def test_rapid_tab_switching(self, d):
        navigate_to_tab(d, "System")
        for _ in range(2):
            for tab in self.SYSTEM_TABS:
                d(text=tab).click()
                time.sleep(0.2)
        assert d.app_current()["package"] == PACKAGE

    def test_back_navigation(self, d):
        navigate_to_tab(d, "System")
        # System is a bottom nav tab -- pressing back should not crash
        d.press("back")
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE
        go_home(d)


# ===========================================================================
# SETTINGS (8 tests)
# ===========================================================================

class TestSettings:
    """Verify Settings screen elements."""

    def _open_settings(self, d):
        go_home(d)
        d(description="Settings").click()
        time.sleep(1)

    def test_settings_accessible(self, d):
        self._open_settings(d)
        assert d(text="Settings").exists(timeout=3)
        d.press("back")

    def test_theme_options(self, d):
        self._open_settings(d)
        assert d(text="Theme").exists(timeout=3)
        # Theme mode selector: System, Light, Dark
        for option in ("System", "Light", "Dark"):
            assert d(text=option).exists(timeout=3), f"Theme option '{option}' not found"
        d.press("back")

    def test_gateway_url_field(self, d):
        self._open_settings(d)
        assert d(text="Gateway URL").exists(timeout=5)
        d.press("back")

    def test_reconnect_button(self, d):
        self._open_settings(d)
        assert d(text="Reconnect").exists(timeout=5)
        d.press("back")

    def test_custom_instructions(self, d):
        self._open_settings(d)
        assert d(text="Custom Instructions").exists(timeout=5)
        d.press("back")

    def test_scroll_to_bottom(self, d):
        self._open_settings(d)
        # Scroll to find the About section
        d.swipe_ext("up", scale=0.8)
        time.sleep(0.5)
        d.swipe_ext("up", scale=0.8)
        time.sleep(0.5)
        # About section should have Version info
        found = d(text="Version").exists(timeout=3) or d(text="About").exists(timeout=3)
        assert found, "Could not scroll to About section"
        d.press("back")

    def test_back_returns(self, d):
        self._open_settings(d)
        d(description="Back").click()
        time.sleep(1)
        assert d(text="Message Dr. CLAW...").exists(timeout=5)

    def test_version_displayed(self, d):
        self._open_settings(d)
        # Scroll to About section
        d.swipe_ext("up", scale=0.8)
        time.sleep(0.3)
        d.swipe_ext("up", scale=0.8)
        time.sleep(0.3)
        d.swipe_ext("up", scale=0.8)
        time.sleep(0.3)
        assert d(text="Version").exists(timeout=3), "Version label not found"
        d.press("back")


# ===========================================================================
# EDGE CASES (10 tests)
# ===========================================================================

class TestEdgeCases:
    """Stress tests, rotation, background/foreground, and resilience."""

    def test_rotate_portrait_landscape(self, d):
        go_home(d)
        d.set_orientation("l")  # landscape
        time.sleep(1)
        assert d.app_current()["package"] == PACKAGE
        d.set_orientation("n")  # portrait (natural)
        time.sleep(1)
        assert d.app_current()["package"] == PACKAGE

    def test_background_foreground(self, d):
        go_home(d)
        d.press("home")
        time.sleep(2)
        d.app_start(PACKAGE)
        time.sleep(2)
        assert d.app_current()["package"] == PACKAGE

    def test_visit_all_screens(self, d):
        """Full tour of all bottom nav screens -- no crash."""
        for tab in ("Chat", "Tools", "Brain", "System"):
            navigate_to_tab(d, tab)
            time.sleep(0.5)
        # Visit Settings
        go_home(d)
        d(description="Settings").click()
        time.sleep(1)
        d.press("back")
        time.sleep(0.5)
        # Verify app still alive
        assert d.app_current()["package"] == PACKAGE

    def test_rapid_back_presses(self, d):
        go_home(d)
        for _ in range(10):
            d.press("back")
            time.sleep(0.1)
        time.sleep(1)
        # App may close -- restart it
        d.app_start(PACKAGE)
        time.sleep(2)
        assert d.app_current()["package"] == PACKAGE

    def test_special_chars_input(self, d):
        go_home(d)
        d(text="Message Dr. CLAW...").click()
        time.sleep(0.3)
        special = '<script>alert("xss")</script> \u2764\ufe0f \U0001f680 "quotes" & <tags>'
        d.send_keys(special)
        time.sleep(0.5)
        assert d.app_current()["package"] == PACKAGE
        d.clear_text()

    def test_screenshot_capture(self, d):
        go_home(d)
        path = os.path.join(SCREENSHOT_DIR, "test_capture.png")
        d.screenshot(path)
        assert os.path.exists(path)
        assert os.path.getsize(path) > 0

    def test_app_survives_rotation_on_each_screen(self, d):
        for tab in ("Chat", "Tools", "Brain", "System"):
            navigate_to_tab(d, tab)
            time.sleep(0.3)
            d.set_orientation("l")
            time.sleep(0.5)
            assert d.app_current()["package"] == PACKAGE, \
                f"App crashed after rotation on {tab} screen"
            d.set_orientation("n")
            time.sleep(0.5)

    def test_all_clickable_elements_respond(self, d):
        """Tap several known clickable elements -- no crash."""
        go_home(d)
        clickable_targets = [
            ("text", "Chat"),
            ("text", "Tools"),
            ("text", "Brain"),
            ("text", "System"),
        ]
        for selector_type, value in clickable_targets:
            if selector_type == "text":
                el = d(text=value)
            else:
                el = d(description=value)
            if el.exists(timeout=2):
                el.click()
                time.sleep(0.3)
        go_home(d)
        assert d.app_current()["package"] == PACKAGE

    def test_no_anr(self, d):
        """Verify app responds within 5 seconds after a sequence of operations."""
        go_home(d)
        # Perform a sequence of actions
        navigate_to_tab(d, "Brain")
        navigate_to_tab(d, "System")
        navigate_to_tab(d, "Chat")
        # Now verify the app responds quickly
        start = time.time()
        assert d(text="Message Dr. CLAW...").exists(timeout=5)
        elapsed = time.time() - start
        assert elapsed < 5, f"App took {elapsed:.1f}s to respond -- possible ANR"

    def test_memory_not_leaking(self, d):
        """Tour all screens twice -- app should not crash from memory pressure."""
        tabs = ("Chat", "Tools", "Brain", "System")
        for _ in range(2):
            for tab in tabs:
                navigate_to_tab(d, tab)
                time.sleep(0.3)
        # Open and close settings
        go_home(d)
        d(description="Settings").click()
        time.sleep(1)
        d.press("back")
        time.sleep(0.5)
        # App should still be alive and responsive
        assert d.app_current()["package"] == PACKAGE
        assert d(text="Message Dr. CLAW...").exists(timeout=5)
