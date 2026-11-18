package com.example.ai

import com.example.data.AppLanguage
import com.example.data.ChatMessageEntity
import com.example.data.CodeLanguage
import com.example.data.ImageAnalysisInput
import com.example.data.MyraPersonaMode
import kotlinx.coroutines.flow.Flow

sealed interface AiStreamEvent {
    data class Delta(val accumulatedText: String) : AiStreamEvent
    data class Completed(
        val fullText: String,
        val isSamplePlaceholder: Boolean,
        val suggestedMemoryFact: String? = null,
        val generatedCode: AiCodeResult? = null
    ) : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

data class AiCodeResult(
    val language: CodeLanguage,
    val title: String,
    val code: String,
    val isSamplePlaceholder: Boolean = true
)

interface AiRepository {
    val configuredModelId: String

    fun streamAssistantReply(
        prompt: String,
        language: AppLanguage,
        personaMode: MyraPersonaMode,
        warmLaughterEnabled: Boolean,
        memoryFacts: List<String>,
        recentHistory: List<ChatMessageEntity>,
        imageInput: ImageAnalysisInput?,
        documentText: String?,
        documentName: String?
    ): Flow<AiStreamEvent>

    suspend fun generateCode(
        request: String,
        targetLanguage: CodeLanguage,
        language: AppLanguage
    ): AiCodeResult
}
