import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) load(secretsFile.inputStream())
}

// Auto-increment versionCode from file (persists across builds)
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) load(versionFile.inputStream())
}
val autoVersionCode = (versionProps.getProperty("versionCode", "18").toInt()).also { code ->
    versionProps.setProperty("versionCode", (code + 1).toString())
    versionFile.writer().use { versionProps.store(it, "Auto-incremented on each build") }
}

android {
    namespace = "com.scaso.drclawapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scaso.drclawapp"
        minSdk = 24
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = "1.3.0"

        buildConfigField("String", "GATEWAY_URL", "\"${secrets.getProperty("GATEWAY_URL", "")}\"")
        buildConfigField("String", "GATEWAY_TOKEN", "\"${secrets.getProperty("GATEWAY_TOKEN", "")}\"")
        // Phase 3: pin the gateway hostname to docker-host's Meshnet IP so the connection
        // does not depend on phone-side DNS (NordVPN silently drops it on Meshnet).
        buildConfigField("String", "MESHNET_IP", "\"${secrets.getProperty("MESHNET_IP", "198.51.100.41")}\"")
        buildConfigField("String", "CC_BRIDGE_URL", "\"${secrets.getProperty("CC_BRIDGE_URL", "")}\"")
        buildConfigField("String", "PHONE_CC_BRIDGE_URL", "\"${secrets.getProperty("PHONE_CC_BRIDGE_URL", "")}\"")
        buildConfigField("String", "PHONE_CC_BRIDGE_TOKEN", "\"${secrets.getProperty("PHONE_CC_BRIDGE_TOKEN", "")}\"")
        // Phase 4: shared secret gating the exported UiActionReceiver (drive-other-apps bridge).
        // Must match the secret baked into the phone-side ui-* wrappers. Blank = receiver rejects all.
        buildConfigField("String", "UI_BRIDGE_SECRET", "\"${secrets.getProperty("UI_BRIDGE_SECRET", "")}\"")

        testInstrumentationRunner = "com.scaso.drclawapp.HiltTestRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.windowsizeclass)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    // Force kotlin-metadata-jvm to match Kotlin 2.3.0 (Dagger 2.57+ unshaded it)
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")

    // Network
    implementation(libs.okhttp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DateTime (KMP)
    implementation(libs.kotlinx.datetime)

    // Markdown rendering (Phase 2)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)

    // Navigation (Phase 3)
    implementation(libs.navigation.compose)

    // DataStore (Phase 3)
    implementation(libs.datastore.preferences)

    // Room (Phase 12)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SQLCipher (Room encryption — S3)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // WorkManager (Phase 15 — Offline-First)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Camera (Phase 7)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)

    // TTS (on-device Piper via sherpa-onnx)
    // AAR from https://github.com/k2-fsa/sherpa-onnx/releases (not on Maven Central)
    implementation(files("libs/sherpa-onnx-1.12.31.aar"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    // Instrumented / E2E Testing
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
