package com.hackastic.decmed.ui.theme

import androidx.compose.ui.graphics.Color

// --- Medical-grade color palette ---
// Design rationale: Health apps conventionally use teal/blue-green tones
// to convey trust and clinical professionalism. These values were selected
// for WCAG AA contrast compliance on both light and dark surfaces.

// Primary: Teal
val Teal80 = Color(0xFFA0D2DB)
val Teal40 = Color(0xFF00796B)
val Teal20 = Color(0xFF004D40)

// Secondary: Soft Blue
val Blue80 = Color(0xFFB3D4FC)
val Blue40 = Color(0xFF1565C0)

// Tertiary: Warm Amber (for accents/warnings)
val Amber80 = Color(0xFFFFE082)
val Amber40 = Color(0xFFF9A825)

// Surface / Background
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E2A2F)
val LightSurface = Color(0xFFF5FAFB)
val LightSurfaceVariant = Color(0xFFE0F2F1)

// Status colors
val AvailableGreen = Color(0xFF4CAF50)
val UnavailableGrey = Color(0xFF9E9E9E)
val ErrorRed = Color(0xFFEF5350)