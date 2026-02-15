package com.ai.assistant.ui.screens.home

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistant.ui.components.*

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
    var hasMicPermission by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    // Auto-scroll to bottom on new messages
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
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
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
            // Warning banner for missing setup
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
                                else "Нажмите для включения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = {
                            if (!hasApiKey) {
                                showPermissionDialog = true
                            } else {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
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
                items(messages, key = { "${it.text.hashCode()}_${it.timestamp}" }) { message ->
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
                        placeholder = if (isListening) "Слушаю..." else "Введите команду..."
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    AnimatedMicButton(
                        isListening = isListening,
                        isProcessing = isProcessing,
                        onClick = {
                            if (!hasMicPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Настройка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionCard(
                        title = "Служба специальных возможностей",
                        description = "Для управления приложениями",
                        isGranted = isAccessibilityEnabled,
                        icon = Icons.Filled.Accessibility,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )
                    PermissionCard(
                        title = "Микрофон",
                        description = "Для голосовых команд",
                        isGranted = hasMicPermission,
                        icon = Icons.Filled.Mic,
                        onRequest = {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                    PermissionCard(
                        title = "API ключ Groq",
                        description = if (hasApiKey) "Настроен" else "Не настроен — укажите в настройках",
                        isGranted = hasApiKey,
                        icon = Icons.Filled.Key,
                        onRequest = { /* Navigate to settings */ }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }
}
