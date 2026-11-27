package com.example.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.example.Prefs
import com.example.RemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class VoiceEngineState(
    val aiSystemOn: Boolean = false,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val partialText: String = "",
    val lastCommand: String = "",
    val lastReply: String = "",
    val errorText: String? = null,
)

class VoiceAssistantController private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: VoiceAssistantController? = null

        fun get(context: Context): VoiceAssistantController =
            instance ?: synchronized(this) {
                instance ?: VoiceAssistantController(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VoiceEngineState(aiSystemOn = prefs.aiSystemEnabled))
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeCallback: ((String) -> Unit)? = null

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bn = Locale("bn", "BD")
                val res = tts?.setLanguage(bn)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        mainHandler.post {
            ensureTts()
            if (ttsReady) {
                tts?.speak(text.take(360), TextToSpeech.QUEUE_FLUSH, null, "vit_ai_${System.currentTimeMillis()}")
            }
        }
    }

    fun syncMasterAiEnabled(enabled: Boolean) {
        prefs.aiSystemEnabled = enabled
        _state.value = _state.value.copy(
            aiSystemOn = enabled,
            errorText = if (!enabled) null else _state.value.errorText,
        )
        if (!enabled) {
            stopListening()
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
        }
    }

    fun stopListening() {
        activeCallback = null
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
            _state.value = _state.value.copy(isListening = false, partialText = "")
        }
    }

    fun startVoiceSession(onTranscript: (String) -> Unit) {
        if (!prefs.aiSystemEnabled) {
            val msg = "হোমপেজ থেকে 'AI সিস্টেম চালু করুন' বাটনটি অন করুন"
            _state.value = _state.value.copy(aiSystemOn = false, isListening = false, errorText = msg)
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = _state.value.copy(errorText = "মাইক্রোফোন পারমিশন দিন")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(errorText = "এই ডিভাইসে স্পিচ রিকগনিশন সাপোর্ট পাওয়া যায়নি")
            return
        }

        mainHandler.post {
            try {
                recognizer?.destroy()
                recognizer = null
                activeCallback = onTranscript
                val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer = sr
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = _state.value.copy(
                            aiSystemOn = prefs.aiSystemEnabled,
                            isListening = true,
                            partialText = "শুনছি...",
                            errorText = null,
                        )
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _state.value = _state.value.copy(isListening = false)
                    }

                    override fun onError(error: Int) {
                        _state.value = _state.value.copy(
                            isListening = false,
                            partialText = "",
                            errorText = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                "কোনো কথা শোনা যায়নি, আবার বলুন"
                            } else {
                                "ভয়েস এরর ($error)"
                            },
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        _state.value = _state.value.copy(isListening = false, partialText = "")
                        if (spoken.isNotEmpty() && prefs.aiSystemEnabled) {
                            _state.value = _state.value.copy(lastCommand = spoken, errorText = null)
                            activeCallback?.invoke(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val txt = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (txt.isNotEmpty()) {
                            _state.value = _state.value.copy(partialText = txt)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                sr.startListening(intent)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isListening = false, errorText = e.message ?: "মাইক্রোফোন চালু হয়নি")
            }
        }
    }

    fun runOwnDeviceVoiceAssistant() {
        startVoiceSession { command ->
            executeOwnDevicePrompt(command)
        }
    }

    fun executeOwnDevicePrompt(command: String) {
        if (!prefs.aiSystemEnabled) {
            _state.value = _state.value.copy(errorText = "AI সিস্টেম বন্ধ আছে")
            return
        }
        val key = prefs.geminiApiKey
        if (key.isBlank()) {
            _state.value = _state.value.copy(errorText = "Google AI Studio Key সেট করা নেই")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isThinking = true, lastCommand = command, errorText = null)
            val svc = RemoteService.instance
            val screenBmp = captureOwnScreenSuspend(svc)
            val uiSummary = svc?.collectUiTreeSummary().orEmpty()
            val apps = DeviceActionExecutor.queryLocalInstalledApps(appContext)
            val planRes = GeminiAssistantClient.decideScreenAction(
                apiKey = key,
                modelName = prefs.geminiModel,
                voiceCommand = command,
                screenBitmap = screenBmp,
                installedApps = apps,
                uiSummary = uiSummary,
                modeLabel = "Own Device Local Control",
                onModelResolved = { resolved -> prefs.geminiModel = resolved },
            )
            screenBmp?.recycle()
            planRes.fold(
                onSuccess = { plan ->
                    val feedback = DeviceActionExecutor.executeOwnDeviceAction(appContext, plan)
                    _state.value = _state.value.copy(isThinking = false, lastReply = feedback, errorText = null)
                    speak(feedback)
                },
                onFailure = { err ->
                    val msg = err.message ?: "AI বুঝতে পারেনি"
                    _state.value = _state.value.copy(isThinking = false, errorText = msg)
                    speak(msg)
                },
            )
        }
    }

    fun setThinking(thinking: Boolean, reply: String? = null, error: String? = null) {
        _state.value = _state.value.copy(
            isThinking = thinking,
            lastReply = reply ?: _state.value.lastReply,
            errorText = error,
        )
    }

    private suspend fun captureOwnScreenSuspend(svc: RemoteService?): Bitmap? {
        if (svc == null) return null
        return suspendCancellableCoroutine { cont ->
            svc.captureOwnScreenOnce { bmp ->
                if (cont.isActive) cont.resume(bmp) else bmp?.recycle()
            }
        }
    }
}
