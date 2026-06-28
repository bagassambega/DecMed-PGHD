package com.hackastic.decmed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ProcessToastKind {
    Success,
    Failure,
    Info
}

data class ProcessToastEvent(
    val kind: ProcessToastKind,
    val detail: String,
    val title: String = kind.name
) {
    val summary: String
        get() = detail.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: title
}

@Composable
fun InteractiveProcessToastHost(
    event: ProcessToastEvent?,
    onEventConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    visibleMillis: Long = 4_000L
) {
    var visibleEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }
    var dialogEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect
        visibleEvent = event
        onEventConsumed()
        delay(visibleMillis)
        if (visibleEvent == event) {
            visibleEvent = null
        }
    }

    visibleEvent?.let { current ->
        ProcessToastSurface(
            event = current,
            modifier = modifier,
            onClick = {
                dialogEvent = current
                visibleEvent = null
            }
        )
    }

    dialogEvent?.let { current ->
        ProcessToastDetailDialog(
            event = current,
            onDismiss = { dialogEvent = null }
        )
    }
}

@Composable
private fun ProcessToastSurface(
    event: ProcessToastEvent,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = processToastColors(event.kind)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = colors.container,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (event.kind) {
                    ProcessToastKind.Success -> Icons.Default.CheckCircle
                    ProcessToastKind.Failure -> Icons.Default.Error
                    ProcessToastKind.Info -> Icons.Default.Info
                },
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.content
                )
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProcessToastDetailDialog(
    event: ProcessToastEvent,
    onDismiss: () -> Unit
) {
    val colors = processToastColors(event.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (event.kind) {
                        ProcessToastKind.Success -> Icons.Default.CheckCircle
                        ProcessToastKind.Failure -> Icons.Default.Error
                        ProcessToastKind.Info -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = colors.content
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(event.title)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = event.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Ok")
            }
        }
    )
}

private data class ProcessToastColors(
    val container: Color,
    val content: Color
)

@Composable
private fun processToastColors(kind: ProcessToastKind): ProcessToastColors =
    when (kind) {
        ProcessToastKind.Success -> ProcessToastColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer
        )
        ProcessToastKind.Failure -> ProcessToastColors(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer
        )
        ProcessToastKind.Info -> ProcessToastColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
