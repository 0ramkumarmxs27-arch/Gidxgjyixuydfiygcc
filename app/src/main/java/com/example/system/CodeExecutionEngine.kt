package com.example.system

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ScriptExecutionResult(
    val stdout: List<String>,
    val error: String? = null,
    val elapsedMs: Long = 0L
)

/**
 * Pure-Kotlin sandboxed interpreter for MYRA_SCRIPT (variables, math formulas, loops, print)
 * so algorithms execute instantaneously with zero dynamic code loading.
 */
object MyraScriptInterpreter {

    fun execute(source: String): ScriptExecutionResult {
        val start = System.currentTimeMillis()
        val output = mutableListOf<String>()
        val vars = mutableMapOf<String, Double>()

        return try {
            val lines = source.lines()
            executeLines(lines, 0, lines.size, vars, output)
            ScriptExecutionResult(
                stdout = if (output.isEmpty()) listOf("(Program finished with no print output)") else output,
                elapsedMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            ScriptExecutionResult(
                stdout = output,
                error = e.message ?: "Execution error",
                elapsedMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun executeLines(
        lines: List<String>,
        startIdx: Int,
        endIdx: Int,
        vars: MutableMap<String, Double>,
        output: MutableList<String>
    ) {
        var i = startIdx
        while (i < endIdx) {
            val raw = lines[i].trim()
            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) {
                i++
                continue
            }

            if (raw.startsWith("repeat ")) {
                val header = raw.removeSuffix("{").trim()
                val match = Regex("""repeat\s+([a-zA-Z_]\w*)\s+from\s+(-?\d+)\s+to\s+(-?\d+)""").find(header)
                    ?: throw IllegalArgumentException("Invalid repeat syntax at line ${i + 1}")
                val varName = match.groupValues[1]
                val fromVal = match.groupValues[2].toInt()
                val toVal = match.groupValues[3].toInt().coerceAtMost(fromVal + 500)

                val blockEnd = findMatchingBrace(lines, i, endIdx)
                for (iter in fromVal..toVal) {
                    vars[varName] = iter.toDouble()
                    executeLines(lines, i + 1, blockEnd, vars, output)
                }
                i = blockEnd + 1
                continue
            }

            if (raw.startsWith("print(") && raw.endsWith(")")) {
                val inner = raw.substring(6, raw.length - 1)
                val parts = splitArgs(inner)
                val rendered = parts.joinToString(" ") { part ->
                    val trimmed = part.trim()
                    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                        (trimmed.startsWith("'") && trimmed.endsWith("'"))
                    ) {
                        trimmed.substring(1, trimmed.length - 1)
                    } else {
                        formatNumber(evalExpr(trimmed, vars))
                    }
                }
                output.add(rendered)
                i++
                continue
            }

            val assignMatch = Regex("""^([a-zA-Z_]\w*)\s*=\s*(.+)$""").find(raw)
            if (assignMatch != null) {
                val name = assignMatch.groupValues[1]
                val expr = assignMatch.groupValues[2]
                vars[name] = evalExpr(expr, vars)
                i++
                continue
            }

            // Standalone expression
            output.add(formatNumber(evalExpr(raw, vars)))
            i++
        }
    }

    private fun findMatchingBrace(lines: List<String>, startLine: Int, limit: Int): Int {
        var depth = 0
        for (idx in startLine until limit) {
            val line = lines[idx]
            depth += line.count { it == '{' }
            depth -= line.count { it == '}' }
            if (depth == 0 && idx > startLine) return idx
            if (depth <= 0 && line.contains("}")) return idx
        }
        throw IllegalArgumentException("Missing closing '}' for block starting at line ${startLine + 1}")
    }

    private fun splitArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = '"'
        for (c in raw) {
            if ((c == '"' || c == '\'') && !inQuotes) {
                inQuotes = true
                quoteChar = c
                current.append(c)
            } else if (c == quoteChar && inQuotes) {
                inQuotes = false
                current.append(c)
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0 && abs(value) < 1e12) {
            value.toLong().toString()
        } else {
            "%.4f".format(value).trimEnd('0').trimEnd('.')
        }
    }

    private fun evalExpr(expr: String, vars: Map<String, Double>): Double {
        return object {
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
                while (ch == ' '.code) nextChar()
                if (pos < expr.length) throw IllegalArgumentException("Unexpected syntax in '$expr'")
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
                        eat('%'.code) -> x %= parseFactor()
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
                } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch == '_'.code) {
                    while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch in '0'.code..'9'.code || ch == '_'.code) nextChar()
                    val ident = expr.substring(startPos, pos)
                    if (eat('('.code)) {
                        val arg = parseExpression()
                        eat(')'.code)
                        x = when (ident.lowercase()) {
                            "sqrt" -> sqrt(arg)
                            "abs" -> abs(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            else -> throw IllegalArgumentException("Unknown function '$ident'")
                        }
                    } else {
                        x = vars[ident] ?: throw IllegalArgumentException("Undefined variable '$ident'")
                    }
                } else {
                    throw IllegalArgumentException("Invalid token in '$expr'")
                }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }
}

object WebSandboxTemplates {
    fun wrapJavaScriptHtml(jsCode: String): String {
        val escaped = jsCode.replace("</script>", "<\\/script>")
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <style>
                body { margin:0; padding:16px; background:#070B1E; color:#E8F6FF; font-family: monospace; font-size:13px; }
                .badge { color:#2EE6FF; font-weight:bold; margin-bottom:8px; }
              </style>
            </head>
            <body>
              <div class="badge">MYRA JS Runtime</div>
              <div id="app"></div>
              <script>
                (function() {
                  const origLog = console.log;
                  const origErr = console.error;
                  console.log = function(...args) {
                    if (window.MyraSandbox) window.MyraSandbox.onLog(args.map(String).join(" "));
                    origLog.apply(console, args);
                  };
                  console.error = function(...args) {
                    if (window.MyraSandbox) window.MyraSandbox.onError(args.map(String).join(" "));
                    origErr.apply(console, args);
                  };
                  try {
                    $escaped
                  } catch (e) {
                    console.error(e.toString());
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun injectConsoleBridge(htmlCode: String): String {
        val bridge = """
            <script>
              (function() {
                const origLog = console.log;
                const origErr = console.error;
                console.log = function(...args) {
                  if (window.MyraSandbox) window.MyraSandbox.onLog(args.map(String).join(" "));
                  origLog.apply(console, args);
                };
                console.error = function(...args) {
                  if (window.MyraSandbox) window.MyraSandbox.onError(args.map(String).join(" "));
                  origErr.apply(console, args);
                };
                window.onerror = function(msg) {
                  if (window.MyraSandbox) window.MyraSandbox.onError(String(msg));
                };
              })();
            </script>
        """.trimIndent()
        return if (htmlCode.contains("<head>", ignoreCase = true)) {
            htmlCode.replaceFirst("<head>", "<head>$bridge", ignoreCase = true)
        } else {
            bridge + htmlCode
        }
    }
}
