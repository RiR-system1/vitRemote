package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DeviceBinding(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerEmailMasked: String = "",
    val deviceCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DeviceCodeLookup(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val deviceId: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val online: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DeviceNode(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val deviceCode: String = "",
    val model: String = "",
    val deviceAlias: String = "",
    val androidVersion: String = "",
    val sdk: Long = 24L,
    val online: Boolean = false,
    val cloudEnabled: Boolean = true,
    val videoStreaming: Boolean = false,
    val screenWidth: Long = 0L,
    val screenHeight: Long = 0L,
    val batteryLevel: Long = -1L,
    val batteryTempTenths: Long = 300L,
    val charging: Boolean = false,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val storageUsedMb: Long = 0L,
    val storageTotalMb: Long = 0L,
    val brightness: Long = -1L,
    val torchOn: Boolean = false,
    val ringing: Boolean = false,
    val clipboardText: String = "",
    val captureMode: String = "",
    val viewerActive: Boolean = false,
    val viewerQuality: Long = 480L,
    val virtualScreenActive: Boolean = false,
    val virtualScreenPackage: String = "",
    val virtualScreenStatus: String = "",
    val activeViewerUid: String? = null,
    val lastSessionCode: String? = null,
    val foregroundPackage: String = "",
    val foregroundLabel: String = "",
    val foregroundChangedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun displayTitle(): String = deviceAlias.ifBlank { model.ifBlank { "Android Device" } }
}

@Keep
@IgnoreExtraProperties
data class LiveFrame(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val seq: Long = 0L,
    val width: Long = 1L,
    val height: Long = 1L,
    @get:PropertyName("isFullShot")
    @PropertyName("isFullShot")
    val isFullShot: Boolean = false,
    val imageBase64: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RemoteCommand(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderDeviceId: String = "",
    val pin: String = "",
    val payload: String = "",
    val status: String = "pending",
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SavedDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetDeviceId: String = "",
    val deviceCode: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val alias: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun displayTitle(): String = alias.ifBlank { model.ifBlank { "Android Device" } }
}

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val targetDeviceId: String = "",
    val authCode: String = "",
    val initiatedByUid: String = "",
    val userAUid: String = "",
    val userADeviceId: String = "",
    val userADeviceName: String = "",
    val userBUid: String = "",
    val userBDeviceId: String = "",
    val userBDeviceName: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastSenderUid: String = "",
    val lastSenderDeviceName: String = "",
    val activeCallId: String? = null,
    val activeCallType: String? = null,
    val activeCallStatus: String? = null,
    val activeCallerUid: String? = null,
    val activeCallerName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun peerDisplayName(myDeviceId: String, myUid: String): String = when {
        userADeviceId == myDeviceId -> userBDeviceName.ifBlank { "রিমোট ডিভাইস" }
        userBDeviceId == myDeviceId -> userADeviceName.ifBlank { "রিমোট ডিভাইস" }
        userAUid == myUid -> userBDeviceName.ifBlank { "রিমোট ডিভাইস" }
        else -> userADeviceName.ifBlank { "রিমোট ডিভাইস" }
    }

    fun peerUid(myUid: String): String =
        if (userAUid == myUid) userBUid else userAUid
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderUid: String = "",
    val senderDeviceId: String = "",
    val senderDeviceName: String = "",
    val type: String = "text",
    val text: String = "",
    val imageBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerUid: String = "",
    val callerDeviceId: String = "",
    val callerName: String = "",
    val calleeUid: String = "",
    val callType: String = "audio",
    val status: String = "ringing",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CallMediaStream(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val seq: Long = 0L,
    val audioBase64: String = "",
    val videoBase64: String = "",
    val muted: Boolean = false,
    val cameraOn: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class InstalledAppEntry(
    val label: String = "",
    val packageName: String = "",
    val systemApp: Boolean = false,
)

@Keep
@IgnoreExtraProperties
data class InstalledAppsPayload(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val apps: List<InstalledAppEntry> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RemoteNotificationEntry(
    val key: String = "",
    val packageName: String = "",
    val appLabel: String = "",
    val title: String = "",
    val text: String = "",
    val postedAtMs: Long = 0L,
    val ongoing: Boolean = false,
)

@Keep
@IgnoreExtraProperties
data class RemoteNotificationsPayload(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val listenerEnabled: Boolean = false,
    val notifications: List<RemoteNotificationEntry> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class UserAccountRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val lastDeviceId: String = "",
    val lastDeviceCode: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val appVersion: String = "",
    val role: String = "user",
    val blocked: Boolean = false,
    val blockReason: String = "",
    val blockedBy: String? = null,
    val blockedAt: Timestamp? = null,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BlockedDeviceRecord(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val blocked: Boolean = false,
    val reason: String = "",
    val blockedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AdminGlobalConfig(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceNotice: String = "",
    val minVersionCode: Long = 1L,
    val globalNotice: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class AdminAccessStatus(
    val blocked: Boolean = false,
    val blockReason: String = "",
    val blockedByDevice: Boolean = false,
    val maintenanceMode: Boolean = false,
    val maintenanceNotice: String = "",
    val globalNotice: String = "",
    val minVersionCode: Long = 1L,
)
