package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CommuteLightColorScheme = lightColorScheme(
    primary = PrimaryInk,
    onPrimary = PureWhite,
    primaryContainer = PureWhite,
    onPrimaryContainer = PrimaryInk,
    secondary = SoftViolet,
    onSecondary = PureWhite,
    secondaryContainer = VioletMuted,
    onSecondaryContainer = SoftViolet,
    tertiary = MutedMint,
    onTertiary = PureWhite,
    tertiaryContainer = MintMuted,
    onTertiaryContainer = MutedMint,
    background = LightCanvas,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SoftMist,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassBorderStrong,
    error = DisruptionCoral,
    onError = PureWhite
)

val CommuteShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CommuteBuddyTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = CommuteLightColorScheme,
        typography = CommuteTypography,
        shapes = CommuteShapes,
        content = content
    )
}
