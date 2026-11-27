package com.example.cloud

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.example.CaptureService
import com.example.DeviceInfo
import com.example.Prefs
import com.example.RemoteService
import com.example.data.BindingCheckResult
import com.example.data.CloudRepository
import com.example.data.DeviceNode
import com.example.data.InstalledAppEntry
import com.example.data.LiveFrame
import com.example.data.RemoteNotificationEntry
import com.example.security.SecureServerVault
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.zip.CRC32

class CloudRelayManager(
    private val context: Context,
    private val prefs: Prefs,
    private val onCommand: (String) -> Unit,
    private val onViewerChanged: (active: Boolean, width: Int) -> Unit,
) {
    private val repo = CloudRepository(context.applicationContext)
    private val auth = Firebase.auth
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeUid: String? = null
    private var attachJob: Job? = null
    private var deviceObserverJob: Job? = null
    private var commandsObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var adminObserverJob: Job? = null

    private val handledCommands = Collections.synchronizedSet(HashSet<String>())
    @Volatile var isOnline: Boolean = false
        private set
    @Volatile var adminBlocked: Boolean = false
        private set
    @Volatile var viewerActive: Boolean = false
        private set
    @Volatile var viewerQuality: Int = 480
        private set
    @Volatile var activeViewerUid: String? = null
        private set

    @Volatile private var lastClipboardText: String = ""
    @Volatile private var foregroundPackage: String = ""
    @Volatile private var foregroundLabel: String = ""
    @Volatile private var foregroundChangedAtMs: Long = 0L
    @Volatile private var lastFrameAt = 0L
    @Volatile private var lastFrameCrc = 0L
    @Volatile private var frameSeq = 0L
    @Volatile private var publishingFrame = false

    @Volatile private var lastVirtualAt = 0L
    @Volatile private var lastVirtualCrc = 0L
    @Volatile private var virtualSeq = 0L
    @Volatile private var publishingVirtual = false

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        val uid = user?.uid
        if (uid != activeUid) {
            detachListeners()
            activeUid = uid
            if (uid != null && prefs.cloudEnabled) {
                attachForUser(uid, user.email)
            }
        }
    }

    fun start() {
        auth.addAuthStateListener(authListener)
        val user = auth.currentUser
        val uid = user?.uid
        activeUid = uid
        if (uid != null && prefs.cloudEnabled) {
            attachForUser(uid, user.email)
        }
    }

    fun syncConfig() {
        val user = auth.currentUser
        val uid = user?.uid
        detachListeners()
        if (uid != null && prefs.cloudEnabled) {
            activeUid = uid
            attachForUser(uid, user.email)
        } else {
            activeUid = uid
            if (uid != null) {
                scope.launch { repo.setDeviceOffline(prefs.deviceId, prefs.deviceCode) }
            }
            isOnline = false
            activeViewerUid = null
            if (viewerActive) {
                viewerActive = false
                onViewerChanged(false, 0)
            }
        }
    }

    fun disconnectViewer() {
        val deviceId = prefs.deviceId
        scope.launch {
            repo.disconnectRemoteViewer(deviceId)
        }
    }

    fun publishClipboard(text: String) {
        lastClipboardText = text.take(4000)
        triggerTelemetrySync()
    }

    fun updateForegroundApp(packageName: String, label: String) {
        val cleanPkg = packageName.trim().take(150)
        if (cleanPkg.isEmpty()) return
        val cleanLabel = label.trim().ifBlank { cleanPkg }.take(100)
        foregroundPackage = cleanPkg
        foregroundLabel = cleanLabel
        foregroundChangedAtMs = System.currentTimeMillis()
        triggerTelemetrySync()
    }

    fun publishInstalledApps(apps: List<InstalledAppEntry>) {
        val uid = auth.currentUser?.uid ?: return
        if (!prefs.cloudEnabled) return
        scope.launch {
            repo.publishInstalledApps(uid, prefs.deviceId, apps)
        }
    }

    fun publishNotifications(listenerEnabled: Boolean, notifications: List<RemoteNotificationEntry>) {
        val uid = auth.currentUser?.uid ?: return
        if (!prefs.cloudEnabled) return
        scope.launch {
            repo.publishNotifications(uid, prefs.deviceId, listenerEnabled, notifications)
        }
    }

    private fun attachForUser(uid: String, email: String?) {
        val deviceId = prefs.deviceId
        attachJob = scope.launch {
            val bindingRes = repo.ensureDeviceBinding(
                uid = uid,
                email = email,
                deviceId = deviceId,
                preferredCode = prefs.deviceCode,
            )
            val bound = bindingRes.getOrNull()
            if (bound is BindingCheckResult.LockedToOtherAccount) {
                isOnline = false
                return@launch
            }
            if (bound is BindingCheckResult.Bound) {
                prefs.deviceCode = bound.deviceCode
            }

            repo.syncUserRegistration(
                uid = uid,
                email = email,
                displayName = prefs.displayName("${Build.MANUFACTURER} ${Build.MODEL}".trim()),
                deviceId = deviceId,
                deviceCode = prefs.deviceCode,
            )

            adminObserverJob = launch {
                while (isActive && prefs.cloudEnabled && auth.currentUser?.uid == uid) {
                    try {
                        repo.observeAdminAccessStatus(uid, deviceId).collect { access ->
                            val wasBlocked = adminBlocked
                            adminBlocked = access.blocked
                            if (access.blocked) {
                                isOnline = false
                                if (viewerActive) {
                                    viewerActive = false
                                    onViewerChanged(false, 0)
                                }
                                if (!wasBlocked) {
                                    repo.setDeviceOffline(deviceId, prefs.deviceCode)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Admin access observer retrying: ${e.message}")
                    }
                    delay(OBSERVER_RETRY_MS)
                }
            }

            heartbeatJob = launch {
                while (isActive && prefs.cloudEnabled && !adminBlocked && auth.currentUser?.uid == uid) {
                    if (!SecureServerVault.isAppPackageValid(context)) {
                        isOnline = false
                        break
                    }
                    val res = repo.upsertDevice(uid, deviceId, buildDeviceNode(uid))
                    isOnline = res.isSuccess
                    delay(HEARTBEAT_MS)
                }
            }

            deviceObserverJob = launch {
                while (isActive && prefs.cloudEnabled && auth.currentUser?.uid == uid) {
                    try {
                        repo.observeDevice(deviceId).collect { node ->
                            val active = node?.viewerActive == true && prefs.cloudEnabled && !adminBlocked
                            val quality = (node?.viewerQuality ?: 480L).toInt().coerceIn(240, 1080)
                            activeViewerUid = if (active) node?.activeViewerUid else null
                            if (active != viewerActive || quality != viewerQuality) {
                                viewerActive = active
                                viewerQuality = quality
                                onViewerChanged(active, if (active) quality else 0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Device observer retrying: ${e.message}")
                    }
                    delay(OBSERVER_RETRY_MS)
                }
            }

            commandsObserverJob = launch {
                while (isActive && prefs.cloudEnabled && auth.currentUser?.uid == uid) {
                    try {
                        repo.observePendingCommands(deviceId).collect { pending ->
                            for (cmd in pending) {
                                if (!handledCommands.add(cmd.id)) continue
                                if (adminBlocked) {
                                    repo.markCommandResult(deviceId, cmd.id, "error", "Blocked by admin")
                                } else if (cmd.pin != prefs.deviceCode) {
                                    repo.markCommandResult(deviceId, cmd.id, "error", "Invalid device code")
                                } else {
                                    try {
                                        onCommand(cmd.payload)
                                        repo.markCommandResult(deviceId, cmd.id, "done")
                                    } catch (e: Exception) {
                                        repo.markCommandResult(
                                            deviceId,
                                            cmd.id,
                                            "error",
                                            e.message ?: "Command failed",
                                        )
                                    }
                                }
                                if (handledCommands.size > 200) {
                                    handledCommands.clear()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Command observer retrying: ${e.message}")
                    }
                    delay(OBSERVER_RETRY_MS)
                }
            }
        }
    }

    fun triggerTelemetrySync() {
        val uid = auth.currentUser?.uid ?: return
        if (!prefs.cloudEnabled || adminBlocked) return
        scope.launch {
            val res = repo.upsertDevice(uid, prefs.deviceId, buildDeviceNode(uid))
            isOnline = res.isSuccess
        }
    }

    fun publishBitmap(bmp: Bitmap, isFullShot: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (!prefs.cloudEnabled || adminBlocked) return
        if (!isFullShot && !viewerActive) return
        val now = SystemClock.uptimeMillis()
        if (!isFullShot && (publishingFrame || now - lastFrameAt < MIN_FRAME_INTERVAL_MS)) return

        publishingFrame = true
        val copyCfg = bmp.config ?: Bitmap.Config.ARGB_8888
        val frameCopy = try {
            bmp.copy(copyCfg, false)
        } catch (_: Throwable) {
            null
        }
        if (frameCopy == null) {
            publishingFrame = false
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val width = frameCopy.width
                val height = frameCopy.height
                val bo = ByteArrayOutputStream(32 * 1024)
                var quality = if (isFullShot) 80 else 50
                frameCopy.compress(Bitmap.CompressFormat.JPEG, quality, bo)
                var bytes = bo.toByteArray()
                while (bytes.size > 560_000 && quality > 20) {
                    bo.reset()
                    quality -= 12
                    frameCopy.compress(Bitmap.CompressFormat.JPEG, quality, bo)
                    bytes = bo.toByteArray()
                }

                val crc = CRC32().apply { update(bytes) }.value
                if (!isFullShot && crc == lastFrameCrc) {
                    return@launch
                }
                lastFrameCrc = crc
                lastFrameAt = now
                val seq = ++frameSeq
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                repo.publishFrame(
                    uid,
                    prefs.deviceId,
                    LiveFrame(
                        ownerUid = uid,
                        seq = seq,
                        width = width.toLong(),
                        height = height.toLong(),
                        isFullShot = isFullShot,
                        imageBase64 = b64,
                    ),
                )
            } finally {
                if (!frameCopy.isRecycled) frameCopy.recycle()
                publishingFrame = false
            }
        }
    }

    fun publishVirtualBitmap(bmp: Bitmap) {
        val uid = auth.currentUser?.uid ?: return
        if (!prefs.cloudEnabled) return
        val now = SystemClock.uptimeMillis()
        if (publishingVirtual || now - lastVirtualAt < MIN_FRAME_INTERVAL_MS) return

        publishingVirtual = true
        val copyCfg = bmp.config ?: Bitmap.Config.ARGB_8888
        val frameCopy = try {
            bmp.copy(copyCfg, false)
        } catch (_: Throwable) {
            null
        }
        if (frameCopy == null) {
            publishingVirtual = false
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val width = frameCopy.width
                val height = frameCopy.height
                val bo = ByteArrayOutputStream(32 * 1024)
                var quality = 54
                frameCopy.compress(Bitmap.CompressFormat.JPEG, quality, bo)
                var bytes = bo.toByteArray()
                while (bytes.size > 560_000 && quality > 20) {
                    bo.reset()
                    quality -= 12
                    frameCopy.compress(Bitmap.CompressFormat.JPEG, quality, bo)
                    bytes = bo.toByteArray()
                }

                val crc = CRC32().apply { update(bytes) }.value
                if (crc == lastVirtualCrc) {
                    return@launch
                }
                lastVirtualCrc = crc
                lastVirtualAt = now
                val seq = ++virtualSeq
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                repo.publishVirtualFrame(
                    uid,
                    prefs.deviceId,
                    LiveFrame(
                        ownerUid = uid,
                        seq = seq,
                        width = width.toLong(),
                        height = height.toLong(),
                        isFullShot = false,
                        imageBase64 = b64,
                    ),
                )
            } finally {
                if (!frameCopy.isRecycled) frameCopy.recycle()
                publishingVirtual = false
            }
        }
    }

    private fun buildDeviceNode(uid: String): DeviceNode {
        val sz = DeviceInfo.screenSize(context)
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) (l * 100 / s).toLong() else -1L
        } ?: -1L
        val tempTenths = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300)?.toLong() ?: 300L
        val charging = bat?.let { it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 } ?: false

        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val ramTotalMb = (mi.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val ramUsedMb = ((mi.totalMem - mi.availMem) / (1024L * 1024L)).coerceIn(0L, ramTotalMb)

        val st = StatFs(Environment.getDataDirectory().path)
        val storageTotalMb = (st.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
        val storageUsedMb = ((st.totalBytes - st.availableBytes) / (1024L * 1024L)).coerceIn(0L, storageTotalMb)

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toLong()
        } catch (_: Exception) {
            -1L
        }

        val captureMode = when {
            CaptureService.instance != null -> "Cloud Stream Ready"
            Build.VERSION.SDK_INT >= 30 -> "Cloud Accessibility Capture"
            else -> "Enable Screen Capture"
        }

        val svc = RemoteService.instance

        return DeviceNode(
            ownerUid = uid,
            deviceCode = prefs.deviceCode,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceAlias = prefs.deviceAlias,
            androidVersion = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT.toLong(),
            online = true,
            cloudEnabled = prefs.cloudEnabled,
            videoStreaming = CaptureService.instance != null,
            screenWidth = sz.x.toLong(),
            screenHeight = sz.y.toLong(),
            batteryLevel = level,
            batteryTempTenths = tempTenths,
            charging = charging,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            storageUsedMb = storageUsedMb,
            storageTotalMb = storageTotalMb,
            brightness = brightness,
            torchOn = svc?.torchOn == true,
            ringing = svc?.ringing == true,
            clipboardText = lastClipboardText,
            captureMode = captureMode,
            viewerActive = viewerActive,
            viewerQuality = viewerQuality.toLong(),
            virtualScreenActive = svc?.virtualScreenActive == true,
            virtualScreenPackage = svc?.virtualScreenPackage.orEmpty(),
            virtualScreenStatus = svc?.virtualScreenStatus.orEmpty(),
            foregroundPackage = foregroundPackage,
            foregroundLabel = foregroundLabel,
            foregroundChangedAtMs = foregroundChangedAtMs,
        )
    }

    private fun detachListeners() {
        attachJob?.cancel()
        attachJob = null
        adminObserverJob?.cancel()
        adminObserverJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        deviceObserverJob?.cancel()
        deviceObserverJob = null
        commandsObserverJob?.cancel()
        commandsObserverJob = null
        isOnline = false
        adminBlocked = false
    }

    fun stop() {
        auth.removeAuthStateListener(authListener)
        detachListeners()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                repo.setDeviceOffline(prefs.deviceId, prefs.deviceCode)
            }
        }
        scope.cancel()
    }

    private companion object {
        const val TAG = "CloudRelay"
        const val HEARTBEAT_MS = 8_000L
        const val OBSERVER_RETRY_MS = 1_500L
        const val MIN_FRAME_INTERVAL_MS = 95L
    }
}
