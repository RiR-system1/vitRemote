package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.Prefs
import com.example.R
import com.example.ai.VoiceEngineState
import com.example.data.DeviceNode
import com.example.data.InstalledAppEntry
import com.example.data.RemoteNotificationEntry
import com.example.data.SavedDevice
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConnectByCodeDialog(
    state: ConnectDialogState,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    if (!state.visible) return
    val s = LocalAppStrings.current
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!state.loading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.PhonelinkSetup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(s.connectDialogTitle) },
        text = {
            Column {
                Text(
                    text = s.connectDialogBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(9)
                        codeInput = digits
                    },
                    label = { Text(s.connectDialogHint) },
                    placeholder = { Text("482915730") },
                    supportingText = {
                        val err = state.error
                        if (err != null) {
                            Text(err, color = MaterialTheme.colorScheme.error)
                        } else if (codeInput.length == 9) {
                            Text("${Prefs.formatCode(codeInput)}")
                        } else {
                            Text("${codeInput.length}/9")
                        }
                    },
                    isError = state.error != null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!state.loading && codeInput.length == 9) {
                                onConnect(codeInput)
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_code_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(codeInput) },
                enabled = !state.loading && codeInput.length == 9,
                modifier = Modifier.testTag("connect_code_submit"),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.loading) s.searchingAction else s.connectAction)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.loading,
            ) {
                Text(s.cancelButton)
            }
        },
    )
}

@Composable
fun RemoteDevicesTab(
    devicesState: DevicesUiState,
    savedDevices: List<SavedDevice>,
    localDeviceId: String,
    sessionState: RemoteSessionState,
    aiSystemEnabled: Boolean = false,
    virtualScreenAiEnabled: Boolean = false,
    remoteScreenAiEnabled: Boolean = false,
    voiceState: VoiceEngineState = VoiceEngineState(),
    onTriggerRemoteAiVoice: (targetVirtual: Boolean) -> Unit = {},
    onShowConnectDialog: () -> Unit,
    onReconnectSaved: (SavedDevice) -> Unit,
    onRenameSaved: (String, String) -> Unit,
    onTogglePinSaved: (String, Boolean) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onOpenOwnDevice: (DeviceNode) -> Unit,
    onSendCommand: (String) -> Unit,
    onChangeQuality: (Int) -> Unit,
    onPullClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onRequestApps: () -> Unit,
    onOpenAppReal: (String) -> Unit,
    onOpenAppVirtual: (String) -> Unit,
    onToggleVirtualMode: (Boolean) -> Unit,
    onCloseVirtualScreen: () -> Unit,
    onRequestNotifications: () -> Unit,
    onToggleWatchedApp: (String) -> Unit = {},
    onDismissAppWatchAlert: () -> Unit = {},
    onToggleRemoteScreenTouch: () -> Unit = {},
    onOpenChat: () -> Unit,
    onCloseSession: () -> Unit,
) {
    if (sessionState.targetDevice != null) {
        BackHandler { onCloseSession() }
        RemoteConsoleScreen(
            session = sessionState,
            aiSystemEnabled = aiSystemEnabled,
            virtualScreenAiEnabled = virtualScreenAiEnabled,
            remoteScreenAiEnabled = remoteScreenAiEnabled,
            voiceState = voiceState,
            onTriggerRemoteAiVoice = onTriggerRemoteAiVoice,
            onSendCommand = onSendCommand,
            onChangeQuality = onChangeQuality,
            onPullClipboard = onPullClipboard,
            onDismissClipboard = onDismissClipboard,
            onRequestApps = onRequestApps,
            onOpenAppReal = onOpenAppReal,
            onOpenAppVirtual = onOpenAppVirtual,
            onToggleVirtualMode = onToggleVirtualMode,
            onCloseVirtualScreen = onCloseVirtualScreen,
            onRequestNotifications = onRequestNotifications,
            onToggleWatchedApp = onToggleWatchedApp,
            onDismissAppWatchAlert = onDismissAppWatchAlert,
            onToggleRemoteScreenTouch = onToggleRemoteScreenTouch,
            onOpenChat = onOpenChat,
            onBack = onCloseSession,
        )
        return
    }

    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("ডিভাইসের নিকনেম দিন") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(60) },
                    label = { Text("নাম (যেমন: অফিস ফোন)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSaved(target.targetDeviceId, renameDraft)
                        renameTarget = null
                    },
                ) {
                    Text("সেভ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("বাতিল")
                }
            },
        )
    }

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
                Text(
                    text = LocalAppStrings.current.tabRemoteControl,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FilledTonalButton(
                    onClick = onShowConnectDialog,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("remote_tab_connect_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhonelinkSetup,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_connect_device))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (savedDevices.isNotEmpty()) {
                    val pinnedList = savedDevices.filter { it.pinned }
                    val regularList = savedDevices.filterNot { it.pinned }

                    if (pinnedList.isNotEmpty()) {
                        item {
                            Text(
                                text = "ফেভারিট পিন করা ডিভাইস",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                        }
                        items(pinnedList, key = { "pin_${it.targetDeviceId}" }) { saved ->
                            SavedDeviceCard(
                                saved = saved,
                                onConnect = { onReconnectSaved(saved) },
                                onTogglePin = { onTogglePinSaved(saved.targetDeviceId, saved.pinned) },
                                onRename = {
                                    renameDraft = saved.alias.ifBlank { saved.model }
                                    renameTarget = saved
                                },
                                onRemove = { onRemoveSaved(saved.targetDeviceId) },
                            )
                        }
                    }

                    if (regularList.isNotEmpty()) {
                        item {
                            Text(
                                text = "রিসেন্ট কানেক্টেড ডিভাইস",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            )
                        }
                        items(regularList, key = { "saved_${it.targetDeviceId}" }) { saved ->
                            SavedDeviceCard(
                                saved = saved,
                                onConnect = { onReconnectSaved(saved) },
                                onTogglePin = { onTogglePinSaved(saved.targetDeviceId, saved.pinned) },
                                onRename = {
                                    renameDraft = saved.alias.ifBlank { saved.model }
                                    renameTarget = saved
                                },
                                onRemove = { onRemoveSaved(saved.targetDeviceId) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "আপনার অ্যাকাউন্টের ডিভাইস",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }

                when (devicesState) {
                    is DevicesUiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is DevicesUiState.Error -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = devicesState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                    }
                    is DevicesUiState.Success -> {
                        if (devicesState.devices.isEmpty() && savedDevices.isEmpty()) {
                            item {
                                EmptyRemoteCard(onConnectClick = onShowConnectDialog)
                            }
                        } else {
                            items(devicesState.devices, key = { "own_${it.id}" }) { dev ->
                                DeviceListCard(
                                    device = dev,
                                    isSelf = dev.id == localDeviceId,
                                    onClick = { onOpenOwnDevice(dev) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRemoteCard(onConnectClick: () -> Unit) {
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
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "যেকোনো ডিভাইসে কানেক্ট করুন",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "অন্য ফোনের ৯-ডিজিট স্থায়ী কোড ব্যবহার করে পৃথিবীর যেকোনো জায়গা থেকে স্ক্রিন দেখুন এবং নিয়ন্ত্রণ করুন।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConnectClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_connect_device))
            }
        }
    }
}

@Composable
private fun SavedDeviceCard(
    saved: SavedDevice,
    onConnect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (saved.pinned) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onConnect)
            .testTag("saved_device_${saved.targetDeviceId}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saved.displayTitle(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "কোড: ${Prefs.formatCode(saved.deviceCode)} · ${saved.model}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (saved.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (saved.pinned) "Unpin" else "Pin favorite",
                    tint = if (saved.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Rename device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove saved device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceListCard(
    device: DeviceNode,
    isSelf: Boolean,
    onClick: () -> Unit,
) {
    val statusColors = LocalStatusColors.current
    val online = device.online && device.cloudEnabled
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = online && !isSelf, onClick = onClick)
            .testTag("remote_device_${device.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isSelf) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = "এই ডিভাইস",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "কোড: ${Prefs.formatCode(device.deviceCode)} · Android ${device.androidVersion}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (device.batteryLevel >= 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${device.batteryLevel}%${if (device.charging) " চার্জিং" else ""} · ${device.captureMode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                color = if (online) statusColors.okContainer else statusColors.badContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = if (online) "Online" else "Offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (online) statusColors.ok else statusColors.bad,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun RemoteConsoleScreen(
    session: RemoteSessionState,
    aiSystemEnabled: Boolean,
    virtualScreenAiEnabled: Boolean,
    remoteScreenAiEnabled: Boolean,
    voiceState: VoiceEngineState,
    onTriggerRemoteAiVoice: (targetVirtual: Boolean) -> Unit,
    onSendCommand: (String) -> Unit,
    onChangeQuality: (Int) -> Unit,
    onPullClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onRequestApps: () -> Unit,
    onOpenAppReal: (String) -> Unit,
    onOpenAppVirtual: (String) -> Unit,
    onToggleVirtualMode: (Boolean) -> Unit,
    onCloseVirtualScreen: () -> Unit,
    onRequestNotifications: () -> Unit,
    onToggleWatchedApp: (String) -> Unit,
    onDismissAppWatchAlert: () -> Unit,
    onToggleRemoteScreenTouch: () -> Unit,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val statusColors = LocalStatusColors.current
    val dev = session.targetDevice ?: return
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var textDraft by remember { mutableStateOf("") }
    var showExtraTools by remember { mutableStateOf(true) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showAppWatchDialog by remember { mutableStateOf(false) }
    var extraTab by remember { mutableIntStateOf(0) } // 0: Utilities, 1: Telemetry Monitor, 2: Media
    var brightnessSlider by remember(dev.brightness) {
        mutableFloatStateOf(if (dev.brightness in 10..255) dev.brightness.toFloat() else 128f)
    }

    if (showAppWatchDialog) {
        RemoteAppWatchPickerDialog(
            deviceName = dev.displayTitle(),
            apps = session.installedApps,
            watchedPackages = session.watchedPackages,
            loading = session.appsLoading,
            onRefresh = onRequestApps,
            onToggleApp = onToggleWatchedApp,
            onDismiss = { showAppWatchDialog = false },
        )
    }

    if (showAppsDialog) {
        RemoteInstalledAppsDialog(
            deviceName = dev.displayTitle(),
            apps = session.installedApps,
            loading = session.appsLoading,
            initialVirtualMode = session.virtualModeActive || (virtualScreenAiEnabled && !remoteScreenAiEnabled),
            aiSystemEnabled = aiSystemEnabled,
            virtualScreenAiEnabled = virtualScreenAiEnabled,
            remoteScreenAiEnabled = remoteScreenAiEnabled,
            onRefresh = onRequestApps,
            onLaunchReal = { app ->
                onOpenAppReal(app.packageName)
                Toast.makeText(context, "${app.label} (রিয়েল স্ক্রিন) খোলা হচ্ছে...", Toast.LENGTH_SHORT).show()
                showAppsDialog = false
            },
            onLaunchVirtual = { app ->
                onOpenAppVirtual(app.packageName)
                Toast.makeText(context, "${app.label} (ভার্চুয়াল স্ক্রিন) চালু হচ্ছে...", Toast.LENGTH_SHORT).show()
                showAppsDialog = false
            },
            onVoiceOpenApp = { useVirtual ->
                showAppsDialog = false
                if (!aiSystemEnabled) {
                    Toast.makeText(
                        context,
                        "হোমপেজ থেকে 'AI সিস্টেম চালু করুন' বাটনটি অন করুন",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onTriggerRemoteAiVoice(useVirtual)
                }
            },
            onDismiss = { showAppsDialog = false },
        )
    }

    if (showNotificationsDialog) {
        RemoteNotificationsDialog(
            deviceName = dev.displayTitle(),
            notifications = session.notifications,
            listenerEnabled = session.notificationsListenerEnabled,
            loading = session.notificationsLoading,
            onRefresh = onRequestNotifications,
            onDismiss = { showNotificationsDialog = false },
        )
    }

    fun toNorm(x: Float, y: Float): Pair<Int, Int> {
        val w = canvasSize.width.coerceAtLeast(1)
        val h = canvasSize.height.coerceAtLeast(1)
        val nx = ((x / w) * 10000f).roundToInt().coerceIn(0, 10000)
        val ny = ((y / h) * 10000f).roundToInt().coerceIn(0, 10000)
        return nx to ny
    }

    val aspect = if (dev.screenWidth > 0 && dev.screenHeight > 0) {
        dev.screenWidth.toFloat() / dev.screenHeight.toFloat()
    } else {
        9f / 19.5f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar with Ping Meter, Quality, and Prominent Messenger "চ্যাট" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("remote_back_button")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Disconnect")
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dev.displayTitle(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.pingMs != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val goodPing = session.pingMs < 400
                        Surface(
                            color = if (goodPing) statusColors.okContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = "${session.pingMs} ms",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (goodPing) statusColors.ok else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = session.statusBanner
                        ?: "Seq #${session.frameSeq} · ${dev.batteryLevel}% · ${"%.1f".format(session.fpsEstimate)} fps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onOpenChat,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("remote_chat_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Chat,
                    contentDescription = "চ্যাট",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("চ্যাট", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }

        // App Watch & Remote Screen Touch-Lock Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = {
                    if (session.installedApps.isEmpty()) onRequestApps()
                    showAppWatchDialog = true
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("app_watch_notif_button"),
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                val watchSuffix = if (session.watchedPackages.isNotEmpty()) " (${session.watchedPackages.size})" else ""
                Text(
                    text = "এপ্স নোটিফিকেশন$watchSuffix",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            FilterChip(
                selected = !session.remoteScreenTouchEnabled,
                onClick = {
                    onToggleRemoteScreenTouch()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (session.remoteScreenTouchEnabled) Icons.Outlined.TouchApp else Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        text = if (session.remoteScreenTouchEnabled) {
                            "রিমোট স্ক্রিন সিস্টেম: চালু"
                        } else {
                            "রিমোট স্ক্রিন: অফ (শুধু দেখা যাবে)"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                modifier = Modifier.testTag("remote_screen_touch_toggle"),
            )
        }

        // Real-Time Watched App Launch Alert Banner
        AnimatedVisibility(visible = !session.appWatchAlert.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("app_watch_alert_banner"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = session.appWatchAlert.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissAppWatchAlert) {
                        Text("ঠিক আছে", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Pulled Two-Way Clipboard Banner
        AnimatedVisibility(visible = !session.pulledClipboard.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "রিমোট ক্লিপবোর্ড পাওয়া গেছে:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = session.pulledClipboard.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = {
                            val txt = session.pulledClipboard.orEmpty()
                            clipboard.setText(AnnotatedString(txt))
                            Toast.makeText(context, "নিজের ক্লিপবোর্ডে কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            onDismissClipboard()
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কপি")
                    }
                }
            }
        }

        // Virtual Screen vs Real Screen mode switcher when virtual screen is active
        AnimatedVisibility(
            visible = session.virtualModeActive || dev.virtualScreenActive || session.virtualFrameBitmap != null,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = !session.virtualModeActive,
                        onClick = { onToggleVirtualMode(false) },
                        label = { Text("রিয়েল স্ক্রিন") },
                        modifier = Modifier.testTag("viewer_mode_real"),
                    )
                    FilterChip(
                        selected = session.virtualModeActive,
                        onClick = { onToggleVirtualMode(true) },
                        label = {
                            val pkgShort = dev.virtualScreenPackage.substringAfterLast('.').ifBlank { "অ্যাপ" }
                            Text("ভার্চুয়াল স্ক্রিন · $pkgShort", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("viewer_mode_virtual"),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onCloseVirtualScreen,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "ভার্চুয়াল স্ক্রিন বন্ধ করুন",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Virtual Screen AI / Remote Real Screen AI voice control bar
        val targetIsVirtual = session.virtualModeActive || (virtualScreenAiEnabled && !remoteScreenAiEnabled)
        val aiModeReady = (targetIsVirtual && virtualScreenAiEnabled) || (!targetIsVirtual && remoteScreenAiEnabled)
        AnimatedVisibility(visible = virtualScreenAiEnabled || remoteScreenAiEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (targetIsVirtual) "ভার্চুয়াল স্ক্রিন AI মোড" else "রিয়েল স্ক্রিন AI মোড",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = when {
                                !aiSystemEnabled -> "বাটন ১ (AI সিস্টেম) অন করুন"
                                voiceState.isListening -> "শুনছি: ${voiceState.partialText.ifBlank { "বলুন..." }}"
                                voiceState.isThinking -> "AI কমান্ড বুঝছে ও চালাচ্ছে..."
                                !aiModeReady -> "এই স্ক্রিনের AI বাটন হোমপেজে অফ আছে"
                                voiceState.lastReply.isNotBlank() -> voiceState.lastReply
                                else -> "অ্যাপ খুলতে বা টাচ করতে ভয়েস বলুন"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (!aiSystemEnabled) {
                                Toast.makeText(
                                    context,
                                    "হোমপেজ থেকে 'AI সিস্টেম চালু করুন' বাটনটি অন করুন",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                onTriggerRemoteAiVoice(targetIsVirtual)
                            }
                        },
                        enabled = !voiceState.isThinking,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("remote_ai_voice_button"),
                    ) {
                        if (voiceState.isThinking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Mic, contentDescription = "AI ভয়েস", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI ভয়েস", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Live screen + side hardware rail
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                var dragStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                var dragEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }

                Box(
                    modifier = Modifier
                        .aspectRatio(aspect.coerceIn(0.4f, 2.2f), matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(dev.id, session.virtualModeActive, session.remoteScreenTouchEnabled) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (!session.remoteScreenTouchEnabled) {
                                        Toast.makeText(
                                            context,
                                            "রিমোট স্ক্রিন সিস্টেম অফ আছে — শুধু স্ক্রিন দেখা যাবে",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@detectTapGestures
                                    }
                                    val (nx, ny) = toNorm(offset.x, offset.y)
                                    val prefix = if (session.virtualModeActive) "Vt" else "t"
                                    onSendCommand("$prefix:$nx,$ny")
                                },
                                onLongPress = { offset ->
                                    if (!session.remoteScreenTouchEnabled) {
                                        Toast.makeText(
                                            context,
                                            "রিমোট স্ক্রিন সিস্টেম অফ আছে — শুধু স্ক্রিন দেখা যাবে",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@detectTapGestures
                                    }
                                    val (nx, ny) = toNorm(offset.x, offset.y)
                                    val prefix = if (session.virtualModeActive) "Vl" else "l"
                                    onSendCommand("$prefix:$nx,$ny")
                                },
                            )
                        }
                        .pointerInput(dev.id, session.virtualModeActive, session.remoteScreenTouchEnabled) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!session.remoteScreenTouchEnabled) return@detectDragGestures
                                    val p = toNorm(offset.x, offset.y)
                                    dragStart = p
                                    dragEnd = p
                                },
                                onDrag = { change, _ ->
                                    if (!session.remoteScreenTouchEnabled) return@detectDragGestures
                                    change.consume()
                                    dragEnd = toNorm(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    if (!session.remoteScreenTouchEnabled) {
                                        dragStart = null
                                        dragEnd = null
                                        return@detectDragGestures
                                    }
                                    val s = dragStart
                                    val e = dragEnd
                                    if (s != null && e != null) {
                                        val prefix = if (session.virtualModeActive) "Vs" else "s"
                                        onSendCommand("$prefix:${s.first},${s.second},${e.first},${e.second},260")
                                    }
                                    dragStart = null
                                    dragEnd = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragEnd = null
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = if (session.virtualModeActive) session.virtualFrameBitmap else session.frameBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = if (session.virtualModeActive) "Virtual screen" else "Remote device screen",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (session.virtualModeActive) {
                                    dev.virtualScreenStatus.ifBlank { "ভার্চুয়াল স্ক্রিন চালু হচ্ছে..." }
                                } else {
                                    "লাইভ স্ক্রিন লোড হচ্ছে..."
                                },
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Hardware & quick-actions side rail
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RailButton(
                    icon = Icons.Outlined.PowerSettingsNew,
                    cd = "Power / Lock",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onSendCommand("P") },
                )
                RailButton(
                    icon = if (dev.torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    cd = "Torch",
                    container = if (dev.torchOn) Color(0xFFFFF3C4) else MaterialTheme.colorScheme.surfaceContainer,
                    tint = if (dev.torchOn) Color(0xFF9E6B00) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSendCommand("f:toggle") },
                )
                RailButton(
                    icon = if (dev.ringing) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    cd = "Find Device Ring",
                    container = if (dev.ringing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                    tint = if (dev.ringing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSendCommand("r:toggle") },
                )
                RailButton(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    cd = "Volume Up",
                    onClick = { onSendCommand("v:up") },
                )
                RailButton(
                    icon = Icons.Outlined.VolumeDown,
                    cd = "Volume Down",
                    onClick = { onSendCommand("v:down") },
                )
                RailButton(
                    icon = Icons.Outlined.Notifications,
                    cd = "Notifications",
                    onClick = { onSendCommand("a:notif") },
                )
                RailButton(
                    icon = Icons.Outlined.Apps,
                    cd = "App Drawer",
                    onClick = { onSendCommand("a:allapps") },
                )
                RailButton(
                    icon = Icons.Outlined.CameraAlt,
                    cd = "Full Screenshot",
                    onClick = { onSendCommand("c") },
                )
            }
        }

        // Expandable 20+ Tools Card: Utilities (Brightness/Torch/Ring/Settings/2-way Clipboard), Live Telemetry Chart, Media/Scroll & Quality
        AnimatedVisibility(visible = showExtraTools) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = extraTab == 0,
                            onClick = { extraTab = 0 },
                            label = { Text("টুলস ও সেটিংস") },
                        )
                        FilterChip(
                            selected = extraTab == 1,
                            onClick = { extraTab = 1 },
                            label = { Text("লাইভ মনিটর") },
                        )
                        FilterChip(
                            selected = extraTab == 2,
                            onClick = { extraTab = 2 },
                            label = { Text("মিডিয়া ও কোয়ালিটি") },
                        )
                    }

                    when (extraTab) {
                        0 -> {
                            // Brightness Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.BrightnessHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Slider(
                                    value = brightnessSlider,
                                    onValueChange = { brightnessSlider = it },
                                    onValueChangeFinished = {
                                        onSendCommand("B:${brightnessSlider.roundToInt()}")
                                    },
                                    valueRange = 10f..255f,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(brightnessSlider * 100f / 255f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier.width(38.dp),
                                    textAlign = TextAlign.End,
                                )
                            }

                            // Quick Settings Deep Links & Two-way Clipboard Pull
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(onClick = onPullClipboard) {
                                    Icon(Icons.Outlined.ContentPasteGo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ক্লিপবোর্ড আনুন")
                                }
                                FilledTonalButton(onClick = { onSendCommand("o:wifi") }) {
                                    Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wi-Fi")
                                }
                                FilledTonalButton(onClick = { onSendCommand("o:bt") }) {
                                    Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bluetooth")
                                }
                                Button(
                                    onClick = {
                                        showAppsDialog = true
                                        onRequestApps()
                                    },
                                    modifier = Modifier.testTag("open_remote_apps_button"),
                                ) {
                                    Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এপ্স")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        showNotificationsDialog = true
                                        onRequestNotifications()
                                    },
                                    modifier = Modifier.testTag("open_remote_notifications_button"),
                                ) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("নোটিফিকেশন")
                                }
                                FilledTonalButton(onClick = { onSendCommand("o:display") }) {
                                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Display")
                                }
                                FilledTonalButton(onClick = { onSendCommand("o:sound") }) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeMute, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sound")
                                }
                            }
                        }

                        1 -> {
                            // Live System Monitor Card (RAM, Storage, Battery Temp, FPS, Latency)
                            val ramPct = if (dev.ramTotalMb > 0) (dev.ramUsedMb.toFloat() / dev.ramTotalMb).coerceIn(0f, 1f) else 0f
                            val stoPct = if (dev.storageTotalMb > 0) (dev.storageUsedMb.toFloat() / dev.storageTotalMb).coerceIn(0f, 1f) else 0f
                            TelemetryBarRow(
                                label = "RAM (${dev.ramUsedMb}/${dev.ramTotalMb} MB)",
                                progress = ramPct,
                            )
                            TelemetryBarRow(
                                label = "Storage (${"%.1f".format(dev.storageUsedMb / 1024f)}/${"%.1f".format(dev.storageTotalMb / 1024f)} GB)",
                                progress = stoPct,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "তাপমাত্রা: ${"%.1f".format(dev.batteryTempTenths / 10f)}°C",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = "পিং: ${session.pingMs?.let { "$it ms" } ?: "..."} · ${"%.1f".format(session.fpsEstimate)} FPS",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                )
                            }
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val scPrefix = if (session.virtualModeActive) "Vg" else "g"
                                FilledTonalIconButton(onClick = { onSendCommand("$scPrefix:up") }) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Scroll Up")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("$scPrefix:down") }) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Scroll Down")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("$scPrefix:left") }) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Swipe Left")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("$scPrefix:right") }) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Swipe Right")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("M:prev") }) {
                                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "Media Previous")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("M:playpause") }) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Media Play/Pause")
                                }
                                FilledTonalIconButton(onClick = { onSendCommand("M:next") }) {
                                    Icon(Icons.Outlined.SkipNext, contentDescription = "Media Next")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("ভিডিও কোয়ালিটি:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                listOf(360, 480, 720).forEach { q ->
                                    FilterChip(
                                        selected = session.qualityWidth == q,
                                        onClick = { onChangeQuality(q) },
                                        label = { Text("${q}p") },
                                        modifier = Modifier.padding(horizontal = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Remote text + two-way clipboard bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = textDraft,
                onValueChange = { textDraft = it },
                placeholder = { Text("লিখুন বা ক্লিপবোর্ডে পাঠান...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textDraft.isNotEmpty()) {
                            onSendCommand("x:$textDraft")
                            textDraft = ""
                        }
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (textDraft.isNotEmpty()) {
                        onSendCommand("x:$textDraft")
                        textDraft = ""
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Type text")
            }
            IconButton(
                onClick = {
                    if (textDraft.isNotEmpty()) {
                        onSendCommand("k:$textDraft")
                        textDraft = ""
                    }
                },
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = "Push to remote clipboard")
            }
            IconButton(onClick = onPullClipboard) {
                Icon(Icons.Outlined.ContentPasteGo, contentDescription = "Pull from remote clipboard")
            }
            IconButton(onClick = { onSendCommand("b") }) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Backspace")
            }
            IconButton(onClick = { onSendCommand("e") }) {
                Icon(Icons.Outlined.KeyboardReturn, contentDescription = "Enter")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Navigation + Extra Tools Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onSendCommand(if (session.virtualModeActive) "Va:back" else "a:back")
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { onSendCommand("a:home") },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Circle, contentDescription = "Home")
                }
                IconButton(
                    onClick = { onSendCommand("a:recents") },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.CropSquare, contentDescription = "Recents")
                }
                IconButton(
                    onClick = { showExtraTools = !showExtraTools },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("extra_tools_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = "Extra remote tools",
                        tint = if (showExtraTools) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onOpenChat,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = "চ্যাট খুলুন",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryBarRow(
    label: String,
    progress: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cd: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = container,
        shape = CircleShape,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = cd,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RemoteInstalledAppsDialog(
    deviceName: String,
    apps: List<InstalledAppEntry>,
    loading: Boolean,
    initialVirtualMode: Boolean,
    aiSystemEnabled: Boolean,
    virtualScreenAiEnabled: Boolean,
    remoteScreenAiEnabled: Boolean,
    onRefresh: () -> Unit,
    onLaunchReal: (InstalledAppEntry) -> Unit,
    onLaunchVirtual: (InstalledAppEntry) -> Unit,
    onVoiceOpenApp: (useVirtual: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableIntStateOf(0) } // 0: All, 1: Downloaded only
    var useVirtualScreen by remember { mutableStateOf(initialVirtualMode) }
    val downloadedCount = remember(apps) { apps.count { !it.systemApp } }
    val filtered = remember(apps, query, filterMode) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            val modeMatch = filterMode == 0 || !app.systemApp
            val queryMatch = q.isEmpty() ||
                app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            modeMatch && queryMatch
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ফোনের সব এপ্স")
                    Text(
                        text = "$deviceName · মোট ${apps.size}টি অ্যাপ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_remote_apps_button"),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "তালিকা রিফ্রেশ করুন")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Two-system Mode Selector: Real Screen vs Virtual Screen (with AI indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !useVirtualScreen,
                        onClick = { useVirtualScreen = false },
                        label = {
                            Text(if (remoteScreenAiEnabled) "রিয়েল স্ক্রিন AI" else "রিয়েল স্ক্রিন")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apps_mode_real"),
                    )
                    FilterChip(
                        selected = useVirtualScreen,
                        onClick = { useVirtualScreen = true },
                        label = {
                            Text(if (virtualScreenAiEnabled) "ভার্চুয়াল স্ক্রিন AI" else "ভার্চুয়াল স্ক্রিন")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apps_mode_virtual"),
                    )
                }

                if ((useVirtualScreen && virtualScreenAiEnabled) || (!useVirtualScreen && remoteScreenAiEnabled)) {
                    FilledTonalButton(
                        onClick = { onVoiceOpenApp(useVirtualScreen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .testTag("apps_dialog_voice_button"),
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!aiSystemEnabled) {
                                "AI সিস্টেম (বাটন ১) চালু করে মুখে বলুন"
                            } else if (useVirtualScreen) {
                                "🎤 মুখে বলুন (ভার্চুয়াল স্ক্রিনে অ্যাপ খুলবে)"
                            } else {
                                "🎤 মুখে বলুন (রিমোট স্ক্রিনে অ্যাপ খুলবে)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Surface(
                    color = if (useVirtualScreen) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (useVirtualScreen) {
                            "ভার্চুয়াল স্ক্রিন: ট্যাপ করলে আপনার অ্যাপের ভেতর ভার্চুয়াল ডিসপ্লেতে অ্যাপটি চালু হবে এবং এখানেই টাচ/AI কাজ করবে।"
                        } else {
                            "রিয়েল স্ক্রিন: ট্যাপ করলে সরাসরি অন্য ইউজারের ফোনের মূল স্ক্রিনে অ্যাপটি ওপেন হবে।"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("অ্যাপ খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_apps_search_input"),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("সব (${apps.size})") },
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("ডাউনলোড ($downloadedCount)") },
                    )
                }

                when {
                    loading && apps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "ইউজার ফোনের সব এপ্স লোড হচ্ছে...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "কোনো অ্যাপ পাওয়া যায়নি। রিফ্রেশ চাপুন।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                Card(
                                    onClick = {
                                        if (useVirtualScreen) onLaunchVirtual(app) else onLaunchReal(app)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("remote_app_item_${app.packageName}"),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (app.systemApp) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            modifier = Modifier.size(38.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = app.label.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (app.systemApp) {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    },
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.label,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (useVirtualScreen) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else if (app.systemApp) {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                        ) {
                                            Text(
                                                text = if (useVirtualScreen) "ভার্চুয়াল" else if (app.systemApp) "সিস্টেম" else "রিয়েল",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (useVirtualScreen) {
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                } else if (app.systemApp) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        },
    )
}

@Composable
private fun RemoteNotificationsDialog(
    deviceName: String,
    notifications: List<RemoteNotificationEntry>,
    listenerEnabled: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val filtered = remember(notifications, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            notifications
        } else {
            notifications.filter { n ->
                n.appLabel.lowercase().contains(q) ||
                    n.title.lowercase().contains(q) ||
                    n.text.lowercase().contains(q) ||
                    n.packageName.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ফোনের সব নোটিফিকেশন")
                    Text(
                        text = "$deviceName · ${notifications.size}টি সক্রিয় নোটিফিকেশন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_remote_notifications_button"),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "রিফ্রেশ করুন")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!listenerEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "রিমোট ফোনে 'নোটিফিকেশন অ্যাক্সেস' পারমিশন চালু করা নেই। ওই ফোনে পারমিশন চালু করলে সব নোটিফিকেশন এখানে লাইভ দেখা যাবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("নোটিফিকেশন খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_notifications_search_input"),
                )

                when {
                    loading && notifications.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "নোটিফিকেশন লোড হচ্ছে...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "কোনো নোটিফিকেশন পাওয়া যায়নি।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.key }) { item ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("remote_notif_item_${item.key}"),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = item.appLabel.ifBlank { item.packageName },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (item.postedAtMs > 0L) {
                                                Text(
                                                    text = formatter.format(Date(item.postedAtMs)),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (item.title.isNotBlank()) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (item.text.isNotBlank()) {
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        },
    )
}

@Composable
private fun RemoteAppWatchPickerDialog(
    deviceName: String,
    apps: List<InstalledAppEntry>,
    watchedPackages: Set<String>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(apps, search) {
        val q = search.trim().lowercase()
        apps.filter { entry ->
            q.isEmpty() ||
                entry.label.lowercase().contains(q) ||
                entry.packageName.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "এপ্স নোটিফিকেশন ওয়াচ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "$deviceName · টিক দেওয়া অ্যাপে ঢুকলে সতর্কবার্তা আসবে",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "অ্যাপ তালিকা রিফ্রেশ")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("অ্যাপ খুঁজুন (যেমন: WhatsApp, bKash...)") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    loading && apps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "কোনো অ্যাপ পাওয়া যায়নি। রিফ্রেশ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { item ->
                                val isChecked = item.packageName in watchedPackages
                                Surface(
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onToggleApp(item.packageName) },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggleApp(item.packageName) },
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = item.packageName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("সম্পন্ন (${watchedPackages.size})")
            }
        },
    )
}


