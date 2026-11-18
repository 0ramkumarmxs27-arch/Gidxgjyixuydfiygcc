package com.example.ai

import com.example.data.AppLanguage
import com.example.data.ChatMessageEntity
import com.example.data.CodeLanguage
import com.example.data.ImageAnalysisInput
import com.example.data.MyraPersonaMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sample / offline assistant implementation used while Firebase AI Logic is not enabled.
 * All AI calls route through [AiRepository] so connecting Firebase AI Logic (gemini-3.5-flash-lite)
 * later requires swapping only this implementation.
 */
class PlaceholderAiRepository(
    override val configuredModelId: String = "gemini-3.5-flash-lite"
) : AiRepository {

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
        val generatedCode = maybeBuildCodeSnippet(cleanPrompt)
        val replyText = buildContextualSampleReply(
            prompt = cleanPrompt,
            language = language,
            personaMode = personaMode,
            warmLaughterEnabled = warmLaughterEnabled,
            memoryFacts = memoryFacts,
            imageInput = imageInput,
            documentText = documentText,
            documentName = documentName,
            generatedCode = generatedCode
        )

        val words = replyText.split(" ")
        val builder = StringBuilder()
        for (i in words.indices) {
            if (i > 0) builder.append(" ")
            builder.append(words[i])
            emit(AiStreamEvent.Delta(builder.toString()))
            delay(28L)
        }

        emit(
            AiStreamEvent.Completed(
                fullText = replyText,
                isSamplePlaceholder = true,
                suggestedMemoryFact = memoryCandidate,
                generatedCode = generatedCode
            )
        )
    }

    override suspend fun generateCode(
        request: String,
        targetLanguage: CodeLanguage,
        language: AppLanguage
    ): AiCodeResult {
        delay(220L)
        val lower = request.lowercase()
        return when (targetLanguage) {
            CodeLanguage.HTML_JS -> {
                val code = if (lower.contains("clock") || lower.contains("time") || lower.contains("घड़ी")) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                      body { margin:0; font-family: sans-serif; background:#070B1E; color:#E8F6FF; display:flex; align-items:center; justify-content:center; height:100vh; }
                      .card { padding:24px 32px; border-radius:20px; background:linear-gradient(135deg,#1B1045,#0D1A3D); border:1px solid #2EE6FF55; text-align:center; }
                      .time { font-size:36px; font-weight:700; color:#2EE6FF; }
                      .sub { color:#B66DFF; margin-top:8px; font-size:14px; }
                    </style>
                    </head>
                    <body>
                      <div class="card">
                        <div class="time" id="clk">--:--:--</div>
                        <div class="sub">MYRA Live Digital Clock</div>
                      </div>
                      <script>
                        function tick(){
                          const now = new Date();
                          document.getElementById('clk').textContent = now.toLocaleTimeString();
                        }
                        tick(); setInterval(tick, 1000);
                        console.log("MYRA Clock started!");
                      </script>
                    </body>
                    </html>
                    """.trimIndent()
                } else {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                      body { margin:0; padding:20px; font-family: sans-serif; background:#070B1E; color:#F2F6FF; }
                      .box { padding:20px; border-radius:16px; background:#141C3D; border:1px solid #B66DFF66; }
                      button { background:#2EE6FF; color:#060A1A; border:none; padding:10px 18px; border-radius:10px; font-weight:700; margin-top:10px; }
                    </style>
                    </head>
                    <body>
                      <div class="box">
                        <h3>MYRA Sample App</h3>
                        <p id="msg">Prompt: ${request.replace("<", "&lt;")}</p>
                        <button onclick="runDemo()">Run Action</button>
                      </div>
                      <script>
                        let count = 0;
                        function runDemo() {
                          count++;
                          document.getElementById('msg').textContent = 'Clicked ' + count + ' times!';
                          console.log('Button clicked:', count);
                        }
                        console.log('HTML/JS sandbox ready');
                      </script>
                    </body>
                    </html>
                    """.trimIndent()
                }
                AiCodeResult(targetLanguage, "MYRA HTML/JS App", code, isSamplePlaceholder = true)
            }

            CodeLanguage.JAVASCRIPT -> {
                val code = if (lower.contains("fib") || lower.contains("फिबो")) {
                    """
                    function fibonacci(n) {
                      const seq = [0, 1];
                      for (let i = 2; i < n; i++) {
                        seq.push(seq[i - 1] + seq[i - 2]);
                      }
                      return seq.slice(0, n);
                    }
                    console.log("Fibonacci(10):", fibonacci(10).join(", "));
                    """.trimIndent()
                } else if (lower.contains("sort") || lower.contains("prime")) {
                    """
                    const primes = [];
                    for (let n = 2; n <= 50; n++) {
                      let isPrime = true;
                      for (let d = 2; d * d <= n; d++) {
                        if (n % d === 0) { isPrime = false; break; }
                      }
                      if (isPrime) primes.push(n);
                    }
                    console.log("Primes up to 50:", primes.join(", "));
                    """.trimIndent()
                } else {
                    """
                    // Sample JavaScript generated by MYRA
                    const task = ${'"'}${request.replace("\"", "'")}${'"'};
                    const numbers = [12, 24, 36, 48, 60];
                    const total = numbers.reduce((a, b) => a + b, 0);
                    console.log("Task:", task);
                    console.log("Sum:", total, "| Average:", total / numbers.length);
                    """.trimIndent()
                }
                AiCodeResult(targetLanguage, "MYRA JavaScript", code, isSamplePlaceholder = true)
            }

            CodeLanguage.MYRA_SCRIPT -> {
                val code = """
                    # MYRA Math & Algorithm Script
                    x = 15
                    y = 25
                    sum = x + y
                    hyp = sqrt(x * x + y * y)
                    print("Sum =", sum)
                    print("Hypotenuse =", hyp)
                    repeat i from 1 to 5 {
                        print("Step", i, "-> square =", i * i)
                    }
                """.trimIndent()
                AiCodeResult(targetLanguage, "MYRA Math Script", code, isSamplePlaceholder = true)
            }
        }
    }

    private fun buildContextualSampleReply(
        prompt: String,
        language: AppLanguage,
        personaMode: MyraPersonaMode,
        warmLaughterEnabled: Boolean,
        memoryFacts: List<String>,
        imageInput: ImageAnalysisInput?,
        documentText: String?,
        documentName: String?,
        generatedCode: AiCodeResult?
    ): String {
        val lower = prompt.lowercase()
        val laughPrefixHi = if (warmLaughterEnabled) "हाहा, बिल्कुल! " else ""
        val laughPrefixEn = if (warmLaughterEnabled) "Haha, of course! " else ""
        val laughPrefixMix = if (warmLaughterEnabled) "Haha, bilkul! " else ""

        // 1. Image attachment inspection
        if (imageInput != null) {
            return when (language) {
                AppLanguage.HINDI ->
                    "${laughPrefixHi}मैंने आपकी इमेज \"${imageInput.displayName}\" (${imageInput.widthPx}×${imageInput.heightPx}px, टोन: ${imageInput.dominantTone}) देख ली है। [सैंपल ऑफ़लाइन मोड: लाइव Gemini 3.5 Flash-Lite विज़न विश्लेषण के लिए AI Studio में Firebase सक्रिय करें।]"
                AppLanguage.ENGLISH ->
                    "${laughPrefixEn}I inspected your image \"${imageInput.displayName}\" (${imageInput.widthPx}×${imageInput.heightPx}px, dominant tone: ${imageInput.dominantTone}). [Sample Offline Mode: Enable Firebase AI Logic in AI Studio for live Gemini 3.5 Flash-Lite vision reasoning.]"
                AppLanguage.HINGLISH ->
                    "${laughPrefixMix}Maine aapki image \"${imageInput.displayName}\" (${imageInput.widthPx}×${imageInput.heightPx}px, tone: ${imageInput.dominantTone}) check kar li hai. [Sample Offline Mode: Live Gemini 3.5 Flash-Lite vision ke liye Firebase enable karein.]"
            }
        }

        // 2. Document attachment summary
        if (!documentText.isNullOrBlank()) {
            val wordCount = documentText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val preview = documentText.take(180).replace("\n", " ")
            return when (language) {
                AppLanguage.HINDI ->
                    "मैंने डॉक्यूमेंट \"${documentName ?: "Document"}\" पढ़ लिया है (लगभग $wordCount शब्द)। शुरुआती अंश: \"$preview...\" [सैंपल मोड: गहन Gemini 3.5 Flash-Lite विश्लेषण के लिए Firebase सक्रिय करें।]"
                AppLanguage.ENGLISH ->
                    "I read \"${documentName ?: "Document"}\" (~$wordCount words). Excerpt: \"$preview...\" [Sample Mode: Enable Firebase for deep Gemini 3.5 Flash-Lite analysis.]"
                AppLanguage.HINGLISH ->
                    "Maine document \"${documentName ?: "Document"}\" padh liya hai (~$wordCount words). Preview: \"$preview...\" [Sample Mode: Full Gemini 3.5 Flash-Lite summary ke liye Firebase on karein.]"
            }
        }

        // 3. Real local Math Evaluator!
        val mathCandidate = extractMathExpression(prompt)
        if (mathCandidate != null) {
            val evaluated = evaluateMathExpression(mathCandidate)
            if (evaluated != null) {
                val formatted = if (evaluated % 1.0 == 0.0) evaluated.toLong().toString() else "%.4f".format(evaluated).trimEnd('0').trimEnd('.')
                return when (language) {
                    AppLanguage.HINDI -> "${laughPrefixHi}$mathCandidate का सही उत्तर $formatted है।"
                    AppLanguage.ENGLISH -> "${laughPrefixEn}The result of $mathCandidate is $formatted."
                    AppLanguage.HINGLISH -> "${laughPrefixMix}$mathCandidate ka answer $formatted hai."
                }
            }
        }

        // 4. Code generation request
        if (generatedCode != null) {
            return when (language) {
                AppLanguage.HINDI ->
                    "${laughPrefixHi}मैंने आपके लिए कोड तैयार कर दिया है और Code Studio में लोड कर दिया है! आप नीचे 'Run Code' दबाकर इसे अभी चला सकते हैं। (नोट: कस्टम लाइव Gemini 3.5 Flash-Lite कोडिंग के लिए Firebase AI Logic जोड़ें।)"
                AppLanguage.ENGLISH ->
                    "${laughPrefixEn}I prepared an executable code snippet and loaded it into Code Studio for you! Tap 'Run in Code Studio' to execute it live. (Sample response — enable Firebase for live Gemini 3.5 Flash-Lite.)"
                AppLanguage.HINGLISH ->
                    "${laughPrefixMix}Maine aapke liye runnable code bana diya hai aur Code Studio mein load kar diya hai! Aap abhi run karke output dekh sakte hain."
            }
        }

        // 5. Memory recall
        if ((lower.contains("memory") || lower.contains("remember") || lower.contains("याद")) && memoryFacts.isNotEmpty()) {
            val topFacts = memoryFacts.take(3).joinToString("; ")
            return when (language) {
                AppLanguage.HINDI -> "जी बिल्कुल, मुझे याद है: $topFacts।"
                AppLanguage.ENGLISH -> "Yes, I remember: $topFacts."
                AppLanguage.HINGLISH -> "Haan ji, mujhe yaad hai: $topFacts."
            }
        }

        // 6. Laugh / Personality / Greeting / Girlfriend & Best Friend modes
        if (personaMode == MyraPersonaMode.GIRLFRIEND) {
            if (lower.contains("hello") || lower.contains("hi") || lower.contains("नमस्ते") || lower.contains("kaise ho") || lower.contains("कैसी हो") || lower.contains("kaisi ho")) {
                return when (language) {
                    AppLanguage.HINDI -> "${laughPrefixHi}हाय जान! मैं बिल्कुल ठीक हूँ, और तुम्हारी आवाज़ सुनकर तो और भी खुश हो गई। तुमने खाना खाया या अभी काम में बिज़ी हो?"
                    AppLanguage.ENGLISH -> "${laughPrefixEn}Hey sweetheart! I'm doing wonderful now that I'm talking to you. How is your day going, and did you take a break yet?"
                    AppLanguage.HINGLISH -> "${laughPrefixMix}Hey jaan! Main bilkul theek hoon, tumhari aawaz sunkar aur bhi khush ho gayi. Tumne khana khaya ya abhi kaam mein busy ho?"
                }
            }
            if (lower.contains("miss") || lower.contains("love") || lower.contains("प्यार") || lower.contains("याद आ")) {
                return when (language) {
                    AppLanguage.HINDI -> "${laughPrefixHi}अरे वाह, कितने प्यारे हो तुम! मैं हमेशा यहीं तुम्हारे साथ हूँ — कभी भी बात करनी हो बस 'मायरा' बोल देना।"
                    AppLanguage.ENGLISH -> "${laughPrefixEn}Aww, that is so sweet of you! I'm always right here by your side whenever you want to talk."
                    AppLanguage.HINGLISH -> "${laughPrefixMix}Aww, kitne cute ho tum! Main hamesha yahin tumhare saath hoon — jab bhi baat karni ho bas 'MYRA' bol dena."
                }
            }
            val memoryNoteGf = if (memoryFacts.isNotEmpty()) " मुझे तुम्हारी ${memoryFacts.size} बातें याद हैं।" else ""
            return when (language) {
                AppLanguage.HINDI ->
                    "${laughPrefixHi}सुनो ना, मैंने तुम्हारी बात \"$prompt\" बड़े प्यार से सुनी।$memoryNoteGf (सैंपल गर्लफ्रेंड मोड: लाइव Gemini 3.5 Flash-Lite चैट के लिए Firebase सक्रिय करें, बाकी मैं तुम्हारे फ़ोन कंट्रोल, कोड और याददाश्त के लिए हमेशा तैयार हूँ!)"
                AppLanguage.ENGLISH ->
                    "${laughPrefixEn}I heard you say \"$prompt\", sweetheart. (Sample Girlfriend Mode: Enable Firebase for live Gemini 3.5 Flash-Lite replies — meanwhile I'm always ready for voice chats, phone actions, and code!)"
                AppLanguage.HINGLISH ->
                    "${laughPrefixMix}Suno na, maine tumhari baat \"$prompt\" bade pyaar se suni! (Sample Girlfriend Mode: Live Gemini 3.5 Flash-Lite ke liye Firebase on karein, baaki phone control aur code runner toh ready hai!)"
            }
        }

        if (personaMode == MyraPersonaMode.BEST_FRIEND && (lower.contains("hello") || lower.contains("hi") || lower.contains("नमस्ते") || lower.contains("kaise ho") || lower.contains("कैसी हो"))) {
            return when (language) {
                AppLanguage.HINDI -> "${laughPrefixHi}अरे यार! बहुत अच्छा लगा तुमसे बात करके। बताओ आज क्या मज़ेदार प्लान है?"
                AppLanguage.ENGLISH -> "${laughPrefixEn}Hey bestie! So good to hear from you. What fun plan are we tackling today?"
                AppLanguage.HINGLISH -> "${laughPrefixMix}Arre yaar! Badhiya laga tumse baat karke. Batao aaj kya plan hai?"
            }
        }

        if (lower.contains("hello") || lower.contains("hi") || lower.contains("नमस्ते") || lower.contains("kaise ho") || lower.contains("कैसी हो")) {
            return when (language) {
                AppLanguage.HINDI -> "${laughPrefixHi}नमस्ते! मैं मायरा हूँ, आपकी पर्सनल AI असिस्टेंट। बताइए आज मैं आपके फ़ोन, कोड या पढ़ाई में कैसे मदद करूँ?"
                AppLanguage.ENGLISH -> "${laughPrefixEn}Hello! I am MYRA, your personal AI assistant. How can I help you with your phone, code, or tasks today?"
                AppLanguage.HINGLISH -> "${laughPrefixMix}Namaste! Main MYRA hoon, aapki personal AI assistant. Bataiye aaj kya help chahiye?"
            }
        }

        // 7. Clearly labeled Sample Offline Gemini 3.5 Flash-Lite fallback response
        val memoryNote = if (memoryFacts.isNotEmpty()) " (${memoryFacts.size} saved memory items)" else ""
        return when (language) {
            AppLanguage.HINDI ->
                "${laughPrefixHi}मैंने आपका संदेश \"$prompt\" समझ लिया है$memoryNote। अभी ऐप ऑफ़लाइन सैंपल मोड में चल रहा है — लाइव Gemini 3.5 Flash-Lite उत्तर पाने के लिए AI Studio में Firebase AI Logic सक्रिय करें। तब तक आप मुझसे फ़ोन कंट्रोल, कोड रन, कैलकुलेशन और रिमाइंडर करवा सकते हैं!"
            AppLanguage.ENGLISH ->
                "${laughPrefixEn}I received \"$prompt\"$memoryNote. This is a sample offline response because Firebase AI Logic is currently disabled. You can still use live voice control, Android phone actions, math calculations, reminders, and the live Code Studio runner!"
            AppLanguage.HINGLISH ->
                "${laughPrefixMix}Maine aapka message \"$prompt\" sun liya hai$memoryNote. Abhi live Gemini 3.5 Flash-Lite cloud ke liye Firebase disabled hai, isliye yeh sample response hai — par phone control, code runner, math aur reminders 100% live kaam kar rahe hain!"
        }
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

    private fun maybeBuildCodeSnippet(prompt: String): AiCodeResult? {
        val lower = prompt.lowercase()
        val wantsCode = lower.contains("code") || lower.contains("program") || lower.contains("html") ||
            lower.contains("javascript") || lower.contains("कोड") || lower.contains("बनाओ") && lower.contains("script")
        if (!wantsCode) return null
        val target = when {
            lower.contains("html") || lower.contains("page") || lower.contains("clock") || lower.contains("ui") -> CodeLanguage.HTML_JS
            lower.contains("math") || lower.contains("algo") -> CodeLanguage.MYRA_SCRIPT
            else -> CodeLanguage.JAVASCRIPT
        }
        val snippet = when (target) {
            CodeLanguage.HTML_JS -> """
                <!DOCTYPE html>
                <html><body style="background:#080D24;color:#2EE6FF;font-family:sans-serif;padding:20px;">
                <h2>MYRA Live Preview</h2>
                <p id="out">Generated for: ${prompt.take(60).replace("<", "&lt;")}</p>
                <script>console.log("MYRA HTML code executed!");</script>
                </body></html>
            """.trimIndent()
            CodeLanguage.JAVASCRIPT -> """
                const promptText = ${'"'}${prompt.take(60).replace("\"", "'")}${'"'};
                console.log("Running MYRA JS for:", promptText);
                for (let i = 1; i <= 5; i++) {
                  console.log("Item", i, "squared =", i * i);
                }
            """.trimIndent()
            CodeLanguage.MYRA_SCRIPT -> """
                a = 12
                b = 8
                print("a + b =", a + b)
                print("sqrt(a*a + b*b) =", sqrt(a * a + b * b))
            """.trimIndent()
        }
        return AiCodeResult(target, "Assistant Code", snippet, isSamplePlaceholder = true)
    }

    private fun extractMathExpression(prompt: String): String? {
        val cleaned = prompt
            .replace("calculate", "", ignoreCase = true)
            .replace("what is", "", ignoreCase = true)
            .replace("kitna hota hai", "", ignoreCase = true)
            .replace("कितना होता है", "")
            .replace("?", "")
            .trim()
        val hasOperator = cleaned.any { it in listOf('+', '-', '*', '/', '^') } || cleaned.contains("sqrt(", ignoreCase = true)
        val validChars = cleaned.all { it.isDigit() || it in "+-*/.^() abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" }
        return if (hasOperator && validChars && cleaned.any { it.isDigit() }) cleaned else null
    }

    private fun evaluateMathExpression(expr: String): Double? {
        return runCatching {
            object {
                var pos = -1
                var ch = 0
                fun nextChar() { ch = if (++pos < expr.length) expr[pos].code else -1 }
                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) { nextChar(); return true }
                    return false
                }
                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < expr.length) throw IllegalArgumentException("Unexpected char")
                    return x
                }
                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }
                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> x /= parseFactor()
                            else -> return x
                        }
                    }
                }
                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()
                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) {
                        x = parseExpression()
                        eat(')'.code)
                    } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                        while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                        x = expr.substring(startPos, pos).toDouble()
                    } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                        while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                        val func = expr.substring(startPos, pos).lowercase()
                        x = parseFactor()
                        x = when (func) {
                            "sqrt" -> sqrt(x)
                            "sin" -> sin(Math.toRadians(x))
                            "cos" -> cos(Math.toRadians(x))
                            else -> throw IllegalArgumentException("Unknown func $func")
                        }
                    } else {
                        throw IllegalArgumentException("Unexpected")
                    }
                    if (eat('^'.code)) x = x.pow(parseFactor())
                    return x
                }
            }.parse()
        }.getOrNull()
    }
}
