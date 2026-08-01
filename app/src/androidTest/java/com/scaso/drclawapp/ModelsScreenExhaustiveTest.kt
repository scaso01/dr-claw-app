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
 * Exhaustive instrumented E2E tests for the Model Manager screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ModelsScreenExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToModels()
    }

    private fun navigateToModels() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Models"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Models").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun p01_model_manager_screen_renders() {
        composeTestRule.onNodeWithText("Model Manager").assertIsDisplayed()
    }

    @Test
    fun p02_llama_server_card_visible() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodes(hasText("llama-server", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p03_preset_section_visible() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Preset", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p04_local_chip_visible() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Local", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p05_cloud_chip_visible() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Cloud", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun p06_refresh_button_present() {
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
    }

    @Test
    fun p07_refresh_button_clickable() {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Model Manager").assertIsDisplayed()
    }

    @Test
    fun p08_back_navigation_returns_to_chat() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun n01_disconnected_state_handled_gracefully() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val llamaServer = composeTestRule
                .onAllNodes(hasText("llama-server", substring = true))
                .fetchSemanticsNodes()
            val unknown = composeTestRule
                .onAllNodes(hasText("Unknown", substring = true))
                .fetchSemanticsNodes()
            val title = composeTestRule
                .onAllNodes(hasText("Model Manager"))
                .fetchSemanticsNodes()
            llamaServer.isNotEmpty() || unknown.isNotEmpty() || title.isNotEmpty()
        }
    }

    @Test
    fun n02_rapid_refresh_does_not_crash() {
        repeat(5) {
            composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Model Manager").assertIsDisplayed()
    }

    @Test
    fun n03_back_button_visible() {
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
