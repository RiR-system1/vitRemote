package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.VoiceAssistantController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("vitRemote", appName)
    }

    @Test
    fun `prefs generates permanent 9 digit code and formats cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(context)
        assertTrue(prefs.cloudEnabled)
        assertTrue(prefs.deviceId.startsWith("dev_"))
        assertTrue(prefs.deviceCode.matches(Prefs.CODE_REGEX))
        assertEquals(
            "${prefs.deviceCode.substring(0, 3)}-${prefs.deviceCode.substring(3, 6)}-${prefs.deviceCode.substring(6, 9)}",
            Prefs.formatCode(prefs.deviceCode),
        )
    }

    @Test
    fun `device info encodes json cleanly`() {
        val json = DeviceInfo.toJson(mapOf("t" to "err", "m" to "Test \"msg\"", "ok" to true, "n" to 42))
        assertEquals("{\"t\":\"err\",\"m\":\"Test \\\"msg\\\"\",\"ok\":true,\"n\":42}", json)
    }

    @Test
    fun `ai voice controller blocks listening when master switch is off`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(context)
        prefs.aiSystemEnabled = false
        val controller = VoiceAssistantController.get(context)
        controller.syncMasterAiEnabled(false)
        var invoked = false
        controller.startVoiceSession { invoked = true }
        assertFalse(invoked)
        assertFalse(controller.state.value.aiSystemOn)
        assertFalse(controller.state.value.isListening)
    }

    @Test
    fun `gemini client accepts both AQ and AIza key formats`() {
        assertTrue(com.example.ai.GeminiAssistantClient.isPlausibleApiKeyFormat("AQ.Ab8RN6SampleKeyValue1234567890"))
        assertTrue(com.example.ai.GeminiAssistantClient.isPlausibleApiKeyFormat("AIzaSySampleLegacyKey1234567890123"))
        assertFalse(com.example.ai.GeminiAssistantClient.isPlausibleApiKeyFormat("short"))
        assertFalse(com.example.ai.GeminiAssistantClient.isPlausibleApiKeyFormat("AQ. key with spaces 1234567890"))
    }

    @Test
    fun `gemini client extracts replacement model from deprecation error`() {
        val msg = "This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features."
        assertEquals("gemini-3.8-flash", com.example.ai.GeminiAssistantClient.extractSuggestedModel(msg))
    }

    @Test
    fun `prefs defaults and migrates legacy models to gemini-flash-latest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(context)
        assertEquals("gemini-flash-latest", prefs.geminiModel)
        prefs.geminiModel = "gemini-3.8-flash"
        assertEquals("gemini-flash-latest", prefs.geminiModel)
        prefs.geminiModel = "gemini-2.5-flash"
        assertEquals("gemini-flash-latest", prefs.geminiModel)
    }

    @Test
    fun `gemini client prioritizes gemini-flash-latest and handles 503 high demand as transient`() {
        val ordered = com.example.ai.GeminiAssistantClient.buildOrderedCandidates(listOf("gemini-2.5-flash", "gemini-3.8-flash"))
        assertEquals("gemini-flash-latest", ordered.first())
        assertTrue(
            com.example.ai.GeminiAssistantClient.isTransientDemandError(
                503,
                "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
            ),
        )
        assertFalse(com.example.ai.GeminiAssistantClient.isAuthFailure(503, "high demand"))
        assertTrue(com.example.ai.GeminiAssistantClient.isAuthFailure(400, "API key not valid. Please pass a valid API key."))
        assertFalse(com.example.ai.GeminiAssistantClient.isModelDeprecationError(400, "API key not valid. Please pass a valid API key."))
    }

    @Test
    fun `secure server vault decodes collections and endpoints accurately and validates asset lock`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("users", com.example.security.SecureServerVault.colUsers())
        assertEquals("blockedDevices", com.example.security.SecureServerVault.colBlockedDevices())
        assertEquals("appConfig", com.example.security.SecureServerVault.colAppConfig())
        assertEquals("global", com.example.security.SecureServerVault.docGlobal())
        assertEquals("admins", com.example.security.SecureServerVault.colAdmins())
        assertEquals("devices", com.example.security.SecureServerVault.colDevices())
        assertEquals("deviceBindings", com.example.security.SecureServerVault.colDeviceBindings())
        assertEquals("deviceCodes", com.example.security.SecureServerVault.colDeviceCodes())
        assertEquals("chats", com.example.security.SecureServerVault.colChats())
        assertEquals("X-goog-api-key", com.example.security.SecureServerVault.apiKeyHeaderName())
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models",
            com.example.security.SecureServerVault.geminiModelsUrl(),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            com.example.security.SecureServerVault.geminiGenerateUrl("gemini-flash-latest"),
        )
        assertTrue(com.example.security.SecureServerVault.isVaultArchiveIntact(context))
        assertTrue(com.example.security.SecureServerVault.isAppPackageValid(context))
        assertTrue(com.example.security.SecureServerVault.verifyVaultPassword(listOf("Rubaeat", "@", "1234567890").joinToString("")))
        assertFalse(com.example.security.SecureServerVault.verifyVaultPassword("wrong-password"))
        assertTrue(com.example.data.CloudRepository.DEFAULT_BLOCK_NOTICE.contains("ব্লক"))
    }

    @Test
    fun `theme language prefs and telegram support constants work properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(context)

        assertEquals("https://t.me/BDrubaeat22", Prefs.ADMIN_TELEGRAM_URL)
        assertEquals("@BDrubaeat22", Prefs.ADMIN_TELEGRAM_HANDLE)

        assertFalse(prefs.isDarkTheme)
        prefs.isDarkTheme = true
        assertTrue(prefs.isDarkTheme)
        prefs.isDarkTheme = false

        assertEquals(Prefs.LANG_BN, prefs.appLanguage)
        prefs.appLanguage = Prefs.LANG_EN
        assertEquals(Prefs.LANG_EN, prefs.appLanguage)
        assertTrue(com.example.ui.appStringsFor(Prefs.LANG_EN).isEnglish)
        prefs.appLanguage = "invalid"
        assertEquals(Prefs.LANG_BN, prefs.appLanguage)
        assertFalse(com.example.ui.appStringsFor(Prefs.LANG_BN).isEnglish)
    }
}



