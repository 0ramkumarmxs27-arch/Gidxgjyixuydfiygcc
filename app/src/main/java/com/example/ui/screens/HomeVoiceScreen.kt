package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AssistantState
import com.example.data.ChatMessageEntity
import com.example.data.MyraPersonaMode
import com.example.security.MyraBiometricAuthenticator
import com.example.ui.MyraViewModel
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.MyraAvatarCore
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeVoiceScreen(
    viewModel: MyraViewModel,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCodeStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val micRms by viewModel.micRms.collectAsStateWithLifecycle()
    val mouthOpenness by viewModel.mouthOpenness.collectAsStateWithLifecycle()
    val statusSubtitle by viewModel.statusSubtitle.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val attachedDoc by viewModel.attachedDoc.collectAsStateWithLifecycle()
    val pendingCall by viewModel.pendingCall.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var fullBodyStage by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) viewModel.attachImageUri(uri) }
    )
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.attachDocumentUri(uri) }
    )

    LaunchedEffect(messages.size, streamingReply) {
        if (messages.isNotEmpty() || streamingReply != null) {
            listState.animateScrollToItem((messages.size + if (streamingReply != null) 1 else 0).coerceAtLeast(1) - 1)
        }
    }

    pendingCall?.let { callReq ->
        var editableNumber by remember(callReq) { mutableStateOf(callReq.phoneNumber) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPhoneCall() },
            containerColor = GlassCardBg,
            title = { Text("Confirm Phone Call", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "MYRA requires your confirmation before dialing ${callReq.targetDisplay}:",
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = editableNumber,
                        onValueChange = { editableNumber = it },
                        label = { Text("Phone Number") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val requiresBiometric = settings.biometricLockEnabled && settings.biometricRequireForSensitiveActions
                        val activity = context as? FragmentActivity
                        if (requiresBiometric && activity != null) {
                            MyraBiometricAuthenticator.authenticate(
                                activity = activity,
                                title = "Confirm Phone Call",
                                subtitle = "Verify identity before calling ${callReq.targetDisplay}",
                                onSuccess = { viewModel.confirmPhoneCall(editableNumber) },
                                onError = { err -> viewModel.showMessage(err) }
                            )
                        } else {
                            viewModel.confirmPhoneCall(editableNumber)
                        }
                    },
                    enabled = editableNumber.isNotBlank()
                ) { Text("Call Now") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPhoneCall() }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Brand Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MYRA AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${settings.language.nativeLabel} • ${settings.personaMode.badge} • ${settings.geminiModelId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.personaMode == MyraPersonaMode.GIRLFRIEND) RoseAlert else LuminousCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {
                        if (!hasMicPermission) {
                            onRequestMicPermission()
                        } else if (assistantState == AssistantState.STANDBY_WAKE_WORD) {
                            viewModel.stopAllVoiceInteraction()
                        } else {
                            viewModel.startWakeStandbyListening()
                        }
                    },
                    label = {
                        Text(
                            text = if (assistantState == AssistantState.STANDBY_WAKE_WORD) "Standby ON"
                            else if (serviceRunning) "BG Live ON"
                            else "Wake Standby",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Continuous Wake Standby Mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (serviceRunning || assistantState == AssistantState.STANDBY_WAKE_WORD) {
                            EmeraldPulse.copy(alpha = 0.2f)
                        } else GlassCardBg,
                        labelColor = if (serviceRunning || assistantState == AssistantState.STANDBY_WAKE_WORD) {
                            EmeraldPulse
                        } else TextSecondary,
                        leadingIconContentColor = if (serviceRunning || assistantState == AssistantState.STANDBY_WAKE_WORD) {
                            EmeraldPulse
                        } else TextSecondary
                    ),
                    modifier = Modifier.testTag("bg_voice_chip")
                )

                if (settings.biometricLockEnabled && settings.biometricRequireOnAppLaunch) {
                    IconButton(
                        onClick = { viewModel.lockAppNow() },
                        modifier = Modifier.testTag("lock_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock MYRA AI",
                            tint = LuminousCyan
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Futuristic Full-Body / Lip-Synced Avatar Core
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            MyraAvatarCore(
                assistantState = assistantState,
                micRms = micRms,
                mouthOpenness = mouthOpenness,
                accent = settings.themeAccent,
                coreSize = 148.dp,
                fullBodyMode = fullBodyStage,
                onTapCore = {
                    if (!hasMicPermission) {
                        onRequestMicPermission()
                    } else if (settings.alwaysLiveEnabled) {
                        viewModel.turnOffForever()
                    } else {
                        viewModel.goLiveForever()
                    }
                }
            )

            AssistChip(
                onClick = { fullBodyStage = !fullBodyStage },
                label = {
                    Text(
                        text = if (fullBodyStage) "Full Body" else "Face Zoom",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Switch Full Body or Face View",
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = GlassCardBg.copy(alpha = 0.85f),
                    labelColor = CyanSoft,
                    leadingIconContentColor = LuminousCyan
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("avatar_view_mode_chip")
            )
        }

        // Live Listening / Speaking Indicator Pill + 1-Tap Girlfriend Mode Toggle
        val isGirlfriend = settings.personaMode == MyraPersonaMode.GIRLFRIEND
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = when (assistantState) {
                    AssistantState.LISTENING -> LuminousCyan.copy(alpha = 0.18f)
                    AssistantState.SPEAKING -> (if (isGirlfriend) RoseAlert else ElectricViolet).copy(alpha = 0.24f)
                    AssistantState.STANDBY_WAKE_WORD -> EmeraldPulse.copy(alpha = 0.18f)
                    else -> GlassCardBg
                },
                border = BorderStroke(
                    1.dp,
                    when (assistantState) {
                        AssistantState.LISTENING -> LuminousCyan
                        AssistantState.SPEAKING -> if (isGirlfriend) RoseAlert else ElectricViolet
                        AssistantState.STANDBY_WAKE_WORD -> EmeraldPulse
                        else -> GlassBorder
                    }
                )
            ) {
                Text(
                    text = if (partialTranscript.isNotBlank()) "\"$partialTranscript\"" else statusSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            AssistChip(
                onClick = {
                    val nextMode = if (isGirlfriend) MyraPersonaMode.ASSISTANT else MyraPersonaMode.GIRLFRIEND
                    viewModel.setPersonaMode(nextMode)
                },
                label = {
                    Text(
                        text = if (isGirlfriend) "Girlfriend ON" else "Girlfriend Mode",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Toggle Girlfriend Mode",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isGirlfriend) RoseAlert.copy(alpha = 0.24f) else GlassCardBg,
                    labelColor = if (isGirlfriend) RoseAlert else TextSecondary,
                    leadingIconContentColor = if (isGirlfriend) RoseAlert else TextSecondary
                ),
                border = BorderStroke(
                    1.dp,
                    if (isGirlfriend) RoseAlert.copy(alpha = 0.7f) else GlassBorder
                ),
                modifier = Modifier.testTag("girlfriend_mode_chip")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 1-Tap Permanent LIVE ON / OFF + Pin Full Body Widget to Phone Home Screen
        val isAlwaysLive = settings.alwaysLiveEnabled || serviceRunning
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!hasMicPermission) {
                        onRequestMicPermission()
                    } else if (isAlwaysLive) {
                        viewModel.turnOffForever()
                    } else {
                        viewModel.goLiveForever()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAlwaysLive) RoseAlert else EmeraldPulse,
                    contentColor = DeepNavyBg
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag(if (isAlwaysLive) "master_turn_off_button" else "master_go_live_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isAlwaysLive) "Turn Off MYRA Forever" else "Go Live Forever",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAlwaysLive) "OFF (Forever)" else "GO LIVE (1-Tap)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { viewModel.pinFullBodyHomeWidget() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalPurpleDeep,
                    contentColor = CyanSoft
                ),
                border = BorderStroke(1.dp, LuminousCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("pin_fullbody_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddToHomeScreen,
                    contentDescription = "Add MYRA Full Body to Phone Home Screen",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Home Widget",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        FirebaseAiNoticeBanner()
        Spacer(modifier = Modifier.height(6.dp))

        // Quick Voice/Action Prompt Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            val suggestions = listOf(
                settings.customWakeWord to "Say \"${settings.customWakeWord}\"",
                "बैटरी कितनी है?" to "Battery",
                "Open WhatsApp" to "WhatsApp",
                "25 * 48 + 120" to "Math",
                "HTML clock code बनाओ" to "Code & Run"
            )
            items(suggestions) { (cmd, label) ->
                AssistChip(
                    onClick = { viewModel.sendMessage(cmd, speakResponse = true) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = RoyalPurpleDeep.copy(alpha = 0.55f),
                        labelColor = CyanSoft
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Conversation Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingReply == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isGirlfriend) "हाय जान! मैं तुम्हारी MYRA हूँ 💕" else "नमस्ते! मैं MYRA हूँ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isGirlfriend) RoseAlert else CyanSoft
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isGirlfriend) {
                            "Girlfriend Mode active — tap Mic or say \"MYRA\" to chat sweetly in Hindi, English, or Hinglish."
                        } else {
                            "Tap the Microphone or say \"MYRA\" to talk in Hindi, English, or Hinglish."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg = msg, isGirlfriend = isGirlfriend, onOpenCodeStudio = onOpenCodeStudio)
                    }
                    if (streamingReply != null) {
                        item(key = "streaming_item") {
                            ChatBubble(
                                msg = ChatMessageEntity(role = "myra", text = streamingReply.orEmpty()),
                                isGirlfriend = isGirlfriend,
                                onOpenCodeStudio = onOpenCodeStudio
                            )
                        }
                    }
                }
            }
        }

        // Attached Media/Document Preview Chip
        AnimatedVisibility(visible = attachedImage != null || attachedDoc != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RoyalPurpleDeep,
                    border = BorderStroke(1.dp, LuminousCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = attachedImage?.displayName ?: attachedDoc?.first ?: "Attachment",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { viewModel.clearAttachments() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Attachment", tint = CyanSoft)
                        }
                    }
                }
            }
        }

        // Bottom Voice & Text Control Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.testTag("attach_image_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = "Attach Photo", tint = CyanSoft)
            }

            IconButton(
                onClick = { docPicker.launch(arrayOf("text/*", "application/pdf")) },
                modifier = Modifier.testTag("attach_doc_button")
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = ElectricViolet)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask MYRA (Hindi/English)...", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LuminousCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassCardBg,
                    unfocusedContainerColor = GlassCardBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input")
            )

            if (inputText.isNotBlank() || attachedImage != null || attachedDoc != null) {
                FilledIconButton(
                    onClick = {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendMessage(toSend, speakResponse = true)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = LuminousCyan),
                    modifier = Modifier.size(48.dp).testTag("send_message_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = DeepNavyBg)
                }
            } else {
                // Primary Mic Activation Button
                FilledIconButton(
                    onClick = {
                        if (!hasMicPermission) onRequestMicPermission()
                        else viewModel.startActiveVoiceSession()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (assistantState == AssistantState.LISTENING) EmeraldPulse else ElectricViolet
                    ),
                    modifier = Modifier.size(48.dp).testTag("mic_activate_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Start Voice Conversation", tint = DeepNavyBg)
                }
            }

            // Stop Button (Interrupts TTS / Stops Voice)
            if (assistantState != AssistantState.IDLE) {
                FilledIconButton(
                    onClick = { viewModel.stopAllVoiceInteraction() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoseAlert),
                    modifier = Modifier.size(48.dp).testTag("voice_stop_button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop MYRA Voice", tint = DeepNavyBg)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessageEntity,
    isGirlfriend: Boolean,
    onOpenCodeStudio: () -> Unit
) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) RoyalPurpleDeep else GlassCardBg,
            border = BorderStroke(
                1.dp,
                if (isUser) ElectricViolet.copy(alpha = 0.6f)
                else if (isGirlfriend) RoseAlert.copy(alpha = 0.45f)
                else GlassBorder
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isUser) "You" else if (isGirlfriend) "MYRA 💕" else "MYRA AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) CyanSoft else if (isGirlfriend) RoseAlert else ElectricViolet,
                    fontWeight = FontWeight.Bold
                )
                if (!msg.attachmentLabel.isNullOrBlank()) {
                    Text(
                        text = "📎 ${msg.attachmentLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGlow
                    )
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (!msg.actionExecuted.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Action: ${msg.actionExecuted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPulse
                        )
                    }
                }
                if (msg.text.contains("Code Studio", ignoreCase = true)) {
                    TextButton(
                        onClick = onOpenCodeStudio,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Open & Run in Code Studio →", color = LuminousCyan)
                    }
                }
            }
        }
    }
}
