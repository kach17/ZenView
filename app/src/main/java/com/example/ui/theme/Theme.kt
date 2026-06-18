package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

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
