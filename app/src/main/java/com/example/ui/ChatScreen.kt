package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.Prefs
import com.example.R
import com.example.data.ChatMessage
import com.example.data.ChatThread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatTabScreen(
    threads: List<ChatThread>,
    activeChat: ChatThread?,
    messages: List<ChatMessage>,
    chatBusy: Boolean,
    chatError: String?,
    currentUserId: String,
    localDeviceId: String,
    onOpenThread: (ChatThread) -> Unit,
    onCloseThread: () -> Unit,
    onShowConnectDialog: () -> Unit,
    onSendText: (String) -> Unit,
    onSendPhoto: (Uri) -> Unit,
    onStartCall: (String) -> Unit,
) {
    if (activeChat != null) {
        BackHandler { onCloseThread() }
        ChatThreadScreen(
            chat = activeChat,
            messages = messages,
            busy = chatBusy,
            errorText = chatError,
            currentUserId = currentUserId,
            localDeviceId = localDeviceId,
            onBack = onCloseThread,
            onSendText = onSendText,
            onSendPhoto = onSendPhoto,
            onStartCall = onStartCall,
        )
        return
    }

    val s = LocalAppStrings.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.chatTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = s.chatSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onShowConnectDialog,
                    modifier = Modifier.testTag("chat_connect_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhonelinkSetup,
                        contentDescription = s.connectDeviceButton,
                    )
                }
            }

            if (threads.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = s.chatEmptyTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = s.chatEmptyBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onShowConnectDialog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(s.connectDeviceButton)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(threads, key = { it.id }) { thread ->
                        ChatInboxCard(
                            thread = thread,
                            peerName = thread.peerDisplayName(localDeviceId, currentUserId),
                            onClick = { onOpenThread(thread) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInboxCard(
    thread: ChatThread,
    peerName: String,
    onClick: () -> Unit,
) {
    val timeText = remember(thread.updatedAt) {
        thread.updatedAt?.toDate()?.let { d ->
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d)
        } ?: ""
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("chat_thread_${thread.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (timeText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = thread.lastMessageText.ifBlank { "মেসেজ দেখতে ট্যাপ করুন" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChatThreadScreen(
    chat: ChatThread,
    messages: List<ChatMessage>,
    busy: Boolean,
    errorText: String?,
    currentUserId: String,
    localDeviceId: String,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendPhoto: (Uri) -> Unit,
    onStartCall: (String) -> Unit,
) {
    val context = LocalContext.current
    val peerName = chat.peerDisplayName(localDeviceId, currentUserId)
    var draft by remember { mutableStateOf("") }
    var permissionNotice by remember { mutableStateOf<String?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCallType by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Zero-permission Google Play compliant Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onSendPhoto(uri)
        }
    }

    // Runtime permission launcher for Voice (RECORD_AUDIO) & Video (CAMERA + RECORD_AUDIO) calls
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        val type = pendingCallType
        pendingCallType = null
        if (type == "video") {
            if (micOk && camOk) {
                onStartCall("video")
            } else {
                permissionNotice = "ভিডিও কলের জন্য ক্যামেরা ও মাইক্রোফোন পারমিশন প্রয়োজন।"
            }
        } else if (type == "audio") {
            if (micOk) {
                onStartCall("audio")
            } else {
                permissionNotice = "অডিও কলের জন্য মাইক্রোফোন পারমিশন প্রয়োজন।"
            }
        }
    }

    fun launchWithPermissions(type: String) {
        permissionNotice = null
        val needMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        val needCam = type == "video" &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        if (!needMic && !needCam) {
            onStartCall(type)
        } else {
            pendingCallType = type
            val reqs = if (type == "video") {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            callPermissionLauncher.launch(reqs)
        }
    }

    fullScreenPhoto?.let { bmp ->
        Dialog(
            onDismissRequest = { fullScreenPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreenPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { fullScreenPhoto = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Messenger Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "কোড: ${Prefs.formatCode(chat.authCode)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { launchWithPermissions("audio") },
                    modifier = Modifier.testTag("chat_audio_call_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Audio Call",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = { launchWithPermissions("video") },
                    modifier = Modifier.testTag("chat_video_call_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Video Call",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val banner = permissionNotice ?: errorText
        if (banner != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = banner,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderDeviceId == localDeviceId ||
                    (msg.senderDeviceId.isBlank() && msg.senderUid == currentUserId)
                ChatMessageBubble(
                    message = msg,
                    isMine = isMine,
                    onPhotoClick = { bmp -> fullScreenPhoto = bmp },
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Messenger Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.testTag("chat_photo_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "Send photo from gallery",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("মেসেজ লিখুন...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            onSendText(draft)
                            draft = ""
                        }
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input"),
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSendText(draft)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("chat_send_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (draft.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onPhotoClick: (Bitmap) -> Unit,
) {
    if (message.type == "call") {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
            ) {
                Text(
                    text = "${message.senderDeviceName}: ${message.text}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        return
    }

    val decodedPhoto = remember(message.imageBase64) {
        val b64 = message.imageBase64
        if (b64.isNullOrBlank()) null
        else try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }

    val timeLabel = remember(message.createdAt) {
        message.createdAt?.toDate()?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
        } ?: ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        if (!isMine) {
            Text(
                text = message.senderDeviceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
            )
        }
        Surface(
            color = if (isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isMine) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (decodedPhoto != null) {
                    Image(
                        bitmap = decodedPhoto.asImageBitmap(),
                        contentDescription = "Shared photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhotoClick(decodedPhoto) },
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (timeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
fun IncomingCallDialog(
    info: IncomingCallInfo?,
    onAccept: (IncomingCallInfo) -> Unit,
    onDecline: (IncomingCallInfo) -> Unit,
) {
    if (info == null) return
    val context = LocalContext.current
    var permError by remember(info.callId) { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = info.callType != "video" ||
            grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micOk && camOk) {
            onAccept(info)
        } else {
            permError = "কল রিসিভ করতে মাইক্রোফোন/ক্যামেরা পারমিশন দিন"
        }
    }

    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = if (info.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        },
        title = {
            Text(
                text = if (info.callType == "video") "ভিডিও কল আসছে" else "অডিও কল আসছে",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = info.callerName,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (permError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = permError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val needMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    val needCam = info.callType == "video" &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    if (!needMic && !needCam) {
                        onAccept(info)
                    } else {
                        val perms = if (info.callType == "video") {
                            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                        } else {
                            arrayOf(Manifest.permission.RECORD_AUDIO)
                        }
                        launcher.launch(perms)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B8E3E)),
                modifier = Modifier.testTag("accept_call_button"),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("রিসিভ")
            }
        },
        dismissButton = {
            Button(
                onClick = { onDecline(info) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("decline_call_button"),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("কেটে দিন")
            }
        },
    )
}

@Composable
fun ActiveCallScreen(
    state: ActiveCallUiState,
    onBindLocalPreview: (LifecycleOwner, PreviewView) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: (LifecycleOwner, PreviewView?) -> Unit,
    onFlipCamera: (LifecycleOwner, PreviewView?) -> Unit,
    onEndCall: () -> Unit,
) {
    if (state.callId == null) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewRef by remember { mutableStateOf<PreviewView?>(null) }

    BackHandler { onEndCall() }

    val statusText = when (state.status) {
        "ringing" -> if (state.isCaller) "রিং হচ্ছে..." else "সংযুক্ত হচ্ছে..."
        "active" -> "%02d:%02d".format(state.durationSeconds / 60, state.durationSeconds % 60)
        "declined" -> "কল রিসিভ করা হয়নি"
        "ended" -> "কল শেষ হয়েছে"
        else -> state.status
    }

    Surface(
        color = Color(0xFF0E141B),
        contentColor = Color.White,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Remote video background or audio call avatar
            if (state.callType == "video" && state.peerVideoBitmap != null) {
                Image(
                    bitmap = state.peerVideoBitmap.asImageBitmap(),
                    contentDescription = "Peer video stream",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F2D3D)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF7FB3FF),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = state.peerName.ifBlank { "রিমোট ডিভাইস" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFB6C6DC),
                    )
                    if (state.status == "ringing") {
                        Spacer(modifier = Modifier.height(14.dp))
                        CircularProgressIndicator(color = Color(0xFF7FB3FF), modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Top overlay info during video call
            if (state.callType == "video") {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.peerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Text(
                            text = statusText + if (state.peerMuted) " · Muted" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD6E2F0),
                        )
                    }
                }
            }

            // Local camera PiP preview for video call
            if (state.callType == "video" && state.localCameraOn) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 74.dp, end = 14.dp)
                        .size(width = 112.dp, height = 160.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                previewRef = pv
                                onBindLocalPreview(lifecycleOwner, pv)
                            }
                        },
                        update = { pv ->
                            previewRef = pv
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Bottom Messenger call control bar
            Surface(
                color = Color(0xFF17212B).copy(alpha = 0.92f),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 28.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallControlIcon(
                        icon = if (state.localMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        cd = "Mute",
                        active = state.localMuted,
                        onClick = onToggleMute,
                    )
                    CallControlIcon(
                        icon = if (state.speakerOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.Outlined.Hearing,
                        cd = "Speaker",
                        active = state.speakerOn,
                        onClick = onToggleSpeaker,
                    )
                    if (state.callType == "video") {
                        CallControlIcon(
                            icon = if (state.localCameraOn) Icons.Filled.Videocam else Icons.Outlined.VideocamOff,
                            cd = "Toggle camera",
                            active = state.localCameraOn,
                            onClick = { onToggleCamera(lifecycleOwner, previewRef) },
                        )
                        CallControlIcon(
                            icon = Icons.Outlined.Cameraswitch,
                            cd = "Switch camera",
                            active = false,
                            onClick = { onFlipCamera(lifecycleOwner, previewRef) },
                        )
                    }
                    Surface(
                        onClick = onEndCall,
                        color = Color(0xFFE53935),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("end_call_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cd: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) Color(0xFF2D435E) else Color(0xFF222E3C),
        shape = CircleShape,
        modifier = Modifier.size(50.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = cd,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
