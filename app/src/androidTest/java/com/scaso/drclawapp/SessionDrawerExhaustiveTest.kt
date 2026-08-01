package com.scaso.drclawapp

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
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
 * Exhaustive instrumented E2E tests for the Session Drawer.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionDrawerExhaustiveTest {

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

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // POSITIVE TESTS

    @Test
    fun session_drawer_opens_from_chat_header() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Sessions", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun drawer_shows_session_list_or_search_bar() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun drawer_shows_conversations_section_or_empty() {
        openDrawer()
        val conversationsNodes = composeTestRule
            .onAllNodes(hasText("Conversations", substring = true))
            .fetchSemanticsNodes()
        val searchNodes = composeTestRule
            .onAllNodes(hasText("Search sessions...", substring = true))
            .fetchSemanticsNodes()
        assert(conversationsNodes.isNotEmpty() || searchNodes.isNotEmpty()) {
            "Drawer should show either Conversations header or search bar"
        }
    }

    @Test
    fun new_chat_button_exists_in_drawer() {
        openDrawer()
        composeTestRule.onNodeWithContentDescription("New Chat").assertIsDisplayed()
    }

    @Test
    fun cc_sessions_navigation_item_exists_in_drawer() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("CC Sessions")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun drawer_can_be_closed() {
        openDrawer()
        composeTestRule.onNodeWithContentDescription("New Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Message Dr. CLAW..."))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // NEGATIVE TESTS

    @Test
    fun drawer_opens_gracefully_in_any_connection_state() {
        // Verify the drawer opens regardless of connection state (Connected or Disconnected)
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val chatReady = composeTestRule
                .onAllNodes(hasText("Message Dr. CLAW...", substring = true))
                .fetchSemanticsNodes()
            val disconnected = composeTestRule
                .onAllNodes(hasText("Disconnected", substring = true))
                .fetchSemanticsNodes()
            val error = composeTestRule
                .onAllNodes(hasText("Connection error", substring = true))
                .fetchSemanticsNodes()
            chatReady.isNotEmpty() || disconnected.isNotEmpty() || error.isNotEmpty()
        }
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodes(hasText("Search sessions...", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun rapid_drawer_toggle_does_not_crash() {
        repeat(5) {
            composeTestRule.onNodeWithContentDescription("Open sessions").performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Message Dr. CLAW...").assertIsDisplayed()
    }

    @Test
    fun drawer_navigation_items_clickable_when_disconnected() {
        openDrawer()
        composeTestRule.onNodeWithText("CC Sessions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun drawer_handles_search_with_long_query() {
        openDrawer()
        val longQuery = "a".repeat(500)
        composeTestRule.onNodeWithText("Search sessions...").performTextInput(longQuery)
        composeTestRule.waitForIdle()
        val sessionsHeader = composeTestRule
            .onAllNodes(hasText("Sessions", substring = true))
            .fetchSemanticsNodes()
        assert(sessionsHeader.isNotEmpty()) {
            "Sessions header should still be visible after long search query"
        }
    }

    @Test
    fun drawer_shows_chronicle_navigation_item() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Chronicle")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun drawer_shows_models_navigation_item() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Models")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun drawer_shows_projects_navigation_item() {
        openDrawer()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Projects")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun drawer_search_filters_sessions() {
        openDrawer()
        composeTestRule.onNodeWithText("Search sessions...").performTextInput("zzzznoexist")
        composeTestRule.waitForIdle()
        val sessionsHeader = composeTestRule
            .onAllNodes(hasText("Sessions", substring = true))
            .fetchSemanticsNodes()
        val noMatching = composeTestRule
            .onAllNodes(hasText("No matching sessions", substring = true))
            .fetchSemanticsNodes()
        assert(sessionsHeader.isNotEmpty() || noMatching.isNotEmpty()) {
            "Should show sessions header or no matching message"
        }
    }
}
