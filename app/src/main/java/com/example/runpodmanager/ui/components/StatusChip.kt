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
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val normalizedStatus = status.uppercase()
    val backgroundColor = getStatusBackgroundColor(normalizedStatus)
    val textColor = getStatusTextColor(normalizedStatus)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = normalizedStatus,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun getStatusBackgroundColor(status: String): Color = when (status) {
    "RUNNING" -> StatusGreen
    "EXITED", "STOPPED" -> StatusOrange
    "CREATED", "PENDING" -> StatusBlue
    "TERMINATED", "FAILED" -> StatusRed
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun getStatusTextColor(status: String): Color = when (status) {
    "RUNNING", "EXITED", "STOPPED", "CREATED", "PENDING", "TERMINATED", "FAILED" -> Color.White
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
