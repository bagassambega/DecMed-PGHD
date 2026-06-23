package com.hackastic.decmed.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ─── Domain types ──────────────────────────────────────────────────────────────

private data class TosClause(val heading: String, val body: String)

private data class TosSection(
    val id: String,
    val title: String,
    val summary: String,
    val clauses: List<TosClause>,
    val checkboxLabel: String
)

// ─── Terms content (mirrors terms-content.ts TERMS_SECTIONS exactly) ──────────

private val TERMS_SECTIONS = listOf(
    TosSection(
        id = "general",
        title = "General Terms & PGHD Consent Framework",
        summary = "These terms govern your use of the DecMed Patient-Generated Health Data (PGHD) experience. " +
            "By proceeding, you confirm that you have read each section in full and explicitly agree to all terms below.",
        clauses = listOf(
            TosClause(
                "1.1 Acceptance of Terms",
                "By accessing or using the DecMed application, you confirm that you are at least 18 years old " +
                    "(or have verifiable parental/guardian consent) and agree to be bound by these terms. " +
                    "If you do not agree, you must not use this application."
            ),
            TosClause(
                "1.2 PGHD Collection Scope",
                "DecMed collects patient-generated health data (PGHD) from your approved device sensors and from " +
                    "your in-app health interactions. This PGHD is used to support your longitudinal health record " +
                    "and authorized care workflows."
            ),
            TosClause(
                "1.3 Account Responsibilities",
                "You are responsible for maintaining the confidentiality of your PIN, seed words, and any " +
                    "authentication credentials. Activity performed under your account is your responsibility. " +
                    "You must notify DecMed immediately if you suspect unauthorized access."
            ),
            TosClause(
                "1.4 Accuracy of Information",
                "You agree to provide accurate, current, and complete information during registration and while " +
                    "using the platform. False or misleading information may result in suspension or termination of access."
            ),
            TosClause(
                "1.5 Irreversible Decision After Acceptance",
                "Once you accept these Terms and PGHD consent, that acceptance decision is final for your account " +
                    "context and cannot be revoked, withdrawn, or changed through in-app controls. If you do not " +
                    "agree with this condition, you must decline before continuing."
            ),
            TosClause(
                "1.6 Limitation of Liability",
                "DecMed is provided on an \"as-is\" basis. To the maximum extent permitted by applicable rules and " +
                    "agreements, DecMed developers and operators are not liable for indirect, incidental, special, " +
                    "consequential, or punitive damages resulting from use of the platform."
            ),
            TosClause(
                "1.7 Modifications to Terms",
                "DecMed may update these terms over time. Material changes will be communicated in-app. " +
                    "Continued use after such updates indicates acceptance of the revised terms."
            )
        ),
        checkboxLabel = "I have read and agree to the General Terms & PGHD Consent Framework."
    ),
    TosSection(
        id = "data-access",
        title = "PGHD Access by Authorized Care Parties",
        summary = "This section describes how authorized healthcare organizations and approved personnel may " +
            "access and act on your PGHD-linked records within DecMed.",
        clauses = listOf(
            TosClause(
                "2.1 Authorized Access",
                "By agreeing to this section, you consent to authorized healthcare organizations and credentialed " +
                    "personnel accessing your records where PGHD is included. Access is session-bound through " +
                    "cryptographic authorization and all access events are auditable."
            ),
            TosClause(
                "2.2 Scope of Access",
                "Authorized personnel may access relevant administrative context, clinical records, and " +
                    "PGHD-derived summaries needed for care delivery and continuity."
            ),
            TosClause(
                "2.3 Data Modification Rights",
                "Authorized personnel may create and update clinical records related to your care. All changes " +
                    "are versioned and timestamped to preserve traceability."
            ),
            TosClause(
                "2.4 Institutional Accountability",
                "Each organization and personnel account interacting with your records is identifiable and " +
                    "auditable. Access and modification events are retained for oversight and verification."
            ),
            TosClause(
                "2.5 Emergency Access Context",
                "In emergency contexts, designated providers may receive temporary access to critical records, " +
                    "including relevant PGHD context, with all events recorded for later review."
            )
        ),
        checkboxLabel = "I consent to authorized care parties accessing and updating my records with PGHD context as described above."
    ),
    TosSection(
        id = "data-collection",
        title = "PGHD Collection, Processing, and Retention",
        summary = "This section covers data gathered from device sensors and patient inputs, how it is processed, " +
            "and how it is retained in DecMed.",
        clauses = listOf(
            TosClause(
                "3.1 Sensor Data Collection",
                "DecMed may collect data from approved device sensors, including motion, environmental, and " +
                    "health-related streams supported by your hardware. Collection begins only after your explicit " +
                    "acceptance and sensor configuration."
            ),
            TosClause(
                "3.2 PGHD Classification",
                "PGHD includes self-reported inputs and device-generated measurements. DecMed stores PGHD with " +
                    "dedicated classification metadata so it can be distinguished from clinician-verified records."
            ),
            TosClause(
                "3.3 Processing and Storage",
                "Collected data is processed and stored using protected data pipelines and encrypted storage layers. " +
                    "Access is restricted to authorized workflows and auditable operations."
            ),
            TosClause(
                "3.4 Irrevocable Consent Acknowledgement",
                "By accepting this section, you acknowledge that PGHD collection consent is final for your accepted " +
                    "terms state and cannot be revoked or changed through in-app controls after acceptance."
            ),
            TosClause(
                "3.5 Retention and Integrity",
                "PGHD and related record artifacts are retained according to platform retention policy to preserve " +
                    "continuity, reproducibility, and audit integrity."
            ),
            TosClause(
                "3.6 Collection Control Before Start",
                "You choose sensors and collection intervals before collection starts. The manual start action " +
                    "indicates your instruction to begin data capture under the accepted terms."
            )
        ),
        checkboxLabel = "I consent to PGHD collection, processing, retention, and the irrevocable-consent acknowledgement described above."
    ),
    TosSection(
        id = "notifications",
        title = "PGHD Access and Change Notifications",
        summary = "This section describes how DecMed notifies you when records that include PGHD are accessed or modified.",
        clauses = listOf(
            TosClause(
                "4.1 Access Notifications",
                "You receive notifications when authorized parties access your records, including actor identity, " +
                    "access type, data scope, and timestamp."
            ),
            TosClause(
                "4.2 Modification Notifications",
                "You receive notifications when records are created or updated, including what changed, who initiated " +
                    "the action, and when it occurred."
            ),
            TosClause(
                "4.3 System and Oversight Access",
                "Where operational oversight or mandated platform reviews require controlled access, such access " +
                    "remains auditable and is surfaced to you through the notification and access-log experience " +
                    "when available."
            ),
            TosClause(
                "4.4 Third-Party Audit Access",
                "If independent auditors are engaged to verify system integrity, their access is controlled, " +
                    "auditable, and limited to agreed review scope."
            ),
            TosClause(
                "4.5 Notification Delivery",
                "Notifications are provided via in-app channels and access logs. You are responsible for reviewing " +
                    "these notices regularly."
            ),
            TosClause(
                "4.6 Contesting Access",
                "If you believe access was unauthorized or improper, you may submit a dispute through official " +
                    "support channels for investigation and follow-up."
            )
        ),
        checkboxLabel = "I acknowledge and agree to receive notifications when PGHD-related records are accessed or modified as described above."
    )
)

// ─── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TermsOfServiceScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Per-section read/agreed state
    val sectionScrolled = remember { mutableStateMapOf<String, Boolean>() }
    val sectionAgreed = remember { mutableStateMapOf<String, Boolean>() }
    var masterAgreed by remember { mutableStateOf(false) }
    var showDeclineDialog by remember { mutableStateOf(false) }

    // First section expanded by default, matching web behaviour
    var expandedSectionId by remember { mutableStateOf<String?>(TERMS_SECTIONS.firstOrNull()?.id) }

    val allSectionsAgreed = TERMS_SECTIONS.all { sectionAgreed[it.id] == true }
    val canProceed = allSectionsAgreed && masterAgreed

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Sticky header ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Terms & Conditions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "DecMed — Decentralized EHR Management",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Scrollable body ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Please carefully read each section below. You must scroll to the bottom of every " +
                        "section and check the agreement box before you can proceed. All sections require your explicit consent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TERMS_SECTIONS.forEachIndexed { index, section ->
                    val isScrolled = sectionScrolled[section.id] == true
                    val isAgreed = sectionAgreed[section.id] == true
                    val isExpanded = expandedSectionId == section.id

                    TermsSectionCard(
                        section = section,
                        sectionNumber = index + 1,
                        isScrolled = isScrolled,
                        isAgreed = isAgreed,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedSectionId = if (isExpanded) null else section.id
                        },
                        onScrolled = { sectionScrolled[section.id] = true },
                        onAgreeChange = { checked ->
                            sectionAgreed[section.id] = checked
                            // If any section is unchecked, also uncheck master
                            if (!checked) masterAgreed = false
                        }
                    )
                }

                // ── Master confirmation card ───────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = masterAgreed,
                                enabled = allSectionsAgreed,
                                onCheckedChange = { masterAgreed = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "I have read, understood, and agree to all of the above Terms and Conditions, " +
                                        "including the irrevocable PGHD consent effect after acceptance.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (allSectionsAgreed)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!allSectionsAgreed) {
                                    Text(
                                        text = "(agree to all sections above to enable)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeclineDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Decline")
                            }
                            Button(
                                onClick = onAccept,
                                enabled = canProceed,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Accept & Continue")
                            }
                        }
                    }
                }

                // Footer
                Text(
                    text = "DecMed — Decentralized Electronic Health Record System",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }

    // ── Decline dialog (mirrors web decline dialog exactly) ────────────────
    if (showDeclineDialog) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = {
                Text(
                    text = "Unable to Continue",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You must accept all Terms and Conditions to use the DecMed application. " +
                            "PGHD consent in this flow is final after acceptance.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "If you have concerns about any specific terms, please contact our support team " +
                            "for clarification before making your decision.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDeclineDialog = false }) {
                    Text("Return to Terms")
                }
            }
        )
    }
}

// ─── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun TermsSectionCard(
    section: TosSection,
    sectionNumber: Int,
    isScrolled: Boolean,
    isAgreed: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onScrolled: () -> Unit,
    onAgreeChange: (Boolean) -> Unit
) {
    val contentScrollState = rememberScrollState()

    // Detect reaching the bottom of the clause content.
    // Use a small threshold (8px) to account for sub-pixel rounding.
    val reachedBottom by remember(contentScrollState.value, contentScrollState.maxValue) {
        derivedStateOf {
            contentScrollState.maxValue > 0 &&
                contentScrollState.value >= contentScrollState.maxValue - 8
        }
    }
    LaunchedEffect(reachedBottom) {
        if (reachedBottom) onScrolled()
    }
    // If the content is short enough that no scrolling is needed, mark read once expanded.
    LaunchedEffect(isExpanded, contentScrollState.maxValue) {
        if (isExpanded && contentScrollState.maxValue == 0) onScrolled()
    }

    val badgeText: String
    val badgeContainerColor: androidx.compose.ui.graphics.Color
    val badgeContentColor: androidx.compose.ui.graphics.Color

    when {
        isAgreed -> {
            badgeText = "Agreed"
            badgeContainerColor = MaterialTheme.colorScheme.tertiaryContainer
            badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        isScrolled -> {
            badgeText = "Read"
            badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer
            badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        else -> {
            badgeText = "Unread"
            badgeContainerColor = MaterialTheme.colorScheme.surfaceVariant
            badgeContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // ── Section header row (always visible) ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Section $sectionNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeContainerColor
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeContentColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                androidx.compose.material3.Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Collapsible body ──────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(150))
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Scrollable clause content — fixed height forces the user to scroll
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(contentScrollState)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = section.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        section.clauses.forEach { clause ->
                            Text(
                                text = clause.heading,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = clause.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // ── Per-section agreement checkbox ─────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (!isScrolled) {
                            Text(
                                text = "Scroll to the bottom to enable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = isAgreed,
                                enabled = isScrolled,
                                onCheckedChange = onAgreeChange
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = section.checkboxLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isScrolled)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}