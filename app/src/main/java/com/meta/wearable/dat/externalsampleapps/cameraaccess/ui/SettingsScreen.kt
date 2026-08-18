package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

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
    var geminiAPIKey by remember { mutableStateOf(SettingsManager.geminiAPIKey) }
    var language by remember { mutableStateOf(SettingsManager.assistantLanguage) }
    var systemPrompt by remember { mutableStateOf(SettingsManager.geminiSystemPrompt) }
    var videoFrameMode by remember { mutableStateOf(SettingsManager.videoFrameMode) }
    var glassesMic by remember { mutableStateOf(SettingsManager.glassesMicEnabled) }
    var dedaActivation by remember { mutableStateOf(SettingsManager.dedaActivationMode) }
    var dedaSilenceSec by remember { mutableStateOf(SettingsManager.dedaSilenceTimeoutSec.toString()) }
    var dedaMaxMin by remember { mutableStateOf(SettingsManager.dedaMaxSessionMin.toString()) }
    var webrtcSignalingURL by remember { mutableStateOf(SettingsManager.webrtcSignalingURL) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun save() {
        SettingsManager.geminiAPIKey = geminiAPIKey.trim()
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.videoFrameMode = videoFrameMode
        SettingsManager.glassesMicEnabled = glassesMic
        SettingsManager.dedaActivationMode = dedaActivation
        dedaSilenceSec.toIntOrNull()?.let { SettingsManager.dedaSilenceTimeoutSec = it }
        dedaMaxMin.toIntOrNull()?.let { SettingsManager.dedaMaxSessionMin = it }
        // The always-on service re-reads the activation mode on every start.
        GlassesButtonService.start(context)
    }

    fun reload() {
        geminiAPIKey = SettingsManager.geminiAPIKey
        language = SettingsManager.assistantLanguage
        systemPrompt = SettingsManager.geminiSystemPrompt
        videoFrameMode = SettingsManager.videoFrameMode
        glassesMic = SettingsManager.glassesMicEnabled
        dedaActivation = SettingsManager.dedaActivationMode
        dedaSilenceSec = SettingsManager.dedaSilenceTimeoutSec.toString()
        dedaMaxMin = SettingsManager.dedaMaxSessionMin.toString()
        webrtcSignalingURL = SettingsManager.webrtcSignalingURL
    }

    // Switching language rewrites the prompt in the box, unless the user has
    // written their own. A hand-edited prompt is never discarded silently.
    fun selectLanguage(next: AssistantLanguage) {
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.assistantLanguage = next
        language = next
        systemPrompt = SettingsManager.geminiSystemPrompt
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = {
                    save()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
            Hint("Free key from aistudio.google.com/apikey. No card required.")

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
