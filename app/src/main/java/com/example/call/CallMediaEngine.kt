package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class CallMediaEngine(
    private val context: Context,
) {
    private val appCtx = context.applicationContext
    private val running = AtomicBoolean(false)
    private val seqCounter = AtomicLong(0L)

    @Volatile var muted: Boolean = false
    @Volatile var speakerOn: Boolean = true
        private set
    @Volatile var cameraEnabled: Boolean = false
    @Volatile var useFrontCamera: Boolean = true
    @Volatile private var latestVideoB64: String = ""
    @Volatile private var lastVideoCaptureAt: Long = 0L
    @Volatile private var lastPlayedSeq: Long = -1L

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    fun startAudioAndLoop(
        isVideoCall: Boolean,
        onPacketReady: (seq: Long, audioB64: String, videoB64: String, muted: Boolean, camOn: Boolean) -> Unit,
    ) {
        if (running.getAndSet(true)) return
        cameraEnabled = isVideoCall
        lastPlayedSeq = -1L
        seqCounter.set(0L)
        configureAudioRouting(speaker = isVideoCall)

        captureExecutor.execute {
            runCaptureLoop(onPacketReady)
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        speakerOn = enabled
        configureAudioRouting(enabled)
    }

    @Suppress("DEPRECATION")
    private fun configureAudioRouting(speaker: Boolean) {
        try {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                val desiredType = if (speaker) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type == desiredType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else {
                    am.isSpeakerphoneOn = speaker
                }
            } else {
                am.isSpeakerphoneOn = speaker
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing note: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun runCaptureLoop(
        onPacketReady: (seq: Long, audioB64: String, videoB64: String, muted: Boolean, camOn: Boolean) -> Unit,
    ) {
        val sampleRate = 8000
        val minRecBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(3200)

        val chunkBytes = 3200 // 200ms at 8kHz 16-bit mono
        val buffer = ByteArray(chunkBytes)

        val hasRecordPerm = ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRecordPerm) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecBuf * 2,
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.also {
                    it.startRecording()
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord init failed: ${e.message}")
            }
        }

        while (running.get()) {
            val audioB64 = if (!muted && audioRecord != null) {
                val read = try {
                    audioRecord?.read(buffer, 0, chunkBytes) ?: 0
                } catch (_: Exception) {
                    0
                }
                if (read > 0) {
                    Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                } else {
                    ""
                }
            } else {
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
                ""
            }

            val videoB64 = if (cameraEnabled) latestVideoB64 else ""
            val seq = seqCounter.incrementAndGet()
            try {
                onPacketReady(seq, audioB64, videoB64, muted, cameraEnabled)
            } catch (e: Exception) {
                Log.w(TAG, "Packet dispatch error: ${e.message}")
            }
        }
    }

    fun playPeerAudio(seq: Long, audioBase64: String) {
        if (!running.get() || audioBase64.isBlank()) return
        if (seq <= lastPlayedSeq) return
        lastPlayedSeq = seq

        playbackExecutor.execute {
            if (!running.get()) return@execute
            try {
                ensureAudioTrack()
                val pcm = Base64.decode(audioBase64, Base64.DEFAULT)
                if (pcm.isNotEmpty()) {
                    audioTrack?.write(pcm, 0, pcm.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio playback error: ${e.message}")
            }
        }
    }

    private fun ensureAudioTrack() {
        if (audioTrack != null) return
        val sampleRate = 8000
        val minPlayBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(3200)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minPlayBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also { it.play() }
    }

    @Suppress("DEPRECATION")
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frontCamera: Boolean,
    ) {
        useFrontCamera = frontCamera
        if (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val future = ProcessCameraProvider.getInstance(appCtx)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                if (!running.get() || !cameraEnabled) return@addListener

                val selector = if (frontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(240, 320))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(captureExecutor) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if (cameraEnabled && running.get() && now - lastVideoCaptureAt >= 240L) {
                            lastVideoCaptureAt = now
                            val rawBmp = image.toBitmap()
                            val rot = image.imageInfo.rotationDegrees.toFloat()
                            val rotated = if (rot != 0f) {
                                val matrix = Matrix().apply { postRotate(rot) }
                                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true).also {
                                    if (it !== rawBmp) rawBmp.recycle()
                                }
                            } else {
                                rawBmp
                            }
                            val targetW = minOf(220, rotated.width).coerceAtLeast(120)
                            val targetH = (rotated.height * targetW / rotated.width).coerceAtLeast(120)
                            val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
                            if (scaled !== rotated) rotated.recycle()

                            val baos = ByteArrayOutputStream(16 * 1024)
                            scaled.compress(Bitmap.CompressFormat.JPEG, 40, baos)
                            scaled.recycle()
                            val bytes = baos.toByteArray()
                            if (bytes.size <= 160_000) {
                                latestVideoB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame encode skipped: ${e.message}")
                    } finally {
                        image.close()
                    }
                }

                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(appCtx))
    }

    fun unbindCamera() {
        latestVideoB64 = ""
        try {
            ContextCompat.getMainExecutor(appCtx).execute {
                cameraProvider?.unbindAll()
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        running.set(false)
        cameraEnabled = false
        latestVideoB64 = ""
        unbindCamera()
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null

        try {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                am.isSpeakerphoneOn = false
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "CallMediaEngine"
    }
}
