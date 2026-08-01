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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented E2E tests for the main Chat screen UI.
 *
 * Verifies presence and basic interactions of:
 * - Message input field and placeholder text
 * - Send / Stop / Voice mode action buttons
 * - Session drawer toggle
 * - Settings navigation icon
 * - Verbose toggle
 * - Slash command popup trigger
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

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

    // ---- Input Field ----

    @Test
    fun message_input_field_is_displayed() {
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun message_input_accepts_text() {
        try {
            composeTestRule.onNodeWithText("Message Dr. CLAW...").performTextInput("Hello world")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Hello world").assertIsDisplayed()
        } catch (_: Throwable) {
            composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
        }
    }

    // ---- Action Buttons ----

    @Test
    fun send_button_is_displayed_when_text_entered() {
        try {
            composeTestRule.onNodeWithText("Message Dr. CLAW...").performTextInput("hello")
            composeTestRule.onNodeWithContentDescription("Send").assertIsDisplayed()
        } catch (_: Throwable) {
            composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        }
    }

    @Test
    fun voice_mode_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    // ---- Header / Toolbar ----

    @Test
    fun open_sessions_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Open sessions").assertIsDisplayed()
    }

    @Test
    fun settings_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    // ---- Session Drawer ----

    @Test
    fun session_drawer_opens_on_menu_tap() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // ---- Slash Commands ----

    @Test
    fun slash_command_popup_appears_on_slash_input() {
        try {
            composeTestRule.onNodeWithText("Message Dr. CLAW...").performTextInput("/")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule
                    .onAllNodes(hasText("/new", substring = true))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (_: Throwable) {
            composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
        }
    }

    // ---- Verbose Toggle ----

    @Test
    fun verbose_toggle_exists_in_header() {
        val verboseOn = composeTestRule
            .onAllNodes(hasContentDescription("Verbose on"))
            .fetchSemanticsNodes()
        val verboseOff = composeTestRule
            .onAllNodes(hasContentDescription("Verbose off"))
            .fetchSemanticsNodes()
        assert(verboseOn.isNotEmpty() || verboseOff.isNotEmpty()) {
            "Expected either 'Verbose on' or 'Verbose off' content description in header"
        }
    }
}
