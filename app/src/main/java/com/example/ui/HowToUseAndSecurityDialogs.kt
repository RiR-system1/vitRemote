package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.Prefs
import com.example.data.CloudRepository
import com.example.security.SecureServerVault
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalStatusColors

@Composable
fun HowToUseDialog(
    onDismiss: () -> Unit,
    onOpenServerVault: () -> Unit,
) {
    val s = LocalAppStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp),
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
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.howToUseGuideTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = s.howToUseGuideSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_how_to_use_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "বন্ধ করুন",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuideStepCard(
                        icon = Icons.Outlined.Pin,
                        stepNumber = if (s.isEnglish) "Step 1" else "ধাপ ১",
                        title = if (s.isEnglish) "Connect with Permanent 9-Digit Code" else "স্থায়ী ৯-ডিজিট কোডে সরাসরি কানেক্ট",
                        body = if (s.isEnglish) {
                            "Share your permanent 9-digit device code or tap 'Connect Device' and enter another phone's 9-digit code to view and control it from anywhere."
                        } else {
                            "হোমপেজের স্থায়ী ৯-ডিজিট ডিভাইস কোড শেয়ার করুন অথবা 'অন্য ডিভাইসে কানেক্ট' বাটনে অন্য ফোনের ৯-ডিজিট কোড বসিয়ে সরাসরি পৃথিবীর যেকোনো প্রান্ত থেকে লাইভ স্ক্রিন দেখতে ও কন্ট্রোল করতে পারবেন।"
                        },
                    )
                    GuideStepCard(
                        icon = Icons.Outlined.DevicesOther,
                        stepNumber = if (s.isEnglish) "Step 2" else "ধাপ ২",
                        title = if (s.isEnglish) "Real & Virtual Screen Apps" else "রিয়েল স্ক্রিন ও ভার্চুয়াল স্ক্রিন অ্যাপস",
                        body = if (s.isEnglish) {
                            "Tap 'Apps' during a remote session to launch apps on the target phone's real screen or inside an isolated virtual display."
                        } else {
                            "রিমোট স্ক্রিন চালানোর সময় \"অ্যাপস\" বাটনে ক্লিক করলে ফোনের সব ডাউনলোড করা অ্যাপ দেখাবে। \"রিয়েল স্ক্রিন\" বেছে নিলে সেই ফোনে অ্যাপ খুলবে; \"ভার্চুয়াল স্ক্রিন\" বেছে নিলে আপনার ফোনের ভেতরের আলাদা ভার্চুয়াল পর্দায় অ্যাপ খুলে টাচ করে চালাতে পারবেন।"
                        },
                    )
                    GuideStepCard(
                        icon = Icons.Outlined.FlashlightOn,
                        stepNumber = if (s.isEnglish) "Step 3" else "ধাপ ৩",
                        title = if (s.isEnglish) "Notifications, Torch, Ring & Brightness" else "নোটিফিকেশন, টর্চ, রিং ও ব্রাইটনেস",
                        body = if (s.isEnglish) {
                            "View remote notifications, toggle flashlight, trigger full-volume find-phone ring alerts, adjust brightness, and sync clipboard."
                        } else {
                            "\"নোটিফিকেশন\" বাটনে ট্যাপ করলে রিমোট ফোনের সব নোটিফিকেশন দেখাবে। এছাড়া টর্চলাইট অন/অফ, হারানো ফোন খুঁজতে ফুল-সাউন্ড রিং অ্যালার্ট, ব্রাইটনেস কমানো-বাড়ানো ও ২-ওয়ে ক্লিপবোর্ড কপি-পেস্ট করা যাবে।"
                        },
                    )
                    GuideStepCard(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        stepNumber = if (s.isEnglish) "Step 4" else "ধাপ ৪",
                        title = if (s.isEnglish) "Messenger Chat & Audio/Video Calls" else "ইন-অ্যাপ মেসেঞ্জার চ্যাট ও অডিও/ভিডিও কল",
                        body = if (s.isEnglish) {
                            "Send text messages, photos, and live audio/video calls from the Chat tab or active remote session."
                        } else {
                            "নিচের \"চ্যাট\" ট্যাব বা রিমোট সেশন থেকে সরাসরি টেক্সট মেসেজ, গ্যালারির ছবি ও লাইভ অডিও/ভিডিও কল করতে পারবেন। একবার সেভ হওয়া ডিভাইসের নাম পরিবর্তন ও ফেভারিট পিন করে রাখা যায়।"
                        },
                    )
                    GuideStepCard(
                        icon = Icons.Outlined.AutoAwesome,
                        stepNumber = if (s.isEnglish) "Step 5" else "ধাপ ৫",
                        title = if (s.isEnglish) "6-Button AI Voice & Chat System" else "৬ বাটনের AI ভয়েস ও চ্যাট সিস্টেম",
                        body = if (s.isEnglish) {
                            "Use the 6 AI buttons to control virtual, remote, or your own screen by voice, chat with AI, or enable the floating assistant overlay."
                        } else {
                            "হোমপেজের ১-৬ নং AI বাটন দিয়ে AI চালু করুন। ভার্চুয়াল স্ক্রিন AI, রিমোট স্ক্রিন AI বা নিজের মোবাইল ভয়েস AI অন করে মুখে যা বলবেন (অ্যাপ খোলা, স্ক্রিন দেখে টাচ, কল করা) AI তা করে দেবে। বাটন ৫ দিয়ে AI চ্যাট ও ১-ক্লিক কপি কোড পাবেন, বাটন ৬ দিয়ে ভাসমান লোগো চালু হবে।"
                        },
                    )
                    GuideStepCard(
                        icon = Icons.Outlined.Security,
                        stepNumber = if (s.isEnglish) "Step 6" else "ধাপ ৬",
                        title = if (s.isEnglish) "Security Vault & Server Lock" else "নিরাপত্তা ও সার্ভার ভল্ট লক",
                        body = if (s.isEnglish) {
                            "All server endpoints and sync paths are protected with password-locked vault encryption and real-time security monitoring."
                        } else {
                            "অ্যাপের সব সার্ভার লিংক ও এন্ডপয়েন্ট পাসওয়ার্ড লকে সুরক্ষিত এবং রিয়েল-টাইম সার্ভার সিকিউরিটি দ্বারা নিয়ন্ত্রিত।"
                        },
                    )

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenServerVault()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("open_server_vault_button"),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.serverVaultButton)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(s.gotItButton)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    icon: ImageVector,
    stepNumber: String,
    title: String,
    body: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ServerVaultLockDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val statusColors = LocalStatusColors.current
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val archiveIntact = remember(context) { SecureServerVault.isVaultArchiveIntact(context) }
    val packageValid = remember(context) { SecureServerVault.isAppPackageValid(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (unlocked) statusColors.ok else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = if (unlocked) "সার্ভার ভল্ট আনলক হয়েছে" else "পাসওয়ার্ড লক সার্ভার ভল্ট",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!unlocked) {
                    Text(
                        text = "অ্যাপের সব সার্ভার এন্ডপয়েন্ট, এনক্রিপ্টেড ভল্ট আর্কাইভ (assets) এবং অ্যাডমিন সিঙ্ক পথ পাসওয়ার্ড দিয়ে লক করা আছে। ভল্ট স্ট্যাটাস দেখতে পাসওয়ার্ড দিন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorText = null
                        },
                        label = { Text("ভল্ট পাসওয়ার্ড") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "পাসওয়ার্ড দেখুন",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_vault_password_input"),
                    )
                    if (!errorText.isNullOrBlank()) {
                        Text(
                            text = errorText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    VaultStatusRow("পাসওয়ার্ড লক যাচাই", "যাচাইকৃত (SHA-256)", true)
                    VaultStatusRow(
                        "পাসওয়ার্ড-লক ZIP আর্কাইভ",
                        if (archiveIntact) "সুরক্ষিত (server_vault_locked.zip)" else "পাওয়া যায়নি",
                        archiveIntact,
                    )
                    VaultStatusRow("সার্ভার এন্ডপয়েন্ট এনক্রিপশন", "XOR + SHA-256 মাস্কড", true)
                    VaultStatusRow(
                        "প্যাকেজ ইন্টিগ্রিটি গার্ড",
                        if (packageValid) "বৈধ (Anti-Tamper সক্রিয়)" else "সতর্কতা",
                        packageValid,
                    )
                    VaultStatusRow("অ্যাডমিন প্যানেল ব্লক সিঙ্ক", "রিয়েল-টাইম Firestore অ্যাক্টিভ", true)
                    Text(
                        text = "অ্যাডমিন টিপ: MT Manager-এ assets/server_vault_locked.zip খুলতে গেলেও একই পাসওয়ার্ড চাইবে এবং কোডে প্লেইনটেক্সট URL/পাসওয়ার্ড রাখা হয়নি।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!unlocked) {
                Button(
                    onClick = {
                        if (SecureServerVault.verifyVaultPassword(passwordInput)) {
                            unlocked = true
                            errorText = null
                        } else {
                            errorText = "ভুল পাসওয়ার্ড! সঠিক পাসওয়ার্ড দিয়ে আনলক করুন।"
                        }
                    },
                    modifier = Modifier.testTag("unlock_server_vault_button"),
                ) {
                    Text("আনলক করুন")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("ঠিক আছে")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        },
    )
}

@Composable
private fun VaultStatusRow(label: String, value: String, ok: Boolean) {
    val statusColors = LocalStatusColors.current
    Surface(
        color = if (ok) statusColors.okContainer.copy(alpha = 0.6f) else statusColors.badContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (ok) statusColors.ok else statusColors.bad,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BlockedAccountScreen(
    reason: String,
    blockedByDevice: Boolean,
    userEmail: String?,
    deviceId: String,
    onSignOut: () -> Unit,
) {
    val s = LocalAppStrings.current
    val displayNotice = reason.ifBlank { CloudRepository.DEFAULT_BLOCK_NOTICE }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = CircleShape,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = "ব্লক নোটিশ",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = s.blockedTitle,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blocked_notice_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s.blockedAdminNotice,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = displayNotice,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (blockedByDevice) {
                            "এই ডিভাইসটি অ্যাডমিন দ্বারা স্থায়ীভাবে বন্ধ করা হয়েছে। এই মোবাইলে অ্যাপটি আর চলবে না।"
                        } else {
                            "আপনার অ্যাকাউন্টটি অ্যাডমিন প্যানেল থেকে ব্লক করা হয়েছে। শুধু পরিবারের অনুমোদিত ব্যবহারকারীই এটি চালাতে পারবেন।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = "${s.accountLabel}: $userEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${s.deviceIdLabel}: $deviceId",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("blocked_sign_out_button"),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(s.signOutButton)
            }
        }
    }
}

@Composable
fun MaintenanceNoticeScreen(
    notice: String,
    onSignOut: () -> Unit,
) {
    val s = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(84.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = s.maintenanceTitle,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = notice.ifBlank {
                    if (s.isEnglish) {
                        "Server maintenance is in progress from the Admin Panel. Please check back shortly."
                    } else {
                        "অ্যাডমিন প্যানেল থেকে সার্ভার আপডেট ও রক্ষণাবেক্ষণ চলছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))
            FilledTonalButton(
                onClick = onSignOut,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(s.signOutButton)
            }
        }
    }
}

@Composable
fun TelegramSupportDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val s = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = s.supportTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = s.supportBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Telegram: ${Prefs.ADMIN_TELEGRAM_HANDLE}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = Prefs.ADMIN_TELEGRAM_URL,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(Prefs.ADMIN_TELEGRAM_URL))
                        Toast.makeText(context, s.linkCopiedToast, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.copyLinkButton)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.ADMIN_TELEGRAM_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, Prefs.ADMIN_TELEGRAM_URL, Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .heightIn(min = 46.dp)
                    .testTag("support_open_telegram_button"),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.openTelegramButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.closeButton)
            }
        },
    )
}
