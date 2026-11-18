package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CodeLanguage
import com.example.system.WebSandboxTemplates
import com.example.ui.MyraViewModel
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.GlassCard
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private class MyraJsConsoleBridge(
    private val onLogLine: (String) -> Unit
) {
    @JavascriptInterface
    fun onLog(msg: String) {
        onLogLine("› $msg")
    }

    @JavascriptInterface
    fun onError(msg: String) {
        onLogLine("✖ $msg")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeStudioScreen(
    viewModel: MyraViewModel,
    modifier: Modifier = Modifier
) {
    val language by viewModel.codeLanguage.collectAsStateWithLifecycle()
    val codeSource by viewModel.codeSource.collectAsStateWithLifecycle()
    val logs by viewModel.codeLogs.collectAsStateWithLifecycle()
    val runTrigger by viewModel.codeRunTrigger.collectAsStateWithLifecycle()

    var aiCodePrompt by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(runTrigger) {
        if (runTrigger > 0 && (language == CodeLanguage.HTML_JS || language == CodeLanguage.JAVASCRIPT)) {
            val html = if (language == CodeLanguage.HTML_JS) {
                WebSandboxTemplates.injectConsoleBridge(codeSource)
            } else {
                WebSandboxTemplates.wrapJavaScriptHtml(codeSource)
            }
            webViewRef?.loadDataWithBaseURL("https://myra.local/", html, "text/html", "UTF-8", null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 92.dp)
    ) {
        item {
            Text("MYRA Code Studio & Live Runner", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Write HTML/CSS/JS, JavaScript, or Math Scripts and execute live", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        item { FirebaseAiNoticeBanner() }

        // Runtime Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodeLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { viewModel.selectCodeLanguage(lang) },
                        label = { Text(lang.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalPurpleDeep,
                            selectedLabelColor = LuminousCyan
                        )
                    )
                }
            }
        }

        // Ask MYRA to write code
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = aiCodePrompt,
                    onValueChange = { aiCodePrompt = it },
                    placeholder = { Text("e.g., Digital clock or Fibonacci...", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = GlassCardBg,
                        unfocusedContainerColor = GlassCardBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f).testTag("code_ai_prompt")
                )
                Button(
                    onClick = {
                        viewModel.generateAiCodeIntoStudio(aiCodePrompt)
                        aiCodePrompt = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet, contentColor = DeepNavyBg),
                    modifier = Modifier.testTag("generate_code_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Write")
                }
            }
        }

        // Code Editor
        item {
            OutlinedTextField(
                value = codeSource,
                onValueChange = { viewModel.updateCodeSource(it) },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    color = CyanSoft
                ),
                minLines = 8,
                maxLines = 14,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LuminousCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = DeepNavyBg,
                    unfocusedContainerColor = DeepNavyBg
                ),
                modifier = Modifier.fillMaxWidth().testTag("code_editor_input")
            )
        }

        // Run Button
        item {
            Button(
                onClick = { viewModel.runCurrentCode() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = DeepNavyBg),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_code_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Code Now", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Live WebView Sandbox Output (for HTML/CSS/JS & JavaScript)
        if (language == CodeLanguage.HTML_JS || language == CodeLanguage.JAVASCRIPT) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Live Interactive Preview", style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth().height(190.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    webViewClient = WebViewClient()
                                    addJavascriptInterface(
                                        MyraJsConsoleBridge { line ->
                                            post { viewModel.appendCodeLog(line) }
                                        },
                                        "MyraSandbox"
                                    )
                                    val initialHtml = if (language == CodeLanguage.HTML_JS) {
                                        WebSandboxTemplates.injectConsoleBridge(codeSource)
                                    } else {
                                        WebSandboxTemplates.wrapJavaScriptHtml(codeSource)
                                    }
                                    loadDataWithBaseURL("https://myra.local/", initialHtml, "text/html", "UTF-8", null)
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Console Output
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Execution Console", style = MaterialTheme.typography.labelMedium, color = ElectricViolet)
                Spacer(modifier = Modifier.height(6.dp))
                logs.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (line.startsWith("✖")) RoseAlert else EmeraldPulse
                    )
                }
            }
        }
    }
}
