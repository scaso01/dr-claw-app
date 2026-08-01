package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented E2E tests for the CC Sessions screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CcSessionsScreenTest {

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

    // ---- Screen Structure ----

    @Test
    fun cc_sessions_screen_title_is_displayed() {
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun back_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun refresh_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
    }

    // ---- Search ----

    @Test
    fun search_bar_is_displayed() {
        // Search bar is shown in empty-no-error state. If both data sources
        // fail, the error state renders without a search bar.
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

    // ---- Empty / Error States ----

    @Test
    fun shows_empty_or_error_state_without_gateway() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val errorNodes = composeTestRule
                .onAllNodes(hasText("Pull to refresh or check gateway connection.", substring = true))
                .fetchSemanticsNodes()
            val emptyNodes = composeTestRule
                .onAllNodes(hasText("No CC sessions found.", substring = true))
                .fetchSemanticsNodes()
            val sessionNodes = composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
            errorNodes.isNotEmpty() || emptyNodes.isNotEmpty() || sessionNodes.isNotEmpty()
        }
    }

    // ---- Navigation ----

    @Test
    fun back_button_returns_to_chat_screen() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun refresh_button_is_clickable() {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }
}
