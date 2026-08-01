package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Instrumented E2E tests for the Settings screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToSettings()
    }

    private fun navigateToSettings() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
    }

    // ---- Screen Structure ----

    @Test
    fun settings_screen_title_is_displayed() {
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun back_button_is_displayed() {
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    // ---- Appearance Section ----

    @Test
    fun appearance_section_header_is_displayed() {
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun theme_label_is_displayed() {
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun theme_mode_system_option_exists() {
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun theme_mode_light_option_exists() {
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
    }

    @Test
    fun theme_mode_dark_option_exists() {
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    // ---- Chat Section ----

    @Test
    fun chat_section_header_is_displayed() {
        composeTestRule.onNodeWithText("Chat").performScrollTo()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
    }

    @Test
    fun custom_instructions_label_is_displayed() {
        composeTestRule.onNodeWithText("Custom Instructions").performScrollTo()
        composeTestRule.onNodeWithText("Custom Instructions").assertIsDisplayed()
    }

    // ---- Effort Level Section ----

    @Test
    fun effort_level_section_is_displayed() {
        composeTestRule.onNodeWithText("Effort Level").performScrollTo()
        composeTestRule.onNodeWithText("Effort Level").assertIsDisplayed()
    }

    @Test
    fun effort_level_low_option_exists() {
        composeTestRule.onNodeWithText("Low").performScrollTo()
        composeTestRule.onNodeWithText("Low").assertIsDisplayed()
    }

    @Test
    fun effort_level_medium_option_exists() {
        composeTestRule.onNodeWithText("Medium").performScrollTo()
        composeTestRule.onNodeWithText("Medium").assertIsDisplayed()
    }

    @Test
    fun effort_level_high_option_exists() {
        composeTestRule.onNodeWithText("High").performScrollTo()
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
    }

    // ---- Connection Section ----

    @Test
    fun connection_section_is_displayed() {
        composeTestRule.onNodeWithText("Connection").performScrollTo()
        composeTestRule.onNodeWithText("Connection").assertIsDisplayed()
    }

    @Test
    fun gateway_url_field_is_displayed() {
        composeTestRule.onNodeWithText("Gateway URL").performScrollTo()
        composeTestRule.onNodeWithText("Gateway URL").assertIsDisplayed()
    }

    @Test
    fun reconnect_button_is_displayed() {
        composeTestRule.onNodeWithText("Reconnect").performScrollTo()
        composeTestRule.onNodeWithText("Reconnect").assertIsDisplayed()
    }

    @Test
    fun pair_device_button_is_displayed() {
        composeTestRule.onNodeWithText("Pair Device").performScrollTo()
        composeTestRule.onNodeWithText("Pair Device").assertIsDisplayed()
    }

    // ---- About Section ----

    @Test
    fun about_section_is_displayed() {
        composeTestRule.onNodeWithText("About").performScrollTo()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun version_label_is_displayed() {
        composeTestRule.onNodeWithText("Version").performScrollTo()
        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
    }

    // ---- Navigation ----

    @Test
    fun back_button_returns_to_chat() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }
}
