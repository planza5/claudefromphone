package com.example.runpodmanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runpodmanager.data.model.Pod

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodCard(
    pod: Pod,
    onClick: () -> Unit,
    onStartPod: () -> Unit = {},
    onStopPod: () -> Unit = {},
    onDeletePod: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isRunning = pod.desiredStatus?.uppercase() == "RUNNING"
    val sshCommand = pod.publicIp?.let { ip -> pod.portMappings?.get("22")?.let { "ssh root@$ip -p $it" } }
    val nameWithPrice = pod.name + (pod.costPerHr?.let { " (\$${String.format("%.3f", it)}/hr)" } ?: "")

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Línea 1: Nombre (precio) | StatusChip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nameWithPrice,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusChip(status = pod.desiredStatus ?: "UNKNOWN")
                }

                // Línea 2: SSH command
                Text(
                    text = sshCommand ?: "SSH no disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sshCommand != null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (isRunning) {
                DropdownMenuItem(
                    text = { Text("Detener") },
                    onClick = {
                        showMenu = false
                        onStopPod()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Stop, contentDescription = null)
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Iniciar") },
                    onClick = {
                        showMenu = false
                        onStartPod()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                )
            }

            DropdownMenuItem(
                text = {
                    Text(
                        "Eliminar",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onDeletePod()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}
