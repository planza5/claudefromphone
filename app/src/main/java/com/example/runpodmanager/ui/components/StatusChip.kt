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

private val StatusGreen = Color(0xFF4CAF50)
private val StatusOrange = Color(0xFFFF9800)
private val StatusBlue = Color(0xFF2196F3)
private val StatusRed = Color(0xFFF44336)

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val normalizedStatus = status.uppercase()
    val (displayStatus, backgroundColor) = when (normalizedStatus) {
        "RUNNING" -> normalizedStatus to StatusGreen
        "EXITED", "STOPPED" -> "PAUSED" to StatusOrange
        "CREATED", "PENDING" -> normalizedStatus to StatusBlue
        "TERMINATED", "FAILED" -> normalizedStatus to StatusRed
        else -> normalizedStatus to MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (backgroundColor != MaterialTheme.colorScheme.surfaceVariant)
        Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(displayStatus, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}
