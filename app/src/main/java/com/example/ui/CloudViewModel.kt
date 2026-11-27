package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.Prefs
import com.example.RemoteService
import com.example.ai.DeviceActionExecutor
import com.example.ai.GeminiAssistantClient
import com.example.ai.VoiceAssistantController
import com.example.call.CallMediaEngine
import com.example.data.AdminAccessStatus
import com.example.data.BindingCheckResult
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.CloudRepository
import com.example.data.DeviceNode
import com.example.data.InstalledAppEntry
import com.example.data.LiveFrame
import com.example.data.RemoteNotificationEntry
import com.example.data.SavedDevice
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

sealed interface AccountBindingState {
    data object Checking : AccountBindingState
    data class Bound(val deviceCode: String) : AccountBindingState
    data class LockedToOtherAccount(val maskedEmail: String) : AccountBindingState
    data class Error(val message: String) : AccountBindingState
}

sealed interface DevicesUiState {
    data object Loading : DevicesUiState
    data class Success(val devices: List<DeviceNode>) : DevicesUiState
    data class Error(val message: String) : DevicesUiState
}

data class ConnectDialogState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

data class RemoteSessionState(
    val targetDevice: DeviceNode? = null,
    val verifiedCode: String = "",
    val frameBitmap: Bitmap? = null,
    val frameSeq: Long = 0L,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val isFullShot: Boolean = false,
    val qualityWidth: Int = 480,
    val pingMs: Long? = null,
    val fpsEstimate: Float = 0f,
    val pulledClipboard: String? = null,
    val installedApps: List<InstalledAppEntry> = emptyList(),
    val appsLoading: Boolean = false,
    val virtualFrameBitmap: Bitmap? = null,
    val virtualFrameSeq: Long = 0L,
    val virtualModeActive: Boolean = false,
    val notifications: List<RemoteNotificationEntry> = emptyList(),
    val notificationsListenerEnabled: Boolean = true,
    val notificationsLoading: Boolean = false,
    val watchedPackages: Set<String> = emptySet(),
    val appWatchAlert: String? = null,
    val remoteScreenTouchEnabled: Boolean = true,
    val statusBanner: String? = null,
)

data class IncomingCallInfo(
    val chatId: String,
    val callId: String,
    val callType: String,
    val callerUid: String,
    val callerName: String,
)

data class ActiveCallUiState(
    val callId: String? = null,
    val chatId: String? = null,
    val callType: String = "audio",
    val status: String = "idle",
    val isCaller: Boolean = true,
    val peerName: String = "",
    val peerUid: String = "",
    val peerVideoBitmap: Bitmap? = null,
    val peerMuted: Boolean = false,
    val peerCameraOn: Boolean = false,
    val localMuted: Boolean = false,
    val localCameraOn: Boolean = false,
    val speakerOn: Boolean = true,
    val useFrontCamera: Boolean = true,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
)

class CloudViewModel(
    application: Application,
    private val repository: CloudRepository,
    private val prefs: Prefs,
    private val currentUserId: String,
    private val currentUserEmail: String?,
    private val localDeviceId: String,
    private val localModelName: String,
    private val onBindingVerified: (String) -> Unit,
) : AndroidViewModel(application) {

    private val mediaEngine = CallMediaEngine(application.applicationContext)

    private val _bindingState = MutableStateFlow<AccountBindingState>(AccountBindingState.Checking)
    val bindingState: StateFlow<AccountBindingState> = _bindingState.asStateFlow()

    val ownDevicesState: StateFlow<DevicesUiState> = repository.observeOwnDevices(currentUserId)
        .map<List<DeviceNode>, DevicesUiState> { list ->
            val sorted = list.sortedWith(
                compareByDescending<DeviceNode> { it.online }
                    .thenBy { it.id == localDeviceId }
                    .thenBy { it.displayTitle() },
            )
            DevicesUiState.Success(sorted)
        }
        .catch { error ->
            val fsEx = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsEx == null && error is CancellationException) throw error
            val root = fsEx ?: error
            Log.w(TAG, "Error observing own devices", root)
            emit(DevicesUiState.Error(root.message ?: "Failed to load devices"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DevicesUiState.Loading,
        )

    val savedDevices: StateFlow<List<SavedDevice>> = repository.observeSavedDevices(currentUserId)
        .map { list ->
            list.sortedWith(
                compareByDescending<SavedDevice> { it.pinned }
                    .thenByDescending { it.updatedAt?.seconds ?: 0L },
            )
        }
        .catch { error ->
            if (error !is CancellationException || error.cause != null) {
                Log.w(TAG, "Error observing saved devices", error)
                emit(emptyList())
            } else {
                throw error
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList(),
        )

    val chatThreads: StateFlow<List<ChatThread>> = repository.observeChats(currentUserId)
        .catch { error ->
            if (error !is CancellationException || error.cause != null) {
                Log.w(TAG, "Error observing chat threads", error)
                emit(emptyList())
            } else {
                throw error
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList(),
        )

    val adminAccessStatus: StateFlow<AdminAccessStatus> =
        repository.observeAdminAccessStatus(currentUserId, localDeviceId)
            .catch { error ->
                val fsEx = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsEx == null && error is CancellationException) throw error
                Log.w(TAG, "Admin access observer error", fsEx ?: error)
                emit(AdminAccessStatus())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = AdminAccessStatus(),
            )

    private val _connectDialog = MutableStateFlow(ConnectDialogState())
    val connectDialog: StateFlow<ConnectDialogState> = _connectDialog.asStateFlow()

    private val _session = MutableStateFlow(RemoteSessionState())
    val session: StateFlow<RemoteSessionState> = _session.asStateFlow()

    private val _activeChat = MutableStateFlow<ChatThread?>(null)
    val activeChat: StateFlow<ChatThread?> = _activeChat.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _activeCall = MutableStateFlow(ActiveCallUiState())
    val activeCall: StateFlow<ActiveCallUiState> = _activeCall.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCallInfo?>(null)
    val incomingCall: StateFlow<IncomingCallInfo?> = _incomingCall.asStateFlow()

    private var frameJob: Job? = null
    private var virtualFrameJob: Job? = null
    private var deviceJob: Job? = null
    private var appsJob: Job? = null
    private var notifJob: Job? = null
    private var pingJob: Job? = null
    private var messagesJob: Job? = null
    private var callSessionJob: Job? = null
    private var peerStreamJob: Job? = null
    private var callTimerJob: Job? = null
    private var activeTargetId: String? = null
    private var lastFrameArrivalMs = 0L
    private var mediaStartedForCallId: String? = null
    private var lastClipboardSeen: String = ""
    private var lastWatchedChangeAtMs: Long = 0L

    init {
        verifyDeviceBinding()
        observeIncomingCallsFromThreads()
    }

    private fun observeIncomingCallsFromThreads() {
        viewModelScope.launch {
            chatThreads.collect { threads ->
                val curCallId = _activeCall.value.callId
                val updatedActive = _activeChat.value?.let { cur ->
                    threads.firstOrNull { it.id == cur.id }
                }
                if (updatedActive != null) {
                    _activeChat.value = updatedActive
                }

                val ringing = threads.firstOrNull { t ->
                    val cid = t.activeCallId
                    cid != null &&
                        t.activeCallStatus == "ringing" &&
                        cid != curCallId &&
                        t.activeCallerUid != currentUserId
                }
                _incomingCall.value = ringing?.let { t ->
                    IncomingCallInfo(
                        chatId = t.id,
                        callId = t.activeCallId!!,
                        callType = t.activeCallType ?: "audio",
                        callerUid = t.activeCallerUid ?: "",
                        callerName = t.activeCallerName?.ifBlank { t.peerDisplayName(localDeviceId, currentUserId) }
                            ?: t.peerDisplayName(localDeviceId, currentUserId),
                    )
                }
            }
        }
    }



    fun localDeviceDisplayName(): String = prefs.displayName(localModelName)

    fun updateLocalDeviceAlias(alias: String) {
        prefs.deviceAlias = alias
        RemoteService.instance?.applyConfig()
    }

    fun renameSavedDevice(targetDeviceId: String, alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSavedDeviceMeta(currentUserId, targetDeviceId, alias = alias)
        }
    }

    fun togglePinSavedDevice(targetDeviceId: String, currentPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSavedDeviceMeta(currentUserId, targetDeviceId, pinned = !currentPinned)
        }
    }

    private var backgroundSyncJob: Job? = null

    fun verifyDeviceBinding() {
        _bindingState.value = AccountBindingState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val fallbackCode = prefs.deviceCode
            val quickRes = withTimeoutOrNull(5_000L) {
                repository.ensureDeviceBinding(
                    uid = currentUserId,
                    email = currentUserEmail,
                    deviceId = localDeviceId,
                    preferredCode = fallbackCode,
                )
            }

            if (quickRes != null) {
                quickRes.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is BindingCheckResult.Bound -> {
                                prefs.deviceCode = outcome.deviceCode
                                _bindingState.value = AccountBindingState.Bound(outcome.deviceCode)
                                onBindingVerified(outcome.deviceCode)
                                launch {
                                    repository.syncUserRegistration(
                                        uid = currentUserId,
                                        email = currentUserEmail,
                                        displayName = localDeviceDisplayName(),
                                        deviceId = localDeviceId,
                                        deviceCode = outcome.deviceCode,
                                        deviceModel = localModelName,
                                    )
                                }
                            }
                            is BindingCheckResult.LockedToOtherAccount -> {
                                _bindingState.value = AccountBindingState.LockedToOtherAccount(outcome.maskedEmail)
                            }
                        }
                    },
                    onFailure = {
                        _bindingState.value = AccountBindingState.Bound(fallbackCode)
                        onBindingVerified(fallbackCode)
                        launchBackgroundBindingSync(fallbackCode)
                    },
                )
            } else {
                _bindingState.value = AccountBindingState.Bound(fallbackCode)
                onBindingVerified(fallbackCode)
                launchBackgroundBindingSync(fallbackCode)
            }
        }
    }

    private fun launchBackgroundBindingSync(fallbackCode: String) {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val res = repository.ensureDeviceBinding(
                uid = currentUserId,
                email = currentUserEmail,
                deviceId = localDeviceId,
                preferredCode = fallbackCode,
            )
            res.onSuccess { outcome ->
                when (outcome) {
                    is BindingCheckResult.Bound -> {
                        prefs.deviceCode = outcome.deviceCode
                        _bindingState.value = AccountBindingState.Bound(outcome.deviceCode)
                        onBindingVerified(outcome.deviceCode)
                        repository.syncUserRegistration(
                            uid = currentUserId,
                            email = currentUserEmail,
                            displayName = localDeviceDisplayName(),
                            deviceId = localDeviceId,
                            deviceCode = outcome.deviceCode,
                            deviceModel = localModelName,
                        )
                    }
                    is BindingCheckResult.LockedToOtherAccount -> {
                        _bindingState.value = AccountBindingState.LockedToOtherAccount(outcome.maskedEmail)
                    }
                }
            }
        }
    }

    fun showConnectDialog() {
        _connectDialog.value = ConnectDialogState(visible = true, loading = false, error = null)
    }

    fun dismissConnectDialog() {
        _connectDialog.value = ConnectDialogState(visible = false, loading = false, error = null)
    }

    fun connectByCode(rawCode: String) {
        val normalized = Prefs.normalizeCode(rawCode)
        if (normalized.length != 9) {
            _connectDialog.value = _connectDialog.value.copy(
                error = "সঠিক ৯-ডিজিটের ডিভাইস কোড দিন",
            )
            return
        }
        if (normalized == prefs.deviceCode) {
            _connectDialog.value = _connectDialog.value.copy(
                error = "এটি এই ডিভাইসেরই কোড! অন্য ডিভাইসের কোড দিন।",
            )
            return
        }
        _connectDialog.value = _connectDialog.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.connectByCode(normalized, currentUserId)
            res.fold(
                onSuccess = { dev ->
                    _connectDialog.value = ConnectDialogState(visible = false)
                    openSession(dev, dev.deviceCode)
                },
                onFailure = { err ->
                    _connectDialog.value = _connectDialog.value.copy(
                        loading = false,
                        error = err.localizedMessage ?: "ডিভাইসের সাথে কানেক্ট করা যায়নি",
                    )
                },
            )
        }
    }

    fun removeSavedDevice(targetDeviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSavedDevice(currentUserId, targetDeviceId)
        }
    }

    fun openSession(device: DeviceNode, code: String) {
        closeSession()
        val cleanCode = Prefs.normalizeCode(code)
        activeTargetId = device.id
        lastFrameArrivalMs = 0L
        lastClipboardSeen = device.clipboardText
        lastWatchedChangeAtMs = device.foregroundChangedAtMs
        _session.value = RemoteSessionState(
            targetDevice = device,
            verifiedCode = cleanCode,
            qualityWidth = 480,
            appsLoading = true,
            watchedPackages = emptySet(),
            appWatchAlert = null,
            remoteScreenTouchEnabled = true,
            statusBanner = "Connecting to ${device.displayTitle()}...",
        )

        viewModelScope.launch(Dispatchers.IO) {
            val viewerRes = repository.setViewerState(
                targetDeviceId = device.id,
                viewerActive = true,
                viewerQuality = 480L,
                viewerUid = currentUserId,
                sessionCode = cleanCode,
            )
            if (viewerRes.isFailure) {
                _session.value = _session.value.copy(
                    statusBanner = viewerRes.exceptionOrNull()?.message ?: "Permission denied",
                )
                return@launch
            }

            deviceJob = viewModelScope.launch {
                repository.observeDevice(device.id)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) false else { delay(1500L); true }
                    }
                    .catch { error ->
                        Log.w(TAG, "Error observing target device", error)
                    }
                    .collect { updated ->
                        if (updated != null) {
                            val pulled = if (updated.clipboardText.isNotEmpty() && updated.clipboardText != lastClipboardSeen) {
                                lastClipboardSeen = updated.clipboardText
                                updated.clipboardText
                            } else {
                                _session.value.pulledClipboard
                            }
                            val watched = _session.value.watchedPackages
                            val nextAlert = if (
                                updated.foregroundChangedAtMs > lastWatchedChangeAtMs &&
                                updated.foregroundPackage.isNotBlank()
                            ) {
                                lastWatchedChangeAtMs = updated.foregroundChangedAtMs
                                if (updated.foregroundPackage in watched) {
                                    val label = updated.foregroundLabel.ifBlank { updated.foregroundPackage }
                                    "সতর্কতা: ইউজার এই মুহূর্তে $label অ্যাপে প্রবেশ করেছে!"
                                } else {
                                    _session.value.appWatchAlert
                                }
                            } else {
                                _session.value.appWatchAlert
                            }
                            _session.value = _session.value.copy(
                                targetDevice = updated,
                                pulledClipboard = pulled,
                                appWatchAlert = nextAlert,
                            )
                        }
                    }
            }

            frameJob = viewModelScope.launch {
                repository.observeLatestFrame(device.id)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) false else { delay(1500L); true }
                    }
                    .catch { error ->
                        Log.w(TAG, "Error observing live frame", error)
                    }
                    .collect { frame: LiveFrame? ->
                        if (frame != null && frame.imageBase64.isNotEmpty()) {
                            val bmp = withContext(Dispatchers.Default) {
                                decodeBitmap(frame.imageBase64)
                            }
                            if (bmp != null) {
                                val now = System.currentTimeMillis()
                                val fps = if (lastFrameArrivalMs > 0L) {
                                    val delta = (now - lastFrameArrivalMs).coerceAtLeast(50L)
                                    (1000f / delta).coerceIn(0.2f, 30f)
                                } else {
                                    1f
                                }
                                lastFrameArrivalMs = now
                                _session.value = _session.value.copy(
                                    frameBitmap = bmp,
                                    frameSeq = frame.seq,
                                    frameWidth = frame.width.toInt(),
                                    frameHeight = frame.height.toInt(),
                                    isFullShot = frame.isFullShot,
                                    fpsEstimate = fps,
                                    statusBanner = null,
                                )
                            }
                        }
                    }
            }

            virtualFrameJob = viewModelScope.launch {
                repository.observeVirtualFrame(device.id)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) false else { delay(1500L); true }
                    }
                    .catch { error ->
                        Log.w(TAG, "Error observing virtual frame", error)
                    }
                    .collect { vFrame: LiveFrame? ->
                        if (vFrame != null && vFrame.imageBase64.isNotEmpty()) {
                            val bmp = withContext(Dispatchers.Default) {
                                decodeBitmap(vFrame.imageBase64)
                            }
                            if (bmp != null) {
                                _session.value = _session.value.copy(
                                    virtualFrameBitmap = bmp,
                                    virtualFrameSeq = vFrame.seq,
                                )
                            }
                        }
                    }
            }

            appsJob = viewModelScope.launch {
                repository.observeInstalledApps(device.id)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) false else { delay(1500L); true }
                    }
                    .catch { error ->
                        Log.w(TAG, "Error observing installed apps", error)
                    }
                    .collect { apps ->
                        _session.value = _session.value.copy(
                            installedApps = apps,
                            appsLoading = if (apps.isNotEmpty()) false else _session.value.appsLoading,
                        )
                    }
            }

            notifJob = viewModelScope.launch {
                repository.observeNotifications(device.id)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) false else { delay(1500L); true }
                    }
                    .catch { error ->
                        Log.w(TAG, "Error observing notifications", error)
                    }
                    .collect { payload ->
                        if (payload != null) {
                            _session.value = _session.value.copy(
                                notifications = payload.notifications,
                                notificationsListenerEnabled = payload.listenerEnabled,
                                notificationsLoading = false,
                            )
                        }
                    }
            }

            pingJob = viewModelScope.launch {
                while (isActive && activeTargetId == device.id) {
                    withTimeoutOrNull(4_000L) {
                        measureLatencyOnce(device.id, cleanCode)
                    }
                    delay(3_500L)
                }
            }
        }
    }

    private suspend fun measureLatencyOnce(targetId: String, code: String) {
        val sentAt = System.currentTimeMillis()
        val res = repository.sendCommand(
            targetDeviceId = targetId,
            senderUid = currentUserId,
            senderDeviceId = localDeviceId,
            pinCode = code,
            payload = "Q:$sentAt",
        )
        val cmdId = res.getOrNull() ?: return
        try {
            repository.observeCommand(targetId, cmdId)
                .catch { }
                .collect { cmd ->
                    if (cmd != null && cmd.status == "done") {
                        val rtt = (System.currentTimeMillis() - sentAt).coerceIn(1L, 9999L)
                        _session.value = _session.value.copy(pingMs = rtt)
                        throw CancellationException("ping complete")
                    } else if (cmd?.status == "error") {
                        throw CancellationException("ping error")
                    }
                }
        } catch (_: CancellationException) {
            // Expected single-shot completion
        }
    }

    fun dismissPulledClipboard() {
        _session.value = _session.value.copy(pulledClipboard = null)
    }

    fun pullRemoteClipboard() {
        lastClipboardSeen = ""
        sendCommand("K:pull")
    }

    fun requestRemoteInstalledApps() {
        _session.value = _session.value.copy(appsLoading = true)
        sendCommand("A:list")
    }

    fun requestRemoteNotifications() {
        _session.value = _session.value.copy(notificationsLoading = true)
        sendCommand("N:list")
    }

    fun toggleWatchedApp(packageName: String) {
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        val current = _session.value.watchedPackages
        val updated = if (clean in current) current - clean else current + clean
        _session.value = _session.value.copy(watchedPackages = updated)
    }

    fun dismissAppWatchAlert() {
        _session.value = _session.value.copy(appWatchAlert = null)
    }

    fun toggleRemoteScreenTouch() {
        val next = !_session.value.remoteScreenTouchEnabled
        _session.value = _session.value.copy(
            remoteScreenTouchEnabled = next,
            statusBanner = if (next) {
                "রিমোট স্ক্রিন সিস্টেম চালু — স্ক্রিনে টাচ কাজ করবে"
            } else {
                "রিমোট স্ক্রিন সিস্টেম অফ আছে — শুধু স্ক্রিন দেখা যাবে"
            },
        )
    }

    fun openRemoteAppReal(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        _session.value = _session.value.copy(virtualModeActive = false)
        sendCommand("A:open:$cleanPkg")
    }

    fun openRemoteAppVirtual(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        _session.value = _session.value.copy(
            virtualModeActive = true,
            statusBanner = "ভার্চুয়াল স্ক্রিনে $cleanPkg খোলা হচ্ছে...",
        )
        sendCommand("A:vopen:$cleanPkg")
    }

    fun setVirtualModeActive(active: Boolean) {
        _session.value = _session.value.copy(virtualModeActive = active)
    }

    fun closeRemoteVirtualScreen() {
        _session.value = _session.value.copy(
            virtualModeActive = false,
            virtualFrameBitmap = null,
            virtualFrameSeq = 0L,
        )
        sendCommand("A:vclose")
    }

    fun setQuality(width: Int) {
        val targetId = activeTargetId ?: return
        val code = _session.value.verifiedCode
        if (code.length != 9) return
        _session.value = _session.value.copy(qualityWidth = width)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setViewerState(
                targetDeviceId = targetId,
                viewerActive = true,
                viewerQuality = width.toLong(),
                viewerUid = currentUserId,
                sessionCode = code,
            )
        }
    }

    fun sendCommand(payload: String) {
        val targetId = activeTargetId ?: return
        val code = _session.value.verifiedCode
        if (code.length != 9) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.sendCommand(
                targetDeviceId = targetId,
                senderUid = currentUserId,
                senderDeviceId = localDeviceId,
                pinCode = code,
                payload = payload,
            )
            if (res.isFailure) {
                _session.value = _session.value.copy(
                    statusBanner = res.exceptionOrNull()?.message ?: "Failed to send command",
                )
            } else {
                val cmdId = res.getOrNull() ?: return@launch
                repository.observeCommand(targetId, cmdId)
                    .catch { }
                    .collect { cmd ->
                        if (cmd?.status == "error") {
                            _session.value = _session.value.copy(
                                statusBanner = cmd.errorMessage ?: "Remote error",
                            )
                        }
                    }
            }
        }
    }

    fun closeSession() {
        val prevId = activeTargetId
        val prevCode = _session.value.verifiedCode
        activeTargetId = null
        frameJob?.cancel()
        frameJob = null
        virtualFrameJob?.cancel()
        virtualFrameJob = null
        deviceJob?.cancel()
        deviceJob = null
        appsJob?.cancel()
        appsJob = null
        notifJob?.cancel()
        notifJob = null
        pingJob?.cancel()
        pingJob = null
        _session.value = RemoteSessionState()
        if (prevId != null && prevCode.length == 9) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                repository.setViewerState(
                    targetDeviceId = prevId,
                    viewerActive = false,
                    viewerQuality = 480L,
                    viewerUid = currentUserId,
                    sessionCode = prevCode,
                )
            }
        }
    }

    // ================= Remote / Virtual AI Voice Control =================

    fun runRemoteVoiceAssistant(
        voice: VoiceAssistantController,
        targetVirtual: Boolean,
    ) {
        if (!prefs.aiSystemEnabled) {
            _session.value = _session.value.copy(
                statusBanner = "হোমপেজ থেকে 'AI সিস্টেম চালু করুন' বাটনটি অন করুন",
            )
            return
        }
        voice.startVoiceSession { spoken ->
            executeRemoteVoicePrompt(voice = voice, voiceCommand = spoken, targetVirtual = targetVirtual)
        }
    }

    fun executeRemoteVoicePrompt(
        voice: VoiceAssistantController,
        voiceCommand: String,
        targetVirtual: Boolean,
    ) {
        if (!prefs.aiSystemEnabled) {
            _session.value = _session.value.copy(statusBanner = "AI সিস্টেম বন্ধ আছে")
            return
        }
        val key = prefs.geminiApiKey
        if (key.isBlank()) {
            _session.value = _session.value.copy(statusBanner = "Google AI Studio Key সেট করা নেই")
            return
        }
        val snap = _session.value
        val screenBmp = if (targetVirtual) snap.virtualFrameBitmap ?: snap.frameBitmap else snap.frameBitmap
        val apps = snap.installedApps
        if (apps.isEmpty()) {
            requestRemoteInstalledApps()
        }

        viewModelScope.launch {
            voice.setThinking(true)
            val modeLabel = if (targetVirtual) "Remote Virtual Screen Control" else "Remote Real Screen Control"
            val planRes = GeminiAssistantClient.decideScreenAction(
                apiKey = key,
                modelName = prefs.geminiModel,
                voiceCommand = voiceCommand,
                screenBitmap = screenBmp,
                installedApps = apps,
                uiSummary = "",
                modeLabel = modeLabel,
                onModelResolved = { resolved -> prefs.geminiModel = resolved },
            )
            planRes.fold(
                onSuccess = { plan ->
                    val feedback = DeviceActionExecutor.executeRemoteOrVirtualAction(
                        plan = plan,
                        isVirtual = targetVirtual,
                        sendCommand = { cmd -> sendCommand(cmd) },
                        launchRealApp = { pkg -> openRemoteAppReal(pkg) },
                        launchVirtualApp = { pkg -> openRemoteAppVirtual(pkg) },
                    )
                    _session.value = _session.value.copy(statusBanner = "AI: $feedback")
                    voice.setThinking(false, reply = feedback)
                    voice.speak(feedback)
                },
                onFailure = { err ->
                    val msg = err.message ?: "AI কমান্ড ব্যর্থ"
                    _session.value = _session.value.copy(statusBanner = msg)
                    voice.setThinking(false, error = msg)
                    voice.speak(msg)
                },
            )
        }
    }

    // ================= Messenger Chat & Calls =================

    fun openChatFromRemoteSession(onOpened: () -> Unit) {
        val target = _session.value.targetDevice ?: return
        val code = _session.value.verifiedCode
        if (code.length != 9) return
        _chatBusy.value = true
        _chatError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.ensureChatThread(
                localUid = currentUserId,
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceDisplayName(),
                targetDevice = target,
                authCode = code,
            )
            _chatBusy.value = false
            res.fold(
                onSuccess = { thread ->
                    launch(Dispatchers.Main) {
                        openChatThread(thread)
                        onOpened()
                    }
                },
                onFailure = { e ->
                    _chatError.value = e.localizedMessage ?: "চ্যাট শুরু করা যায়নি"
                },
            )
        }
    }

    fun openChatThread(thread: ChatThread) {
        _activeChat.value = thread
        _chatError.value = null
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.observeMessages(thread.id)
                .catch { e ->
                    _chatError.value = e.localizedMessage ?: "মেসেজ লোড করা যায়নি"
                }
                .collect { msgs ->
                    _chatMessages.value = msgs
                }
        }
    }

    fun closeChatThread() {
        messagesJob?.cancel()
        messagesJob = null
        _activeChat.value = null
        _chatMessages.value = emptyList()
        _chatError.value = null
    }

    fun sendChatText(text: String) {
        val chat = _activeChat.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.sendTextMessage(
                chatId = chat.id,
                senderUid = currentUserId,
                senderDeviceId = localDeviceId,
                senderDeviceName = localDeviceDisplayName(),
                text = clean,
            )
            if (res.isFailure) {
                _chatError.value = res.exceptionOrNull()?.localizedMessage ?: "মেসেজ পাঠানো যায়নি"
            }
        }
    }

    fun sendChatPhoto(uri: Uri, caption: String = "") {
        val chat = _activeChat.value ?: return
        _chatBusy.value = true
        _chatError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val b64 = encodeGalleryImageBase64(uri)
            if (b64 == null) {
                _chatBusy.value = false
                _chatError.value = "ফটো প্রসেস করা যায়নি"
                return@launch
            }
            val res = repository.sendPhotoMessage(
                chatId = chat.id,
                senderUid = currentUserId,
                senderDeviceId = localDeviceId,
                senderDeviceName = localDeviceDisplayName(),
                imageBase64 = b64,
                caption = caption,
            )
            _chatBusy.value = false
            if (res.isFailure) {
                _chatError.value = res.exceptionOrNull()?.localizedMessage ?: "ফটো পাঠানো যায়নি"
            }
        }
    }

    fun startCall(callType: String) {
        val chat = _activeChat.value ?: return
        val peerUid = chat.peerUid(currentUserId)
        val peerName = chat.peerDisplayName(localDeviceId, currentUserId)
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.startCall(
                chatId = chat.id,
                callerUid = currentUserId,
                callerDeviceId = localDeviceId,
                callerName = localDeviceDisplayName(),
                calleeUid = peerUid,
                callType = callType,
            )
            res.fold(
                onSuccess = { session ->
                    attachToCallSession(
                        chatId = chat.id,
                        callId = session.id,
                        callType = callType,
                        isCaller = true,
                        peerName = peerName,
                        peerUid = peerUid,
                    )
                },
                onFailure = { e ->
                    _chatError.value = e.localizedMessage ?: "কল শুরু করা যায়নি"
                },
            )
        }
    }

    fun acceptIncomingCall(info: IncomingCallInfo) {
        _incomingCall.value = null
        val thread = chatThreads.value.firstOrNull { it.id == info.chatId }
        if (thread != null) {
            openChatThread(thread)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.updateCallStatus(info.chatId, info.callId, "active")
            if (res.isSuccess) {
                attachToCallSession(
                    chatId = info.chatId,
                    callId = info.callId,
                    callType = info.callType,
                    isCaller = false,
                    peerName = info.callerName,
                    peerUid = info.callerUid,
                )
            }
        }
    }

    fun declineIncomingCall(info: IncomingCallInfo) {
        _incomingCall.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCallStatus(info.chatId, info.callId, "declined")
        }
    }

    private fun attachToCallSession(
        chatId: String,
        callId: String,
        callType: String,
        isCaller: Boolean,
        peerName: String,
        peerUid: String,
    ) {
        cleanupCallJobs()
        val isVideo = callType == "video"
        _activeCall.value = ActiveCallUiState(
            callId = callId,
            chatId = chatId,
            callType = callType,
            status = if (isCaller) "ringing" else "active",
            isCaller = isCaller,
            peerName = peerName,
            peerUid = peerUid,
            localMuted = false,
            localCameraOn = isVideo,
            speakerOn = true,
            useFrontCamera = true,
            durationSeconds = 0,
        )

        if (!isCaller) {
            startMediaStreamForCall(chatId, callId, peerUid, isVideo)
        }

        callSessionJob = viewModelScope.launch {
            repository.observeCall(chatId, callId)
                .catch { e ->
                    _activeCall.value = _activeCall.value.copy(errorMessage = e.localizedMessage)
                }
                .collect { call ->
                    if (call == null) return@collect
                    _activeCall.value = _activeCall.value.copy(status = call.status)
                    when (call.status) {
                        "active" -> {
                            startMediaStreamForCall(chatId, callId, peerUid, isVideo)
                        }
                        "ended", "declined" -> {
                            mediaEngine.stop()
                            mediaStartedForCallId = null
                            delay(900)
                            cleanupCallJobs()
                            _activeCall.value = ActiveCallUiState()
                        }
                    }
                }
        }
    }

    private fun startMediaStreamForCall(
        chatId: String,
        callId: String,
        peerUid: String,
        isVideo: Boolean,
    ) {
        if (mediaStartedForCallId == callId) return
        mediaStartedForCallId = callId
        mediaEngine.muted = _activeCall.value.localMuted
        mediaEngine.cameraEnabled = _activeCall.value.localCameraOn

        mediaEngine.startAudioAndLoop(isVideoCall = isVideo) { seq, audioB64, videoB64, muted, camOn ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.publishCallStream(
                    chatId = chatId,
                    callId = callId,
                    participantUid = currentUserId,
                    seq = seq,
                    audioBase64 = audioB64,
                    videoBase64 = videoB64,
                    muted = muted,
                    cameraOn = camOn,
                )
            }
        }

        peerStreamJob?.cancel()
        peerStreamJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observePeerStream(chatId, callId, peerUid)
                .catch { }
                .collect { stream ->
                    if (stream == null) return@collect
                    if (stream.audioBase64.isNotEmpty()) {
                        mediaEngine.playPeerAudio(stream.seq, stream.audioBase64)
                    }
                    val peerBmp = if (stream.cameraOn && stream.videoBase64.isNotEmpty()) {
                        decodeBitmap(stream.videoBase64)
                    } else {
                        null
                    }
                    _activeCall.value = _activeCall.value.copy(
                        peerVideoBitmap = peerBmp ?: if (!stream.cameraOn) null else _activeCall.value.peerVideoBitmap,
                        peerMuted = stream.muted,
                        peerCameraOn = stream.cameraOn,
                    )
                }
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive && _activeCall.value.status == "active") {
                delay(1000L)
                _activeCall.value = _activeCall.value.copy(
                    durationSeconds = _activeCall.value.durationSeconds + 1,
                )
            }
        }
    }

    fun bindLocalCameraPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val state = _activeCall.value
        if (state.callId == null || !state.localCameraOn) return
        mediaEngine.cameraEnabled = true
        mediaEngine.bindCamera(lifecycleOwner, previewView, state.useFrontCamera)
    }

    fun toggleCallMute() {
        val next = !_activeCall.value.localMuted
        mediaEngine.muted = next
        _activeCall.value = _activeCall.value.copy(localMuted = next)
    }

    fun toggleCallSpeaker() {
        val next = !_activeCall.value.speakerOn
        mediaEngine.setSpeakerphone(next)
        _activeCall.value = _activeCall.value.copy(speakerOn = next)
    }

    fun toggleCallCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView?) {
        val next = !_activeCall.value.localCameraOn
        mediaEngine.cameraEnabled = next
        _activeCall.value = _activeCall.value.copy(localCameraOn = next)
        if (next && previewView != null) {
            mediaEngine.bindCamera(lifecycleOwner, previewView, _activeCall.value.useFrontCamera)
        } else if (!next) {
            mediaEngine.unbindCamera()
        }
    }

    fun flipCallCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView?) {
        val next = !_activeCall.value.useFrontCamera
        _activeCall.value = _activeCall.value.copy(useFrontCamera = next)
        if (_activeCall.value.localCameraOn && previewView != null) {
            mediaEngine.bindCamera(lifecycleOwner, previewView, next)
        }
    }

    fun endActiveCall() {
        val callId = _activeCall.value.callId
        val chatId = _activeCall.value.chatId
        mediaEngine.stop()
        mediaStartedForCallId = null
        cleanupCallJobs()
        _activeCall.value = ActiveCallUiState()
        if (callId != null && chatId != null) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                repository.updateCallStatus(chatId, callId, "ended")
            }
        }
    }

    private fun cleanupCallJobs() {
        callSessionJob?.cancel()
        callSessionJob = null
        peerStreamJob?.cancel()
        peerStreamJob = null
        callTimerJob?.cancel()
        callTimerJob = null
    }

    private fun encodeGalleryImageBase64(uri: Uri): String? = try {
        val resolver = getApplication<Application>().contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxDim / sample > 1280) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val targetW = minOf(960, bmp.width).coerceAtLeast(160)
        val targetH = (bmp.height.toLong() * targetW / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(160)
        val scaled = if (targetW < bmp.width) {
            Bitmap.createScaledBitmap(bmp, targetW, targetH, true).also {
                if (it !== bmp) bmp.recycle()
            }
        } else {
            bmp
        }

        val baos = ByteArrayOutputStream(32 * 1024)
        var q = 78
        scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
        var bytes = baos.toByteArray()
        while (bytes.size > 560_000 && q > 24) {
            baos.reset()
            q -= 12
            scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
            bytes = baos.toByteArray()
        }
        scaled.recycle()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "Gallery photo encode failed: ${e.message}")
        null
    }

    override fun onCleared() {
        endActiveCall()
        closeChatThread()
        closeSession()
        super.onCleared()
    }

    private fun decodeBitmap(b64: String): Bitmap? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val TAG = "CloudVM"
    }
}
