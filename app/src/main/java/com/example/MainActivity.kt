package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiOverlayService
import com.example.ai.VoiceAssistantController
import com.example.data.CloudRepository
import com.example.ui.AccountBindingState
import com.example.ui.ActiveCallScreen
import com.example.ui.AiChatScreen
import com.example.ui.AuthScreen
import com.example.ui.BlockedAccountScreen
import com.example.ui.ChangeGeminiApiKeyDialog
import com.example.ui.ChatTabScreen
import com.example.ui.CloudViewModel
import com.example.ui.ConnectByCodeDialog
import com.example.ui.DeveloperCreditDialog
import com.example.ui.DeveloperIntroStepScreen
import com.example.ui.DeviceCheckingStepScreen
import com.example.ui.DeviceLockedToAccountScreen
import com.example.ui.GeminiApiKeyStepScreen
import com.example.ui.HowToUseDialog
import com.example.ui.IncomingCallDialog
import com.example.ui.LocalAppStrings
import com.example.ui.MaintenanceNoticeScreen
import com.example.ui.PermissionsAllowStepScreen
import com.example.ui.RemoteDevicesTab
import com.example.ui.ServerVaultLockDialog
import com.example.ui.TelegramSupportDialog
import com.example.ui.appStringsFor
import com.example.ui.authStateFlow
import com.example.ui.signOut
import com.example.ui.theme.VitRemoteTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }
    private val dpm by lazy { getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val ui = Handler(Looper.getMainLooper())
    private val voiceController by lazy { VoiceAssistantController.get(this) }

    private var uiState by mutableStateOf(
        RemoteUiState(
            userEmail = "",
            deviceId = "",
            deviceCode = "",
            deviceAlias = "",
            statusText = "Inactive",
            statusOk = false,
            cloudEnabled = true,
            cloudOnline = false,
            viewerActive = false,
            accessibilityEnabled = false,
            screenCaptureActive = false,
            showScreenLock = Build.VERSION.SDK_INT < 28,
            screenLockActive = false,
            batteryUnrestricted = true,
            writeSettingsGranted = false,
            showNotificationsPermission = Build.VERSION.SDK_INT >= 33,
            notificationsGranted = true,
            notificationListenerGranted = false,
            runtimeMediaAndNotifGranted = false,
            deviceRows = emptyList(),
        ),
    )

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refresh()
    }

    private val multiPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refresh()
    }

    private val contactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refresh()
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        voiceController.syncMasterAiEnabled(prefs.aiSystemEnabled)
        refresh()

        setContent {
            var isDarkTheme by remember { mutableStateOf(prefs.isDarkTheme) }
            var appLanguage by remember { mutableStateOf(prefs.appLanguage) }
            val strings = remember(appLanguage) { appStringsFor(appLanguage) }

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
            }

            CompositionLocalProvider(LocalAppStrings provides strings) {
                VitRemoteTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                    val voiceState by voiceController.state.collectAsStateWithLifecycle()
                    val localizedStatus = when {
                        !uiState.accessibilityEnabled -> strings.statusInactive
                        !uiState.cloudEnabled -> strings.statusCloudOff
                        uiState.viewerActive -> strings.statusLiveSession
                        uiState.cloudOnline -> strings.statusReady
                        else -> strings.statusStarting
                    }
                    val enrichedHostState = uiState.copy(
                        statusText = localizedStatus,
                        voiceState = voiceState,
                    )
                    AppContent(
                        hostState = enrichedHostState,
                        prefs = prefs,
                        voiceController = voiceController,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = {
                            val next = !isDarkTheme
                            prefs.isDarkTheme = next
                            isDarkTheme = next
                        },
                        currentLanguage = appLanguage,
                        onSelectLanguage = { lang ->
                            prefs.appLanguage = lang
                            appLanguage = lang
                        },
                        onRefreshState = { refresh() },
                    onAuthChanged = { restart() },
                    onBindingVerified = { code ->
                        prefs.deviceCode = code
                        restart()
                    },
                    onCopyCode = {
                        val formatted = Prefs.formatCode(prefs.deviceCode)
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("device_code", prefs.deviceCode))
                        Toast.makeText(this, "কোড কপি হয়েছে: $formatted", Toast.LENGTH_SHORT).show()
                    },
                    onShareCode = {
                        val formatted = Prefs.formatCode(prefs.deviceCode)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "আমার vitRemote ডিভাইস কোড: $formatted (${prefs.deviceCode})",
                            )
                        }
                        startActivity(Intent.createChooser(shareIntent, "কোড শেয়ার করুন"))
                    },
                    onDisconnectViewer = {
                        RemoteService.instance?.disconnectViewer()
                        refresh()
                    },
                    onToggleCloud = { enabled ->
                        prefs.cloudEnabled = enabled
                        restart()
                    },
                    onEnableAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleScreenCapture = { enabled ->
                        if (enabled) {
                            startActivity(Intent(this, ProjectionActivity::class.java))
                        } else {
                            CaptureService.stop(this)
                            refresh()
                        }
                    },
                    onAllowScreenLock = {
                        startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                .putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Lets remote power control turn the screen off.",
                                ),
                        )
                    },
                    onAllowBattery = { requestBattery() },
                    onAllowWriteSettings = { requestWriteSettings() },
                    onAllowNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onAllowNotificationListener = { openNotificationListenerSettings() },
                    onAllowOverlay = { openOverlaySettings() },
                    onAllowContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onRequestAllRuntimePermissions = { requestAllRuntimePermissions() },
                    onToggleAiSystem = { enabled ->
                        prefs.aiSystemEnabled = enabled
                        voiceController.syncMasterAiEnabled(enabled)
                        if (enabled && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            requestAllRuntimePermissions()
                        }
                        refresh()
                    },
                    onToggleVirtualScreenAi = { enabled ->
                        prefs.virtualScreenAiEnabled = enabled
                        if (enabled) {
                            prefs.ownDeviceAiEnabled = false
                            if (!prefs.aiSystemEnabled) {
                                prefs.aiSystemEnabled = true
                                voiceController.syncMasterAiEnabled(true)
                            }
                        }
                        refresh()
                    },
                    onToggleRemoteScreenAi = { enabled ->
                        prefs.remoteScreenAiEnabled = enabled
                        if (enabled) {
                            prefs.ownDeviceAiEnabled = false
                            if (!prefs.aiSystemEnabled) {
                                prefs.aiSystemEnabled = true
                                voiceController.syncMasterAiEnabled(true)
                            }
                        }
                        refresh()
                    },
                    onToggleOwnDeviceAi = { enabled ->
                        prefs.ownDeviceAiEnabled = enabled
                        if (enabled) {
                            // Button 4 exclusivity: turn off other screen AI mode buttons
                            prefs.virtualScreenAiEnabled = false
                            prefs.remoteScreenAiEnabled = false
                            if (!prefs.aiSystemEnabled) {
                                prefs.aiSystemEnabled = true
                                voiceController.syncMasterAiEnabled(true)
                            }
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                requestAllRuntimePermissions()
                            } else {
                                voiceController.runOwnDeviceVoiceAssistant()
                            }
                        } else {
                            voiceController.stopListening()
                        }
                        refresh()
                    },
                    onTriggerOwnDeviceVoice = {
                        if (!prefs.aiSystemEnabled) {
                            Toast.makeText(this, "প্রথমে বাটন ১ (AI সিস্টেম চালু করুন) অন করুন", Toast.LENGTH_SHORT).show()
                        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            requestAllRuntimePermissions()
                        } else {
                            voiceController.runOwnDeviceVoiceAssistant()
                        }
                    },
                    onToggleOverlay = { enabled ->
                        if (enabled) {
                            if (!overlayOk()) {
                                Toast.makeText(this, "অনুগ্রহ করে Display Over Apps এলাও করুন", Toast.LENGTH_SHORT).show()
                                openOverlaySettings()
                            } else {
                                prefs.overlayEnabled = true
                                AiOverlayService.start(this)
                            }
                        } else {
                            prefs.overlayEnabled = false
                            AiOverlayService.stop(this)
                        }
                        refresh()
                    },
                )
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        voiceController.syncMasterAiEnabled(prefs.aiSystemEnabled)
        if (prefs.overlayEnabled && overlayOk() && !AiOverlayService.isRunning) {
            AiOverlayService.start(this)
        } else if (!overlayOk() && prefs.overlayEnabled) {
            prefs.overlayEnabled = false
        }
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun restart() {
        RemoteService.instance?.applyConfig()
        refresh()
    }

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, RemoteService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun batteryOk(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun writeSettingsOk(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this)

    private fun notificationsOk(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun overlayOk(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun contactsOk(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    private fun runtimeMediaAndNotifOk(): Boolean =
        requiredRuntimePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestAllRuntimePermissions() {
        multiPermLauncher.launch(requiredRuntimePermissions())
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun notificationListenerOk(): Boolean =
        RemoteNotificationListenerService.isEnabled(this)

    private fun openNotificationListenerSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val cn = ComponentName(this, RemoteNotificationListenerService::class.java)
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
                    },
                )
                return
            }
        } catch (_: Exception) {
        }
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestWriteSettings() {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBattery() {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refresh() {
        val svc = RemoteService.instance
        val a11y = a11yEnabled()
        val cloudOn = svc?.cloudOnline == true
        val viewers = svc?.cloudViewers ?: 0
        val (status, ok) = when {
            !a11y -> getString(R.string.status_inactive) to false
            !prefs.cloudEnabled -> getString(R.string.status_cloud_off) to false
            viewers > 0 -> "লাইভ সেশন" to true
            cloudOn -> getString(R.string.status_ready) to true
            else -> getString(R.string.status_starting) to false
        }

        uiState = RemoteUiState(
            userEmail = Firebase.auth.currentUser?.email ?: "",
            deviceId = prefs.deviceId,
            deviceCode = prefs.deviceCode,
            deviceAlias = prefs.deviceAlias,
            statusText = status,
            statusOk = ok,
            cloudEnabled = prefs.cloudEnabled,
            cloudOnline = cloudOn,
            viewerActive = viewers > 0,
            accessibilityEnabled = a11y,
            screenCaptureActive = CaptureService.instance != null,
            showScreenLock = Build.VERSION.SDK_INT < 28,
            screenLockActive = dpm.isAdminActive(admin),
            batteryUnrestricted = batteryOk(),
            writeSettingsGranted = writeSettingsOk(),
            showNotificationsPermission = Build.VERSION.SDK_INT >= 33,
            notificationsGranted = notificationsOk(),
            notificationListenerGranted = notificationListenerOk(),
            runtimeMediaAndNotifGranted = runtimeMediaAndNotifOk(),
            overlayGranted = overlayOk(),
            contactsGranted = contactsOk(),
            geminiKeyConfigured = prefs.geminiApiKey.isNotBlank(),
            aiSystemEnabled = prefs.aiSystemEnabled,
            virtualScreenAiEnabled = prefs.virtualScreenAiEnabled,
            remoteScreenAiEnabled = prefs.remoteScreenAiEnabled,
            ownDeviceAiEnabled = prefs.ownDeviceAiEnabled,
            overlayEnabled = prefs.overlayEnabled && AiOverlayService.isRunning,
            voiceState = uiState.voiceState,
            deviceRows = collectDeviceRows(),
        )
    }

    private fun collectDeviceRows(): List<Pair<String, String>> {
        val rows = ArrayList<Pair<String, String>>()
        rows += "Model" to "${Build.MANUFACTURER} ${Build.MODEL}"
        rows += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        if (Build.VERSION.SDK_INT >= 23) rows += "Security patch" to Build.VERSION.SECURITY_PATCH
        rows += "CPU" to "${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} · ${Runtime.getRuntime().availableProcessors()} cores"
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b ->
            val lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val chg = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            rows += "Battery" to "$lvl%${if (chg) " · charging" else ""} · ${b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0}°C"
        }
        val mi = ActivityManager.MemoryInfo().also {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        rows += "RAM" to "${gb(mi.totalMem - mi.availMem)} / ${gb(mi.totalMem)}"
        val st = StatFs(Environment.getDataDirectory().path)
        rows += "Storage" to "${gb(st.totalBytes - st.availableBytes)} / ${gb(st.totalBytes)}"
        val sz = DeviceInfo.screenSize(this)
        rows += "Display" to "${sz.x}×${sz.y} · ${resources.displayMetrics.densityDpi} dpi"
        rows += "Uptime" to uptime(SystemClock.elapsedRealtime() / 1000)
        return rows
    }

    private fun gb(b: Long) = if (b >= 1_000_000_000) "%.1f GB".format(b / 1e9) else "${b / 1_000_000} MB"

    private fun uptime(s: Long): String {
        val d = s / 86400
        val h = s % 86400 / 3600
        val m = s % 3600 / 60
        return (if (d > 0) "${d}d " else "") + "${h}h ${m}m"
    }
}

@Composable
private fun AppContent(
    hostState: RemoteUiState,
    prefs: Prefs,
    voiceController: VoiceAssistantController,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onRefreshState: () -> Unit,
    onAuthChanged: () -> Unit,
    onBindingVerified: (String) -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onDisconnectViewer: () -> Unit,
    onToggleCloud: (Boolean) -> Unit,
    onEnableAccessibility: () -> Unit,
    onToggleScreenCapture: (Boolean) -> Unit,
    onAllowScreenLock: () -> Unit,
    onAllowBattery: () -> Unit,
    onAllowWriteSettings: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowNotificationListener: () -> Unit,
    onAllowOverlay: () -> Unit,
    onAllowContacts: () -> Unit,
    onRequestAllRuntimePermissions: () -> Unit,
    onToggleAiSystem: (Boolean) -> Unit,
    onToggleVirtualScreenAi: (Boolean) -> Unit,
    onToggleRemoteScreenAi: (Boolean) -> Unit,
    onToggleOwnDeviceAi: (Boolean) -> Unit,
    onTriggerOwnDeviceVoice: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = onAuthChanged)
    } else {
        key(user.uid) {
            AuthenticatedShell(
                currentUserId = user.uid,
                currentUserEmail = user.email,
                hostState = hostState,
                prefs = prefs,
                voiceController = voiceController,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                currentLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage,
                onRefreshState = onRefreshState,
                onSignOutComplete = onAuthChanged,
                onBindingVerified = onBindingVerified,
                onCopyCode = onCopyCode,
                onShareCode = onShareCode,
                onDisconnectViewer = onDisconnectViewer,
                onToggleCloud = onToggleCloud,
                onEnableAccessibility = onEnableAccessibility,
                onToggleScreenCapture = onToggleScreenCapture,
                onAllowScreenLock = onAllowScreenLock,
                onAllowBattery = onAllowBattery,
                onAllowWriteSettings = onAllowWriteSettings,
                onAllowNotifications = onAllowNotifications,
                onAllowNotificationListener = onAllowNotificationListener,
                onAllowOverlay = onAllowOverlay,
                onAllowContacts = onAllowContacts,
                onRequestAllRuntimePermissions = onRequestAllRuntimePermissions,
                onToggleAiSystem = onToggleAiSystem,
                onToggleVirtualScreenAi = onToggleVirtualScreenAi,
                onToggleRemoteScreenAi = onToggleRemoteScreenAi,
                onToggleOwnDeviceAi = onToggleOwnDeviceAi,
                onTriggerOwnDeviceVoice = onTriggerOwnDeviceVoice,
                onToggleOverlay = onToggleOverlay,
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    currentUserEmail: String?,
    hostState: RemoteUiState,
    prefs: Prefs,
    voiceController: VoiceAssistantController,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onRefreshState: () -> Unit,
    onSignOutComplete: () -> Unit,
    onBindingVerified: (String) -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onDisconnectViewer: () -> Unit,
    onToggleCloud: (Boolean) -> Unit,
    onEnableAccessibility: () -> Unit,
    onToggleScreenCapture: (Boolean) -> Unit,
    onAllowScreenLock: () -> Unit,
    onAllowBattery: () -> Unit,
    onAllowWriteSettings: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowNotificationListener: () -> Unit,
    onAllowOverlay: () -> Unit,
    onAllowContacts: () -> Unit,
    onRequestAllRuntimePermissions: () -> Unit,
    onToggleAiSystem: (Boolean) -> Unit,
    onToggleVirtualScreenAi: (Boolean) -> Unit,
    onToggleRemoteScreenAi: (Boolean) -> Unit,
    onToggleOwnDeviceAi: (Boolean) -> Unit,
    onTriggerOwnDeviceVoice: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val cloudViewModel: CloudViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CloudViewModel(
                    application = app,
                    repository = CloudRepository(db),
                    prefs = prefs,
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    localDeviceId = prefs.deviceId,
                    localModelName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    onBindingVerified = onBindingVerified,
                )
            }
        },
    )

    val bindingState by cloudViewModel.bindingState.collectAsStateWithLifecycle()
    val devicesState by cloudViewModel.ownDevicesState.collectAsStateWithLifecycle()
    val savedDevices by cloudViewModel.savedDevices.collectAsStateWithLifecycle()
    val connectDialogState by cloudViewModel.connectDialog.collectAsStateWithLifecycle()
    val sessionState by cloudViewModel.session.collectAsStateWithLifecycle()
    val chatThreads by cloudViewModel.chatThreads.collectAsStateWithLifecycle()
    val activeChat by cloudViewModel.activeChat.collectAsStateWithLifecycle()
    val chatMessages by cloudViewModel.chatMessages.collectAsStateWithLifecycle()
    val chatBusy by cloudViewModel.chatBusy.collectAsStateWithLifecycle()
    val chatError by cloudViewModel.chatError.collectAsStateWithLifecycle()
    val incomingCall by cloudViewModel.incomingCall.collectAsStateWithLifecycle()
    val activeCall by cloudViewModel.activeCall.collectAsStateWithLifecycle()
    val adminAccessStatus by cloudViewModel.adminAccessStatus.collectAsStateWithLifecycle()

    LaunchedEffect(adminAccessStatus.blocked) {
        if (adminAccessStatus.blocked) {
            voiceController.stopListening()
            AiOverlayService.stop(context)
            cloudViewModel.closeSession()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var openedChatFromRemote by remember { mutableStateOf(false) }
    var showAiChatScreen by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var showChangeKeyDialog by remember(currentUserId) { mutableStateOf(false) }
    var showHowToUseDialog by remember(currentUserId) { mutableStateOf(false) }
    var showServerVaultDialog by remember(currentUserId) { mutableStateOf(false) }
    var showSupportDialog by remember(currentUserId) { mutableStateOf(false) }

    val initiallyOnboarded = remember(currentUserId) { prefs.onboardingDone }
    var onboardDone by rememberSaveable(currentUserId) { mutableStateOf(initiallyOnboarded) }
    var onboardStep by rememberSaveable(currentUserId) { mutableStateOf("checking") }

    if (adminAccessStatus.blocked) {
        BlockedAccountScreen(
            reason = adminAccessStatus.blockReason,
            blockedByDevice = adminAccessStatus.blockedByDevice,
            userEmail = currentUserEmail,
            deviceId = prefs.deviceId,
            onSignOut = {
                signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignOutComplete,
                )
            },
        )
        return
    }

    if (adminAccessStatus.maintenanceMode) {
        MaintenanceNoticeScreen(
            notice = adminAccessStatus.maintenanceNotice,
            onSignOut = {
                signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignOutComplete,
                )
            },
        )
        return
    }

    if (bindingState is AccountBindingState.LockedToOtherAccount) {
        val b = bindingState as AccountBindingState.LockedToOtherAccount
        DeviceLockedToAccountScreen(
            boundMaskedEmail = b.maskedEmail,
            currentEmail = currentUserEmail,
            onSwitchAccount = {
                signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignOutComplete,
                )
            },
        )
        return
    }

    if (!onboardDone) {
        when (onboardStep) {
            "checking" -> {
                DeviceCheckingStepScreen(
                    deviceCode = (bindingState as? AccountBindingState.Bound)?.deviceCode,
                    isChecking = bindingState is AccountBindingState.Checking,
                    errorMessage = (bindingState as? AccountBindingState.Error)?.message,
                    onRetry = { cloudViewModel.verifyDeviceBinding() },
                    onContinue = { onboardStep = "developer" },
                )
            }
            "developer" -> {
                DeveloperIntroStepScreen(
                    onNext = { onboardStep = "permissions" },
                )
            }
            "permissions" -> {
                PermissionsAllowStepScreen(
                    state = hostState,
                    onEnableAccessibility = onEnableAccessibility,
                    onEnableScreenCapture = { onToggleScreenCapture(true) },
                    onEnableNotificationAccess = onAllowNotificationListener,
                    onAllowBattery = onAllowBattery,
                    onAllowWriteSettings = onAllowWriteSettings,
                    onAllowOverlay = onAllowOverlay,
                    onRequestAllRuntimePermissions = onRequestAllRuntimePermissions,
                    onFinishToHome = { onboardStep = "apikey" },
                )
            }
            else -> {
                GeminiApiKeyStepScreen(
                    initialKey = prefs.geminiApiKey,
                    onVerified = { key, model ->
                        prefs.geminiApiKey = key
                        prefs.geminiModel = model
                        prefs.onboardingDone = true
                        onboardDone = true
                        Toast.makeText(context, "Google AI Studio Key যাচাই সম্পন্ন!", Toast.LENGTH_SHORT).show()
                        onRefreshState()
                    },
                    onSkip = {
                        prefs.onboardingDone = true
                        onboardDone = true
                        Toast.makeText(context, "হোমপেজ থেকে যেকোনো সময় Key এড করতে পারবেন", Toast.LENGTH_SHORT).show()
                        onRefreshState()
                    },
                )
            }
        }
        return
    }

    when (val b = bindingState) {
        is AccountBindingState.Checking -> {
            var secondsLeft by remember(currentUserId) { mutableIntStateOf(5) }
            LaunchedEffect(currentUserId) {
                secondsLeft = 5
                while (secondsLeft > 0) {
                    delay(1000L)
                    secondsLeft -= 1
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ডিভাইস সংযোগ হচ্ছে... (${secondsLeft} সেকেন্ড)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }
        is AccountBindingState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = b.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { cloudViewModel.verifyDeviceBinding() }) {
                        Text("আবার চেষ্টা করুন")
                    }
                }
            }
            return
        }
        is AccountBindingState.Bound, is AccountBindingState.LockedToOtherAccount -> Unit
    }

    var showDeveloperPopup by rememberSaveable(currentUserId) { mutableStateOf(initiallyOnboarded) }
    if (showDeveloperPopup) {
        DeveloperCreditDialog(onDismiss = { showDeveloperPopup = false })
    }

    if (showChangeKeyDialog) {
        ChangeGeminiApiKeyDialog(
            currentKey = prefs.geminiApiKey,
            onDismiss = { showChangeKeyDialog = false },
            onSaved = { key, model ->
                prefs.geminiApiKey = key
                prefs.geminiModel = model
                showChangeKeyDialog = false
                Toast.makeText(context, "Google AI Studio Key আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                onRefreshState()
            },
        )
    }

    if (showHowToUseDialog) {
        HowToUseDialog(
            onDismiss = { showHowToUseDialog = false },
            onOpenServerVault = { showServerVaultDialog = true },
        )
    }

    if (showServerVaultDialog) {
        ServerVaultLockDialog(
            onDismiss = { showServerVaultDialog = false },
        )
    }

    if (showSupportDialog) {
        TelegramSupportDialog(
            onDismiss = { showSupportDialog = false },
        )
    }

    // Button 5: Dedicated AI Chat Screen
    if (showAiChatScreen) {
        AiChatScreen(
            apiKey = prefs.geminiApiKey,
            modelName = prefs.geminiModel,
            aiSystemEnabled = prefs.aiSystemEnabled,
            onModelResolved = { resolved ->
                prefs.geminiModel = resolved
                onRefreshState()
            },
            onOpenKeySettings = {
                showAiChatScreen = false
                showChangeKeyDialog = true
            },
            onBack = { showAiChatScreen = false },
        )
        return
    }

    // Active Audio/Video Call Full Screen Overlay
    if (activeCall.callId != null) {
        ActiveCallScreen(
            state = activeCall,
            onBindLocalPreview = { owner, pv -> cloudViewModel.bindLocalCameraPreview(owner, pv) },
            onToggleMute = { cloudViewModel.toggleCallMute() },
            onToggleSpeaker = { cloudViewModel.toggleCallSpeaker() },
            onToggleCamera = { owner, pv -> cloudViewModel.toggleCallCamera(owner, pv) },
            onFlipCamera = { owner, pv -> cloudViewModel.flipCallCamera(owner, pv) },
            onEndCall = { cloudViewModel.endActiveCall() },
        )
        return
    }

    IncomingCallDialog(
        info = incomingCall,
        onAccept = { info ->
            selectedTab = 2
            cloudViewModel.acceptIncomingCall(info)
        },
        onDecline = { info ->
            cloudViewModel.declineIncomingCall(info)
        },
    )

    ConnectByCodeDialog(
        state = connectDialogState,
        onDismiss = { cloudViewModel.dismissConnectDialog() },
        onConnect = { code ->
            selectedTab = 1
            cloudViewModel.connectByCode(code)
        },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            val inRemoteConsole = selectedTab == 1 && sessionState.targetDevice != null
            val inChatThread = selectedTab == 2 && activeChat != null
            if (!inRemoteConsole && !inChatThread) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Smartphone else Icons.Outlined.Smartphone,
                                contentDescription = s.tabThisDevice,
                            )
                        },
                        label = { Text(s.tabThisDevice) },
                        modifier = Modifier.testTag("tab_this_device"),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Filled.Devices else Icons.Outlined.Devices,
                                contentDescription = s.tabRemoteControl,
                            )
                        },
                        label = { Text(s.tabRemoteControl) },
                        modifier = Modifier.testTag("tab_remote_control"),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            openedChatFromRemote = false
                            selectedTab = 2
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (chatThreads.isNotEmpty()) {
                                        Badge { Text("${chatThreads.size}") }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 2) Icons.Filled.Chat else Icons.Outlined.Chat,
                                    contentDescription = s.tabChat,
                                )
                            }
                        },
                        label = { Text(s.tabChat) },
                        modifier = Modifier.testTag("tab_chat"),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                0 -> {
                    VitRemoteHostTab(
                        state = hostState.copy(globalNotice = adminAccessStatus.globalNotice),
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        currentLanguage = currentLanguage,
                        onSelectLanguage = onSelectLanguage,
                        onOpenSupport = { showSupportDialog = true },
                        onConnectDeviceClick = { cloudViewModel.showConnectDialog() },
                        onUpdateAlias = { alias ->
                            cloudViewModel.updateLocalDeviceAlias(alias)
                            onRefreshState()
                        },
                        onCopyCode = onCopyCode,
                        onShareCode = onShareCode,
                        onDisconnectViewer = onDisconnectViewer,
                        onToggleCloud = onToggleCloud,
                        onEnableAccessibility = onEnableAccessibility,
                        onToggleScreenCapture = onToggleScreenCapture,
                        onAllowScreenLock = onAllowScreenLock,
                        onAllowBattery = onAllowBattery,
                        onAllowWriteSettings = onAllowWriteSettings,
                        onAllowNotifications = onAllowNotifications,
                        onAllowNotificationListener = onAllowNotificationListener,
                        onAllowOverlay = onAllowOverlay,
                        onAllowContacts = onAllowContacts,
                        onToggleAiSystem = onToggleAiSystem,
                        onToggleVirtualScreenAi = onToggleVirtualScreenAi,
                        onToggleRemoteScreenAi = onToggleRemoteScreenAi,
                        onToggleOwnDeviceAi = onToggleOwnDeviceAi,
                        onTriggerOwnDeviceVoice = onTriggerOwnDeviceVoice,
                        onOpenAiChat = { showAiChatScreen = true },
                        onToggleOverlay = onToggleOverlay,
                        onChangeApiKey = { showChangeKeyDialog = true },
                        onHowToUse = { showHowToUseDialog = true },
                        onSignOut = {
                            signOut(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignOutComplete,
                            )
                        },
                    )
                }
                1 -> {
                    RemoteDevicesTab(
                        devicesState = devicesState,
                        savedDevices = savedDevices,
                        localDeviceId = prefs.deviceId,
                        sessionState = sessionState,
                        aiSystemEnabled = hostState.aiSystemEnabled,
                        virtualScreenAiEnabled = hostState.virtualScreenAiEnabled,
                        remoteScreenAiEnabled = hostState.remoteScreenAiEnabled,
                        voiceState = hostState.voiceState,
                        onTriggerRemoteAiVoice = { targetVirtual ->
                            cloudViewModel.runRemoteVoiceAssistant(voiceController, targetVirtual)
                        },
                        onShowConnectDialog = { cloudViewModel.showConnectDialog() },
                        onReconnectSaved = { saved -> cloudViewModel.connectByCode(saved.deviceCode) },
                        onRenameSaved = { id, alias -> cloudViewModel.renameSavedDevice(id, alias) },
                        onTogglePinSaved = { id, pinned -> cloudViewModel.togglePinSavedDevice(id, pinned) },
                        onRemoveSaved = { id -> cloudViewModel.removeSavedDevice(id) },
                        onOpenOwnDevice = { dev -> cloudViewModel.openSession(dev, dev.deviceCode) },
                        onSendCommand = { cmd -> cloudViewModel.sendCommand(cmd) },
                        onChangeQuality = { w -> cloudViewModel.setQuality(w) },
                        onPullClipboard = { cloudViewModel.pullRemoteClipboard() },
                        onDismissClipboard = { cloudViewModel.dismissPulledClipboard() },
                        onRequestApps = { cloudViewModel.requestRemoteInstalledApps() },
                        onOpenAppReal = { pkg -> cloudViewModel.openRemoteAppReal(pkg) },
                        onOpenAppVirtual = { pkg -> cloudViewModel.openRemoteAppVirtual(pkg) },
                        onToggleVirtualMode = { active -> cloudViewModel.setVirtualModeActive(active) },
                        onCloseVirtualScreen = { cloudViewModel.closeRemoteVirtualScreen() },
                        onRequestNotifications = { cloudViewModel.requestRemoteNotifications() },
                        onToggleWatchedApp = { pkg -> cloudViewModel.toggleWatchedApp(pkg) },
                        onDismissAppWatchAlert = { cloudViewModel.dismissAppWatchAlert() },
                        onToggleRemoteScreenTouch = { cloudViewModel.toggleRemoteScreenTouch() },
                        onOpenChat = {
                            cloudViewModel.openChatFromRemoteSession {
                                openedChatFromRemote = true
                                selectedTab = 2
                            }
                        },
                        onCloseSession = { cloudViewModel.closeSession() },
                    )
                }
                else -> {
                    ChatTabScreen(
                        threads = chatThreads,
                        activeChat = activeChat,
                        messages = chatMessages,
                        chatBusy = chatBusy,
                        chatError = chatError,
                        currentUserId = currentUserId,
                        localDeviceId = prefs.deviceId,
                        onOpenThread = { thread ->
                            openedChatFromRemote = false
                            cloudViewModel.openChatThread(thread)
                        },
                        onCloseThread = {
                            cloudViewModel.closeChatThread()
                            if (openedChatFromRemote && sessionState.targetDevice != null) {
                                openedChatFromRemote = false
                                selectedTab = 1
                            }
                        },
                        onShowConnectDialog = { cloudViewModel.showConnectDialog() },
                        onSendText = { txt -> cloudViewModel.sendChatText(txt) },
                        onSendPhoto = { uri -> cloudViewModel.sendChatPhoto(uri) },
                        onStartCall = { type -> cloudViewModel.startCall(type) },
                    )
                }
            }
        }
    }
}
