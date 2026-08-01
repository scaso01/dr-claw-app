package com.scaso.drclawapp.fake

import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.MeshnetIpHolder
import com.scaso.drclawapp.di.GatewayModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt test module that replaces [GatewayModule] in all @HiltAndroidTest classes.
 *
 * Provides a [FakeGatewayClient] pre-set to [ConnectionState.Connected] so that
 * UI tests can render screens without a real Ironjaw Gateway connection. Also
 * re-provides [MeshnetIpHolder] and [OkHttpClient] -- GatewayModule normally supplies
 * both, and @TestInstallIn(replaces = ...) drops the whole module, not just
 * provideGatewayClient, so anything else injecting them directly (SettingsViewModel,
 * AppModule's @LocalBridge CcBridgeClient) needs a stand-in binding here too.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [GatewayModule::class],
)
object FakeGatewayModule {

    @Provides
    @Singleton
    fun provideGatewayClient(scope: CoroutineScope): GatewayClient {
        return FakeGatewayClient(scope)
    }

    @Provides
    @Singleton
    fun provideMeshnetIpHolder(): MeshnetIpHolder {
        return MeshnetIpHolder("")
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }
}
