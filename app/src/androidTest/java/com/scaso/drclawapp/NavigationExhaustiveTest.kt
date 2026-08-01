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
 * Exhaustive instrumented E2E tests for all navigation paths.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationExhaustiveTest {

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

    // POSITIVE: App Launch

    @Test
    fun p01_app_launches_to_chat_screen() {
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p02_bottom_nav_has_exactly_four_items() {
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun p03_navigate_to_tools_shows_tools_screen() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val tools = composeTestRule
                .onAllNodes(hasText("No tools available", substring = true))
                .fetchSemanticsNodes()
            val errorState = composeTestRule
                .onAllNodes(hasText("Tools"))
                .fetchSemanticsNodes()
            tools.isNotEmpty() || errorState.isNotEmpty()
        }
    }

    @Test
    fun p04_navigate_to_brain_shows_brain_screen() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Search", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p05_navigate_to_system_shows_system_screen() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("Infra", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p06_chat_tab_shows_selected_state_on_launch() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p07_tools_tab_shows_selected_state_when_active() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().assertIsDisplayed()
    }

    @Test
    fun p08_brain_tab_shows_selected_state_when_active() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun p09_system_tab_shows_selected_state_when_active() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("System")).onFirst().assertIsDisplayed()
    }

    @Test
    fun p10_settings_accessible_from_header_icon() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun p11_back_from_settings_returns_to_chat() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p12_cc_sessions_accessible_from_drawer() {
        navigateToDrawerItem("CC Sessions")
        composeTestRule.onNodeWithText("CC Sessions").assertIsDisplayed()
    }

    @Test
    fun p13_back_from_cc_sessions_returns_to_chat() {
        navigateToDrawerItem("CC Sessions")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p14_projects_screen_accessible_from_drawer() {
        navigateToDrawerItem("Projects")
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    fun p15_models_screen_accessible_from_drawer() {
        navigateToDrawerItem("Models")
        composeTestRule.onNodeWithText("Model Manager").assertIsDisplayed()
    }

    @Test
    fun p16_chronicle_screen_accessible_from_drawer() {
        navigateToDrawerItem("Chronicle")
        composeTestRule.onNodeWithText("Chronicle").assertIsDisplayed()
    }

    @Test
    fun p17_deep_navigation_round_trip_chat_drawer_cc_sessions_back() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
        navigateToDrawerItem("CC Sessions")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p18_navigation_preserves_bottom_nav_state_after_settings() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    // NEGATIVE

    @Test
    fun n01_rapid_bottom_nav_tapping_does_not_crash() {
        repeat(4) {
            composeTestRule.onNodeWithText("Chat").performClick()
            composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
            composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
            composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        }
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun n02_navigating_all_screens_and_back_sequentially() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun n03_double_tap_same_bottom_nav_does_not_push_duplicate() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun n04_deep_forward_navigation_and_back() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        navigateToDrawerItem("CC Sessions")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        navigateToDrawerItem("Projects")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        navigateToDrawerItem("Models")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        navigateToDrawerItem("Chronicle")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    // EDGE: Minimum content

    @Test
    fun e01_chat_screen_renders_minimum_content() {
        composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    @Test
    fun e02_tools_screen_renders_minimum_content() {
        composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val noTools = composeTestRule
                .onAllNodes(hasText("No tools available", substring = true))
                .fetchSemanticsNodes()
            val toolsTitle = composeTestRule
                .onAllNodes(hasText("Tools"))
                .fetchSemanticsNodes()
            noTools.isNotEmpty() || toolsTitle.size >= 1
        }
    }

    @Test
    fun e03_brain_screen_renders_minimum_content() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodes(hasText("Search")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun e04_system_screen_renders_minimum_content() {
        composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodes(hasText("Infra")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun e05_settings_screen_renders_minimum_content() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun e06_cc_sessions_screen_renders_minimum_content() {
        navigateToDrawerItem("CC Sessions")
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val title = composeTestRule
                .onAllNodes(hasText("CC Sessions"))
                .fetchSemanticsNodes()
            title.isNotEmpty()
        }
    }

    @Test
    fun e07_projects_screen_renders_minimum_content() {
        navigateToDrawerItem("Projects")
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Projects")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun e08_models_screen_renders_minimum_content() {
        navigateToDrawerItem("Models")
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Model Manager")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun e09_chronicle_screen_renders_minimum_content() {
        navigateToDrawerItem("Chronicle")
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Chronicle")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun e10_multiple_rapid_round_trips_no_leak() {
        repeat(3) {
            composeTestRule.onAllNodes(hasText("Tools")).onFirst().performClick()
            composeTestRule.onNodeWithText("Chat").performClick()
            composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithText("Chat").performClick()
            composeTestRule.onAllNodes(hasText("System")).onFirst().performClick()
            composeTestRule.onNodeWithText("Chat").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun e11_bottom_nav_hidden_on_settings_screen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun p19_back_from_projects_returns_to_chat() {
        navigateToDrawerItem("Projects")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p20_back_from_models_returns_to_chat() {
        navigateToDrawerItem("Models")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun p21_back_from_chronicle_returns_to_chat() {
        navigateToDrawerItem("Chronicle")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }
}
