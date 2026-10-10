package com.littlepages.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    val Muted = Color(0xFF738078)
    val Line = Color(0xFFE8E5DC)
    val Gold = Color(0xFFD49A38)
    val Error = Color(0xFF9B4740)
}

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
        typography = MaterialTheme.typography,
        content = content
    )
}
