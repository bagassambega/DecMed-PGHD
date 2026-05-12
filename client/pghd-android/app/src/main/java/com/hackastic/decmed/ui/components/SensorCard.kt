package com.hackastic.decmed.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.theme.AvailableGreen
import com.hackastic.decmed.ui.theme.UnavailableGrey

/**
 * Reusable card composable for displaying sensor information.
 *
 * Supports two modes:
 * 1. Display mode (SensorListScreen): Shows sensor name, availability badge, expand for details.
 * 2. Config mode (SensorConfigScreen): Adds a toggle switch for user approval.
 *
 * The expand/collapse animation uses AnimatedVisibility for smooth height transitions.
 */
@Composable
fun SensorCard(
    sensorName: String,
    isAvailable: Boolean,
    healthDataCapabilities: List<String>,
    clinicalRelevance: String,
    modifier: Modifier = Modifier,
    showToggle: Boolean = false,
    isApproved: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAvailable) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: icon + name + availability badge + toggle/expand
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isAvailable) MaterialTheme.colorScheme.primary else UnavailableGrey
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sensorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAvailable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            UnavailableGrey
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAvailable) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RemoveCircleOutline
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isAvailable) AvailableGreen else UnavailableGrey
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAvailable) "Available" else "Not available",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAvailable) AvailableGreen else UnavailableGrey
                        )
                    }
                }

                // Toggle switch (config mode only)
                if (showToggle && isAvailable) {
                    Switch(
                        checked = isApproved,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    )
                }

                // Expand/collapse button (only for available sensors)
                if (isAvailable) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            // Expandable detail section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Health Data Capabilities",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    healthDataCapabilities.forEach { capability ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = capability,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Clinical Relevance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = clinicalRelevance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
