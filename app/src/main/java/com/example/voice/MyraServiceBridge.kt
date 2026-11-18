package com.example.voice

import com.example.data.AssistantState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceServiceCommand {
    GO_LIVE_FOREVER,
    START_WAKE_STANDBY,
    START_LISTENING,
    STOP_INTERACTION,
    TURN_OFF_SERVICE
}

object MyraServiceBridge {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning = _serviceRunning.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState = _assistantState.asStateFlow()

    private val _micRms = MutableStateFlow(0f)
    val micRms = _micRms.asStateFlow()

    private val _mouthOpenness = MutableStateFlow(0f)
    val mouthOpenness = _mouthOpenness.asStateFlow()

    private val _statusSubtitle = MutableStateFlow("Ready • Tap mic or say MYRA")
    val statusSubtitle = _statusSubtitle.asStateFlow()

    private val _lastWakeEvent = MutableStateFlow<String?>(null)
    val lastWakeEvent = _lastWakeEvent.asStateFlow()

    private val _transcriptFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val transcriptFlow = _transcriptFlow.asSharedFlow()

    private val _commandFlow = MutableSharedFlow<VoiceServiceCommand>(extraBufferCapacity = 16)
    val commandFlow = _commandFlow.asSharedFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setAssistantState(state: AssistantState, subtitle: String? = null) {
        _assistantState.value = state
        if (subtitle != null) _statusSubtitle.value = subtitle
    }

    fun setMicRms(rms: Float) {
        _micRms.value = rms.coerceIn(0f, 1f)
    }

    fun setMouthOpenness(openness: Float) {
        _mouthOpenness.value = openness.coerceIn(0f, 1f)
    }

    fun recordWakeDetected(phrase: String) {
        _lastWakeEvent.value = phrase
    }

    fun emitTranscript(text: String) {
        _transcriptFlow.tryEmit(text)
    }

    fun sendCommand(command: VoiceServiceCommand) {
        _commandFlow.tryEmit(command)
    }
}
