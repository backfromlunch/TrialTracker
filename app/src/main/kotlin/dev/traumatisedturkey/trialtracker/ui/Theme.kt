package dev.traumatisedturkey.trialtracker.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.traumatisedturkey.trialtracker.data.ThemeMode

/* --- Colour --- */

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Okabe-Ito colour-blind-safe palette.
val OkabeItoBlue = Color(0xFF0072B2)
val OkabeItoOrange = Color(0xFFE69F00)
val OkabeItoSkyBlue = Color(0xFF56B4E9)

/* --- Type --- */

val Typography =
    Typography(
        bodyLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
    )

/* --- Theme --- */

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

/**
 * Light-background scheme (readability matters more here than in Dark, since this is chosen for
 * accessibility, not aesthetics) with primary/tertiary set to Okabe-Ito blue/orange.
 * onPrimary/onTertiary are white, which has sufficient contrast against both.
 */
private val ColorBlindColorScheme = lightColorScheme(
    primary = OkabeItoBlue,
    onPrimary = Color.White,
    secondary = OkabeItoSkyBlue,
    tertiary = OkabeItoOrange,
    onTertiary = Color.White,
)

/**
 * Three statically-defined themes, chosen explicitly via Settings.themeMode.
 * Colour-blind-friendliness has to be an explicit choice rather than inferred.
 */
private fun colorSchemeFor(themeMode: ThemeMode): ColorScheme = when (themeMode) {
    ThemeMode.LIGHT -> LightColorScheme
    ThemeMode.DARK -> DarkColorScheme
    ThemeMode.COLOR_BLIND -> ColorBlindColorScheme
}

@Composable
fun TrialTrackerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(themeMode),
        typography = Typography,
        content = content,
    )
}
