package com.example.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed class BiometricCapabilityStatus(
    val title: String,
    val description: String,
    val isUsable: Boolean
) {
    data class Ready(val strongSupported: Boolean) : BiometricCapabilityStatus(
        title = if (strongSupported) "Fingerprint / Class-3 Biometric Ready" else "Biometric & Device Unlock Ready",
        description = if (strongSupported) {
            "Hardware biometric sensor (fingerprint / face) is enrolled and active."
        } else {
            "Biometric or device screen-lock credential is active."
        },
        isUsable = true
    )

    data object NoneEnrolled : BiometricCapabilityStatus(
        title = "No Biometrics Enrolled",
        description = "Add a fingerprint, face unlock, or screen lock in Android Settings to enable biometric protection.",
        isUsable = false
    )

    data object HardwareUnavailable : BiometricCapabilityStatus(
        title = "Biometric Sensor Busy / Unavailable",
        description = "The biometric sensor is temporarily unavailable or requires a security update.",
        isUsable = false
    )

    data object Unsupported : BiometricCapabilityStatus(
        title = "Biometric Hardware Unsupported",
        description = "This device does not expose compatible biometric authentication.",
        isUsable = false
    )
}

object MyraBiometricAuthenticator {

    const val ALLOWED_AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    const val STRONG_BIOMETRIC_ONLY: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun checkCapability(context: Context): BiometricCapabilityStatus {
        val manager = BiometricManager.from(context)
        val combinedResult = manager.canAuthenticate(ALLOWED_AUTHENTICATORS)
        val strongResult = manager.canAuthenticate(STRONG_BIOMETRIC_ONLY)
        return mapCapability(combinedResult, strongResult == BiometricManager.BIOMETRIC_SUCCESS)
    }

    fun mapCapability(
        combinedResult: Int,
        strongSupported: Boolean = false
    ): BiometricCapabilityStatus {
        return when (combinedResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.Ready(strongSupported)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricCapabilityStatus.HardwareUnavailable
            else -> BiometricCapabilityStatus.Unsupported
        }
    }

    fun buildEnrollmentIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, ALLOWED_AUTHENTICATORS)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        when (val capability = checkCapability(activity)) {
            is BiometricCapabilityStatus.Ready -> Unit
            is BiometricCapabilityStatus.NoneEnrolled,
            is BiometricCapabilityStatus.HardwareUnavailable,
            is BiometricCapabilityStatus.Unsupported -> {
                onError(capability.description)
                return
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val readable = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> "Authentication canceled."
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Too many attempts. Try device PIN or wait briefly."
                        else -> errString.toString()
                    }
                    onError(readable)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)

        if (description.isNotBlank()) {
            builder.setDescription(description)
        }

        prompt.authenticate(builder.build())
    }
}
