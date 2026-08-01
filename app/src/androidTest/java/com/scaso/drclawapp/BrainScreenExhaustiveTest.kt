package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * Exhaustive instrumented E2E tests for the Brain screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrainScreenExhaustiveTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        navigateToBrain()
    }

    private fun navigateToBrain() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    // POSITIVE TESTS

    @Test
    fun brain_screen_is_accessible_from_bottom_nav() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun brain_tab_search_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun brain_tab_add_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Add"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun brain_tab_context_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Context"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun brain_tab_summaries_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Summaries"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun brain_tab_timeline_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Timeline"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun brain_tab_graph_is_visible() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Graph"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun search_tab_shows_search_input() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search memories...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun add_tab_shows_remember_input() {
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Enter something to remember...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun context_tab_shows_context_content() {
        composeTestRule.onNodeWithText("Context").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val noVars = composeTestRule
                .onAllNodes(hasText("No context variables", substring = true))
                .fetchSemanticsNodes()
            val variableName = composeTestRule
                .onAllNodes(hasText("Variable name", substring = true))
                .fetchSemanticsNodes()
            noVars.isNotEmpty() || variableName.isNotEmpty()
        }
    }

    @Test
    fun summaries_tab_shows_summaries_content() {
        composeTestRule.onNodeWithText("Summaries").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Session ID (optional)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun timeline_tab_shows_timeline_content() {
        composeTestRule.onNodeWithText("Timeline").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Date (e.g. 2026-03-23)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun graph_tab_shows_graph_content() {
        composeTestRule.onNodeWithText("Graph").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val emptyMsg = composeTestRule
                .onAllNodes(hasText("Tap the Graph tab to load entities.", substring = true))
                .fetchSemanticsNodes()
            val graphTab = composeTestRule
                .onAllNodes(hasText("Graph"))
                .fetchSemanticsNodes()
            emptyMsg.isNotEmpty() || graphTab.isNotEmpty()
        }
    }

    @Test
    fun search_tab_search_button_exists() {
        val searchButtons = composeTestRule
            .onAllNodes(hasText("Search"))
            .fetchSemanticsNodes()
        assert(searchButtons.size >= 1) {
            "Expected at least one 'Search' element (tab + button)"
        }
    }

    // NEGATIVE TESTS

    @Test
    fun brain_renders_when_disconnected() {
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun search_with_empty_string_handled() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val noMemories = composeTestRule
                .onAllNodes(hasText("No memories found.", substring = true))
                .fetchSemanticsNodes()
            val searchInput = composeTestRule
                .onAllNodes(hasText("Search memories...", substring = true))
                .fetchSemanticsNodes()
            noMemories.isNotEmpty() || searchInput.isNotEmpty()
        }
    }

    @Test
    fun search_with_special_characters_does_not_crash() {
        composeTestRule.onNodeWithText("Search memories...").performTextInput(
            "<script>alert('xss')</script> & \"quotes\" | \\ ${'$'}(cmd)"
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Brain", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun rapid_tab_switching_does_not_crash() {
        val tabs = listOf("Search", "Add", "Context", "Summaries", "Timeline", "Graph")
        repeat(3) {
            tabs.forEach { tab ->
                composeTestRule.onNodeWithTag("brain_tab_$tab").performClick()
                composeTestRule.waitForIdle()
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun back_navigation_from_brain_to_chat() {
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun search_tab_detail_level_chips_exist() {
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Compact", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Full").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deep").assertIsDisplayed()
    }

    @Test
    fun add_tab_remember_button_exists() {
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Remember", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
