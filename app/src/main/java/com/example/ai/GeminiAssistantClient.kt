package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.InstalledAppEntry
import com.example.security.SecureServerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AiChatTurn(
    val isUser: Boolean,
    val text: String,
)

data class AiScreenPlan(
    val action: String = "speak",
    val packageName: String = "",
    val xNorm: Int = 5000,
    val yNorm: Int = 5000,
    val x2Norm: Int = 5000,
    val y2Norm: Int = 2500,
    val targetText: String = "",
    val phoneQuery: String = "",
    val inputText: String = "",
    val reply: String = "",
)

object GeminiAssistantClient {

    const val DEFAULT_MODEL = "gemini-flash-latest"

    internal val preferredModels = listOf(
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-2.5-flash-lite",
        "gemini-3-flash-preview",
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
    )

    private class GeminiHttpException(
        val statusCode: Int,
        override val message: String,
    ) : IllegalStateException(message)

    internal fun isAuthFailure(statusCode: Int, message: String?): Boolean {
        val msg = message.orEmpty()
        return statusCode == 401 ||
            msg.contains("API key not valid", ignoreCase = true) ||
            msg.contains("API_KEY_INVALID", ignoreCase = true) ||
            msg.contains("invalid api key", ignoreCase = true)
    }

    internal fun isTransientDemandError(statusCode: Int, message: String?): Boolean {
        val msg = message.orEmpty()
        return statusCode in listOf(429, 500, 502, 503, 504) ||
            msg.contains("high demand", ignoreCase = true) ||
            msg.contains("overloaded", ignoreCase = true) ||
            msg.contains("temporarily unavailable", ignoreCase = true) ||
            msg.contains("try again later", ignoreCase = true) ||
            msg.contains("resource exhausted", ignoreCase = true) ||
            msg.contains("rate limit", ignoreCase = true)
    }

    internal fun isModelDeprecationError(statusCode: Int, message: String?): Boolean {
        if (isAuthFailure(statusCode, message)) return false
        val msg = message.orEmpty()
        return statusCode in listOf(400, 403, 404, 410) ||
            msg.contains("no longer available", ignoreCase = true) ||
            msg.contains("deprecated", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("not supported", ignoreCase = true) ||
            msg.contains("update your code", ignoreCase = true)
    }

    fun extractSuggestedModel(errorMessage: String?, failedModel: String? = null): String? {
        if (errorMessage.isNullOrBlank()) return null
        val cleanFailed = failedModel?.removePrefix("models/")?.trim()
        val useMatch = Regex("(?:use|switch to|try|migrated to)\\s+models/([a-zA-Z0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(errorMessage)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trimEnd('.', ',', ';', ')')
        if (!useMatch.isNullOrBlank() && useMatch != cleanFailed && useMatch.contains("gemini")) {
            return useMatch
        }
        return Regex("models/([a-zA-Z0-9._-]+)")
            .findAll(errorMessage)
            .map { it.groupValues[1].trim().trimEnd('.', ',', ';', ')') }
            .lastOrNull { it.isNotBlank() && it.contains("gemini") && it != cleanFailed }
    }

    fun isPlausibleApiKeyFormat(rawKey: String): Boolean {
        val cleanKey = rawKey.trim()
        if (cleanKey.length < 20 || cleanKey.any { it.isWhitespace() }) return false
        return cleanKey.startsWith("AQ.") || cleanKey.startsWith("AIza") || cleanKey.length >= 28
    }

    suspend fun verifyApiKey(rawKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = rawKey.trim()
        if (!isPlausibleApiKeyFormat(cleanKey)) {
            return@withContext Result.failure(
                IllegalArgumentException("সঠিক Google AI Studio API Key দিন (AQ. বা AIza দিয়ে শুরু হয়)"),
            )
        }
        try {
            withTimeout(30_000L) {
                val url = URL(SecureServerVault.geminiModelsUrl())
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty(SecureServerVault.apiKeyHeaderName(), cleanKey)
                }
                val code = conn.responseCode
                val body = readBody(conn)
                val available = if (code in 200..299) parseAvailableModels(body) else emptyList()
                val candidates = buildOrderedCandidates(available)

                val tried = LinkedHashSet<String>()
                var lastErr: String? = if (code !in 200..299) parseApiError(body) else null
                val queue = ArrayDeque(candidates)
                while (queue.isNotEmpty()) {
                    val model = queue.removeFirst()
                    if (!tried.add(model)) continue
                    try {
                        val probePayload = JSONObject()
                            .put(
                                "contents",
                                JSONArray().put(
                                    JSONObject()
                                        .put("role", "user")
                                        .put("parts", JSONArray().put(JSONObject().put("text", "hi"))),
                                ),
                            )
                            .put(
                                "generationConfig",
                                JSONObject().put("maxOutputTokens", 4),
                            )
                        postGenerateContent(cleanKey, model, probePayload)
                        return@withTimeout Result.success(model)
                    } catch (e: GeminiHttpException) {
                        lastErr = e.message
                        if (isAuthFailure(e.statusCode, e.message)) {
                            break
                        }
                        val suggested = extractSuggestedModel(e.message, model)
                        if (suggested != null && !tried.contains(suggested)) {
                            queue.addFirst(suggested)
                        }
                    } catch (e: Exception) {
                        lastErr = e.message
                    }
                }

                Result.failure(
                    IllegalStateException(lastErr ?: "API Key সঠিক নয় (HTTP $code)"),
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Google AI Studio যাচাই ব্যর্থ: ${e.message ?: "নেটওয়ার্ক ত্রুটি"}"),
            )
        }
    }

    suspend fun sendChatMessage(
        apiKey: String,
        modelName: String,
        history: List<AiChatTurn>,
        userPrompt: String,
        screenshot: Bitmap? = null,
        onModelResolved: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Google AI Studio API Key সেট করা নেই"))
        }
        val activeModel = modelName.trim().ifBlank { DEFAULT_MODEL }
        try {
            withTimeout(60_000L) {
                val contents = JSONArray()
                for (turn in history.takeLast(14)) {
                    val role = if (turn.isUser) "user" else "model"
                    val parts = JSONArray().put(JSONObject().put("text", turn.text))
                    contents.put(JSONObject().put("role", role).put("parts", parts))
                }
                val currentParts = JSONArray().put(JSONObject().put("text", userPrompt))
                if (screenshot != null) {
                    val b64 = encodeBitmapJpegBase64(screenshot)
                    if (b64 != null) {
                        currentParts.put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject()
                                    .put("mimeType", "image/jpeg")
                                    .put("data", b64),
                            ),
                        )
                    }
                }
                contents.put(JSONObject().put("role", "user").put("parts", currentParts))

                val sysPrompt = """
                    You are vitRemote AI Assistant (built by developer Rubaeat Islam Raja / অরুপ Raja).
                    Reply naturally in Bengali when the user writes in Bengali (or English if asked in English).
                    When writing code (Kotlin, Python, JS, HTML, CSS, Java, SQL, etc.), always wrap code inside clean markdown fenced blocks like ```kotlin ... ``` so the user can copy code with one click.
                    Be clear, helpful, accurate, and concise.
                """.trimIndent()

                val payload = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt))),
                    )
                    .put("contents", contents)
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.65)
                            .put("maxOutputTokens", 4096),
                    )

                val resJson = postGenerateContentWithFallback(cleanKey, activeModel, payload, onModelResolved)
                val text = extractCandidateText(resJson)
                if (text.isBlank()) {
                    Result.failure(IllegalStateException("AI থেকে কোনো উত্তর পাওয়া যায়নি"))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "AI উত্তর পাওয়া যায়নি"))
        }
    }

    suspend fun decideScreenAction(
        apiKey: String,
        modelName: String,
        voiceCommand: String,
        screenBitmap: Bitmap?,
        installedApps: List<InstalledAppEntry>,
        uiSummary: String,
        modeLabel: String,
        onModelResolved: ((String) -> Unit)? = null,
    ): Result<AiScreenPlan> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Google AI Studio API Key সেট করা নেই"))
        }
        val activeModel = modelName.trim().ifBlank { DEFAULT_MODEL }
        try {
            withTimeout(45_000L) {
                val appCatalog = installedApps.take(110).joinToString("\n") {
                    "${it.label} -> ${it.packageName}"
                }
                val prompt = """
                    You are an intelligent Android screen & voice control AI operating in mode: $modeLabel.
                    User spoken request: "$voiceCommand"

                    Visible UI elements on screen (coordinates normalized 0..10000):
                    ${uiSummary.ifBlank { "(Not available; rely on screenshot if provided)" }}

                    Installed apps on device:
                    ${appCatalog.ifBlank { "(No app catalog provided)" }}

                    Decide the exact best single JSON action to satisfy the user's spoken request.
                    Supported "action" values:
                    - "speak": Only answer user's question about the screen or conversation without tapping. Put short Bengali answer in "reply".
                    - "open_app": Launch an installed app matching user's request. Put exact package name in "packageName" from Installed apps list.
                    - "call_contact": User wants to call someone or dial a number. Put the contact name or phone number in "phoneQuery".
                    - "click_text": Tap a visible UI button/element by exact label text shown on screen. Put short label in "targetText" and best fallback coordinates in xNorm/yNorm (0..10000).
                    - "click_xy": Tap coordinates on screen (xNorm 0..10000 left-to-right, yNorm 0..10000 top-to-bottom).
                    - "swipe": Swipe from (xNorm, yNorm) to (x2Norm, y2Norm).
                    - "scroll": Put targetText as "up", "down", "left", or "right".
                    - "type_text": Type text into focused field. Put text in "inputText".
                    - "back": Press Android Back.
                    - "home": Press Android Home.
                    - "recents": Open Recents.
                    - "notifications": Pull down notification shade.
                    - "torch": Put targetText as "on", "off", or "toggle".

                    Always include "reply" with a short, natural Bengali confirmation sentence describing what you are doing or answering.
                    Respond ONLY with valid JSON matching the schema.
                """.trimIndent()

                val parts = JSONArray().put(JSONObject().put("text", prompt))
                if (screenBitmap != null) {
                    val b64 = encodeBitmapJpegBase64(screenBitmap)
                    if (b64 != null) {
                        parts.put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject()
                                    .put("mimeType", "image/jpeg")
                                    .put("data", b64),
                            ),
                        )
                    }
                }

                val schema = JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject()
                            .put("action", JSONObject().put("type", "STRING"))
                            .put("packageName", JSONObject().put("type", "STRING"))
                            .put("xNorm", JSONObject().put("type", "INTEGER"))
                            .put("yNorm", JSONObject().put("type", "INTEGER"))
                            .put("x2Norm", JSONObject().put("type", "INTEGER"))
                            .put("y2Norm", JSONObject().put("type", "INTEGER"))
                            .put("targetText", JSONObject().put("type", "STRING"))
                            .put("phoneQuery", JSONObject().put("type", "STRING"))
                            .put("inputText", JSONObject().put("type", "STRING"))
                            .put("reply", JSONObject().put("type", "STRING")),
                    )
                    .put("required", JSONArray().put("action").put("reply"))

                val payload = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.2)
                            .put("responseMimeType", "application/json")
                            .put("responseSchema", schema),
                    )

                val resJson = postGenerateContentWithFallback(cleanKey, activeModel, payload, onModelResolved)
                val rawText = extractCandidateText(resJson).trim()
                val obj = JSONObject(stripCodeFence(rawText))
                Result.success(
                    AiScreenPlan(
                        action = obj.optString("action", "speak"),
                        packageName = obj.optString("packageName", ""),
                        xNorm = obj.optInt("xNorm", 5000).coerceIn(0, 10000),
                        yNorm = obj.optInt("yNorm", 5000).coerceIn(0, 10000),
                        x2Norm = obj.optInt("x2Norm", 5000).coerceIn(0, 10000),
                        y2Norm = obj.optInt("y2Norm", 2500).coerceIn(0, 10000),
                        targetText = obj.optString("targetText", ""),
                        phoneQuery = obj.optString("phoneQuery", ""),
                        inputText = obj.optString("inputText", ""),
                        reply = obj.optString("reply", "কাজ সম্পন্ন হয়েছে"),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "AI কমান্ড প্রসেস করা যায়নি"))
        }
    }

    private suspend fun postGenerateContentWithFallback(
        apiKey: String,
        initialModel: String,
        payload: JSONObject,
        onModelResolved: ((String) -> Unit)?,
    ): JSONObject {
        val cleanInitial = initialModel.removePrefix("models/").trim().ifBlank { DEFAULT_MODEL }
        val tried = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(cleanInitial)
        preferredModels.forEach { if (it != cleanInitial) queue.add(it) }

        var lastError: Exception? = null
        var hadPermanentModelFailure = false
        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst()
            if (!tried.add(candidate)) continue
            try {
                val res = postGenerateContent(apiKey, candidate, payload)
                if (candidate != cleanInitial && hadPermanentModelFailure) {
                    onModelResolved?.invoke(candidate)
                }
                return res
            } catch (e: GeminiHttpException) {
                lastError = e
                if (isAuthFailure(e.statusCode, e.message)) {
                    throw e
                }
                val isTransient = isTransientDemandError(e.statusCode, e.message)
                if (isTransient && candidate == cleanInitial) {
                    delay(450)
                    try {
                        return postGenerateContent(apiKey, candidate, payload)
                    } catch (retryErr: GeminiHttpException) {
                        lastError = retryErr
                        if (isAuthFailure(retryErr.statusCode, retryErr.message)) {
                            throw retryErr
                        }
                    }
                }
                val isModelIssue = isModelDeprecationError(e.statusCode, e.message)
                if (isModelIssue) {
                    hadPermanentModelFailure = true
                }
                if (!isTransient && !isModelIssue) {
                    throw e
                }
                val suggested = extractSuggestedModel(e.message, candidate)
                if (suggested != null && !tried.contains(suggested)) {
                    queue.addFirst(suggested)
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemini API ত্রুটি")
    }

    private fun postGenerateContent(apiKey: String, model: String, payload: JSONObject): JSONObject {
        val cleanModel = model.removePrefix("models/")
        val url = URL(
            SecureServerVault.geminiGenerateUrl(URLEncoder.encode(cleanModel, "UTF-8")),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 50_000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty(SecureServerVault.apiKeyHeaderName(), apiKey.trim())
        }
        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = conn.responseCode
        val body = readBody(conn)
        if (code !in 200..299) {
            throw GeminiHttpException(code, parseApiError(body) ?: "Gemini API ত্রুটি (HTTP $code)")
        }
        return JSONObject(body)
    }

    private fun parseAvailableModels(modelsBody: String): List<String> {
        val available = ArrayList<String>()
        try {
            val root = JSONObject(modelsBody)
            val arr = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").removePrefix("models/")
                val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
                var supportsGen = false
                for (m in 0 until methods.length()) {
                    if (methods.optString(m) == "generateContent") {
                        supportsGen = true
                        break
                    }
                }
                if (supportsGen && name.isNotBlank()) {
                    available.add(name)
                }
            }
        } catch (_: Exception) {
        }
        return available
    }

    internal fun buildOrderedCandidates(available: List<String>): List<String> {
        val ordered = LinkedHashSet<String>()
        // Always try the official Google AI Studio -latest aliases first
        ordered.add("gemini-flash-latest")
        ordered.add("gemini-flash-lite-latest")
        val availSet = available.toHashSet()
        for (p in preferredModels) {
            if (availSet.isEmpty() || availSet.contains(p)) {
                ordered.add(p)
            }
        }
        // Also prioritize any newer gemini flash models listed in /models (highest version first)
        available
            .filter {
                it.contains("gemini") &&
                    it.contains("flash") &&
                    !it.contains("tts") &&
                    !it.contains("image") &&
                    !it.contains("audio")
            }
            .sortedDescending()
            .forEach { ordered.add(it) }
        for (p in preferredModels) {
            ordered.add(p)
        }
        return ordered.toList()
    }

    private fun extractCandidateText(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val content = first.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val t = part.optString("text", "")
            if (t.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
        }
        return sb.toString().trim()
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val lastFence = trimmed.lastIndexOf("```")
        return if (firstNewline >= 0 && lastFence > firstNewline) {
            trimmed.substring(firstNewline + 1, lastFence).trim()
        } else {
            trimmed
        }
    }

    private fun parseApiError(body: String): String? = try {
        val obj = JSONObject(body)
        obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun encodeBitmapJpegBase64(bmp: Bitmap): String? = try {
        val maxW = 720
        val scaled = if (bmp.width > maxW) {
            val h = (bmp.height.toLong() * maxW / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(160)
            Bitmap.createScaledBitmap(bmp, maxW, h, true)
        } else {
            bmp
        }
        val baos = ByteArrayOutputStream(32 * 1024)
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        if (scaled !== bmp) scaled.recycle()
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Throwable) {
        null
    }
}
