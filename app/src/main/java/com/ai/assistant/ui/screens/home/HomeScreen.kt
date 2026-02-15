package com.ai.assistant.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistant.ui.components.AnimatedMicButton
import com.ai.assistant.ui.components.ChatBubble
import com.ai.assistant.ui.components.CommandInput
import com.ai.assistant.ui.components.PermissionCard
import com.ai.assistant.ui.components.StatusIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val textInput by viewModel.textInput.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceManager.isListening.collectAsStateWithLifecycle()
    val partialText by viewModel.voiceManager.partialText.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showRestrictedSettingsGuide by remember { mutableStateOf(false) }
    var hasMicPermission by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isAccessibilityEnabled = viewModel.isAccessibilityEnabled()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI Assistant",
                            style = MaterialTheme.typography.titleLarge
                        )
                        StatusIndicator(status = serviceStatus)
                    }
                },
                actions = {
                    if (!isAccessibilityEnabled || !hasApiKey) {
                        IconButton(onClick = { showPermissionDialog = true }) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Setup required"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Warning banner
            AnimatedVisibility(
                visible = !isAccessibilityEnabled || !hasApiKey
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!hasApiKey) "Нужен API ключ Groq"
                                else "Нужна служба специальных возможностей",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (!hasApiKey) "Укажите ключ в настройках"
                                else "Нажмите для настройки",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                                    .copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = {
                            if (!isAccessibilityEnabled && Build.VERSION.SDK_INT >= 33) {
                                showRestrictedSettingsGuide = true
                            } else {
                                showPermissionDialog = true
                            }
                        }) {
                            Text("Настроить")
                        }
                    }
                }
            }

            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(message = message)
                }
            }

            // Partial recognition text
            AnimatedVisibility(
                visible = isListening && partialText != null
            ) {
                Text(
                    text = "🎤 ${partialText ?: ""}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    CommandInput(
                        value = textInput,
                        onValueChange = viewModel::updateTextInput,
                        onSend = viewModel::sendCommand,
                        isProcessing = isProcessing,
                        modifier = Modifier.weight(1f),
                        placeholder = if (isListening) "Слушаю..."
                        else "Введите команду..."
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    AnimatedMicButton(
                        isListening = isListening,
                        isProcessing = isProcessing,
                        onClick = {
                            if (!hasMicPermission) {
                                micPermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            } else {
                                viewModel.toggleVoice()
                            }
                        }
                    )
                }
            }
        }
    }

    // Permission setup dialog
    if (showPermissionDialog) {
        PermissionSetupDialog(
            isAccessibilityEnabled = isAccessibilityEnabled,
            hasMicPermission = hasMicPermission,
            hasApiKey = hasApiKey,
            onDismiss = { showPermissionDialog = false },
            onRequestAccessibility = {
                if (Build.VERSION.SDK_INT >= 33 && !isAccessibilityEnabled) {
                    showPermissionDialog = false
                    showRestrictedSettingsGuide = true
                } else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
            onRequestMic = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }

    // Restricted Settings Guide (Android 13+)
    if (showRestrictedSettingsGuide) {
        RestrictedSettingsDialog(
            onDismiss = { showRestrictedSettingsGuide = false },
            onOpenAppSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            onOpenAccessibility = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun PermissionSetupDialog(
    isAccessibilityEnabled: Boolean,
    hasMicPermission: Boolean,
    hasApiKey: Boolean,
    onDismiss: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestMic: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionCard(
                    title = "Служба специальных возможностей",
                    description = "Для управления приложениями",
                    isGranted = isAccessibilityEnabled,
                    icon = Icons.Filled.Accessibility,
                    onRequest = onRequestAccessibility
                )
                PermissionCard(
                    title = "Микрофон",
                    description = "Для голосовых команд",
                    isGranted = hasMicPermission,
                    icon = Icons.Filled.Mic,
                    onRequest = onRequestMic
                )
                PermissionCard(
                    title = "API ключ Groq",
                    description = if (hasApiKey) "Настроен"
                    else "Не настроен — укажите в настройках",
                    isGranted = hasApiKey,
                    icon = Icons.Filled.Key,
                    onRequest = { }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
private fun RestrictedSettingsDialog(
    onDismiss: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Разрешение ограниченных настроек",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Android 13+ блокирует службу специальных возможностей " +
                            "для приложений, установленных не из магазина. " +
                            "Нужно сначала снять ограничение:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme
                            .primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StepItem(1, "Нажмите кнопку \"Открыть настройки\" ниже")
                        StepItem(2, "Нажмите ⋮ (три точки) в правом верхнем углу")
                        StepItem(3, "Выберите \"Разрешить ограниченные настройки\"")
                        StepItem(4, "Подтвердите отпечатком / PIN-кодом")
                        StepItem(5, "Вернитесь и нажмите \"Включить службу\"")
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Шаг 1: Открыть настройки приложения")
                }

                OutlinedButton(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Accessibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Шаг 5: Включить службу")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть")
                }
            }
        }
    )
}

@Composable
private fun StepItem(step: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$step",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
