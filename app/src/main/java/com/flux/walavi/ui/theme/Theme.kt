package com.flux.walavi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WalaviColors {
    val Bg = Color(0xFF0D0D0D)
    val Sidebar = Color(0xFF111111)
    val Panel = Color(0xFF171717)
    val Border = Color(0xFF292929)
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFF929292)
    val Btn = Color(0xFFF5F5F5)
    val BtnText = Color(0xFF111111)

    val Green = Color(0xFF4ADE80)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF87171)
    val Blue = Color(0xFF60A5FA)
}

private val GraphiteScheme = darkColorScheme(
    primary = WalaviColors.Btn,
    onPrimary = WalaviColors.BtnText,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = WalaviColors.Text,
    secondary = WalaviColors.Muted,
    onSecondary = WalaviColors.Bg,
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = WalaviColors.Text,
    tertiary = WalaviColors.Blue,
    onTertiary = WalaviColors.Bg,
    background = WalaviColors.Bg,
    onBackground = WalaviColors.Text,
    surface = WalaviColors.Panel,
    onSurface = WalaviColors.Text,
    surfaceVariant = WalaviColors.Sidebar,
    onSurfaceVariant = WalaviColors.Muted,
    surfaceTint = WalaviColors.Text,
    inverseSurface = WalaviColors.Text,
    inverseOnSurface = WalaviColors.Bg,
    error = WalaviColors.Red,
    onError = WalaviColors.Bg,
    errorContainer = Color(0xFF2A1515),
    onErrorContainer = WalaviColors.Red,
    outline = WalaviColors.Border,
    outlineVariant = Color(0xFF1F1F1F),
    scrim = Color(0xFF000000),
    surfaceContainer = WalaviColors.Panel,
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceContainerLow = WalaviColors.Sidebar,
    surfaceContainerLowest = WalaviColors.Bg,
)

val Mono = FontFamily.Monospace

private val WalaviTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
)

private val WalaviShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun WalaviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraphiteScheme,
        typography = WalaviTypography,
        shapes = WalaviShapes,
        content = content,
    )
}
