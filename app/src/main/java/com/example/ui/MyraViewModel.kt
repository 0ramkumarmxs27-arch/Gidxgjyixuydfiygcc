package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiRepository
import com.example.data.AppLanguage
import com.example.data.BatteryTelemetry
import com.example.data.ChatMessageEntity
import com.example.data.CodeLanguage
import com.example.data.ImageAnalysisInput
import com.example.data.InstalledAppInfo
import com.example.data.MemoryFactEntity
import com.example.data.MyraDao
import com.example.data.MyraDatabase
import com.example.data.MyraPersonaMode
import com.example.data.MyraSettings
import com.example.data.MyraSettingsRepository
import com.example.data.PendingCallRequest
import com.example.data.SpeakerAuthStatus
import com.example.data.TaskReminderEntity
import com.example.data.ThemeAccent
import com.example.data.VoiceProfileEntity
import com.example.system.AndroidDeviceController
import com.example.system.MyraScriptInterpreter
import com.example.system.ReminderScheduler
import com.example.voice.MyraServiceBridge
import com.example.voice.MyraSessionController
import com.example.voice.SpeakerVerificationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyraViewModel(
    private val appContext: Context,
    private val dao: MyraDao,
    private val settingsRepo: MyraSettingsRepository,
    private val aiRepository: AiRepository,
    private val deviceController: AndroidDeviceController,
    private val sessionController: MyraSessionController
) : ViewModel() {

    private val speakerEngine = SpeakerVerificationEngine()
    private val enrollmentBuffers = mutableListOf<FloatArray>()

    val voiceEngine get() = sessionController.voiceEngine

    val settings: StateFlow<MyraSettings> = sessionController.settings

    val chatMessages: StateFlow<List<ChatMessageEntity>> = dao.observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryFacts: StateFlow<List<MemoryFactEntity>> = dao.observeMemoryFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskReminderEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceProfile: StateFlow<VoiceProfileEntity?> = dao.observeVoiceProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val assistantState = MyraServiceBridge.assistantState
    val micRms = MyraServiceBridge.micRms
    val mouthOpenness = MyraServiceBridge.mouthOpenness
    val serviceRunning = MyraServiceBridge.serviceRunning
    val statusSubtitle = MyraServiceBridge.statusSubtitle
    val lastWakeEvent = MyraServiceBridge.lastWakeEvent
    val availableVoices = sessionController.voiceEngine.availableVoices
    val partialTranscript = sessionController.voiceEngine.partialText

    val streamingReply: StateFlow<String?> = sessionController.streamingReply
    val continuousVoiceMode: StateFlow<Boolean> = sessionController.continuousVoiceMode
    val pendingCall: StateFlow<PendingCallRequest?> = sessionController.pendingCall
    val speakerAuthStatus: StateFlow<SpeakerAuthStatus> = sessionController.speakerAuthStatus

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _batteryTelemetry = MutableStateFlow(deviceController.readBatteryTelemetry())
    val batteryTelemetry = _batteryTelemetry.asStateFlow()

    private val _attachedImage = MutableStateFlow<ImageAnalysisInput?>(null)
    val attachedImage = _attachedImage.asStateFlow()

    private val _attachedDoc = MutableStateFlow<Pair<String, String>?>(null)
    val attachedDoc = _attachedDoc.asStateFlow()

    private val _speakerFeedback = MutableStateFlow("Enroll 3 voice samples to secure owner actions.")
    val speakerFeedback = _speakerFeedback.asStateFlow()

    private val _enrollmentStep = MutableStateFlow(0)
    val enrollmentStep = _enrollmentStep.asStateFlow()

    private val _isRecordingVoiceprint = MutableStateFlow(false)
    val isRecordingVoiceprint = _isRecordingVoiceprint.asStateFlow()

    val toastMessage: StateFlow<String?> = sessionController.toastMessage

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked = _isSessionUnlocked.asStateFlow()

    // Code Studio State
    private val _codeLanguage = MutableStateFlow(CodeLanguage.HTML_JS)
    val codeLanguage = _codeLanguage.asStateFlow()

    private val _codeSource = MutableStateFlow(DEFAULT_HTML_SAMPLE)
    val codeSource = _codeSource.asStateFlow()

    private val _codeLogs = MutableStateFlow<List<String>>(listOf("MYRA Sandbox ready. Edit or generate code and tap Run."))
    val codeLogs = _codeLogs.asStateFlow()

    private val _codeRunTrigger = MutableStateFlow(0)
    val codeRunTrigger = _codeRunTrigger.asStateFlow()

    init {
        refreshDeviceState()
        viewModelScope.launch {
            voiceProfile.collectLatest { profile ->
                if (profile == null) {
                    sessionController.setSpeakerAuthStatus(SpeakerAuthStatus.NOT_ENROLLED)
                } else if (speakerAuthStatus.value == SpeakerAuthStatus.NOT_ENROLLED) {
                    sessionController.setSpeakerAuthStatus(SpeakerAuthStatus.ENROLLED_UNVERIFIED)
                    _speakerFeedback.value = "Enrolled for ${profile.ownerName} (${profile.sampleCount} samples)"
                }
            }
        }
        viewModelScope.launch {
            sessionController.generatedCodeFlow.collectLatest { generated ->
                _codeLanguage.value = generated.language
                _codeSource.value = generated.code
            }
        }
    }

    fun refreshDeviceState() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = deviceController.getInstalledLaunchableApps()
            val battery = deviceController.readBatteryTelemetry()
            _installedApps.value = apps
            _batteryTelemetry.value = battery
        }
    }

    fun goLiveForever() = sessionController.goLiveForever(speakGreeting = true)

    fun turnOffForever() = sessionController.turnOffForever()

    fun startActiveVoiceSession() = sessionController.startActiveVoiceSession()

    fun startWakeStandbyListening() = sessionController.startWakeStandbyListening()

    fun stopAllVoiceInteraction() = sessionController.stopCurrentInteraction()

    fun sendMessage(rawPrompt: String, speakResponse: Boolean = true) {
        val img = _attachedImage.value
        val doc = _attachedDoc.value
        if (rawPrompt.isBlank() && img == null && doc == null) return
        _attachedImage.value = null
        _attachedDoc.value = null
        sessionController.sendMessage(
            rawPrompt = rawPrompt,
            speakResponse = speakResponse,
            imageInput = img,
            document = doc
        )
    }

    fun confirmPhoneCall(phoneNumber: String) {
        sessionController.clearPendingCall()
        val ok = deviceController.initiateDialConfirmed(phoneNumber)
        sessionController.setToast(if (ok) "Opening dialer for $phoneNumber" else "Dialer unavailable")
    }

    fun dismissPhoneCall() = sessionController.clearPendingCall()

    fun attachImageUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val name = queryDisplayName(uri) ?: "Selected Photo"
            val mime = resolver.getType(uri) ?: "image/*"
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val tone = if (bitmap != null) {
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                when {
                    r > g + 30 && r > b + 30 -> "Warm Coral/Red"
                    b > r + 25 && b > g + 15 -> "Cool Blue/Cyan"
                    g > r + 20 && g > b + 20 -> "Natural Green"
                    (r + g + b) / 3 > 190 -> "Bright Light"
                    (r + g + b) / 3 < 65 -> "Deep Dark"
                    else -> "Balanced Neutral"
                }
            } else "Unknown"
            _attachedImage.value = ImageAnalysisInput(
                displayName = name,
                widthPx = (bitmap?.width ?: 0) * 4,
                heightPx = (bitmap?.height ?: 0) * 4,
                mimeType = mime,
                dominantTone = tone,
                bitmap = bitmap
            )
        }
    }

    fun attachDocumentUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = queryDisplayName(uri) ?: "Document"
            val text = runCatching {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText().take(4000)
                }
            }.getOrNull().orEmpty().ifBlank {
                "Binary or formatted document ($name) attached for inspection."
            }
            _attachedDoc.value = name to text
        }
    }

    fun clearAttachments() {
        _attachedImage.value = null
        _attachedDoc.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    // --- Background Foreground Service ---
    fun toggleBackgroundService(enable: Boolean) {
        if (enable) {
            goLiveForever()
        } else {
            turnOffForever()
        }
    }

    // --- Owner Voice Enrollment & Verification ---
    fun recordEnrollmentSample(ownerName: String) {
        if (_isRecordingVoiceprint.value) return
        viewModelScope.launch {
            _isRecordingVoiceprint.value = true
            _speakerFeedback.value = "Speak clearly for 2.5 seconds..."
            val res = speakerEngine.recordAndExtractVoiceprint(2400L)
            _isRecordingVoiceprint.value = false
            res.onSuccess { analysis ->
                if (!analysis.isLiveHumanSpeech) {
                    _speakerFeedback.value = "Sample rejected: ${analysis.livenessReason}. Speak naturally."
                    return@onSuccess
                }
                enrollmentBuffers.add(analysis.featureVector)
                _enrollmentStep.value = enrollmentBuffers.size
                if (enrollmentBuffers.size >= 3) {
                    val averaged = speakerEngine.averageVectors(enrollmentBuffers)
                    val csv = averaged.joinToString(",")
                    dao.upsertVoiceProfile(
                        VoiceProfileEntity(
                            ownerName = ownerName.ifBlank { "Owner" },
                            featureVectorCsv = csv,
                            sampleCount = enrollmentBuffers.size
                        )
                    )
                    enrollmentBuffers.clear()
                    _enrollmentStep.value = 0
                    sessionController.setSpeakerAuthStatus(SpeakerAuthStatus.VERIFIED_OWNER)
                    _speakerFeedback.value = "Owner voiceprint securely enrolled & active!"
                } else {
                    _speakerFeedback.value = "Sample ${enrollmentBuffers.size}/3 captured. Tap again to record next."
                }
            }.onFailure {
                _speakerFeedback.value = it.localizedMessage ?: "Audio capture failed"
            }
        }
    }

    fun verifyOwnerVoiceNow() {
        if (_isRecordingVoiceprint.value) return
        viewModelScope.launch {
            val profile = dao.getVoiceProfile()
            if (profile == null) {
                _speakerFeedback.value = "No owner voice enrolled yet."
                return@launch
            }
            _isRecordingVoiceprint.value = true
            _speakerFeedback.value = "Verifying live voiceprint & anti-spoofing..."
            val res = speakerEngine.recordAndExtractVoiceprint(2200L)
            _isRecordingVoiceprint.value = false
            res.onSuccess { candidate ->
                val outcome = speakerEngine.verifyAgainstEnrolled(profile.featureVectorCsv, candidate)
                sessionController.setSpeakerAuthStatus(outcome.status)
                _speakerFeedback.value = outcome.message
            }.onFailure {
                _speakerFeedback.value = it.localizedMessage ?: "Verification audio error"
            }
        }
    }

    fun resetOwnerVoiceProfile() {
        viewModelScope.launch {
            enrollmentBuffers.clear()
            _enrollmentStep.value = 0
            dao.clearVoiceProfile()
            sessionController.setSpeakerAuthStatus(SpeakerAuthStatus.NOT_ENROLLED)
            _speakerFeedback.value = "Owner voiceprint cleared."
        }
    }

    // --- Code Studio ---
    fun selectCodeLanguage(lang: CodeLanguage) {
        _codeLanguage.value = lang
        _codeSource.value = when (lang) {
            CodeLanguage.HTML_JS -> DEFAULT_HTML_SAMPLE
            CodeLanguage.JAVASCRIPT -> DEFAULT_JS_SAMPLE
            CodeLanguage.MYRA_SCRIPT -> DEFAULT_SCRIPT_SAMPLE
        }
    }

    fun updateCodeSource(code: String) {
        _codeSource.value = code
    }

    fun generateAiCodeIntoStudio(request: String) {
        if (request.isBlank()) return
        viewModelScope.launch {
            appendCodeLog("▶ Asking Gemini 3.5 Flash-Lite to generate code...")
            try {
                val result = aiRepository.generateCode(request, _codeLanguage.value, settings.value.language)
                _codeSource.value = result.code
                appendCodeLog("[Gemini] Loaded ${result.title}. Tap Run to execute.")
            } catch (e: TimeoutCancellationException) {
                Log.e("MyraViewModel", "Code generation timed out", e)
                val msg = "Code generation timed out after 60s."
                appendCodeLog("✖ Error: $msg")
                sessionController.setToast(msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MyraViewModel", "Code generation failed", e)
                val msg = e.localizedMessage ?: "Gemini code generation failed."
                appendCodeLog("✖ Error: $msg")
                sessionController.setToast(msg)
            }
        }
    }

    fun runCurrentCode() {
        when (_codeLanguage.value) {
            CodeLanguage.MYRA_SCRIPT -> {
                val res = MyraScriptInterpreter.execute(_codeSource.value)
                val out = buildList {
                    add("▶ MYRA Script ran in ${res.elapsedMs}ms:")
                    addAll(res.stdout)
                    if (res.error != null) add("✖ Error: ${res.error}")
                }
                _codeLogs.value = out
            }
            CodeLanguage.HTML_JS, CodeLanguage.JAVASCRIPT -> {
                _codeLogs.value = listOf("▶ Running in WebView Sandbox...")
                _codeRunTrigger.value += 1
            }
        }
    }

    fun appendCodeLog(line: String) {
        _codeLogs.value = (_codeLogs.value + line).takeLast(60)
    }

    // --- Tasks, Reminders & Memory ---
    fun addTaskOrReminder(title: String, notes: String, reminderDelayMinutes: Int?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val due = reminderDelayMinutes?.let { System.currentTimeMillis() + it * 60_000L }
            val id = dao.insertTask(
                TaskReminderEntity(
                    title = title.trim(),
                    notes = notes.trim(),
                    dueTimeMillis = due,
                    isReminder = due != null
                )
            )
            if (due != null) {
                ReminderScheduler.scheduleReminder(appContext, id, title.trim(), notes.trim(), due)
                sessionController.setToast("Reminder scheduled in $reminderDelayMinutes min")
            }
        }
    }

    fun toggleTaskComplete(task: TaskReminderEntity) {
        viewModelScope.launch { dao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { dao.deleteTask(id) }
    }

    fun addMemoryFact(fact: String) {
        if (fact.isBlank()) return
        viewModelScope.launch { dao.insertMemoryFact(MemoryFactEntity(fact = fact.trim())) }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch { dao.deleteMemoryFact(id) }
    }

    fun clearChatHistory() {
        viewModelScope.launch { dao.clearChatHistory() }
    }

    // --- Settings Mutations ---
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { settingsRepo.updateLanguage(lang) }
    fun setPersonaMode(mode: MyraPersonaMode) = viewModelScope.launch { settingsRepo.updatePersonaMode(mode) }
    fun setVoiceName(name: String) = viewModelScope.launch { settingsRepo.updateVoiceName(name) }
    fun setSpeechSpeed(v: Float) = viewModelScope.launch { settingsRepo.updateSpeechSpeed(v) }
    fun setSpeechPitch(v: Float) = viewModelScope.launch { settingsRepo.updateSpeechPitch(v) }
    fun setSpeechVolume(v: Float) = viewModelScope.launch { settingsRepo.updateSpeechVolume(v) }
    fun setWarmLaughter(v: Boolean) = viewModelScope.launch { settingsRepo.updateWarmLaughter(v) }
    fun setWakeWord(v: Boolean) = viewModelScope.launch { settingsRepo.updateWakeWord(v) }
    fun setCustomWakeWord(word: String) = viewModelScope.launch {
        settingsRepo.updateCustomWakeWord(word)
        sessionController.setToast("Wake word set to \"${word.trim().ifBlank { "MYRA" }}\"")
    }
    fun setStandbyWakeWordOnly(v: Boolean) = viewModelScope.launch { settingsRepo.updateStandbyWakeWordOnly(v) }
    fun setWakeWordSensitivity(v: Float) = viewModelScope.launch { settingsRepo.updateWakeWordSensitivity(v) }
    fun setMemoryConsent(v: Boolean) = viewModelScope.launch { settingsRepo.updateMemoryConsent(v) }
    fun setRequireVoiceAuth(v: Boolean) = viewModelScope.launch { settingsRepo.updateRequireVoiceAuth(v) }
    fun setBiometricLock(v: Boolean) = viewModelScope.launch {
        settingsRepo.updateBiometricLock(v)
        if (!v) _isSessionUnlocked.value = true
    }
    fun setBiometricOnAppLaunch(v: Boolean) = viewModelScope.launch { settingsRepo.updateBiometricOnAppLaunch(v) }
    fun setBiometricForSensitiveActions(v: Boolean) = viewModelScope.launch { settingsRepo.updateBiometricForSensitiveActions(v) }
    fun markSessionUnlocked() { _isSessionUnlocked.value = true }
    fun lockAppNow() { _isSessionUnlocked.value = false }
    fun showMessage(message: String) = sessionController.setToast(message)
    fun setThemeAccent(a: ThemeAccent) = viewModelScope.launch { settingsRepo.updateThemeAccent(a) }
    fun savePin(pin: String) = viewModelScope.launch { settingsRepo.setPin(pin) }
    fun clearPin() = viewModelScope.launch { settingsRepo.clearPin() }
    fun verifyPin(pin: String): Boolean = settingsRepo.verifyPin(pin, settings.value.pinHash)
    fun saveApiConfigNote(modelId: String, note: String) = viewModelScope.launch {
        settingsRepo.updateModelAndApiNote(modelId, note)
        sessionController.setToast("AI configuration preference saved.")
    }

    fun clearToast() = sessionController.setToast(null)

    // Direct device actions
    fun launchApp(packageName: String) {
        if (!deviceController.launchPackage(packageName)) {
            sessionController.setToast("Unable to launch app")
        }
    }
    fun openCamera() = deviceController.openCamera()
    fun openSettings() = deviceController.openSettings()
    fun openWifi() = deviceController.openWifiSettings()
    fun openBluetooth() = deviceController.openBluetoothSettings()
    fun openWhatsApp() = deviceController.openWhatsApp()
    fun prepareSms() = deviceController.prepareSms()
    fun setQuickAlarm(hour: Int, minute: Int) = deviceController.setAlarm(hour, minute)
    fun launchNav(dest: String) = deviceController.launchNavigation(dest)
    fun toggleFlashlight() {
        deviceController.toggleFlashlight()
            .onSuccess { on -> sessionController.setToast(if (on) "Flashlight ON" else "Flashlight OFF") }
            .onFailure { sessionController.setToast(it.localizedMessage) }
    }
    fun pinHomeShortcut() {
        val ok = deviceController.pinVoiceHomeShortcut()
        sessionController.setToast(if (ok) "Home screen shortcut requested!" else "Launcher does not support pinned shortcuts.")
    }
    fun pinFullBodyHomeWidget() {
        val ok = deviceController.pinFullBodyHomeWidget()
        sessionController.setToast(
            if (ok) "MYRA Full-Body Widget pinned to Home Screen!"
            else "Long-press your phone Home Screen -> Widgets -> MYRA AI to add Full Body."
        )
    }
    fun isBatteryUnrestricted(): Boolean = deviceController.isIgnoringBatteryOptimizations()
    fun openBatterySettings() = deviceController.openBatteryOptimizationSettings()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                val controller = MyraSessionController.getInstance(appCtx)
                return MyraViewModel(
                    appContext = appCtx,
                    dao = controller.dao,
                    settingsRepo = controller.settingsRepo,
                    aiRepository = controller.aiRepository,
                    deviceController = controller.deviceController,
                    sessionController = controller
                ) as T
            }
        }

        private val DEFAULT_HTML_SAMPLE = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:20px;background:#070B1E;color:#F3F6FF;font-family:sans-serif;">
              <h2 style="color:#2EE6FF;margin:0 0 8px;">MYRA Live App Runner</h2>
              <p id="status">Tap the button to test live JS execution!</p>
              <button onclick="greet()" style="background:#B66DFF;color:#060A1A;border:none;padding:10px 18px;border-radius:10px;font-weight:bold;">
                Say Namaste
              </button>
              <script>
                let n = 0;
                function greet() {
                  n++;
                  document.getElementById('status').textContent = 'Namaste from MYRA! Click #' + n;
                  console.log('Clicked #' + n);
                }
                console.log('MYRA HTML/JS Sandbox initialized');
              </script>
            </body>
            </html>
        """.trimIndent()

        private val DEFAULT_JS_SAMPLE = """
            const greetings = ["नमस्ते", "Hello", "Namaste"];
            greetings.forEach((g, idx) => console.log(idx + 1, "->", g, "MYRA AI!"));
            const sum = [10, 20, 30, 40].reduce((a, b) => a + b, 0);
            console.log("Total sum:", sum);
        """.trimIndent()

        private val DEFAULT_SCRIPT_SAMPLE = """
            # MYRA Algorithm & Math Runner
            a = 12
            b = 16
            hyp = sqrt(a * a + b * b)
            print("Hypotenuse =", hyp)
            repeat i from 1 to 5 {
                print("Step", i, "cube =", i * i * i)
            }
        """.trimIndent()
    }
}
