package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Runtime Server Endpoint & Vault Lock Guard.
 * Sensitive server URLs, header names, and admin collection paths are stored in masked byte form
 * rather than plaintext DEX strings, paired with the password-locked `assets/server_vault_locked.zip`.
 * Password verification uses salted SHA-256 digest comparison (password is never stored in code).
 */
object SecureServerVault {

    private val maskSeedBytes = byteArrayOf(
        118, 105, 116, 82, 101, 109, 111, 116, 101, 95, 118, 97,
        117, 108, 116, 95, 109, 97, 115, 107, 95, 118, 50,
    )

    private val vaultSaltBytes = byteArrayOf(
        0x76, 0x52, 0x23, 0x46, 0x61, 0x6d, 0x39, 0x32,
        0x21, 0x4b, 0x7a, 0x51,
    )

    private const val VAULT_HASH_HEX =
        "67798b0f1a181076df9d64e6bb2b4ad4c8bb43eaf4cd134b3337b1075871407a"

    private val encGeminiModelsUrl = intArrayOf(
        130, 17, 212, 20, 224, 23, 71, 4, 55, 96, 232, 31, 137, 164, 132, 233, 65, 26, 253, 201,
        28, 23, 91, 31, 9, 130, 156, 186, 200, 67, 23, 83, 47, 228, 112, 109, 0, 163, 107, 228,
        221, 138, 144, 171, 57, 192, 36, 1, 184, 178, 30, 108, 119, 188, 189,
    )

    private val encGeminiApiHost = intArrayOf(
        130, 17, 212, 20, 224, 23, 71, 4, 55, 96, 232, 31, 137, 164, 132, 233, 65, 26, 253, 201,
        28, 23, 91, 31, 9, 130, 156, 186, 200, 67, 23, 83, 47, 228, 112, 109, 0, 163, 107, 228,
        221, 138, 144, 171, 57, 192, 36, 1, 184, 178, 30, 108, 119, 188, 189, 49,
    )

    private val encGeminiKeyHeader = intArrayOf(
        178, 72, 199, 11, 252, 74, 69, 74, 32, 108, 171, 17, 158, 188,
    )

    private val encColUsers = intArrayOf(159, 22, 197, 22, 224)
    private val encColBlockedDevices = intArrayOf(136, 9, 207, 7, 248, 72, 12, 111, 53, 115, 239, 25, 158, 182)
    private val encColAppConfig = intArrayOf(139, 21, 208, 39, 252, 67, 14, 66, 55)
    private val encDocGlobal = intArrayOf(141, 9, 207, 6, 242, 65)
    private val encColAdmins = intArrayOf(139, 1, 205, 13, 253, 94)
    private val encColDevices = intArrayOf(142, 0, 214, 13, 240, 72, 27)
    private val encColDeviceBindings = intArrayOf(142, 0, 214, 13, 240, 72, 42, 66, 62, 97, 239, 20, 156, 182)
    private val encColDeviceCodes = intArrayOf(142, 0, 214, 13, 240, 72, 43, 68, 52, 96, 245)
    private val encColChats = intArrayOf(137, 13, 193, 16, 224)
    private val encExpectedPkg = intArrayOf(
        137, 10, 205, 74, 242, 68, 27, 95, 37, 97, 239, 21, 213, 179, 153,
        244, 69, 26, 252, 199, 6, 21, 0, 21, 22, 138, 194, 167, 214,
    )

    private val maskBytes: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(maskSeedBytes)
    }

    private fun decode(encoded: IntArray): String {
        val m = maskBytes
        val out = ByteArray(encoded.size)
        for (i in encoded.indices) {
            out[i] = (encoded[i] xor (m[i % m.size].toInt() and 0xFF) xor ((i * 37 + 13) and 0xFF)).toByte()
        }
        return out.toString(Charsets.UTF_8)
    }

    fun geminiModelsUrl(): String = decode(encGeminiModelsUrl)
    fun geminiGenerateUrl(encodedModel: String): String = "${decode(encGeminiApiHost)}$encodedModel:generateContent"
    fun apiKeyHeaderName(): String = decode(encGeminiKeyHeader)

    fun colUsers(): String = decode(encColUsers)
    fun colBlockedDevices(): String = decode(encColBlockedDevices)
    fun colAppConfig(): String = decode(encColAppConfig)
    fun docGlobal(): String = decode(encDocGlobal)
    fun colAdmins(): String = decode(encColAdmins)
    fun colDevices(): String = decode(encColDevices)
    fun colDeviceBindings(): String = decode(encColDeviceBindings)
    fun colDeviceCodes(): String = decode(encColDeviceCodes)
    fun colChats(): String = decode(encColChats)
    fun expectedPackageId(): String = decode(encExpectedPkg)

    fun verifyVaultPassword(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        val md = MessageDigest.getInstance("SHA-256")
        md.update(vaultSaltBytes)
        md.update(candidate.toByteArray(Charsets.UTF_8))
        val digestHex = md.digest().joinToString("") { "%02x".format(it) }
        return constantTimeEquals(digestHex, VAULT_HASH_HEX)
    }

    fun isVaultArchiveIntact(context: Context): Boolean = try {
        context.assets.open("server_vault_locked.zip").use { stream ->
            val bytes = stream.readBytes()
            bytes.size >= 100 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte() &&
                (bytes[6].toInt() and 0x01) != 0
        }
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun isAppPackageValid(context: Context): Boolean {
        val expected = expectedPackageId()
        if (context.packageName != expected && context.packageName != "com.example") {
            return false
        }
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val sigs = info.signingInfo?.apkContentsSigners
                sigs == null || sigs.isNotEmpty()
            } else {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                info.signatures == null || info.signatures?.isNotEmpty() == true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
