package it.fast4x.opentv.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette "Dark TV" — stessa identità visiva dell'app Android
// (it/fast4x/opentv/ui/theme/Color.kt).
val AppBackground = Color(0xFF0A0D14)
val AppSurface = Color(0xFF131926)
val AppSurfaceHigh = Color(0xFF1D2736)
val AppPrimary = Color(0xFF4D9FFF)
val AppPrimaryDim = Color(0xFF2C5E9E)
val AppAccent = Color(0xFF35D0A5)
val AppTextPrimary = Color(0xFFF2F6FC)
val AppTextSecondary = Color(0xFF93A0B4)
val AppFavorite = Color(0xFFFFC94D)
val AppError = Color(0xFFFF6B6B)
val AppGeoBlocked = Color(0xFFFF8A3D)

@Composable
fun OpenTvDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppPrimary,
            secondary = AppAccent,
            background = AppBackground,
            surface = AppSurface,
            surfaceVariant = AppSurfaceHigh,
            onPrimary = Color.Black,
            onBackground = AppTextPrimary,
            onSurface = AppTextPrimary,
            onSurfaceVariant = AppTextSecondary,
            error = AppError,
        ),
        content = content,
    )
}
