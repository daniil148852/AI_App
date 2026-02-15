package com.ai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistant.ui.theme.*

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isAction: Boolean = false,
    val actionStatus: ActionStatus = ActionStatus.NONE,
    val timestamp: String = ""
)

enum class ActionStatus { NONE, IN_PROGRESS, SUCCESS, FAILED }

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Assistant avatar
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Top),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    message.isAction -> when (message.actionStatus) {
                        ActionStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.15f)
                        ActionStatus.FAILED -> ErrorRed.copy(alpha = 0.15f)
                        ActionStatus.IN_PROGRESS -> InfoBlue.copy(alpha = 0.15f)
                        ActionStatus.NONE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isAction) {
                        val icon = when (message.actionStatus) {
                            ActionStatus.SUCCESS -> Icons.Filled.CheckCircle
                            ActionStatus.FAILED -> Icons.Filled.Error
                            ActionStatus.IN_PROGRESS -> Icons.Filled.PlayCircle
                            ActionStatus.NONE -> Icons.Filled.TouchApp
                        }
                        val iconColor = when (message.actionStatus) {
                            ActionStatus.SUCCESS -> SuccessGreen
                            ActionStatus.FAILED -> ErrorRed
                            ActionStatus.IN_PROGRESS -> InfoBlue
                            ActionStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = iconColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = message.text,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            if (message.timestamp.isNotEmpty()) {
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(
                        start = if (isUser) 0.dp else 4.dp,
                        end = if (isUser) 4.dp else 0.dp,
                        top = 2.dp
                    )
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Top),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
