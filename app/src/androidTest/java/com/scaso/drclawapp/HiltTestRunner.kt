package com.scaso.drclawapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication
/**
 * Custom JUnit runner that uses HiltTestApplication instead of the real DrClawApp.
 * This avoids initializing production Hilt modules that require real gateway connections.
 *
 * Referenced in build.gradle.kts: testInstrumentationRunner
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
