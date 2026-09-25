package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Status colours that Material's own scheme does not define.
 *
 * Every screen used to compute these inline with `if (isSystemInDarkTheme())`, which meant the
 * same "warning" was a different colour on three different screens. They are tokens now.
 */
@Immutable
data class AppColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val googleSurface: Color
)

val LightAppColors = AppColors(
    success = SuccessColorLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = OnSuccessContainerLight,
    danger = ErrorColorLight,
    dangerContainer = ErrorContainerLight,
    onDangerContainer = OnErrorContainerLight,
    warning = WarningColorLight,
    warningContainer = WarningContainerLight,
    onWarningContainer = OnWarningContainerLight,
    info = InfoColorLight,
    infoContainer = InfoContainerLight,
    onInfoContainer = OnInfoContainerLight,
    neutralContainer = NeutralContainerLight,
    onNeutralContainer = OnNeutralContainerLight,
    googleSurface = GoogleSurfaceLight
)

val DarkAppColors = AppColors(
    success = SuccessColorDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = OnSuccessContainerDark,
    danger = ErrorColorDark,
    dangerContainer = ErrorContainerDark,
    onDangerContainer = OnErrorContainerDark,
    warning = WarningColorDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = OnWarningContainerDark,
    info = InfoColorDark,
    infoContainer = InfoContainerDark,
    onInfoContainer = OnInfoContainerDark,
    neutralContainer = NeutralContainerDark,
    onNeutralContainer = OnNeutralContainerDark,
    googleSurface = GoogleSurfaceDark
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** `MaterialTheme.appColors.success` reads like the rest of the design system. */
val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
