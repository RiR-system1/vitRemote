package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

class Prefs(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val sp = appCtx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var cloudEnabled: Boolean
        get() = sp.getBoolean("cloud_enabled", true)
        set(v) = sp.edit().putBoolean("cloud_enabled", v).apply()

    val deviceId: String
        get() {
            val hardwareId = stableHardwareDeviceId(appCtx)
            if (hardwareId != null) {
                if (sp.getString("device_id", null) != hardwareId) {
                    sp.edit().putString("device_id", hardwareId).apply()
                }
                return hardwareId
            }
            return sp.getString("device_id", null) ?: run {
                val id = "dev_${UUID.randomUUID().toString().replace("-", "").take(20)}"
                sp.edit().putString("device_id", id).apply()
                id
            }
        }

    /**
     * Permanent 9-digit device code for this device (never rotated; synced with Firestore binding).
     */
    var deviceCode: String
        get() = sp.getString("device_code", null)?.takeIf { it.matches(CODE_REGEX) } ?: run {
            val derived = deterministicCodeFromId(deviceId)
            sp.edit().putString("device_code", derived).apply()
            derived
        }
        set(v) {
            val clean = normalizeCode(v)
            if (clean.matches(CODE_REGEX)) {
                sp.edit().putString("device_code", clean).apply()
            }
        }

    var deviceAlias: String
        get() = sp.getString("device_alias", "")?.trim().orEmpty()
        set(v) {
            sp.edit().putString("device_alias", v.trim().take(60)).apply()
        }

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    var geminiApiKey: String
        get() = sp.getString("gemini_api_key", "")?.trim().orEmpty()
        set(v) = sp.edit().putString("gemini_api_key", v.trim()).apply()

    var geminiModel: String
        get() {
            val saved = sp.getString("gemini_model", DEFAULT_GEMINI_MODEL)?.trim().orEmpty()
            return when {
                saved.isBlank() || saved == "gemini-2.5-flash" || saved == "gemini-3.8-flash" -> {
                    if (saved != DEFAULT_GEMINI_MODEL) {
                        sp.edit().putString("gemini_model", DEFAULT_GEMINI_MODEL).apply()
                    }
                    DEFAULT_GEMINI_MODEL
                }
                else -> saved
            }
        }
        set(v) = sp.edit().putString("gemini_model", v.trim().ifBlank { DEFAULT_GEMINI_MODEL }).apply()

    var aiSystemEnabled: Boolean
        get() = sp.getBoolean("ai_system_enabled", false)
        set(v) = sp.edit().putBoolean("ai_system_enabled", v).apply()

    var virtualScreenAiEnabled: Boolean
        get() = sp.getBoolean("virtual_screen_ai_enabled", false)
        set(v) = sp.edit().putBoolean("virtual_screen_ai_enabled", v).apply()

    var remoteScreenAiEnabled: Boolean
        get() = sp.getBoolean("remote_screen_ai_enabled", false)
        set(v) = sp.edit().putBoolean("remote_screen_ai_enabled", v).apply()

    var ownDeviceAiEnabled: Boolean
        get() = sp.getBoolean("own_device_ai_enabled", false)
        set(v) = sp.edit().putBoolean("own_device_ai_enabled", v).apply()

    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay_enabled", false)
        set(v) = sp.edit().putBoolean("overlay_enabled", v).apply()

    var isDarkTheme: Boolean
        get() = sp.getBoolean("is_dark_theme", false)
        set(v) = sp.edit().putBoolean("is_dark_theme", v).apply()

    var appLanguage: String
        get() = sp.getString("app_language", LANG_BN)?.takeIf { it == LANG_BN || it == LANG_EN } ?: LANG_BN
        set(v) = sp.edit().putString("app_language", if (v == LANG_EN) LANG_EN else LANG_BN).apply()

    fun displayName(fallbackModel: String): String =
        deviceAlias.ifBlank { fallbackModel.ifBlank { "Android Device" } }

    companion object {
        const val LANG_BN = "bn"
        const val LANG_EN = "en"
        const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
        const val ADMIN_TELEGRAM_URL = "https://t.me/BDrubaeat22"
        const val ADMIN_TELEGRAM_HANDLE = "@BDrubaeat22"
        val CODE_REGEX = Regex("^[0-9]{9}$")

        fun normalizeCode(raw: String): String = raw.filter { it.isDigit() }

        fun formatCode(code: String): String {
            val digits = normalizeCode(code)
            return if (digits.length == 9) {
                "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6, 9)}"
            } else {
                digits
            }
        }

        fun deterministicCodeFromId(deviceId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("vit_code:$deviceId".toByteArray())
            val num = ((digest[0].toLong() and 0xFF) shl 24) or
                ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or
                (digest[3].toLong() and 0xFF)
            val nineDigits = 100_000_000L + (kotlin.math.abs(num) % 900_000_000L)
            return nineDigits.toString()
        }

        @SuppressLint("HardwareIds")
        private fun stableHardwareDeviceId(ctx: Context): String? {
            val androidId = try {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }
            if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
                return null
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("vitremote_hw:$androidId".toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }.take(20)
            return "dev_$hex"
        }
    }
}
