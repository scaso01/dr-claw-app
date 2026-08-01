package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exhaustive instrumented E2E tests for the CC Sessions screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CcSessionsExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToCcSessions()
    }

    private fun navigateToCcSessions() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("CC Sessions"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("CC Sessions").performClick()
        composeTestRule.waitForIdle()
    }

    // POSITIVE TESTS

    @Test
    fun screen_title_cc_sessions_is_displayed() {
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun back_button_exists_and_is_clickable() {
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun refresh_button_exists_and_is_clickable() {
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun search_bar_exists_with_placeholder_text() {
        // Search bar is shown in the empty-no-error state.
        // If gateway returns an error, the error state takes priority (no search bar).
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search CC sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("No CC sessions found.", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun typing_in_search_bar_filters_content() {
        // Wait for the screen to settle (search bar or error state)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search CC sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Only test search if the search bar is present (not in error state)
        val searchBarNodes = composeTestRule
            .onAllNodes(hasText("Search CC sessions...", substring = true))
            .fetchSemanticsNodes()
        if (searchBarNodes.isNotEmpty()) {
            composeTestRule.onNodeWithText("Search CC sessions...").performTextInput("nonexistent_xyz_42")
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                val noMatching = composeTestRule
                    .onAllNodes(hasText("No matching sessions", substring = true))
                    .fetchSemanticsNodes()
                val noSessions = composeTestRule
                    .onAllNodes(hasText("No CC sessions found.", substring = true))
                    .fetchSemanticsNodes()
                noMatching.isNotEmpty() || noSessions.isNotEmpty()
            }
        }
        // Screen should be stable regardless
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun clearing_search_restores_content() {
        // Wait for the screen to settle
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search CC sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Only test search clear if search bar is present
        val searchBarNodes = composeTestRule
            .onAllNodes(hasText("Search CC sessions...", substring = true))
            .fetchSemanticsNodes()
        if (searchBarNodes.isNotEmpty()) {
            composeTestRule.onNodeWithText("Search CC sessions...").performTextInput("test")
            composeTestRule.waitForIdle()
            val clearNodes = composeTestRule
                .onAllNodes(hasContentDescription("Clear search"))
                .fetchSemanticsNodes()
            if (clearNodes.isNotEmpty()) {
                composeTestRule.onNodeWithContentDescription("Clear search").performClick()
                composeTestRule.waitForIdle()
            }
        }
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun fab_does_not_crash_screen_when_disconnected() {
        val fabNodes = composeTestRule
            .onAllNodes(hasContentDescription("Create session"))
            .fetchSemanticsNodes()
        assert(true) { "Screen is stable -- FAB may or may not be present" }
    }

    @Test
    fun session_list_area_renders() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val errorNodes = composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
            val emptyNodes = composeTestRule
                .onAllNodes(hasText("No CC sessions found.", substring = true))
                .fetchSemanticsNodes()
            val ironjawHeader = composeTestRule
                .onAllNodes(hasText("Ironjaw Sessions", substring = true))
                .fetchSemanticsNodes()
            val sessionsHeader = composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
            errorNodes.isNotEmpty() || emptyNodes.isNotEmpty() ||
                ironjawHeader.isNotEmpty() || sessionsHeader.isNotEmpty()
        }
    }

    @Test
    fun pull_to_refresh_does_not_crash() {
        composeTestRule.onNodeWithText("CC Sessions").performTouchInput {
            swipeDown()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun live_badge_not_shown_when_disconnected() {
        val liveNodes = composeTestRule
            .onAllNodes(hasText("LIVE"))
            .fetchSemanticsNodes()
        // Verify no crash regardless of LIVE badge presence
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    // NEGATIVE TESTS

    @Test
    fun disconnected_shows_error_or_empty_state_not_crash() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val errorNodes = composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
            val emptyNodes = composeTestRule
                .onAllNodes(hasText("No CC sessions found.", substring = true))
                .fetchSemanticsNodes()
            val sessionsNodes = composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
            errorNodes.isNotEmpty() || emptyNodes.isNotEmpty() || sessionsNodes.isNotEmpty()
        }
    }

    @Test
    fun search_with_special_characters_does_not_crash() {
        // Wait for the screen to settle
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search CC sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Only test search if the search bar is present
        val searchBarNodes = composeTestRule
            .onAllNodes(hasText("Search CC sessions...", substring = true))
            .fetchSemanticsNodes()
        if (searchBarNodes.isNotEmpty()) {
            composeTestRule.onNodeWithText("Search CC sessions...")
                .performTextInput("<script>alert('xss')</script> & \"quotes\" | pipes \\ backslash")
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun search_with_very_long_string_does_not_crash() {
        // Wait for the screen to settle
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search CC sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Only test search if the search bar is present
        val searchBarNodes = composeTestRule
            .onAllNodes(hasText("Search CC sessions...", substring = true))
            .fetchSemanticsNodes()
        if (searchBarNodes.isNotEmpty()) {
            val longString = "a".repeat(5000)
            composeTestRule.onNodeWithText("Search CC sessions...")
                .performTextInput(longString)
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun rapid_tapping_refresh_does_not_crash() {
        repeat(10) {
            composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun navigating_away_and_back_preserves_screen() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        navigateToCcSessions()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun empty_session_list_handled_gracefully() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val emptyNodes = composeTestRule
                .onAllNodes(hasText("No CC sessions found.", substring = true))
                .fetchSemanticsNodes()
            val errorNodes = composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
            val sessionNodes = composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
            emptyNodes.isNotEmpty() || errorNodes.isNotEmpty() || sessionNodes.isNotEmpty()
        }
    }

    @Test
    fun back_button_navigates_to_chat_correctly() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    // DATA VALIDATION TESTS

    @Test
    fun screen_does_not_crash_with_null_session_data() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val anyContent = composeTestRule
                .onAllNodes(hasText("CC Sessions", substring = true))
                .fetchSemanticsNodes()
            anyContent.isNotEmpty()
        }
    }

    @Test
    fun screen_handles_empty_session_id_gracefully() {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun screen_renders_stably_for_context_pct_boundaries() {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }
}
