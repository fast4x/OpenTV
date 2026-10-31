package it.fast4x.opentv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkTvColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    primaryContainer = AppPrimaryDim,
    onPrimaryContainer = AppTextPrimary,
    secondary = AppAccent,
    onSecondary = Color(0xFF04120E),
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceHigh,
    onSurfaceVariant = AppTextSecondary,
    error = AppError,
    onError = Color(0xFF140404),
)

/** Tema scuro fisso: l'app è pensata per la visione TV notturna. */
@Composable
fun OpenTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkTvColorScheme,
        typography = OpenTvTypography,
        content = content,
    )
}
