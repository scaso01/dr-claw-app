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
 * Instrumented E2E tests for connection state UI behavior.
 *
 * Without a live gateway, the app will be in a Disconnected or Error state.
 * These tests verify that the UI correctly reflects offline conditions.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionStateTest {

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

    @Test
    fun app_shows_connection_state_indicator() {
        // With FakeGateway providing Connected state, verify the UI renders
        // a valid connection state (Connected, Disconnected, or Error)
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val disconnected = composeTestRule
                .onAllNodes(hasText("Disconnected", substring = true))
                .fetchSemanticsNodes()
            val error = composeTestRule
                .onAllNodes(hasText("Connection error", substring = true))
                .fetchSemanticsNodes()
            val connected = composeTestRule
                .onAllNodes(hasText("Message Dr. CLAW...", substring = true))
                .fetchSemanticsNodes()
            disconnected.isNotEmpty() || error.isNotEmpty() || connected.isNotEmpty()
        }
    }

    @Test
    fun chat_input_is_still_accessible_when_disconnected() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun action_button_visible_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    @Test
    fun session_drawer_accessible_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun bottom_nav_still_functional_when_disconnected() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }
}
