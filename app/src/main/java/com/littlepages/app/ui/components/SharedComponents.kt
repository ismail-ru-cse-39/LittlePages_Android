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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlepages.app.ui.AppText
import com.littlepages.app.ui.Book
import com.littlepages.app.ui.theme.PagesColors
import com.littlepages.app.ui.theme.PagesShapes
import com.littlepages.app.ui.theme.PagesSpacing

// Shared layout and design-system components.
@Composable
internal fun MainPage(title: String, subtitle: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagesSpacing.ScreenHorizontal)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { AppText(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink); AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            action?.invoke()
        }
        Spacer(Modifier.height(18.dp)); content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun PageScaffold(onBack: () -> Unit, title: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagesSpacing.ScreenHorizontal)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PagesColors.Ink) }
            AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            action?.invoke()
        }
        content(); Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun FormPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PageScaffold(onBack, "Account") {
        AppText(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        AppText(subtitle, color = PagesColors.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        content()
    }
}

@Composable
internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) { AppText("📖", fontSize = 28.sp); AppText(" LittlePages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink) }
}

@Composable
internal fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(PagesShapes.Control), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest), contentPadding = PaddingValues(horizontal = PagesSpacing.Medium, vertical = 13.dp)) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)) }
        AppText(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun OutlineButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(PagesShapes.Control), contentPadding = PaddingValues(horizontal = PagesSpacing.Medium, vertical = 12.dp)) {
        if (icon != null) { Icon(icon, null, tint = PagesColors.Forest, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)) }
        AppText(text, color = PagesColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line); AppText("  or  ", color = PagesColors.Muted); HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line) }
}

@Composable
internal fun SearchBarCard(onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Search, null, tint = PagesColors.Muted); AppText("  Search books, characters, or topics", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium) } }
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (action != null) TextButton(onClick = { onClick?.invoke() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { AppText(action, color = PagesColors.Forest, style = MaterialTheme.typography.labelLarge) } }
}

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, color: Color = PagesColors.Paper, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(PagesShapes.Card), tonalElevation = 1.dp) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
internal fun ChipRow(labels: List<String>, selectedIndex: Int = 0, onSelect: ((Int) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            if (onSelect == null) {
                Surface(shape = CircleShape, color = if (index == 0) PagesColors.Forest else PagesColors.Sage) { AppText(label, color = if (index == 0) Color.White else PagesColors.Ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
            } else {
                FilterChip(selected = index == selectedIndex, onClick = { onSelect(index) }, label = { AppText(label, style = MaterialTheme.typography.labelMedium) })
            }
        }
    }
}

@Composable
internal fun BookCover(book: Book, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(book.tint, PagesColors.Cream))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { AppText(book.emoji, fontSize = 44.sp); AppText(book.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) }
    }
}

@Composable
internal fun BookRow(items: List<Book>, onBook: (Book) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { book -> Column(Modifier.width(142.dp).clickable { onBook(book) }) { BookCover(book, Modifier.fillMaxWidth().height(150.dp)); AppText(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp)); AppText(book.age, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
internal fun BookListRow(book: Book, trailing: String? = null, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(72.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) { AppText(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); AppText("${book.author} · ${book.age}", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); AppText("⭐ ${book.rating} · English · বাংলা", color = PagesColors.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
            if (trailing != null) AppText(trailing, color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun Avatar(initial: String) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(PagesColors.Peach), contentAlignment = Alignment.Center) { AppText(initial, color = PagesColors.Ink, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun GroupTile(title: String, members: String, emoji: String, onClick: () -> Unit) {
    CardSurface(Modifier.width(174.dp).clickable(onClick = onClick), color = PagesColors.Sky) { AppText(emoji, fontSize = 26.sp); AppText(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)); AppText(members, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); AppText("Join group →", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
internal fun CommunityPost(name: String, time: String, body: String, photo: String, onClick: (() -> Unit)?) {
    var liked by remember(name, time, body) { mutableStateOf(false) }
    var likeCount by remember(name, time, body) { mutableIntStateOf(24) }
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(name.first().toString()); Column(Modifier.padding(start = 10.dp).weight(1f)) { AppText(name, fontWeight = FontWeight.Bold); AppText(time, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Filled.MoreHoriz, "Post options", tint = PagesColors.Muted) }
        AppText(body, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        if (photo.isNotBlank()) Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(132.dp).clip(RoundedCornerShape(16.dp)).background(PagesColors.Sage), contentAlignment = Alignment.Center) { AppText(photo, fontSize = 56.sp) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText(if (liked) "♥  $likeCount" else "♡  $likeCount", modifier = Modifier.clickable { liked = !liked; likeCount += if (liked) 1 else -1 }, color = if (liked) PagesColors.Forest else PagesColors.Muted)
            val commentsModifier = Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            AppText("    ◯  8 comments", modifier = commentsModifier, color = PagesColors.Muted)
            AppText("Share", color = PagesColors.Forest)
        }
    }
}

@Composable
internal fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { AppText(title, fontWeight = FontWeight.SemiBold); if (subtitle.isNotBlank()) AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Muted) }
    }
}

@Composable
internal fun BenefitRow(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = PagesColors.Forest, modifier = Modifier.size(22.dp)); AppText(text, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Medium) }
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = PagesColors.Forest, modifier = Modifier.size(36.dp))
            AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            AppText(message, style = MaterialTheme.typography.bodyMedium, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { AppText(actionLabel, color = PagesColors.Forest) }
        }
    }
}

@Composable
internal fun FormatChoice(title: String, price: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = if (selected) PagesColors.Sage else PagesColors.Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = PagesColors.Forest)); Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { AppText(title, fontWeight = FontWeight.Bold); AppText(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText(price, fontWeight = FontWeight.Bold, color = PagesColors.Forest) }
    }
}
