package com.ai.assistant.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember(settings.groqApiKey) { mutableStateOf(settings.groqApiKey) }
    var showModelPicker by remember { mutableStateOf(false) }

    val availableModels = listOf(
        "llama-3.3-70b-versatile" to "LLaMA 3.3 70B (рекомендуется)",
        "llama-3.1-70b-versatile" to "LLaMA 3.1 70B",
        "llama-3.1-8b-instant" to "LLaMA 3.1 8B (быстрая)",
        "llama3-70b-8192" to "LLaMA 3 70B",
        "llama3-8b-8192" to "LLaMA 3 8B (быстрая)",
        "mixtral-8x7b-32768" to "Mixtral 8x7B",
        "gemma2-9b-it" to "Gemma 2 9B"
    )

    val languages = listOf(
        "ru-RU" to "Русский",
        "en-US" to "English",
        "uk-UA" to "Українська",
        "de-DE" to "Deutsch",
        "fr-FR" to "Français",
        "es-ES" to "Español"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════════════════ API Settings ═══════════════════
            Text(
                "API Настройки",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Groq API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.updateApiKey(apiKeyInput) },
                            enabled = apiKeyInput != settings.groqApiKey
                        ) {
                            Icon(Icons.Filled.Save, "Save")
                        }
                    }
                },
                supportingText = {
                    Text("Получите ключ на console.groq.com")
                },
                singleLine = true
            )

            OutlinedCard(
                onClick = { showModelPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Psychology, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI Модель", style = MaterialTheme.typography.titleSmall)
                        Text(
                            availableModels.find { it.first == settings.aiModel }?.second
                                ?: settings.aiModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }

            Divider()

            // ═══════════════════ Voice Settings ═══════════════════
            Text(
                "Голос",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            var showLanguagePicker by remember { mutableStateOf(false) }

            OutlinedCard(
                onClick = { showLanguagePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Язык распознавания", style = MaterialTheme.typography.titleSmall)
                        Text(
                            languages.find { it.first == settings.voiceLanguage }?.second
                                ?: settings.voiceLanguage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }

            if (showLanguagePicker) {
                AlertDialog(
                    onDismissRequest = { showLanguagePicker = false },
                    title = { Text("Язык") },
                    text = {
                        Column {
                            languages.forEach { (code, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings.voiceLanguage == code,
                                        onClick = {
                                            viewModel.updateVoiceLanguage(code)
                                            showLanguagePicker = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguagePicker = false }) {
                            Text("Закрыть")
                        }
                    }
                )
            }

            Divider()

            // ═══════════════════ Execution Settings ═══════════════════
            Text(
                "Выполнение",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Автовыполнение", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Выполнять команды без подтверждения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoExecute,
                    onCheckedChange = viewModel::updateAutoExecute
                )
            }

            Column {
                Text(
                    "Максимум шагов: ${settings.maxStepsPerCommand}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Предел шагов на одну команду",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.maxStepsPerCommand.toFloat(),
                    onValueChange = { viewModel.updateMaxSteps(it.toInt()) },
                    valueRange = 5f..50f,
                    steps = 8
                )
            }

            Column {
                Text(
                    "Задержка между действиями: ${settings.actionDelayMs}мс",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Больше значение = надёжнее, но медленнее",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.actionDelayMs.toFloat(),
                    onValueChange = { viewModel.updateActionDelay(it.toLong()) },
                    valueRange = 200f..2000f,
                    steps = 8
                )
            }

            Divider()

            // ═══════════════════ Debug ═══════════════════
            Text(
                "Отладка",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Показывать debug-информацию",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Рассуждения AI, дерево экрана",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showDebugInfo,
                    onCheckedChange = viewModel::updateShowDebugInfo
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Вибрация", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Тактильная обратная связь при действиях",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.hapticFeedback,
                    onCheckedChange = viewModel::updateHapticFeedback
                )
            }

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "AI Phone Assistant v1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Powered by Groq + LLaMA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Model picker dialog
    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Выберите модель") },
            text = {
                Column {
                    availableModels.forEach { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.aiModel == id,
                                onClick = {
                                    viewModel.updateModel(id)
                                    showModelPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}
