package com.example.runpodmanager.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

private val AccentColor = Color(0xFF4EC9B0)
private val ErrorColor = Color(0xFFE53935)

@Composable
fun BuildProgressOverlay(
    isBuilding: Boolean,
    isDownloadingApk: Boolean,
    downloadProgress: Float,
    modifier: Modifier = Modifier
) {
    Log.d("BuildOverlay", "isBuilding=$isBuilding, isDownloadingApk=$isDownloadingApk")
    if (!isBuilding && !isDownloadingApk) return
    Log.d("BuildOverlay", "MOSTRANDO OVERLAY")

    // Animación de rotación continua para el icono
    var rotation by remember { mutableStateOf(0f) }
    LaunchedEffect(isBuilding, isDownloadingApk) {
        if (!isBuilding && !isDownloadingApk) return@LaunchedEffect
        var lastFrameTime = 0L
        while (isActive && (isBuilding || isDownloadingApk)) {
            val frameTime = withFrameNanos { it }
            if (lastFrameTime != 0L) {
                val deltaNanos = frameTime - lastFrameTime
                val deltaDegrees = (deltaNanos / 1_000_000_000f) * 360f
                rotation = (rotation + deltaDegrees) % 360f
            }
            lastFrameTime = frameTime
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isBuilding) "Compilando..." else "Descargando APK...",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
            if (isDownloadingApk) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${(downloadProgress * 100).toInt()}%",
                    color = AccentColor,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                color = AccentColor,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun BuildResultOverlay(
    buildSuccess: Boolean?,
    canCopyLog: Boolean,
    onCopyLog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (buildSuccess == null) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val isSuccess = buildSuccess == true
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isSuccess) AccentColor else ErrorColor,
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isSuccess) "Build Exitoso" else "Build Fallido",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            if (!isSuccess && canCopyLog) {
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = onCopyLog) {
                    Text(
                        text = "Copy Log",
                        color = AccentColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Toca para continuar",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
