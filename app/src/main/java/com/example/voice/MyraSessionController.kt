package com.example.voice

import android.content.Context
import com.example.ai.AiCodeResult
import com.example.ai.AiRepository
import com.example.ai.AiStreamEvent
import com.example.ai.FirebaseAiRepository
import com.example.data.AppLanguage
import com.example.data.AssistantState
import com.example.data.ChatMessageEntity
import com.example.data.ImageAnalysisInput
import com.example.data.MemoryFactEntity
import com.example.data.MyraDao
import com.example.data.MyraDatabase
import com.example.data.MyraPersonaMode
import com.example.data.MyraSettings
import com.example.data.MyraSettingsRepository
import com.example.data.PendingCallRequest
import com.example.data.SpeakerAuthStatus
import com.example.system.AndroidDeviceController
import com.example.system.MyraHomeWidgetProvider
import com.example.system.VoiceIntentParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-wide voice and AI session coordinator so MYRA stays continuously live
 * across app UI, persistent foreground service, Notification controls, and the
 * Android Home Screen Full-Body Widget.
 */
class MyraSessionController private constructor(
    val appContext: Context,
    val dao: MyraDao,
    val settingsRepo: MyraSettingsRepository,
    val aiRepository: AiRepository,
    val deviceController: AndroidDeviceController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: StateFlow<MyraSettings> = settingsRepo.settingsFlow
        .stateIn(scope, SharingStarted.Eagerly, MyraSettings())

    private val _continuousVoiceMode = MutableStateFlow(MyraSettingsRepository.isAlwaysLiveSync(appContext))
    val continuousVoiceMode = _continuousVoiceMode.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply = _streamingReply.asStateFlow()

    private val _pendingCall = MutableStateFlow<PendingCallRequest?>(null)
    val pendingCall = _pendingCall.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    private val _speakerAuthStatus = MutableStateFlow(SpeakerAuthStatus.NOT_ENROLLED)
    val speakerAuthStatus = _speakerAuthStatus.asStateFlow()

    private val _generatedCodeFlow = MutableSharedFlow<AiCodeResult>(extraBufferCapacity = 8)
    val generatedCodeFlow = _generatedCodeFlow.asSharedFlow()

    val voiceEngine = MyraVoiceEngine(
        context = appContext,
        onRecognizedSpeech = { hypotheses, fromStandby -> handleRecognizedSpeech(hypotheses, fromStandby) },
        onVoiceError = { err -> _toastMessage.value = err },
        isContinuousAlwaysLive = {
            _continuousVoiceMode.value ||
                settings.value.alwaysLiveEnabled ||
                MyraSettingsRepository.isAlwaysLiveSync(appContext)
        }
    )

    init {
        scope.launch {
            settings.collectLatest { latest ->
                voiceEngine.updateSettings(latest)
                if (latest.alwaysLiveEnabled && !_continuousVoiceMode.value) {
                    _continuousVoiceMode.value = true
                }
            }
        }
        scope.launch {
            MyraServiceBridge.assistantState.collectLatest {
                MyraHomeWidgetProvider.updateAllWidgets(appContext)
            }
        }
        scope.launch {
            MyraServiceBridge.commandFlow.collectLatest { cmd ->
                when (cmd) {
                    VoiceServiceCommand.GO_LIVE_FOREVER -> goLiveForever(speakGreeting = true)
                    VoiceServiceCommand.START_WAKE_STANDBY -> startWakeStandbyListening()
                    VoiceServiceCommand.START_LISTENING -> startActiveVoiceSession()
                    VoiceServiceCommand.STOP_INTERACTION -> stopCurrentInteraction()
                    VoiceServiceCommand.TURN_OFF_SERVICE -> turnOffForever()
                }
            }
        }
    }

    fun setSpeakerAuthStatus(status: SpeakerAuthStatus) {
        _speakerAuthStatus.value = status
    }

    fun setToast(message: String?) {
        _toastMessage.value = message
    }

    fun clearPendingCall() {
        _pendingCall.value = null
    }

    /**
     * One-tap Permanent Live ON: keeps MYRA running across background, screen switches,
     * Notification Panel, Home Screen Widget, and reboots until user taps OFF.
     */
    fun goLiveForever(speakGreeting: Boolean = true) {
        _continuousVoiceMode.value = true
        MyraSettingsRepository.setAlwaysLiveSync(appContext, true)
        scope.launch {
            settingsRepo.updateAlwaysLive(true)
            runCatching { MyraVoiceService.start(appContext) }
                .onFailure { _toastMessage.value = "Grant Microphone permission to keep MYRA Live." }
            MyraHomeWidgetProvider.updateAllWidgets(appContext)

            val current = settings.value
            val useWakeStandby = current.wakeWordEnabled && current.standbyWakeWordOnly
            if (speakGreeting) {
                val greeting = when (current.personaMode) {
                    MyraPersonaMode.GIRLFRIEND -> when (current.language) {
                        AppLanguage.HINDI -> "हाँ जान, मैं अब हमेशा के लिए लाइव हूँ! बोलो, क्या बात करनी है?"
                        AppLanguage.ENGLISH -> "Hey sweetheart, I am live now! Talk to me anytime."
                        AppLanguage.HINGLISH -> "Haan jaan, main ab live hoon! Bolo na, kya baat karni hai?"
                    }
                    MyraPersonaMode.BEST_FRIEND -> when (current.language) {
                        AppLanguage.HINDI -> "अरे यार, मैं अब लाइव हूँ! बताओ क्या बात करनी है?"
                        AppLanguage.ENGLISH -> "Hey bestie, I am live now! What's on your mind?"
                        AppLanguage.HINGLISH -> "Arre yaar, main live hoon! Batao kya baat karni hai?"
                    }
                    MyraPersonaMode.ASSISTANT -> when (current.language) {
                        AppLanguage.HINDI -> "मैं लाइव हूँ। बताइए, मैं आपकी क्या मदद करूँ?"
                        AppLanguage.ENGLISH -> "MYRA is live now. How can I help you?"
                        AppLanguage.HINGLISH -> "Main live hoon! Bataiye, kya help chahiye?"
                    }
                }
                voiceEngine.speak(greeting) {
                    if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                        voiceEngine.startListening(settings.value.language, standbyMode = useWakeStandby)
                    }
                }
            } else {
                voiceEngine.startListening(current.language, standbyMode = useWakeStandby)
            }
        }
    }

    /**
     * One-tap Permanent OFF: stops voice, mic, TTS, foreground service, and stays OFF
     * forever until user taps Live ON again.
     */
    fun turnOffForever() {
        _continuousVoiceMode.value = false
        MyraSettingsRepository.setAlwaysLiveSync(appContext, false)
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        MyraServiceBridge.setAssistantState(AssistantState.IDLE, "OFF • Tap GO LIVE")
        scope.launch {
            settingsRepo.updateAlwaysLive(false)
            MyraVoiceService.stop(appContext)
            MyraHomeWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun startActiveVoiceSession() {
        _continuousVoiceMode.value = true
        voiceEngine.startListening(settings.value.language, standbyMode = false)
    }

    fun startWakeStandbyListening() {
        _continuousVoiceMode.value = true
        runCatching { MyraVoiceService.start(appContext) }
        voiceEngine.startListening(settings.value.language, standbyMode = true)
    }

    fun stopCurrentInteraction() {
        _continuousVoiceMode.value = false
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Paused")
        MyraHomeWidgetProvider.updateAllWidgets(appContext)
    }

    private fun handleRecognizedSpeech(hypotheses: List<String>, fromStandby: Boolean) {
        val currentSettings = settings.value
        val evaluated = WakeWordDetector.evaluateHypotheses(
            hypotheses = hypotheses,
            customWakeWord = currentSettings.customWakeWord,
            sensitivity = currentSettings.wakeWordSensitivity
        ) ?: return

        val (spoken, match) = evaluated

        if (match.isStopCommand) {
            if (match.isPermanentOffCommand) turnOffForever() else stopCurrentInteraction()
            scope.launch {
                dao.insertChatMessage(ChatMessageEntity(role = "user", text = spoken))
                val ack = when (currentSettings.language) {
                    AppLanguage.HINDI -> if (match.isPermanentOffCommand) "ठीक है, मैं हमेशा के लिए ऑफ हो गई हूँ।" else "ठीक है, मैं रुक गई हूँ।"
                    AppLanguage.ENGLISH -> if (match.isPermanentOffCommand) "Okay, turning off completely." else "Okay, stopping voice interaction."
                    AppLanguage.HINGLISH -> if (match.isPermanentOffCommand) "Theek hai, main off ho gayi hoon." else "Theek hai, voice stop kar diya."
                }
                dao.insertChatMessage(ChatMessageEntity(role = "myra", text = ack))
            }
            return
        }

        // In Wake-Word Standby mode, ignore background speech unless the custom wake word was detected
        if (fromStandby && currentSettings.wakeWordEnabled && !match.matched) {
            if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                voiceEngine.startListening(currentSettings.language, standbyMode = true)
            }
            return
        }

        if (currentSettings.wakeWordEnabled && match.matched) {
            MyraServiceBridge.recordWakeDetected(match.matchedPhrase)
            if (match.remainingCommand.isBlank()) {
                val greeting = if (currentSettings.personaMode == MyraPersonaMode.GIRLFRIEND) {
                    when (currentSettings.language) {
                        AppLanguage.HINDI -> "हाँ जान, मैं सुन रही हूँ! बताओ न, आज क्या करना है?"
                        AppLanguage.ENGLISH -> "Yes sweetheart, I am listening! Tell me, what should we do?"
                        AppLanguage.HINGLISH -> "Haan jaan, main sun rahi hoon! Batao na, kya karna hai?"
                    }
                } else {
                    when (currentSettings.language) {
                        AppLanguage.HINDI -> "जी, मैं सुन रही हूँ। बताइए, क्या करना है?"
                        AppLanguage.ENGLISH -> "Yes, I am listening. Tell me, how can I help you?"
                        AppLanguage.HINGLISH -> "Ji, main sun rahi hoon! Bataiye, kya karna hai?"
                    }
                }
                _continuousVoiceMode.value = true
                scope.launch {
                    dao.insertChatMessage(ChatMessageEntity(role = "user", text = spoken))
                    dao.insertChatMessage(ChatMessageEntity(role = "myra", text = greeting))
                }
                voiceEngine.speak(greeting) {
                    if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                        // Transition into active listening so the user can immediately speak their request
                        voiceEngine.startListening(settings.value.language, standbyMode = false)
                    }
                }
                return
            } else {
                // Inline command like "Hey MYRA what's the battery?" -> execute immediately
                sendMessage(match.remainingCommand, speakResponse = true)
                return
            }
        }

        sendMessage(spoken, speakResponse = true)
    }

    fun sendMessage(
        rawPrompt: String,
        speakResponse: Boolean = true,
        imageInput: ImageAnalysisInput? = null,
        document: Pair<String, String>? = null
    ) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank() && imageInput == null && document == null) return

        val currentSettings = settings.value
        val lowerPrompt = prompt.lowercase()
        if (imageInput == null && document == null &&
            (lowerPrompt.contains("girlfriend mode") || lowerPrompt.contains("गर्लफ्रेंड मोड"))
        ) {
            val turnOff = lowerPrompt.contains("off") || lowerPrompt.contains("बंद") || lowerPrompt.contains("band")
            val newMode = if (turnOff) MyraPersonaMode.ASSISTANT else MyraPersonaMode.GIRLFRIEND
            scope.launch {
                settingsRepo.updatePersonaMode(newMode)
                dao.insertChatMessage(ChatMessageEntity(role = "user", text = prompt))
                val reply = if (newMode == MyraPersonaMode.GIRLFRIEND) {
                    when (currentSettings.language) {
                        AppLanguage.HINDI -> "हाहा, गर्लफ्रेंड मोड ऑन हो गया जान! अब मैं सिर्फ तुम्हारी प्यारी मायरा हूँ।"
                        AppLanguage.ENGLISH -> "Haha, Girlfriend Mode is on sweetheart! I'm all yours."
                        AppLanguage.HINGLISH -> "Haha, Girlfriend Mode on ho gaya jaan! Ab main tumhari pyari MYRA hoon."
                    }
                } else {
                    when (currentSettings.language) {
                        AppLanguage.HINDI -> "जी, अब मैं वापस असिस्टेंट मोड में हूँ।"
                        AppLanguage.ENGLISH -> "Switched back to Assistant Mode."
                        AppLanguage.HINGLISH -> "Ji, wapas Assistant Mode on kar diya."
                    }
                }
                dao.insertChatMessage(ChatMessageEntity(role = "myra", text = reply))
                if (speakResponse) {
                    voiceEngine.speak(reply) {
                        if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                            voiceEngine.startListening(settings.value.language, standbyMode = false)
                        }
                    }
                }
            }
            return
        }

        val effectivePrompt = prompt.ifBlank {
            if (imageInput != null) "Analyze this image" else "Summarize this document"
        }
        val attachmentLabel = imageInput?.displayName ?: document?.first

        scope.launch {
            dao.insertChatMessage(
                ChatMessageEntity(
                    role = "user",
                    text = effectivePrompt,
                    attachmentLabel = attachmentLabel
                )
            )

            if (imageInput == null && document == null) {
                when (val cmd = deviceController.tryExecuteNaturalCommand(effectivePrompt)) {
                    is VoiceIntentParseResult.NeedsCallConfirmation -> {
                        if (currentSettings.requireVoiceAuthForSensitive &&
                            _speakerAuthStatus.value != SpeakerAuthStatus.VERIFIED_OWNER
                        ) {
                            val msg = "Speaker verification required before placing calls. Verify voice or PIN in Settings."
                            dao.insertChatMessage(ChatMessageEntity(role = "myra", text = msg))
                            if (speakResponse) voiceEngine.speak(msg)
                            return@launch
                        }
                        _pendingCall.value = cmd.request
                        val ask = when (currentSettings.language) {
                            AppLanguage.HINDI -> "${cmd.request.targetDisplay} को कॉल करने से पहले कृपया स्क्रीन पर पुष्टि करें।"
                            AppLanguage.ENGLISH -> "Please confirm on screen before calling ${cmd.request.targetDisplay}."
                            AppLanguage.HINGLISH -> "${cmd.request.targetDisplay} ko call karne se pehle please confirm karein."
                        }
                        dao.insertChatMessage(ChatMessageEntity(role = "myra", text = ask))
                        if (speakResponse) voiceEngine.speak(ask)
                        return@launch
                    }

                    is VoiceIntentParseResult.Executed -> {
                        val reply = when (currentSettings.language) {
                            AppLanguage.HINDI, AppLanguage.HINGLISH -> cmd.spokenSummaryHi
                            AppLanguage.ENGLISH -> cmd.spokenSummaryEn
                        }
                        dao.insertChatMessage(
                            ChatMessageEntity(
                                role = "myra",
                                text = reply,
                                actionExecuted = cmd.actionName
                            )
                        )
                        if (speakResponse) {
                            voiceEngine.speak(reply) {
                                if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                                    voiceEngine.startListening(settings.value.language, standbyMode = false)
                                }
                            }
                        }
                        return@launch
                    }

                    VoiceIntentParseResult.NotADeviceCommand -> Unit
                }
            }

            MyraServiceBridge.setAssistantState(AssistantState.THINKING, "Thinking...")
            val facts = if (currentSettings.memoryConsentEnabled) {
                dao.observeMemoryFacts().first().map { it.fact }
            } else emptyList()
            val recent = dao.observeChatMessages().first().takeLast(8)

            aiRepository.streamAssistantReply(
                prompt = effectivePrompt,
                language = currentSettings.language,
                personaMode = currentSettings.personaMode,
                warmLaughterEnabled = currentSettings.warmLaughterEnabled,
                memoryFacts = facts,
                recentHistory = recent,
                imageInput = imageInput,
                documentText = document?.second,
                documentName = document?.first
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> _streamingReply.value = event.accumulatedText
                    is AiStreamEvent.Completed -> {
                        _streamingReply.value = null
                        dao.insertChatMessage(
                            ChatMessageEntity(
                                role = "myra",
                                text = event.fullText,
                                isSamplePlaceholder = event.isSamplePlaceholder
                            )
                        )
                        if (event.suggestedMemoryFact != null && currentSettings.memoryConsentEnabled) {
                            dao.insertMemoryFact(MemoryFactEntity(fact = event.suggestedMemoryFact))
                        }
                        if (event.generatedCode != null) {
                            _generatedCodeFlow.tryEmit(event.generatedCode)
                        }
                        if (speakResponse) {
                            voiceEngine.speak(event.fullText) {
                                if (_continuousVoiceMode.value || MyraSettingsRepository.isAlwaysLiveSync(appContext)) {
                                    voiceEngine.startListening(settings.value.language, standbyMode = false)
                                }
                            }
                        } else {
                            MyraServiceBridge.setAssistantState(AssistantState.IDLE, "Ready")
                        }
                    }

                    is AiStreamEvent.Error -> {
                        _streamingReply.value = null
                        _toastMessage.value = event.message
                        dao.insertChatMessage(
                            ChatMessageEntity(
                                role = "myra",
                                text = "Error: ${event.message}",
                                isSamplePlaceholder = false
                            )
                        )
                        MyraServiceBridge.setAssistantState(AssistantState.IDLE, "AI Error")
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MyraSessionController? = null

        fun getIfInitialized(): MyraSessionController? = INSTANCE

        fun getInstance(context: Context): MyraSessionController {
            return INSTANCE ?: synchronized(this) {
                val existing = INSTANCE
                if (existing != null) existing
                else {
                    val appCtx = context.applicationContext
                    val db = MyraDatabase.getInstance(appCtx)
                    MyraSessionController(
                        appContext = appCtx,
                        dao = db.myraDao(),
                        settingsRepo = MyraSettingsRepository(appCtx),
                        aiRepository = FirebaseAiRepository(),
                        deviceController = AndroidDeviceController(appCtx)
                    ).also { INSTANCE = it }
                }
            }
        }
    }
}
