package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
