package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.Prefs
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiOverlayService : Service() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
            val i = Intent(context, AiOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AiOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceAssistantController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var rootView: FrameLayout? = null
    private var bubbleView: FrameLayout? = null
    private var panelView: LinearLayout? = null
    private var statusView: TextView? = null
    private var replyView: TextView? = null
    private var micButton: Button? = null
    private var quickInput: EditText? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        voice = VoiceAssistantController.get(this)
        startForegroundCompat()
        setupOverlayWindow()
        observeVoiceState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    "ai_overlay",
                    "vitRemote AI ভয়েস ওভারলে",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            Notification.Builder(this, "ai_overlay")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val pi = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = builder
            .setSmallIcon(R.drawable.ic_cast)
            .setContentTitle("vitRemote AI ভয়েস")
            .setContentText("ফ্লোটিং AI লোগো ও মিনি স্ক্রিন চালু আছে")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val fgsType = if (hasMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(2, n, fgsType)
        } else if (Build.VERSION.SDK_INT >= 30) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(2, n)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayWindow() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(140)
        }
        layoutParams = params

        val container = FrameLayout(this)

        // 1) Compact Floating App Logo Bubble
        val bubble = FrameLayout(this).apply {
            val size = dp(58)
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1D4ED8"))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(6).toFloat()
            val logo = ImageView(this@AiOverlayService).apply {
                setImageResource(R.drawable.vitremote_icon_fg_1794809525202)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                contentDescription = "vitRemote AI Overlay Logo"
            }
            addView(logo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) {
                        moved = true
                    }
                    params.x = startX + dx
                    params.y = startY + dy
                    try {
                        wm.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleExpanded(true)
                    }
                    true
                }
                else -> false
            }
        }

        // 2) Compact Mini AI Screen
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val w = dp(286)
            layoutParams = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#F8FAFC"))
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
            elevation = dp(10).toFloat()
            visibility = View.GONE
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleTv = TextView(this).apply {
            text = "vitRemote AI মিনি স্ক্রিন"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val devTv = TextView(this).apply {
            text = "Rubaeat Islam Raja · অরুপ Raja"
            textSize = 11f
            setTextColor(Color.parseColor("#1D4ED8"))
        }
        titleCol.addView(titleTv)
        titleCol.addView(devTv)

        val minBtn = Button(this).apply {
            text = "—"
            textSize = 12f
            setOnClickListener { toggleExpanded(false) }
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            textSize = 12f
            setOnClickListener {
                prefs.overlayEnabled = false
                stopSelf()
            }
        }
        headerRow.addView(titleCol)
        headerRow.addView(minBtn, LinearLayout.LayoutParams(dp(42), dp(36)))
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(dp(42), dp(36)))

        val stTv = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, dp(6), 0, dp(4))
        }
        val repTv = TextView(this).apply {
            text = "ট্যাপ করে ভয়েসে বলুন (যেকোনো অ্যাপ ওপেন, কল বা স্ক্রিন প্রশ্ন)"
            textSize = 12.5f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#E2E8F0"))
            }
        }

        val inputBox = EditText(this).apply {
            hint = "বা লিখে কমান্ড দিন..."
            textSize = 12.5f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#64748B"))
            setSingleLine(true)
            setOnTouchListener { _, _ ->
                enableKeyboardFocus(true)
                false
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val talkBtn = Button(this).apply {
            text = "🎤 ভয়েস বলুন"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1D4ED8"))
            }
            setOnClickListener {
                enableKeyboardFocus(false)
                if (!prefs.aiSystemEnabled) {
                    stTv.text = "AI সিস্টেম বন্ধ আছে · হোমপেজ থেকে বাটন ১ অন করুন"
                    return@setOnClickListener
                }
                voice.runOwnDeviceVoiceAssistant()
            }
        }
        val runTxtBtn = Button(this).apply {
            text = "চলাও"
            textSize = 12f
            setTextColor(Color.parseColor("#1E293B"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#CBD5E1"))
            }
            setOnClickListener {
                val cmd = inputBox.text.toString().trim()
                enableKeyboardFocus(false)
                if (cmd.isNotEmpty()) {
                    if (!prefs.aiSystemEnabled) {
                        stTv.text = "AI সিস্টেম বন্ধ আছে · হোমপেজ থেকে অন করুন"
                    } else {
                        inputBox.setText("")
                        voice.executeOwnDevicePrompt(cmd)
                    }
                }
            }
        }

        actionRow.addView(talkBtn, LinearLayout.LayoutParams(0, dp(42), 1.4f))
        val spacer = View(this)
        actionRow.addView(spacer, LinearLayout.LayoutParams(dp(8), dp(1)))
        actionRow.addView(runTxtBtn, LinearLayout.LayoutParams(0, dp(42), 0.9f))

        panel.addView(headerRow)
        panel.addView(stTv)
        panel.addView(repTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(inputBox)
        panel.addView(actionRow)

        container.addView(bubble)
        container.addView(panel)

        rootView = container
        bubbleView = bubble
        panelView = panel
        statusView = stTv
        replyView = repTv
        micButton = talkBtn
        quickInput = inputBox

        wm.addView(container, params)
    }

    private fun enableKeyboardFocus(focusable: Boolean) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        params.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            wm.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
    }

    private fun toggleExpanded(open: Boolean) {
        expanded = open
        bubbleView?.visibility = if (open) View.GONE else View.VISIBLE
        panelView?.visibility = if (open) View.VISIBLE else View.GONE
        if (!open) enableKeyboardFocus(false)
    }

    private fun observeVoiceState() {
        serviceScope.launch {
            voice.state.collect { st ->
                val aiOn = prefs.aiSystemEnabled && st.aiSystemOn
                statusView?.text = when {
                    !aiOn -> "অবস্থা: AI সিস্টেম বন্ধ (হোমপেজে অন করুন)"
                    st.isListening -> "শুনছি: ${st.partialText}"
                    st.isThinking -> "AI স্ক্রিন দেখছে ও কাজ করছে..."
                    st.errorText != null -> "তথ্য: ${st.errorText}"
                    else -> "অবস্থা: AI সক্রিয় · কথা বলার জন্য প্রস্তুত"
                }
                micButton?.isEnabled = aiOn && !st.isThinking
                micButton?.alpha = if (aiOn) 1f else 0.55f
                if (st.lastReply.isNotBlank()) {
                    val cmdLine = if (st.lastCommand.isNotBlank()) "আপনি: ${st.lastCommand}\n" else ""
                    replyView?.text = "${cmdLine}AI: ${st.lastReply}"
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        try {
            rootView?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        rootView = null
        super.onDestroy()
    }
}
