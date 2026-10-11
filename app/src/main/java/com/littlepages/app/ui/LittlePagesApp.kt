package com.littlepages.app.ui

import android.app.Activity
import android.animation.ValueAnimator
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlepages.app.data.ReaderProgressStore
import com.littlepages.app.data.AndroidNarrationEngine
import com.littlepages.app.data.NarrationAudioFocus
import com.littlepages.app.data.NarrationState
import com.littlepages.app.AppLanguage
import com.littlepages.app.R
import com.littlepages.app.ui.components.*
import com.littlepages.app.ui.theme.PagesColors
import kotlinx.coroutines.delay

private enum class Page {
    Welcome, EmailAuth, PhoneAuth, Home, Library, Search, BookDetails, Reader, Listen, ListenPreview,
    Community, Group, CreatePost, Reviews, Offline, Parent, Profiles, Subscription,
    Checkout, Orders, Support
}

internal data class Book(val title: String, val author: String, val age: String, val emoji: String, val tint: Color, val rating: String = "4.9", val categories: Set<String> = emptySet(), val minimumAge: Int = 0, val maximumAge: Int = 12, val hasBangla: Boolean = true)
private val books = listOf(
    Book("The Brave Little Fox", "Emma Clarke", "Ages 3–7", "🦊", PagesColors.Sky, categories = setOf("Animals", "Friendship", "Nature"), minimumAge = 3, maximumAge = 7),
    Book("A Day at the Lake", "Mina Rahman", "Ages 4–8", "🐻", PagesColors.Sage, categories = setOf("Animals", "Nature"), minimumAge = 4, maximumAge = 8),
    Book("The Kind Giant", "Oliver Reed", "Ages 5–9", "🌿", PagesColors.Peach, categories = setOf("Friendship", "Nature"), minimumAge = 5, maximumAge = 9),
    Book("The Star Keeper", "Nadia Noor", "Ages 4–8", "🌙", Color(0xFFE8E4F3), categories = setOf("Nature", "Bedtime"), minimumAge = 4, maximumAge = 8)
)
private data class CommunityEntry(val body: String, val hasPhoto: Boolean)
private data class ChildProfile(val nickname: String, val ageBand: String)

@Composable
fun LittlePagesApp() {
    val navigationStackSaver = remember {
        listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Page>, String>(
            save = { routeStack -> routeStack.map(Page::name) },
            restore = { savedRoutes -> savedRoutes.map(Page::valueOf).toMutableStateList() }
        )
    }
    val stack = rememberSaveable(saver = navigationStackSaver) { mutableStateListOf(Page.Welcome) }
    var premium by remember { mutableStateOf(false) }
    var premiumPrompt by remember { mutableStateOf(false) }
    var unavailableNotice by remember { mutableStateOf<String?>(null) }
    var selectedBook by remember { mutableStateOf(books.first()) }
    var query by remember { mutableStateOf("") }
    val appConfiguration = LocalConfiguration.current
    val useNavigationRail = appConfiguration.screenWidthDp >= 600
    val mainTabs = listOf(Page.Home, Page.Library, Page.Community, Page.Parent, Page.Offline)
    val appContext = LocalContext.current
    val activity = appContext as? Activity
    val systemAnimationsEnabled = ValueAnimator.areAnimatorsEnabled()
    var selectedLanguage by remember(appConfiguration) { mutableStateOf(appConfiguration.locales[0].language.ifBlank { "en" }) }
    var autoplay by remember { mutableStateOf(true) }
    var readerMode by remember { mutableStateOf("Page turn") }
    val progressStore = remember(appContext) { ReaderProgressStore(appContext) }
    val activeChildId = "local-profile"
    var currentPage by remember(selectedBook.title) { mutableIntStateOf(progressStore.page(activeChildId, selectedBook.title).coerceIn(1, 24)) }
    var bookmarked by remember(selectedBook.title, currentPage) { mutableStateOf(progressStore.isBookmarked(activeChildId, selectedBook.title, currentPage)) }
    val communityPosts = remember { mutableStateListOf<CommunityEntry>() }
    val childProfiles = remember { mutableStateListOf(ChildProfile("Emma", "5 years old")) }
    val savedBooks = remember { mutableStateListOf<String>() }
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
            if (!useNavigationRail && page in mainTabs) {
                NavigationBar(containerColor = PagesColors.Paper, tonalElevation = 0.dp) {
                    val items = listOf(
                        Triple(Page.Home, stringResource(R.string.nav_home), Icons.Filled.Home),
                        Triple(Page.Library, stringResource(R.string.nav_library), Icons.Filled.AutoStories),
                        Triple(Page.Community, stringResource(R.string.nav_community), Icons.Filled.Groups),
                        Triple(Page.Parent, stringResource(R.string.nav_profile), Icons.Filled.AccountCircle)
                    )
                    items.forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { if (page != destination) { stack.clear(); stack.add(destination) } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { AppText(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = PagesColors.Forest, indicatorColor = PagesColors.Sage)
                        )
                    }
                }
            }
        }
    ) { insets ->
        Row(Modifier.fillMaxSize().padding(insets)) {
          if (useNavigationRail && page in mainTabs) {
            NavigationRail(containerColor = PagesColors.Paper) {
              listOf(
                Triple(Page.Home, stringResource(R.string.nav_home), Icons.Filled.Home),
                Triple(Page.Library, stringResource(R.string.nav_library), Icons.Filled.AutoStories),
                Triple(Page.Community, stringResource(R.string.nav_community), Icons.Filled.Groups),
                Triple(Page.Parent, stringResource(R.string.nav_profile), Icons.Filled.AccountCircle)
              ).forEach { (destination, label, icon) ->
                NavigationRailItem(selected = page == destination, onClick = { if (page != destination) { stack.clear(); stack.add(destination) } }, icon = { Icon(icon, contentDescription = label) }, label = { AppText(label, fontSize = 11.sp) }, alwaysShowLabel = false, colors = NavigationRailItemDefaults.colors(selectedIconColor = PagesColors.Forest, indicatorColor = PagesColors.Sage))
              }
            }
          }
          AnimatedContent(
            targetState = page,
            transitionSpec = { if (systemAnimationsEnabled) fadeIn() togetherWith fadeOut() else EnterTransition.None togetherWith ExitTransition.None },
            label = "page-transition",
            modifier = Modifier.weight(1f).fillMaxHeight()
          ) { target ->
            when (target) {
                Page.Welcome -> WelcomeScreen(
                    onGoogle = { unavailableNotice = "Google sign-in will be available after Firebase Authentication is configured." }, onEmail = { go(Page.EmailAuth) }, onPhone = { go(Page.PhoneAuth) }, onSignIn = { go(Page.EmailAuth) }, onPreview = { go(Page.Home) }
                )
                Page.EmailAuth -> EmailAuthScreen(onBack = ::back, onContinue = { unavailableNotice = "Email authentication is not connected yet. No account was created or signed in." }, onGoogle = { unavailableNotice = "Google sign-in will be available after Firebase Authentication is configured." }, onPhone = { go(Page.PhoneAuth) }, onPasswordReset = { unavailableNotice = "Password reset will be available after Firebase Authentication is configured." })
                Page.PhoneAuth -> PhoneAuthScreen(onBack = ::back, onContinue = { unavailableNotice = "Phone verification is not connected yet. No verification code was sent." }, onEmail = { go(Page.EmailAuth) })
                Page.Home -> HomeScreen(continuePage = progressStore.page(activeChildId, books[0].title).coerceIn(1, 24), onBook = { selectedBook = it; go(Page.BookDetails) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) }, onPremium = { go(Page.Subscription) })
                Page.Library -> LibraryScreen(onBook = { selectedBook = it; go(Page.BookDetails) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) })
                Page.Search -> SearchScreen(query = query, onQuery = { query = it }, onBook = { selectedBook = it; go(Page.BookDetails) }, onBack = ::back)
                Page.BookDetails -> BookDetailsScreen(book = selectedBook, saved = selectedBook.title in savedBooks, onSave = { if (selectedBook.title in savedBooks) savedBooks.remove(selectedBook.title) else savedBooks.add(selectedBook.title) }, onBack = ::back, onRead = { go(Page.Reader) }, onListen = ::openPremiumFeature, onReviews = { go(Page.Reviews) }, onOrder = { go(Page.Checkout) })
                Page.Reader -> ReaderScreen(book = selectedBook, mode = readerMode, page = currentPage, bookmarked = bookmarked, onBookmark = { bookmarked = progressStore.toggleBookmark(activeChildId, selectedBook.title, currentPage) }, onPage = { currentPage = it; progressStore.savePage(activeChildId, selectedBook.title, it) }, onMode = { readerMode = it }, onBack = ::back, onListen = ::openPremiumFeature, onPremium = { premiumPrompt = true })
                Page.Listen -> ListenScreen(book = selectedBook, page = currentPage, autoplay = autoplay, onAutoplay = { autoplay = it }, onPageAdvance = { if (currentPage < 24) { currentPage += 1; progressStore.savePage(activeChildId, selectedBook.title, currentPage) } }, onBack = ::back)
                Page.ListenPreview -> ListenScreen(book = selectedBook, page = currentPage, autoplay = false, onAutoplay = {}, onPageAdvance = {}, onBack = ::back, previewOnly = true)
                Page.Community -> CommunityScreen(posts = communityPosts, onGroup = { go(Page.Group) }, onCreate = { go(Page.CreatePost) })
                Page.Group -> GroupScreen(onBack = ::back, onCreatePost = { go(Page.CreatePost) })
                Page.CreatePost -> CreatePostScreen(onBack = ::back, onPost = { communityPosts.add(0, it); back() })
                Page.Reviews -> ReviewsScreen(book = selectedBook, onBack = ::back)
                Page.Offline -> OfflineScreen(onBack = ::back, onBrowse = { stack.clear(); stack.add(Page.Library) })
                Page.Parent -> ParentScreen(language = selectedLanguage, child = childProfiles.firstOrNull(), onLanguage = { tag -> selectedLanguage = tag; activity?.let { AppLanguage.set(it, tag) } }, onProfiles = { go(Page.Profiles) }, onSubscription = { go(Page.Subscription) }, onOrders = { go(Page.Orders) }, onSupport = { go(Page.Support) }, onOffline = { go(Page.Offline) }, onUnavailable = { unavailableNotice = it })
                Page.Profiles -> ProfilesScreen(children = childProfiles, onBack = ::back, onSaveChild = { old, updated -> if (old == null) childProfiles.add(updated) else { val index = childProfiles.indexOf(old); if (index >= 0) childProfiles[index] = updated } })
                Page.Subscription -> SubscriptionScreen(premium = premium, onBack = ::back, onSubscribe = { unavailableNotice = "Subscription checkout is not connected yet. Premium access will only activate after a verified payment." }, onPreview = { go(Page.ListenPreview) })
                Page.Checkout -> CheckoutScreen(book = selectedBook, onBack = ::back, onComplete = { unavailableNotice = "Checkout is not connected yet. No payment or order has been placed." })
                Page.Orders -> OrdersScreen(onBack = ::back)
                Page.Support -> SupportScreen(onBack = ::back, onSubmit = { unavailableNotice = "Support requests are not connected yet. Your message has not been sent." })
            }
          }
        }

        if (premiumPrompt) {
            PremiumDialog(onDismiss = { premiumPrompt = false }, onSubscribe = { premiumPrompt = false; go(Page.Subscription) })
        }
        unavailableNotice?.let { message ->
            AlertDialog(onDismissRequest = { unavailableNotice = null }, title = { AppText("Not available yet") }, text = { AppText(message) }, confirmButton = { TextButton(onClick = { unavailableNotice = null }) { AppText("OK") } })
        }
    }
}

@Composable
private fun WelcomeScreen(onGoogle: () -> Unit, onEmail: () -> Unit, onPhone: () -> Unit, onSignIn: () -> Unit, onPreview: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compactHeight = maxHeight < 520.dp
      Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal = if (maxWidth < 360.dp) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(if (compactHeight) 12.dp else 34.dp))
        BrandMark()
        Spacer(Modifier.height(16.dp))
        AppText("A little world\nof stories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, lineHeight = 42.sp)
        AppText("A parent account for your family’s reading journey", style = MaterialTheme.typography.bodyLarge, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(if (compactHeight) 120.dp else 210.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Cream))), contentAlignment = Alignment.Center) {
            AppText("🌿     🐰📖🐰     🌼", fontSize = 38.sp)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Continue with phone", Icons.Filled.Phone, onPhone)
        TextButton(onClick = onPreview, modifier = Modifier.padding(top = 8.dp)) { AppText("Explore app preview", color = PagesColors.Forest) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("Already have an account? ", color = PagesColors.Muted)
            AppText("Sign in", color = PagesColors.Forest, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onSignIn).padding(vertical = 12.dp))
        }
        AppText("By continuing, you agree to our Terms and Privacy Policy.", style = MaterialTheme.typography.labelSmall, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
      }
    }
}

@Composable
private fun EmailAuthScreen(onBack: () -> Unit, onContinue: () -> Unit, onGoogle: () -> Unit, onPhone: () -> Unit, onPasswordReset: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val validPassword = password.length >= 8
    val passwordsMatch = password == confirmPassword
    FormPage(title = if (creatingAccount) "Create parent account" else "Welcome back", subtitle = "Use a parent email to manage your family’s reading.", onBack = onBack) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { AppText("Email address") }, leadingIcon = { Icon(Icons.Filled.Email, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && !validEmail, supportingText = { if (submitted && !validEmail) AppText("Enter a valid email address.") })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { AppText("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (passwordVisible) "Hide password" else "Show password") } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && !validPassword, supportingText = { if (submitted && !validPassword) AppText("Use at least 8 characters.") })
        if (creatingAccount) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { AppText("Confirm password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && !passwordsMatch, supportingText = { if (submitted && !passwordsMatch) AppText("Passwords do not match.") })
        } else {
            AppText("Forgot password?", color = PagesColors.Forest, modifier = Modifier.align(Alignment.End).clickable(onClick = onPasswordReset))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (creatingAccount) "Create account" else "Sign in", null) { submitted = true; if (validEmail && validPassword && (!creatingAccount || passwordsMatch)) onContinue() }
        TextButton(onClick = { creatingAccount = !creatingAccount; submitted = false; confirmPassword = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText(if (creatingAccount) "Already have an account? Sign in" else "New to LittlePages? Create an account", color = PagesColors.Forest) }
        OrDivider()
        OutlineButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Use phone instead", Icons.Filled.Phone, onPhone)
    }
}

@Composable
private fun PhoneAuthScreen(onBack: () -> Unit, onContinue: () -> Unit, onEmail: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    FormPage(title = "Continue with phone", subtitle = "We’ll text a one-time code to verify your number.", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = "+880", onValueChange = {}, readOnly = true, label = { AppText("Code") }, modifier = Modifier.width(105.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(15) }, label = { AppText("Phone number") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && phone.length < 8, supportingText = { if (submitted && phone.length < 8) AppText("Enter a valid phone number.") })
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Send verification code", Icons.Filled.Sms) { submitted = true; if (phone.length >= 8) onContinue() }
        AppText("We use your number only to secure your account.", color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(14.dp))
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
    }
}

@Composable
private fun HomeScreen(continuePage: Int, onBook: (Book) -> Unit, onSearch: () -> Unit, onOffline: () -> Unit, onPremium: () -> Unit) {
    MainPage(title = "Good morning, Emma 👋", subtitle = "Ready for a new adventure?") {
        SearchBarCard(onSearch)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("Continue reading"); AppText("The Brave Little Fox · $continuePage of 24 pages", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }
            AppText("Open", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBook(books[0]) })
        }
        Spacer(Modifier.height(8.dp))
        CardSurface(Modifier.fillMaxWidth().clickable { onBook(books[0]) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(books[0], Modifier.size(width = 92.dp, height = 108.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    AppText(books[0].title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AppText("You’re doing great!", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { continuePage / 24f }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = PagesColors.Forest, trackColor = PagesColors.Sage)
                    AppText("${(continuePage * 100 / 24)}% complete", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Popular this week", "See all", onClick = { onBook(books[1]) })
        BookRow(books, onBook)
        Spacer(Modifier.height(16.dp))
        CardSurface(Modifier.fillMaxWidth().clickable(onClick = onPremium), color = PagesColors.Sage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("🎧", fontSize = 28.sp); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { AppText("Listen and read along", fontWeight = FontWeight.Bold); AppText("Words light up as stories are read", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Forest)
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Your offline books", "See all", onClick = onOffline)
        AppText("Offline book storage isn't set up yet", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted)
    }
}

@Composable
private fun LibraryScreen(onBook: (Book) -> Unit, onSearch: () -> Unit, onOffline: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedAge by remember { mutableStateOf("All ages") }
    val categories = listOf("All", "Animals", "Friendship", "Nature", "Bedtime", "Bangla")
    val ages = listOf("All ages", "Ages 0–3", "Ages 4–6", "Ages 7–9")
    val filteredBooks = books.filter { book ->
        val categoryMatches = when (selectedCategory) {
            "All" -> true
            "Bangla" -> book.hasBangla
            else -> selectedCategory in book.categories
        }
        val ageMatch = when (selectedAge) {
            "All ages" -> true
            "Ages 0–3" -> book.minimumAge <= 3
            "Ages 4–6" -> book.maximumAge >= 4 && book.minimumAge <= 6
            else -> book.maximumAge >= 7
        }
        categoryMatches && ageMatch
    }
    MainPage(title = "Book library", subtitle = "Find a story for every little mood.") {
        SearchBarCard(onSearch)
        Spacer(Modifier.height(14.dp))
        ChipRow(categories, selectedIndex = categories.indexOf(selectedCategory), onSelect = { selectedCategory = categories[it] })
        Spacer(Modifier.height(20.dp))
        SectionTitle("Browse by age")
        ChipRow(ages, selectedIndex = ages.indexOf(selectedAge), onSelect = { selectedAge = ages[it] })
        Spacer(Modifier.height(18.dp))
        SectionTitle("All stories", "Offline", onClick = onOffline)
        if (filteredBooks.isEmpty()) EmptyState(Icons.Filled.SearchOff, "No books match these filters", "Try another category or age range.")
        filteredBooks.forEach { book -> BookListRow(book, onClick = { onBook(book) }) }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, onBook: (Book) -> Unit, onBack: () -> Unit) {
    val suggestions = listOf("Animals", "Kindness", "Bedtime", "বাংলা গল্প")
    val results = books.filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) || it.categories.any { category -> category.contains(query, true) } || (query.contains("বাংলা") && it.hasBangla) }
    PageScaffold(onBack, "Search books") {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { AppText("Title, character, or topic") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true)
        Spacer(Modifier.height(16.dp)); SectionTitle("Suggested for you")
        if (results.isEmpty()) EmptyState(Icons.Filled.SearchOff, "No books found", "Try a title, author, category, or another search.")
        results.forEach { BookListRow(it) { onBook(it) } }
        SectionTitle("Popular searches")
        ChipRow(suggestions, selectedIndex = -1, onSelect = { onQuery(suggestions[it]) })
    }
}

@Composable
private fun BookDetailsScreen(book: Book, saved: Boolean, onSave: () -> Unit, onBack: () -> Unit, onRead: () -> Unit, onListen: () -> Unit, onReviews: () -> Unit, onOrder: () -> Unit) {
    PageScaffold(onBack, "Book details", action = { IconButton(onClick = onSave) { Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (saved) "Remove saved book" else "Save book", tint = PagesColors.Forest) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(width = 142.dp, height = 180.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                AppText(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                AppText("by ${book.author}", color = PagesColors.Muted)
                Spacer(Modifier.height(10.dp)); AppText("${book.age}   ·   24 pages", style = MaterialTheme.typography.labelMedium)
                AppText("⭐ ${book.rating}  ·  1.2K reviews", color = PagesColors.Gold, modifier = Modifier.clickable(onClick = onReviews).padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        AppText("A story about kindness, courage, and finding your voice. Follow a curious little fox as a new adventure leads to an unexpected friendship.", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp)); ChipRow(listOf("Animals", "Courage", "Friendship", "Nature"))
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Read in app", Icons.AutoMirrored.Filled.MenuBook, onRead)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Listen and read along · Premium", Icons.Filled.Headphones, onListen)
        Spacer(Modifier.height(18.dp))
        SectionTitle("Choose a format")
        CardSurface(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.TabletAndroid, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = 12.dp)) { AppText("PDF · Read in app", fontWeight = FontWeight.SemiBold); AppText("Instant access · no PDF download", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳120", fontWeight = FontWeight.Bold) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PagesColors.Line)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = 12.dp)) { AppText("Printed book", fontWeight = FontWeight.SemiBold); AppText("Available · delivery in Bangladesh", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳450", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(14.dp)); PrimaryButton("Order a book", Icons.Filled.ShoppingCart, onOrder)
            }
        }
        Spacer(Modifier.height(18.dp)); SectionTitle("Reader reviews", "Read all", onClick = onReviews)
        AppText("“A lovely story my child asks for every night.”", color = PagesColors.Muted)
    }
}

private val sampleStoryPages = listOf(
    "The little fox looked out across the valley. A brand new day was waiting, full of big possibilities.",
    "The path curled between the trees and down toward the shining river. Fox listened to the birds’ cheerful song.",
    "A tiny blue bird fluttered close. ‘I’m looking for my family,’ she said. Fox smiled and offered to help.",
    "Together they followed the river until they heard a happy chorus from the willow trees.",
    "The bird found her family, and Fox discovered that a kind adventure is even better when shared."
)

@Composable
private fun ReaderScreen(book: Book, mode: String, page: Int, bookmarked: Boolean, onBookmark: () -> Unit, onPage: (Int) -> Unit, onMode: (String) -> Unit, onBack: () -> Unit, onListen: () -> Unit, onPremium: () -> Unit) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Column(Modifier.fillMaxSize().background(Color(0xFFF1EBDD))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compactHeight) 2.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close reader") }
            AppText(book.title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onBookmark) { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark", tint = PagesColors.Forest) }
            IconButton(onClick = onPremium) { Icon(Icons.Filled.Headphones, "Listen with Premium", tint = PagesColors.Forest) }
        }
        if (mode == "Page turn") {
            Box(Modifier.weight(1f).fillMaxWidth().padding(if (compactHeight) 8.dp else 14.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFFDDEBF1), Color(0xFFF5E9D5)))), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxSize().padding(if (compactHeight) 12.dp else 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    AppText(sampleStoryPages[(page - 1) % sampleStoryPages.size], style = if (compactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, lineHeight = if (compactHeight) 26.sp else 34.sp, color = PagesColors.Ink)
                    AppText("🦊", fontSize = if (compactHeight) 56.sp else 116.sp)
                    AppText("The Brave Little Fox", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = PagesColors.Ink)
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (page..(page + 2).coerceAtMost(24)).forEach { scrollPage ->
                    CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Paper) {
                        AppText("Page $scrollPage", style = MaterialTheme.typography.labelMedium, color = PagesColors.Forest, fontWeight = FontWeight.Bold)
                        AppText(sampleStoryPages[(scrollPage - 1) % sampleStoryPages.size], style = MaterialTheme.typography.titleLarge, lineHeight = 36.sp, color = PagesColors.Ink, modifier = Modifier.padding(top = 10.dp))
                        AppText("🦊", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onPage((page - 1).coerceAtLeast(1)) }, enabled = page > 1) { Icon(Icons.Filled.ChevronLeft, "Previous page", tint = PagesColors.Forest) }
            AppText("$page / 24", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onMode(if (mode == "Page turn") "Scroll" else "Page turn") }) { AppText(mode, color = PagesColors.Forest) }
            Surface(shape = RoundedCornerShape(24.dp), color = PagesColors.Forest, modifier = Modifier.clickable(onClick = onListen)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp)); AppText(" Listen", color = Color.White, fontWeight = FontWeight.SemiBold) } }
            IconButton(onClick = { onPage((page + 1).coerceAtMost(24)) }, enabled = page < 24) { Icon(Icons.Filled.ChevronRight, "Next page", tint = PagesColors.Forest) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ListenScreen(book: Book, page: Int, autoplay: Boolean, onAutoplay: (Boolean) -> Unit, onPageAdvance: () -> Unit, onBack: () -> Unit, previewOnly: Boolean = false) {
    val context = LocalContext.current
    var narrationState by remember { mutableStateOf(NarrationState()) }
    var isPlaying by remember { mutableStateOf(false) }
    var focusMessage by remember { mutableStateOf<String?>(null) }
    val narrationEngine = remember(context) { AndroidNarrationEngine(context) { narrationState = it } }
    DisposableEffect(narrationEngine) { onDispose { narrationEngine.close() } }
    val audioFocus = remember(context) { NarrationAudioFocus(context) { narrationEngine.stop(); isPlaying = false; focusMessage = "Narration paused because another app is using audio." } }
    DisposableEffect(audioFocus) { onDispose { audioFocus.abandon() } }
    val narrationText = sampleStoryPages[(page - 1) % sampleStoryPages.size]
    val speedOptions = listOf("0.8×" to .8f, "1×" to 1f, "1.2×" to 1.2f, "1.5×" to 1.5f)
    var speed by remember { mutableStateOf("1×") }
    var selectedVoiceId by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableFloatStateOf(0f) }
    var fallbackRange by remember { mutableStateOf<IntRange?>(null) }
    var resumeOffset by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var lastAutoAdvancedPage by remember { mutableIntStateOf(page) }
    val estimatedSeconds = (narrationText.split(Regex("\\s+")).size * .42f).toInt().coerceAtLeast(1)
    val narrationError = focusMessage ?: narrationState.error

    fun speakFrom(offset: Int): Boolean {
        focusMessage = null
        if (!audioFocus.request()) {
            focusMessage = "Audio focus is unavailable. Pause other audio and try again."
            return false
        }
        val selectedRate = speedOptions.firstOrNull { it.first == speed }?.second ?: 1f
        val started = narrationEngine.speak(narrationText, offset.coerceIn(0, (narrationText.length - 1).coerceAtLeast(0)), selectedRate, selectedVoiceId)
        if (!started) audioFocus.abandon()
        return started
    }
    fun seekTo(value: Float) {
        val wasPlaying = isPlaying
        position = value.coerceIn(0f, 1f)
        resumeOffset = (narrationText.length * position).toInt().coerceIn(0, narrationText.length)
        narrationEngine.stop()
        if (wasPlaying) isPlaying = speakFrom(resumeOffset)
    }

    LaunchedEffect(narrationState.ready, page) {
        if (narrationState.ready) {
            resumeOffset = 0
            position = 0f
            lastAutoAdvancedPage = page
            isPlaying = speakFrom(0)
        }
    }
    LaunchedEffect(narrationState.activeRange) {
        narrationState.activeRange?.let { range -> position = range.last.coerceAtLeast(0).toFloat() / narrationText.length.coerceAtLeast(1) }
    }
    LaunchedEffect(isPlaying, page, resumeOffset, narrationState.wordTimingAvailable, speed) {
        fallbackRange = null
        if (isPlaying && !narrationState.wordTimingAvailable) {
            val rate = speedOptions.firstOrNull { it.first == speed }?.second ?: 1f
            Regex("\\S+").findAll(narrationText)
                .filter { it.range.last >= resumeOffset }
                .forEach { word ->
                    fallbackRange = word.range
                    position = word.range.last.toFloat() / narrationText.length.coerceAtLeast(1)
                    delay((420f / rate).toLong().coerceAtLeast(120L))
                }
        }
    }
    LaunchedEffect(narrationState.finished, page, autoplay) {
        if (narrationState.finished && lastAutoAdvancedPage == page) {
            position = 1f
            if (autoplay && !previewOnly && page < 24) {
                lastAutoAdvancedPage = page + 1
                onPageAdvance()
            } else { isPlaying = false; audioFocus.abandon() }
        }
    }
    LaunchedEffect(narrationState.error) {
        if (!narrationState.error.isNullOrBlank()) { isPlaying = false; audioFocus.abandon() }
    }
    LaunchedEffect(narrationState.voices) {
        if (selectedVoiceId == null) selectedVoiceId = narrationState.voices.firstOrNull()?.id
    }

    val wordRange = narrationState.activeRange ?: fallbackRange
    val highlightedText = remember(narrationText, wordRange) {
        buildAnnotatedString {
            append(narrationText)
            wordRange?.let { range ->
                val start = range.first.coerceIn(0, narrationText.length)
                val end = (range.last + 1).coerceIn(start, narrationText.length)
                if (end > start) addStyle(SpanStyle(background = PagesColors.Peach, fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
    fun durationLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    PageScaffold(onBack, "Read along", action = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Narration settings") } }) {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Sage))), contentAlignment = Alignment.Center) { AppText("🦊   🐦", fontSize = 70.sp) }
        Spacer(Modifier.height(16.dp))
        CardSurface(Modifier.fillMaxWidth()) { MaterialText(text = highlightedText, style = MaterialTheme.typography.headlineSmall, lineHeight = 36.sp) }
        if (!narrationError.isNullOrBlank()) AppText(narrationError, color = PagesColors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        else if (!narrationState.ready) AppText("Preparing your device’s voice…", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = narrationState.ready, onClick = { seekTo((position - 10f / estimatedSeconds).coerceAtLeast(0f)) }) { Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = PagesColors.Forest) }
            FilledIconButton(enabled = narrationState.ready, onClick = {
                if (isPlaying) {
                    resumeOffset = narrationState.activeRange?.first ?: resumeOffset
                    narrationEngine.stop()
                    isPlaying = false
                    audioFocus.abandon()
                } else isPlaying = speakFrom(resumeOffset)
            }, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = PagesColors.Forest)) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause narration" else "Play narration", modifier = Modifier.size(34.dp)) }
            IconButton(enabled = narrationState.ready, onClick = { seekTo((position + 10f / estimatedSeconds).coerceAtMost(1f)) }) { Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = PagesColors.Forest) }
        }
        Slider(value = position, onValueChange = { position = it }, onValueChangeFinished = { seekTo(position) }, enabled = narrationState.ready, colors = SliderDefaults.colors(thumbColor = PagesColors.Forest, activeTrackColor = PagesColors.Forest))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AppText(durationLabel((position * estimatedSeconds).toInt()), color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall); AppText(durationLabel(estimatedSeconds), color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(8.dp))
        if (previewOnly) AppText("One-page read-along preview", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium)
        CardSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoStories, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { AppText("Autoplay pages", fontWeight = FontWeight.SemiBold); AppText("Continue to the next page automatically", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Switch(autoplay, onAutoplay, enabled = !previewOnly, colors = SwitchDefaults.colors(checkedTrackColor = PagesColors.Forest)) }
        }
        Spacer(Modifier.height(10.dp))
        AppText("Voice: ${narrationState.voices.firstOrNull { it.id == selectedVoiceId }?.label ?: "System default"} · Speed: $speed", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium)
        AppText(book.title, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { AppText("Narration settings") },
        text = {
            Column {
                AppText("Voice")
                if (narrationState.voices.isEmpty()) AppText("System default", color = PagesColors.Muted)
                else narrationState.voices.forEach { voice -> FilterChip(selected = selectedVoiceId == voice.id, onClick = { val resume = isPlaying; if (resume) resumeOffset = narrationState.activeRange?.first ?: resumeOffset; selectedVoiceId = voice.id; if (resume) isPlaying = speakFrom(resumeOffset) }, label = { AppText(voice.label) }) }
                Spacer(Modifier.height(14.dp)); AppText("Reading speed")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { speedOptions.forEach { (choice, _) -> FilterChip(selected = speed == choice, onClick = { val resume = isPlaying; if (resume) resumeOffset = narrationState.activeRange?.first ?: resumeOffset; speed = choice; if (resume) isPlaying = speakFrom(resumeOffset) }, label = { AppText(choice) }) } }
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { AppText("Done") } }
    )
}

@Composable
private fun CommunityScreen(posts: List<CommunityEntry>, onGroup: () -> Unit, onCreate: () -> Unit) {
    MainPage(title = "Parent community", subtitle = "A kind space to share the reading journey.", action = { IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, "Create post", tint = PagesColors.Forest) } }) {
        CardSurface(Modifier.fillMaxWidth().clickable(onClick = onCreate), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); AppText(" Share a thought with parents…", color = PagesColors.Muted, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)); Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest) } }
        Spacer(Modifier.height(16.dp)); ChipRow(listOf("For you", "Discussions", "Tips", "Activities"))
        Spacer(Modifier.height(14.dp)); SectionTitle("Groups to explore", "See all", onClick = onGroup)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { GroupTile("Raising Kind Kids", "312 parents", "🌱", onGroup); GroupTile("Bedtime Stories", "186 parents", "🌙", onGroup) }
        Spacer(Modifier.height(18.dp)); SectionTitle("Recent posts")
        posts.forEach { post -> CommunityPost("You", "Just now · preview only", post.body, if (post.hasPhoto) "🖼️" else "", onGroup) }
        CommunityPost("Sarah Mitchell", "2 days ago", "We finished The Brave Little Fox last night. My little one wanted to talk about courage at breakfast! 🦊", "📚", onGroup)
        CommunityPost("Amina Rahman", "Yesterday", "Looking for gentle Bangla stories for a 5-year-old. What are your family favourites?", "🌼", onGroup)
    }
}

@Composable
private fun GroupScreen(onBack: () -> Unit, onCreatePost: () -> Unit) {
    var reply by remember { mutableStateOf("") }
    val replies = remember { mutableStateListOf<String>() }
    PageScaffold(onBack, "Raising Kind Kids", action = { IconButton(onClick = onCreatePost) { Icon(Icons.Filled.MoreVert, "Group options") } }) {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Column { AppText("🌱  Raising Kind Kids", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); AppText("312 parents · Stories and ideas for caring families", color = PagesColors.Muted, modifier = Modifier.padding(top = 4.dp)); AppText("Joined", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) } }
        ChipRow(listOf("Discussion", "Resources", "Members")); SectionTitle("Latest discussion")
        CommunityPost("Priya Sharma", "3 days ago", "How do you talk to your kids about kindness when it’s hard? We read books that show different perspectives and then chat at bedtime.", "", null)
        CommunityPost("James Carter", "2 days ago", "We use the ‘kind choices’ language after reading together. It gives our son a simple way to reflect.", "", null)
        replies.forEach { message -> CommunityPost("You", "Just now", message, "", null) }
        OutlineButton("Start a discussion", Icons.Filled.Edit, onCreatePost)
        OutlinedTextField(value = reply, onValueChange = { reply = it }, placeholder = { AppText("Write a reply…") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), trailingIcon = { IconButton(onClick = { if (reply.isNotBlank()) { replies.add(reply.trim()); reply = "" } }, enabled = reply.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send reply", tint = PagesColors.Forest) } })
    }
}

@Composable
private fun CreatePostScreen(onBack: () -> Unit, onPost: (CommunityEntry) -> Unit) {
    var post by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedPhoto = it }
    PageScaffold(onBack, "Create a post", action = { TextButton(onClick = { if (post.isNotBlank()) onPost(CommunityEntry(post.trim(), selectedPhoto != null)) }, enabled = post.isNotBlank()) { AppText("Post", color = PagesColors.Forest, fontWeight = FontWeight.Bold) } }) {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.padding(start = 12.dp)) { AppText("Emma’s parent", fontWeight = FontWeight.SemiBold); AppText("Posting as a parent", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        OutlinedTextField(value = post, onValueChange = { post = it }, placeholder = { AppText("What would you like to share?") }, modifier = Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(20.dp))
        CardSurface(Modifier.fillMaxWidth().clickable { photoPicker.launch("image/*") }, color = PagesColors.Sky) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { AppText(if (selectedPhoto == null) "Add a family-friendly photo" else "Photo selected", fontWeight = FontWeight.SemiBold); if (selectedPhoto != null) AppText("Attached to this local preview", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.ChevronRight, null) } }
        AppText("Please keep children’s personal details private. Posts are visible to community members.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        PrimaryButton("Preview post", Icons.AutoMirrored.Filled.Send) { if (post.isNotBlank()) onPost(CommunityEntry(post.trim(), selectedPhoto != null)) }
        AppText("Posts are only kept for this app session until the community service is configured.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewsScreen(book: Book, onBack: () -> Unit) {
    var showWriteReview by remember { mutableStateOf(false) }
    var showReportNotice by remember { mutableStateOf(false) }
    var reviewText by remember { mutableStateOf("") }
    var reviewRating by remember { mutableIntStateOf(5) }
    var submitted by remember { mutableStateOf(false) }
    val localReviews = remember { mutableStateListOf<Pair<Int, String>>() }
    PageScaffold(onBack, "Reviews") {
        AppText(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { AppText("4.9", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Column(Modifier.padding(start = 14.dp)) { AppText("★★★★★", color = PagesColors.Gold, fontSize = 20.sp); AppText("Based on 1,240 parent reviews", color = PagesColors.Muted) } } }
        PrimaryButton("Write a review", Icons.Filled.RateReview) { showWriteReview = true }
        localReviews.forEach { (rating, body) -> CardSurface(Modifier.fillMaxWidth()) { Column { AppText("You · preview only", fontWeight = FontWeight.Bold); AppText("★".repeat(rating), color = PagesColors.Gold); AppText(body, color = PagesColors.Muted) } } }
        listOf("A lovely story my child asks for every night.", "Beautiful illustrations and a gentle message.", "We enjoyed reading this together before bed.").forEachIndexed { index, review ->
            CardSurface(Modifier.fillMaxWidth()) { Column { AppText(listOf("A. Rahman", "M. Carter", "S. Patel")[index], fontWeight = FontWeight.Bold); AppText("★★★★★", color = PagesColors.Gold); AppText(review, color = PagesColors.Muted); AppText("Report", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp).clickable { showReportNotice = true }) } }
        }
    }
    if (showWriteReview) AlertDialog(onDismissRequest = { showWriteReview = false }, title = { AppText("Write a review") }, text = { Column { AppText("Your rating"); Row { (1..5).forEach { value -> AppText(if (value <= reviewRating) "★" else "☆", color = PagesColors.Gold, fontSize = 28.sp, modifier = Modifier.clickable { reviewRating = value }.padding(end = 4.dp)) } }; OutlinedTextField(value = reviewText, onValueChange = { reviewText = it.take(800) }, label = { AppText("Share your thoughts") }, minLines = 3, isError = submitted && reviewText.isBlank()) } }, confirmButton = { TextButton(onClick = { submitted = true; if (reviewText.isNotBlank()) { localReviews.add(0, reviewRating to reviewText.trim()); reviewText = ""; submitted = false; showWriteReview = false } }) { AppText("Save preview") } }, dismissButton = { TextButton(onClick = { showWriteReview = false }) { AppText("Cancel") } })
    if (showReportNotice) AlertDialog(onDismissRequest = { showReportNotice = false }, title = { AppText("Report review") }, text = { AppText("Reports are not sent until the community moderation service is configured.") }, confirmButton = { TextButton(onClick = { showReportNotice = false }) { AppText("Got it") } })
}

@Composable
private fun OfflineScreen(onBack: () -> Unit, onBrowse: () -> Unit) {
    PageScaffold(onBack, "Available offline") {
        EmptyState(Icons.Filled.CloudOff, stringResource(R.string.no_offline_books), "Offline book storage will appear here after authorized book access and private storage are connected.", stringResource(R.string.browse_books), onBrowse)
        AppText("No book files are stored on this device by the current preview.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ParentScreen(language: String, child: ChildProfile?, onLanguage: (String) -> Unit, onProfiles: () -> Unit, onSubscription: () -> Unit, onOrders: () -> Unit, onSupport: () -> Unit, onOffline: () -> Unit, onUnavailable: (String) -> Unit) {
    MainPage(title = stringResource(R.string.parent_account), subtitle = "Manage your family’s LittlePages.") {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.padding(start = 12.dp)) { AppText("Emma’s parent", fontWeight = FontWeight.Bold); AppText("emma@example.com", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null) } }
        SectionTitle(stringResource(R.string.family_section))
        SettingsRow(Icons.Filled.ChildCare, stringResource(R.string.child_profiles), child?.let { "${it.nickname} · ${it.ageBand}" } ?: "Add a child profile", onProfiles)
        SettingsRow(Icons.Filled.Star, stringResource(R.string.premium_title), "Explore listening and more", onSubscription)
        SectionTitle(stringResource(R.string.account_section))
        SettingsRow(Icons.Filled.Language, stringResource(R.string.language_label), if (language == "bn") stringResource(R.string.language_bangla) else stringResource(R.string.language_english), { onLanguage(if (language == "bn") "en" else "bn") })
        SettingsRow(Icons.Filled.CloudDownload, stringResource(R.string.offline_books), "Manage saved stories", onOffline)
        SettingsRow(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.orders_label), "View purchases and delivery", onOrders)
        SettingsRow(Icons.Filled.SupportAgent, stringResource(R.string.support_label), "We’re here to help", onSupport)
        SettingsRow(Icons.Filled.PrivacyTip, "Privacy and safety", "Family controls", { onUnavailable("Privacy and consent controls will be added after the parent-data policy is approved.") })
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", "", { onUnavailable("There is no signed-in account in preview mode.") })
    }
}

@Composable
private fun ProfilesScreen(children: List<ChildProfile>, onBack: () -> Unit, onSaveChild: (ChildProfile?, ChildProfile) -> Unit) {
    var editing by remember { mutableStateOf<ChildProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("5 years old") }
    var submitted by remember { mutableStateOf(false) }
    PageScaffold(onBack, "Child profiles") {
        AppText("Preview profiles are held in memory only; account sync and consent settings are not configured.", color = PagesColors.Muted)
        children.forEach { child -> CardSurface(Modifier.fillMaxWidth().clickable { editing = child; nickname = child.nickname; ageBand = child.ageBand; submitted = false }) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(child.nickname.take(1).ifBlank { "?" }); Column(Modifier.weight(1f).padding(start = 12.dp)) { AppText(child.nickname, fontWeight = FontWeight.Bold); AppText("${child.ageBand} · English and বাংলা", color = PagesColors.Muted) }; Icon(Icons.Filled.Edit, "Edit profile", tint = PagesColors.Forest) } } }
        OutlineButton("Add a child profile", Icons.Filled.Add) { adding = true; editing = null; nickname = ""; ageBand = "5 years old"; submitted = false }
        AppText("Parent account controls purchases, subscriptions, and community posts.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (adding || editing != null) AlertDialog(onDismissRequest = { adding = false; editing = null }, title = { AppText(if (adding) "Add child profile" else "Edit child profile") }, text = { Column { OutlinedTextField(nickname, { nickname = it.take(30) }, label = { AppText("Nickname") }, singleLine = true, isError = submitted && nickname.isBlank()); AppText("Age range", modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("3 years old", "5 years old", "7 years old").forEach { choice -> FilterChip(selected = ageBand == choice, onClick = { ageBand = choice }, label = { AppText(choice.substringBefore(' ')) }) } }; AppText("Years", color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) } }, confirmButton = { TextButton(onClick = { submitted = true; if (nickname.isNotBlank()) { onSaveChild(editing, ChildProfile(nickname.trim(), ageBand)); adding = false; editing = null } }) { AppText("Save preview") } }, dismissButton = { TextButton(onClick = { adding = false; editing = null }) { AppText("Cancel") } })
}

@Composable
private fun SubscriptionScreen(premium: Boolean, onBack: () -> Unit, onSubscribe: () -> Unit, onPreview: () -> Unit) {
    PageScaffold(onBack, "LittlePages Premium") {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(PagesColors.Sage, PagesColors.Peach))).padding(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { AppText("✨", fontSize = 40.sp); AppText("Stories come alive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); AppText("A little extra magic for family reading time.", textAlign = TextAlign.Center, color = PagesColors.Muted, modifier = Modifier.padding(top = 8.dp)) }
        }
        listOf("Word-by-word narrated read-along", "Warm voices and adjustable speed", "More stories for your family", "Offline listening on supported books").forEach { BenefitRow(it) }
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { AppText("Family plan", fontWeight = FontWeight.Bold); AppText("Billed monthly · cancel anytime", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳399 / mo", fontWeight = FontWeight.Bold, color = PagesColors.Forest) } }
        PrimaryButton(if (premium) "Premium is active" else "Start Premium", Icons.Filled.Star, onSubscribe)
        OutlineButton("Try a one-page read-along sample", Icons.Filled.Headphones, onPreview)
        AppText("Subscriptions are managed securely through the available payment option.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CheckoutScreen(book: Book, onBack: () -> Unit, onComplete: () -> Unit) {
    var format by remember { mutableStateOf("Printed book") }
    var paymentMethod by remember { mutableStateOf("bKash") }
    var fullName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val validAddress = format != "Printed book" || (fullName.isNotBlank() && address.isNotBlank() && phone.filter(Char::isDigit).length >= 8)
    PageScaffold(onBack, "Choose a format") {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { BookCover(book, Modifier.size(68.dp)); Column(Modifier.padding(start = 12.dp)) { AppText(book.title, fontWeight = FontWeight.Bold); AppText("Choose how your family will read", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        FormatChoice("PDF · Read in app", "৳120", "Instant access. Downloading and exporting are disabled.", format == "PDF · Read in app") { format = "PDF · Read in app" }
        FormatChoice("Printed book", "৳450", "Physical edition available · delivery address required", format == "Printed book") { format = "Printed book" }
        if (format == "Printed book") {
            SectionTitle("Delivery address")
            OutlinedTextField(fullName, { fullName = it }, label = { AppText("Full name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && fullName.isBlank())
            OutlinedTextField(address, { address = it }, label = { AppText("Address and area") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && address.isBlank())
            OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(15) }, label = { AppText("Phone number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && phone.length < 8, supportingText = { if (submitted && phone.length < 8) AppText("Enter a valid phone number.") })
        }
        SectionTitle("Payment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("bKash", "Visa").forEach { method -> FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { AppText(method) }) } }
        PrimaryButton("Continue to payment", Icons.Filled.Lock) { submitted = true; if (validAddress) onComplete() }
        AppText("Selected: $paymentMethod. Payment is not connected yet. PDFs are for in-app reading only.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OrdersScreen(onBack: () -> Unit) {
    PageScaffold(onBack, "Your orders") {
        EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Order history is empty", "Completed orders will appear here after secure checkout is connected.")
    }
}

@Composable
private fun SupportScreen(onBack: () -> Unit, onSubmit: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    PageScaffold(onBack, "Help and support") {
        AppText("Tell us what happened and our team will help.", color = PagesColors.Muted)
        SettingsRow(Icons.Filled.CloudOff, "Offline reading help", "Download or sync troubleshooting", { message = "I need help with offline books or sync." })
        SettingsRow(Icons.Filled.Payment, "Payment and orders", "Billing, refunds, and delivery", { message = "I need help with a payment or order." })
        SettingsRow(Icons.Filled.AccountCircle, "Account and profiles", "Sign-in and family settings", { message = "I need help with my account or child profiles." })
        SectionTitle("Contact us")
        OutlinedTextField(email, { email = it }, label = { AppText("Your email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true, isError = submitted && !validEmail, supportingText = { if (submitted && !validEmail) AppText("Enter a valid email address.") })
        OutlinedTextField(message, { message = it.take(2000) }, label = { AppText("How can we help?") }, modifier = Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(16.dp), isError = submitted && message.isBlank())
        PrimaryButton("Send support request", Icons.AutoMirrored.Filled.Send) { submitted = true; if (validEmail && message.isNotBlank()) onSubmit() }
        AppText("This preview does not send or store support messages.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit, onSubscribe: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PagesColors.Paper,
        shape = RoundedCornerShape(28.dp),
        icon = { AppText("🎧✨", fontSize = 30.sp) },
        title = { AppText("Make story time magical", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = { AppText("Listen to the story with each word lighting up as it’s read. Premium is managed by a parent.", textAlign = TextAlign.Center, color = PagesColors.Muted) },
        confirmButton = { Button(onClick = onSubscribe, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest)) { AppText("Explore Premium") } },
        dismissButton = { TextButton(onClick = onDismiss) { AppText("Not now", color = PagesColors.Forest) } }
    )
}
