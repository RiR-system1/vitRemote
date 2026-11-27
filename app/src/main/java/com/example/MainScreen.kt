package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.VoiceEngineState
import com.example.ui.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalStatusColors

@Immutable
data class RemoteUiState(
    val userEmail: String,
    val deviceId: String,
    val deviceCode: String,
    val deviceAlias: String,
    val statusText: String,
    val statusOk: Boolean,
    val cloudEnabled: Boolean,
    val cloudOnline: Boolean,
    val viewerActive: Boolean,
    val accessibilityEnabled: Boolean,
    val screenCaptureActive: Boolean,
    val showScreenLock: Boolean,
    val screenLockActive: Boolean,
    val batteryUnrestricted: Boolean,
    val writeSettingsGranted: Boolean,
    val showNotificationsPermission: Boolean,
    val notificationsGranted: Boolean,
    val notificationListenerGranted: Boolean = false,
    val runtimeMediaAndNotifGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val geminiKeyConfigured: Boolean = false,
    val aiSystemEnabled: Boolean = false,
    val virtualScreenAiEnabled: Boolean = false,
    val remoteScreenAiEnabled: Boolean = false,
    val ownDeviceAiEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val globalNotice: String = "",
    val voiceState: VoiceEngineState = VoiceEngineState(),
    val deviceRows: List<Pair<String, String>>,
)

@Composable
fun VitRemoteHostTab(
    state: RemoteUiState,
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    currentLanguage: String = Prefs.LANG_BN,
    onSelectLanguage: (String) -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onConnectDeviceClick: () -> Unit,
    onUpdateAlias: (String) -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onDisconnectViewer: () -> Unit,
    onToggleCloud: (Boolean) -> Unit,
    onEnableAccessibility: () -> Unit,
    onToggleScreenCapture: (Boolean) -> Unit,
    onAllowScreenLock: () -> Unit,
    onAllowBattery: () -> Unit,
    onAllowWriteSettings: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowNotificationListener: () -> Unit,
    onAllowOverlay: () -> Unit,
    onAllowContacts: () -> Unit,
    onToggleAiSystem: (Boolean) -> Unit,
    onToggleVirtualScreenAi: (Boolean) -> Unit,
    onToggleRemoteScreenAi: (Boolean) -> Unit,
    onToggleOwnDeviceAi: (Boolean) -> Unit,
    onTriggerOwnDeviceVoice: () -> Unit,
    onOpenAiChat: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onChangeApiKey: () -> Unit,
    onHowToUse: () -> Unit = {},
    onSignOut: () -> Unit,
) {
    val s = LocalAppStrings.current
    val statusColors = LocalStatusColors.current
    val dotColor by animateColorAsState(
        targetValue = if (state.statusOk) statusColors.ok else statusColors.bad,
        label = "status_dot",
    )
    val pillBgColor by animateColorAsState(
        targetValue = if (state.statusOk) statusColors.okContainer else statusColors.badContainer,
        label = "status_pill",
    )

    var showAliasDialog by remember { mutableStateOf(false) }
    var aliasDraft by remember(state.deviceAlias) { mutableStateOf(state.deviceAlias) }
    var langMenuOpen by remember { mutableStateOf(false) }

    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text(s.nicknameDialogTitle) },
            text = {
                OutlinedTextField(
                    value = aliasDraft,
                    onValueChange = { aliasDraft = it.take(60) },
                    label = { Text(s.nicknameDialogHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAlias(aliasDraft)
                        showAliasDialog = false
                    },
                ) {
                    Text(s.saveButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text(s.cancelButton)
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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 28.dp),
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (state.userEmail.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = state.userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = pillBgColor,
                        shape = CircleShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = dotColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(
                        onClick = onOpenSupport,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("top_support_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SupportAgent,
                            contentDescription = s.supportButton,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = s.supportButton,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = s.signOutDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Homepage Top Action Strip: How to Use + Dark Theme Toggle + Language Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onHowToUse,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("how_to_use_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = s.howToUseButton,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = s.howToUseButton,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }

                FilledTonalButton(
                    onClick = onToggleDarkTheme,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dark_theme_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = if (isDarkTheme) s.lightThemeButton else s.darkThemeButton,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDarkTheme) s.lightThemeButton else s.darkThemeButton,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }

                Box {
                    FilledTonalButton(
                        onClick = { langMenuOpen = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("language_toggle_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = s.languageButtonLabel,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s.languageButtonLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(
                        expanded = langMenuOpen,
                        onDismissRequest = { langMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.languageBengali) },
                            leadingIcon = {
                                if (currentLanguage == Prefs.LANG_BN) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                langMenuOpen = false
                                onSelectLanguage(Prefs.LANG_BN)
                            },
                            modifier = Modifier.testTag("lang_option_bn"),
                        )
                        DropdownMenuItem(
                            text = { Text(s.languageEnglish) },
                            leadingIcon = {
                                if (currentLanguage == Prefs.LANG_EN) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                langMenuOpen = false
                                onSelectLanguage(Prefs.LANG_EN)
                            },
                            modifier = Modifier.testTag("lang_option_en"),
                        )
                    }
                }
            }

            if (state.globalNotice.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("admin_global_notice_banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.globalNotice,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Hero Action: Connect to another device worldwide
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = s.controlHeroTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = s.controlHeroDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onConnectDeviceClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("connect_device_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhonelinkSetup,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.connectDeviceButton,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Permanent Single Device Code Card (NO New Code button)
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.cloudOnline) {
                                    s.cloudOnlineReady
                                } else if (state.cloudEnabled) {
                                    s.cloudWaitingA11y
                                } else {
                                    s.cloudRelayOff
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = s.permanentCodeSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onCopyCode)
                            .testTag("permanent_code_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Pin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Prefs.formatCode(state.deviceCode),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onCopyCode,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_code_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.copyCodeButton)
                        }
                        OutlinedButton(
                            onClick = onShareCode,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_code_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.shareCodeButton)
                        }
                    }

                    AnimatedVisibility(visible = state.viewerActive) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = statusColors.okContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = statusColors.ok,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = s.liveSessionBanner,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = statusColors.ok,
                                        modifier = Modifier.weight(1f),
                                    )
                                    FilledTonalButton(
                                        onClick = onDisconnectViewer,
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("disconnect_viewer_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LinkOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(s.disconnectButton)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Permissions
            SectionTitle(text = s.sectionPermissions)
            SectionCard {
                ActionPermissionRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    label = s.labelAccessibility,
                    buttonText = if (state.accessibilityEnabled) s.stateOn else s.actionEnable,
                    enabled = !state.accessibilityEnabled,
                    onClick = onEnableAccessibility,
                    testTag = "accessibility_button",
                )
                SectionDivider()
                SwitchRow(
                    icon = Icons.Outlined.Cast,
                    label = s.labelScreenCapture,
                    checked = state.screenCaptureActive,
                    onCheckedChange = onToggleScreenCapture,
                    testTag = "screen_capture_switch",
                )
                if (state.showScreenLock) {
                    SectionDivider()
                    ActionPermissionRow(
                        icon = Icons.Outlined.Lock,
                        label = s.labelScreenLock,
                        buttonText = if (state.screenLockActive) s.stateOn else s.actionAllow,
                        enabled = !state.screenLockActive,
                        onClick = onAllowScreenLock,
                        testTag = "screen_lock_button",
                    )
                }
                SectionDivider()
                ActionPermissionRow(
                    icon = Icons.Outlined.BatteryChargingFull,
                    label = s.labelBattery,
                    buttonText = if (state.batteryUnrestricted) s.stateOn else s.actionAllow,
                    enabled = !state.batteryUnrestricted,
                    onClick = onAllowBattery,
                    testTag = "battery_button",
                )
                SectionDivider()
                ActionPermissionRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    label = s.labelBrightness,
                    buttonText = if (state.writeSettingsGranted) s.stateOn else s.actionAllow,
                    enabled = !state.writeSettingsGranted,
                    onClick = onAllowWriteSettings,
                    testTag = "brightness_permission_button",
                )
                SectionDivider()
                ActionPermissionRow(
                    icon = Icons.Outlined.NotificationsActive,
                    label = s.labelNotifListener,
                    buttonText = if (state.notificationListenerGranted) s.stateOn else s.actionAllow,
                    enabled = !state.notificationListenerGranted,
                    onClick = onAllowNotificationListener,
                    testTag = "notification_listener_button",
                )
                SectionDivider()
                ActionPermissionRow(
                    icon = Icons.Outlined.Layers,
                    label = s.labelOverlay,
                    buttonText = if (state.overlayGranted) s.stateOn else s.actionAllow,
                    enabled = !state.overlayGranted,
                    onClick = onAllowOverlay,
                    testTag = "overlay_permission_button",
                )
                SectionDivider()
                ActionPermissionRow(
                    icon = Icons.Outlined.Contacts,
                    label = s.labelContacts,
                    buttonText = if (state.contactsGranted) s.stateOn else s.actionAllow,
                    enabled = !state.contactsGranted,
                    onClick = onAllowContacts,
                    testTag = "contacts_permission_button",
                )
                if (state.showNotificationsPermission) {
                    SectionDivider()
                    ActionPermissionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = s.labelNotifications,
                        buttonText = if (state.notificationsGranted) s.stateOn else s.actionAllow,
                        enabled = !state.notificationsGranted,
                        onClick = onAllowNotifications,
                        testTag = "notifications_button",
                    )
                }
            }

            // 6 AI Voice Assistant System Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = s.aiSectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = onChangeApiKey,
                    modifier = Modifier.testTag("change_gemini_key_button"),
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.geminiKeyConfigured) s.keyChange else s.keyAdd,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .clickable(onClick = onChangeApiKey)
                        .padding(vertical = 6.dp)
                        .testTag("homepage_gemini_key_row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = if (state.geminiKeyConfigured) statusColors.ok else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google AI Studio Key",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = if (state.geminiKeyConfigured) s.keyConfiguredDesc else s.keyNotConfiguredDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.geminiKeyConfigured) statusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onChangeApiKey,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("homepage_gemini_key_action_button"),
                    ) {
                        Text(if (state.geminiKeyConfigured) s.keyShortChange else s.keyShortAdd)
                    }
                }
                SectionDivider()
                AiToggleRow(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = s.aiBtn1Title,
                    subtitle = s.aiBtn1Sub,
                    checked = state.aiSystemEnabled,
                    onCheckedChange = onToggleAiSystem,
                    onLabel = s.pillOn,
                    offLabel = s.pillOff,
                    testTag = "ai_btn_1_system",
                )
                SectionDivider()
                AiToggleRow(
                    icon = Icons.Outlined.DevicesOther,
                    title = s.aiBtn2Title,
                    subtitle = s.aiBtn2Sub,
                    checked = state.virtualScreenAiEnabled,
                    onCheckedChange = onToggleVirtualScreenAi,
                    onLabel = s.pillOn,
                    offLabel = s.pillOff,
                    testTag = "ai_btn_2_virtual",
                )
                SectionDivider()
                AiToggleRow(
                    icon = Icons.Outlined.PhonelinkSetup,
                    title = s.aiBtn3Title,
                    subtitle = s.aiBtn3Sub,
                    checked = state.remoteScreenAiEnabled,
                    onCheckedChange = onToggleRemoteScreenAi,
                    onLabel = s.pillOn,
                    offLabel = s.pillOff,
                    testTag = "ai_btn_3_remote",
                )
                SectionDivider()
                AiToggleRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = s.aiBtn4Title,
                    subtitle = s.aiBtn4Sub,
                    checked = state.ownDeviceAiEnabled,
                    onCheckedChange = onToggleOwnDeviceAi,
                    onLabel = s.pillOn,
                    offLabel = s.pillOff,
                    testTag = "ai_btn_4_own_device",
                )

                AnimatedVisibility(visible = state.ownDeviceAiEnabled) {
                    val v = state.voiceState
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onTriggerOwnDeviceVoice,
                                enabled = state.aiSystemEnabled && !v.isThinking,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("own_device_voice_trigger"),
                            ) {
                                if (v.isThinking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(s.aiOwnThinking)
                                } else {
                                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            !state.aiSystemEnabled -> s.aiOwnEnableBtn1
                                            v.isListening -> "${s.aiOwnListening} ${v.partialText.ifBlank { "..." }}"
                                            else -> s.aiOwnSpeakPrompt
                                        },
                                    )
                                }
                            }
                            if (v.lastCommand.isNotBlank() || v.lastReply.isNotBlank() || !v.errorText.isNullOrBlank()) {
                                val summary = when {
                                    !v.errorText.isNullOrBlank() -> "${v.errorText}"
                                    v.lastReply.isNotBlank() -> "${if (v.lastCommand.isNotBlank()) "${v.lastCommand}\n" else ""}AI: ${v.lastReply}"
                                    else -> v.lastCommand
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                SectionDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.aiBtn5Title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = s.aiBtn5Sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenAiChat,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 46.dp)
                            .testTag("ai_btn_5_chat"),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(s.aiBtn5Button)
                    }
                }

                SectionDivider()
                AiToggleRow(
                    icon = Icons.Outlined.Layers,
                    title = s.aiBtn6Title,
                    subtitle = s.aiBtn6Sub,
                    checked = state.overlayEnabled,
                    onCheckedChange = onToggleOverlay,
                    onLabel = s.pillOn,
                    offLabel = s.pillOff,
                    testTag = "ai_btn_6_overlay",
                )
            }

            // Cloud Controls & Custom Device Alias
            SectionTitle(text = s.sectionCloud)
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable {
                            aliasDraft = state.deviceAlias
                            showAliasDialog = true
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.nicknameTitle,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = state.deviceAlias.ifBlank { s.nicknameUnset },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit nickname",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                SectionDivider()
                SwitchRow(
                    icon = Icons.Outlined.CloudQueue,
                    label = s.acceptCloudSwitch,
                    checked = state.cloudEnabled,
                    onCheckedChange = onToggleCloud,
                    testTag = "cloud_remote_switch",
                )
            }

            // Device Telemetry
            SectionTitle(text = s.sectionDeviceInfo)
            SectionCard {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.deviceRows.forEachIndexed { index, (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.6f),
                            )
                        }
                        if (index < state.deviceRows.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun ActionPermissionRow(
    icon: ImageVector,
    label: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(testTag),
        ) {
            Text(text = buttonText)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun AiToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onLabel: String = "অন",
    offLabel: String = "অফ",
    testTag: String,
) {
    val statusColors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (checked) statusColors.okContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ) {
                    Text(
                        text = if (checked) onLabel else offLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (checked) statusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}

