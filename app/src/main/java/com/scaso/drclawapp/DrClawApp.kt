package com.scaso.drclawapp

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.scaso.drclawapp.cmd.CmdDispatcher
import com.scaso.drclawapp.data.approval.CmdApprovalGate
import com.scaso.drclawapp.data.preferences.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class DrClawApp : Application(), Configuration.Provider {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var cmdDispatcher: CmdDispatcher
    // Eagerly resolved so GatewayClient.cmdApprovalGate is wired before any cmd frame can arrive.
    @Inject lateinit var cmdApprovalGate: CmdApprovalGate
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        /**
         * Simple foreground flag toggled by MainActivity lifecycle.
         * Used by ChatService to decide whether to show push notifications
         * for CC session completion events.
         */
        @Volatile
        var isAppInForeground: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("trace", sw.toString())
                }
                startActivity(intent)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(t, e)
        }

        appScope.launch {
            appPreferences.ensureNtfyTopicInitialized()
        }
    }
}
