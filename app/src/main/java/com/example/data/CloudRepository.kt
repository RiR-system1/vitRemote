package com.example.data

import android.content.Context
import android.os.Build
import com.example.Prefs
import com.example.R
import com.example.security.SecureServerVault
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

sealed interface BindingCheckResult {
    data class Bound(val deviceCode: String) : BindingCheckResult
    data class LockedToOtherAccount(val maskedEmail: String) : BindingCheckResult
}

class CloudRepository(private val db: FirebaseFirestore) {

    private val knownDeviceDocs = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val knownCodeDocs = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private fun bindingDoc(deviceId: String) = db.collection(SecureServerVault.colDeviceBindings()).document(deviceId)
    private fun codeDoc(code: String) = db.collection(SecureServerVault.colDeviceCodes()).document(code)
    private fun devicesCollection() = db.collection(SecureServerVault.colDevices())
    private fun deviceDoc(deviceId: String) = devicesCollection().document(deviceId)
    private fun latestFrameDoc(deviceId: String) =
        deviceDoc(deviceId).collection("frames").document(LATEST_FRAME_ID)
    private fun virtualFrameDoc(deviceId: String) =
        deviceDoc(deviceId).collection("frames").document(VIRTUAL_FRAME_ID)
    private fun installedAppsDoc(deviceId: String) =
        deviceDoc(deviceId).collection("meta").document(INSTALLED_APPS_ID)
    private fun notificationsDoc(deviceId: String) =
        deviceDoc(deviceId).collection("meta").document(NOTIFICATIONS_ID)
    private fun commandsCollection(deviceId: String) =
        deviceDoc(deviceId).collection("commands")
    private fun userDoc(userId: String) =
        db.collection(SecureServerVault.colUsers()).document(userId)
    private fun blockedDeviceDoc(deviceId: String) =
        db.collection(SecureServerVault.colBlockedDevices()).document(deviceId)
    private fun globalConfigDoc() =
        db.collection(SecureServerVault.colAppConfig()).document(SecureServerVault.docGlobal())
    private fun savedDevicesCollection(userId: String) =
        userDoc(userId).collection("savedDevices")

    private fun chatsCollection() = db.collection(SecureServerVault.colChats())
    private fun chatDoc(chatId: String) = chatsCollection().document(chatId)
    private fun messagesCollection(chatId: String) = chatDoc(chatId).collection("messages")
    private fun callsCollection(chatId: String) = chatDoc(chatId).collection("calls")
    private fun callDoc(chatId: String, callId: String) = callsCollection(chatId).document(callId)
    private fun callStreamDoc(chatId: String, callId: String, uid: String) =
        callDoc(chatId, callId).collection("streams").document(uid)

    suspend fun ensureDeviceBinding(
        uid: String,
        email: String?,
        deviceId: String,
        preferredCode: String,
        model: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion: String = Build.VERSION.RELEASE ?: "14",
    ): Result<BindingCheckResult> {
        val bindRef = bindingDoc(deviceId)
        val masked = maskEmail(email)
        return try {
            val snap = bindRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(DeviceBinding::class.java, ServerTimestampBehavior.ESTIMATE)
                val ownerUid = existing?.ownerUid.orEmpty()
                if (ownerUid.isNotEmpty() && ownerUid != uid) {
                    return Result.success(
                        BindingCheckResult.LockedToOtherAccount(
                            existing?.ownerEmailMasked?.ifBlank { "another Google account" }
                                ?: "another Google account",
                        ),
                    )
                }
                val code = existing?.deviceCode?.takeIf { it.matches(Prefs.CODE_REGEX) }
                    ?: preferredCode
                ensureCodeDocExists(
                    code = code,
                    ownerUid = uid,
                    deviceId = deviceId,
                    model = model,
                    androidVersion = androidVersion,
                )
                if (existing != null && existing.ownerEmailMasked != masked && masked.isNotBlank()) {
                    bindRef.update(
                        mapOf(
                            "ownerEmailMasked" to masked,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
                return Result.success(BindingCheckResult.Bound(code))
            }

            var chosenCode = preferredCode.takeIf { it.matches(Prefs.CODE_REGEX) }
                ?: Prefs.deterministicCodeFromId(deviceId)

            var attempt = 0
            while (attempt < 5) {
                val existingCodeSnap = codeDoc(chosenCode).get().await()
                if (!existingCodeSnap.exists() || existingCodeSnap.getString("deviceId") == deviceId) {
                    break
                }
                chosenCode = Random.nextInt(100_000_000, 1_000_000_000).toString()
                attempt++
            }

            val bindingPayload = mapOf(
                "ownerUid" to uid,
                "ownerEmailMasked" to masked,
                "deviceCode" to chosenCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            bindRef.set(bindingPayload).await()

            ensureCodeDocExists(
                code = chosenCode,
                ownerUid = uid,
                deviceId = deviceId,
                model = model,
                androidVersion = androidVersion,
            )
            Result.success(BindingCheckResult.Bound(chosenCode))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, bindRef.path)
            Result.failure(e)
        }
    }

    private suspend fun ensureCodeDocExists(
        code: String,
        ownerUid: String,
        deviceId: String,
        model: String,
        androidVersion: String,
    ) {
        val cRef = codeDoc(code)
        val snap = cRef.get().await()
        val safeModel = model.take(120).ifBlank { "Android Device" }
        val safeVer = androidVersion.take(40).ifBlank { "14" }
        if (!snap.exists()) {
            cRef.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "deviceId" to deviceId,
                    "model" to safeModel,
                    "androidVersion" to safeVer,
                    "online" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    suspend fun connectByCode(
        rawCode: String,
        currentUserId: String,
    ): Result<DeviceNode> {
        val code = Prefs.normalizeCode(rawCode)
        if (!code.matches(Prefs.CODE_REGEX)) {
            return Result.failure(IllegalArgumentException("Enter a valid 9-digit device code"))
        }
        val cRef = codeDoc(code)
        return try {
            val codeSnap = cRef.get().await()
            if (!codeSnap.exists()) {
                return Result.failure(IllegalStateException("No device found with code ${Prefs.formatCode(code)}"))
            }
            val targetDeviceId = codeSnap.getString("deviceId").orEmpty()
            if (targetDeviceId.isEmpty()) {
                return Result.failure(IllegalStateException("Device registration incomplete"))
            }
            val devSnap = deviceDoc(targetDeviceId).get().await()
            val device = devSnap.toObject(DeviceNode::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Device has not come online yet"))
            if (!device.online || !device.cloudEnabled) {
                return Result.failure(
                    IllegalStateException("${device.displayTitle()} is currently offline"),
                )
            }
            saveRecentDevice(currentUserId, device)
            Result.success(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, cRef.path)
            Result.failure(e)
        }
    }

    private suspend fun saveRecentDevice(userId: String, device: DeviceNode) {
        if (device.id.isBlank() || !device.deviceCode.matches(Prefs.CODE_REGEX)) return
        val ref = savedDevicesCollection(userId).document(device.id)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "userId" to userId,
                        "targetDeviceId" to device.id,
                        "deviceCode" to device.deviceCode,
                        "model" to device.model.take(120).ifBlank { "Android Device" },
                        "androidVersion" to device.androidVersion.take(40).ifBlank { "14" },
                        "alias" to device.deviceAlias.take(60),
                        "pinned" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                val existingAlias = snap.getString("alias").orEmpty()
                val existingPinned = snap.getBoolean("pinned") ?: false
                ref.update(
                    mapOf(
                        "deviceCode" to device.deviceCode,
                        "model" to device.model.take(120).ifBlank { "Android Device" },
                        "androidVersion" to device.androidVersion.take(40).ifBlank { "14" },
                        "alias" to existingAlias.ifBlank { device.deviceAlias }.take(60),
                        "pinned" to existingPinned,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-critical convenience cache
        }
    }

    suspend fun updateSavedDeviceMeta(
        userId: String,
        targetDeviceId: String,
        alias: String? = null,
        pinned: Boolean? = null,
    ): Result<Unit> {
        val ref = savedDevicesCollection(userId).document(targetDeviceId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (alias != null) updates["alias"] = alias.trim().take(60)
            if (pinned != null) updates["pinned"] = pinned
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun removeSavedDevice(userId: String, targetDeviceId: String): Result<Unit> {
        val ref = savedDevicesCollection(userId).document(targetDeviceId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    fun observeSavedDevices(userId: String): Flow<List<SavedDevice>> {
        val col = savedDevicesCollection(userId)
        return col.snapshots()
            .map { snap ->
                snap.toObjects(SavedDevice::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.LIST, col.path)
                }
                throw error
            }
    }

    fun observeOwnDevices(ownerUid: String): Flow<List<DeviceNode>> {
        val query = devicesCollection().whereEqualTo("ownerUid", ownerUid)
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(DeviceNode::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.LIST, devicesCollection().path)
                }
                throw error
            }
    }

    fun observeDevice(deviceId: String): Flow<DeviceNode?> {
        val ref = deviceDoc(deviceId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(DeviceNode::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun upsertDevice(ownerUid: String, deviceId: String, node: DeviceNode): Result<Unit> {
        val ref = deviceDoc(deviceId)
        return try {
            val safeModel = node.model.take(120).ifEmpty { "Android Device" }
            val safeVer = node.androidVersion.take(40).ifEmpty { "14" }
            val updatePayload = mapOf(
                "model" to safeModel,
                "deviceAlias" to node.deviceAlias.take(60),
                "androidVersion" to safeVer,
                "sdk" to node.sdk.coerceIn(21L, 99L),
                "online" to node.online,
                "cloudEnabled" to node.cloudEnabled,
                "videoStreaming" to node.videoStreaming,
                "screenWidth" to node.screenWidth.coerceIn(0L, 10000L),
                "screenHeight" to node.screenHeight.coerceIn(0L, 10000L),
                "batteryLevel" to node.batteryLevel.coerceIn(-1L, 100L),
                "batteryTempTenths" to node.batteryTempTenths.coerceIn(-300L, 1200L),
                "charging" to node.charging,
                "ramUsedMb" to node.ramUsedMb.coerceIn(0L, 1048576L),
                "ramTotalMb" to node.ramTotalMb.coerceIn(0L, 1048576L),
                "storageUsedMb" to node.storageUsedMb.coerceIn(0L, 16777216L),
                "storageTotalMb" to node.storageTotalMb.coerceIn(0L, 16777216L),
                "brightness" to node.brightness.coerceIn(-1L, 255L),
                "torchOn" to node.torchOn,
                "ringing" to node.ringing,
                "clipboardText" to node.clipboardText.take(4000),
                "captureMode" to node.captureMode.take(120),
                "virtualScreenActive" to node.virtualScreenActive,
                "virtualScreenPackage" to node.virtualScreenPackage.take(150),
                "virtualScreenStatus" to node.virtualScreenStatus.take(240),
                "foregroundPackage" to node.foregroundPackage.take(150),
                "foregroundLabel" to node.foregroundLabel.take(100),
                "foregroundChangedAtMs" to node.foregroundChangedAtMs.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            var updatedViaFastPath = false
            if (deviceId in knownDeviceDocs) {
                try {
                    ref.update(updatePayload).await()
                    updatedViaFastPath = true
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        knownDeviceDocs.remove(deviceId)
                    } else {
                        throw e
                    }
                }
            }

            if (!updatedViaFastPath) {
                val existingSnap = ref.get().await()
                if (!existingSnap.exists()) {
                    val createPayload = mapOf(
                        "ownerUid" to ownerUid,
                        "deviceCode" to node.deviceCode,
                        "model" to safeModel,
                        "deviceAlias" to node.deviceAlias.take(60),
                        "androidVersion" to safeVer,
                        "sdk" to node.sdk.coerceIn(21L, 99L),
                        "online" to node.online,
                        "cloudEnabled" to node.cloudEnabled,
                        "videoStreaming" to node.videoStreaming,
                        "screenWidth" to node.screenWidth.coerceIn(0L, 10000L),
                        "screenHeight" to node.screenHeight.coerceIn(0L, 10000L),
                        "batteryLevel" to node.batteryLevel.coerceIn(-1L, 100L),
                        "batteryTempTenths" to node.batteryTempTenths.coerceIn(-300L, 1200L),
                        "charging" to node.charging,
                        "ramUsedMb" to node.ramUsedMb.coerceIn(0L, 1048576L),
                        "ramTotalMb" to node.ramTotalMb.coerceIn(0L, 1048576L),
                        "storageUsedMb" to node.storageUsedMb.coerceIn(0L, 16777216L),
                        "storageTotalMb" to node.storageTotalMb.coerceIn(0L, 16777216L),
                        "brightness" to node.brightness.coerceIn(-1L, 255L),
                        "torchOn" to node.torchOn,
                        "ringing" to node.ringing,
                        "clipboardText" to node.clipboardText.take(4000),
                        "captureMode" to node.captureMode.take(120),
                        "viewerActive" to false,
                        "viewerQuality" to node.viewerQuality.coerceIn(160L, 1440L),
                        "virtualScreenActive" to node.virtualScreenActive,
                        "virtualScreenPackage" to node.virtualScreenPackage.take(150),
                        "virtualScreenStatus" to node.virtualScreenStatus.take(240),
                        "activeViewerUid" to null,
                        "lastSessionCode" to null,
                        "foregroundPackage" to node.foregroundPackage.take(150),
                        "foregroundLabel" to node.foregroundLabel.take(100),
                        "foregroundChangedAtMs" to node.foregroundChangedAtMs.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    ref.set(createPayload).await()
                } else {
                    ref.update(updatePayload).await()
                }
                knownDeviceDocs.add(deviceId)
            }

            if (node.deviceCode.matches(Prefs.CODE_REGEX)) {
                val codeKey = "$ownerUid:${node.deviceCode}"
                val cRef = codeDoc(node.deviceCode)
                val codeUpdate = mapOf(
                    "model" to safeModel,
                    "androidVersion" to safeVer,
                    "online" to (node.online && node.cloudEnabled),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                var codeFastOk = false
                if (codeKey in knownCodeDocs) {
                    try {
                        cRef.update(codeUpdate).await()
                        codeFastOk = true
                    } catch (e: FirebaseFirestoreException) {
                        if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                            knownCodeDocs.remove(codeKey)
                        } else {
                            throw e
                        }
                    }
                }
                if (!codeFastOk) {
                    val cSnap = cRef.get().await()
                    if (!cSnap.exists()) {
                        cRef.set(
                            mapOf(
                                "ownerUid" to ownerUid,
                                "deviceId" to deviceId,
                                "model" to safeModel,
                                "androidVersion" to safeVer,
                                "online" to (node.online && node.cloudEnabled),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ),
                        ).await()
                        knownCodeDocs.add(codeKey)
                    } else if (cSnap.getString("ownerUid") == ownerUid) {
                        cRef.update(codeUpdate).await()
                        knownCodeDocs.add(codeKey)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun setDeviceOffline(deviceId: String, deviceCode: String): Result<Unit> {
        val ref = deviceDoc(deviceId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "online" to false,
                        "videoStreaming" to false,
                        "viewerActive" to false,
                        "activeViewerUid" to null,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            if (deviceCode.matches(Prefs.CODE_REGEX)) {
                val cRef = codeDoc(deviceCode)
                if (cRef.get().await().exists()) {
                    cRef.update(
                        mapOf(
                            "online" to false,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun disconnectRemoteViewer(deviceId: String): Result<Unit> {
        val ref = deviceDoc(deviceId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "viewerActive" to false,
                        "activeViewerUid" to null,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun setViewerState(
        targetDeviceId: String,
        viewerActive: Boolean,
        viewerQuality: Long,
        viewerUid: String,
        sessionCode: String,
    ): Result<Unit> {
        val ref = deviceDoc(targetDeviceId)
        return try {
            ref.update(
                mapOf(
                    "viewerActive" to viewerActive,
                    "viewerQuality" to viewerQuality.coerceIn(160L, 1440L),
                    "activeViewerUid" to (if (viewerActive) viewerUid else null),
                    "lastSessionCode" to sessionCode,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun publishFrame(ownerUid: String, deviceId: String, frame: LiveFrame): Result<Unit> {
        val ref = latestFrameDoc(deviceId)
        return try {
            val payload = mapOf(
                "ownerUid" to ownerUid,
                "seq" to frame.seq.coerceAtLeast(0L),
                "width" to frame.width.coerceIn(1L, 4096L),
                "height" to frame.height.coerceIn(1L, 4096L),
                "isFullShot" to frame.isFullShot,
                "imageBase64" to frame.imageBase64,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeLatestFrame(targetDeviceId: String): Flow<LiveFrame?> {
        val ref = latestFrameDoc(targetDeviceId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(LiveFrame::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun publishVirtualFrame(ownerUid: String, deviceId: String, frame: LiveFrame): Result<Unit> {
        val ref = virtualFrameDoc(deviceId)
        return try {
            val payload = mapOf(
                "ownerUid" to ownerUid,
                "seq" to frame.seq.coerceAtLeast(0L),
                "width" to frame.width.coerceIn(1L, 4096L),
                "height" to frame.height.coerceIn(1L, 4096L),
                "isFullShot" to frame.isFullShot,
                "imageBase64" to frame.imageBase64,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeVirtualFrame(targetDeviceId: String): Flow<LiveFrame?> {
        val ref = virtualFrameDoc(targetDeviceId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(LiveFrame::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun publishInstalledApps(
        ownerUid: String,
        deviceId: String,
        apps: List<InstalledAppEntry>,
    ): Result<Unit> {
        val ref = installedAppsDoc(deviceId)
        return try {
            val serialized = apps.take(900).map { entry ->
                mapOf(
                    "label" to entry.label.take(100),
                    "packageName" to entry.packageName.take(150),
                    "systemApp" to entry.systemApp,
                )
            }
            ref.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "apps" to serialized,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeInstalledApps(targetDeviceId: String): Flow<List<InstalledAppEntry>> {
        val ref = installedAppsDoc(targetDeviceId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(InstalledAppsPayload::class.java, ServerTimestampBehavior.ESTIMATE)
                        ?.apps
                        .orEmpty()
                } else {
                    emptyList()
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun publishNotifications(
        ownerUid: String,
        deviceId: String,
        listenerEnabled: Boolean,
        notifications: List<RemoteNotificationEntry>,
    ): Result<Unit> {
        val ref = notificationsDoc(deviceId)
        return try {
            val serialized = notifications.take(80).map { n ->
                mapOf(
                    "key" to n.key.take(160),
                    "packageName" to n.packageName.take(120),
                    "appLabel" to n.appLabel.take(80),
                    "title" to n.title.take(200),
                    "text" to n.text.take(500),
                    "postedAtMs" to n.postedAtMs,
                    "ongoing" to n.ongoing,
                )
            }
            ref.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "listenerEnabled" to listenerEnabled,
                    "notifications" to serialized,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeNotifications(targetDeviceId: String): Flow<RemoteNotificationsPayload?> {
        val ref = notificationsDoc(targetDeviceId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(RemoteNotificationsPayload::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun sendCommand(
        targetDeviceId: String,
        senderUid: String,
        senderDeviceId: String,
        pinCode: String,
        payload: String,
    ): Result<String> {
        val col = commandsCollection(targetDeviceId)
        val cmdId = "cmd_${UUID.randomUUID().toString().replace("-", "")}"
        val ref = col.document(cmdId)
        return try {
            val data = mapOf(
                "senderUid" to senderUid,
                "senderDeviceId" to senderDeviceId.take(64),
                "pin" to pinCode,
                "payload" to payload.take(2000),
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            ref.set(data).await()
            Result.success(cmdId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observePendingCommands(deviceId: String): Flow<List<RemoteCommand>> {
        val col = commandsCollection(deviceId)
        return col.whereEqualTo("status", "pending")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RemoteCommand::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.LIST, col.path)
                }
                throw error
            }
    }

    fun observeCommand(targetDeviceId: String, commandId: String): Flow<RemoteCommand?> {
        val ref = commandsCollection(targetDeviceId).document(commandId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(RemoteCommand::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun markCommandResult(
        deviceId: String,
        commandId: String,
        status: String,
        errorMessage: String? = null,
    ): Result<Unit> {
        val ref = commandsCollection(deviceId).document(commandId)
        return try {
            val updateMap = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (errorMessage != null) {
                updateMap["errorMessage"] = errorMessage.take(300)
            }
            ref.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // ================= Messenger Chat & Audio/Video Calls =================

    suspend fun ensureChatThread(
        localUid: String,
        localDeviceId: String,
        localDeviceName: String,
        targetDevice: DeviceNode,
        authCode: String,
    ): Result<ChatThread> {
        val chatId = deterministicChatId(localDeviceId, targetDevice.id)
        val ref = chatDoc(chatId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(ChatThread::class.java, ServerTimestampBehavior.ESTIMATE)
                if (existing != null) {
                    val safeLocal = localDeviceName.take(120).ifBlank { "My Device" }
                    val safeTarget = targetDevice.displayTitle().take(120)
                    val namePatch = mutableMapOf<String, Any>()
                    if (existing.userADeviceId == localDeviceId && existing.userADeviceName != safeLocal) {
                        namePatch["userADeviceName"] = safeLocal
                    }
                    if (existing.userBDeviceId == targetDevice.id && existing.userBDeviceName != safeTarget) {
                        namePatch["userBDeviceName"] = safeTarget
                    }
                    if (namePatch.isNotEmpty()) {
                        namePatch["updatedAt"] = FieldValue.serverTimestamp()
                        ref.update(namePatch).await()
                    }
                    return Result.success(existing)
                }
            }

            val participants = listOf(localUid, targetDevice.ownerUid).distinct()
            val safeLocal = localDeviceName.take(120).ifBlank { "Android Device" }
            val safeTarget = targetDevice.displayTitle().take(120)
            val payload = mapOf(
                "participantUids" to participants,
                "targetDeviceId" to targetDevice.id,
                "authCode" to authCode,
                "initiatedByUid" to localUid,
                "userAUid" to localUid,
                "userADeviceId" to localDeviceId,
                "userADeviceName" to safeLocal,
                "userBUid" to targetDevice.ownerUid,
                "userBDeviceId" to targetDevice.id,
                "userBDeviceName" to safeTarget,
                "lastMessageText" to "চ্যাট শুরু হয়েছে",
                "lastMessageType" to "text",
                "lastSenderUid" to localUid,
                "lastSenderDeviceName" to safeLocal,
                "activeCallId" to null,
                "activeCallType" to null,
                "activeCallStatus" to null,
                "activeCallerUid" to null,
                "activeCallerName" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            ref.set(payload).await()
            val created = ref.get().await().toObject(ChatThread::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: ChatThread(
                    id = chatId,
                    participantUids = participants,
                    targetDeviceId = targetDevice.id,
                    authCode = authCode,
                    initiatedByUid = localUid,
                    userAUid = localUid,
                    userADeviceId = localDeviceId,
                    userADeviceName = safeLocal,
                    userBUid = targetDevice.ownerUid,
                    userBDeviceId = targetDevice.id,
                    userBDeviceName = safeTarget,
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> {
        val col = chatsCollection()
        return col.whereArrayContains("participantUids", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatThread::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.LIST, col.path)
                }
                throw error
            }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        val col = messagesCollection(chatId)
        return col.orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.LIST, col.path)
                }
                throw error
            }
    }

    suspend fun sendTextMessage(
        chatId: String,
        senderUid: String,
        senderDeviceId: String,
        senderDeviceName: String,
        text: String,
    ): Result<Unit> = sendChatMessageInternal(
        chatId = chatId,
        senderUid = senderUid,
        senderDeviceId = senderDeviceId,
        senderDeviceName = senderDeviceName,
        type = "text",
        text = text.trim().take(2000),
        imageBase64 = null,
        threadLastType = "text",
        threadPreview = text.trim().take(180),
    )

    suspend fun sendPhotoMessage(
        chatId: String,
        senderUid: String,
        senderDeviceId: String,
        senderDeviceName: String,
        imageBase64: String,
        caption: String = "",
    ): Result<Unit> = sendChatMessageInternal(
        chatId = chatId,
        senderUid = senderUid,
        senderDeviceId = senderDeviceId,
        senderDeviceName = senderDeviceName,
        type = "photo",
        text = caption.trim().take(500),
        imageBase64 = imageBase64,
        threadLastType = "photo",
        threadPreview = caption.trim().ifBlank { "📷 ফটো পাঠিয়েছেন" }.take(180),
    )

    private suspend fun sendChatMessageInternal(
        chatId: String,
        senderUid: String,
        senderDeviceId: String,
        senderDeviceName: String,
        type: String,
        text: String,
        imageBase64: String?,
        threadLastType: String,
        threadPreview: String,
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
        val msgRef = messagesCollection(chatId).document(msgId)
        val cRef = chatDoc(chatId)
        val safeName = senderDeviceName.take(120).ifBlank { "Android Device" }
        return try {
            val payload = mutableMapOf<String, Any>(
                "chatId" to chatId,
                "senderUid" to senderUid,
                "senderDeviceId" to senderDeviceId.take(64),
                "senderDeviceName" to safeName,
                "type" to type,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            if (imageBase64 != null) {
                payload["imageBase64"] = imageBase64
            }
            msgRef.set(payload).await()
            cRef.update(
                mapOf(
                    "lastMessageText" to threadPreview,
                    "lastMessageType" to threadLastType,
                    "lastSenderUid" to senderUid,
                    "lastSenderDeviceName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun startCall(
        chatId: String,
        callerUid: String,
        callerDeviceId: String,
        callerName: String,
        calleeUid: String,
        callType: String,
    ): Result<CallSession> {
        val callId = "call_${UUID.randomUUID().toString().replace("-", "")}"
        val ref = callDoc(chatId, callId)
        val cRef = chatDoc(chatId)
        val safeCallerName = callerName.take(120).ifBlank { "Android Device" }
        return try {
            val payload = mapOf(
                "chatId" to chatId,
                "callerUid" to callerUid,
                "callerDeviceId" to callerDeviceId,
                "callerName" to safeCallerName,
                "calleeUid" to calleeUid,
                "callType" to callType,
                "status" to "ringing",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            ref.set(payload).await()
            val label = if (callType == "video") "📹 ভিডিও কল" else "📞 অডিও কল"
            cRef.update(
                mapOf(
                    "activeCallId" to callId,
                    "activeCallType" to callType,
                    "activeCallStatus" to "ringing",
                    "activeCallerUid" to callerUid,
                    "activeCallerName" to safeCallerName,
                    "lastMessageText" to label,
                    "lastMessageType" to "call_$callType",
                    "lastSenderUid" to callerUid,
                    "lastSenderDeviceName" to safeCallerName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            val logMsgId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
            messagesCollection(chatId).document(logMsgId).set(
                mapOf(
                    "chatId" to chatId,
                    "senderUid" to callerUid,
                    "senderDeviceId" to callerDeviceId.take(64),
                    "senderDeviceName" to safeCallerName,
                    "type" to "call",
                    "text" to "$label শুরু করেছেন",
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            Result.success(
                CallSession(
                    id = callId,
                    chatId = chatId,
                    callerUid = callerUid,
                    callerDeviceId = callerDeviceId,
                    callerName = safeCallerName,
                    calleeUid = calleeUid,
                    callType = callType,
                    status = "ringing",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        chatId: String,
        callId: String,
        status: String,
    ): Result<Unit> {
        val ref = callDoc(chatId, callId)
        val cRef = chatDoc(chatId)
        return try {
            ref.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            val chatPatch = if (status == "active") {
                mapOf<String, Any?>(
                    "activeCallStatus" to "active",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            } else {
                mapOf<String, Any?>(
                    "activeCallId" to null,
                    "activeCallType" to null,
                    "activeCallStatus" to null,
                    "activeCallerUid" to null,
                    "activeCallerName" to null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }
            cRef.update(chatPatch).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeCall(chatId: String, callId: String): Flow<CallSession?> {
        val ref = callDoc(chatId, callId)
        return ref.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(CallSession::class.java, ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    suspend fun publishCallStream(
        chatId: String,
        callId: String,
        participantUid: String,
        seq: Long,
        audioBase64: String,
        videoBase64: String,
        muted: Boolean,
        cameraOn: Boolean,
    ): Result<Unit> {
        val ref = callStreamDoc(chatId, callId, participantUid)
        return try {
            ref.set(
                mapOf(
                    "senderUid" to participantUid,
                    "seq" to seq,
                    "audioBase64" to audioBase64,
                    "videoBase64" to videoBase64,
                    "muted" to muted,
                    "cameraOn" to cameraOn,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observePeerStream(chatId: String, callId: String, peerUid: String): Flow<CallMediaStream?> {
        val ref = callStreamDoc(chatId, callId, peerUid)
        return ref.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(CallMediaStream::class.java, ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                if (error !is CancellationException || error.cause != null) {
                    handleFirestoreError(error, OperationType.GET, ref.path)
                }
                throw error
            }
    }

    // ================= Future Admin Panel Sync & Real-Time Block Enforcement =================

    suspend fun syncUserRegistration(
        uid: String,
        email: String?,
        displayName: String,
        deviceId: String,
        deviceCode: String,
        deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion: String = Build.VERSION.RELEASE ?: "14",
        appVersion: String = "1.0",
    ): Result<Unit> {
        if (uid.isBlank() || deviceId.isBlank() || !deviceCode.matches(Prefs.CODE_REGEX)) {
            return Result.failure(IllegalArgumentException("Invalid user registration telemetry"))
        }
        val ref = userDoc(uid)
        val safeEmail = email?.trim().orEmpty().take(160)
        val safeName = displayName.trim().take(120).ifBlank { deviceModel.take(120).ifBlank { "vitRemote User" } }
        val safeModel = deviceModel.trim().take(120).ifBlank { "Android Device" }
        val safeVer = androidVersion.trim().take(40).ifBlank { "14" }
        val safeAppVer = appVersion.trim().take(40).ifBlank { "1.0" }
        return try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "uid" to uid,
                        "email" to safeEmail,
                        "displayName" to safeName,
                        "lastDeviceId" to deviceId.take(128),
                        "lastDeviceCode" to deviceCode,
                        "deviceModel" to safeModel,
                        "androidVersion" to safeVer,
                        "appVersion" to safeAppVer,
                        "role" to "user",
                        "blocked" to false,
                        "blockReason" to "",
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "email" to safeEmail,
                        "displayName" to safeName,
                        "lastDeviceId" to deviceId.take(128),
                        "lastDeviceCode" to deviceCode,
                        "deviceModel" to safeModel,
                        "androidVersion" to safeVer,
                        "appVersion" to safeAppVer,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeUserAccount(uid: String): Flow<UserAccountRecord?> {
        val ref = userDoc(uid)
        return ref.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(UserAccountRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                val fsEx = generateSequence(error) { it.cause }
                    .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsEx == null && error is CancellationException) throw error
                if (fsEx != null) handleFirestoreError(fsEx, OperationType.GET, ref.path)
                emit(null)
            }
    }

    fun observeBlockedDevice(deviceId: String): Flow<BlockedDeviceRecord?> {
        val ref = blockedDeviceDoc(deviceId)
        return ref.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(BlockedDeviceRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                val fsEx = generateSequence(error) { it.cause }
                    .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsEx == null && error is CancellationException) throw error
                if (fsEx != null) handleFirestoreError(fsEx, OperationType.GET, ref.path)
                emit(null)
            }
    }

    fun observeGlobalAppConfig(): Flow<AdminGlobalConfig?> {
        val ref = globalConfigDoc()
        return ref.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(AdminGlobalConfig::class.java, ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                val fsEx = generateSequence(error) { it.cause }
                    .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsEx == null && error is CancellationException) throw error
                if (fsEx != null) handleFirestoreError(fsEx, OperationType.GET, ref.path)
                emit(null)
            }
    }

    fun observeAdminAccessStatus(uid: String, deviceId: String): Flow<AdminAccessStatus> = combine(
        observeUserAccount(uid),
        observeBlockedDevice(deviceId),
        observeGlobalAppConfig(),
    ) { userAcc, devBlock, globalCfg ->
        val userBlocked = userAcc?.blocked == true
        val deviceBlocked = devBlock?.blocked == true
        val reason = when {
            deviceBlocked && !devBlock?.reason.isNullOrBlank() -> devBlock!!.reason
            userBlocked && !userAcc?.blockReason.isNullOrBlank() -> userAcc!!.blockReason
            userBlocked || deviceBlocked -> DEFAULT_BLOCK_NOTICE
            else -> ""
        }
        AdminAccessStatus(
            blocked = userBlocked || deviceBlocked,
            blockReason = reason,
            blockedByDevice = deviceBlocked,
            maintenanceMode = globalCfg?.maintenanceMode == true,
            maintenanceNotice = globalCfg?.maintenanceNotice.orEmpty(),
            globalNotice = globalCfg?.globalNotice.orEmpty(),
            minVersionCode = globalCfg?.minVersionCode ?: 1L,
        )
    }

    companion object {
        const val DEFAULT_BLOCK_NOTICE = "তোমাকে ব্লক করা হয়েছে তুমি খারাপ কাজে ব্যবহার করবো"
        const val LATEST_FRAME_ID = "latest"
        const val VIRTUAL_FRAME_ID = "virtual"
        const val INSTALLED_APPS_ID = "installedApps"
        const val NOTIFICATIONS_ID = "notifications"

        fun deterministicChatId(deviceA: String, deviceB: String): String {
            val pair = listOf(deviceA.take(40), deviceB.take(40)).sorted()
            return "chat_${pair[0]}_${pair[1]}"
        }

        fun maskEmail(email: String?): String {
            val raw = email?.trim().orEmpty()
            val at = raw.indexOf('@')
            if (at <= 0) return "Google account"
            val local = raw.substring(0, at)
            val domain = raw.substring(at)
            val prefix = local.take(2)
            return "$prefix***$domain"
        }
    }
}
