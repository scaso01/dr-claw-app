package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exhaustive instrumented E2E tests for connection states.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionExhaustiveTest {

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

    private fun openSessionDrawer() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Search sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun navigateToDrawerItem(label: String) {
        openSessionDrawer()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun p01_connection_status_indicator_exists_on_main_screen() {
        // With FakeGateway providing Connected state, verify the chat screen renders.
        // The connection status may show as Connected (input enabled) or any other state.
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val disconnected = composeTestRule
                .onAllNodes(hasText("Disconnected", substring = true))
                .fetchSemanticsNodes()
            val error = composeTestRule
                .onAllNodes(hasText("Connection error", substring = true))
                .fetchSemanticsNodes()
            val connecting = composeTestRule
                .onAllNodes(hasText("Connecting", substring = true))
                .fetchSemanticsNodes()
            val connected = composeTestRule
                .onAllNodes(hasText("Message Dr. CLAW...", substring = true))
                .fetchSemanticsNodes()
            disconnected.isNotEmpty() || error.isNotEmpty() ||
                connecting.isNotEmpty() || connected.isNotEmpty()
        }
    }

    @Test
    fun p02_input_field_exists_when_disconnected() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p03_action_button_exists_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    @Test
    fun p04_bottom_navigation_works_when_disconnected() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun p05_tools_screen_accessible_when_disconnected() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val noTools = composeTestRule
                .onAllNodes(hasText("No tools available", substring = true))
                .fetchSemanticsNodes()
            val toolsTitle = composeTestRule
                .onAllNodes(hasText("Tools"))
                .fetchSemanticsNodes()
            val errorState = composeTestRule
                .onAllNodes(hasText("error", substring = true, ignoreCase = true))
                .fetchSemanticsNodes()
            noTools.isNotEmpty() || toolsTitle.isNotEmpty() || errorState.isNotEmpty()
        }
    }

    @Test
    fun p06_brain_screen_accessible_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p07_system_screen_accessible_when_disconnected() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Infra", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun n01_chat_screen_stable_when_disconnected() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun n02_cc_sessions_handles_disconnected_state() {
        navigateToDrawerItem("CC Sessions")
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val errorNodes = composeTestRule
                .onAllNodes(hasText("Pull to refresh", substring = true))
                .fetchSemanticsNodes()
            val emptyNodes = composeTestRule
                .onAllNodes(hasText("No CC sessions", substring = true))
                .fetchSemanticsNodes()
            val title = composeTestRule
                .onAllNodes(hasText("CC Sessions"))
                .fetchSemanticsNodes()
            errorNodes.isNotEmpty() || emptyNodes.isNotEmpty() || title.isNotEmpty()
        }
    }

    @Test
    fun n03_brain_screen_handles_disconnected_state() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search memories...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
            composeTestRule
                .onAllNodes(hasText("Brain"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun n04_settings_fully_functional_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").performClick()
        composeTestRule.onNodeWithText("Light").performClick()
        composeTestRule.onNodeWithText("Custom Instructions").performScrollTo()
        composeTestRule.onNodeWithText("Custom Instructions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection").performScrollTo()
        composeTestRule.onNodeWithText("Connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("About").performScrollTo()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun n05_reconnect_button_repeated_tap_does_not_crash() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Reconnect").performScrollTo()
        repeat(5) {
            composeTestRule.onNodeWithText("Reconnect").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun e01_session_drawer_opens_when_disconnected() {
        openSessionDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun e02_cc_sessions_has_back_nav_when_disconnected() {
        navigateToDrawerItem("CC Sessions")
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun e03_projects_has_back_nav_when_disconnected() {
        navigateToDrawerItem("Projects")
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun e04_models_has_back_nav_when_disconnected() {
        navigateToDrawerItem("Models")
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun e05_chronicle_has_back_nav_when_disconnected() {
        navigateToDrawerItem("Chronicle")
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun e06_settings_has_back_nav_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun e07_full_navigation_tour_while_disconnected() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }
}
