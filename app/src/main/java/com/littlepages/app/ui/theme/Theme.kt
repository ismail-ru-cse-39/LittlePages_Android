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
    // Keep rating/award icons readable against the light paper surfaces.
    val Gold = Color(0xFF805000)
    val Error = Color(0xFF9B4740)
}

object PagesSpacing {
    val None = 0.dp
    val Micro = 2.dp
    val Hairline = 1.dp
    val ExtraSmall = 4.dp
    val Dense = 6.dp
    val Small = 8.dp
    val ControlGap = 10.dp
    val Compact = 12.dp
    val CardContent = 14.dp
    val Medium = 16.dp
    val SectionGap = 18.dp
    val Relaxed = 20.dp
    val Large = 24.dp
    val LargeSectionGap = 28.dp
    val ExtraLarge = 32.dp
    val ButtonVerticalPadding = 13.dp
    val ButtonIconGap = 9.dp
    val ScreenHorizontal = 18.dp
    val NarrowScreenHorizontal = 14.dp
    val WideScreenHorizontal = 28.dp
}

object PagesBreakpoints {
    val CompactWidth = 360.dp
    val ExpandedWidth = 840.dp
    val WelcomeContentMaxWidth = 480.dp
    val CompactHeight = 520.dp
    val ReaderCompactHeight = 480.dp
    val WideLayoutWidth = 600.dp
    val CompactContentMaxWidth = 760.dp
    val ExpandedContentMaxWidth = 960.dp
}

object PagesShapes {
    val Card = 20.dp
    val Control = 16.dp
    val Tag = 18.dp
    val ReaderPage = 22.dp
    val LargeCard = 24.dp
    val Hero = 28.dp
    val Pill = 28.dp
    val Round = 50.dp
}

object PagesSizes {
    val MinimumTouchTarget = 48.dp
    val StandardControlHeight = 52.dp
    val CompactControlHeight = 50.dp
    val IconSmall = 18.dp
    val IconMedium = 20.dp
    val IconLarge = 36.dp
    val ButtonIcon = 19.dp
    val WelcomeArtCompactHeight = 120.dp
    val WelcomeArtHeight = 210.dp
    val ReadAlongIllustrationHeight = 180.dp
    val PhoneCountryCodeWidth = 105.dp
    val HomeBookCoverWidth = 92.dp
    val HomeBookCoverHeight = 108.dp
    val BookDetailCoverWidth = 142.dp
    val BookDetailCoverHeight = 180.dp
    val ReaderContentPadding = 22.dp
    val CatalogueCardWidth = 142.dp
    val CatalogueCoverHeight = 150.dp
    val BookListCover = 72.dp
    val AvatarDiameter = 42.dp
    val GroupCardWidth = 174.dp
    val CommunityImageHeight = 132.dp
    val HomeOfflineCover = 68.dp
    val CheckoutBookCover = 68.dp
    val NarrationButton = 68.dp
    val NarrationPlayIcon = 34.dp
    val PostInputHeight = 190.dp
    val SupportInputHeight = 130.dp
    val BenefitIcon = 22.dp
}

object PagesMotion {
    const val PageTransitionMillis = 180
    const val EmphasisTransitionMillis = 240
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
