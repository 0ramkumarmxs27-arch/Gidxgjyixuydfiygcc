package com.example

import com.example.ai.AiStreamEvent
import com.example.ai.PlaceholderAiRepository
import com.example.data.AppLanguage
import com.example.data.CodeLanguage
import com.example.data.MyraPersonaMode
import com.example.system.MyraScriptInterpreter
import com.example.voice.WakeWordDetector
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyraLogicUnitTest {

    @Test
    fun scriptInterpreter_executesLoopAndMath() {
        val code = """
            a = 3
            b = 4
            c = sqrt(a * a + b * b)
            print("Hyp =", c)
            repeat i from 1 to 3 {
                print("Step", i)
            }
        """.trimIndent()
        val res = MyraScriptInterpreter.execute(code)
        assertNull(res.error)
        assertEquals(listOf("Hyp = 5", "Step 1", "Step 2", "Step 3"), res.stdout)
    }

    @Test
    fun placeholderAi_evaluatesMathAndMarksSampleMode() = runBlocking {
        val repo = PlaceholderAiRepository()
        val event = repo.streamAssistantReply(
            prompt = "25 * 48 + 120",
            language = AppLanguage.HINDI,
            personaMode = MyraPersonaMode.ASSISTANT,
            warmLaughterEnabled = true,
            memoryFacts = emptyList(),
            recentHistory = emptyList(),
            imageInput = null,
            documentText = null,
            documentName = null
        ).last() as AiStreamEvent.Completed

        assertTrue(event.isSamplePlaceholder)
        assertTrue(event.fullText.contains("1320"))
    }

    @Test
    fun placeholderAi_girlfriendMode_respondsAffectionately() = runBlocking {
        val repo = PlaceholderAiRepository()
        val event = repo.streamAssistantReply(
            prompt = "Hello kaise ho?",
            language = AppLanguage.HINGLISH,
            personaMode = MyraPersonaMode.GIRLFRIEND,
            warmLaughterEnabled = true,
            memoryFacts = emptyList(),
            recentHistory = emptyList(),
            imageInput = null,
            documentText = null,
            documentName = null
        ).last() as AiStreamEvent.Completed

        assertTrue(event.isSamplePlaceholder)
        assertTrue(event.fullText.contains("jaan", ignoreCase = true))
    }

    @Test
    fun placeholderAi_generatesExecutableCode() = runBlocking {
        val repo = PlaceholderAiRepository()
        val snippet = repo.generateCode("Digital clock", CodeLanguage.HTML_JS, AppLanguage.ENGLISH)
        assertTrue(snippet.code.contains("<script>"))
        assertTrue(snippet.isSamplePlaceholder)
    }

    @Test
    fun wakeWordDetector_detectsCustomWakeWordAndExtractsInlineCommand() {
        val inline = WakeWordDetector.evaluate(
            transcript = "Hey Nova open WhatsApp now",
            customWakeWord = "Nova",
            sensitivity = 0.8f
        )
        assertTrue(inline.matched)
        assertFalse(inline.isStopCommand)
        assertEquals("open WhatsApp now", inline.remainingCommand)

        val hindiWake = WakeWordDetector.evaluate(
            transcript = "मायरा",
            customWakeWord = "MYRA",
            sensitivity = 0.8f
        )
        assertTrue(hindiWake.matched)
        assertEquals("", hindiWake.remainingCommand)

        val fuzzyPhonetic = WakeWordDetector.evaluate(
            transcript = "maira check battery",
            customWakeWord = "MYRA",
            sensitivity = 0.75f
        )
        assertTrue(fuzzyPhonetic.matched)
        assertEquals("check battery", fuzzyPhonetic.remainingCommand)
    }

    @Test
    fun wakeWordDetector_ignoresUnrelatedChatterAndDetectsStop() {
        val chatter = WakeWordDetector.evaluate(
            transcript = "what is the weather outside today",
            customWakeWord = "MYRA",
            sensitivity = 0.8f
        )
        assertFalse(chatter.matched)

        val off = WakeWordDetector.evaluate(
            transcript = "turn off myra",
            customWakeWord = "MYRA",
            sensitivity = 0.8f
        )
        assertTrue(off.matched)
        assertTrue(off.isStopCommand)
        assertTrue(off.isPermanentOffCommand)
    }
}
