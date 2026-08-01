package com.scaso.drclawapp.di

import com.scaso.drclawapp.BuildConfig
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.preferences.SecureTokenStore
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.MeshnetFirstDns
import com.scaso.drclawapp.data.websocket.MeshnetIpHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {

    /** Hostname all workstation-bound transport pins to (from GATEWAY_URL, e.g. gateway.example.com). */
    private val pinnedHost: String =
        BuildConfig.GATEWAY_URL.substringAfter("://").substringBefore("/").substringBefore(":")

    @Provides
    @Singleton
    fun provideMeshnetIpHolder(
        scope: CoroutineScope,
        appPreferences: AppPreferences,
    ): MeshnetIpHolder {
        val holder = MeshnetIpHolder(BuildConfig.MESHNET_IP)
        // Apply a saved override (Settings escape hatch) once it loads, like the URL below.
        scope.launch {
            val saved = appPreferences.meshnetIp.first()
            if (saved.isNotBlank()) holder.ip = saved
        }
        return holder
    }

    /**
     * One OkHttp client shared by every workstation-bound WebSocket (gateway + @LocalBridge):
     * Meshnet-IP-first DNS, a 20s keepalive ping, and the gateway cert pin. The phone-local
     * bridge (@PhoneBridge, localhost) keeps its own default client.
     */
    @Provides
    @Singleton
    fun provideTransportOkHttpClient(holder: MeshnetIpHolder): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // ponytail: 20s (was 30) catches a dead tunnel ~10s sooner; minor extra keepalive traffic.
            .pingInterval(20, TimeUnit.SECONDS)
            .dns(MeshnetFirstDns(pinnedHost, ipSupplier = { holder.ip }))
        if (pinnedHost.isNotBlank()) {
            // Pin the trust ANCHOR, not the leaf. OkHttp matches if ANY pin is present in the
            // cleaned chain, so all three ISRG roots are listed: Let's Encrypt migrated to the
            // Root YE hierarchy in 2026 and the chain now anchors at X2, not X1. Pinning X1
            // alone broke every connection on 2026-07-30 (SSLPeerUnverifiedException) — keep
            // all three so a further LE rotation cannot brick the app again.
            builder.certificatePinner(
                CertificatePinner.Builder()
                    .add(pinnedHost, "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
                    .add(pinnedHost, "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=") // ISRG Root X2
                    .add(pinnedHost, "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=") // ISRG Root YE
                    .build()
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGatewayClient(
        scope: CoroutineScope,
        appPreferences: AppPreferences,
        secureTokenStore: SecureTokenStore,
        okHttpClient: OkHttpClient,
    ): GatewayClient {
        // Start with BuildConfig URL; update asynchronously from saved preferences
        val token = secureTokenStore.getGatewayToken(BuildConfig.GATEWAY_TOKEN)
        val client = GatewayClient(
            url = BuildConfig.GATEWAY_URL,
            token = token,
            scope = scope,
            appVersion = BuildConfig.VERSION_NAME,
            okHttpClient = okHttpClient,
        )
        scope.launch {
            val savedUrl = appPreferences.gatewayUrl.first()
            if (savedUrl.isNotEmpty()) client.reconnectWith(savedUrl)
        }
        return client
    }
}
