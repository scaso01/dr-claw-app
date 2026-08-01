package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
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
 * Instrumented E2E tests for the main navigation flows.
 *
 * Verifies:
 * - App launches to the Chat screen (default start destination)
 * - Bottom navigation bar is visible on main tabs
 * - Each bottom nav tab (Chat, Tools, Brain, System) is reachable
 * - Settings screen is accessible from the Chat header
 * - Back navigation returns to previous screen
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
    }

    // ---- Bottom Navigation ----

    @Test
    fun app_launches_to_chat_screen() {
        // The Chat tab should be the visible/selected bottom nav item on launch
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun bottom_nav_chat_tab_is_displayed() {
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun bottom_nav_tools_tab_is_displayed() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
    }

    @Test
    fun bottom_nav_brain_tab_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun bottom_nav_system_tab_is_displayed() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun navigate_to_tools_tab() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitForIdle()
        // After clicking Tools, that tab should be selected
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
    }

    @Test
    fun navigate_to_brain_tab() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun navigate_to_system_tab() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun navigate_back_to_chat_from_tools() {
        // Go to Tools
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitForIdle()
        // Go back to Chat
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        // Chat-specific elements should be visible again (message input)
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    // ---- Settings Navigation ----

    @Test
    fun settings_icon_is_accessible_from_chat() {
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun navigate_to_settings_screen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        // Settings screen title
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_settings() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        // Press back arrow
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        // Should be back on Chat
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }
}
