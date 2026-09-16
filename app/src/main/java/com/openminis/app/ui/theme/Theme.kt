package com.openminis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// LAEP MASTER TASK / DEC-004, user request 2026-09-14: "让它更融入我的 app" —
// re-hued from the original iOS-blue accent (see git history / OPENMINIS_UPSTREAM.md
// for the original rationale, preserved there) onto TeachTrack's own palette
// (`MainActivity.kt`'s `DeepGreen`/`Green`/`Mint`/`Ink`/`AppBackground`), so the
// embedded Agent panel reads as part of TeachTrack rather than a second app
// glued on. Structure (which token is a container/on-container/outline, and
// each token's original lightness/contrast role) is kept identical to the
// original — only hue changes. Names keep the `Teal` prefix only to avoid
// churning 90+ call sites; the value is the contract, not the name.
private val TealPrimary = Color(0xFF176B57) // TeachTrack DeepGreen
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealPrimaryContainer = Color(0xFFDDF5EA) // TeachTrack Mint
private val TealOnPrimaryContainer = Color(0xFF15312A) // TeachTrack Ink
private val TealSecondary = Color(0xFF4A6357)
private val TealOnSecondary = Color(0xFFFFFFFF)
private val TealSecondaryContainer = Color(0xFFCCE8DC)
private val TealOnSecondaryContainer = Color(0xFF05201A)
private val TealTertiary = Color(0xFF46617A)
private val TealOnTertiary = Color(0xFFFFFFFF)
private val TealTertiaryContainer = Color(0xFFCDE5FF)
private val TealOnTertiaryContainer = Color(0xFF001D32)
private val TealBackground = Color(0xFFF4F9F6) // TeachTrack AppBackground
private val TealOnBackground = Color(0xFF15312A) // TeachTrack Ink
private val TealSurface = Color(0xFFF4F9F6)
private val TealOnSurface = Color(0xFF15312A)
private val TealSurfaceVariant = Color(0xFFDAE5DE)
private val TealOnSurfaceVariant = Color(0xFF3F4944)
private val TealOutline = Color(0xFF6F7974)

private val TealDarkPrimary = Color(0xFF6ACEA9)
private val TealDarkOnPrimary = Color(0xFF003729)
private val TealDarkPrimaryContainer = Color(0xFF176B57)
private val TealDarkOnPrimaryContainer = Color(0xFFDDF5EA)
private val TealDarkSecondary = Color(0xFFB1CCBE)
private val TealDarkOnSecondary = Color(0xFF1C352A)
private val TealDarkSecondaryContainer = Color(0xFF334B3F)
private val TealDarkOnSecondaryContainer = Color(0xFFCCE8DC)
private val TealDarkBackground = Color(0xFF0E1512)
private val TealDarkOnBackground = Color(0xFFDEE4E0)
private val TealDarkSurface = Color(0xFF0E1512)
private val TealDarkOnSurface = Color(0xFFDEE4E0)
private val TealDarkSurfaceVariant = Color(0xFF3F4944)
private val TealDarkOnSurfaceVariant = Color(0xFFBEC9C3)
private val TealDarkOutline = Color(0xFF899389)

// Neutral grouped-card surfaces (iOS-style system-grouped background), now
// tinted toward TeachTrack's own AppBackground instead of iOS system gray.
// Light: page = TeachTrack AppBackground, card = white
// Dark:  page = #000, card = #1C1E1C
private val NeutralGroupedBg = Color(0xFFF4F9F6)
private val NeutralGroupedCard = Color(0xFFFFFFFF)
private val NeutralGroupedCardElevated = Color(0xFFF3F9F5)
private val NeutralOutline = Color(0xFFD1D6D2)

private val NeutralDarkGroupedBg = Color(0xFF000000)
private val NeutralDarkGroupedCard = Color(0xFF1C1E1C)
private val NeutralDarkGroupedCardElevated = Color(0xFF2C2E2B)
private val NeutralDarkOutline = Color(0xFF383A37)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = TealTertiary,
    onTertiary = TealOnTertiary,
    tertiaryContainer = TealTertiaryContainer,
    onTertiaryContainer = TealOnTertiaryContainer,
    background = NeutralGroupedBg,
    onBackground = TealOnBackground,
    surface = NeutralGroupedBg,
    onSurface = TealOnSurface,
    surfaceVariant = NeutralGroupedCard,
    onSurfaceVariant = TealOnSurfaceVariant,
    surfaceContainerLowest = NeutralGroupedBg,
    surfaceContainerLow = NeutralGroupedCard,
    surfaceContainer = NeutralGroupedCard,
    surfaceContainerHigh = NeutralGroupedCardElevated,
    surfaceContainerHighest = NeutralGroupedCardElevated,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealDarkPrimary,
    onPrimary = TealDarkOnPrimary,
    primaryContainer = TealDarkPrimaryContainer,
    onPrimaryContainer = TealDarkOnPrimaryContainer,
    secondary = TealDarkSecondary,
    onSecondary = TealDarkOnSecondary,
    secondaryContainer = TealDarkSecondaryContainer,
    onSecondaryContainer = TealDarkOnSecondaryContainer,
    background = NeutralDarkGroupedBg,
    onBackground = TealDarkOnBackground,
    surface = NeutralDarkGroupedBg,
    onSurface = TealDarkOnSurface,
    surfaceVariant = NeutralDarkGroupedCard,
    onSurfaceVariant = TealDarkOnSurfaceVariant,
    surfaceContainerLowest = NeutralDarkGroupedBg,
    surfaceContainerLow = NeutralDarkGroupedCard,
    surfaceContainer = NeutralDarkGroupedCard,
    surfaceContainerHigh = NeutralDarkGroupedCardElevated,
    surfaceContainerHighest = NeutralDarkGroupedCardElevated,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutline,
)

// App-wide FAB accent color (warm beige, matching iOS New Chat button).
// Reads from ChatPalette so it follows the in-app theme override (theme_mode pref),
// not android.isSystemInDarkTheme(), which only tracks the system setting.
@Composable
fun minisFabColor(): Color = LocalChatPalette.current.fabAccent

// App-wide shape system — larger corners for a modern, friendly feel
// DropdownMenu uses extraSmall, Dialog uses extraLarge, BottomSheet uses extraLarge
private val MinisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // DropdownMenu, Tooltip, OutlinedTextField default
    small = RoundedCornerShape(12.dp),        // Chip, TextField
    medium = RoundedCornerShape(20.dp),       // Card, Snackbar
    large = RoundedCornerShape(24.dp),        // NavigationDrawer
    extraLarge = RoundedCornerShape(28.dp),   // Dialog, BottomSheet
)

@Composable
fun MinisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = scaledTypography(fontScale)
    val chatPalette = if (darkTheme) DarkChatPalette else LightChatPalette

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MinisShapes,
        typography = typography,
    ) {
        CompositionLocalProvider(LocalChatPalette provides chatPalette, content = content)
    }
}

private fun TextStyle.scale(factor: Float): TextStyle =
    if (factor == 1f) this else copy(fontSize = fontSize * factor)

private fun scaledTypography(factor: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.scale(factor),
        displayMedium = base.displayMedium.scale(factor),
        displaySmall = base.displaySmall.scale(factor),
        headlineLarge = base.headlineLarge.scale(factor),
        headlineMedium = base.headlineMedium.scale(factor),
        headlineSmall = base.headlineSmall.scale(factor),
        titleLarge = base.titleLarge.scale(factor),
        titleMedium = base.titleMedium.scale(factor),
        titleSmall = base.titleSmall.scale(factor),
        bodyLarge = base.bodyLarge.scale(factor),
        bodyMedium = base.bodyMedium.scale(factor),
        bodySmall = base.bodySmall.scale(factor),
        labelLarge = base.labelLarge.scale(factor),
        labelMedium = base.labelMedium.scale(factor),
        labelSmall = base.labelSmall.scale(factor),
    )
}
