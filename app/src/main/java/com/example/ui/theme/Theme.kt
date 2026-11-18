package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MyraDarkColorScheme = darkColorScheme(
    primary = LuminousCyan,
    onPrimary = DeepNavyBg,
    primaryContainer = RoyalPurpleDeep,
    onPrimaryContainer = CyanSoft,
    secondary = ElectricViolet,
    onSecondary = DeepNavyBg,
    secondaryContainer = ColorTokens.PurpleContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldPulse,
    onTertiary = DeepNavyBg,
    background = DeepNavyBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassCardBg,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = RoseAlert,
    onError = DeepNavyBg
)

private object ColorTokens {
    val PurpleContainer = androidx.compose.ui.graphics.Color(0xFF33186B)
}

private val MyraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyraAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MyraDarkColorScheme,
        typography = Typography,
        shapes = MyraShapes,
        content = content
    )
}
