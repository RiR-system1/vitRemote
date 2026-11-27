package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock

/**
 * Captures compressed screen frames via MediaProjection -> VirtualDisplay -> ImageReader
 * for global Cloud viewers.
 */
class CaptureService : Service() {
    companion object {
        @Volatile var instance: CaptureService? = null
            private set

        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, CaptureService::class.java).putExtra("code", code).putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CaptureService::class.java))
        }
    }

    private val thread = HandlerThread("vit-cap-cloud").apply { start() }
    private val h = Handler(thread.looper)
    private var projection: MediaProjection? = null

    private var cloudDisplay: VirtualDisplay? = null
    private var cloudReader: ImageReader? = null
    private var cloudWantW = 0
    private var cloudCurKey = ""
    private var lastCloudCaptureAt = 0L

    private var appVirtualDisplay: VirtualDisplay? = null
    private var appVirtualReader: ImageReader? = null
    @Volatile var virtualDisplayId: Int = -1
        private set
    @Volatile var virtualWidth: Int = 0
        private set
    @Volatile var virtualHeight: Int = 0
        private set
    private var lastVirtualCaptureAt = 0L
    private var captureWakeLock: PowerManager.WakeLock? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) {
            if (id == 0) h.post { reconfigureCloud() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null || projection != null) return START_NOT_STICKY
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = try {
            mpm.getMediaProjection(code, data)
        } catch (_: Exception) {
            null
        }
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                h.post {
                    releaseCloud()
                    releaseAppVirtualDisplay()
                }
                stopSelf()
            }
        }, h)
        projection = p
        instance = this
        acquireCaptureWakeLock()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, h)
        RemoteService.instance?.onCaptureChanged()
        return START_NOT_STICKY
    }

    private fun acquireCaptureWakeLock() {
        if (captureWakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        captureWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vitRemote:CaptureKeepAlive").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCaptureWakeLock() {
        try {
            if (captureWakeLock?.isHeld == true) captureWakeLock?.release()
        } catch (_: Exception) {
        }
        captureWakeLock = null
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("cap", "Cloud screen sharing", NotificationManager.IMPORTANCE_LOW),
            )
            Notification.Builder(this, "cap")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val nBuilder = b.setSmallIcon(R.drawable.ic_cast)
            .setContentTitle("vitRemote Cloud")
            .setContentText("Cloud live stream & keep-alive active")
            .setContentIntent(pi)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 31) {
            nBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val n = nBuilder.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        instance = null
        releaseCaptureWakeLock()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        h.post {
            releaseCloud()
            releaseAppVirtualDisplay()
            projection?.stop()
            projection = null
        }
        thread.quitSafely()
        RemoteService.instance?.onCaptureChanged()
        super.onDestroy()
    }

    /** width 0 stops the cloud frame stream. */
    fun setCloudStream(w: Int) = h.post {
        cloudWantW = w
        reconfigureCloud()
    }

    private fun releaseCloud() {
        cloudDisplay?.release()
        cloudDisplay = null
        cloudReader?.close()
        cloudReader = null
        cloudCurKey = ""
    }

    private fun reconfigureCloud() {
        val p = projection ?: return
        if (cloudWantW <= 0) {
            releaseCloud()
            return
        }
        val sz = DeviceInfo.screenSize(this)
        val tw = minOf(cloudWantW, sz.x).coerceIn(240, 1080)
        val th = (sz.y.toLong() * tw / sz.x.coerceAtLeast(1)).toInt().coerceAtLeast(240)
        val key = "$tw:$th"
        if (key == cloudCurKey && cloudReader != null) return

        releaseCloud()
        val reader = ImageReader.newInstance(tw, th, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val img = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.uptimeMillis()
                if (now - lastCloudCaptureAt >= 95L) {
                    lastCloudCaptureAt = now
                    val plane = img.planes[0]
                    val rowStride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val rowPadding = rowStride - pixelStride * tw
                    val rawBmp = Bitmap.createBitmap(
                        tw + rowPadding / pixelStride,
                        th,
                        Bitmap.Config.ARGB_8888,
                    )
                    rawBmp.copyPixelsFromBuffer(plane.buffer)
                    val cropped = if (rowPadding == 0) {
                        rawBmp
                    } else {
                        Bitmap.createBitmap(rawBmp, 0, 0, tw, th).also { rawBmp.recycle() }
                    }
                    RemoteService.instance?.onCloudCapturedBitmap(cropped)
                    cropped.recycle()
                }
            } catch (_: Throwable) {
            } finally {
                img.close()
            }
        }, h)

        cloudReader = reader
        cloudCurKey = key
        cloudDisplay = p.createVirtualDisplay(
            "vitRemote-cloud",
            tw,
            th,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            h,
        )
    }

    private fun releaseAppVirtualDisplay() {
        appVirtualDisplay?.release()
        appVirtualDisplay = null
        appVirtualReader?.close()
        appVirtualReader = null
        virtualDisplayId = -1
        virtualWidth = 0
        virtualHeight = 0
    }

    fun stopVirtualDisplay() {
        h.post { releaseAppVirtualDisplay() }
    }

    fun ensureVirtualDisplay(
        wantWidth: Int,
        onResult: (Result<Triple<Int, Int, Int>>) -> Unit,
    ) {
        h.post {
            val p = projection
            if (p == null) {
                onResult(Result.failure(IllegalStateException("লাইভ স্ক্রিন শেয়ারিং চালু নেই")))
                return@post
            }
            val existingId = virtualDisplayId
            if (existingId > 0 && appVirtualDisplay != null && appVirtualReader != null) {
                onResult(Result.success(Triple(existingId, virtualWidth, virtualHeight)))
                return@post
            }

            releaseAppVirtualDisplay()
            val sz = DeviceInfo.screenSize(this)
            val vw = wantWidth.coerceIn(360, 720)
            val vh = (sz.y.toLong() * vw / sz.x.coerceAtLeast(1)).toInt().coerceIn(480, 1560)
            val reader = ImageReader.newInstance(vw, vh, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener({ r ->
                val img = try {
                    r.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastVirtualCaptureAt >= 95L) {
                        lastVirtualCaptureAt = now
                        val plane = img.planes[0]
                        val rowStride = plane.rowStride
                        val pixelStride = plane.pixelStride
                        val rowPadding = rowStride - pixelStride * vw
                        val rawBmp = Bitmap.createBitmap(
                            vw + rowPadding / pixelStride,
                            vh,
                            Bitmap.Config.ARGB_8888,
                        )
                        rawBmp.copyPixelsFromBuffer(plane.buffer)
                        val cropped = if (rowPadding == 0) {
                            rawBmp
                        } else {
                            Bitmap.createBitmap(rawBmp, 0, 0, vw, vh).also { rawBmp.recycle() }
                        }
                        RemoteService.instance?.onCloudVirtualBitmap(cropped)
                        cropped.recycle()
                    }
                } catch (_: Throwable) {
                } finally {
                    img.close()
                }
            }, h)

            val vd = try {
                p.createVirtualDisplay(
                    "vitRemote-virtual",
                    vw,
                    vh,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    reader.surface,
                    null,
                    h,
                )
            } catch (e: SecurityException) {
                reader.close()
                onResult(Result.failure(IllegalStateException("এই ডিভাইস ভার্চুয়াল ডিসপ্লে অনুমতি দেয়নি (${e.message})")))
                return@post
            } catch (e: Exception) {
                reader.close()
                onResult(Result.failure(e))
                return@post
            }

            if (vd == null) {
                reader.close()
                onResult(Result.failure(IllegalStateException("ভার্চুয়াল ডিসপ্লে তৈরি করা যায়নি")))
                return@post
            }

            appVirtualDisplay = vd
            appVirtualReader = reader
            virtualWidth = vw
            virtualHeight = vh
            virtualDisplayId = vd.display.displayId
            onResult(Result.success(Triple(virtualDisplayId, vw, vh)))
        }
    }
}
