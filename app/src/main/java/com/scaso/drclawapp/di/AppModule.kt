package com.scaso.drclawapp.di

import android.content.Context
import androidx.room.Room
import com.scaso.drclawapp.BuildConfig
import com.scaso.drclawapp.data.ccbridge.CcBridgeRepository
import com.scaso.drclawapp.data.filedownload.FileDownloadRepository
import com.scaso.drclawapp.data.infra.InfraRepository
import com.scaso.drclawapp.data.roles.ModelRepository
import com.scaso.drclawapp.data.roles.RoleRepository
import com.scaso.drclawapp.data.local.DrClawDatabase
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.dao.SessionDao
import com.scaso.drclawapp.data.local.dao.ToolEventDao
import com.scaso.drclawapp.data.ntfy.NtfyClient
import com.scaso.drclawapp.data.projects.ProjectRepository
import com.scaso.drclawapp.data.security.SecurityRepository
import com.scaso.drclawapp.data.tools.ToolRepository
import com.scaso.drclawapp.data.brain.BrainRepository
import com.scaso.drclawapp.data.schedule.ScheduleRepository
import com.scaso.drclawapp.data.device.DeviceRepository
import com.scaso.drclawapp.data.export.ExportRepository
import com.scaso.drclawapp.data.plugins.PluginRepository
import com.scaso.drclawapp.data.vault.VaultRepository
import com.scaso.drclawapp.data.chronicle.ChronicleRepository
import com.scaso.drclawapp.data.experiments.ExperimentRepository
import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.approval.CmdApprovalGate
import com.scaso.drclawapp.data.tts.AudioTrackPlayer
import com.scaso.drclawapp.data.tts.PiperTtsEngine
import com.scaso.drclawapp.data.tts.TtsManager
import com.scaso.drclawapp.cmd.CmdApprovalGateImpl
import com.scaso.drclawapp.cmd.CmdDispatcher
import com.scaso.drclawapp.service.NetworkMonitor
import com.scaso.drclawapp.service.NotificationHelper
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.preferences.SecureTokenStore
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.repository.GatewayRepository
import com.scaso.drclawapp.data.repository.SessionRepository
import com.scaso.drclawapp.data.websocket.CcBridgeClient
import com.scaso.drclawapp.data.websocket.GatewayClient
import okhttp3.OkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** The cc-bridge daemon on workstation (configured via CC_BRIDGE_URL; may be blank/off). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalBridge

/** The phone-local cc-bridge daemon (Termux/proot, ws://localhost:18790). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PhoneBridge

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("DrClaw", "Unhandled coroutine exception", throwable)
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }

    @Provides
    @Singleton
    @LocalBridge
    fun provideLocalBridge(
        scope: CoroutineScope,
        secureTokenStore: SecureTokenStore,
        okHttpClient: OkHttpClient,
    ): CcBridgeClient {
        // Use dedicated CC_BRIDGE_URL if configured, otherwise disable by using empty URL
        val ccBridgeUrl = BuildConfig.CC_BRIDGE_URL.ifBlank { "" }
        val token = secureTokenStore.getGatewayToken(BuildConfig.GATEWAY_TOKEN)
        // Same DuckDNS host as the gateway -> share the Meshnet-first / pinned transport client.
        return CcBridgeClient(url = ccBridgeUrl, token = token, scope = scope, okHttpClient = okHttpClient)
    }

    @Provides
    @Singleton
    @PhoneBridge
    fun providePhoneBridge(scope: CoroutineScope): CcBridgeClient {
        // Phone-local daemon (Termux/proot). Blank URL when not built for the phone → disabled.
        // Uses its OWN token (the daemon's CC_BRIDGE_TOKEN), not the gateway token.
        val url = BuildConfig.PHONE_CC_BRIDGE_URL.ifBlank { "" }
        return CcBridgeClient(url = url, token = BuildConfig.PHONE_CC_BRIDGE_TOKEN, scope = scope)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DrClawDatabase {
        val passphrase = com.scaso.drclawapp.data.local.DatabaseEncryptionHelper.getOrCreatePassphrase(context)
        val factory = net.sqlcipher.database.SupportFactory(passphrase)
        return Room.databaseBuilder(context, DrClawDatabase::class.java, "drclaw.db")
            .openHelperFactory(factory)
            // 1→2: no-op (identical schema). 2→3: contextPct/contextWindow on sessions.
            // 3→4: syncState on messages. Phase 17: 4→5 adds tool_events table.
            // A13: 5→6 adds mode column to sessions. A6-client: 6→7 adds injectedMemoryIds.
            .addMigrations(
                DrClawDatabase.MIGRATION_1_2,
                DrClawDatabase.MIGRATION_2_3,
                DrClawDatabase.MIGRATION_3_4,
                DrClawDatabase.MIGRATION_4_5,
                DrClawDatabase.MIGRATION_5_6,
                DrClawDatabase.MIGRATION_6_7,
            )
            // No fallbackToDestructiveMigration: local-only state (pins, archives, forks,
            // offline-pending) must never be silently wiped. A missing migration now fails
            // loudly instead.
            .build()
    }

    @Provides
    fun provideSessionDao(database: DrClawDatabase): SessionDao =
        database.sessionDao()

    @Provides
    fun provideMessageDao(database: DrClawDatabase): MessageDao =
        database.messageDao()

    @Provides
    fun provideToolEventDao(database: DrClawDatabase): ToolEventDao =
        database.toolEventDao()

    @Provides
    @Singleton
    fun provideChatRepository(
        gatewayClient: GatewayClient,
        messageDao: MessageDao,
        toolEventDao: ToolEventDao,
        scope: CoroutineScope,
    ): ChatRepository = ChatRepository(gatewayClient, scope, messageDao, toolEventDao)

    @Provides
    @Singleton
    fun provideGatewayRepository(
        gatewayClient: GatewayClient,
    ): GatewayRepository = GatewayRepository(gatewayClient)

    @Provides
    @Singleton
    fun provideSessionRepository(
        gatewayClient: GatewayClient,
        chatRepository: ChatRepository,
        sessionDao: SessionDao,
        messageDao: MessageDao,
        scope: CoroutineScope,
    ): SessionRepository = SessionRepository(gatewayClient, chatRepository, scope, sessionDao, messageDao)

    @Provides
    @Singleton
    fun provideCcBridgeRepository(
        gatewayClient: GatewayClient,
        @LocalBridge localBridge: CcBridgeClient,
        @PhoneBridge phoneBridge: CcBridgeClient,
        scope: CoroutineScope,
    ): CcBridgeRepository = CcBridgeRepository(gatewayClient, localBridge, phoneBridge, scope)

    @Provides
    @Singleton
    fun provideInfraRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): InfraRepository = InfraRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideProjectRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ProjectRepository = ProjectRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideModelRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ModelRepository = ModelRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideRoleRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): RoleRepository = RoleRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideFileDownloadRepository(
        gatewayClient: GatewayClient,
    ): FileDownloadRepository = FileDownloadRepository(gatewayClient)

    @Provides
    @Singleton
    fun provideNtfyClient(scope: CoroutineScope): NtfyClient =
        NtfyClient(scope)

    @Provides
    @Singleton
    fun provideToolRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ToolRepository = ToolRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideSecurityRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): SecurityRepository = SecurityRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideBrainRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): BrainRepository = BrainRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideScheduleRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ScheduleRepository = ScheduleRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideDeviceRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): DeviceRepository = DeviceRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideVaultRepository(
        gatewayClient: GatewayClient,
    ): VaultRepository = VaultRepository(gatewayClient)

    @Provides
    @Singleton
    fun provideExportRepository(
        gatewayClient: GatewayClient,
    ): ExportRepository = ExportRepository(gatewayClient)

    @Provides
    @Singleton
    fun providePluginRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): PluginRepository = PluginRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideExperimentRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ExperimentRepository = ExperimentRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideApprovalRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ApprovalRepository = ApprovalRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideChronicleRepository(
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): ChronicleRepository = ChronicleRepository(gatewayClient, scope)

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
        gatewayClient: GatewayClient,
        appPreferences: AppPreferences,
    ): TtsManager = TtsManager(gatewayClient, appPreferences, PiperTtsEngine(context), AudioTrackPlayer())

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)

    @Provides
    @Singleton
    fun provideSecureTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(context)

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context,
        gatewayClient: GatewayClient,
        @LocalBridge ccBridgeClient: CcBridgeClient,
        ntfyClient: NtfyClient,
    ): NetworkMonitor = NetworkMonitor(context, gatewayClient, ccBridgeClient, ntfyClient)

    @Provides
    @Singleton
    fun provideCmdDispatcher(
        @ApplicationContext context: Context,
        gatewayClient: GatewayClient,
    ): CmdDispatcher {
        val dispatcher = CmdDispatcher(context)
        gatewayClient.cmdDispatcher = dispatcher
        return dispatcher
    }

    @Provides
    @Singleton
    fun provideCmdApprovalGate(
        approvalRepository: ApprovalRepository,
        appPreferences: AppPreferences,
        notificationHelper: NotificationHelper,
        gatewayClient: GatewayClient,
        scope: CoroutineScope,
    ): CmdApprovalGate {
        val gate = CmdApprovalGateImpl(
            approvalRepository = approvalRepository,
            appPreferences = appPreferences,
            scope = scope,
            isAppInForeground = { com.scaso.drclawapp.DrClawApp.isAppInForeground },
            notifyBackgrounded = { tool -> notificationHelper.showCmdApprovalNotification(tool) },
        )
        gatewayClient.cmdApprovalGate = gate
        return gate
    }
}
