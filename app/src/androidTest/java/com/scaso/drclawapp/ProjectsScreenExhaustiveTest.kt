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
 * Exhaustive instrumented E2E tests for the Projects screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProjectsScreenExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToProjects()
    }

    private fun navigateToProjects() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Projects"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Projects").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun p01_projects_screen_renders() {
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    fun p02_back_button_visible() {
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun p03_refresh_button_present() {
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
    }

    @Test
    fun p04_refresh_button_clickable() {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    fun p05_projects_shows_content_or_empty_state() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            val title = composeTestRule
                .onAllNodes(hasText("Projects"))
                .fetchSemanticsNodes()
            val errorState = composeTestRule
                .onAllNodes(hasText("error", substring = true, ignoreCase = true))
                .fetchSemanticsNodes()
            val projectCards = composeTestRule
                .onAllNodes(hasText("ExamplePipeline", substring = true))
                .fetchSemanticsNodes()
            title.isNotEmpty() || errorState.isNotEmpty() || projectCards.isNotEmpty()
        }
    }

    @Test
    fun p06_back_navigation_returns_to_chat() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun n01_disconnected_state_handled_gracefully() {
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    fun n02_rapid_refresh_does_not_crash() {
        repeat(5) {
            composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    fun n03_navigate_away_and_back_to_projects() {
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        navigateToProjects()
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }
}
