package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppLanguage
import com.example.data.MyraPersonaMode
import com.example.data.SpeakerAuthStatus
import com.example.data.ThemeAccent
import com.example.security.BiometricCapabilityStatus
import com.example.security.MyraBiometricAuthenticator
import com.example.ui.MyraViewModel
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.GlassCard
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsSecurityScreen(
    viewModel: MyraViewModel,
    hasMicPermission: Boolean,
    hasNotifPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val voiceProfile by viewModel.voiceProfile.collectAsStateWithLifecycle()
    val speakerStatus by viewModel.speakerAuthStatus.collectAsStateWithLifecycle()
    val speakerFeedback by viewModel.speakerFeedback.collectAsStateWithLifecycle()
    val enrollmentStep by viewModel.enrollmentStep.collectAsStateWithLifecycle()
    val isRecordingVoiceprint by viewModel.isRecordingVoiceprint.collectAsStateWithLifecycle()

    val biometricCapability = remember(context) { MyraBiometricAuthenticator.checkCapability(context) }
    val hasLockConfigured = settings.pinHash.isNotBlank() || settings.biometricLockEnabled
    var unlocked by remember(hasLockConfigured) { mutableStateOf(!hasLockConfigured) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricTestFeedback by remember { mutableStateOf<String?>(null) }

    var ownerNameInput by remember(voiceProfile) { mutableStateOf(voiceProfile?.ownerName ?: "Owner") }
    var customWakeWordInput by remember(settings.customWakeWord) { mutableStateOf(settings.customWakeWord) }
    var newPinInput by remember { mutableStateOf("") }
    var apiNoteInput by remember(settings.apiConfigNote) { mutableStateOf(settings.apiConfigNote) }

    if (!unlocked) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = LuminousCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Owner Settings Protected", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text("Authenticate with PIN or Biometrics to access owner & security controls.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                if (settings.pinHash.isNotBlank()) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = null },
                        label = { Text("Enter Owner PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("unlock_pin_input")
                    )
                    if (pinError != null) {
                        Text(pinError.orEmpty(), color = RoseAlert, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (viewModel.verifyPin(pinInput)) {
                                unlocked = true
                                pinInput = ""
                            } else {
                                pinError = "Incorrect PIN"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("unlock_pin_button")
                    ) { Text("Unlock with PIN") }
                }
                if (settings.biometricLockEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                MyraBiometricAuthenticator.authenticate(
                                    activity = activity,
                                    title = "Unlock MYRA Owner Settings",
                                    subtitle = "Verify fingerprint, face, or device credential",
                                    onSuccess = {
                                        unlocked = true
                                        viewModel.markSessionUnlocked()
                                    },
                                    onError = { err -> pinError = err }
                                )
                            } else {
                                pinError = "Biometric prompt unavailable in current host."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("unlock_biometrics_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock with Fingerprint / Face")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 92.dp)
    ) {
        item {
            Text("Settings, Voice & Security", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Configure MYRA's voice, background mode, owner voiceprint & AI brain", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        // 1. Language & Female Voice Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Language & Lyra Female Voice", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = { Text(lang.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Personality Mode", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MyraPersonaMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.personaMode == mode,
                            onClick = { viewModel.setPersonaMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("persona_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select Female Voice Profile", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    voices.take(5).forEach { voice ->
                        FilterChip(
                            selected = settings.selectedVoiceName == voice.id,
                            onClick = { viewModel.setVoiceName(voice.id) },
                            label = {
                                Text(
                                    "${voice.displayName} (${voice.localeLabel})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Speech Speed: ${"%.2f".format(settings.speechSpeed)}x", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Slider(
                    value = settings.speechSpeed,
                    onValueChange = { viewModel.setSpeechSpeed(it) },
                    valueRange = 0.6f..1.6f
                )
                Text("Female Tone Pitch: ${"%.2f".format(settings.speechPitch)}", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Slider(
                    value = settings.speechPitch,
                    onValueChange = { viewModel.setSpeechPitch(it) },
                    valueRange = 0.85f..1.4f
                )
                Text("Voice Volume: ${(settings.speechVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Slider(
                    value = settings.speechVolume,
                    onValueChange = { viewModel.setSpeechVolume(it) },
                    valueRange = 0.25f..1.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Warm Laughter & Friendly Tone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Adds natural playful chuckles & warmth in Hindi/English", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Switch(
                        checked = settings.warmLaughterEnabled,
                        onCheckedChange = { viewModel.setWarmLaughter(it) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val sample = when (settings.language) {
                            AppLanguage.HINDI -> "हाहा! नमस्ते, मैं मायरा हूँ। मेरी आवाज़ और लिप मूवमेंट बिल्कुल तैयार हैं!"
                            AppLanguage.ENGLISH -> "Haha! Hello, I am MYRA. My female voice and lip sync are ready!"
                            AppLanguage.HINGLISH -> "Haha! Namaste, main MYRA hoon. Meri voice aur lip movement ready hai!"
                        }
                        viewModel.voiceEngine.speak(sample)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet, contentColor = DeepNavyBg),
                    modifier = Modifier.fillMaxWidth().testTag("test_voice_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Female Voice & Lip Sync")
                }
            }
        }

        // 2. Always-On Background Mode, Wake Word & Notification Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Background Mode & Notification Controls", style = MaterialTheme.typography.titleMedium, color = EmeraldPulse)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Wake Word Detection", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Say \"${settings.customWakeWord}\" to wake hands-free or \"MYRA STOP\" to halt", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Switch(
                        checked = settings.wakeWordEnabled,
                        onCheckedChange = { viewModel.setWakeWord(it) },
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }

                if (settings.wakeWordEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWakeWordInput,
                            onValueChange = { if (it.length <= 28) customWakeWordInput = it },
                            label = { Text("Custom Wake Word / Phrase") },
                            placeholder = { Text("e.g., MYRA, Hey Nova, Suno MYRA") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom_wake_word_input")
                        )
                        Button(
                            onClick = { viewModel.setCustomWakeWord(customWakeWordInput) },
                            enabled = customWakeWordInput.isNotBlank(),
                            modifier = Modifier.testTag("save_wake_word_button")
                        ) { Text("Save") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("MYRA", "Hey MYRA", "Suno MYRA", "Nova").forEach { preset ->
                            FilterChip(
                                selected = settings.customWakeWord.equals(preset, ignoreCase = true),
                                onClick = {
                                    customWakeWordInput = preset
                                    viewModel.setCustomWakeWord(preset)
                                },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wake-Word Standby Filtering", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Ignore room chatter until wake word is heard", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        Switch(
                            checked = settings.standbyWakeWordOnly,
                            onCheckedChange = { viewModel.setStandbyWakeWordOnly(it) },
                            modifier = Modifier.testTag("wake_standby_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wake Match Sensitivity: ${(settings.wakeWordSensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.wakeWordSensitivity,
                        onValueChange = { viewModel.setWakeWordSensitivity(it) },
                        valueRange = 0.5f..1.0f
                    )

                    OutlinedButton(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission()
                            else viewModel.startWakeStandbyListening()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("start_wake_service_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = LuminousCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Continuous Wake-Word Listening")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Foreground Voice", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (serviceRunning) "Active in Android Notification Panel (Listen / Stop / Turn Off)"
                            else "Keep MYRA voice controls alive when leaving app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = { enable ->
                            if (enable && !hasMicPermission) {
                                onRequestMicPermission()
                            } else {
                                viewModel.toggleBackgroundService(enable)
                            }
                        },
                        modifier = Modifier.testTag("background_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRequestMicPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (hasMicPermission) "Mic Granted" else "Allow Mic")
                    }
                    OutlinedButton(
                        onClick = onRequestNotifPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (hasNotifPermission) "Notif OK" else "Allow Notif")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { viewModel.openBatterySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = AmberGlow)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (viewModel.isBatteryUnrestricted()) "Battery Unrestricted (Optimal)"
                        else "Adjust Battery Optimization for Background"
                    )
                }
            }
        }

        // 3. Owner Voice Recognition & Anti-Spoofing + PIN / Biometric
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = LuminousCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Owner Voice Recognition", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${speakerStatus.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when (speakerStatus) {
                        SpeakerAuthStatus.VERIFIED_OWNER -> EmeraldPulse
                        SpeakerAuthStatus.REJECTED_UNKNOWN, SpeakerAuthStatus.SPOOF_SUSPECTED -> RoseAlert
                        else -> CyanSoft
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(speakerFeedback, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ownerNameInput,
                    onValueChange = { ownerNameInput = it },
                    label = { Text("Owner Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission()
                            else viewModel.recordEnrollmentSample(ownerNameInput)
                        },
                        enabled = !isRecordingVoiceprint,
                        modifier = Modifier.weight(1f).testTag("enroll_voice_button")
                    ) {
                        Text(
                            if (isRecordingVoiceprint) "Recording..."
                            else "Enroll (${enrollmentStep}/3)"
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission()
                            else viewModel.verifyOwnerVoiceNow()
                        },
                        enabled = voiceProfile != null && !isRecordingVoiceprint,
                        modifier = Modifier.weight(1f).testTag("verify_voice_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify")
                    }
                }
                if (voiceProfile != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.resetOwnerVoiceProfile() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reset Enrolled Voiceprint") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Voice Auth for Calls", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Block unrecognized speakers from placing calls", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Switch(
                        checked = settings.requireVoiceAuthForSensitive,
                        onCheckedChange = { viewModel.setRequireVoiceAuth(it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = LuminousCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Biometric Security (androidx.biometric)", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${biometricCapability.title}: ${biometricCapability.description}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (biometricCapability.isUsable) EmeraldPulse else AmberGlow
                )

                if (biometricCapability is BiometricCapabilityStatus.NoneEnrolled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(MyraBiometricAuthenticator.buildEnrollmentIntent())
                            }.onFailure {
                                viewModel.showMessage("Open Android Settings -> Security to enroll fingerprint/face.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("enroll_biometric_settings_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enroll Fingerprint / Face in Settings")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Biometric Lock", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Secures Owner Settings & protected MYRA features", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Switch(
                        checked = settings.biometricLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable && !biometricCapability.isUsable && settings.pinHash.isBlank()) {
                                viewModel.showMessage(biometricCapability.description)
                            } else {
                                viewModel.setBiometricLock(enable)
                            }
                        },
                        modifier = Modifier.testTag("biometric_master_switch")
                    )
                }

                if (settings.biometricLockEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock MYRA AI on App Launch", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Require fingerprint / face unlock before entering MYRA", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        Switch(
                            checked = settings.biometricRequireOnAppLaunch,
                            onCheckedChange = { viewModel.setBiometricOnAppLaunch(it) },
                            modifier = Modifier.testTag("biometric_app_launch_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verify for Calls & Sensitive Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Prompt biometric confirmation before placing calls", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        Switch(
                            checked = settings.biometricRequireForSensitiveActions,
                            onCheckedChange = { viewModel.setBiometricForSensitiveActions(it) },
                            modifier = Modifier.testTag("biometric_sensitive_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                biometricTestFeedback = "Biometric test requires FragmentActivity."
                            } else {
                                MyraBiometricAuthenticator.authenticate(
                                    activity = activity,
                                    title = "Test MYRA Biometric Shield",
                                    subtitle = "Confirm fingerprint or face unlock works",
                                    onSuccess = {
                                        biometricTestFeedback = "Biometric verification succeeded!"
                                        viewModel.showMessage("Fingerprint / Face Unlock verified!")
                                    },
                                    onError = { err -> biometricTestFeedback = err }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("test_biometric_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Unlock")
                    }

                    if (settings.biometricLockEnabled && settings.biometricRequireOnAppLaunch) {
                        OutlinedButton(
                            onClick = {
                                unlocked = false
                                viewModel.lockAppNow()
                            },
                            modifier = Modifier.weight(1f).testTag("lock_myra_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock Now")
                        }
                    }
                }
                if (biometricTestFeedback != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = biometricTestFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (biometricTestFeedback?.contains("succeeded", ignoreCase = true) == true) EmeraldPulse else RoseAlert
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6) newPinInput = it.filter(Char::isDigit) },
                        label = { Text(if (settings.pinHash.isBlank()) "Set 4-Digit Owner PIN" else "Change Owner PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.savePin(newPinInput)
                            newPinInput = ""
                        },
                        enabled = newPinInput.length >= 4
                    ) { Text("Save") }
                }
                if (settings.pinHash.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = { viewModel.clearPin() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove Owner PIN")
                    }
                }
            }
        }

        // 4. Gemini 3.5 Flash-Lite & API Connection Status + Theme + Chat History
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = ElectricViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Brain & API Configuration", style = MaterialTheme.typography.titleMedium, color = ElectricViolet)
                }
                Spacer(modifier = Modifier.height(6.dp))
                FirebaseAiNoticeBanner()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Target Model: ${settings.geminiModelId}", style = MaterialTheme.typography.labelMedium, color = CyanSoft)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiNoteInput,
                    onValueChange = { apiNoteInput = it },
                    label = { Text("Firebase / API Connection Label (Optional)") },
                    placeholder = { Text("e.g., Gemini 3.5 Flash-Lite Production") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.saveApiConfigNote("gemini-3.5-flash-lite", apiNoteInput) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save AI Config Preference") }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Theme Accent", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeAccent.entries.forEach { accent ->
                        FilterChip(
                            selected = settings.themeAccent == accent,
                            onClick = { viewModel.setThemeAccent(accent) },
                            label = { Text(accent.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.clearChatHistory() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = RoseAlert)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Conversation History", color = RoseAlert)
                }
            }
        }
    }
}
