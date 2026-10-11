package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Light Theme Colors
val CleanBgLight = Color(0xFFF4F5F8)
val CleanSurfaceLight = Color(0xFFFFFFFF)
val CleanTextLight = Color(0xFF1A1C20)
val CleanSubTextLight = Color(0xFF646975)
val CleanMutedLight = Color(0xFFA6AAB5)
val BorderLight = Color(0xFFE2E4EB)

// Dark Theme Colors
val CleanBgDark = Color(0xFF1A1A1E)
val CleanSurfaceDark = Color(0xFF222328)
val CleanTextDark = Color(0xFFB5B8C2)
val CleanSubTextDark = Color(0xFF7A7D8A)
val CleanMutedDark = Color(0xFF3B3E48)
val BorderDark = Color(0xFF2B2D35)

// Accents (Warm Peach & Vibrant Coral for Material 3 Expressive high-energy branding)
val AccentPeach = Color(0xFFF7DACF)
val OnAccentPeach = Color(0xFF4A2519)
val AccentPeachActive = Color(0xFFE8BCAE)

// M3 Expressive Vibrant highlights
val AccentCoral = Color(0xFFFF5A43)
val AccentCyberTeal = Color(0xFF14F7D3)
val AppGlowAccent = Color(0xFFFFDAB9)

private val DarkColorScheme =
  darkColorScheme(
    primary = AccentPeach,
    onPrimary = OnAccentPeach,
    primaryContainer = AccentPeach,
    onPrimaryContainer = OnAccentPeach,
    secondary = AccentCoral,
    onSecondary = Color.White,
    tertiary = AccentCyberTeal,
    onTertiary = Color(0xFF00372D),
    background = CleanBgDark,
    onBackground = CleanTextDark,
    surface = CleanSurfaceDark,
    onSurface = CleanTextDark,
    surfaceVariant = CleanBgDark,
    onSurfaceVariant = CleanSubTextDark,
    surfaceContainer = CleanSurfaceDark,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = BorderDark,
    outlineVariant = CleanMutedDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = OnAccentPeach, // Distinctive high-contrast dark peach for primary elements in light mode
    onPrimary = AccentPeach,
    primaryContainer = AccentPeach,
    onPrimaryContainer = OnAccentPeach,
    secondary = AccentCoral,
    onSecondary = Color.White,
    tertiary = AccentCyberTeal,
    onTertiary = Color(0xFF00372D),
    background = CleanBgLight,
    onBackground = CleanTextLight,
    surface = CleanSurfaceLight,
    onSurface = CleanTextLight,
    surfaceVariant = CleanBgLight,
    onSurfaceVariant = CleanSubTextLight,
    surfaceContainer = CleanSurfaceLight,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    outline = BorderLight,
    outlineVariant = CleanMutedLight
  )

val Typography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.Serif,
      fontWeight = FontWeight.Black,
      fontSize = 57.sp,
      lineHeight = 64.sp,
      letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
      fontFamily = FontFamily.Serif,
      fontWeight = FontWeight.Black,
      fontSize = 45.sp,
      lineHeight = 52.sp,
      letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
      fontFamily = FontFamily.Serif,
      fontWeight = FontWeight.Bold,
      fontSize = 36.sp,
      lineHeight = 44.sp,
      letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 32.sp,
      lineHeight = 40.sp,
      letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 28.sp,
      lineHeight = 36.sp,
      letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 24.sp,
      lineHeight = 32.sp,
      letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      lineHeight = 18.sp,
      letterSpacing = 0.5.sp,
    )
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Enforce bespoke Minimalist aesthetic directly
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
