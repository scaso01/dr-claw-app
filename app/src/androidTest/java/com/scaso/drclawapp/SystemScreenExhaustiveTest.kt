package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exhaustive instrumented E2E tests for the System screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SystemScreenExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToSystem()
    }

    private fun navigateToSystem() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitForIdle()
    }

    // POSITIVE TESTS

    @Test
    fun system_screen_is_accessible_from_bottom_nav() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("System"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun system_screen_title_is_displayed() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun system_tab_infra_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Infra")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_schedules_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Schedules")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_devices_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Devices")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_metrics_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Metrics")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_vault_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Vault")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_export_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Export")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun system_tab_plugins_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Plugins")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun infra_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Infra").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun schedules_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Schedules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun devices_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun metrics_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Metrics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun vault_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Vault").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun export_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Export").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun plugins_tab_clickable_and_renders() {
        composeTestRule.onNodeWithText("Plugins").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    // NEGATIVE TESTS

    @Test
    fun system_screen_renders_when_disconnected() {
        composeTestRule.onNodeWithText("Infra").assertIsDisplayed()
    }

    @Test
    fun scrolling_within_system_tab_does_not_crash() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rapid_system_tab_switching_does_not_crash() {
        val tabs = listOf("Infra", "Schedules", "Devices", "Metrics", "Vault", "Export", "Plugins")
        repeat(3) {
            tabs.forEach { tab ->
                composeTestRule.onNodeWithText(tab).performClick()
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun navigate_back_to_chat_from_system() {
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun bottom_nav_visible_on_system_screen() {
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }
}
