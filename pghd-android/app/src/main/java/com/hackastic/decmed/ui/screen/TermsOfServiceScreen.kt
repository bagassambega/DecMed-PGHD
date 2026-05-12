package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Terms of Service screen — the first screen displayed on initial launch.
 *
 * UX behavior:
 * - Accept and Decline buttons are DISABLED until the user scrolls to the bottom.
 * - Scroll-to-end detection uses LazyListState.canScrollForward: when it returns false,
 *   the user has reached the end of the content.
 * - Decline calls onDecline (which triggers finishAffinity in the Activity).
 * - Accept calls onAccept (which persists ToS acceptance and navigates forward).
 */
@Composable
fun TermsOfServiceScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val listState = rememberLazyListState()

    // canScrollForward is false when the user has scrolled to the very bottom.
    // We also handle the edge case where content fits the screen without scrolling
    // (canScrollForward is false AND canScrollBackward is false → no scroll needed).
    val hasReachedBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Title
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Please read the following terms carefully before proceeding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable ToS content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    TosSection(
                        title = "1. Introduction",
                        body = "Welcome to DecMed PGHD Collector (\"the App\"). This application " +
                            "is designed to collect Patient-Generated Health Data (PGHD) using " +
                            "the sensors available on your Android device. By using this App, " +
                            "you agree to be bound by these Terms of Service."
                    )
                }
                item {
                    TosSection(
                        title = "2. Data Collection",
                        body = "The App collects data from hardware sensors on your device, " +
                            "including but not limited to: accelerometer, gyroscope, heart rate " +
                            "sensor, step counter, barometric pressure sensor, and ambient light " +
                            "sensor. You will be asked to explicitly approve which sensors the " +
                            "App may access. You may modify these approvals at any time through " +
                            "the Settings screen."
                    )
                }
                item {
                    TosSection(
                        title = "3. Data Storage & Encryption",
                        body = "All collected sensor data is stored locally on your device in an " +
                            "encrypted SQLite database using AES-256 encryption (SQLCipher). " +
                            "Your data is never transmitted to external servers without your " +
                            "explicit consent. The encryption key is managed through the Android " +
                            "Keystore system to prevent unauthorized access."
                    )
                }
                item {
                    TosSection(
                        title = "4. Purpose of Data Collection",
                        body = "The data collected by this App is intended for health monitoring " +
                            "and wellness purposes. This includes, but is not limited to: " +
                            "physical activity tracking, gait analysis, fall detection, sleep " +
                            "pattern estimation, cardiovascular monitoring, and environmental " +
                            "health factor tracking. The App does NOT provide medical diagnoses " +
                            "or treatment recommendations."
                    )
                }
                item {
                    TosSection(
                        title = "5. User Consent",
                        body = "By accepting these terms, you consent to the collection of health " +
                            "data from the sensors you approve. You understand that:\n\n" +
                            "• You have full control over which sensors are activated.\n" +
                            "• You may revoke sensor access at any time via the Settings screen.\n" +
                            "• Data collection occurs in the background via a foreground service.\n" +
                            "• A persistent notification will be displayed while data collection " +
                            "is active, as required by the Android operating system."
                    )
                }
                item {
                    TosSection(
                        title = "6. Privacy",
                        body = "We are committed to protecting your privacy. The App operates " +
                            "entirely on-device. No personal data, sensor readings, or device " +
                            "identifiers are transmitted to any third party. Your data remains " +
                            "under your sole control. You may delete all collected data at any " +
                            "time by clearing the App's data through Android system settings."
                    )
                }
                item {
                    TosSection(
                        title = "7. Permissions",
                        body = "The App requires the following Android permissions to function:\n\n" +
                            "• BODY_SENSORS: To access health-related sensors such as heart rate.\n" +
                            "• BODY_SENSORS_BACKGROUND: To continue sensor access when the App " +
                            "is not in the foreground.\n" +
                            "• HIGH_SAMPLING_RATE_SENSORS: To collect medical-grade sensor data " +
                            "at higher frequencies.\n" +
                            "• FOREGROUND_SERVICE: To maintain continuous data collection.\n" +
                            "• POST_NOTIFICATIONS: To display the required foreground service " +
                            "notification."
                    )
                }
                item {
                    TosSection(
                        title = "8. Limitations",
                        body = "This App is a research and health monitoring tool. It is NOT a " +
                            "certified medical device. Data collected should not be used as the " +
                            "sole basis for medical decisions. Always consult a qualified " +
                            "healthcare professional for medical advice. The developers assume " +
                            "no liability for health decisions made based on data from this App."
                    )
                }
                item {
                    TosSection(
                        title = "9. Updates to Terms",
                        body = "We reserve the right to modify these Terms of Service at any time. " +
                            "You will be notified of material changes through the App. Continued " +
                            "use of the App after such modifications constitutes acceptance of " +
                            "the updated terms."
                    )
                }
                item {
                    TosSection(
                        title = "10. Contact",
                        body = "For questions or concerns regarding these terms or the App's data " +
                            "practices, please contact the development team at the repository " +
                            "listed in the App's About section."
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "— End of Terms of Service —",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Scroll hint
            if (!hasReachedBottom) {
                Text(
                    text = "↓ Scroll to the bottom to enable the buttons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = hasReachedBottom,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    enabled = hasReachedBottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun TosSection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
