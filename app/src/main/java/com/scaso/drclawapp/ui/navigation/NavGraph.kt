package com.scaso.drclawapp.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.ui.brain.BrainScreen
import com.scaso.drclawapp.ui.ccbridge.AttachedSessionScreen
import com.scaso.drclawapp.ui.ccbridge.CcSessionsScreen
import com.scaso.drclawapp.ui.ccbridge.SplitSessionScreen
import com.scaso.drclawapp.ui.chat.ChatScreen
import com.scaso.drclawapp.ui.approval.ApprovalDialog
import com.scaso.drclawapp.ui.approval.ApprovalViewModel
import com.scaso.drclawapp.ui.chronicle.ChronicleScreen
import com.scaso.drclawapp.ui.media.AttachmentViewModel
import com.scaso.drclawapp.ui.projects.ProjectsScreen
import com.scaso.drclawapp.ui.media.CameraScreen
import com.scaso.drclawapp.ui.settings.PairingScreen
import com.scaso.drclawapp.ui.settings.SettingsScreen
import com.scaso.drclawapp.ui.models.ModelManagerScreen
import com.scaso.drclawapp.ui.security.PermissionRulesScreen
import com.scaso.drclawapp.ui.system.SystemScreen
import com.scaso.drclawapp.ui.tools.ToolsScreen
import com.scaso.drclawapp.ui.voice.VoiceScreen
import com.scaso.drclawapp.ui.voice.VoiceViewModel

object Routes {
    const val CHAT = "chat"
    const val TOOLS = "tools"
    const val BRAIN = "brain"
    const val SYSTEM = "system"
    const val SETTINGS = "settings"
    const val VOICE = "voice"
    const val CAMERA = "camera"
    const val CC_SESSIONS = "cc_sessions"
    const val PROJECTS = "projects"
    const val MODELS = "models"
    const val CHRONICLE = "chronicle"
    const val PAIRING = "pairing"
    const val PERMISSION_RULES = "permission_rules"
    const val ATTACHED_SESSION = "attached_session/{sessionId}?backend={backend}"
    const val SPLIT_SESSION =
        "split_session/{leftId}/{rightId}?leftBackend={leftBackend}&rightBackend={rightBackend}"

    fun attachedSession(sessionId: String, backend: String = "daemon") =
        "attached_session/$sessionId?backend=$backend"

    fun splitSession(
        leftId: String,
        rightId: String,
        leftBackend: String = "ironjaw",
        rightBackend: String = "ironjaw",
    ) = "split_session/$leftId/$rightId?leftBackend=$leftBackend&rightBackend=$rightBackend"

    /** Routes that show the bottom navigation bar */
    val bottomNavRoutes = setOf(CHAT, TOOLS, BRAIN, SYSTEM)
}

@Composable
fun DrClawNavGraph(
    navController: NavHostController = rememberNavController(),
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    initialSharedText: String? = null,
    initialSessionKey: String? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Global HITL approval dialog (D10)
    val approvalViewModel: ApprovalViewModel = hiltViewModel()
    val approvalState by approvalViewModel.uiState.collectAsStateWithLifecycle()

    if (approvalState.showDialog && approvalState.currentRequest != null) {
        ApprovalDialog(
            request = approvalState.currentRequest!!,
            onApprove = approvalViewModel::approve,
            onDeny = approvalViewModel::deny,
            onModify = approvalViewModel::modify,
            onDismiss = approvalViewModel::dismissDialog,
            onApproveAlways = approvalViewModel::approveAlways,
        )
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.bottomNavRoutes) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.CHAT) {
                ChatScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToCamera = { navController.navigate(Routes.CAMERA) },
                    onNavigateToVoice = { navController.navigate(Routes.VOICE) },
                    onNavigateToCcSessions = { navController.navigate(Routes.CC_SESSIONS) },
                    onNavigateToProjects = { navController.navigate(Routes.PROJECTS) },
                    onNavigateToModels = { navController.navigate(Routes.MODELS) },
                    onNavigateToChronicle = { navController.navigate(Routes.CHRONICLE) },
                    initialSharedText = initialSharedText,
                    initialSessionKey = initialSessionKey,
                )
            }
            composable(Routes.TOOLS) {
                ToolsScreen()
            }
            composable(Routes.BRAIN) {
                BrainScreen()
            }
            composable(Routes.SYSTEM) {
                SystemScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    darkTheme = darkTheme,
                    onThemeChanged = onThemeChanged,
                    onNavigateToPairing = { navController.navigate(Routes.PAIRING) },
                    onNavigateToPermissionRules = { navController.navigate(Routes.PERMISSION_RULES) },
                )
            }
            composable(Routes.PAIRING) {
                PairingScreen(
                    onPairSuccess = { /* token stored by caller */ },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.VOICE) {
                val voiceViewModel: VoiceViewModel = hiltViewModel()
                val fullDuplexAvailable by voiceViewModel.fullDuplexAvailable.collectAsStateWithLifecycle()
                VoiceScreen(
                    voiceManager = voiceViewModel.voiceManager,
                    onNavigateBack = { navController.popBackStack() },
                    fullDuplexAvailable = fullDuplexAvailable,
                    onToggleMode = { voiceViewModel.toggleMode() },
                )
            }
            composable(Routes.CC_SESSIONS) {
                CcSessionsScreen(
                    onBack = { navController.popBackStack() },
                    onAttachSession = { sessionId, backend ->
                        navController.navigate(Routes.attachedSession(sessionId, backend))
                    },
                    onSplitSession = { leftId, leftBackend, rightId, rightBackend ->
                        navController.navigate(
                            Routes.splitSession(leftId, rightId, leftBackend, rightBackend),
                        )
                    },
                )
            }
            composable(
                route = Routes.ATTACHED_SESSION,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("backend") {
                        type = NavType.StringType
                        defaultValue = "daemon"
                    },
                ),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                AttachedSessionScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SPLIT_SESSION,
                arguments = listOf(
                    navArgument("leftId") { type = NavType.StringType },
                    navArgument("rightId") { type = NavType.StringType },
                    navArgument("leftBackend") {
                        type = NavType.StringType
                        defaultValue = "ironjaw"
                    },
                    navArgument("rightBackend") {
                        type = NavType.StringType
                        defaultValue = "ironjaw"
                    },
                ),
            ) { backStackEntry ->
                val leftId = backStackEntry.arguments?.getString("leftId") ?: ""
                val rightId = backStackEntry.arguments?.getString("rightId") ?: ""
                val leftBackend = backStackEntry.arguments?.getString("leftBackend") ?: "ironjaw"
                val rightBackend = backStackEntry.arguments?.getString("rightBackend") ?: "ironjaw"
                SplitSessionScreen(
                    leftSessionId = leftId,
                    leftBackend = leftBackend,
                    rightSessionId = rightId,
                    rightBackend = rightBackend,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MODELS) {
                ModelManagerScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CHRONICLE) {
                ChronicleScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PERMISSION_RULES) {
                PermissionRulesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CAMERA) {
                // Share the ChatScreen's AttachmentViewModel so photos persist after popBackStack
                val chatEntry = remember(navController) {
                    navController.getBackStackEntry(Routes.CHAT)
                }
                val attachmentVm: AttachmentViewModel = hiltViewModel(chatEntry)
                val context = LocalContext.current
                var cameraPermissionGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    cameraPermissionGranted = granted
                    if (!granted) navController.popBackStack()
                }

                LaunchedEffect(Unit) {
                    if (!cameraPermissionGranted) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (cameraPermissionGranted) {
                    CameraScreen(
                        onPhotoCaptured = { uri ->
                            attachmentVm.addAttachment(uri, "image/jpeg", displayName = "Photo")
                            navController.popBackStack()
                        },
                        onDismiss = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
