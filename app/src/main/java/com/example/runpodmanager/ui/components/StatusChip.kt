package com.example.runpodmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status.uppercase()) {
        "RUNNING" -> Color(0xFF4CAF50) to Color.White
        "EXITED", "STOPPED" -> Color(0xFFFF9800) to Color.White
        "CREATED", "PENDING" -> Color(0xFF2196F3) to Color.White
        "TERMINATED", "FAILED" -> Color(0xFFF44336) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
