package com.littlepages.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.littlepages.app.ui.AppText
import com.littlepages.app.ui.Book
import com.littlepages.app.ui.theme.PagesColors
import com.littlepages.app.ui.theme.PagesBreakpoints
import com.littlepages.app.ui.theme.PagesSizes
import com.littlepages.app.ui.theme.PagesShapes
import com.littlepages.app.ui.theme.PagesSpacing

// Shared layout and design-system components.
@Composable
internal fun MainPage(title: String, subtitle: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePadding = if (maxWidth < PagesBreakpoints.CompactWidth) PagesSpacing.NarrowScreenHorizontal else if (maxWidth >= PagesBreakpoints.ExpandedWidth) PagesSpacing.WideScreenHorizontal else PagesSpacing.ScreenHorizontal
        val contentMaxWidth = if (maxWidth >= PagesBreakpoints.ExpandedWidth) PagesBreakpoints.ExpandedContentMaxWidth else PagesBreakpoints.CompactContentMaxWidth
        Column(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal = sidePadding)) {
            Row(Modifier.fillMaxWidth().padding(top = PagesSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { AppText(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink); AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = PagesSpacing.ExtraSmall)) }
                action?.invoke()
            }
            Spacer(Modifier.height(PagesSpacing.Medium)); content()
            Spacer(Modifier.height(PagesSpacing.Relaxed))
        }
    }
}

@Composable
internal fun PageScaffold(onBack: () -> Unit, title: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePadding = if (maxWidth < PagesBreakpoints.CompactWidth) PagesSpacing.NarrowScreenHorizontal else if (maxWidth >= PagesBreakpoints.ExpandedWidth) PagesSpacing.WideScreenHorizontal else PagesSpacing.ScreenHorizontal
        val contentMaxWidth = if (maxWidth >= PagesBreakpoints.ExpandedWidth) PagesBreakpoints.ExpandedContentMaxWidth else PagesBreakpoints.CompactContentMaxWidth
        Column(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal = sidePadding)) {
            Row(Modifier.fillMaxWidth().padding(top = PagesSpacing.ExtraSmall, bottom = PagesSpacing.Compact), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(PagesSizes.MinimumTouchTarget)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PagesColors.Ink) }
                AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                action?.invoke()
            }
            content(); Spacer(Modifier.height(PagesSpacing.Large))
        }
    }
}

@Composable
internal fun FormPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PageScaffold(onBack, "Account") {
        AppText(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        AppText(subtitle, color = PagesColors.Muted, modifier = Modifier.padding(top = PagesSpacing.Dense, bottom = PagesSpacing.Large))
        content()
    }
}

@Composable
internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) { AppText("📖", fontSize = 28.sp); AppText(" LittlePages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink) }
}

@Composable
internal fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = PagesSizes.StandardControlHeight), shape = RoundedCornerShape(PagesShapes.Control), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest), contentPadding = PaddingValues(horizontal = PagesSpacing.Medium, vertical = PagesSpacing.ButtonVerticalPadding)) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(PagesSizes.ButtonIcon)); Spacer(Modifier.width(PagesSpacing.ButtonIconGap)) }
        AppText(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun OutlineButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = PagesSizes.CompactControlHeight), shape = RoundedCornerShape(PagesShapes.Control), contentPadding = PaddingValues(horizontal = PagesSpacing.Medium, vertical = PagesSpacing.Compact)) {
        if (icon != null) { Icon(icon, null, tint = PagesColors.Forest, modifier = Modifier.size(PagesSizes.ButtonIcon)); Spacer(Modifier.width(PagesSpacing.ButtonIconGap)) }
        AppText(text, color = PagesColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = PagesSpacing.Compact), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line); AppText("  or  ", color = PagesColors.Muted); HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line) }
}

@Composable
internal fun SearchBarCard(onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Search, null, tint = PagesColors.Muted); AppText("  Search books, characters, or topics", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium) } }
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = PagesSpacing.ExtraSmall, bottom = PagesSpacing.Small), verticalAlignment = Alignment.CenterVertically) { AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (action != null) TextButton(onClick = { onClick?.invoke() }, contentPadding = PaddingValues(horizontal = PagesSpacing.Small, vertical = PagesSpacing.None)) { AppText(action, color = PagesColors.Forest, style = MaterialTheme.typography.labelLarge) } }
}

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, color: Color = PagesColors.Paper, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(PagesShapes.Card), tonalElevation = PagesSpacing.Hairline) { Column(Modifier.padding(PagesSpacing.CardContent), content = content) }
}

@Composable
internal fun ChipRow(labels: List<String>, selectedIndex: Int = 0, onSelect: ((Int) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Small)) {
        labels.forEachIndexed { index, label ->
            if (onSelect == null) {
                Surface(shape = CircleShape, color = if (index == 0) PagesColors.Forest else PagesColors.Sage) { AppText(label, color = if (index == 0) Color.White else PagesColors.Ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = PagesSpacing.CardContent, vertical = PagesSpacing.Small)) }
            } else {
                FilterChip(selected = index == selectedIndex, onClick = { onSelect(index) }, label = { AppText(label, style = MaterialTheme.typography.labelMedium) })
            }
        }
    }
}

@Composable
internal fun BookCover(book: Book, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(PagesShapes.Control)).background(Brush.verticalGradient(listOf(book.tint, PagesColors.Cream))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { AppText(book.emoji, fontSize = 44.sp); AppText(book.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, modifier = Modifier.padding(horizontal = PagesSpacing.Small, vertical = PagesSpacing.ExtraSmall)) }
    }
}

@Composable
internal fun BookRow(items: List<Book>, onBook: (Book) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Compact)) {
        items.forEach { book -> Column(Modifier.width(PagesSizes.CatalogueCardWidth).clickable { onBook(book) }) { BookCover(book, Modifier.fillMaxWidth().height(PagesSizes.CatalogueCoverHeight)); AppText(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = PagesSpacing.Small)); AppText(book.age, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
internal fun BookListRow(book: Book, trailing: String? = null, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = PagesSpacing.Small).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(PagesSizes.BookListCover))
            Column(Modifier.weight(1f).padding(start = PagesSpacing.Compact)) { AppText(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); AppText("${book.author} · ${book.age}", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); AppText("⭐ ${book.rating} · English · বাংলা", color = PagesColors.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = PagesSpacing.ExtraSmall)) }
            if (trailing != null) AppText(trailing, color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun Avatar(initial: String) {
    Box(Modifier.size(PagesSizes.AvatarDiameter).clip(CircleShape).background(PagesColors.Peach), contentAlignment = Alignment.Center) { AppText(initial, color = PagesColors.Ink, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun GroupTile(title: String, members: String, emoji: String, onClick: () -> Unit) {
    CardSurface(Modifier.width(PagesSizes.GroupCardWidth).clickable(onClick = onClick), color = PagesColors.Sky) { AppText(emoji, fontSize = 26.sp); AppText(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = PagesSpacing.Dense)); AppText(members, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); AppText("Join group →", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = PagesSpacing.Dense)) }
}

@Composable
internal fun CommunityPost(name: String, time: String, body: String, photo: String, onClick: (() -> Unit)?) {
    var liked by remember(name, time, body) { mutableStateOf(false) }
    var likeCount by remember(name, time, body) { mutableIntStateOf(24) }
    CardSurface(Modifier.fillMaxWidth().padding(bottom = PagesSpacing.ControlGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(name.first().toString()); Column(Modifier.padding(start = PagesSpacing.ControlGap).weight(1f)) { AppText(name, fontWeight = FontWeight.Bold); AppText(time, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Filled.MoreHoriz, "Post options", tint = PagesColors.Muted) }
        AppText(body, modifier = Modifier.padding(top = PagesSpacing.Compact), style = MaterialTheme.typography.bodyMedium)
        if (photo.isNotBlank()) Box(Modifier.fillMaxWidth().padding(top = PagesSpacing.ControlGap).height(PagesSizes.CommunityImageHeight).clip(RoundedCornerShape(PagesShapes.Control)).background(PagesColors.Sage), contentAlignment = Alignment.Center) { AppText(photo, fontSize = 56.sp) }
        Row(Modifier.padding(top = PagesSpacing.ControlGap), verticalAlignment = Alignment.CenterVertically) {
            AppText(if (liked) "♥  $likeCount" else "♡  $likeCount", modifier = Modifier.clickable { liked = !liked; likeCount += if (liked) 1 else -1 }, color = if (liked) PagesColors.Forest else PagesColors.Muted)
            val commentsModifier = Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            AppText("    ◯  8 comments", modifier = commentsModifier, color = PagesColors.Muted)
            AppText("Share", color = PagesColors.Forest)
        }
    }
}

@Composable
internal fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = PagesSpacing.Small).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.Compact)) { AppText(title, fontWeight = FontWeight.SemiBold); if (subtitle.isNotBlank()) AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Muted) }
    }
}

@Composable
internal fun BenefitRow(text: String) {
    Row(Modifier.padding(vertical = PagesSpacing.ExtraSmall), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = PagesColors.Forest, modifier = Modifier.size(PagesSizes.BenefitIcon)); AppText(text, modifier = Modifier.padding(start = PagesSpacing.ControlGap), fontWeight = FontWeight.Medium) }
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) {
        Column(Modifier.fillMaxWidth().padding(vertical = PagesSpacing.SectionGap), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = PagesColors.Forest, modifier = Modifier.size(PagesSizes.IconLarge))
            AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = PagesSpacing.ControlGap))
            AppText(message, style = MaterialTheme.typography.bodyMedium, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = PagesSpacing.Dense))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = PagesSpacing.ExtraSmall)) { AppText(actionLabel, color = PagesColors.Forest) }
        }
    }
}

@Composable
internal fun FormatChoice(title: String, price: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = if (selected) PagesColors.Sage else PagesColors.Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = PagesColors.Forest)); Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.Small)) { AppText(title, fontWeight = FontWeight.Bold); AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText(price, fontWeight = FontWeight.Bold, color = PagesColors.Forest) }
    }
}
