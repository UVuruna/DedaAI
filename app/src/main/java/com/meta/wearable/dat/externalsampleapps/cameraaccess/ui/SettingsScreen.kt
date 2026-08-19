package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.deda.GlassesButtonService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.DedaActivationMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.VideoFrameMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The user's own key only — the built-in default must never be shown
    // as if it were theirs (public APK, pregled 8). Empty = default active.
    var geminiAPIKey by remember { mutableStateOf(SettingsManager.geminiAPIKeyUser) }
    var language by remember { mutableStateOf(SettingsManager.assistantLanguage) }
    var systemPrompt by remember { mutableStateOf(SettingsManager.geminiSystemPrompt) }
    var videoFrameMode by remember { mutableStateOf(SettingsManager.videoFrameMode) }
    var glassesMic by remember { mutableStateOf(SettingsManager.glassesMicEnabled) }
    var dedaActivation by remember { mutableStateOf(SettingsManager.dedaActivationMode) }
    var dedaSilenceSec by remember { mutableStateOf(SettingsManager.dedaSilenceTimeoutSec.toString()) }
    var dedaMaxMin by remember { mutableStateOf(SettingsManager.dedaMaxSessionMin.toString()) }
    var webrtcSignalingURL by remember { mutableStateOf(SettingsManager.webrtcSignalingURLUser) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun save() {
        SettingsManager.geminiAPIKey = geminiAPIKey.trim()
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.videoFrameMode = videoFrameMode
        SettingsManager.glassesMicEnabled = glassesMic
        SettingsManager.dedaActivationMode = dedaActivation
        dedaSilenceSec.toIntOrNull()?.let { SettingsManager.dedaSilenceTimeoutSec = it }
        dedaMaxMin.toIntOrNull()?.let { SettingsManager.dedaMaxSessionMin = it }
        SettingsManager.webrtcSignalingURL = webrtcSignalingURL.trim()
        // The always-on service re-reads the activation mode on every start.
        GlassesButtonService.start(context)
    }

    fun reload() {
        geminiAPIKey = SettingsManager.geminiAPIKeyUser
        language = SettingsManager.assistantLanguage
        systemPrompt = SettingsManager.geminiSystemPrompt
        videoFrameMode = SettingsManager.videoFrameMode
        glassesMic = SettingsManager.glassesMicEnabled
        dedaActivation = SettingsManager.dedaActivationMode
        dedaSilenceSec = SettingsManager.dedaSilenceTimeoutSec.toString()
        dedaMaxMin = SettingsManager.dedaMaxSessionMin.toString()
        webrtcSignalingURL = SettingsManager.webrtcSignalingURLUser
    }

    // Switching language rewrites the prompt in the box, unless the user has
    // written their own. A hand-edited prompt is never discarded silently.
    fun selectLanguage(next: AssistantLanguage) {
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.assistantLanguage = next
        language = next
        systemPrompt = SettingsManager.geminiSystemPrompt
    }

    // Declared before the guide early-return so the list does not jump back
    // to the top after closing a guide (pregled 11).
    val scrollState = rememberScrollState()

    // A help guide (assets/guide_*.html) opened over Settings, or null. Pair of
    // (asset file, screen title). Shown full-screen so the guide reads big.
    var guideAsset by remember { mutableStateOf<Pair<String, String>?>(null) }
    guideAsset?.let { (asset, title) ->
        GuideScreen(assetFile = asset, title = title, onBack = { guideAsset = null })
        return
    }

    // One exit path for the arrow AND the system back gesture — two copies
    // is exactly how the gesture was forgotten the first time (pregled 9).
    val exitAndSave = {
        save()
        onBack()
    }
    BackHandler { exitAndSave() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { exitAndSave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Gemini API")
            MonoTextField(
                value = geminiAPIKey,
                onValueChange = { geminiAPIKey = it },
                label = "API Key",
                placeholder = "Enter Gemini API key",
            )
            Hint("Deda needs your own free Gemini key (no card required). Tap the guide below — it walks you through it with pictures.")

            // lang-ok-begin: user-facing help button labels and share text, per language
            val keyGuide = when (language) {
                AssistantLanguage.SERBIAN -> "Vodič: kako uneti ključ"
                AssistantLanguage.SLOVENIAN -> "Vodič: kako vnesti ključ"
                AssistantLanguage.ENGLISH -> "Guide: how to add the key"
            }
            val installGuide = when (language) {
                AssistantLanguage.SERBIAN -> "Vodič: instalacija (za deljenje)"
                AssistantLanguage.SLOVENIAN -> "Vodič: namestitev (za deljenje)"
                AssistantLanguage.ENGLISH -> "Guide: install (to share)"
            }
            val shareLabel = when (language) {
                AssistantLanguage.SERBIAN -> "Podeli Dedu (link / QR)"
                AssistantLanguage.SLOVENIAN -> "Deli Dedo (povezava / QR)"
                AssistantLanguage.ENGLISH -> "Share Deda (link / QR)"
            }
            val shareText = "Deda — glasovni asistent. Instalacija korak po korak:\nhttps://uvuruna.github.io/DedaAI/"
            // lang-ok-end
            Button(
                onClick = { guideAsset = "guide_apikey.html" to keyGuide },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📖  $keyGuide") }
            TextButton(onClick = { guideAsset = "guide_install.html" to installGuide }) {
                Text("📲  $installGuide")
            }
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(android.content.Intent.createChooser(send, shareLabel))
            }) { Text("🔗  $shareLabel") }

            SectionHeader("Language")
            ChipRow(
                options = AssistantLanguage.entries,
                selected = language,
                labelOf = { it.displayName },
                onSelect = { selectLanguage(it) },
            )
            Hint("The language the assistant answers in.")

            SectionHeader("Camera")
            ChipRow(
                options = VideoFrameMode.entries,
                selected = videoFrameMode,
                labelOf = { it.displayName },
                onSelect = { videoFrameMode = it },
            )
            Hint(videoFrameMode.description)
            if (glassesMic && videoFrameMode == VideoFrameMode.ON_QUESTION) {
                // Glasses-mic conversations cannot keep the camera open (the
                // stream mutes the headset link — 2026-08-19), so the picture
                // is grabbed once, before the audio starts. Say so.
                Hint(
                    "Note: with the Glasses microphone the picture is taken " +
                        "once, at the start of the conversation — keeping the " +
                        "camera open during the talk would mute the glasses' audio."
                )
            } else if (glassesMic && videoFrameMode == VideoFrameMode.STREAM) {
                Hint(
                    "Note: continuous frames don't work with the Glasses " +
                        "microphone (the open camera mutes its audio link), so " +
                        "conversations get no pictures in this combination. " +
                        "Choose On question for one picture per conversation."
                )
            }

            SectionHeader("Microphone")
            ChipRow(
                options = listOf(true, false),
                selected = glassesMic,
                labelOf = { if (it) "Glasses" else "Phone" },
                onSelect = { glassesMic = it },
            )
            Hint(
                if (glassesMic) "Listens and answers through the glasses (headset audio). Phone can stay in a pocket."
                else "Listens through the phone mic; the answer still plays on the glasses if connected."
            )

            SectionHeader("Deda")
            ChipRow(
                options = DedaActivationMode.entries,
                selected = dedaActivation,
                labelOf = { it.displayName },
                onSelect = { dedaActivation = it },
            )
            Hint(dedaActivation.description)
            MonoTextField(
                value = dedaSilenceSec,
                onValueChange = { dedaSilenceSec = it.filter { c -> c.isDigit() } },
                label = "Close conversation after silence (seconds)",
                placeholder = "15",
                keyboardType = KeyboardType.Number,
            )
            MonoTextField(
                value = dedaMaxMin,
                onValueChange = { dedaMaxMin = it.filter { c -> c.isDigit() } },
                label = "Force-close conversation after (minutes)",
                placeholder = "15",
                keyboardType = KeyboardType.Number,
            )
            Hint("A conversation ends by itself after the silence above, and never runs longer than the cap.")

            SectionHeader("System Prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            if (systemPrompt.trim() != language.systemPrompt.trim()) {
                TextButton(onClick = {
                    SettingsManager.clearSystemPromptOverride()
                    systemPrompt = SettingsManager.geminiSystemPrompt
                }) {
                    Text("Restore default for " + language.displayName)
                }
            }

            SectionHeader("WebRTC")
            MonoTextField(
                value = webrtcSignalingURL,
                onValueChange = { webrtcSignalingURL = it },
                label = "Signaling URL",
                placeholder = "wss://your-server.example.com",
                keyboardType = KeyboardType.Uri,
            )
            Hint("Optional. Only needed to mirror the view to a browser.")

            TextButton(onClick = { showResetDialog = true }) {
                Text("Reset to Defaults", color = Color.Red)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("This will reset all settings to the values built into the app.") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.resetAll()
                    reload()
                    showResetDialog = false
                }) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
