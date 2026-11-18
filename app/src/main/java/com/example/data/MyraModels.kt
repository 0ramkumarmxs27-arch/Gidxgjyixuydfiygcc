package com.example.data

enum class AppLanguage(val label: String, val localeTag: String, val nativeLabel: String) {
    HINDI("Hindi", "hi-IN", "हिन्दी (Primary)"),
    ENGLISH("English", "en-IN", "English"),
    HINGLISH("Hinglish", "hi-IN", "Hinglish (Mix)")
}

enum class AssistantState {
    IDLE,
    STANDBY_WAKE_WORD,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class ThemeAccent(val label: String) {
    NEBULA_PURPLE("Nebula Purple"),
    CYBER_CYAN("Cyber Cyan"),
    MIDNIGHT_ROYAL("Midnight Royal")
}

enum class MyraPersonaMode(val label: String, val badge: String) {
    ASSISTANT("Assistant", "AI Assistant"),
    BEST_FRIEND("Best Friend", "Bestie"),
    GIRLFRIEND("Girlfriend Mode", "Girlfriend 💕")
}

enum class SpeakerAuthStatus(val label: String) {
    NOT_ENROLLED("Not Enrolled"),
    ENROLLED_UNVERIFIED("Enrolled • Ready"),
    VERIFIED_OWNER("Verified Owner"),
    REJECTED_UNKNOWN("Rejected • Unknown Speaker"),
    SPOOF_SUSPECTED("Blocked • Anti-Spoof Alert")
}

enum class CodeLanguage(val label: String) {
    HTML_JS("HTML / CSS / JS"),
    JAVASCRIPT("JavaScript"),
    MYRA_SCRIPT("Math / Algo Script")
}

data class MyraSettings(
    val language: AppLanguage = AppLanguage.HINDI,
    val personaMode: MyraPersonaMode = MyraPersonaMode.ASSISTANT,
    val selectedVoiceName: String = "LYRA_WARM",
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.15f,
    val speechVolume: Float = 1.0f,
    val warmLaughterEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = true,
    val customWakeWord: String = "MYRA",
    val standbyWakeWordOnly: Boolean = true,
    val wakeWordSensitivity: Float = 0.8f,
    val backgroundModeEnabled: Boolean = false,
    val alwaysLiveEnabled: Boolean = false,
    val memoryConsentEnabled: Boolean = true,
    val requireVoiceAuthForSensitive: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val biometricRequireOnAppLaunch: Boolean = false,
    val biometricRequireForSensitiveActions: Boolean = true,
    val pinHash: String = "",
    val geminiModelId: String = "gemini-3.5-flash-lite",
    val apiConfigNote: String = "",
    val themeAccent: ThemeAccent = ThemeAccent.NEBULA_PURPLE
)

data class ImageAnalysisInput(
    val displayName: String,
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String,
    val dominantTone: String,
    val bitmap: android.graphics.Bitmap? = null
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class BatteryTelemetry(
    val levelPercent: Int,
    val isCharging: Boolean,
    val statusLabel: String,
    val temperatureCelsius: Float,
    val voltageMillivolts: Int
)

data class PendingCallRequest(
    val targetDisplay: String,
    val phoneNumber: String
)

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val localeLabel: String,
    val isFemaleHint: Boolean
)
