package com.scaso.drclawapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.scaso.drclawapp.data.preferences.AppPreferences
import androidx.lifecycle.lifecycleScope
import com.scaso.drclawapp.service.BubbleService
import com.scaso.drclawapp.service.ChatService
import com.scaso.drclawapp.service.NotificationHelper
import kotlinx.coroutines.launch
import com.scaso.drclawapp.ui.navigation.DrClawNavGraph
import com.scaso.drclawapp.ui.theme.DrClawTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Start service regardless — it works without notifications on older Android
        ChatService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission (Android 13+), then start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ChatService.start(this)
        }

        // Start or stop BubbleService based on user preference
        lifecycleScope.launch {
            appPreferences.bubbleEnabled.collect { enabled ->
                if (enabled) {
                    BubbleService.start(this@MainActivity)
                } else {
                    BubbleService.stop(this@MainActivity)
                }
            }
        }

        // Extract shared text from ACTION_SEND intent
        val sharedText = extractSharedText(intent)

        // Extract session key from ntfy notification deep-link
        val sessionKey = extractSessionKey(intent)

        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = "system")
            val systemDark = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            DrClawTheme(darkTheme = darkTheme) {
                DrClawNavGraph(
                    darkTheme = darkTheme,
                    onThemeChanged = { /* Theme changes are persisted via SettingsViewModel */ },
                    initialSharedText = sharedText,
                    initialSessionKey = sessionKey,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DrClawApp.isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        DrClawApp.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }

    /**
     * Extracts a session key from a notification tap or drclaw:// deep-link.
     * Validates UUID format to prevent injection via crafted deep links.
     */
    private fun extractSessionKey(intent: Intent?): String? {
        intent ?: return null
        // From NotificationHelper extra (ntfy notification tap)
        intent.getStringExtra(NotificationHelper.EXTRA_SESSION_KEY)
            ?.takeIf { SESSION_KEY_PATTERN.matches(it) }
            ?.let { return it }
        // From drclaw://session/<sessionKey> deep-link URI
        val data = intent.data
        if (data?.scheme == "drclaw" && data.host == "session") {
            return data.pathSegments?.firstOrNull()
                ?.takeIf { it.isNotBlank() && SESSION_KEY_PATTERN.matches(it) }
        }
        return null
    }

    companion object {
        private val SESSION_KEY_PATTERN = Regex("[a-f0-9-]{36}")
    }
}
