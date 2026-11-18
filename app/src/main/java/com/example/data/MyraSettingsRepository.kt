package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.myraDataStore: DataStore<Preferences> by preferencesDataStore(name = "myra_settings")

class MyraSettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val PERSONA_MODE = stringPreferencesKey("persona_mode")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val SPEECH_VOLUME = floatPreferencesKey("speech_volume")
        val WARM_LAUGHTER = booleanPreferencesKey("warm_laughter")
        val WAKE_WORD = booleanPreferencesKey("wake_word")
        val CUSTOM_WAKE_WORD = stringPreferencesKey("custom_wake_word")
        val STANDBY_WAKE_ONLY = booleanPreferencesKey("standby_wake_only")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val BACKGROUND_MODE = booleanPreferencesKey("background_mode")
        val ALWAYS_LIVE = booleanPreferencesKey("always_live")
        val MEMORY_CONSENT = booleanPreferencesKey("memory_consent")
        val REQUIRE_VOICE_AUTH = booleanPreferencesKey("require_voice_auth")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val BIOMETRIC_ON_APP_LAUNCH = booleanPreferencesKey("biometric_on_app_launch")
        val BIOMETRIC_SENSITIVE_ACTIONS = booleanPreferencesKey("biometric_sensitive_actions")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val API_CONFIG_NOTE = stringPreferencesKey("api_config_note")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
    }

    val settingsFlow: Flow<MyraSettings> = context.myraDataStore.data.map { prefs ->
        MyraSettings(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.HINDI,
            personaMode = prefs[Keys.PERSONA_MODE]?.let { runCatching { MyraPersonaMode.valueOf(it) }.getOrNull() }
                ?: MyraPersonaMode.ASSISTANT,
            selectedVoiceName = prefs[Keys.VOICE_NAME] ?: "LYRA_WARM",
            speechSpeed = prefs[Keys.SPEECH_SPEED] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 1.15f,
            speechVolume = prefs[Keys.SPEECH_VOLUME] ?: 1.0f,
            warmLaughterEnabled = prefs[Keys.WARM_LAUGHTER] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD] ?: true,
            customWakeWord = prefs[Keys.CUSTOM_WAKE_WORD]?.takeIf { it.isNotBlank() } ?: "MYRA",
            standbyWakeWordOnly = prefs[Keys.STANDBY_WAKE_ONLY] ?: true,
            wakeWordSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.8f,
            backgroundModeEnabled = prefs[Keys.BACKGROUND_MODE] ?: false,
            alwaysLiveEnabled = prefs[Keys.ALWAYS_LIVE] ?: false,
            memoryConsentEnabled = prefs[Keys.MEMORY_CONSENT] ?: true,
            requireVoiceAuthForSensitive = prefs[Keys.REQUIRE_VOICE_AUTH] ?: false,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            biometricRequireOnAppLaunch = prefs[Keys.BIOMETRIC_ON_APP_LAUNCH] ?: false,
            biometricRequireForSensitiveActions = prefs[Keys.BIOMETRIC_SENSITIVE_ACTIONS] ?: true,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            geminiModelId = prefs[Keys.GEMINI_MODEL_ID] ?: "gemini-3.5-flash-lite",
            apiConfigNote = prefs[Keys.API_CONFIG_NOTE] ?: "",
            themeAccent = prefs[Keys.THEME_ACCENT]?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() }
                ?: ThemeAccent.NEBULA_PURPLE
        )
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.myraDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updatePersonaMode(mode: MyraPersonaMode) {
        context.myraDataStore.edit { it[Keys.PERSONA_MODE] = mode.name }
    }

    suspend fun updateVoiceName(voiceName: String) {
        context.myraDataStore.edit { it[Keys.VOICE_NAME] = voiceName }
    }

    suspend fun updateSpeechSpeed(speed: Float) {
        context.myraDataStore.edit { it[Keys.SPEECH_SPEED] = speed.coerceIn(0.6f, 1.8f) }
    }

    suspend fun updateSpeechPitch(pitch: Float) {
        context.myraDataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.75f, 1.45f) }
    }

    suspend fun updateSpeechVolume(volume: Float) {
        context.myraDataStore.edit { it[Keys.SPEECH_VOLUME] = volume.coerceIn(0.2f, 1.0f) }
    }

    suspend fun updateWarmLaughter(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.WARM_LAUGHTER] = enabled }
    }

    suspend fun updateWakeWord(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.WAKE_WORD] = enabled }
    }

    suspend fun updateCustomWakeWord(wakeWord: String) {
        val cleaned = wakeWord.trim().ifBlank { "MYRA" }.take(28)
        context.myraDataStore.edit { it[Keys.CUSTOM_WAKE_WORD] = cleaned }
    }

    suspend fun updateStandbyWakeWordOnly(wakeOnly: Boolean) {
        context.myraDataStore.edit { it[Keys.STANDBY_WAKE_ONLY] = wakeOnly }
    }

    suspend fun updateWakeWordSensitivity(sensitivity: Float) {
        context.myraDataStore.edit { it[Keys.WAKE_SENSITIVITY] = sensitivity.coerceIn(0.4f, 1.0f) }
    }

    suspend fun updateBackgroundMode(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.BACKGROUND_MODE] = enabled }
    }

    suspend fun updateAlwaysLive(enabled: Boolean) {
        setAlwaysLiveSync(context, enabled)
        context.myraDataStore.edit {
            it[Keys.ALWAYS_LIVE] = enabled
            it[Keys.BACKGROUND_MODE] = enabled
        }
    }

    suspend fun updateMemoryConsent(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.MEMORY_CONSENT] = enabled }
    }

    suspend fun updateRequireVoiceAuth(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.REQUIRE_VOICE_AUTH] = enabled }
    }

    suspend fun updateBiometricLock(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun updateBiometricOnAppLaunch(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.BIOMETRIC_ON_APP_LAUNCH] = enabled }
    }

    suspend fun updateBiometricForSensitiveActions(enabled: Boolean) {
        context.myraDataStore.edit { it[Keys.BIOMETRIC_SENSITIVE_ACTIONS] = enabled }
    }

    suspend fun setPin(rawPin: String) {
        val hash = sha256(rawPin)
        context.myraDataStore.edit { it[Keys.PIN_HASH] = hash }
    }

    suspend fun clearPin() {
        context.myraDataStore.edit { it[Keys.PIN_HASH] = "" }
    }

    fun verifyPin(rawPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return true
        return sha256(rawPin) == storedHash
    }

    suspend fun updateModelAndApiNote(modelId: String, apiNote: String) {
        context.myraDataStore.edit {
            it[Keys.GEMINI_MODEL_ID] = modelId
            it[Keys.API_CONFIG_NOTE] = apiNote
        }
    }

    suspend fun updateThemeAccent(accent: ThemeAccent) {
        context.myraDataStore.edit { it[Keys.THEME_ACCENT] = accent.name }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SYNC_PREFS = "myra_live_sync_prefs"
        private const val KEY_ALWAYS_LIVE_SYNC = "always_live_forever"

        fun isAlwaysLiveSync(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALWAYS_LIVE_SYNC, false)
        }

        fun setAlwaysLiveSync(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALWAYS_LIVE_SYNC, enabled)
                .apply()
        }
    }
}
