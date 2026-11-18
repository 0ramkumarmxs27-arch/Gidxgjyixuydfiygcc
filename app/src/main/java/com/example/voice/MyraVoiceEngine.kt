package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.example.data.AppLanguage
import com.example.data.AssistantState
import com.example.data.MyraSettings
import com.example.data.TtsVoiceOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

class MyraVoiceEngine(
    context: Context,
    private val onRecognizedSpeech: (hypotheses: List<String>, standbyMode: Boolean) -> Unit,
    private val onVoiceError: (String) -> Unit,
    private val isContinuousAlwaysLive: () -> Boolean = { false }
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var lipSyncJob: Job? = null
    private var autoRearmJob: Job? = null
    private var currentUtteranceText: String = ""
    private var activeSettings: MyraSettings = MyraSettings()
    private var onSpeechCompletedCallback: (() -> Unit)? = null
    private var consecutiveErrors = 0
    private var activeStandbySession = false

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText = _partialText.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                refreshVoices()
                attachUtteranceListener()
            }
        }
    }

    fun updateSettings(settings: MyraSettings) {
        activeSettings = settings
        applyTtsParameters(settings)
    }

    private fun refreshVoices() {
        val engine = tts ?: return
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val filtered = voices
            .filter { v ->
                val lang = v.locale.language.lowercase()
                lang == "hi" || lang == "en"
            }
            .sortedWith(compareByDescending<Voice> { it.locale.language == "hi" }
                .thenByDescending { isFemaleVoiceHint(it.name) })
            .take(16)
            .map { v ->
                TtsVoiceOption(
                    id = v.name,
                    displayName = formatVoiceName(v),
                    localeLabel = "${v.locale.language}-${v.locale.country}",
                    isFemaleHint = isFemaleVoiceHint(v.name)
                )
            }

        val lyraPreset = TtsVoiceOption(
            id = "LYRA_WARM",
            displayName = "Lyra Warm (Female Hindi/English)",
            localeLabel = "hi-IN / en-IN",
            isFemaleHint = true
        )
        _availableVoices.value = listOf(lyraPreset) + filtered
        applyTtsParameters(activeSettings)
    }

    private fun applyTtsParameters(settings: MyraSettings) {
        val engine = tts ?: return
        if (!ttsReady) return

        val targetLocale = when (settings.language) {
            AppLanguage.HINDI, AppLanguage.HINGLISH -> Locale("hi", "IN")
            AppLanguage.ENGLISH -> Locale("en", "IN")
        }
        engine.language = targetLocale
        engine.setSpeechRate(settings.speechSpeed)
        engine.setPitch(settings.speechPitch)

        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        if (settings.selectedVoiceName != "LYRA_WARM") {
            voices.firstOrNull { it.name == settings.selectedVoiceName }?.let {
                engine.voice = it
            }
        } else {
            val preferred = voices.firstOrNull {
                it.locale.language == targetLocale.language && isFemaleVoiceHint(it.name)
            } ?: voices.firstOrNull {
                it.locale.language == targetLocale.language
            }
            if (preferred != null) {
                engine.voice = preferred
            }
        }
    }

    private fun attachUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    MyraServiceBridge.setAssistantState(AssistantState.SPEAKING, "MYRA is speaking...")
                    startAmbientSpeechLipMovement()
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val wordSlice = if (start in 0 until currentUtteranceText.length && end > start) {
                    currentUtteranceText.substring(start, end.coerceAtMost(currentUtteranceText.length))
                } else {
                    ""
                }
                val vowelEnergy = computeSyllableOpenness(wordSlice)
                scope.launch {
                    pulseWordLipMovement(vowelEnergy)
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    stopLipSync()
                    MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
                    onSpeechCompletedCallback?.invoke()
                    onSpeechCompletedCallback = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    stopLipSync()
                    MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
                    onSpeechCompletedCallback = null
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch {
                    stopLipSync()
                    MyraServiceBridge.setAssistantState(AssistantState.IDLE, "TTS error ($errorCode)")
                    onSpeechCompletedCallback = null
                }
            }
        })
    }

    fun speak(text: String, onCompleted: (() -> Unit)? = null) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            onCompleted?.invoke()
            return
        }
        val engine = tts
        if (engine == null || !ttsReady) {
            onCompleted?.invoke()
            return
        }
        applyTtsParameters(activeSettings)
        currentUtteranceText = clean
        onSpeechCompletedCallback = onCompleted
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, activeSettings.speechVolume)
        }
        val utteranceId = "myra_${UUID.randomUUID()}"
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        onSpeechCompletedCallback = null
        stopLipSync()
        if (MyraServiceBridge.assistantState.value == AssistantState.SPEAKING) {
            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Stopped")
        }
    }

    fun startListening(language: AppLanguage, standbyMode: Boolean = false) {
        autoRearmJob?.cancel()
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onVoiceError("Microphone permission is required for continuous listening.")
            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Mic permission needed")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onVoiceError("Speech recognition service is unavailable on this device.")
            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Speech service unavailable")
            return
        }

        activeStandbySession = standbyMode
        stopSpeaking()
        _partialText.value = ""
        val wakeWordLabel = activeSettings.customWakeWord.ifBlank { "MYRA" }

        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        consecutiveErrors = 0
                        val state = if (standbyMode) AssistantState.STANDBY_WAKE_WORD else AssistantState.LISTENING
                        val label = if (standbyMode) {
                            "Wake Standby • Say \"$wakeWordLabel\""
                        } else {
                            "Listening..."
                        }
                        MyraServiceBridge.setAssistantState(state, label)
                    }

                    override fun onBeginningOfSpeech() {
                        MyraServiceBridge.setAssistantState(
                            if (standbyMode) AssistantState.STANDBY_WAKE_WORD else AssistantState.LISTENING,
                            if (standbyMode) "Detecting \"$wakeWordLabel\"..." else "Hearing voice..."
                        )
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        MyraServiceBridge.setMicRms(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        MyraServiceBridge.setMicRms(0f)
                    }

                    override fun onError(error: Int) {
                        MyraServiceBridge.setMicRms(0f)
                        val keepLive = isContinuousAlwaysLive()
                        val isTransientSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_CLIENT
                        val isRecoverableInContinuous = isTransientSilence ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                        if (keepLive && isRecoverableInContinuous) {
                            consecutiveErrors = (consecutiveErrors + 1).coerceAtMost(5)
                            // If user was in active follow-up and timed out in silence, return cleanly to Wake Standby when enabled
                            val nextStandby = if (!standbyMode &&
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                                activeSettings.wakeWordEnabled &&
                                activeSettings.standbyWakeWordOnly
                            ) {
                                true
                            } else {
                                standbyMode
                            }
                            MyraServiceBridge.setAssistantState(
                                if (nextStandby) AssistantState.STANDBY_WAKE_WORD else AssistantState.LISTENING,
                                if (nextStandby) "Wake Standby • Say \"$wakeWordLabel\"" else "Live • Listening..."
                            )
                            scheduleAutoRearm(language, nextStandby)
                        } else if (standbyMode && isTransientSilence) {
                            consecutiveErrors = (consecutiveErrors + 1).coerceAtMost(5)
                            MyraServiceBridge.setAssistantState(
                                AssistantState.STANDBY_WAKE_WORD,
                                "Wake Standby • Say \"$wakeWordLabel\""
                            )
                            scheduleAutoRearm(language, standbyMode = true)
                        } else {
                            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
                            if (!isTransientSilence) {
                                onVoiceError(mapRecognizerError(error))
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        MyraServiceBridge.setMicRms(0f)
                        consecutiveErrors = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val best = matches.firstOrNull()?.trim().orEmpty()
                        _partialText.value = ""
                        if (matches.any { it.isNotBlank() }) {
                            if (best.isNotBlank()) MyraServiceBridge.emitTranscript(best)
                            onRecognizedSpeech(matches, standbyMode)
                        } else if (isContinuousAlwaysLive() || standbyMode) {
                            val nextStandby = standbyMode ||
                                (activeSettings.wakeWordEnabled && activeSettings.standbyWakeWordOnly)
                            MyraServiceBridge.setAssistantState(
                                if (nextStandby) AssistantState.STANDBY_WAKE_WORD else AssistantState.LISTENING,
                                if (nextStandby) "Wake Standby • Say \"$wakeWordLabel\"" else "Live • Listening..."
                            )
                            scheduleAutoRearm(language, nextStandby)
                        } else {
                            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        _partialText.value = partial
                        if (standbyMode && partial.isNotBlank() && activeSettings.wakeWordEnabled) {
                            val probe = WakeWordDetector.evaluate(
                                transcript = partial,
                                customWakeWord = activeSettings.customWakeWord,
                                sensitivity = activeSettings.wakeWordSensitivity
                            )
                            if (probe.matched && !probe.isStopCommand) {
                                MyraServiceBridge.recordWakeDetected(probe.matchedPhrase)
                                MyraServiceBridge.setAssistantState(
                                    AssistantState.LISTENING,
                                    "Wake heard: \"${probe.matchedPhrase}\""
                                )
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
            }
            speechRecognizer?.startListening(intent)
        }.onFailure {
            onVoiceError(it.localizedMessage ?: "Failed to start microphone recognition.")
            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
        }
    }

    fun stopListening() {
        autoRearmJob?.cancel()
        activeStandbySession = false
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
        MyraServiceBridge.setMicRms(0f)
        _partialText.value = ""
        if (MyraServiceBridge.assistantState.value == AssistantState.LISTENING ||
            MyraServiceBridge.assistantState.value == AssistantState.STANDBY_WAKE_WORD
        ) {
            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Stopped")
        }
    }

    private fun scheduleAutoRearm(language: AppLanguage, standbyMode: Boolean) {
        autoRearmJob?.cancel()
        val backoffMs = (350L + consecutiveErrors * 250L).coerceAtMost(1500L)
        autoRearmJob = scope.launch {
            delay(backoffMs)
            val shouldKeepListening = isContinuousAlwaysLive() || standbyMode
            if (shouldKeepListening && MyraServiceBridge.assistantState.value != AssistantState.SPEAKING) {
                startListening(language, standbyMode)
            }
        }
    }

    private fun startAmbientSpeechLipMovement() {
        lipSyncJob?.cancel()
        lipSyncJob = scope.launch {
            var phase = 0f
            while (MyraServiceBridge.assistantState.value == AssistantState.SPEAKING) {
                phase += 0.45f
                val wave = ((sin(phase.toDouble()) + 1.0) / 2.0).toFloat()
                val current = MyraServiceBridge.mouthOpenness.value
                val blended = (current * 0.65f + (0.2f + wave * 0.45f) * 0.35f).coerceIn(0.12f, 0.95f)
                MyraServiceBridge.setMouthOpenness(blended)
                delay(55L)
            }
            MyraServiceBridge.setMouthOpenness(0f)
        }
    }

    private suspend fun pulseWordLipMovement(targetOpenness: Float) {
        MyraServiceBridge.setMouthOpenness(targetOpenness)
        delay(85L)
        MyraServiceBridge.setMouthOpenness((targetOpenness * 0.45f).coerceAtLeast(0.15f))
    }

    private fun stopLipSync() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        MyraServiceBridge.setMouthOpenness(0f)
    }

    private fun computeSyllableOpenness(word: String): Float {
        if (word.isBlank()) return 0.25f
        val openVowels = "aAeEoOuUआअइईउऊएऐओऔािीुूेैोौ"
        val vowelCount = word.count { it in openVowels }
        val ratio = (vowelCount.toFloat() / word.length.coerceAtLeast(1))
        return (0.42f + ratio * 0.53f).coerceIn(0.35f, 0.96f)
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace("[सैंपल ऑफ़लाइन मोड:", "")
            .replace("[Sample Offline Mode:", "")
            .replace("]", "")
            .replace("*", "")
            .replace("#", "")
            .trim()
    }

    private fun isFemaleVoiceHint(voiceName: String): Boolean {
        val lower = voiceName.lowercase()
        return lower.contains("female") || lower.contains("hid") || lower.contains("hie") ||
            lower.contains("en-in-x-end") || lower.contains("hi-in-x-hia") ||
            lower.contains("hi-in-x-hie") || lower.contains("lyra") || lower.contains("sfg")
    }

    private fun formatVoiceName(voice: Voice): String {
        val tag = if (isFemaleVoiceHint(voice.name)) "Female" else "Neural"
        val short = voice.name.substringAfterLast("-").take(12)
        return "MYRA $tag ($short)"
    }

    private fun mapRecognizerError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network unavailable"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, retrying shortly"
        else -> "Speech recognition error ($error)"
    }

    fun release() {
        stopListening()
        stopSpeaking()
        runCatching { speechRecognizer?.destroy() }
        runCatching { tts?.shutdown() }
        scope.cancel()
    }
}
