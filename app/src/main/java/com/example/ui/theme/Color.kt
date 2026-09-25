package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------- Light palette
val PrimaryLight = Color(0xFF2563EB)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDBEAFE)
val OnPrimaryContainerLight = Color(0xFF1E3A8A)

val SecondaryLight = Color(0xFF0D9488)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCCFBF1)
val OnSecondaryContainerLight = Color(0xFF115E59)

val TertiaryLight = Color(0xFF7C3AED)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFEDE9FE)
val OnTertiaryContainerLight = Color(0xFF5B21B6)

val BackgroundLight = Color(0xFFF6F8FB)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFEEF2F7)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFF94A3B8)
val OutlineVariantLight = Color(0xFFDDE4ED)
val ErrorSchemeLight = Color(0xFFDC2626)
val OnErrorSchemeLight = Color(0xFFFFFFFF)
val ErrorSchemeContainerLight = Color(0xFFFEE2E2)
val OnErrorSchemeContainerLight = Color(0xFF7F1D1D)

// ---------------------------------------------------------------- Dark palette
val PrimaryDark = Color(0xFF7BA9FB)
val OnPrimaryDark = Color(0xFF0B192C)
val PrimaryContainerDark = Color(0xFF1E3A8A)
val OnPrimaryContainerDark = Color(0xFFDBEAFE)

val SecondaryDark = Color(0xFF2DD4BF)
val OnSecondaryDark = Color(0xFF042F2E)
val SecondaryContainerDark = Color(0xFF134E4A)
val OnSecondaryContainerDark = Color(0xFFCCFBF1)

val TertiaryDark = Color(0xFFA78BFA)
val OnTertiaryDark = Color(0xFF2E1065)
val TertiaryContainerDark = Color(0xFF4C1D95)
val OnTertiaryContainerDark = Color(0xFFEDE9FE)

val BackgroundDark = Color(0xFF0A0F1A)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF141C2B)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF1E293B)
val OnSurfaceVariantDark = Color(0xFFBFCAD9)
val OutlineDark = Color(0xFF5A6A80)
val OutlineVariantDark = Color(0xFF2C3A4F)
val ErrorSchemeDark = Color(0xFFF87171)
val OnErrorSchemeDark = Color(0xFF450A0A)
val ErrorSchemeContainerDark = Color(0xFF7F1D1D)
val OnErrorSchemeContainerDark = Color(0xFFFEE2E2)

// ---------------------------------------------------------------- Semantic status colours
// Screens must read these through MaterialTheme.appColors rather than branching on
// isSystemInDarkTheme() themselves, which is how the palette drifted between screens before.
val SuccessColorLight = Color(0xFF15803D)
val SuccessContainerLight = Color(0xFFDCFCE7)
val OnSuccessContainerLight = Color(0xFF14532D)
val SuccessColorDark = Color(0xFF4ADE80)
val SuccessContainerDark = Color(0xFF0B3D2A)
val OnSuccessContainerDark = Color(0xFFBBF7D0)

val ErrorColorLight = Color(0xFFDC2626)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)
val ErrorColorDark = Color(0xFFF87171)
val ErrorContainerDark = Color(0xFF4C1111)
val OnErrorContainerDark = Color(0xFFFECACA)

val WarningColorLight = Color(0xFFB45309)
val WarningContainerLight = Color(0xFFFEF3C7)
val OnWarningContainerLight = Color(0xFF78350F)
val WarningColorDark = Color(0xFFFBBF24)
val WarningContainerDark = Color(0xFF422006)
val OnWarningContainerDark = Color(0xFFFDE68A)

val InfoColorLight = Color(0xFF2563EB)
val InfoContainerLight = Color(0xFFDBEAFE)
val OnInfoContainerLight = Color(0xFF1E3A8A)
val InfoColorDark = Color(0xFF7BA9FB)
val InfoContainerDark = Color(0xFF13233F)
val OnInfoContainerDark = Color(0xFFDBEAFE)

val NeutralContainerLight = Color(0xFFEEF2F7)
val OnNeutralContainerLight = Color(0xFF475569)
val NeutralContainerDark = Color(0xFF1E293B)
val OnNeutralContainerDark = Color(0xFFBFCAD9)

/** Accent used for the Google sign-in surface in both themes. */
val GoogleSurfaceLight = Color(0xFFFFFFFF)
val GoogleSurfaceDark = Color(0xFF1B2537)
