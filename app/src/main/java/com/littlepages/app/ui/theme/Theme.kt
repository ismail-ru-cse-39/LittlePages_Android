package com.littlepages.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PagesColors {
    val Ink = Color(0xFF23392F)
    val Forest = Color(0xFF28644F)
    val ForestDark = Color(0xFF1E4F3E)
    val Sage = Color(0xFFE5EBDD)
    val SageDeep = Color(0xFFB7C9B2)
    val Cream = Color(0xFFFBF8F0)
    val Paper = Color(0xFFFFFEFB)
    val Peach = Color(0xFFF7E2CF)
    val Sky = Color(0xFFE4EFF2)
    val Muted = Color(0xFF59675E)
    val Line = Color(0xFFE8E5DC)
    val Gold = Color(0xFFD49A38)
    val Error = Color(0xFF9B4740)
}

object PagesSpacing {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ScreenHorizontal = 18.dp
}

object PagesShapes {
    val Card = 20.dp
    val Control = 16.dp
}

private val PagesTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
)

private val LightColors = lightColorScheme(
    primary = PagesColors.Forest,
    onPrimary = Color.White,
    secondary = PagesColors.SageDeep,
    onSecondary = PagesColors.Ink,
    background = PagesColors.Cream,
    onBackground = PagesColors.Ink,
    surface = PagesColors.Paper,
    onSurface = PagesColors.Ink,
    surfaceVariant = PagesColors.Sage,
    onSurfaceVariant = PagesColors.Muted,
    outline = PagesColors.Line,
    error = PagesColors.Error
)

@Composable
fun LittlePagesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = PagesTypography,
        content = content
    )
}
