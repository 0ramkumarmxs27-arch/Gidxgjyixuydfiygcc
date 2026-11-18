package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricCapabilityStatus
import com.example.security.MyraBiometricAuthenticator
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BiometricAppLockGate(
    hasPinConfigured: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val capability = remember(context) { MyraBiometricAuthenticator.checkCapability(context) }

    var authFeedback by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var promptLaunchedOnce by remember { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        if (activity == null) {
            authFeedback = "Biometric prompt requires an active FragmentActivity."
            return
        }
        authFeedback = null
        MyraBiometricAuthenticator.authenticate(
            activity = activity,
            title = "Unlock MYRA AI",
            subtitle = "Verify fingerprint, face, or device credential",
            description = "MYRA AI voice, memory, and device automation are protected.",
            onSuccess = { onUnlocked() },
            onError = { err -> authFeedback = err }
        )
    }

    LaunchedEffect(capability) {
        if (!promptLaunchedOnce && capability.isUsable && activity != null) {
            promptLaunchedOnce = true
            launchBiometricPrompt()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("biometric_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoyalPurpleDeep,
                    border = BorderStroke(1.5.dp, LuminousCyan),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Security Shield",
                            tint = LuminousCyan,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = "MYRA AI Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Authenticate with Fingerprint, Face Unlock, or Device Lock to access MYRA AI features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = capability.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (capability.isUsable) CyanSoft else RoseAlert,
                    textAlign = TextAlign.Center
                )

                if (authFeedback != null) {
                    Text(
                        text = authFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = RoseAlert,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = { launchBiometricPrompt() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminousCyan,
                        contentColor = DeepNavyBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Fingerprint / Face", fontWeight = FontWeight.Bold)
                }

                if (capability is BiometricCapabilityStatus.NoneEnrolled) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(MyraBiometricAuthenticator.buildEnrollmentIntent())
                            }.onFailure {
                                authFeedback = "Open Android Settings -> Security to enroll biometrics."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("enroll_biometrics_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enroll Fingerprint / Face in Settings")
                    }
                }

                if (hasPinConfigured) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter(Char::isDigit).take(6)
                            authFeedback = null
                        },
                        label = { Text("Or enter Owner PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_lock_pin_input")
                    )
                    OutlinedButton(
                        onClick = {
                            if (onVerifyPin(pinInput)) {
                                pinInput = ""
                                onUnlocked()
                            } else {
                                authFeedback = "Incorrect Owner PIN"
                            }
                        },
                        enabled = pinInput.length >= 4,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("app_lock_pin_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock with Owner PIN")
                    }
                } else if (!capability.isUsable) {
                    Text(
                        text = "Tip: Enable device screen lock or set an Owner PIN in Settings -> Security.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
