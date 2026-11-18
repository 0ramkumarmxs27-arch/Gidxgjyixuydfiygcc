package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.system.AndroidDeviceController
import com.example.ui.MyraViewModel
import com.example.ui.components.BiometricAppLockGate
import com.example.ui.components.FloatingMyraOrb
import com.example.ui.screens.CodeStudioScreen
import com.example.ui.screens.HomeVoiceScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.screens.SettingsSecurityScreen
import com.example.ui.screens.TasksMemoryScreen
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MyraAiTheme
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

object MyraRoutes {
    const val VOICE = "voice_hub"
    const val PHONE = "phone_control"
    const val CODE = "code_studio"
    const val TASKS = "tasks_memory"
    const val SETTINGS = "settings_security"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : FragmentActivity() {

    private var shortcutLaunchVoice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        if (intent?.action == AndroidDeviceController.ACTION_LAUNCH_VOICE_MODE) {
            shortcutLaunchVoice = true
        }
        setContent {
            MyraAiTheme {
                MyraRootApp(
                    launchVoiceImmediately = shortcutLaunchVoice,
                    onConsumedShortcutLaunch = { shortcutLaunchVoice = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == AndroidDeviceController.ACTION_LAUNCH_VOICE_MODE) {
            shortcutLaunchVoice = true
        }
    }
}

@Composable
fun MyraRootApp(
    launchVoiceImmediately: Boolean,
    onConsumedShortcutLaunch: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: MyraViewModel = viewModel(factory = MyraViewModel.factory(context))
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MyraRoutes.VOICE

    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSessionUnlocked by viewModel.isSessionUnlocked.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val requiresAppLaunchBiometric = settings.biometricLockEnabled && settings.biometricRequireOnAppLaunch

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted) viewModel.goLiveForever()
        }
    )

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotifPermission = granted }
    )

    LaunchedEffect(settings.alwaysLiveEnabled, hasMicPermission) {
        if (settings.alwaysLiveEnabled && hasMicPermission && !serviceRunning) {
            viewModel.goLiveForever()
        }
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
    }

    LaunchedEffect(launchVoiceImmediately) {
        if (launchVoiceImmediately) {
            onConsumedShortcutLaunch()
            navController.navigate(MyraRoutes.VOICE) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            if (hasMicPermission) {
                viewModel.startActiveVoiceSession()
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(MyraRoutes.VOICE, "MYRA", Icons.Filled.Mic, Icons.Outlined.Mic),
            NavDestination(MyraRoutes.PHONE, "Phone", Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid),
            NavDestination(MyraRoutes.CODE, "Code", Icons.Filled.Code, Icons.Outlined.Code),
            NavDestination(MyraRoutes.TASKS, "Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
            NavDestination(MyraRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        // Futuristic Deep Navy & Purple Backdrop
        Image(
            painter = painterResource(id = R.drawable.img_myra_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.34f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DeepNavyBg.copy(alpha = 0.85f),
                            RoyalPurpleDeep.copy(alpha = 0.45f),
                            DeepNavyBg.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        if (requiresAppLaunchBiometric && !isSessionUnlocked) {
            BiometricAppLockGate(
                hasPinConfigured = settings.pinHash.isNotBlank(),
                onVerifyPin = { pin -> viewModel.verifyPin(pin) },
                onUnlocked = { viewModel.markSessionUnlocked() },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding()
            )
            return@BoxWithConstraints
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MidnightSurface.copy(alpha = 0.95f),
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LuminousCyan,
                                    selectedTextColor = LuminousCyan,
                                    indicatorColor = RoyalPurpleDeep,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute != MyraRoutes.VOICE) {
                    FloatingMyraOrb(
                        assistantState = assistantState,
                        onClick = {
                            navigateTo(MyraRoutes.VOICE)
                            if (hasMicPermission) viewModel.startActiveVoiceSession()
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MidnightSurface.copy(alpha = 0.92f)
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = LuminousCyan,
                                    selectedTextColor = LuminousCyan,
                                    indicatorColor = RoyalPurpleDeep,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = MyraRoutes.VOICE,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(MyraRoutes.VOICE) {
                            HomeVoiceScreen(
                                viewModel = viewModel,
                                hasMicPermission = hasMicPermission,
                                onRequestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onOpenSettings = { navigateTo(MyraRoutes.SETTINGS) },
                                onOpenCodeStudio = { navigateTo(MyraRoutes.CODE) }
                            )
                        }
                        composable(MyraRoutes.PHONE) {
                            PhoneControlScreen(viewModel = viewModel)
                        }
                        composable(MyraRoutes.CODE) {
                            CodeStudioScreen(viewModel = viewModel)
                        }
                        composable(MyraRoutes.TASKS) {
                            TasksMemoryScreen(viewModel = viewModel)
                        }
                        composable(MyraRoutes.SETTINGS) {
                            SettingsSecurityScreen(
                                viewModel = viewModel,
                                hasMicPermission = hasMicPermission,
                                hasNotifPermission = hasNotifPermission,
                                onRequestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onRequestNotifPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
