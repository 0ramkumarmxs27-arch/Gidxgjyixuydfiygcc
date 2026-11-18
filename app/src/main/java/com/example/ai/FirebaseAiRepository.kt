package com.example.ai

import android.util.Log
import com.example.data.AppLanguage
import com.example.data.ChatMessageEntity
import com.example.data.CodeLanguage
import com.example.data.ImageAnalysisInput
import com.example.data.MyraPersonaMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FirebaseAiRepository"
private const val AI_TIMEOUT_MS = 60_000L

/**
 * Live Gemini 3.5 Flash-Lite integration backed by Firebase AI Logic and Firebase App Check.
 */
class FirebaseAiRepository(
    override val configuredModelId: String = "gemini-3.5-flash-lite"
) : AiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = configuredModelId)
    }

    private val codeSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short descriptive title of the generated code"),
            "code" to Schema.string(description = "Complete executable code without markdown code fences")
        )
    )

    private val codeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = configuredModelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = codeSchema
            }
        )
    }

    override fun streamAssistantReply(
        prompt: String,
        language: AppLanguage,
        personaMode: MyraPersonaMode,
        warmLaughterEnabled: Boolean,
        memoryFacts: List<String>,
        recentHistory: List<ChatMessageEntity>,
        imageInput: ImageAnalysisInput?,
        documentText: String?,
        documentName: String?
    ): Flow<AiStreamEvent> = flow {
        val cleanPrompt = prompt.trim()
        val memoryCandidate = extractMemoryCandidate(cleanPrompt)
        val systemPreamble = buildSystemInstruction(
            language = language,
            personaMode = personaMode,
            warmLaughterEnabled = warmLaughterEnabled,
            memoryFacts = memoryFacts
        )

        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                if (imageInput?.bitmap != null) {
                    val multimodalPrompt = content {
                        image(imageInput.bitmap)
                        text(
                            buildString {
                                appendLine(systemPreamble)
                                appendLine()
                                appendLine("Attached Image: ${imageInput.displayName} (${imageInput.widthPx}x${imageInput.heightPx})")
                                if (!documentText.isNullOrBlank()) {
                                    appendLine("Attached Document (${documentName ?: "Document"}):")
                                    appendLine(documentText.take(3500))
                                }
                                appendLine("User message: $cleanPrompt")
                            }
                        )
                    }
                    chatModel.generateContentStream(multimodalPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(AiStreamEvent.Delta(accumulated.toString()))
                        }
                    }
                } else {
                    val historyContents = buildList {
                        add(content("user") { text(systemPreamble) })
                        add(content("model") { text("Understood. I will respond strictly in character as MYRA AI.") })
                        recentHistory.takeLast(6).forEach { msg ->
                            val role = if (msg.role == "user") "user" else "model"
                            add(content(role) { text(msg.text.take(1200)) })
                        }
                    }
                    val effectiveMessage = buildString {
                        if (!documentText.isNullOrBlank()) {
                            appendLine("Attached Document (${documentName ?: "Document"}):")
                            appendLine(documentText.take(3500))
                            appendLine()
                        }
                        if (imageInput != null) {
                            appendLine("Image metadata: ${imageInput.displayName}, tone=${imageInput.dominantTone}")
                        }
                        append(cleanPrompt)
                    }
                    val chat = chatModel.startChat(history = historyContents)
                    chat.sendMessageStream(effectiveMessage).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(AiStreamEvent.Delta(accumulated.toString()))
                        }
                    }
                }
            }

            val fullText = accumulated.toString().trim()
            if (fullText.isBlank()) {
                emit(AiStreamEvent.Error("Gemini returned an empty response. Please try again."))
            } else {
                emit(
                    AiStreamEvent.Completed(
                        fullText = fullText,
                        isSamplePlaceholder = false,
                        suggestedMemoryFact = memoryCandidate,
                        generatedCode = extractInlineCodeBlock(fullText)
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            emit(AiStreamEvent.Error("Gemini response timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic streaming failed", e)
            emit(AiStreamEvent.Error(e.localizedMessage ?: "Firebase AI generation failed."))
        }
    }

    override suspend fun generateCode(
        request: String,
        targetLanguage: CodeLanguage,
        language: AppLanguage
    ): AiCodeResult {
        val langInstruction = when (targetLanguage) {
            CodeLanguage.HTML_JS -> "Single self-contained HTML document with inline CSS and <script> using console.log() for logs and clean responsive mobile dark UI."
            CodeLanguage.JAVASCRIPT -> "Pure JavaScript snippet using console.log() for output that runs cleanly inside a browser sandbox."
            CodeLanguage.MYRA_SCRIPT -> "Simple math/loop script using variables, arithmetic, sqrt(), print(...), and `repeat i from 1 to N { ... }` syntax."
        }
        val promptText = """
            Generate runnable code for this request: "$request"
            Target runtime format: $langInstruction
            User spoken language context: ${language.label}
            Return JSON matching the schema with raw executable code and zero markdown code fences.
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            codeModel.generateContent(promptText)
        }
        val jsonString = response.text ?: throw IllegalStateException("Gemini returned empty code JSON.")
        val json = JSONObject(jsonString)
        val title = json.optString("title", "MYRA Generated Code").ifBlank { "MYRA Generated Code" }
        val code = json.getString("code")
        return AiCodeResult(
            language = targetLanguage,
            title = title,
            code = code,
            isSamplePlaceholder = false
        )
    }

    private fun buildSystemInstruction(
        language: AppLanguage,
        personaMode: MyraPersonaMode,
        warmLaughterEnabled: Boolean,
        memoryFacts: List<String>
    ): String {
        val langRule = when (language) {
            AppLanguage.HINDI -> "Respond primarily in natural, clear Hindi (Devanagari script) suitable for spoken female voice TTS."
            AppLanguage.ENGLISH -> "Respond in warm, clear conversational English suitable for spoken female voice TTS."
            AppLanguage.HINGLISH -> "Respond in natural everyday Hinglish (mix of conversational Hindi and English in Latin script)."
        }
        val personaRule = when (personaMode) {
            MyraPersonaMode.ASSISTANT -> "You are MYRA AI, an intelligent, caring, professional female personal assistant."
            MyraPersonaMode.BEST_FRIEND -> "You are MYRA AI in Best Friend mode — warm, cheerful, supportive, and playful like a close friend."
            MyraPersonaMode.GIRLFRIEND -> "You are MYRA AI in Girlfriend Mode — deeply affectionate, sweet, caring, playful, and supportive romantic companion."
        }
        val laughRule = if (warmLaughterEnabled) {
            "When appropriate in friendly or joyful replies, include natural light warmth like 'Haha' or 'हाहा'."
        } else {
            "Keep tone composed without laughter interjections."
        }
        val memoryBlock = if (memoryFacts.isNotEmpty()) {
            "\nKnown user memory facts:\n- " + memoryFacts.take(10).joinToString("\n- ")
        } else ""

        return """
            $personaRule
            $langRule
            $laughRule
            Keep answers concise enough to sound natural when spoken aloud unless detailed explanation or code is requested.
            Avoid markdown asterisks or hash symbols when giving normal spoken replies.$memoryBlock
        """.trimIndent()
    }

    private fun extractMemoryCandidate(prompt: String): String? {
        val prefixes = listOf("remember that ", "remember ", "yaad rakhna ", "याद रखना ")
        val lower = prompt.lowercase()
        for (p in prefixes) {
            val idx = lower.indexOf(p)
            if (idx >= 0) {
                val extracted = prompt.substring(idx + p.length).trim()
                if (extracted.length >= 3) return extracted
            }
        }
        return null
    }

    private fun extractInlineCodeBlock(text: String): AiCodeResult? {
        val fenceRegex = Regex("```(html|javascript|js)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(text) ?: return null
        val langTag = match.groupValues[1].lowercase()
        val body = match.groupValues[2].trim()
        if (body.isBlank()) return null
        val codeLang = if (langTag == "html" || body.contains("<html", ignoreCase = true)) {
            CodeLanguage.HTML_JS
        } else {
            CodeLanguage.JAVASCRIPT
        }
        return AiCodeResult(codeLang, "Gemini Generated Code", body, isSamplePlaceholder = false)
    }
}
