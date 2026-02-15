package com.ai.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistant.ui.theme.ErrorRed
import com.ai.assistant.ui.theme.SuccessGreen
import com.ai.assistant.ui.theme.WarningOrange

enum class ServiceStatus {
    CONNECTED, DISCONNECTED, PROCESSING
}

@Composable
fun StatusIndicator(
    status: ServiceStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        ServiceStatus.CONNECTED -> SuccessGreen
        ServiceStatus.DISCONNECTED -> ErrorRed
        ServiceStatus.PROCESSING -> WarningOrange
    }

    val label = when (status) {
        ServiceStatus.CONNECTED -> "Подключено"
        ServiceStatus.DISCONNECTED -> "Отключено"
        ServiceStatus.PROCESSING -> "Выполняется..."
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ServiceStatus.PROCESSING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
