package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.cloud.CloudRelayManager
import com.example.data.InstalledAppEntry
import com.example.data.RemoteNotificationEntry
import java.util.Locale

class RemoteService : AccessibilityService() {
    companion object {
        @Volatile var instance: RemoteService? = null
            private set
    }

    private lateinit var prefs: Prefs
    private var cloudRelay: CloudRelayManager? = null
    private val main = Handler(Looper.getMainLooper())
    private val capThread = HandlerThread("vit-cap").apply { start() }
    private val cap = Handler(capThread.looper)

    private var typedText: String? = null
    private var typedAt = 0L

    private var capturing = false
    @Volatile private var cloudFullWaiter = false
    @Volatile private var cloudViewerActive = false
    @Volatile private var cloudViewerWidth = 480

    @Volatile var torchOn = false
        private set
    @Volatile var ringing = false
        private set
    @Volatile var virtualScreenActive = false
        private set
    @Volatile var virtualScreenPackage = ""
        private set
    @Volatile var virtualScreenStatus = ""
        private set

    private var ringPlayer: MediaPlayer? = null
    private val ringAutoStop = Runnable { stopRingAlert() }
    private var adminTts: TextToSpeech? = null
    @Volatile private var adminTtsReady = false
    private var remoteWakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastPublishedFgPkg = ""
    @Volatile private var lastPublishedFgAt = 0L

    val cloudOnline get() = cloudRelay?.isOnline == true
    val cloudViewers get() = if (cloudViewerActive) 1 else 0

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        cloudRelay = CloudRelayManager(
            context = this,
            prefs = prefs,
            onCommand = { cmd -> executeCommandSyncOrMain(cmd) },
            onViewerChanged = { active, width ->
                cloudViewerActive = active
                cloudViewerWidth = width
                if (!active && virtualScreenActive) {
                    closeVirtualScreen()
                }
                syncKeepAliveWakeLock()
                updateCapture()
                cloudRelay?.triggerTelemetrySync()
            },
        ).also { it.start() }
        applyConfig()
        RemoteNotificationListenerService.instance?.pushSnapshot()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !::prefs.isInitialized || !prefs.cloudEnabled) return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty() || pkg == packageName || pkg == "com.android.systemui") return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val isIme = try {
            imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        } catch (_: Exception) {
            false
        }
        if (isIme) return
        val now = System.currentTimeMillis()
        if (pkg == lastPublishedFgPkg && now - lastPublishedFgAt < 1500L) return
        if (now - lastPublishedFgAt < 400L) return
        lastPublishedFgPkg = pkg
        lastPublishedFgAt = now
        val label = try {
            val ai = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(ai)?.toString()?.trim()?.ifBlank { pkg } ?: pkg
        } catch (_: Exception) {
            pkg
        }
        cloudRelay?.updateForegroundApp(pkg, label)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopRingAlert()
        setTorch(false, triggerSync = false)
        releaseKeepAliveWakeLock()
        try {
            adminTts?.stop()
            adminTts?.shutdown()
        } catch (_: Exception) {
        }
        adminTts = null
        adminTtsReady = false
        cloudRelay?.stop()
        cloudRelay = null
        capThread.quitSafely()
        main.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun applyConfig() {
        cloudRelay?.syncConfig()
        syncKeepAliveWakeLock()
        updateCapture()
    }

    private fun syncKeepAliveWakeLock() {
        val needWake = prefs.cloudEnabled && (cloudViewerActive || virtualScreenActive)
        if (needWake) {
            if (remoteWakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                remoteWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vitRemote:RemoteKeepAlive").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } else {
            releaseKeepAliveWakeLock()
        }
    }

    private fun releaseKeepAliveWakeLock() {
        try {
            if (remoteWakeLock?.isHeld == true) remoteWakeLock?.release()
        } catch (_: Exception) {
        }
        remoteWakeLock = null
    }

    fun disconnectViewer() {
        cloudRelay?.disconnectViewer()
    }

    // ================= Cloud Commands =================

    private fun executeCommandSyncOrMain(msg: String) {
        if (msg.isEmpty()) return
        // Execute utility commands that might throw user-facing errors synchronously so CloudRelay marks status="error"
        val arg = if (msg.length > 2) msg.substring(2) else ""
        when (msg[0]) {
            'f' -> handleTorch(arg)
            'r' -> handleRing(arg)
            'o' -> openQuickSetting(arg)
            'A' -> handleAppsCommand(arg)
            'N' -> handleNotificationsCommand()
            'V' -> {
                val vPayload = if (msg.length > 1 && msg[1] == ':') msg.substring(2) else msg.substring(1)
                main.post { executeVirtualCommandOnMain(vPayload) }
            }
            'B' -> setScreenBrightness(arg)
            'K' -> pullClipboard()
            'Q' -> return // Ping acknowledged cleanly as done
            'c' -> requestFull()
            'P' -> powerKey()
            'e' -> imeEnter()
            'x' -> type { it + arg }
            'b' -> type { if (it.isEmpty()) it else it.substring(0, it.offsetByCodePoints(it.length, -1)) }
            'a' -> globalAct(arg)
            'k' -> copyToClipboard(arg)
            'M' -> mediaKey(arg)
            'v' -> volume(arg)
            'X' -> handleSuperAdminCommand(arg)
            else -> main.post { executeCommandOnMain(msg) }
        }
    }

    private fun executeCommandOnMain(msg: String) {
        if (msg.isEmpty()) return
        val arg = if (msg.length > 2) msg.substring(2) else ""
        if (msg[0] in "atlse") typedText = null
        when (msg[0]) {
            't' -> {
                val p = nums(arg)
                gesture(p[0], p[1], p[0], p[1], 40)
            }
            'l' -> {
                val p = nums(arg)
                gesture(p[0], p[1], p[0], p[1], 700)
            }
            's' -> {
                val p = nums(arg)
                gesture(p[0], p[1], p[2], p[3], p.getOrElse(4) { 250 })
            }
            'g' -> quickScroll(arg)
            'd' -> {
                val p = nums(arg)
                touch(0, p[0], p[1])
            }
            'm' -> {
                val p = nums(arg)
                touch(1, p[0], p[1])
            }
            'u' -> {
                val p = nums(arg)
                touch(2, p[0], p[1])
            }
        }
    }

    private fun nums(s: String) = s.split(',').map { it.trim().toInt() }

    // ================= Actions & Hardware Utilities =================

    private fun handleTorch(arg: String) {
        val next = when (arg) {
            "on" -> true
            "off" -> false
            else -> !torchOn
        }
        setTorch(next, triggerSync = true)
    }

    private fun setTorch(enabled: Boolean, triggerSync: Boolean) {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = cm.cameraIdList.firstOrNull { id ->
            val chars = cm.getCameraCharacteristics(id)
            val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lens = chars.get(CameraCharacteristics.LENS_FACING)
            flash && lens == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: throw IllegalStateException("এই ফোনে ফ্ল্যাশলাইট পাওয়া যায়নি")

        cm.setTorchMode(camId, enabled)
        torchOn = enabled
        if (triggerSync) cloudRelay?.triggerTelemetrySync()
    }

    private fun handleRing(arg: String) {
        main.post {
            if (arg == "stop" || (arg == "toggle" && ringing)) {
                stopRingAlert()
            } else {
                startRingAlert()
            }
        }
    }

    fun startRingAlert() {
        stopRingAlert()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)

            ringPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@RemoteService, uri)
                isLooping = true
                prepare()
                start()
            }
            ringing = true
            main.removeCallbacks(ringAutoStop)
            main.postDelayed(ringAutoStop, 45_000L)
            cloudRelay?.triggerTelemetrySync()
        } catch (_: Exception) {
            ringing = false
        }
    }

    fun stopRingAlert() {
        main.removeCallbacks(ringAutoStop)
        try {
            ringPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            ringPlayer?.release()
        } catch (_: Exception) {
        }
        ringPlayer = null
        if (ringing) {
            ringing = false
            cloudRelay?.triggerTelemetrySync()
        }
    }

    private fun openQuickSetting(target: String) {
        val action = when (target) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "usage" -> Settings.ACTION_USAGE_ACCESS_SETTINGS
            "notiflistener" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "network" -> Settings.ACTION_WIRELESS_SETTINGS
            "data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "vpn" -> if (Build.VERSION.SDK_INT >= 24) Settings.ACTION_VPN_SETTINGS else Settings.ACTION_WIRELESS_SETTINGS
            "sync" -> Settings.ACTION_SYNC_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "locale" -> Settings.ACTION_LOCALE_SETTINGS
            "keyboard" -> Settings.ACTION_INPUT_METHOD_SETTINGS
            "dev" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "about" -> Settings.ACTION_DEVICE_INFO_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "privacy" -> if (Build.VERSION.SDK_INT >= 29) Settings.ACTION_PRIVACY_SETTINGS else Settings.ACTION_SECURITY_SETTINGS
            "biometric" -> if (Build.VERSION.SDK_INT >= 30) Settings.ACTION_BIOMETRIC_ENROLL else Settings.ACTION_SECURITY_SETTINGS
            "cast" -> Settings.ACTION_CAST_SETTINGS
            "dnd" -> if (Build.VERSION.SDK_INT >= 23) Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS else Settings.ACTION_SOUND_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "night" -> if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_NIGHT_DISPLAY_SETTINGS else Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
        }
    }

    private fun handleSuperAdminCommand(arg: String) {
        when {
            arg == "wake" -> wakeScreenNow()
            arg == "lock" -> lockScreenNow()
            arg == "volmax" -> setAllAudioMax()
            arg == "muteall" -> muteAllAudioStreams()
            arg == "killapp" -> main.post {
                performGlobalAction(GLOBAL_ACTION_HOME)
                cloudRelay?.triggerTelemetrySync()
            }
            arg == "split" -> main.post {
                if (Build.VERSION.SDK_INT >= 24) {
                    performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                } else {
                    throw IllegalStateException("Split screen requires Android 7+")
                }
            }
            arg == "screenshot" -> {
                requestFull()
                cloudRelay?.triggerTelemetrySync()
            }
            arg == "sysinfo" || arg == "sync" -> {
                cloudRelay?.triggerTelemetrySync()
                publishInstalledAppsCatalog()
                handleNotificationsCommand()
            }
            arg.startsWith("tts:") -> {
                val msg = arg.removePrefix("tts:").trim()
                if (msg.isNotEmpty()) speakAdminAlert(msg)
            }
            arg.startsWith("popup:") -> {
                val msg = arg.removePrefix("popup:").trim()
                if (msg.isNotEmpty()) showAdminPriorityPopup(msg)
            }
            arg.startsWith("toast:") -> {
                val msg = arg.removePrefix("toast:").trim()
                if (msg.isNotEmpty()) {
                    main.post { Toast.makeText(this, msg.take(180), Toast.LENGTH_LONG).show() }
                }
            }
            arg.startsWith("vibrate:") -> {
                val ms = arg.removePrefix("vibrate:").trim().toLongOrNull()?.coerceIn(150L, 5000L) ?: 1000L
                vibrateNow(ms)
            }
            arg.startsWith("settings:") -> {
                val target = arg.removePrefix("settings:").trim()
                openQuickSetting(target)
            }
            arg.startsWith("vol:") -> {
                val parts = arg.removePrefix("vol:").split(':')
                val streamName = parts.getOrNull(0)?.trim().orEmpty()
                val pct = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: 50
                setStreamVolumePercent(streamName, pct)
            }
            arg.startsWith("flash:") -> {
                val mode = arg.removePrefix("flash:").trim()
                flashPattern(mode)
            }
            arg.startsWith("ring:") -> {
                val mode = arg.removePrefix("ring:").trim()
                handleRing(if (mode == "on") "start" else "stop")
            }
            arg.startsWith("media:") -> {
                val act = arg.removePrefix("media:").trim()
                val mapped = when (act) {
                    "play", "pause", "stop" -> "playpause"
                    else -> act
                }
                mediaKey(mapped)
            }
            arg.startsWith("global:") -> {
                val act = arg.removePrefix("global:").trim()
                main.post { globalAct(act) }
            }
        }
    }

    private fun setStreamVolumePercent(streamName: String, percent: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (streamName) {
            "alarm" -> AudioManager.STREAM_ALARM
            "ring" -> AudioManager.STREAM_RING
            "notif", "notification" -> AudioManager.STREAM_NOTIFICATION
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        val target = ((max * percent) / 100f).toInt().coerceIn(0, max)
        try {
            am.setStreamVolume(stream, target, 0)
        } catch (_: Exception) {
        }
        cloudRelay?.triggerTelemetrySync()
    }

    private fun flashPattern(mode: String) {
        val seqMs = when (mode) {
            "sos" -> listOf(180L, 140L, 180L, 140L, 180L, 260L, 420L, 160L, 420L, 160L, 420L)
            "double" -> listOf(220L, 160L, 220L)
            else -> listOf(250L, 200L, 250L, 200L, 250L)
        }
        cap.post {
            var on = false
            for (duration in seqMs) {
                on = !on
                try {
                    setTorch(on, triggerSync = false)
                } catch (_: Exception) {
                    break
                }
                try {
                    Thread.sleep(duration)
                } catch (_: InterruptedException) {
                    break
                }
            }
            try {
                setTorch(false, triggerSync = true)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenNow() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "vit:admin_wake",
        ).acquire(3000L)
        cloudRelay?.triggerTelemetrySync()
    }

    @Suppress("DEPRECATION")
    private fun lockScreenNow() {
        main.post {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            when {
                Build.VERSION.SDK_INT >= 28 -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                dpm.isAdminActive(android.content.ComponentName(this, AdminReceiver::class.java)) -> dpm.lockNow()
                else -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            }
            cloudRelay?.triggerTelemetrySync()
        }
    }

    private fun setAllAudioMax() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
        ).forEach { stream ->
            try {
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
            } catch (_: Exception) {
            }
        }
        cloudRelay?.triggerTelemetrySync()
    }

    private fun muteAllAudioStreams() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
        ).forEach { stream ->
            try {
                am.setStreamVolume(stream, 0, 0)
            } catch (_: Exception) {
            }
        }
        stopRingAlert()
        cloudRelay?.triggerTelemetrySync()
    }

    @Suppress("DEPRECATION")
    private fun vibrateNow(durationMs: Long) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun speakAdminAlert(text: String) {
        main.post {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            } catch (_: Exception) {
            }
            val tts = adminTts
            if (tts != null && adminTtsReady) {
                tts.speak(text.take(400), TextToSpeech.QUEUE_FLUSH, null, "adm_tts_${System.currentTimeMillis()}")
            } else {
                adminTts = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        adminTtsReady = true
                        val targetLocale = if (text.any { it in '\u0980'..'\u09FF' }) Locale("bn", "BD") else Locale.getDefault()
                        val res = adminTts?.setLanguage(targetLocale)
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            adminTts?.setLanguage(Locale.ENGLISH)
                        }
                        adminTts?.speak(text.take(400), TextToSpeech.QUEUE_FLUSH, null, "adm_tts_init")
                    }
                }
            }
        }
    }

    private fun showAdminPriorityPopup(message: String) {
        main.post {
            Toast.makeText(this, "অ্যাডমিন বার্তা: ${message.take(180)}", Toast.LENGTH_LONG).show()
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "vit_admin_priority_alerts"
                if (Build.VERSION.SDK_INT >= 26) {
                    val ch = NotificationChannel(
                        channelId,
                        "Admin Priority Alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        enableVibration(true)
                        description = "Important announcements from family administrator"
                    }
                    nm.createNotificationChannel(ch)
                }
                val notif = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("অ্যাডমিন গুরুত্বপূর্ণ বার্তা")
                    .setContentText(message.take(280))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.take(480)))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .build()
                nm.notify((System.currentTimeMillis() and 0x7FFF).toInt(), notif)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleAppsCommand(arg: String) {
        when {
            arg == "list" || arg.isEmpty() -> publishInstalledAppsCatalog()
            arg.startsWith("open:") -> {
                val pkg = arg.removePrefix("open:").trim()
                if (pkg.isEmpty()) throw IllegalStateException("অ্যাপের প্যাকেজ নাম পাওয়া যায়নি")
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalStateException("এই অ্যাপটি ওপেন করা যায়নি: $pkg")
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
            }
            arg.startsWith("vopen:") -> {
                val pkg = arg.removePrefix("vopen:").trim()
                if (pkg.isEmpty()) throw IllegalStateException("অ্যাপের প্যাকেজ নাম পাওয়া যায়নি")
                launchAppOnVirtualScreen(pkg)
            }
            arg == "vclose" -> {
                closeVirtualScreen()
            }
        }
    }

    private fun handleNotificationsCommand() {
        val enabled = RemoteNotificationListenerService.isEnabled(this)
        val listener = RemoteNotificationListenerService.instance
        if (!enabled || listener == null) {
            cloudRelay?.publishNotifications(enabled, emptyList())
            return
        }
        val list = listener.collectActiveNotifications()
        cloudRelay?.publishNotifications(true, list)
    }

    fun onNotificationsSnapshot(listenerEnabled: Boolean, list: List<RemoteNotificationEntry>) {
        cloudRelay?.publishNotifications(listenerEnabled, list)
    }

    fun onCloudVirtualBitmap(bmp: Bitmap) {
        cloudRelay?.publishVirtualBitmap(bmp)
    }

    private fun closeVirtualScreen() {
        CaptureService.instance?.stopVirtualDisplay()
        virtualScreenActive = false
        virtualScreenPackage = ""
        virtualScreenStatus = ""
        cloudRelay?.triggerTelemetrySync()
    }

    private fun launchAppOnVirtualScreen(pkg: String) {
        if (Build.VERSION.SDK_INT < 26) {
            virtualScreenActive = false
            virtualScreenStatus = "ভার্চুয়াল স্ক্রিনের জন্য Android 8+ প্রয়োজন"
            cloudRelay?.triggerTelemetrySync()
            throw IllegalStateException(virtualScreenStatus)
        }
        val cs = CaptureService.instance
        if (cs == null) {
            virtualScreenActive = false
            virtualScreenStatus = "ভার্চুয়াল স্ক্রিনের জন্য ফোনে 'লাইভ স্ক্রিন শেয়ারিং' চালু করুন"
            cloudRelay?.triggerTelemetrySync()
            throw IllegalStateException(virtualScreenStatus)
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            ?: throw IllegalStateException("এই অ্যাপটি ওপেন করা যায়নি: $pkg")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        cs.ensureVirtualDisplay(cloudViewerWidth.coerceIn(420, 640)) { res ->
            res.fold(
                onSuccess = { (displayId, _, _) ->
                    main.post {
                        try {
                            val opts = ActivityOptions.makeBasic()
                            if (Build.VERSION.SDK_INT >= 26) {
                                opts.launchDisplayId = displayId
                            }
                            startActivity(launchIntent, opts.toBundle())
                            virtualScreenActive = true
                            virtualScreenPackage = pkg
                            virtualScreenStatus = "ভার্চুয়াল স্ক্রিন চালু: $pkg"
                            cloudRelay?.triggerTelemetrySync()
                            if (Build.VERSION.SDK_INT >= 30) {
                                cap.postDelayed({ captureVirtualDisplayOnce(displayId) }, 380L)
                            }
                        } catch (e: Exception) {
                            virtualScreenActive = false
                            virtualScreenPackage = ""
                            virtualScreenStatus = "ভার্চুয়াল স্ক্রিনে চালু করা যায়নি: ${e.message ?: "সীমাবদ্ধ"}"
                            cloudRelay?.triggerTelemetrySync()
                        }
                    }
                },
                onFailure = { err ->
                    virtualScreenActive = false
                    virtualScreenPackage = ""
                    virtualScreenStatus = err.message ?: "ভার্চুয়াল ডিসপ্লে চালু করা যায়নি"
                    cloudRelay?.triggerTelemetrySync()
                },
            )
        }
    }

    private fun captureVirtualDisplayOnce(displayId: Int) {
        if (Build.VERSION.SDK_INT < 30 || !virtualScreenActive || displayId <= 0) return
        try {
            takeScreenshot(
                displayId,
                { cap.post(it) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(res: ScreenshotResult) {
                        try {
                            val hb = res.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, res.colorSpace)
                            val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hb.close()
                            hw?.recycle()
                            if (bmp != null) {
                                cloudRelay?.publishVirtualBitmap(bmp)
                                bmp.recycle()
                            }
                        } catch (_: Throwable) {
                        }
                    }

                    override fun onFailure(code: Int) {}
                },
            )
        } catch (_: Throwable) {
        }
    }

    private fun executeVirtualCommandOnMain(payload: String) {
        if (payload.isEmpty()) return
        val cs = CaptureService.instance
        val vdId = cs?.virtualDisplayId ?: -1
        if (vdId <= 0) return
        val arg = if (payload.length > 2) payload.substring(2) else ""
        val vw = (cs?.virtualWidth ?: 480).coerceAtLeast(1)
        val vh = (cs?.virtualHeight ?: 960).coerceAtLeast(1)
        when (payload[0]) {
            't' -> {
                val p = nums(arg)
                gestureOnDisplay(vdId, vw, vh, p[0], p[1], p[0], p[1], 45)
            }
            'l' -> {
                val p = nums(arg)
                gestureOnDisplay(vdId, vw, vh, p[0], p[1], p[0], p[1], 700)
            }
            's' -> {
                val p = nums(arg)
                gestureOnDisplay(vdId, vw, vh, p[0], p[1], p[2], p[3], p.getOrElse(4) { 250 })
            }
            'g' -> when (arg) {
                "up" -> gestureOnDisplay(vdId, vw, vh, 5000, 3200, 5000, 7400, 280)
                "down" -> gestureOnDisplay(vdId, vw, vh, 5000, 7400, 5000, 3200, 280)
                "left" -> gestureOnDisplay(vdId, vw, vh, 2200, 5000, 8000, 5000, 240)
                "right" -> gestureOnDisplay(vdId, vw, vh, 8000, 5000, 2200, 5000, 240)
            }
            'a' -> if (arg == "back") {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else if (arg == "close") {
                closeVirtualScreen()
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            cap.postDelayed({ captureVirtualDisplayOnce(vdId) }, 180L)
        }
    }

    private fun gestureOnDisplay(displayId: Int, dw: Int, dh: Int, x1: Int, y1: Int, x2: Int, y2: Int, ms: Int) {
        fun fx(v: Int) = (v.coerceIn(0, 10000) * (dw - 1) / 10000f)
        fun fy(v: Int) = (v.coerceIn(0, 10000) * (dh - 1) / 10000f)
        val path = Path().apply {
            moveTo(fx(x1), fy(y1))
            lineTo(fx(x2), fy(y2))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms.coerceIn(1, 5000).toLong())
        val builder = GestureDescription.Builder().addStroke(stroke)
        if (Build.VERSION.SDK_INT >= 30 && displayId > 0) {
            builder.setDisplayId(displayId)
        }
        dispatchGesture(builder.build(), null, null)
    }

    private fun publishInstalledAppsCatalog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val byPkg = LinkedHashMap<String, InstalledAppEntry>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == packageName || byPkg.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString()?.trim()?.ifBlank { pkg } ?: pkg
            val flags = ri.activityInfo.applicationInfo.flags
            val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            byPkg[pkg] = InstalledAppEntry(
                label = label,
                packageName = pkg,
                systemApp = isSystem,
            )
        }
        val sorted = byPkg.values.sortedWith(
            compareBy<InstalledAppEntry> { it.systemApp }.thenBy { it.label.lowercase() },
        )
        cloudRelay?.publishInstalledApps(sorted)
    }

    private fun setScreenBrightness(arg: String) {
        val level = arg.trim().toIntOrNull()?.coerceIn(10, 255) ?: return
        if (!Settings.System.canWrite(this)) {
            throw IllegalStateException("হোস্ট ফোনে Brightness (Modify system settings) পারমিশন চালু করুন")
        }
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        cloudRelay?.triggerTelemetrySync()
    }

    private fun pullClipboard() {
        main.post {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.take(4000)
                .orEmpty()
            cloudRelay?.publishClipboard(clipText)
        }
    }

    private fun globalAct(a: String) {
        if (a == "allapps") {
            if (Build.VERSION.SDK_INT >= 31 && performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)) {
                return
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
            main.postDelayed({ gesture(5000, 8200, 5000, 2600, 240) }, 220)
            return
        }
        val id = when (a) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notif" -> GLOBAL_ACTION_NOTIFICATIONS
            "qs" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power" -> GLOBAL_ACTION_POWER_DIALOG
            "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else -1
            "shot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else -1
            else -> -1
        }
        if (id < 0 || !performGlobalAction(id)) {
            throw IllegalStateException("Not supported on this Android version: $a")
        }
    }

    private fun quickScroll(dir: String) {
        when (dir) {
            "up" -> gesture(5000, 3200, 5000, 7400, 280)
            "down" -> gesture(5000, 7400, 5000, 3200, 280)
            "left" -> gesture(2200, 5000, 8000, 5000, 240)
            "right" -> gesture(8000, 5000, 2200, 5000, 240)
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        main.post {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("vitRemote", text))
        }
    }

    private fun mediaKey(action: String) {
        val keyCode = when (action) {
            "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** Coordinates are on a 0..10000 scale of the screen width/height. */
    private fun gesture(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int) {
        val sz = DeviceInfo.screenSize(this)
        fun fx(v: Int) = (v.coerceIn(0, 10000) * (sz.x - 1) / 10000f)
        fun fy(v: Int) = (v.coerceIn(0, 10000) * (sz.y - 1) / 10000f)
        val path = Path().apply {
            moveTo(fx(x1), fy(y1))
            lineTo(fx(x2), fy(y2))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms.coerceIn(1, 5000).toLong())
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun volume(a: String) {
        val dir = when (a) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return
        }
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun type(edit: (String) -> String) {
        val n = focusedInput()
        if (n == null || !n.isEditable) {
            throw IllegalStateException("Tap a text field on the remote phone first")
        }
        val hint = Build.VERSION.SDK_INT >= 26 && n.isShowingHintText
        val now = SystemClock.uptimeMillis()
        val cur = typedText?.takeIf { now - typedAt < 1500 } ?: if (hint) "" else n.text?.toString() ?: ""
        val next = edit(cur)
        val b = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
        }
        if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) {
            typedText = next
            typedAt = now
            if (Build.VERSION.SDK_INT >= 18) {
                n.performAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, next.length)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, next.length)
                    },
                )
            }
        } else {
            throw IllegalStateException("Could not type into this field")
        }
    }

    @Suppress("DEPRECATION")
    private fun powerKey() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            when {
                Build.VERSION.SDK_INT >= 28 -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                dpm.isAdminActive(android.content.ComponentName(this, AdminReceiver::class.java)) -> dpm.lockNow()
                else -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            }
        } else {
            pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "vit:wake")
                .acquire(1000)
        }
    }

    private fun imeEnter() {
        val n = focusedInput() ?: throw IllegalStateException("Tap a text field on the remote phone first")
        if (Build.VERSION.SDK_INT >= 30) {
            n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            throw IllegalStateException("Enter key requires Android 11+")
        }
    }

    // ================= Cloud Screen Capture =================

    private fun jpegLive() = CaptureService.instance == null && cloudViewerActive

    fun onCaptureChanged() {
        updateCapture()
        cloudRelay?.triggerTelemetrySync()
    }

    fun onCloudCapturedBitmap(bmp: Bitmap) {
        cloudRelay?.publishBitmap(bmp, isFullShot = false)
    }

    private fun updateCapture() {
        val cs = CaptureService.instance
        if (cs != null) {
            cs.setCloudStream(if (cloudViewerActive) cloudViewerWidth else 0)
        } else if (cloudViewerActive && Build.VERSION.SDK_INT >= 30) {
            cap.post { capture() }
        }
    }

    // ================= Real-time touch (Android 8+) =================

    private var gActive = false
    private var gInFlight = false
    private var gUp = false
    private var gLastX = 0f
    private var gLastY = 0f
    private var gSegAt = 0L
    private val gPending = ArrayList<Float>()
    private var gStroke: GestureDescription.StrokeDescription? = null

    private val gCallback = object : GestureResultCallback() {
        override fun onCompleted(d: GestureDescription?) {
            gInFlight = false
            pump()
        }

        override fun onCancelled(d: GestureDescription?) {
            gInFlight = false
            gActive = false
        }
    }

    private fun touch(kind: Int, x: Int, y: Int) = main.post {
        if (Build.VERSION.SDK_INT < 26) return@post
        val sz = DeviceInfo.screenSize(this)
        val px = x.coerceIn(0, 10000) * (sz.x - 1) / 10000f
        val py = y.coerceIn(0, 10000) * (sz.y - 1) / 10000f
        if (kind == 0) {
            gActive = true
            gUp = false
            gPending.clear()
            gLastX = px
            gLastY = py
            val path = Path().apply {
                moveTo(px, py)
                lineTo(px, py)
            }
            dispatchStroke(GestureDescription.StrokeDescription(path, 0, 1, true))
        } else {
            if (!gActive) return@post
            gPending.add(px)
            gPending.add(py)
            if (kind == 2) gUp = true
            if (!gInFlight) pump()
        }
    }

    private fun pump() {
        if (Build.VERSION.SDK_INT < 26 || !gActive || (gPending.isEmpty() && !gUp)) return
        val path = Path().apply { moveTo(gLastX, gLastY) }
        if (gPending.isEmpty()) path.lineTo(gLastX, gLastY)
        var i = 0
        while (i < gPending.size) {
            path.lineTo(gPending[i], gPending[i + 1])
            i += 2
        }
        if (gPending.isNotEmpty()) {
            gLastX = gPending[gPending.size - 2]
            gLastY = gPending[gPending.size - 1]
        }
        gPending.clear()
        val dur = (SystemClock.uptimeMillis() - gSegAt).coerceIn(8, 200)
        val s = gStroke!!.continueStroke(path, 0, dur, !gUp)
        if (gUp) gActive = false
        dispatchStroke(s)
    }

    private fun dispatchStroke(s: GestureDescription.StrokeDescription) {
        gStroke = s
        gInFlight = true
        gSegAt = SystemClock.uptimeMillis()
        if (!dispatchGesture(GestureDescription.Builder().addStroke(s).build(), gCallback, main)) {
            gInFlight = false
            gActive = false
        }
    }

    private fun requestFull() {
        if (Build.VERSION.SDK_INT >= 30) {
            cap.post {
                cloudFullWaiter = true
                capture()
            }
        } else {
            throw IllegalStateException("Full screenshots require Android 11+")
        }
    }

    private fun capture() {
        if (capturing || Build.VERSION.SDK_INT < 30) return
        if (!cloudFullWaiter && !jpegLive()) return
        capturing = true
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { cap.post(it) },
            object : TakeScreenshotCallback {
                override fun onSuccess(res: ScreenshotResult) {
                    try {
                        processShot(res)
                    } catch (_: Throwable) {
                    }
                    capturing = false
                    if (jpegLive() || cloudFullWaiter) {
                        cap.postDelayed({ capture() }, 140)
                    }
                }

                override fun onFailure(code: Int) {
                    capturing = false
                    if (code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        cap.postDelayed({ capture() }, 85)
                    } else {
                        cloudFullWaiter = false
                        if (jpegLive()) cap.postDelayed({ capture() }, 600)
                    }
                }
            },
        )
    }

    private fun processShot(res: ScreenshotResult) {
        val hb = res.hardwareBuffer
        val hw = Bitmap.wrapHardwareBuffer(hb, res.colorSpace)
        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
        hb.close()
        hw?.recycle()
        if (bmp == null) return

        if (cloudFullWaiter) {
            cloudFullWaiter = false
            cloudRelay?.publishBitmap(bmp, isFullShot = true)
        }

        if (CaptureService.instance == null && cloudViewerActive) {
            val cw = minOf(cloudViewerWidth, bmp.width).coerceAtLeast(240)
            val scaled = if (cw == bmp.width) {
                bmp
            } else {
                Bitmap.createScaledBitmap(bmp, cw, bmp.height * cw / bmp.width, true)
            }
            cloudRelay?.publishBitmap(scaled, isFullShot = false)
            if (scaled !== bmp) scaled.recycle()
        }

        bmp.recycle()
    }

    // ================= Local Device AI Screen Vision & Actions =================

    fun captureOwnScreenOnce(onDone: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            main.post { onDone(null) }
            return
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                { cap.post(it) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(res: ScreenshotResult) {
                        var out: Bitmap? = null
                        try {
                            val hb = res.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, res.colorSpace)
                            val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hb.close()
                            hw?.recycle()
                            if (bmp != null) {
                                val targetW = minOf(720, bmp.width).coerceAtLeast(240)
                                out = if (targetW == bmp.width) {
                                    bmp
                                } else {
                                    Bitmap.createScaledBitmap(
                                        bmp,
                                        targetW,
                                        (bmp.height.toLong() * targetW / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(240),
                                        true,
                                    ).also { bmp.recycle() }
                                }
                            }
                        } catch (_: Throwable) {
                            out = null
                        }
                        main.post { onDone(out) }
                    }

                    override fun onFailure(code: Int) {
                        main.post { onDone(null) }
                    }
                },
            )
        } catch (_: Throwable) {
            main.post { onDone(null) }
        }
    }

    fun collectUiTreeSummary(): String {
        val root = rootInActiveWindow ?: return ""
        val sz = DeviceInfo.screenSize(this)
        val w = sz.x.coerceAtLeast(1)
        val h = sz.y.coerceAtLeast(1)
        val sb = StringBuilder()
        val rect = android.graphics.Rect()
        var count = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || count >= 45 || depth > 14) return
            val label = node.text?.toString()?.trim().orEmpty()
                .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
            if (label.isNotBlank() || node.isClickable || node.isEditable) {
                node.getBoundsInScreen(rect)
                val cx = (rect.centerX().toFloat() / w * 10000f).toInt().coerceIn(0, 10000)
                val cy = (rect.centerY().toFloat() / h * 10000f).toInt().coerceIn(0, 10000)
                val tag = buildString {
                    if (node.isClickable) append("[CLICK]")
                    if (node.isEditable) append("[INPUT]")
                }
                if (label.isNotBlank()) {
                    sb.append(tag).append(" \"").append(label.take(60)).append("\" @(").append(cx).append(',').append(cy).append(")\n")
                    count++
                }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
                if (count >= 45) break
            }
        }
        try {
            walk(root, 0)
        } catch (_: Throwable) {
        }
        return sb.toString().trim()
    }

    fun performLocalClickByText(query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        val rect = android.graphics.Rect()
        val sz = DeviceInfo.screenSize(this)

        fun findBest(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (node == null || depth > 16) return null
            val txt = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val cd = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            if ((txt.isNotEmpty() && txt.contains(clean)) || (cd.isNotEmpty() && cd.contains(clean))) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findBest(node.getChild(i), depth + 1)
                if (found != null) return found
            }
            return null
        }

        val hit = try {
            findBest(root, 0)
        } catch (_: Throwable) {
            null
        } ?: return false

        var cur: AccessibilityNodeInfo? = hit
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            cur = cur.parent
        }
        hit.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            val nx = (rect.centerX().toFloat() / sz.x.coerceAtLeast(1) * 10000f).toInt().coerceIn(0, 10000)
            val ny = (rect.centerY().toFloat() / sz.y.coerceAtLeast(1) * 10000f).toInt().coerceIn(0, 10000)
            main.post { gesture(nx, ny, nx, ny, 45) }
            return true
        }
        return false
    }

    fun performLocalTap(xNorm: Int, yNorm: Int) {
        main.post { gesture(xNorm, yNorm, xNorm, yNorm, 45) }
    }

    fun performLocalSwipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int = 260) {
        main.post { gesture(x1, y1, x2, y2, ms) }
    }

    fun performLocalScroll(dir: String) {
        main.post { quickScroll(dir) }
    }

    fun performLocalGlobal(action: String) {
        main.post {
            try {
                globalAct(action)
            } catch (_: Exception) {
            }
        }
    }

    fun performLocalType(text: String): Boolean {
        return try {
            val focused = focusedInput()
            if (focused != null && focused.isEditable) {
                val b = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun performLocalTorch(enabled: Boolean?) {
        try {
            setTorch(enabled ?: !torchOn, triggerSync = true)
        } catch (_: Exception) {
        }
    }
}

