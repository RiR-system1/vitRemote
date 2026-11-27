package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiChatTurn
import com.example.ai.GeminiAssistantClient
import com.example.ai.VoiceAssistantController
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ChatSegment {
    data class Plain(val text: String) : ChatSegment
    data class Code(val language: String, val code: String) : ChatSegment
}

private fun parseChatSegments(raw: String): List<ChatSegment> {
    val out = ArrayList<ChatSegment>()
    val regex = Regex("```([a-zA-Z0-9_+#.-]*)\\s*\\n([\\s\\S]*?)```")
    var cursor = 0
    for (m in regex.findAll(raw)) {
        if (m.range.first > cursor) {
            val plain = raw.substring(cursor, m.range.first).trim()
            if (plain.isNotEmpty()) out.add(ChatSegment.Plain(plain))
        }
        val lang = m.groupValues[1].ifBlank { "code" }
        val code = m.groupValues[2].trimEnd()
        if (code.isNotEmpty()) {
            out.add(ChatSegment.Code(lang, code))
        }
        cursor = m.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) out.add(ChatSegment.Plain(tail))
    }
    if (out.isEmpty() && raw.isNotBlank()) {
        out.add(ChatSegment.Plain(raw.trim()))
    }
    return out
}

private data class AiChatUiMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
)

@Composable
fun AiChatScreen(
    apiKey: String,
    modelName: String,
    aiSystemEnabled: Boolean,
    onModelResolved: (String) -> Unit = {},
    onOpenKeySettings: () -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val voice = remember(context) { VoiceAssistantController.get(context) }

    val messages = remember {
        mutableStateListOf(
            AiChatUiMessage(
                id = "welcome",
                isUser = false,
                text = "আসসালামু আলাইকুম! আমি আপনার **vitRemote AI Assistant**। বাংলায় যেকোনো প্রশ্ন করতে পারেন অথবা প্রোগ্রামিং কোড চাইলে সাথে সাথে কপি করার বক্সে কোড লিখে দেবো।",
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submit(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || busy) return
        if (apiKey.isBlank()) {
            messages.add(
                AiChatUiMessage(
                    id = "e_${System.currentTimeMillis()}",
                    isUser = false,
                    text = "Google AI Studio Key সেট করা নেই। হোমপেজ থেকে 'Key এড' বাটনে ট্যাপ করে আপনার Key যোগ করুন।",
                    isError = true,
                ),
            )
            return
        }
        val history = messages.filter { !it.isError && it.id != "welcome" }
            .map { AiChatTurn(isUser = it.isUser, text = it.text) }
        messages.add(AiChatUiMessage(id = "u_${System.currentTimeMillis()}", isUser = true, text = clean))
        input = ""
        busy = true
        scope.launch {
            val res = GeminiAssistantClient.sendChatMessage(
                apiKey = apiKey,
                modelName = modelName,
                history = history,
                userPrompt = clean,
                onModelResolved = onModelResolved,
            )
            busy = false
            res.fold(
                onSuccess = { reply ->
                    messages.add(
                        AiChatUiMessage(
                            id = "a_${System.currentTimeMillis()}",
                            isUser = false,
                            text = reply,
                        ),
                    )
                },
                onFailure = { err ->
                    messages.add(
                        AiChatUiMessage(
                            id = "e_${System.currentTimeMillis()}",
                            isUser = false,
                            text = err.message ?: "উত্তর আনা যায়নি",
                            isError = true,
                        ),
                    )
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
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
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ai_chat_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val s = LocalAppStrings.current
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = s.aiChatTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (s.isEnglish) "Developer: Rubaeat Islam Raja · অরুপ Raja" else "ডেভেলপার: Rubaeat Islam Raja · অরুপ Raja",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (messages.size > 1) {
                                val first = messages.first()
                                messages.clear()
                                messages.add(first)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            val s = LocalAppStrings.current
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (apiKey.isBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (s.isEnglish) "Google AI Studio Key not configured" else "Google AI Studio Key দেওয়া নেই",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onOpenKeySettings) {
                                    Text(s.keyAdd)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { submit("Android Jetpack Compose এ একটি সুন্দর বাটন কোড দাও") },
                            label = { Text("কোড লিখুন") },
                        )
                        AssistChip(
                            onClick = { submit("বাংলায় একটি অফিশিয়াল ছুটির দরখাস্ত লিখে দাও") },
                            label = { Text("বাংলা দরখাস্ত") },
                        )
                        AssistChip(
                            onClick = { submit("Python দিয়ে একটি ক্যালকুলেটর কোড লিখে দাও") },
                            label = { Text("Python কোড") },
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (!aiSystemEnabled) {
                                    Toast.makeText(
                                        context,
                                        "হোমপেজ থেকে 'AI সিস্টেম চালু করুন' বাটনটি অন করুন",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    voice.startVoiceSession { spoken ->
                                        submit(spoken)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_chat_voice_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Voice Prompt",
                                tint = if (aiSystemEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("বাংলা বা ইংরেজিতে কিছু জিজ্ঞাসা করুন...") },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit(input) }),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_chat_input"),
                        )
                        FilledIconButton(
                            onClick = { submit(input) },
                            enabled = !busy && input.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_chat_send_button"),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(messages, key = { it.id }) { msg ->
                AiChatMessageBubble(msg = msg)
            }
            if (busy) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI উত্তর তৈরি করছে...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatMessageBubble(msg: AiChatUiMessage) {
    val segments = remember(msg.text) { parseChatSegments(msg.text) }
    val bgColor = when {
        msg.isError -> MaterialTheme.colorScheme.errorContainer
        msg.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = when {
        msg.isError -> MaterialTheme.colorScheme.onErrorContainer
        msg.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (msg.isUser) 20.dp else 6.dp,
                bottomEnd = if (msg.isUser) 6.dp else 20.dp,
            ),
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                segments.forEach { seg ->
                    when (seg) {
                        is ChatSegment.Plain -> {
                            Text(
                                text = seg.text.replace("**", ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = fgColor,
                            )
                        }
                        is ChatSegment.Code -> {
                            CodeSnippetBox(language = seg.language, code = seg.code)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeSnippetBox(
    language: String,
    code: String,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    color = Color(0xFF334155),
                    shape = CircleShape,
                ) {
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = Color(0xFF93C5FD),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        Toast.makeText(context, "কোড কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000L)
                            copied = false
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("copy_code_snippet_button"),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF4ADE80) else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) "কপি ✓" else "কপি",
                        color = if (copied) Color(0xFF4ADE80) else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = Color(0xFFE2E8F0),
                )
            }
        }
    }
}
