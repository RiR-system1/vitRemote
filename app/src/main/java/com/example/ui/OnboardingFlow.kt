package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.Prefs
import com.example.R
import com.example.RemoteUiState
import com.example.ai.GeminiAssistantClient
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeviceCheckingStepScreen(
    deviceCode: String?,
    isChecking: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val statusColors = LocalStatusColors.current
    val currentOnContinue by rememberUpdatedState(onContinue)
    var secondsLeft by remember { mutableIntStateOf(5) }
    var advanced by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) return@LaunchedEffect
        secondsLeft = 5
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        if (!advanced) {
            advanced = true
            currentOnContinue()
        }
    }

    LaunchedEffect(isChecking, errorMessage) {
        if (!isChecking && errorMessage == null && !advanced) {
            delay(750L)
            if (!advanced) {
                advanced = true
                currentOnContinue()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .testTag("onboarding_device_checking_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            } else {
                                Icon(
                                    imageVector = if (errorMessage != null) Icons.Outlined.Security else Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (errorMessage != null) MaterialTheme.colorScheme.error else statusColors.ok,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            isChecking -> "ডিভাইস চেকিং হচ্ছে..."
                            errorMessage != null -> "ডিভাইস চেকিং ব্যর্থ হয়েছে"
                            else -> "ডিভাইস যাচাই সম্পন্ন"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            isChecking -> "আপনার ফোনের হার্ডওয়্যার আইডি, গুগল অ্যাকাউন্ট এবং স্থায়ী ৯-ডিজিট কোড যাচাই করা হচ্ছে (সর্বোচ্চ ${secondsLeft} সেকেন্ড)..."
                            errorMessage != null -> errorMessage
                            else -> "এই ডিভাইসটি আপনার গুগল অ্যাকাউন্টের সাথে স্থায়ীভাবে যুক্ত হয়েছে (একটি ডিভাইস · একবার লগ ইন)।"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (errorMessage == null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (!isChecking) 1f else ((5 - secondsLeft) / 5f).coerceIn(0.12f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                        )
                    }

                    AnimatedVisibility(visible = !isChecking && !deviceCode.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "কোড: ${Prefs.formatCode(deviceCode.orEmpty())}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    if (errorMessage != null) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                        ) {
                            Text("আবার চেষ্টা করুন")
                        }
                    } else if (!isChecking) {
                        Button(
                            onClick = {
                                if (!advanced) {
                                    advanced = true
                                    currentOnContinue()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("device_checking_next_button"),
                        ) {
                            Text("পরবর্তী")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperIntroStepScreen(
    onNext: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .testTag("onboarding_developer_card"),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            .padding(vertical = 26.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                tonalElevation = 6.dp,
                                modifier = Modifier.size(76.dp),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.vitremote_icon_fg_1794809525202),
                                    contentDescription = stringResource(R.string.cd_app_icon),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(22.dp)),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                                shape = CircleShape,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "অ্যাপ নির্মাতা · অ্যাপ ডেভেলপার",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Rubaeat Islam Raja",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = "Verified Developer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = "অরুপ Raja",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${stringResource(R.string.app_name)} তৈরি করেছেন Rubaeat Islam Raja (অরুপ Raja)। গ্লোবাল রিমোট স্ক্রিন, ভার্চুয়াল অ্যাপ কন্ট্রোল, AI ভয়েস অ্যাসিস্ট্যান্ট, লাইভ নোটিফিকেশন এবং মেসেঞ্জার চ্যাট/কল সিস্টেম।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = onNext,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("developer_intro_next_button"),
                        ) {
                            Text(
                                text = "পরবর্তী",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsAllowStepScreen(
    state: RemoteUiState,
    onEnableAccessibility: () -> Unit,
    onEnableScreenCapture: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onAllowBattery: () -> Unit,
    onAllowWriteSettings: () -> Unit,
    onAllowOverlay: () -> Unit,
    onRequestAllRuntimePermissions: () -> Unit,
    onFinishToHome: () -> Unit,
) {
    var showIncompletePrompt by remember { mutableStateOf(false) }

    val checks = listOf(
        state.accessibilityEnabled,
        state.notificationListenerGranted,
        state.batteryUnrestricted,
        state.writeSettingsGranted,
        state.overlayGranted,
        state.runtimeMediaAndNotifGranted,
        state.screenCaptureActive,
    )
    val grantedCount = checks.count { it }
    val totalCount = checks.size
    val coreGranted = state.accessibilityEnabled &&
        state.notificationListenerGranted &&
        state.runtimeMediaAndNotifGranted

    if (showIncompletePrompt) {
        AlertDialog(
            onDismissRequest = { showIncompletePrompt = false },
            title = { Text("কিছু এলাও এখনো বাকি আছে") },
            text = {
                Text(
                    "রিমোট কন্ট্রোল, ভার্চুয়াল স্ক্রিন, AI ভয়েস ও লাইভ নোটিফিকেশন সঠিকভাবে কাজ করার জন্য সব এলাও চালু রাখা জরুরি। এখনই সেট করবেন নাকি পরবর্তী ধাপে যাবেন?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIncompletePrompt = false
                        onFinishToHome()
                    },
                    modifier = Modifier.testTag("confirm_proceed_home_button"),
                ) {
                    Text("পরবর্তী ধাপে যান")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncompletePrompt = false }) {
                    Text("এখনই এলাও করবো")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            if (coreGranted) {
                                onFinishToHome()
                            } else {
                                showIncompletePrompt = true
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("permissions_next_button"),
                    ) {
                        Text(
                            text = if (grantedCount == totalCount) "সব এলাও সম্পন্ন · পরবর্তী (AI Key)" else "পরবর্তী",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "সব পারমিশন এলাও করুন",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "রিমোট কন্ট্রোল, ভার্চুয়াল স্ক্রিন, AI ভয়েস, ওভারলে বাটন ও কল/চ্যাটের জন্য নিচের সব পারমিশন এলাও করে 'পরবর্তী' বাটনে ক্লিক করুন।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { grantedCount.toFloat() / totalCount.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$grantedCount/$totalCount পারমিশন অনুমোদিত",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OnboardingPermRow(
                            icon = Icons.Outlined.AccessibilityNew,
                            title = "Accessibility (টাচ ও স্ক্রিন AI)",
                            subtitle = "রিমোট টাচ, নিজের স্ক্রিন দেখা ও ভার্চুয়াল কন্ট্রোল",
                            granted = state.accessibilityEnabled,
                            onAllow = onEnableAccessibility,
                            tag = "onb_accessibility",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.NotificationsNone,
                            title = "নোটিফিকেশন অ্যাক্সেস",
                            subtitle = "অন্য মোবাইল থেকে সব নোটিফিকেশন দেখার জন্য",
                            granted = state.notificationListenerGranted,
                            onAllow = onEnableNotificationAccess,
                            tag = "onb_notif_listener",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.Cast,
                            title = "লাইভ স্ক্রিন ও ভার্চুয়াল স্ক্রিন",
                            subtitle = "ভার্চুয়াল ডিসপ্লে ও হাই-স্পিড স্ক্রিন শেয়ারিং",
                            granted = state.screenCaptureActive,
                            onAllow = onEnableScreenCapture,
                            tag = "onb_screen_capture",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.Layers,
                            title = "ডিসপ্লে ওভার (ফ্লোটিং লোগো)",
                            subtitle = "অন্য অ্যাপের উপর AI ভয়েস বাটন ভাসিয়ে রাখতে",
                            granted = state.overlayGranted,
                            onAllow = onAllowOverlay,
                            tag = "onb_overlay",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.Mic,
                            title = "মাইক্রোফোন, ক্যামেরা ও কন্টাক্টস",
                            subtitle = "AI ভয়েস অ্যাসিস্ট্যান্ট, কল/ভিডিও ও নাম্বার খোঁজা",
                            granted = state.runtimeMediaAndNotifGranted,
                            onAllow = onRequestAllRuntimePermissions,
                            tag = "onb_runtime_media",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.BatteryChargingFull,
                            title = "আনরেস্ট্রিক্টেড ব্যাটারি",
                            subtitle = "ব্যাকগ্রাউন্ডে কানেকশন ও AI ওভারলে সচল রাখতে",
                            granted = state.batteryUnrestricted,
                            onAllow = onAllowBattery,
                            tag = "onb_battery",
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        OnboardingPermRow(
                            icon = Icons.Outlined.BrightnessMedium,
                            title = "সিস্টেম ব্রাইটনেস সেটিংস",
                            subtitle = "রিমোট থেকে ফোনের আলো কমানো/বাড়ানো",
                            granted = state.writeSettingsGranted,
                            onAllow = onAllowWriteSettings,
                            tag = "onb_write_settings",
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiApiKeyStepScreen(
    initialKey: String,
    onVerified: (apiKey: String, selectedModel: String) -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyDraft by remember(initialKey) { mutableStateOf(initialKey) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun verifyAndProceed() {
        val clean = keyDraft.trim()
        if (clean.isEmpty() || busy) {
            errorMsg = "অনুগ্রহ করে আপনার Google AI Studio API Key লিখুন"
            return
        }
        busy = true
        errorMsg = null
        scope.launch {
            val res = GeminiAssistantClient.verifyApiKey(clean)
            busy = false
            res.fold(
                onSuccess = { model ->
                    onVerified(clean, model)
                },
                onFailure = { err ->
                    errorMsg = err.message ?: "API Key যাচাই ব্যর্থ হয়েছে"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(innerPadding)
                .padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .testTag("onboarding_gemini_key_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Google AI Studio Key দিন",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "AI ভয়েস অ্যাসিস্ট্যান্ট ও AI চ্যাট চালু করতে Google AI Studio API Key দিন। চাইলে Key না দিয়েও সরাসরি পরবর্তী পেজে (হোমপেজে) যেতে পারবেন এবং পরে হোমপেজ থেকেই Key এড বা পরিবর্তন করতে পারবেন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "ডেভেলপার: Rubaeat Islam Raja · অরুপ Raja",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = {
                            keyDraft = it.trim()
                            errorMsg = null
                        },
                        label = { Text("Google AI Studio API Key (ঐচ্ছিক)") },
                        placeholder = { Text("AQ.... বা AIzaSy...") },
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key",
                                )
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = errorMsg != null,
                        supportingText = {
                            if (errorMsg != null) {
                                Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("aistudio.google.com থেকে প্রাপ্ত আপনার নিজস্ব Key বসান বা নিচে থেকে স্কিপ করুন")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { verifyAndProceed() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { verifyAndProceed() },
                        enabled = !busy && keyDraft.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("verify_gemini_key_button"),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Key যাচাই হচ্ছে...")
                        } else {
                            Text(
                                text = "যাচাই করুন ও হোমপেজে যান",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onSkip,
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("skip_gemini_key_button"),
                    ) {
                        Text(
                            text = "Key ছাড়াই পরবর্তী পেজে যান",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeGeminiApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSaved: (apiKey: String, model: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyDraft by remember(currentKey) { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                if (currentKey.isBlank()) "Google AI Studio Key এড করুন" else "Google AI Studio Key পরিবর্তন",
            )
        },
        text = {
            OutlinedTextField(
                value = keyDraft,
                onValueChange = {
                    keyDraft = it.trim()
                    err = null
                },
                label = { Text("Google AI Studio API Key") },
                placeholder = { Text("AQ.... বা AIzaSy...") },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key",
                        )
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                isError = err != null,
                supportingText = err?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } }
                    ?: { Text("সঠিক Key বসিয়ে সেভ করলে AI সিস্টেম চালু হবে") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("change_gemini_key_input"),
            )
        },
        confirmButton = {
            Button(
                enabled = !busy && keyDraft.isNotBlank(),
                onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        val res = GeminiAssistantClient.verifyApiKey(keyDraft)
                        busy = false
                        res.fold(
                            onSuccess = { m -> onSaved(keyDraft.trim(), m) },
                            onFailure = { e -> err = e.message ?: "Key সঠিক নয়" },
                        )
                    }
                },
                modifier = Modifier.testTag("save_gemini_key_dialog_button"),
            ) {
                Text(if (busy) "যাচাই হচ্ছে..." else "যাচাই ও সেভ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("বাতিল")
            }
        },
    )
}

@Composable
private fun OnboardingPermRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onAllow: () -> Unit,
    tag: String,
) {
    val statusColors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) statusColors.ok else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onAllow,
            enabled = !granted,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag(tag),
        ) {
            Text(if (granted) "এলাও ✓" else "এলাও")
        }
    }
}

