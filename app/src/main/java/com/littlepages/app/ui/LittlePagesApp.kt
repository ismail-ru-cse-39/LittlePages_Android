package com.littlepages.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.littlepages.app.ui.theme.PagesColors

private enum class Page {
    Welcome, EmailAuth, PhoneAuth, Home, Library, Search, BookDetails, Reader, Listen,
    Community, Group, CreatePost, Reviews, Offline, Parent, Profiles, Subscription,
    Checkout, Orders, Support
}

private data class Book(val title: String, val author: String, val age: String, val emoji: String, val tint: Color, val rating: String = "4.9")
private val books = listOf(
    Book("The Brave Little Fox", "Emma Clarke", "Ages 3–7", "🦊", PagesColors.Sky),
    Book("A Day at the Lake", "Mina Rahman", "Ages 4–8", "🐻", PagesColors.Sage),
    Book("The Kind Giant", "Oliver Reed", "Ages 5–9", "🌿", PagesColors.Peach),
    Book("The Star Keeper", "Nadia Noor", "Ages 4–8", "🌙", Color(0xFFE8E4F3))
)

@Composable
fun LittlePagesApp() {
    val stack = remember { mutableStateListOf(Page.Welcome) }
    var premium by remember { mutableStateOf(false) }
    var premiumPrompt by remember { mutableStateOf(false) }
    var unavailableNotice by remember { mutableStateOf<String?>(null) }
    var selectedBook by remember { mutableStateOf(books.first()) }
    var query by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var autoplay by remember { mutableStateOf(true) }
    var readerMode by remember { mutableStateOf("Page turn") }
    var currentPage by remember { mutableIntStateOf(8) }
    var bookmarked by remember { mutableStateOf(false) }
    val page = stack.last()
    fun go(next: Page) { stack.add(next) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openPremiumFeature() { if (premium) go(Page.Listen) else premiumPrompt = true }

    BackHandler(enabled = stack.size > 1 || premiumPrompt) {
        if (premiumPrompt) premiumPrompt = false else back()
    }

    Scaffold(
        containerColor = PagesColors.Cream,
        bottomBar = {
            if (page in listOf(Page.Home, Page.Library, Page.Community, Page.Parent, Page.Offline)) {
                NavigationBar(containerColor = PagesColors.Paper, tonalElevation = 0.dp) {
                    val items = listOf(
                        Triple(Page.Home, "Home", Icons.Filled.Home),
                        Triple(Page.Library, "Library", Icons.Filled.AutoStories),
                        Triple(Page.Community, "Community", Icons.Filled.Groups),
                        Triple(Page.Parent, "Profile", Icons.Filled.AccountCircle)
                    )
                    items.forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { if (page != destination) { stack.clear(); stack.add(destination) } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = PagesColors.Forest, indicatorColor = PagesColors.Sage)
                        )
                    }
                }
            }
        }
    ) { insets ->
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "page-transition",
            modifier = Modifier.padding(insets)
        ) { target ->
            when (target) {
                Page.Welcome -> WelcomeScreen(
                    onGoogle = { go(Page.Home) }, onEmail = { go(Page.EmailAuth) }, onPhone = { go(Page.PhoneAuth) }, onSignIn = { go(Page.EmailAuth) }
                )
                Page.EmailAuth -> EmailAuthScreen(onBack = ::back, onContinue = { go(Page.Home) }, onGoogle = { go(Page.Home) }, onPhone = { go(Page.PhoneAuth) })
                Page.PhoneAuth -> PhoneAuthScreen(onBack = ::back, onContinue = { go(Page.Home) }, onEmail = { go(Page.EmailAuth) })
                Page.Home -> HomeScreen(onBook = { selectedBook = it; go(Page.BookDetails) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) }, onPremium = { go(Page.Subscription) })
                Page.Library -> LibraryScreen(onBook = { selectedBook = it; go(Page.BookDetails) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) })
                Page.Search -> SearchScreen(query = query, onQuery = { query = it }, onBook = { selectedBook = it; go(Page.BookDetails) }, onBack = ::back)
                Page.BookDetails -> BookDetailsScreen(book = selectedBook, onBack = ::back, onRead = { go(Page.Reader) }, onListen = ::openPremiumFeature, onReviews = { go(Page.Reviews) }, onOrder = { go(Page.Checkout) })
                Page.Reader -> ReaderScreen(book = selectedBook, mode = readerMode, page = currentPage, bookmarked = bookmarked, onBookmark = { bookmarked = !bookmarked }, onPage = { currentPage = it }, onMode = { readerMode = it }, onBack = ::back, onListen = ::openPremiumFeature, onPremium = { premiumPrompt = true })
                Page.Listen -> ListenScreen(book = selectedBook, autoplay = autoplay, onAutoplay = { autoplay = it }, onBack = ::back)
                Page.Community -> CommunityScreen(onGroup = { go(Page.Group) }, onCreate = { go(Page.CreatePost) })
                Page.Group -> GroupScreen(onBack = ::back, onCreatePost = { go(Page.CreatePost) })
                Page.CreatePost -> CreatePostScreen(onBack = ::back, onPost = ::back)
                Page.Reviews -> ReviewsScreen(book = selectedBook, onBack = ::back)
                Page.Offline -> OfflineScreen(onBack = ::back, onBook = { selectedBook = it; go(Page.Reader) })
                Page.Parent -> ParentScreen(language = selectedLanguage, onLanguage = { selectedLanguage = it }, onProfiles = { go(Page.Profiles) }, onSubscription = { go(Page.Subscription) }, onOrders = { go(Page.Orders) }, onSupport = { go(Page.Support) }, onOffline = { go(Page.Offline) })
                Page.Profiles -> ProfilesScreen(onBack = ::back)
                Page.Subscription -> SubscriptionScreen(premium = premium, onBack = ::back, onSubscribe = { unavailableNotice = "Subscription checkout is not connected yet. Premium access will only activate after a verified payment." })
                Page.Checkout -> CheckoutScreen(book = selectedBook, onBack = ::back, onComplete = { unavailableNotice = "Checkout is not connected yet. No payment or order has been placed." })
                Page.Orders -> OrdersScreen(onBack = ::back)
                Page.Support -> SupportScreen(onBack = ::back)
            }
        }

        if (premiumPrompt) {
            PremiumDialog(onDismiss = { premiumPrompt = false }, onSubscribe = { premiumPrompt = false; go(Page.Subscription) })
        }
        unavailableNotice?.let { message ->
            AlertDialog(onDismissRequest = { unavailableNotice = null }, title = { Text("Not available yet") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { unavailableNotice = null }) { Text("OK") } })
        }
    }
}

@Composable
private fun WelcomeScreen(onGoogle: () -> Unit, onEmail: () -> Unit, onPhone: () -> Unit, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(34.dp))
        BrandMark()
        Spacer(Modifier.height(16.dp))
        Text("A little world\nof stories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, lineHeight = 42.sp)
        Text("A parent account for your family’s reading journey", style = MaterialTheme.typography.bodyLarge, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Cream))), contentAlignment = Alignment.Center) {
            Text("🌿     🐰📖🐰     🌼", fontSize = 38.sp)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Continue with phone", Icons.Filled.Phone, onPhone)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account? ", color = PagesColors.Muted)
            Text("Sign in", color = PagesColors.Forest, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onSignIn))
        }
        Text("By continuing, you agree to our Terms and Privacy Policy.", style = MaterialTheme.typography.labelSmall, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
    }
}

@Composable
private fun EmailAuthScreen(onBack: () -> Unit, onContinue: () -> Unit, onGoogle: () -> Unit, onPhone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    FormPage(title = "Welcome back", subtitle = "Sign in or create your parent account.", onBack = onBack) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Filled.Email, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, trailingIcon = { Icon(Icons.Filled.Visibility, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        Text("Forgot password?", color = PagesColors.Forest, modifier = Modifier.align(Alignment.End).clickable { })
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue", null, onContinue)
        OrDivider()
        OutlineButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Use phone instead", Icons.Filled.Phone, onPhone)
    }
}

@Composable
private fun PhoneAuthScreen(onBack: () -> Unit, onContinue: () -> Unit, onEmail: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    FormPage(title = "Continue with phone", subtitle = "We’ll text a one-time code to verify your number.", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = "+880", onValueChange = {}, label = { Text("Code") }, modifier = Modifier.width(105.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Send verification code", Icons.Filled.Sms, onContinue)
        Text("We use your number only to secure your account.", color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(14.dp))
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
    }
}

@Composable
private fun HomeScreen(onBook: (Book) -> Unit, onSearch: () -> Unit, onOffline: () -> Unit, onPremium: () -> Unit) {
    MainPage(title = "Good morning, Emma 👋", subtitle = "Ready for a new adventure?") {
        SearchBarCard(onSearch)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("Continue reading"); Text("The Brave Little Fox · 8 of 24 pages", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }
            Text("Open", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBook(books[0]) })
        }
        Spacer(Modifier.height(8.dp))
        CardSurface(Modifier.fillMaxWidth().clickable { onBook(books[0]) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(books[0], Modifier.size(width = 92.dp, height = 108.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(books[0].title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("You’re doing great!", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { .34f }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = PagesColors.Forest, trackColor = PagesColors.Sage)
                    Text("34% complete", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Popular this week", "See all", onClick = { onBook(books[1]) })
        BookRow(books, onBook)
        Spacer(Modifier.height(16.dp))
        CardSurface(Modifier.fillMaxWidth().clickable(onClick = onPremium), color = PagesColors.Sage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎧", fontSize = 28.sp); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Listen and read along", fontWeight = FontWeight.Bold); Text("Words light up as stories are read", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Forest)
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Your offline books", "See all", onClick = onOffline)
        Text("2 stories ready for a trip", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted)
    }
}

@Composable
private fun LibraryScreen(onBook: (Book) -> Unit, onSearch: () -> Unit, onOffline: () -> Unit) {
    MainPage(title = "Book library", subtitle = "Find a story for every little mood.") {
        SearchBarCard(onSearch)
        Spacer(Modifier.height(14.dp))
        ChipRow(listOf("All", "Animals", "Friendship", "Nature", "Bedtime", "Bangla"))
        Spacer(Modifier.height(20.dp))
        SectionTitle("Browse by age")
        ChipRow(listOf("Ages 0–3", "Ages 4–6", "Ages 7–9"))
        Spacer(Modifier.height(18.dp))
        SectionTitle("All stories", "Offline", onClick = onOffline)
        books.forEach { book -> BookListRow(book, onClick = { onBook(book) }) }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, onBook: (Book) -> Unit, onBack: () -> Unit) {
    PageScaffold(onBack, "Search books") {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Title, character, or topic") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true)
        Spacer(Modifier.height(16.dp)); SectionTitle("Suggested for you")
        books.filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) }.forEach { BookListRow(it) { onBook(it) } }
        SectionTitle("Popular searches")
        ChipRow(listOf("Animals", "Kindness", "Bedtime", "বাংলা গল্প"))
    }
}

@Composable
private fun BookDetailsScreen(book: Book, onBack: () -> Unit, onRead: () -> Unit, onListen: () -> Unit, onReviews: () -> Unit, onOrder: () -> Unit) {
    PageScaffold(onBack, "Book details", action = { IconButton(onClick = onReviews) { Icon(Icons.Filled.FavoriteBorder, "Save book", tint = PagesColors.Forest) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(width = 142.dp, height = 180.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("by ${book.author}", color = PagesColors.Muted)
                Spacer(Modifier.height(10.dp)); Text("${book.age}   ·   24 pages", style = MaterialTheme.typography.labelMedium)
                Text("⭐ ${book.rating}  ·  1.2K reviews", color = PagesColors.Gold, modifier = Modifier.clickable(onClick = onReviews).padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("A story about kindness, courage, and finding your voice. Follow a curious little fox as a new adventure leads to an unexpected friendship.", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp)); ChipRow(listOf("Animals", "Courage", "Friendship", "Nature"))
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Read in app", Icons.AutoMirrored.Filled.MenuBook, onRead)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Listen and read along · Premium", Icons.Filled.Headphones, onListen)
        Spacer(Modifier.height(18.dp))
        SectionTitle("Choose a format")
        CardSurface(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.TabletAndroid, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("PDF · Read in app", fontWeight = FontWeight.SemiBold); Text("Instant access · no PDF download", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Text("৳120", fontWeight = FontWeight.Bold) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PagesColors.Line)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Printed book", fontWeight = FontWeight.SemiBold); Text("Available · delivery in Bangladesh", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Text("৳450", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(14.dp)); PrimaryButton("Order a book", Icons.Filled.ShoppingCart, onOrder)
            }
        }
        Spacer(Modifier.height(18.dp)); SectionTitle("Reader reviews", "Read all", onClick = onReviews)
        Text("“A lovely story my child asks for every night.”", color = PagesColors.Muted)
    }
}

@Composable
private fun ReaderScreen(book: Book, mode: String, page: Int, bookmarked: Boolean, onBookmark: () -> Unit, onPage: (Int) -> Unit, onMode: (String) -> Unit, onBack: () -> Unit, onListen: () -> Unit, onPremium: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF1EBDD))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close reader") }
            Text(book.title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onBookmark) { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark", tint = PagesColors.Forest) }
            IconButton(onClick = onPremium) { Icon(Icons.Filled.Headphones, "Listen with Premium", tint = PagesColors.Forest) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFFDDEBF1), Color(0xFFF5E9D5)))), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Text("The little fox looked out across the valley. A brand new day was waiting, full of big possibilities.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, lineHeight = 34.sp, color = PagesColors.Ink)
                Text("🦊", fontSize = 116.sp)
                Text("The path curled between the trees and down toward the shining river.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, lineHeight = 28.sp, color = PagesColors.Ink)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onPage((page - 1).coerceAtLeast(1)) }, enabled = page > 1) { Icon(Icons.Filled.ChevronLeft, "Previous page", tint = PagesColors.Forest) }
            Text("$page / 24", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onMode(if (mode == "Page turn") "Scroll" else "Page turn") }) { Text(mode, color = PagesColors.Forest) }
            Surface(shape = RoundedCornerShape(24.dp), color = PagesColors.Forest, modifier = Modifier.clickable(onClick = onListen)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp)); Text(" Listen", color = Color.White, fontWeight = FontWeight.SemiBold) } }
            IconButton(onClick = { onPage((page + 1).coerceAtMost(24)) }, enabled = page < 24) { Icon(Icons.Filled.ChevronRight, "Next page", tint = PagesColors.Forest) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ListenScreen(book: Book, autoplay: Boolean, onAutoplay: (Boolean) -> Unit, onBack: () -> Unit) {
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableFloatStateOf(.42f) }
    var speed by remember { mutableStateOf("1×") }
    var showSettings by remember { mutableStateOf(false) }
    PageScaffold(onBack, "Read along", action = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Narration settings") } }) {
        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Sage))), contentAlignment = Alignment.Center) { Text("🦊   🐦", fontSize = 70.sp) }
        Spacer(Modifier.height(16.dp))
        CardSurface(Modifier.fillMaxWidth()) {
            Text("The little fox ", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("listened", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.background(PagesColors.Peach, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp)); Text(" to the birds’ cheerful song.", style = MaterialTheme.typography.headlineSmall) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { position = (position - .22f).coerceAtLeast(0f) }) { Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = PagesColors.Forest) }
            FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = PagesColors.Forest)) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause narration" else "Play narration", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = { position = (position + .22f).coerceAtMost(1f) }) { Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = PagesColors.Forest) }
        }
        Slider(value = position, onValueChange = { position = it }, colors = SliderDefaults.colors(thumbColor = PagesColors.Forest, activeTrackColor = PagesColors.Forest))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0:18", color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall); Text("0:46", color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(8.dp))
        CardSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoStories, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Autoplay pages", fontWeight = FontWeight.SemiBold); Text("Continue to the next page automatically", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Switch(autoplay, onAutoplay, colors = SwitchDefaults.colors(checkedTrackColor = PagesColors.Forest)) }
        }
        Spacer(Modifier.height(10.dp)); ChipRow(listOf("Voice: Warm storyteller", "Speed: $speed"))
        Text(book.title, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Narration settings") }, text = { Column { Text("Voice"); Text("Warm storyteller · English", color = PagesColors.Muted); Spacer(Modifier.height(14.dp)); Text("Reading speed"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("0.8×", "1×", "1.2×", "1.5×").forEach { choice -> FilterChip(selected = speed == choice, onClick = { speed = choice }, label = { Text(choice) }) } } } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
}

@Composable
private fun CommunityScreen(onGroup: () -> Unit, onCreate: () -> Unit) {
    MainPage(title = "Parent community", subtitle = "A kind space to share the reading journey.", action = { IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, "Create post", tint = PagesColors.Forest) } }) {
        CardSurface(Modifier.fillMaxWidth().clickable(onClick = onCreate), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Text(" Share a thought with parents…", color = PagesColors.Muted, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)); Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest) } }
        Spacer(Modifier.height(16.dp)); ChipRow(listOf("For you", "Discussions", "Tips", "Activities"))
        Spacer(Modifier.height(14.dp)); SectionTitle("Groups to explore", "See all", onClick = onGroup)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { GroupTile("Raising Kind Kids", "312 parents", "🌱", onGroup); GroupTile("Bedtime Stories", "186 parents", "🌙", onGroup) }
        Spacer(Modifier.height(18.dp)); SectionTitle("Recent posts")
        CommunityPost("Sarah Mitchell", "2 days ago", "We finished The Brave Little Fox last night. My little one wanted to talk about courage at breakfast! 🦊", "📚", onGroup)
        CommunityPost("Amina Rahman", "Yesterday", "Looking for gentle Bangla stories for a 5-year-old. What are your family favourites?", "🌼", onGroup)
    }
}

@Composable
private fun GroupScreen(onBack: () -> Unit, onCreatePost: () -> Unit) {
    var reply by remember { mutableStateOf("") }
    val replies = remember { mutableStateListOf<String>() }
    PageScaffold(onBack, "Raising Kind Kids", action = { IconButton(onClick = onCreatePost) { Icon(Icons.Filled.MoreVert, "Group options") } }) {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Column { Text("🌱  Raising Kind Kids", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("312 parents · Stories and ideas for caring families", color = PagesColors.Muted, modifier = Modifier.padding(top = 4.dp)); Text("Joined", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) } }
        ChipRow(listOf("Discussion", "Resources", "Members")); SectionTitle("Latest discussion")
        CommunityPost("Priya Sharma", "3 days ago", "How do you talk to your kids about kindness when it’s hard? We read books that show different perspectives and then chat at bedtime.", "", {})
        CommunityPost("James Carter", "2 days ago", "We use the ‘kind choices’ language after reading together. It gives our son a simple way to reflect.", "", {})
        replies.forEach { message -> CommunityPost("You", "Just now", message, "", {}) }
        OutlineButton("Start a discussion", Icons.Filled.Edit, onCreatePost)
        OutlinedTextField(value = reply, onValueChange = { reply = it }, placeholder = { Text("Write a reply…") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), trailingIcon = { IconButton(onClick = { if (reply.isNotBlank()) { replies.add(reply.trim()); reply = "" } }, enabled = reply.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send reply", tint = PagesColors.Forest) } })
    }
}

@Composable
private fun CreatePostScreen(onBack: () -> Unit, onPost: () -> Unit) {
    var post by remember { mutableStateOf("") }
    PageScaffold(onBack, "Create a post", action = { TextButton(onClick = onPost) { Text("Post", color = PagesColors.Forest, fontWeight = FontWeight.Bold) } }) {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.padding(start = 12.dp)) { Text("Emma’s parent", fontWeight = FontWeight.SemiBold); Text("Posting as a parent", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        OutlinedTextField(value = post, onValueChange = { post = it }, placeholder = { Text("What would you like to share?") }, modifier = Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(20.dp))
        CardSurface(Modifier.fillMaxWidth().clickable { }, color = PagesColors.Sky) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest); Text("  Add a family-friendly photo", fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null) } }
        Text("Please keep children’s personal details private. Posts are visible to community members.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        PrimaryButton("Publish post", Icons.AutoMirrored.Filled.Send, onPost)
    }
}

@Composable
private fun ReviewsScreen(book: Book, onBack: () -> Unit) {
    PageScaffold(onBack, "Reviews") {
        Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Text("4.9", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Column(Modifier.padding(start = 14.dp)) { Text("★★★★★", color = PagesColors.Gold, fontSize = 20.sp); Text("Based on 1,240 parent reviews", color = PagesColors.Muted) } } }
        PrimaryButton("Write a review", Icons.Filled.RateReview, {})
        listOf("A lovely story my child asks for every night.", "Beautiful illustrations and a gentle message.", "We enjoyed reading this together before bed.").forEachIndexed { index, review ->
            CardSurface(Modifier.fillMaxWidth()) { Column { Text(listOf("A. Rahman", "M. Carter", "S. Patel")[index], fontWeight = FontWeight.Bold); Text("★★★★★", color = PagesColors.Gold); Text(review, color = PagesColors.Muted); Text("Helpful  ·  Report", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp)) } }
        }
    }
}

@Composable
private fun OfflineScreen(onBack: () -> Unit, onBook: (Book) -> Unit) {
    PageScaffold(onBack, "Available offline") {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CloudDone, null, tint = PagesColors.Forest); Column(Modifier.padding(start = 12.dp)) { Text("Ready for offline reading", fontWeight = FontWeight.Bold); Text("Progress will sync when you reconnect.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        SectionTitle("Saved on this device")
        books.take(2).forEach { BookListRow(it, trailing = "Available offline") { onBook(it) } }
        Text("Offline copies stay inside LittlePages. Remove a book here to free up space.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ParentScreen(language: String, onLanguage: (String) -> Unit, onProfiles: () -> Unit, onSubscription: () -> Unit, onOrders: () -> Unit, onSupport: () -> Unit, onOffline: () -> Unit) {
    MainPage(title = "Parent account", subtitle = "Manage your family’s LittlePages.") {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.padding(start = 12.dp)) { Text("Emma’s parent", fontWeight = FontWeight.Bold); Text("emma@example.com", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null) } }
        SectionTitle("Family")
        SettingsRow(Icons.Filled.ChildCare, "Child profiles", "Emma · 5 years old", onProfiles)
        SettingsRow(Icons.Filled.Star, "LittlePages Premium", "Explore listening and more", onSubscription)
        SectionTitle("Your account")
        SettingsRow(Icons.Filled.Language, "Language", language, { onLanguage(if (language == "English") "বাংলা" else "English") })
        SettingsRow(Icons.Filled.CloudDownload, "Offline books", "Manage saved stories", onOffline)
        SettingsRow(Icons.AutoMirrored.Filled.ReceiptLong, "Orders", "View purchases and delivery", onOrders)
        SettingsRow(Icons.Filled.SupportAgent, "Help and support", "We’re here to help", onSupport)
        SettingsRow(Icons.Filled.PrivacyTip, "Privacy and safety", "Family controls", {})
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", "", {})
    }
}

@Composable
private fun ProfilesScreen(onBack: () -> Unit) {
    PageScaffold(onBack, "Child profiles") {
        Text("Each child gets their own library and reading progress.", color = PagesColors.Muted)
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Emma", fontWeight = FontWeight.Bold); Text("5 years old · English and বাংলা", color = PagesColors.Muted) }; Icon(Icons.Filled.Edit, "Edit profile", tint = PagesColors.Forest) } }
        OutlineButton("Add a child profile", Icons.Filled.Add, {})
        Text("Parent account controls purchases, subscriptions, and community posts.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SubscriptionScreen(premium: Boolean, onBack: () -> Unit, onSubscribe: () -> Unit) {
    PageScaffold(onBack, "LittlePages Premium") {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(PagesColors.Sage, PagesColors.Peach))).padding(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Text("✨", fontSize = 40.sp); Text("Stories come alive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("A little extra magic for family reading time.", textAlign = TextAlign.Center, color = PagesColors.Muted, modifier = Modifier.padding(top = 8.dp)) }
        }
        listOf("Word-by-word narrated read-along", "Warm voices and adjustable speed", "More stories for your family", "Offline listening on supported books").forEach { BenefitRow(it) }
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Family plan", fontWeight = FontWeight.Bold); Text("Billed monthly · cancel anytime", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Text("৳399 / mo", fontWeight = FontWeight.Bold, color = PagesColors.Forest) } }
        PrimaryButton(if (premium) "Premium is active" else "Start Premium", Icons.Filled.Star, onSubscribe)
        Text("Subscriptions are managed securely through the available payment option.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CheckoutScreen(book: Book, onBack: () -> Unit, onComplete: () -> Unit) {
    var format by remember { mutableStateOf("Printed book") }
    var fullName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    PageScaffold(onBack, "Choose a format") {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { BookCover(book, Modifier.size(68.dp)); Column(Modifier.padding(start = 12.dp)) { Text(book.title, fontWeight = FontWeight.Bold); Text("Choose how your family will read", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        FormatChoice("PDF · Read in app", "৳120", "Instant access. Downloading and exporting are disabled.", format == "PDF · Read in app") { format = "PDF · Read in app" }
        FormatChoice("Printed book", "৳450", "Physical edition available · delivery address required", format == "Printed book") { format = "Printed book" }
        if (format == "Printed book") {
            SectionTitle("Delivery address")
            OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(address, { address = it }, label = { Text("Address and area") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        }
        SectionTitle("Payment")
        ChipRow(listOf("bKash", "Visa"))
        PrimaryButton("Place order", Icons.Filled.Lock, onComplete)
        Text("Payment is securely processed. Your PDF purchase is for in-app reading only.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OrdersScreen(onBack: () -> Unit) {
    PageScaffold(onBack, "Your orders") {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Text("📦", fontSize = 28.sp); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("The Brave Little Fox · Print", fontWeight = FontWeight.Bold); Text("Order LP-1048 · Placed 10 Oct", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Text("On the way", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold) } }
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = PagesColors.Forest); Column(Modifier.padding(start = 12.dp)) { Text("A Day at the Lake · PDF", fontWeight = FontWeight.Bold); Text("In-app reading access", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text("Ready", color = PagesColors.Forest) } }
    }
}

@Composable
private fun SupportScreen(onBack: () -> Unit) {
    PageScaffold(onBack, "Help and support") {
        Text("Tell us what happened and our team will help.", color = PagesColors.Muted)
        SettingsRow(Icons.Filled.CloudOff, "Offline reading help", "Download or sync troubleshooting", {})
        SettingsRow(Icons.Filled.Payment, "Payment and orders", "Billing, refunds, and delivery", {})
        SettingsRow(Icons.Filled.AccountCircle, "Account and profiles", "Sign-in and family settings", {})
        SectionTitle("Contact us")
        OutlinedTextField("", {}, label = { Text("Your email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        OutlinedTextField("", {}, label = { Text("How can we help?") }, modifier = Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(16.dp))
        PrimaryButton("Send support request", Icons.AutoMirrored.Filled.Send, {})
        Text("If a download or payment fails, your order and reading progress remain safe while we resolve it.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit, onSubscribe: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PagesColors.Paper,
        shape = RoundedCornerShape(28.dp),
        icon = { Text("🎧✨", fontSize = 30.sp) },
        title = { Text("Make story time magical", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = { Text("Listen to the story with each word lighting up as it’s read. Premium is managed by a parent.", textAlign = TextAlign.Center, color = PagesColors.Muted) },
        confirmButton = { Button(onClick = onSubscribe, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest)) { Text("Explore Premium") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = PagesColors.Forest) } }
    )
}

// Shared layout and design-system components.
@Composable
private fun MainPage(title: String, subtitle: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink); Text(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            action?.invoke()
        }
        Spacer(Modifier.height(18.dp)); content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PageScaffold(onBack: () -> Unit, title: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PagesColors.Ink) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            action?.invoke()
        }
        content(); Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PageScaffold(onBack, "Account") {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = PagesColors.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        content()
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("📖", fontSize = 28.sp); Text(" LittlePages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PagesColors.Ink) }
}

@Composable
private fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        if (icon != null) { Icon(icon, null, tint = PagesColors.Forest, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)) }
        Text(text, color = PagesColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line); Text("  or  ", color = PagesColors.Muted); HorizontalDivider(Modifier.weight(1f), color = PagesColors.Line) }
}

@Composable
private fun SearchBarCard(onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Search, null, tint = PagesColors.Muted); Text("  Search books, characters, or topics", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium) } }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (action != null) Text(action, color = PagesColors.Forest, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onClick?.invoke() }) }
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, color: Color = PagesColors.Paper, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun ChipRow(labels: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label -> Surface(shape = CircleShape, color = if (index == 0) PagesColors.Forest else PagesColors.Sage) { Text(label, color = if (index == 0) Color.White else PagesColors.Ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) } }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(book.tint, PagesColors.Cream))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(book.emoji, fontSize = 44.sp); Text(book.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) }
    }
}

@Composable
private fun BookRow(items: List<Book>, onBook: (Book) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { book -> Column(Modifier.width(142.dp).clickable { onBook(book) }) { BookCover(book, Modifier.fillMaxWidth().height(150.dp)); Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp)); Text(book.age, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun BookListRow(book: Book, trailing: String? = null, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(72.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${book.author} · ${book.age}", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); Text("⭐ ${book.rating} · English · বাংলা", color = PagesColors.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
            if (trailing != null) Text(trailing, color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Avatar(initial: String) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(PagesColors.Peach), contentAlignment = Alignment.Center) { Text(initial, color = PagesColors.Ink, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GroupTile(title: String, members: String, emoji: String, onClick: () -> Unit) {
    CardSurface(Modifier.width(174.dp).clickable(onClick = onClick), color = PagesColors.Sky) { Text(emoji, fontSize = 26.sp); Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)); Text(members, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall); Text("Join group →", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
private fun CommunityPost(name: String, time: String, body: String, photo: String, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(name.first().toString()); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(time, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Filled.MoreHoriz, "Post options", tint = PagesColors.Muted) }
        Text(body, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        if (photo.isNotBlank()) Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(132.dp).clip(RoundedCornerShape(16.dp)).background(PagesColors.Sage), contentAlignment = Alignment.Center) { Text(photo, fontSize = 56.sp) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("♡  24", modifier = Modifier.clickable { }, color = PagesColors.Muted); Text("    ◯  8 comments", modifier = Modifier.weight(1f).clickable(onClick = onClick), color = PagesColors.Muted); Text("Share", color = PagesColors.Forest) }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); if (subtitle.isNotBlank()) Text(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Muted) }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = PagesColors.Forest, modifier = Modifier.size(22.dp)); Text(text, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Medium) }
}

@Composable
private fun FormatChoice(title: String, price: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    CardSurface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = if (selected) PagesColors.Sage else PagesColors.Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = PagesColors.Forest)); Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Text(price, fontWeight = FontWeight.Bold, color = PagesColors.Forest) }
    }
}
