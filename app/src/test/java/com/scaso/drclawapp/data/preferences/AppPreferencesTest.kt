package com.scaso.drclawapp.data.preferences

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * AppPreferences is otherwise entirely DataStore/Context-bound (see project SKIP RULE), but its
 * setter input validation runs via `require()` before ever touching the DataStore, so it is
 * reachable on a plain JVM with a null Context -- the invalid path throws before the `context!!`
 * dereference, and the valid path only fails afterward (NullPointerException), which is enough to
 * pin that validation itself passed.
 */
class AppPreferencesTest {

    private lateinit var preferences: AppPreferences

    @Before
    fun setup() {
        preferences = AppPreferences(context = null)
    }

    @Test
    fun `setThemeMode rejects invalid mode`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runTest { preferences.setThemeMode("neon") }
        }
        assertEquals("Invalid theme mode: neon. Must be one of: system, dark, light", error.message)
    }

    @Test
    fun `setThemeMode accepts valid modes and proceeds past validation`() {
        for (mode in listOf("system", "dark", "light")) {
            assertThrows(NullPointerException::class.java) {
                runTest { preferences.setThemeMode(mode) }
            }
        }
    }

    @Test
    fun `setEffortLevel rejects invalid level`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runTest { preferences.setEffortLevel("extreme") }
        }
        assertEquals("Invalid effort level: extreme", error.message)
    }

    @Test
    fun `setEffortLevel accepts valid levels and proceeds past validation`() {
        for (level in listOf("low", "medium", "high")) {
            assertThrows(NullPointerException::class.java) {
                runTest { preferences.setEffortLevel(level) }
            }
        }
    }

    @Test
    fun `setThinkingLevel rejects invalid level`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runTest { preferences.setThinkingLevel("extreme") }
        }
        assertEquals("Invalid thinking level: extreme. Must be one of: none, low, medium, high", error.message)
    }

    @Test
    fun `setThinkingLevel accepts valid levels and proceeds past validation`() {
        for (level in listOf("none", "low", "medium", "high")) {
            assertThrows(NullPointerException::class.java) {
                runTest { preferences.setThinkingLevel(level) }
            }
        }
    }
}
