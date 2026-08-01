package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
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
 * Exhaustive instrumented E2E tests for the Infrastructure screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InfrastructureScreenExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToInfraTab()
    }

    private fun navigateToInfraTab() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Infra", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p01_infra_tab_renders_within_system() {
        composeTestRule.onNodeWithText("Infra").assertIsDisplayed()
    }

    @Test
    fun p02_ironjaw_gateway_card_visible() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Ironjaw Gateway", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p03_gateway_shows_connection_state() {
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val connected = composeTestRule
                .onAllNodes(hasText("Connected", substring = true))
                .fetchSemanticsNodes()
            val disconnected = composeTestRule
                .onAllNodes(hasText("Disconnected", substring = true))
                .fetchSemanticsNodes()
            val connecting = composeTestRule
                .onAllNodes(hasText("Connecting", substring = true))
                .fetchSemanticsNodes()
            val error = composeTestRule
                .onAllNodes(hasText("Error", substring = true))
                .fetchSemanticsNodes()
            connected.isNotEmpty() || disconnected.isNotEmpty() ||
                connecting.isNotEmpty() || error.isNotEmpty()
        }
    }

    @Test
    fun p04_system_screen_has_schedules_tab() {
        composeTestRule.onNodeWithText("Schedules").assertIsDisplayed()
    }

    @Test
    fun p05_system_screen_has_devices_tab() {
        composeTestRule.onNodeWithText("Devices").assertIsDisplayed()
    }

    @Test
    fun p06_system_screen_has_metrics_tab() {
        composeTestRule.onNodeWithText("Metrics").assertIsDisplayed()
    }

    @Test
    fun n01_infra_handles_disconnected_state_gracefully() {
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val gateway = composeTestRule
                .onAllNodes(hasText("Ironjaw Gateway", substring = true))
                .fetchSemanticsNodes()
            val disconnected = composeTestRule
                .onAllNodes(hasText("Disconnected", substring = true))
                .fetchSemanticsNodes()
            val error = composeTestRule
                .onAllNodes(hasText("Error", substring = true))
                .fetchSemanticsNodes()
            gateway.isNotEmpty() || disconnected.isNotEmpty() || error.isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun n02_infra_tab_still_navigable_after_error_state() {
        composeTestRule.onNodeWithText("Schedules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Infra").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Infra").assertIsDisplayed()
    }

    @Test
    fun p07_back_to_chat_from_system_via_bottom_nav() {
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }
}
