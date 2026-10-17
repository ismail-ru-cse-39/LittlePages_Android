package com.littlepages.app.ui

import android.app.Activity
import android.app.Application
import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.littlepages.app.data.ReaderProgressStore
import com.littlepages.app.data.AndroidNarrationEngine
import com.littlepages.app.data.NarrationAudioFocus
import com.littlepages.app.data.NarrationState
import com.littlepages.app.data.SamplePdfRenderer
import com.littlepages.app.data.ManagedCategory
import com.littlepages.app.data.PreviewCatalogConfigurationSource
import com.littlepages.app.data.FirestorePublishedStorefrontConfigurationRemote
import com.littlepages.app.data.SharedPreferencesStorefrontConfigurationCache
import com.littlepages.app.data.VersionedStorefrontConfigurationRepository
import com.littlepages.app.AppLanguage
import com.littlepages.app.BuildConfig
import com.littlepages.app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.littlepages.app.ui.components.*
import com.littlepages.app.ui.theme.PagesColors
import com.littlepages.app.ui.theme.PagesBreakpoints
import com.littlepages.app.ui.theme.PagesMotion
import com.littlepages.app.ui.theme.PagesSpacing
import com.littlepages.app.ui.theme.PagesSizes
import com.littlepages.app.ui.theme.PagesShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private enum class Page {
    Welcome, EmailAuth, EmailVerification, PasswordReset, PhoneAuth, PhoneCode, ParentSetup, Home, Library, Search, BookDetails, Reader, Listen, ListenPreview,
    Community, Group, CreatePost, Reviews, Offline, Parent, Profiles, Privacy, Subscription,
    Checkout, OrderConfirmation, Recovery, Orders, Support
}

internal data class Book(val title: String, val author: String, val age: String, val emoji: String, val tint: Color, val rating: String = "4.9", val categories: Set<String> = emptySet(), val minimumAge: Int = 0, val maximumAge: Int = 12, val hasBangla: Boolean = true, val isAvailable: Boolean = true, val pdfAvailable: Boolean = true, val physicalAvailable: Boolean = true, val premiumOnly: Boolean = false)
private val books = listOf(
    Book("The Brave Little Fox", "Emma Clarke", "Ages 3–7", "🦊", PagesColors.Sky, categories = setOf("animals", "friendship", "nature"), minimumAge = 3, maximumAge = 7),
    Book("A Day at the Lake", "Mina Rahman", "Ages 4–8", "🐻", PagesColors.Sage, categories = setOf("animals", "nature"), minimumAge = 4, maximumAge = 8, physicalAvailable = false),
    Book("The Kind Giant", "Oliver Reed", "Ages 5–9", "🌿", PagesColors.Peach, categories = setOf("friendship", "nature"), minimumAge = 5, maximumAge = 9, pdfAvailable = false, premiumOnly = true),
    Book("The Star Keeper", "Nadia Noor", "Ages 4–8", "🌙", Color(0xFFE8E4F3), categories = setOf("nature", "bedtime"), minimumAge = 4, maximumAge = 8, isAvailable = false, pdfAvailable = false, physicalAvailable = false)
)
private data class CommunityEntry(val body: String, val imageUri: android.net.Uri? = null, val category: String = "stories", val author: String = "You", val time: String = "Just now") {
    val hasPhoto: Boolean get() = imageUri != null
}
private data class ChildProfile(val nickname: String, val ageBand: String, val id: String = "${nickname.lowercase()}-${ageBand.filter(Char::isDigit)}")
private data class PurchasePreview(val id: String, val bookTitle: String, val format: String, val paymentMethod: String, val fullName: String = "", val address: String = "", val phone: String = "")

private fun firebaseAuthError(error: Exception?): String = when ((error as? FirebaseAuthException)?.errorCode) {
    "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Email or password is incorrect."
    "ERROR_INVALID_VERIFICATION_CODE" -> "That SMS code is incorrect. Check it and try again."
    "ERROR_SESSION_EXPIRED" -> "That SMS code expired. Request another code."
    "ERROR_INVALID_PHONE_NUMBER" -> "Enter a valid Bangladesh mobile number with the +880 country code."
    "ERROR_QUOTA_EXCEEDED" -> "SMS verification is temporarily limited. Try again later."
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "This account already uses another sign-in method. Sign in with that method."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses this email. Sign in instead."
    "ERROR_WEAK_PASSWORD" -> "Choose a stronger password with at least 8 characters."
    "ERROR_OPERATION_NOT_ALLOWED" -> "Email and password sign-in is not enabled for this Firebase project."
    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a little and try again."
    "ERROR_NETWORK_REQUEST_FAILED" -> "Check your internet connection and try again."
    else -> "Check your connection and Firebase Authentication settings, then try again."
}

@Composable
fun LittlePagesApp() {
    val navigationStackSaver = remember {
        listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Page>, String>(
            save = { routeStack -> routeStack.map(Page::name) },
            restore = { savedRoutes -> savedRoutes.map(Page::valueOf).toMutableStateList() }
        )
    }
    val stack = rememberSaveable(saver = navigationStackSaver) { mutableStateListOf(Page.Welcome) }
    var guestMode by rememberSaveable { mutableStateOf(false) }
    var parentPreviewMode by rememberSaveable { mutableStateOf(false) }
    var premium by remember { mutableStateOf(false) }
    var premiumPrompt by remember { mutableStateOf(false) }
    var unavailableNotice by remember { mutableStateOf<String?>(null) }
    var noticeTitle by remember { mutableStateOf("Not available yet") }
    var authBusy by remember { mutableStateOf(false) }
    var recoveryType by remember { mutableStateOf("book") }
    var phoneNumber by remember { mutableStateOf("") }
    var pendingParentEmail by remember { mutableStateOf("") }
    var previewParentName by rememberSaveable { mutableStateOf("") }
    var previewParentEmail by rememberSaveable { mutableStateOf("") }
    var lastPurchase by remember { mutableStateOf<PurchasePreview?>(null) }
    var selectedBook by remember { mutableStateOf(books.first()) }
    var query by remember { mutableStateOf("") }
    val appConfiguration = LocalConfiguration.current
    val useNavigationRail = appConfiguration.screenWidthDp >= PagesBreakpoints.WideLayoutWidth.value.toInt()
    val mainTabs = listOf(Page.Home, Page.Library, Page.Community, Page.Parent, Page.Offline)
    val appContext = LocalContext.current
    val firebaseAuth = remember(appContext) {
        if (BuildConfig.FIREBASE_CONFIGURED) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
    }
    var firebaseUser by remember(firebaseAuth) { mutableStateOf(firebaseAuth?.currentUser) }
    var pendingVerificationEmail by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var phoneRequestNumber by rememberSaveable { mutableStateOf("") }
    var newEmailAccountNeedsSetup by rememberSaveable { mutableStateOf(false) }
    var pendingAccountLinkCredential by remember { mutableStateOf<AuthCredential?>(null) }
    val isAuthenticatedUser = firebaseUser?.let { user ->
        user.email == null || user.isEmailVerified || user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    } == true
    val authenticatedEmail = firebaseUser?.takeIf { isAuthenticatedUser }?.email
    DisposableEffect(firebaseAuth) {
        if (firebaseAuth == null) return@DisposableEffect onDispose { }
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }
    val coroutineScope = rememberCoroutineScope()
    val activity = appContext as? Activity
    val configurationSource = remember(appContext) {
        val preview = PreviewCatalogConfigurationSource()
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            preview
        } else {
            runCatching {
                val cache = SharedPreferencesStorefrontConfigurationCache(appContext)
                VersionedStorefrontConfigurationRepository(
                    remote = FirestorePublishedStorefrontConfigurationRemote(FirebaseFirestore.getInstance(), cache),
                    cache = cache,
                    fallback = preview.configuration.value
                )
            }.getOrElse { preview }
        }
    }
    LaunchedEffect(configurationSource) {
        (configurationSource as? VersionedStorefrontConfigurationRepository)?.initialize()
    }
    DisposableEffect(configurationSource, activity) {
        val repository = configurationSource as? VersionedStorefrontConfigurationRepository
        if (repository == null || !BuildConfig.FIREBASE_CONFIGURED) {
            return@DisposableEffect onDispose { }
        }
        var receivedInitialManifest = false
        val manifestListener = FirebaseFirestore.getInstance()
            .collection("publicConfiguration")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    if (receivedInitialManifest) {
                        coroutineScope.launch { repository.refresh() }
                    } else {
                        receivedInitialManifest = true
                    }
                }
            }
        var hasResumedOnce = false
        val application = appContext.applicationContext as? Application
        val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(resumedActivity: Activity) {
                if (resumedActivity !== activity) return
                if (hasResumedOnce) coroutineScope.launch { repository.refresh() }
                else hasResumedOnce = true
            }
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application?.registerActivityLifecycleCallbacks(activityCallbacks)
        onDispose {
            manifestListener.remove()
            application?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        }
    }
    val managedConfiguration by configurationSource.configuration.collectAsState()
    val systemAnimationsEnabled = ValueAnimator.areAnimatorsEnabled()
    var hasInternet by remember(appContext) { mutableStateOf(true) }
    DisposableEffect(appContext) {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var currentNetwork: Network? = connectivity.activeNetwork
        fun hasValidatedInternet(): Boolean {
            val network = connectivity.activeNetwork ?: return false
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { currentNetwork = network }
            override fun onLost(network: Network) {
                if (currentNetwork == network) { currentNetwork = null; hasInternet = false }
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (currentNetwork == network) {
                    val wasConnected = hasInternet
                    hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (!wasConnected && hasInternet) {
                        (configurationSource as? VersionedStorefrontConfigurationRepository)?.let { repository ->
                            coroutineScope.launch { repository.refresh() }
                        }
                    }
                }
            }
        }
        hasInternet = hasValidatedInternet()
        connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    var selectedLanguage by remember(appConfiguration) { mutableStateOf(appConfiguration.locales[0].language.ifBlank { "en" }) }
    var autoplay by remember { mutableStateOf(true) }
    var readerMode by remember { mutableStateOf("Page turn") }
    val progressStore = remember(appContext) { ReaderProgressStore(appContext) }
    val childProfiles = remember { mutableStateListOf(ChildProfile("Emma", "5 years old")) }
    var selectedChildId by rememberSaveable { mutableStateOf(childProfiles.first().id) }
    val activeChildId = childProfiles.firstOrNull { it.id == selectedChildId }?.id ?: childProfiles.first().id
    var currentPage by remember(selectedBook.title, activeChildId) { mutableIntStateOf(progressStore.page(activeChildId, selectedBook.title).coerceIn(1, 24)) }
    var bookmarked by remember(selectedBook.title, activeChildId, currentPage) { mutableStateOf(progressStore.isBookmarked(activeChildId, selectedBook.title, currentPage)) }
    val communityPosts = remember { mutableStateListOf<CommunityEntry>() }
    val groupPosts = remember { mutableStateListOf<CommunityEntry>() }
    val blockedCommunityAuthors = remember { mutableStateListOf<String>() }
    val offlineBookTitles = remember { mutableStateListOf(books[0].title, books[1].title) }
    val purchaseHistory = remember { mutableStateListOf<PurchasePreview>() }
    val savedBooks = remember { mutableStateListOf<String>() }
    val page = stack.last()
    fun go(next: Page) { stack.add(next) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun showNotice(title: String, message: String) { noticeTitle = title; unavailableNotice = message }
    fun signOutFromAuth() {
        firebaseAuth?.signOut()
        coroutineScope.launch {
            runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }
    fun sendCurrentEmailVerification() {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            showNotice("Verification unavailable", "Sign in again to request a verification email.")
            return
        }
        authBusy = true
        firebaseAuth?.useAppLanguage()
        user.sendEmailVerification().addOnCompleteListener { task ->
            authBusy = false
            if (task.isSuccessful) {
                pendingVerificationEmail = user.email.orEmpty()
                stack.clear(); stack.add(Page.EmailVerification)
                showNotice("Verification email sent", "Open the link we emailed to ${user.email.orEmpty()}, then return here and tap ‘I’ve verified’. Check your spam folder if it doesn’t arrive.")
            } else showNotice("Couldn’t send verification email", firebaseAuthError(task.exception))
        }
    }
    fun runAuth(operation: (FirebaseAuth) -> com.google.android.gms.tasks.Task<*>, onSuccess: () -> Unit = {}) {
        val auth = firebaseAuth
        if (auth == null) {
            showNotice("Firebase isn’t configured", "Add the Firebase Android configuration for this build before using email accounts.")
            return
        }
        authBusy = true
        runCatching { operation(auth).addOnCompleteListener { task ->
            authBusy = false
            if (task.isSuccessful) onSuccess() else showNotice("Couldn’t complete that request", firebaseAuthError(task.exception))
        } }.onFailure {
            authBusy = false
            showNotice("Couldn’t complete that request", firebaseAuthError(it as? Exception))
        }
    }
    fun enterAuthenticatedHome() {
        parentPreviewMode = false; guestMode = false; stack.clear(); stack.add(Page.Home)
    }
    fun finishEmailSignIn() {
        val user = firebaseAuth?.currentUser
        val credential = pendingAccountLinkCredential
        if (user != null && credential != null) {
            authBusy = true
            user.linkWithCredential(credential).addOnCompleteListener { task ->
                authBusy = false
                if (task.isSuccessful) {
                    pendingAccountLinkCredential = null
                    firebaseUser = task.result.user
                    enterAuthenticatedHome()
                } else showNotice("Couldn’t link sign-in methods", firebaseAuthError(task.exception))
            }
        } else enterAuthenticatedHome()
    }
    fun handleAccountCollision(error: Exception?, method: String): Boolean {
        val collision = error as? FirebaseAuthUserCollisionException ?: return false
        val credential = collision.updatedCredential ?: return false
        pendingAccountLinkCredential = credential
        showNotice("This account already exists", "Sign in with the existing email and password. We’ll link $method to that account after you sign in.")
        return true
    }
    fun returnToWelcome(signOut: Boolean) {
        if (signOut) signOutFromAuth()
        guestMode = false; parentPreviewMode = false; stack.clear(); stack.add(Page.Welcome)
    }
    fun finishPhoneSignIn(result: com.google.firebase.auth.AuthResult) {
        authBusy = false
        firebaseUser = result.user
        pendingParentEmail = ""
        previewParentEmail = result.user?.phoneNumber.orEmpty()
        if (result.additionalUserInfo?.isNewUser == true) {
            parentPreviewMode = false; guestMode = false; stack.clear(); stack.add(Page.Welcome); stack.add(Page.ParentSetup)
        } else {
            finishEmailSignIn()
        }
    }
    fun requestPhoneVerification(number: String, resendToken: PhoneAuthProvider.ForceResendingToken? = null) {
        val auth = firebaseAuth
        val hostActivity = activity
        if (auth == null || hostActivity == null) {
            showNotice("Firebase isn’t ready", "Configure Firebase for this build and try again.")
            return
        }
        phoneRequestNumber = number
        verificationId = null
        authBusy = true
        auth.useAppLanguage()
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    if (task.isSuccessful) finishPhoneSignIn(task.result) else {
                        authBusy = false
                        if (!handleAccountCollision(task.exception, "phone sign-in")) showNotice("Phone sign-in failed", firebaseAuthError(task.exception))
                    }
                }
            }
            override fun onVerificationFailed(error: FirebaseException) {
                authBusy = false
                showNotice("Couldn’t send verification code", firebaseAuthError(error as? Exception))
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                authBusy = false
                verificationId = id
                phoneResendToken = token
                if (stack.lastOrNull() != Page.PhoneCode) stack.add(Page.PhoneCode)
            }
            override fun onCodeAutoRetrievalTimeOut(id: String) {
                verificationId = id
                authBusy = false
            }
        }
        runCatching {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(number)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(hostActivity)
                .setCallbacks(callbacks)
                .apply { if (resendToken != null) setForceResendingToken(resendToken) }
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }.onFailure {
            authBusy = false
            showNotice("Couldn’t send verification code", firebaseAuthError(it as? Exception))
        }
    }
    fun launchGoogleSignIn() {
        val auth = firebaseAuth
        val webClientId = BuildConfig.FIREBASE_WEB_CLIENT_ID
        if (auth == null || webClientId.isBlank()) {
            showNotice("Google sign-in needs setup", "Enable Google in Firebase Authentication, add this app’s SHA-1 and SHA-256 fingerprints, then add the Firebase Web client ID to local.properties and refresh the Firebase Android config.")
            return
        }
        authBusy = true
        coroutineScope.launch {
            try {
                val googleOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                val result = CredentialManager.create(appContext).getCredential(appContext, request)
                val credential = result.credential
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    authBusy = false
                    showNotice("Google sign-in failed", "Choose a Google account and try again.")
                    return@launch
                }
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).addOnCompleteListener { task ->
                    authBusy = false
                    if (task.isSuccessful) {
                        firebaseUser = task.result.user
                        previewParentEmail = task.result.user?.email.orEmpty()
                        parentPreviewMode = false; guestMode = false
                        if (task.result.additionalUserInfo?.isNewUser == true) {
                            pendingParentEmail = previewParentEmail
                            stack.clear(); stack.add(Page.Welcome); stack.add(Page.ParentSetup)
                        } else { stack.clear(); stack.add(Page.Home) }
                    } else if (!handleAccountCollision(task.exception, "Google sign-in")) showNotice("Google sign-in failed", firebaseAuthError(task.exception))
                }
            } catch (error: GetCredentialException) {
                authBusy = false
                if (error.javaClass.simpleName.contains("Cancellation", ignoreCase = true)) return@launch
                showNotice("Google sign-in was cancelled or unavailable", error.localizedMessage ?: "Try again or use email sign-in.")
            } catch (error: Exception) {
                authBusy = false
                showNotice("Google sign-in failed", error.localizedMessage ?: "Try again or use email sign-in.")
            }
        }
    }
    LaunchedEffect(firebaseUser) {
        val current = firebaseUser ?: return@LaunchedEffect
        val isGoogleAccount = current.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        if (current.email != null && !current.isEmailVerified && !isGoogleAccount) {
            pendingVerificationEmail = current.email.orEmpty()
            if (stack.lastOrNull() != Page.EmailVerification) { stack.clear(); stack.add(Page.EmailVerification) }
        } else if (stack.lastOrNull() == Page.Welcome) {
            guestMode = false; parentPreviewMode = false; stack.clear(); stack.add(Page.Home)
        }
    }
    fun openPremiumFeature() { if (premium) go(Page.Listen) else premiumPrompt = true }

    BackHandler(enabled = stack.size > 1 || premiumPrompt) {
        if (premiumPrompt) premiumPrompt = false else back()
    }

    Scaffold(
        containerColor = PagesColors.Cream,
        bottomBar = {
            if (!useNavigationRail && page in mainTabs) {
                NavigationBar(containerColor = PagesColors.Paper, tonalElevation = PagesSpacing.None) {
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
                            icon = { Icon(icon, contentDescription = localizedAppCopy(label)) },
                            label = { AppText(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = PagesColors.Forest, indicatorColor = PagesColors.Sage)
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
          if (!hasInternet) NetworkStatusBanner()
          Row(Modifier.weight(1f).fillMaxWidth()) {
          if (useNavigationRail && page in mainTabs) {
            NavigationRail(containerColor = PagesColors.Paper) {
              listOf(
                Triple(Page.Home, stringResource(R.string.nav_home), Icons.Filled.Home),
                Triple(Page.Library, stringResource(R.string.nav_library), Icons.Filled.AutoStories),
                Triple(Page.Community, stringResource(R.string.nav_community), Icons.Filled.Groups),
                Triple(Page.Parent, stringResource(R.string.nav_profile), Icons.Filled.AccountCircle)
              ).forEach { (destination, label, icon) ->
                NavigationRailItem(selected = page == destination, onClick = { if (page != destination) { stack.clear(); stack.add(destination) } }, icon = { Icon(icon, contentDescription = localizedAppCopy(label)) }, label = { AppText(label, fontSize = 11.sp) }, alwaysShowLabel = false, colors = NavigationRailItemDefaults.colors(selectedIconColor = PagesColors.Forest, indicatorColor = PagesColors.Sage))
              }
            }
          }
          AnimatedContent(
            targetState = page,
            transitionSpec = { if (systemAnimationsEnabled) fadeIn(tween(PagesMotion.PageTransitionMillis)) togetherWith fadeOut(tween(PagesMotion.PageTransitionMillis)) else EnterTransition.None togetherWith ExitTransition.None },
            label = "page-transition",
            modifier = Modifier.weight(1f).fillMaxHeight()
          ) { target ->
            when (target) {
                Page.Welcome -> WelcomeScreen(
                    onGoogle = ::launchGoogleSignIn, onEmail = { go(Page.EmailAuth) }, onPhone = { go(Page.PhoneAuth) }, onSignIn = { go(Page.EmailAuth) }, onGuest = { signOutFromAuth(); guestMode = true; parentPreviewMode = false; stack.clear(); stack.add(Page.Home) }, onParentPreview = { signOutFromAuth(); guestMode = false; parentPreviewMode = true; stack.clear(); stack.add(Page.Home) }
                )
                Page.EmailAuth -> EmailAuthScreen(busy = authBusy, onBack = ::back, onContinue = { creatingAccount, email, password ->
                    runAuth(
                        operation = { auth -> if (creatingAccount) auth.createUserWithEmailAndPassword(email, password) else auth.signInWithEmailAndPassword(email, password) },
                        onSuccess = {
                            pendingParentEmail = email
                            val user = firebaseAuth?.currentUser
                            if (creatingAccount || user?.isEmailVerified == false) {
                                newEmailAccountNeedsSetup = creatingAccount
                                sendCurrentEmailVerification()
                            } else { previewParentEmail = email; finishEmailSignIn() }
                        }
                    )
                }, onGoogle = ::launchGoogleSignIn, onPhone = { go(Page.PhoneAuth) }, onPasswordReset = { go(Page.PasswordReset) })
                Page.EmailVerification -> EmailVerificationScreen(
                    email = pendingVerificationEmail,
                    busy = authBusy,
                    onCheck = {
                        val user = firebaseAuth?.currentUser
                        if (user == null) showNotice("Sign in again", "The account session expired. Sign in and request another verification email.")
                        else {
                            authBusy = true
                            user.reload().addOnCompleteListener { task ->
                                authBusy = false
                                if (task.isSuccessful) {
                                    firebaseUser = firebaseAuth.currentUser
                                    if (firebaseAuth.currentUser?.isEmailVerified == true) {
                                        previewParentEmail = pendingVerificationEmail
                                        if (newEmailAccountNeedsSetup) { newEmailAccountNeedsSetup = false; pendingParentEmail = pendingVerificationEmail; stack.clear(); stack.add(Page.Welcome); stack.add(Page.ParentSetup) }
                                        else finishEmailSignIn()
                                    } else showNotice("Email not verified yet", "Open the verification link from your email, then try again.")
                                } else showNotice("Couldn’t check verification", firebaseAuthError(task.exception))
                            }
                        }
                    },
                    onResend = ::sendCurrentEmailVerification,
                    onSignOut = { signOutFromAuth(); newEmailAccountNeedsSetup = false; pendingVerificationEmail = ""; stack.clear(); stack.add(Page.Welcome) }
                )
                Page.PasswordReset -> PasswordResetScreen(busy = authBusy, onBack = ::back, onSubmit = { email ->
                    runAuth(operation = { it.sendPasswordResetEmail(email) }, onSuccess = { showNotice("Check your email", "If an account exists for this address, Firebase has sent password reset instructions.") })
                })
                Page.PhoneAuth -> PhoneAuthScreen(busy = authBusy, onBack = ::back, onContinue = ::requestPhoneVerification, onEmail = { go(Page.EmailAuth) })
                Page.PhoneCode -> PhoneVerificationScreen(phoneNumber = phoneRequestNumber, busy = authBusy, onBack = ::back, onVerify = { code ->
                    val auth = firebaseAuth
                    val id = verificationId
                    if (auth == null || id == null) showNotice("Verification expired", "Request a new SMS code and try again.")
                    else {
                        authBusy = true
                        runCatching { auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code)).addOnCompleteListener { task ->
                            if (task.isSuccessful) finishPhoneSignIn(task.result) else {
                                authBusy = false
                                if (!handleAccountCollision(task.exception, "phone sign-in")) showNotice("Phone verification failed", firebaseAuthError(task.exception))
                            }
                        } }.onFailure { authBusy = false; showNotice("Phone verification failed", firebaseAuthError(it as? Exception)) }
                    }
                }, onResend = { requestPhoneVerification(phoneRequestNumber, phoneResendToken) })
                Page.ParentSetup -> ParentOnboardingScreen(email = pendingParentEmail.ifBlank { previewParentEmail }, onBack = ::back, onComplete = { name, email, child -> previewParentName = name; previewParentEmail = email; childProfiles.add(child); selectedChildId = child.id; parentPreviewMode = false; guestMode = false; stack.clear(); stack.add(Page.Home) })
                Page.Home -> HomeScreen(continuePage = progressStore.page(activeChildId, books[0].title).coerceIn(1, 24), onBook = { selectedBook = it; go(Page.BookDetails) }, onContinue = { selectedBook = books[0]; go(Page.Reader) }, onLibrary = { stack.clear(); stack.add(Page.Library) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) }, onPremium = { go(Page.Subscription) })
                Page.Library -> LibraryScreen(categories = managedConfiguration.bookCategories, onBook = { selectedBook = it; go(Page.BookDetails) }, onSearch = { go(Page.Search) }, onOffline = { go(Page.Offline) })
                Page.Search -> SearchScreen(query = query, categories = managedConfiguration.bookCategories, onQuery = { query = it }, onBook = { selectedBook = it; go(Page.BookDetails) }, onBack = ::back)
                Page.BookDetails -> BookDetailsScreen(book = selectedBook, categories = managedConfiguration.bookCategories, saved = selectedBook.title in savedBooks, premiumUnlocked = premium, onPremiumRequired = { if (guestMode) unavailableNotice = "Create a parent account to explore Premium." else premiumPrompt = true }, onSave = { if (guestMode) unavailableNotice = "Create a parent account to save books." else if (selectedBook.title in savedBooks) savedBooks.remove(selectedBook.title) else savedBooks.add(selectedBook.title) }, onBack = ::back, onRead = { if (selectedBook.premiumOnly && !premium) { if (guestMode) unavailableNotice = "Create a parent account to explore Premium." else premiumPrompt = true } else go(Page.Reader) }, onListen = { if (guestMode) unavailableNotice = "Create a parent account to use Premium listening." else openPremiumFeature() }, onReviews = { go(Page.Reviews) }, onOrder = { if (guestMode) unavailableNotice = "Create a parent account to order books." else go(Page.Checkout) }, onRecovery = { recoveryType = "book"; go(Page.Recovery) })
                Page.Reader -> ReaderScreen(book = selectedBook, mode = readerMode, page = currentPage, bookmarked = bookmarked, onBookmark = { bookmarked = progressStore.toggleBookmark(activeChildId, selectedBook.title, currentPage) }, onPage = { currentPage = it; progressStore.savePage(activeChildId, selectedBook.title, it) }, onMode = { readerMode = it }, onBack = ::back, onListen = ::openPremiumFeature, onPremium = { premiumPrompt = true })
                Page.Listen -> ListenScreen(book = selectedBook, page = currentPage, autoplay = autoplay, onAutoplay = { autoplay = it }, onPageAdvance = { if (currentPage < 24) { currentPage += 1; progressStore.savePage(activeChildId, selectedBook.title, currentPage) } }, onBack = ::back)
                Page.ListenPreview -> ListenScreen(book = selectedBook, page = currentPage, autoplay = false, onAutoplay = {}, onPageAdvance = {}, onBack = ::back, previewOnly = true)
                Page.Community -> CommunityScreen(posts = communityPosts, categories = managedConfiguration.communityCategories, blockedAuthors = blockedCommunityAuthors, onBlockAuthor = { if (it !in blockedCommunityAuthors) blockedCommunityAuthors.add(it) }, onGroup = { if (guestMode) unavailableNotice = "Create a parent account to join a group." else go(Page.Group) }, onCreate = { if (guestMode) unavailableNotice = "Create a parent account to join the discussion." else go(Page.CreatePost) })
                Page.Group -> GroupScreen(posts = groupPosts, blockedAuthors = blockedCommunityAuthors, onBlockAuthor = { if (it !in blockedCommunityAuthors) blockedCommunityAuthors.add(it) }, canParticipate = !guestMode, onBack = ::back, onCreatePost = { if (guestMode) unavailableNotice = "Create a parent account to join the discussion." else go(Page.CreatePost) })
                Page.CreatePost -> CreatePostScreen(categories = managedConfiguration.communityCategories, maximumImageBytes = managedConfiguration.maximumCommunityImageBytes, allowedImageMimeTypes = managedConfiguration.allowedCommunityImageMimeTypes, onBack = ::back, onPost = { post -> if (stack.getOrNull(stack.lastIndex - 1) == Page.Group) groupPosts.add(0, post) else communityPosts.add(0, post); back() })
                Page.Reviews -> ReviewsScreen(book = selectedBook, onBack = ::back)
                Page.Offline -> OfflineScreen(books = books.filter { it.title in offlineBookTitles }, onBack = ::back, onBrowse = { stack.clear(); stack.add(Page.Library) }, onRemove = { offlineBookTitles.remove(it.title) }, onRetrySync = { recoveryType = "offline"; go(Page.Recovery) })
                Page.Parent -> ParentScreen(guest = guestMode, previewAccount = parentPreviewMode, authenticated = isAuthenticatedUser, parentName = previewParentName, parentEmail = authenticatedEmail ?: previewParentEmail, language = selectedLanguage, child = childProfiles.firstOrNull { it.id == activeChildId }, onLanguage = { tag -> selectedLanguage = tag; activity?.let { AppLanguage.set(it, tag) } }, onProfiles = { if (guestMode) unavailableNotice = "Create a parent account to add child profiles." else go(Page.Profiles) }, onSubscription = { if (guestMode) unavailableNotice = "Create a parent account to explore Premium." else go(Page.Subscription) }, onOrders = { if (guestMode) unavailableNotice = "Sign in to view orders." else go(Page.Orders) }, onSupport = { go(Page.Support) }, onOffline = { if (guestMode) unavailableNotice = "Sign in to manage offline books." else go(Page.Offline) }, onPrivacy = { go(Page.Privacy) }, onUnavailable = { message -> if (guestMode || parentPreviewMode || isAuthenticatedUser) returnToWelcome(signOut = isAuthenticatedUser) else unavailableNotice = message }, onCreateAccount = { returnToWelcome(signOut = isAuthenticatedUser) })
                Page.Profiles -> ProfilesScreen(children = childProfiles, selectedChildId = activeChildId, progressStore = progressStore, onSelectChild = { selectedChildId = it }, onBack = ::back, onSaveChild = { old, updated -> if (old == null) childProfiles.add(updated) else { val index = childProfiles.indexOf(old); if (index >= 0) childProfiles[index] = updated } })
                Page.Privacy -> PrivacyScreen(onBack = ::back)
                Page.Subscription -> SubscriptionScreen(premium = premium, onBack = ::back, onSubscribe = { unavailableNotice = "Subscription checkout is not connected yet. Premium access will only activate after a verified payment." }, onPreview = { go(Page.ListenPreview) }, onPaymentRecovery = { recoveryType = "payment"; go(Page.Recovery) })
                Page.Checkout -> CheckoutScreen(book = selectedBook, onBack = ::back, onComplete = { format, method, name, address, phone -> PurchasePreview("LP-PREVIEW-${System.currentTimeMillis()}", selectedBook.title, format, method, name, address, phone).also { lastPurchase = it; purchaseHistory.add(0, it); go(Page.OrderConfirmation) } }, onPaymentRecovery = { recoveryType = "payment"; go(Page.Recovery) })
                Page.OrderConfirmation -> OrderConfirmationScreen(order = lastPurchase, onViewOrders = { stack.clear(); stack.add(Page.Orders) }, onContinueReading = { stack.clear(); stack.add(Page.Reader) })
                Page.Recovery -> ServiceRecoveryScreen(type = recoveryType, onBack = ::back, onRetry = { unavailableNotice = when (recoveryType) { "payment" -> "Payment is not connected. No charge was made and your order was not placed."; "offline" -> "Offline storage is not connected. No book files were changed."; else -> "Book access is not connected. Your reading progress is unchanged." } })
                Page.Orders -> OrdersScreen(orders = purchaseHistory, onBack = ::back)
                Page.Support -> SupportScreen(onBack = ::back, onSubmit = { unavailableNotice = "Support requests are not connected yet. Your message has not been sent." })
            }
          }
          }
        }

        if (premiumPrompt) {
            PremiumDialog(onDismiss = { premiumPrompt = false }, onSubscribe = { premiumPrompt = false; go(Page.Subscription) })
        }
        unavailableNotice?.let { message ->
            AlertDialog(onDismissRequest = { unavailableNotice = null }, title = { AppText(noticeTitle) }, text = { AppText(message) }, confirmButton = { TextButton(onClick = { unavailableNotice = null }) { AppText("OK") } })
        }
    }
}

@Composable
private fun NetworkStatusBanner() {
    Surface(color = PagesColors.Sage, contentColor = PagesColors.Ink) {
        Row(Modifier.fillMaxWidth().padding(horizontal = PagesSpacing.Medium, vertical = PagesSpacing.ControlGap), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = PagesColors.Forest, modifier = Modifier.size(PagesSizes.IconMedium))
            AppText("You're offline. Only locally available content can be opened.", color = PagesColors.Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = PagesSpacing.ControlGap))
        }
    }
}

@Composable
private fun WelcomeScreen(onGoogle: () -> Unit, onEmail: () -> Unit, onPhone: () -> Unit, onSignIn: () -> Unit, onGuest: () -> Unit, onParentPreview: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compactHeight = maxHeight < PagesBreakpoints.CompactHeight
      Column(
        Modifier.widthIn(max = PagesBreakpoints.WelcomeContentMaxWidth).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal = if (maxWidth < PagesBreakpoints.CompactWidth) PagesSpacing.Medium else PagesSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(if (compactHeight) PagesSpacing.Compact else PagesSpacing.ExtraLarge))
        BrandMark()
        Spacer(Modifier.height(PagesSpacing.Medium))
        AppText("A little world\nof stories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PagesColors.Ink, lineHeight = 42.sp)
        AppText("A parent account for your family’s reading journey", style = MaterialTheme.typography.bodyLarge, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = PagesSpacing.ControlGap))
        Spacer(Modifier.height(PagesSpacing.Small))
        Box(Modifier.fillMaxWidth().height(if (compactHeight) PagesSizes.WelcomeArtCompactHeight else PagesSizes.WelcomeArtHeight).clip(RoundedCornerShape(PagesShapes.Hero)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Cream))), contentAlignment = Alignment.Center) {
            AppText("🌿     🐰📖🐰     🌼", fontSize = 38.sp)
        }
        Spacer(Modifier.height(PagesSpacing.Relaxed))
        PrimaryButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
        Spacer(Modifier.height(PagesSpacing.ControlGap))
        OutlineButton("Continue with phone", Icons.Filled.Phone, onPhone)
        TextButton(onClick = onGuest, modifier = Modifier.padding(top = PagesSpacing.Small)) { AppText("Continue as guest", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onParentPreview) { AppText("Explore parent features · preview", color = PagesColors.Forest) }
        Spacer(Modifier.height(PagesSpacing.Medium))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("Already have an account? ", color = PagesColors.Muted)
            TextButton(onClick = onSignIn, contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("Sign in", color = PagesColors.Forest, fontWeight = FontWeight.Bold) }
        }
        AppText("By continuing, you agree to our Terms and Privacy Policy.", style = MaterialTheme.typography.labelSmall, color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = PagesSpacing.Medium, bottom = PagesSpacing.Relaxed))
      }
    }
}

@Composable
private fun EmailAuthScreen(busy: Boolean, onBack: () -> Unit, onContinue: (creatingAccount: Boolean, email: String, password: String) -> Unit, onGoogle: () -> Unit, onPhone: () -> Unit, onPasswordReset: () -> Unit) {
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
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { AppText("Email address") }, leadingIcon = { Icon(Icons.Filled.Email, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !validEmail, supportingText = { if (submitted && !validEmail) AppText("Enter a valid email address.") })
        Spacer(Modifier.height(PagesSpacing.Compact))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { AppText("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, localizedAppCopy(if (passwordVisible) "Hide password" else "Show password")) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !validPassword, supportingText = { if (submitted && !validPassword) AppText("Use at least 8 characters.") })
        if (creatingAccount) {
            Spacer(Modifier.height(PagesSpacing.Compact))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { AppText("Confirm password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !passwordsMatch, supportingText = { if (submitted && !passwordsMatch) AppText("Passwords do not match.") })
        } else {
            TextButton(onClick = onPasswordReset, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("Forgot password?", color = PagesColors.Forest) }
        }
        Spacer(Modifier.height(PagesSpacing.Relaxed))
        PrimaryButton(if (busy) "Please wait…" else if (creatingAccount) "Create account" else "Sign in", null, enabled = !busy) { submitted = true; if (validEmail && validPassword && (!creatingAccount || passwordsMatch)) onContinue(creatingAccount, email.trim(), password) }
        TextButton(onClick = { creatingAccount = !creatingAccount; submitted = false; confirmPassword = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText(if (creatingAccount) "Already have an account? Sign in" else "New to LittlePages? Create an account", color = PagesColors.Forest) }
        OrDivider()
        OutlineButton("Continue with Google", Icons.Filled.AccountCircle, onGoogle)
        Spacer(Modifier.height(PagesSpacing.ControlGap))
        OutlineButton("Use phone instead", Icons.Filled.Phone, onPhone)
    }
}

@Composable
private fun EmailVerificationScreen(email: String, busy: Boolean, onCheck: () -> Unit, onResend: () -> Unit, onSignOut: () -> Unit) {
    FormPage(title = "Verify your email", subtitle = "We sent a verification link to $email. Verify this address before continuing.", onBack = onSignOut) {
        AppText("After opening the link, return to LittlePages and check your verification status. You can request another email if needed.", color = PagesColors.Muted)
        PrimaryButton(if (busy) "Checking…" else "I’ve verified", Icons.Filled.VerifiedUser, enabled = !busy, onClick = onCheck)
        OutlineButton(if (busy) "Please wait…" else "Resend verification email", Icons.AutoMirrored.Filled.Send) { if (!busy) onResend() }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText("Sign out", color = PagesColors.Forest) }
    }
}

@Composable
private fun PasswordResetScreen(busy: Boolean, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    FormPage(title = "Reset your password", subtitle = "Enter the parent email address associated with your account.", onBack = onBack) {
        OutlinedTextField(email, { email = it }, label = { AppText("Email address") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !validEmail, supportingText = { if (submitted && !validEmail) AppText("Enter a valid email address.") })
        PrimaryButton(if (busy) "Sending…" else "Send reset link", Icons.AutoMirrored.Filled.Send, enabled = !busy) { submitted = true; if (validEmail) onSubmit(email.trim()) }
    }
}

@Composable
private fun PhoneAuthScreen(busy: Boolean, onBack: () -> Unit, onContinue: (String) -> Unit, onEmail: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val phoneDigits = phone.filter(Char::isDigit)
    val localNumber = phoneDigits.removePrefix("0")
    val validPhone = localNumber.length == 10 && localNumber.startsWith("1")
    FormPage(title = "Continue with phone", subtitle = "Enter your Bangladesh mobile number. We’ll text you a one-time code; standard SMS rates may apply.", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = "+880", onValueChange = {}, readOnly = true, label = { AppText("Code") }, modifier = Modifier.width(PagesSizes.PhoneCountryCodeWidth), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(11) }, label = { AppText("Phone number") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !validPhone, supportingText = { if (submitted && !validPhone) AppText("Enter a valid Bangladesh mobile number.") })
        }
        Spacer(Modifier.height(PagesSpacing.SectionGap))
        PrimaryButton(if (busy) "Sending code…" else "Text me a code", Icons.Filled.Sms, enabled = !busy) { submitted = true; if (validPhone) onContinue("+880$localNumber") }
        AppText("Firebase uses your phone number to send the sign-in code and for abuse prevention.", color = PagesColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(PagesSpacing.CardContent))
        OrDivider()
        OutlineButton("Continue with email", Icons.Filled.Email, onEmail)
    }
}

@Composable
private fun PhoneVerificationScreen(phoneNumber: String, busy: Boolean, onBack: () -> Unit, onVerify: (String) -> Unit, onResend: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    FormPage(title = "Verify your number", subtitle = "Enter the one-time code sent to $phoneNumber.", onBack = onBack) {
        OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, label = { AppText("Verification code") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && code.length != 6, supportingText = { if (submitted && code.length != 6) AppText("Enter all 6 digits.") })
        PrimaryButton(if (busy) "Verifying…" else "Verify and continue", Icons.Filled.VerifiedUser, enabled = !busy) { submitted = true; if (code.length == 6) onVerify(code) }
        TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText("Resend code", color = PagesColors.Forest) }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText("Change phone number", color = PagesColors.Forest) }
        AppText("Firebase may verify this device automatically. Otherwise, enter the code from your SMS.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ParentOnboardingScreen(email: String, onBack: () -> Unit, onComplete: (String, String, ChildProfile) -> Unit) {
    var parentName by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("5 years old") }
    var submitted by remember { mutableStateOf(false) }
    PageScaffold(onBack, "Set up your family") {
        AppText("Create a parent profile and add a child reader.", color = PagesColors.Muted)
        OutlinedTextField(value = parentName, onValueChange = { parentName = it.take(50) }, label = { AppText("Parent name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && parentName.isBlank())
        OutlinedTextField(value = email, onValueChange = {}, readOnly = true, label = { AppText("Parent email or phone") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true)
        SectionTitle("First child profile")
        OutlinedTextField(value = childName, onValueChange = { childName = it.take(30) }, label = { AppText("Child nickname") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && childName.isBlank())
        AppText("Age range", color = PagesColors.Muted, modifier = Modifier.padding(top = PagesSpacing.Compact, bottom = PagesSpacing.Small))
        Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Small)) { listOf("3 years old", "5 years old", "7 years old").forEach { option -> FilterChip(selected = ageBand == option, onClick = { ageBand = option }, label = { AppText(option.substringBefore(' ')) }) } }
        AppText("Your parent email account is active. The parent name and child nickname stay in this device preview and are not synced to the database.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        PrimaryButton("Continue to family preview", Icons.Filled.FamilyRestroom) { submitted = true; if (parentName.isNotBlank() && childName.isNotBlank()) onComplete(parentName.trim(), email, ChildProfile(childName.trim(), ageBand, "child-${System.currentTimeMillis()}")) }
    }
}

@Composable
private fun HomeScreen(continuePage: Int, onBook: (Book) -> Unit, onContinue: () -> Unit, onLibrary: () -> Unit, onSearch: () -> Unit, onOffline: () -> Unit, onPremium: () -> Unit) {
    MainPage(title = "Good morning, Emma 👋", subtitle = "Ready for a new adventure?") {
        SearchBarCard(onSearch)
        Spacer(Modifier.height(PagesSpacing.SectionGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("Continue reading"); AppText("The Brave Little Fox · $continuePage of 24 pages", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onContinue) { AppText("Open", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(PagesSpacing.Small))
        CardSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onContinue)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(books[0], Modifier.size(width = PagesSizes.HomeBookCoverWidth, height = PagesSizes.HomeBookCoverHeight))
                Column(Modifier.weight(1f).padding(start = PagesSpacing.CardContent)) {
                    AppText(books[0].title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AppText("You’re doing great!", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { continuePage / 24f }, modifier = Modifier.fillMaxWidth().padding(top = PagesSpacing.CardContent), color = PagesColors.Forest, trackColor = PagesColors.Sage)
                    AppText("${(continuePage * 100 / 24)}% complete", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = PagesSpacing.Dense))
                }
            }
        }
        Spacer(Modifier.height(PagesSpacing.Large))
        SectionTitle("Popular this week", "See all", onClick = onLibrary)
        BookRow(books, onBook)
        Spacer(Modifier.height(PagesSpacing.Medium))
        CardSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPremium), color = PagesColors.Sage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("🎧", fontSize = 28.sp); Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.Compact)) { AppText("Listen and read along", fontWeight = FontWeight.Bold); AppText("Words light up as stories are read", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Icon(Icons.Filled.ChevronRight, null, tint = PagesColors.Forest)
            }
        }
        Spacer(Modifier.height(PagesSpacing.Compact))
        SectionTitle("Your offline books", "See all", onClick = onOffline)
        AppText("Offline book storage isn't set up yet", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted)
    }
}

@Composable
private fun LibraryScreen(categories: List<ManagedCategory>, onBook: (Book) -> Unit, onSearch: () -> Unit, onOffline: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val activeCategories = categories.filter { it.enabled }.sortedBy { it.sortOrder }
    val categoryOptions = listOf("all" to if (language == "bn") "সব" else "All") + activeCategories.map { it.id to it.label(language) } + listOf("bangla-language" to "বাংলা")
    var selectedCategory by remember { mutableStateOf("all") }
    val effectiveCategory = selectedCategory.takeIf { id -> categoryOptions.any { it.first == id } } ?: "all"
    var selectedAge by remember { mutableStateOf("All ages") }
    val ages = listOf("All ages", "Ages 0–3", "Ages 4–6", "Ages 7–9")
    val filteredBooks = books.filter { book ->
        val categoryMatches = when (effectiveCategory) {
            "all" -> true
            "bangla-language" -> book.hasBangla
            else -> effectiveCategory in book.categories
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
        Spacer(Modifier.height(PagesSpacing.CardContent))
        ChipRow(categoryOptions.map { it.second }, selectedIndex = categoryOptions.indexOfFirst { it.first == effectiveCategory }, onSelect = { selectedCategory = categoryOptions[it].first })
        Spacer(Modifier.height(PagesSpacing.Relaxed))
        SectionTitle("Browse by age")
        ChipRow(ages, selectedIndex = ages.indexOf(selectedAge), onSelect = { selectedAge = ages[it] })
        Spacer(Modifier.height(PagesSpacing.SectionGap))
        SectionTitle("All stories", "Offline", onClick = onOffline)
        if (filteredBooks.isEmpty()) EmptyState(Icons.Filled.SearchOff, "No books match these filters", "Try another category or age range.")
        filteredBooks.forEach { book -> BookListRow(book, onClick = { onBook(book) }) }
    }
}

@Composable
private fun SearchScreen(query: String, categories: List<ManagedCategory>, onQuery: (String) -> Unit, onBook: (Book) -> Unit, onBack: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val activeCategories = categories.filter { it.enabled }.sortedBy { it.sortOrder }
    val suggestions = activeCategories.map { it.label(language) }.take(5) + if (language == "bn") listOf("বাংলা গল্প") else listOf("Bangla stories")
    val results = books.filter { book ->
        val labels = book.categories.mapNotNull { id -> activeCategories.firstOrNull { it.id == id }?.label(language) }
        query.isBlank() || book.title.contains(query, true) || book.author.contains(query, true) || labels.any { it.contains(query, true) } || (query.contains("বাংলা", true) && book.hasBangla)
    }
    PageScaffold(onBack, "Search books") {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { AppText("Title, character, or topic") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = localizedAppCopy("Clear search")) } }, shape = RoundedCornerShape(PagesShapes.Tag), singleLine = true)
        Spacer(Modifier.height(PagesSpacing.Medium)); SectionTitle(if (query.isBlank()) "Suggested for you" else "Search results (${results.size})")
        if (results.isEmpty()) EmptyState(Icons.Filled.SearchOff, "No books found", "Try a title, author, category, or another search.")
        results.forEach { BookListRow(it) { onBook(it) } }
        SectionTitle("Popular searches")
        ChipRow(suggestions, selectedIndex = -1, onSelect = { onQuery(suggestions[it]) })
    }
}

@Composable
private fun BookDetailsScreen(book: Book, categories: List<ManagedCategory>, saved: Boolean, premiumUnlocked: Boolean, onPremiumRequired: () -> Unit, onSave: () -> Unit, onBack: () -> Unit, onRead: () -> Unit, onListen: () -> Unit, onReviews: () -> Unit, onOrder: () -> Unit, onRecovery: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    PageScaffold(onBack, "Book details", action = { IconButton(onClick = onSave) { Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, localizedAppCopy(if (saved) "Remove saved book" else "Save book"), tint = PagesColors.Forest) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(width = PagesSizes.BookDetailCoverWidth, height = PagesSizes.BookDetailCoverHeight))
            Column(Modifier.weight(1f).padding(start = PagesSpacing.Medium)) {
                AppText(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                AppText("by ${book.author}", color = PagesColors.Muted)
                Spacer(Modifier.height(PagesSpacing.ControlGap)); AppText("${book.age}   ·   24 pages", style = MaterialTheme.typography.labelMedium)
                val accessLabel = when { !book.isAvailable -> "Unavailable"; book.premiumOnly -> "Premium story"; else -> "Standard story" }
                Surface(shape = CircleShape, color = if (book.premiumOnly && book.isAvailable) PagesColors.Peach else PagesColors.Sage, modifier = Modifier.padding(top = PagesSpacing.Small)) { AppText(accessLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = PagesSpacing.Compact, vertical = PagesSpacing.ExtraSmall)) }
                TextButton(onClick = onReviews, contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("⭐ ${book.rating}  ·  1.2K reviews", color = PagesColors.Gold) }
            }
        }
        Spacer(Modifier.height(PagesSpacing.SectionGap))
        AppText("A story about kindness, courage, and finding your voice. Follow a curious little fox as a new adventure leads to an unexpected friendship.", color = PagesColors.Muted, style = MaterialTheme.typography.bodyMedium)
        val bookCategoryLabels = book.categories.mapNotNull { id -> categories.firstOrNull { it.id == id && it.enabled }?.label(language) }
        if (bookCategoryLabels.isNotEmpty()) { Spacer(Modifier.height(PagesSpacing.Medium)); ChipRow(bookCategoryLabels) }
        Spacer(Modifier.height(PagesSpacing.Large))
        if (!book.isAvailable) {
            EmptyState(Icons.Filled.AutoStories, "This book is unavailable", "Check back later to see if this story returns to the catalogue.")
        } else {
            if (book.pdfAvailable) {
                if (book.premiumOnly && !premiumUnlocked) PrimaryButton("Unlock with Premium", Icons.Filled.Star, onPremiumRequired)
                else PrimaryButton("Read in app", Icons.AutoMirrored.Filled.MenuBook, onRead)
            } else if (book.premiumOnly && !premiumUnlocked) PrimaryButton("Unlock with Premium", Icons.Filled.Star, onPremiumRequired)
            Spacer(Modifier.height(PagesSpacing.ControlGap))
            OutlineButton("Listen and read along · Premium", Icons.Filled.Headphones, onListen)
            if (book.pdfAvailable || book.physicalAvailable) {
                Spacer(Modifier.height(PagesSpacing.SectionGap))
                SectionTitle("Choose a format")
                CardSurface(Modifier.fillMaxWidth()) {
                    Column {
                        if (book.pdfAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.TabletAndroid, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = PagesSpacing.Compact)) { AppText("PDF · Read in app", fontWeight = FontWeight.SemiBold); AppText("Instant access · no PDF download", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳120", fontWeight = FontWeight.Bold) }
                        }
                        if (book.pdfAvailable && book.physicalAvailable) HorizontalDivider(Modifier.padding(vertical = PagesSpacing.Compact), color = PagesColors.Line)
                        if (book.physicalAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(start = PagesSpacing.Compact)) { AppText("Printed book", fontWeight = FontWeight.SemiBold); AppText("Available · delivery in Bangladesh", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳450", fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(PagesSpacing.CardContent)); PrimaryButton("Order a book", Icons.Filled.ShoppingCart, onOrder)
                    }
                }
            } else {
                EmptyState(Icons.Filled.Inventory2, "No editions available", "This title is listed, but no readable or physical edition is available right now.")
            }
        }
        TextButton(onClick = onRecovery, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText("Trouble opening this book?", color = PagesColors.Forest) }
        Spacer(Modifier.height(PagesSpacing.SectionGap)); SectionTitle("Reader reviews", "Read all", onClick = onReviews)
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
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < PagesBreakpoints.ReaderCompactHeight.value.toInt()
    val compactWidth = configuration.screenWidthDp < 520
    val context = LocalContext.current
    DisposableEffect(context) {
        var activityContext: Context? = context
        while (activityContext != null && activityContext !is Activity && activityContext is ContextWrapper) activityContext = activityContext.baseContext
        val window = (activityContext as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var pdfFailed by remember(page, mode) { mutableStateOf(false) }
    val pdfBitmap by produceState<android.graphics.Bitmap?>(null, page, mode) {
        pdfFailed = false
        value = if (mode == "PDF") withContext(Dispatchers.IO) { runCatching { SamplePdfRenderer.render(context, page) }.onFailure { pdfFailed = true }.getOrNull() } else null
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF1EBDD))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = PagesSpacing.Compact, vertical = if (compactHeight) PagesSpacing.Micro else PagesSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, localizedAppCopy("Close reader")) }
            AppText(book.title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onBookmark) { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark", tint = PagesColors.Forest) }
            IconButton(onClick = onPremium) { Icon(Icons.Filled.Headphones, localizedAppCopy("Listen with Premium"), tint = PagesColors.Forest) }
        }
        if (mode == "Page turn") {
            Box(Modifier.weight(1f).fillMaxWidth().padding(if (compactHeight) PagesSpacing.Small else PagesSpacing.CardContent).clip(RoundedCornerShape(PagesShapes.ReaderPage)).background(Brush.verticalGradient(listOf(Color(0xFFDDEBF1), Color(0xFFF5E9D5)))), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxSize().padding(if (compactHeight) PagesSpacing.Compact else PagesSizes.ReaderContentPadding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    AppText(sampleStoryPages[(page - 1) % sampleStoryPages.size], style = if (compactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, lineHeight = if (compactHeight) 26.sp else 34.sp, color = PagesColors.Ink)
                    AppText("🦊", fontSize = if (compactHeight) 56.sp else 116.sp)
                    AppText("The Brave Little Fox", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = PagesColors.Ink)
                }
            }
        } else if (mode == "Scroll") {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagesSpacing.CardContent).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PagesSpacing.Compact)) {
                (page..(page + 2).coerceAtMost(24)).forEach { scrollPage ->
                    CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Paper) {
                        AppText("Page $scrollPage", style = MaterialTheme.typography.labelMedium, color = PagesColors.Forest, fontWeight = FontWeight.Bold)
                        AppText(sampleStoryPages[(scrollPage - 1) % sampleStoryPages.size], style = MaterialTheme.typography.titleLarge, lineHeight = 36.sp, color = PagesColors.Ink, modifier = Modifier.padding(top = PagesSpacing.ControlGap))
                        AppText("🦊", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = PagesSpacing.Compact))
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagesSpacing.CardContent), contentAlignment = Alignment.Center) {
                if (pdfBitmap != null) Image(bitmap = pdfBitmap!!.asImageBitmap(), contentDescription = localizedAppCopy("Sample PDF page $page"), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(PagesShapes.ReaderPage)))
                else AppText(if (pdfFailed) "The sample PDF could not be opened. Switch modes and try again." else "Preparing the sample PDF…", color = PagesColors.Muted, textAlign = TextAlign.Center)
            }
        }
        if (compactWidth) {
            Row(Modifier.fillMaxWidth().padding(horizontal = PagesSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPage((page - 1).coerceAtLeast(1)) }, enabled = page > 1) { Icon(Icons.Filled.ChevronLeft, localizedAppCopy("Previous page"), tint = PagesColors.Forest) }
                Spacer(Modifier.weight(1f)); AppText("$page / 24", fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f))
                IconButton(onClick = { onPage((page + 1).coerceAtMost(24)) }, enabled = page < 24) { Icon(Icons.Filled.ChevronRight, localizedAppCopy("Next page"), tint = PagesColors.Forest) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = PagesSpacing.Medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onMode(when (mode) { "Page turn" -> "Scroll"; "Scroll" -> "PDF"; else -> "Page turn" }) }) { AppText("Mode: $mode", color = PagesColors.Forest) }
                Button(onClick = onListen, modifier = Modifier.heightIn(min = PagesSizes.MinimumTouchTarget), shape = RoundedCornerShape(PagesShapes.LargeCard), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(PagesSizes.IconSmall)); AppText(" Listen", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = PagesSpacing.Medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onPage((page - 1).coerceAtLeast(1)) }, enabled = page > 1) { Icon(Icons.Filled.ChevronLeft, localizedAppCopy("Previous page"), tint = PagesColors.Forest) }
                AppText("$page / 24", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onMode(when (mode) { "Page turn" -> "Scroll"; "Scroll" -> "PDF"; else -> "Page turn" }) }) { AppText(mode, color = PagesColors.Forest) }
                Button(onClick = onListen, modifier = Modifier.heightIn(min = PagesSizes.MinimumTouchTarget), shape = RoundedCornerShape(PagesShapes.LargeCard), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest), contentPadding = PaddingValues(horizontal = PagesSpacing.Compact, vertical = PagesSpacing.Small)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(PagesSizes.IconSmall)); AppText(" Listen", color = Color.White, fontWeight = FontWeight.SemiBold) }
                IconButton(onClick = { onPage((page + 1).coerceAtMost(24)) }, enabled = page < 24) { Icon(Icons.Filled.ChevronRight, localizedAppCopy("Next page"), tint = PagesColors.Forest) }
            }
        }
        Spacer(Modifier.height(PagesSpacing.ControlGap))
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

    PageScaffold(onBack, "Read along", action = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, localizedAppCopy("Narration settings")) } }) {
        Box(Modifier.fillMaxWidth().height(PagesSizes.ReadAlongIllustrationHeight).clip(RoundedCornerShape(PagesShapes.LargeCard)).background(Brush.verticalGradient(listOf(PagesColors.Sky, PagesColors.Sage))), contentAlignment = Alignment.Center) { AppText("🦊   🐦", fontSize = 70.sp) }
        Spacer(Modifier.height(PagesSpacing.Medium))
        CardSurface(Modifier.fillMaxWidth()) { MaterialText(text = highlightedText, style = MaterialTheme.typography.headlineSmall, lineHeight = 36.sp) }
        if (!narrationError.isNullOrBlank()) AppText(narrationError, color = PagesColors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = PagesSpacing.Small))
        else if (!narrationState.ready) AppText("Preparing your device’s voice…", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = PagesSpacing.Small))
        Spacer(Modifier.height(PagesSpacing.CardContent))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = narrationState.ready, onClick = { seekTo((position - 10f / estimatedSeconds).coerceAtLeast(0f)) }) { Icon(Icons.Filled.Replay10, localizedAppCopy("Back 10 seconds"), tint = PagesColors.Forest) }
            FilledIconButton(enabled = narrationState.ready, onClick = {
                if (isPlaying) {
                    resumeOffset = narrationState.activeRange?.first ?: resumeOffset
                    narrationEngine.stop()
                    isPlaying = false
                    audioFocus.abandon()
                } else isPlaying = speakFrom(resumeOffset)
            }, modifier = Modifier.size(PagesSizes.NarrationButton), colors = IconButtonDefaults.filledIconButtonColors(containerColor = PagesColors.Forest)) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, localizedAppCopy(if (isPlaying) "Pause narration" else "Play narration"), modifier = Modifier.size(PagesSizes.NarrationPlayIcon)) }
            IconButton(enabled = narrationState.ready, onClick = { seekTo((position + 10f / estimatedSeconds).coerceAtMost(1f)) }) { Icon(Icons.Filled.Forward10, localizedAppCopy("Forward 10 seconds"), tint = PagesColors.Forest) }
        }
        Slider(value = position, onValueChange = { position = it }, onValueChangeFinished = { seekTo(position) }, enabled = narrationState.ready, colors = SliderDefaults.colors(thumbColor = PagesColors.Forest, activeTrackColor = PagesColors.Forest))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AppText(durationLabel((position * estimatedSeconds).toInt()), color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall); AppText(durationLabel(estimatedSeconds), color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(PagesSpacing.Small))
        if (previewOnly) AppText("One-page read-along preview", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium)
        CardSurface(Modifier.fillMaxWidth()) {
            val autoplayLabel = localizedAppCopy("Autoplay pages")
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoStories, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.Compact)) { AppText("Autoplay pages", fontWeight = FontWeight.SemiBold); AppText("Continue to the next page automatically", style = MaterialTheme.typography.bodySmall, color = PagesColors.Muted) }; Switch(autoplay, onAutoplay, enabled = !previewOnly, modifier = Modifier.semantics { contentDescription = autoplayLabel }, colors = SwitchDefaults.colors(checkedTrackColor = PagesColors.Forest)) }
        }
        Spacer(Modifier.height(PagesSpacing.ControlGap))
        AppText("Voice: ${narrationState.voices.firstOrNull { it.id == selectedVoiceId }?.label ?: "System default"} · Speed: $speed", color = PagesColors.Forest, style = MaterialTheme.typography.labelMedium)
        AppText(book.title, color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = PagesSpacing.Small))
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { AppText("Narration settings") },
        text = {
            Column {
                AppText("Voice")
                if (narrationState.voices.isEmpty()) AppText("System default", color = PagesColors.Muted)
                else narrationState.voices.forEach { voice -> FilterChip(selected = selectedVoiceId == voice.id, onClick = { val resume = isPlaying; if (resume) resumeOffset = narrationState.activeRange?.first ?: resumeOffset; selectedVoiceId = voice.id; if (resume) isPlaying = speakFrom(resumeOffset) }, label = { AppText(voice.label) }) }
                Spacer(Modifier.height(PagesSpacing.CardContent)); AppText("Reading speed")
                Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Small)) { speedOptions.forEach { (choice, _) -> FilterChip(selected = speed == choice, onClick = { val resume = isPlaying; if (resume) resumeOffset = narrationState.activeRange?.first ?: resumeOffset; speed = choice; if (resume) isPlaying = speakFrom(resumeOffset) }, label = { AppText(choice) }) } }
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { AppText("Done") } }
    )
}

@Composable
private fun CommunityScreen(posts: List<CommunityEntry>, categories: List<ManagedCategory>, blockedAuthors: List<String>, onBlockAuthor: (String) -> Unit, onGroup: () -> Unit, onCreate: () -> Unit) {
    var showGuidelines by remember { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val activeCategories = categories.filter { it.enabled }.sortedBy { it.sortOrder }
    val feedCategories = listOf("all" to if (language == "bn") "আপনার জন্য" else "For you") + activeCategories.map { it.id to it.label(language) }
    var selectedFeed by remember { mutableStateOf("all") }
    val effectiveFeed = selectedFeed.takeIf { id -> feedCategories.any { it.first == id } } ?: "all"
    val examplePosts = listOf(
        CommunityEntry("We finished The Brave Little Fox last night. My little one wanted to talk about courage at breakfast! 🦊", category = "tips", author = "Sarah Mitchell", time = "2 days ago"),
        CommunityEntry("Looking for gentle Bangla stories for a 5-year-old. What are your family favourites?", category = "discussions", author = "Amina Rahman", time = "Yesterday"),
        CommunityEntry("Our library hosted a lovely family story hour this weekend.", category = "activities", author = "James Carter", time = "3 days ago")
    )
    val feedPosts = (posts + examplePosts).filter { it.author !in blockedAuthors && (effectiveFeed == "all" || it.category == effectiveFeed) }
    MainPage(title = "Parent community", subtitle = "A kind space to share the reading journey.", action = { Row { IconButton(onClick = { showGuidelines = true }) { Icon(Icons.Filled.Info, localizedAppCopy("Community guidelines"), tint = PagesColors.Forest) }; IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, localizedAppCopy("Create post"), tint = PagesColors.Forest) } } }) {
        AppText("Community guidelines: protect children’s privacy, be kind, and share only images you have permission to post. Report content that feels unsafe. Moderation is not connected in this preview.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        CardSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onCreate), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); AppText(" Share a thought with parents…", color = PagesColors.Muted, modifier = Modifier.weight(1f).padding(horizontal = PagesSpacing.ControlGap)); Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest) } }
        Spacer(Modifier.height(PagesSpacing.Medium)); ChipRow(feedCategories.map { it.second }, selectedIndex = feedCategories.indexOfFirst { it.first == effectiveFeed }, onSelect = { selectedFeed = feedCategories[it].first })
        Spacer(Modifier.height(PagesSpacing.CardContent)); SectionTitle("Groups to explore", "See all", onClick = onGroup)
        Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.ControlGap)) { GroupTile("Raising Kind Kids", "312 parents", "🌱", onGroup); GroupTile("Bedtime Stories", "186 parents", "🌙", onGroup) }
        Spacer(Modifier.height(PagesSpacing.SectionGap)); SectionTitle("Recent posts")
        if (feedPosts.isEmpty()) EmptyState(Icons.Filled.Forum, "No posts in this feed yet", "Choose another topic or start a conversation.", "Create a post", onCreate)
        feedPosts.forEach { post -> CommunityPost(post.author, post.time + if (post.author == "You") " · preview only" else "", post.body, if (post.hasPhoto) "🖼️" else "", onGroup, imageUri = post.imageUri, onBlock = { onBlockAuthor(post.author) }) }
    }
    if (showGuidelines) AlertDialog(onDismissRequest = { showGuidelines = false }, title = { AppText("Community guidelines") }, text = { AppText("Keep children’s names, schools, locations, and contact details private. Treat other families with kindness. Share only images you have permission to post, and report unsafe or inappropriate content. Reports and moderation are preview-only until the community service is connected.") }, confirmButton = { TextButton(onClick = { showGuidelines = false }) { AppText("Got it") } })
}

@Composable
private fun GroupScreen(posts: List<CommunityEntry>, blockedAuthors: List<String>, onBlockAuthor: (String) -> Unit, canParticipate: Boolean, onBack: () -> Unit, onCreatePost: () -> Unit) {
    var reply by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Discussion") }
    val replies = remember { mutableStateListOf<String>() }
    PageScaffold(onBack, "Raising Kind Kids", action = { IconButton(onClick = onCreatePost) { Icon(Icons.Filled.Add, localizedAppCopy("Create discussion")) } }) {
        var joined by remember { mutableStateOf(false) }
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Column { AppText("🌱  Raising Kind Kids", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); AppText("312 parents · Stories and ideas for caring families", color = PagesColors.Muted, modifier = Modifier.padding(top = PagesSpacing.ExtraSmall)); TextButton(onClick = { if (canParticipate) joined = !joined }, modifier = Modifier.align(Alignment.End), enabled = canParticipate) { AppText(if (joined) "Joined · Leave group" else "Join group", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold) } } }
        val tabs = listOf("Discussion", "Resources", "Members")
        ChipRow(tabs, selectedIndex = tabs.indexOf(selectedTab), onSelect = { selectedTab = tabs[it] })
        when (selectedTab) {
            "Discussion" -> {
                if (!joined) {
                    if (canParticipate) EmptyState(Icons.Filled.Groups, "Join the conversation", "Join this group to read discussions and share replies.", "Join group", { joined = true })
                    else EmptyState(Icons.Filled.Groups, "Parent account required", "Sign in to a parent account to participate in this group.")
                } else {
                    SectionTitle("Latest discussion")
                    if ("Priya Sharma" !in blockedAuthors) CommunityPost("Priya Sharma", "3 days ago", "How do you talk to your kids about kindness when it’s hard? We read books that show different perspectives and then chat at bedtime.", "", null, onBlock = { onBlockAuthor("Priya Sharma") })
                    if ("James Carter" !in blockedAuthors) CommunityPost("James Carter", "2 days ago", "We use the ‘kind choices’ language after reading together. It gives our son a simple way to reflect.", "", null, onBlock = { onBlockAuthor("James Carter") })
                    posts.filter { it.author !in blockedAuthors }.forEach { post -> CommunityPost(post.author, post.time + " · preview only", post.body, if (post.hasPhoto) "🖼️" else "", null, imageUri = post.imageUri, onBlock = { onBlockAuthor(post.author) }) }
                    replies.forEach { message -> CommunityPost("You", "Just now", message, "", null) }
                    OutlineButton("Start a discussion", Icons.Filled.Edit, onCreatePost)
                    OutlinedTextField(value = reply, onValueChange = { reply = it }, placeholder = { AppText("Write a reply…") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Tag), trailingIcon = { IconButton(onClick = { if (reply.isNotBlank()) { replies.add(reply.trim()); reply = "" } }, enabled = reply.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, localizedAppCopy("Send reply"), tint = PagesColors.Forest) } })
                }
            }
            "Resources" -> {
                SectionTitle("Family resources")
                CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoStories, null, tint = PagesColors.Forest); Column(Modifier.padding(start = PagesSpacing.Compact)) { AppText("Talking about kindness", fontWeight = FontWeight.SemiBold); AppText("A short family conversation guide", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
                CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Bookmark, null, tint = PagesColors.Forest); Column(Modifier.padding(start = PagesSpacing.Compact)) { AppText("Bedtime reading routine", fontWeight = FontWeight.SemiBold); AppText("Ideas shared by group parents", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
            }
            else -> {
                SectionTitle("Group members")
                listOf("Priya Sharma", "James Carter", "Elena Park", "Amina Rahman").forEach { member -> CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(member.first().toString()); AppText(member, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = PagesSpacing.Compact)) } } }
            }
        }
    }
}

@Composable
private fun CreatePostScreen(categories: List<ManagedCategory>, maximumImageBytes: Long, allowedImageMimeTypes: Set<String>, onBack: () -> Unit, onPost: (CommunityEntry) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val language = LocalConfiguration.current.locales[0].language
    val activeCategories = categories.filter { it.enabled }.sortedBy { it.sortOrder }
    var selectedCategory by remember(activeCategories) { mutableStateOf(activeCategories.firstOrNull()?.id ?: "stories") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (mimeType, withinLimit) = withContext(Dispatchers.IO) {
                val detectedType = context.contentResolver.getType(uri)
                val size = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
                val acceptableSize = if (size >= 0L) size <= maximumImageBytes else runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = stream.read(buffer)
                            if (count == -1) break
                            total += count
                            if (total > maximumImageBytes) break
                        }
                        total <= maximumImageBytes
                    } ?: false
                }.getOrDefault(false)
                detectedType to acceptableSize
            }
            when {
                mimeType !in allowedImageMimeTypes -> photoError = "This image format is not enabled for community posts."
                !withinLimit -> photoError = "Choose an image smaller than ${maximumImageBytes / (1024L * 1024L)} MB. Its size must be readable to check this limit."
                else -> { selectedPhoto = uri; photoError = null }
            }
        }
    }
    PageScaffold(onBack, "Create a post", action = { TextButton(onClick = { if (post.isNotBlank()) onPost(CommunityEntry(post.trim(), selectedPhoto, selectedCategory)) }, enabled = post.isNotBlank()) { AppText("Post", color = PagesColors.Forest, fontWeight = FontWeight.Bold) } }) {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar("E"); Column(Modifier.padding(start = PagesSpacing.Compact)) { AppText("Emma’s parent", fontWeight = FontWeight.SemiBold); AppText("Posting as a parent", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        OutlinedTextField(value = post, onValueChange = { post = it }, placeholder = { AppText("What would you like to share?") }, modifier = Modifier.fillMaxWidth().height(PagesSizes.PostInputHeight), shape = RoundedCornerShape(PagesShapes.Card))
        SectionTitle("Choose a topic")
        ChipRow(activeCategories.map { it.label(language) }, selectedIndex = activeCategories.indexOfFirst { it.id == selectedCategory }, onSelect = { selectedCategory = activeCategories[it].id })
        CardSurface(Modifier.fillMaxWidth().clickable(role = Role.Button) { photoPicker.launch("image/*") }, color = PagesColors.Sky) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = PagesColors.Forest); Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.ControlGap)) { AppText(if (selectedPhoto == null) "Add a family-friendly photo" else "Photo selected", fontWeight = FontWeight.SemiBold); if (selectedPhoto != null) AppText("Attached to this local preview", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.ChevronRight, null) } }
        AppText("Please keep children’s personal details private. Posts are visible to community members.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        PrimaryButton("Preview post", Icons.AutoMirrored.Filled.Send) { if (post.isNotBlank()) onPost(CommunityEntry(post.trim(), selectedPhoto, selectedCategory)) }
        AppText("Posts are only kept for this app session until the community service is configured.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
    photoError?.let { message -> AlertDialog(onDismissRequest = { photoError = null }, title = { AppText("Image not accepted") }, text = { AppText(message) }, confirmButton = { TextButton(onClick = { photoError = null }) { AppText("Choose another") } }) }
}

@Composable
private fun ReviewsScreen(book: Book, onBack: () -> Unit) {
    var showWriteReview by remember { mutableStateOf(false) }
    var showReportNotice by remember { mutableStateOf(false) }
    var authorToBlock by remember { mutableStateOf<String?>(null) }
    var reviewText by remember { mutableStateOf("") }
    var reviewRating by remember { mutableIntStateOf(5) }
    var submitted by remember { mutableStateOf(false) }
    val blockedAuthors = remember { mutableStateListOf<String>() }
    val localReviews = remember { mutableStateListOf<Pair<Int, String>>() }
    PageScaffold(onBack, "Reviews") {
        AppText(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { AppText("4.9", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Column(Modifier.padding(start = PagesSpacing.CardContent)) { AppText("★★★★★", color = PagesColors.Gold, fontSize = 20.sp); AppText("Based on 1,240 parent reviews", color = PagesColors.Muted) } } }
        PrimaryButton("Write a review", Icons.Filled.RateReview) { showWriteReview = true }
        localReviews.forEach { (rating, body) -> CardSurface(Modifier.fillMaxWidth()) { Column { AppText("You · preview only", fontWeight = FontWeight.Bold); AppText("★".repeat(rating), color = PagesColors.Gold); AppText(body, color = PagesColors.Muted) } } }
        listOf("A lovely story my child asks for every night.", "Beautiful illustrations and a gentle message.", "We enjoyed reading this together before bed.").forEachIndexed { index, review ->
            val author = listOf("A. Rahman", "M. Carter", "S. Patel")[index]
            if (author !in blockedAuthors) CardSurface(Modifier.fillMaxWidth()) { Column { AppText(author, fontWeight = FontWeight.Bold); AppText("★★★★★", color = PagesColors.Gold); AppText(review, color = PagesColors.Muted); Row { TextButton(onClick = { showReportNotice = true }, contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("Report", color = PagesColors.Forest, style = MaterialTheme.typography.labelLarge) }; TextButton(onClick = { authorToBlock = author }, contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("Block", color = PagesColors.Forest, style = MaterialTheme.typography.labelLarge) } } } }
        }
    }
    if (showWriteReview) AlertDialog(onDismissRequest = { showWriteReview = false }, title = { AppText("Write a review") }, text = { Column { AppText("Your rating"); Row { (1..5).forEach { value -> IconButton(onClick = { reviewRating = value }, modifier = Modifier.size(PagesSizes.MinimumTouchTarget)) { Icon(if (value <= reviewRating) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = localizedAppCopy("Rate $value out of 5 stars"), tint = PagesColors.Gold) } } }; OutlinedTextField(value = reviewText, onValueChange = { reviewText = it.take(800) }, label = { AppText("Share your thoughts") }, minLines = 3, isError = submitted && reviewText.isBlank()) } }, confirmButton = { TextButton(onClick = { submitted = true; if (reviewText.isNotBlank()) { localReviews.add(0, reviewRating to reviewText.trim()); reviewText = ""; submitted = false; showWriteReview = false } }) { AppText("Save preview") } }, dismissButton = { TextButton(onClick = { showWriteReview = false }) { AppText("Cancel") } })
    if (showReportNotice) AlertDialog(onDismissRequest = { showReportNotice = false }, title = { AppText("Report review") }, text = { AppText("Reports are not sent until the community moderation service is configured.") }, confirmButton = { TextButton(onClick = { showReportNotice = false }) { AppText("Got it") } })
    authorToBlock?.let { author -> AlertDialog(onDismissRequest = { authorToBlock = null }, title = { AppText("Block $author?") }, text = { AppText("This preview will hide this person’s reviews on this screen. Blocking is not saved or sent to a moderation service.") }, confirmButton = { TextButton(onClick = { blockedAuthors.add(author); authorToBlock = null }) { AppText("Block") } }, dismissButton = { TextButton(onClick = { authorToBlock = null }) { AppText("Cancel") } }) }
}

@Composable
private fun OfflineScreen(books: List<Book>, onBack: () -> Unit, onBrowse: () -> Unit, onRemove: (Book) -> Unit, onRetrySync: () -> Unit) {
    var bookToRemove by remember { mutableStateOf<Book?>(null) }
    PageScaffold(onBack, "Available offline") {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) {
            AppText("Storage and sync preview", fontWeight = FontWeight.Bold)
            AppText("These are sample list entries only. No book files are stored on this device, and real storage usage is not available yet.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
            AppText("Sync status · Not connected", color = PagesColors.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = PagesSpacing.Small))
            TextButton(onClick = onRetrySync, contentPadding = PaddingValues(horizontal = PagesSpacing.Small)) { AppText("Retry sync", color = PagesColors.Forest) }
        }
        SectionTitle("Offline book list")
        if (books.isEmpty()) EmptyState(Icons.Filled.CloudOff, stringResource(R.string.no_offline_books), "Books you save for offline reading will appear here after private encrypted storage is connected.", stringResource(R.string.browse_books), onBrowse)
        books.forEach { book ->
            CardSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, Modifier.size(PagesSizes.BookListCover))
                    Column(Modifier.weight(1f).padding(horizontal = PagesSpacing.Compact)) { AppText(book.title, fontWeight = FontWeight.Bold); AppText("Preview entry · file not stored", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { bookToRemove = book }) { Icon(Icons.Filled.DeleteOutline, localizedAppCopy("Remove ${book.title} from preview list"), tint = PagesColors.Forest) }
                }
            }
        }
    }
    bookToRemove?.let { book -> AlertDialog(onDismissRequest = { bookToRemove = null }, title = { AppText("Remove this book?") }, text = { AppText("${book.title} will be removed from the sample list. No stored book file will be deleted.") }, confirmButton = { TextButton(onClick = { onRemove(book); bookToRemove = null }) { AppText("Remove") } }, dismissButton = { TextButton(onClick = { bookToRemove = null }) { AppText("Cancel") } }) }
}

@Composable
private fun ParentScreen(guest: Boolean, previewAccount: Boolean, authenticated: Boolean, parentName: String, parentEmail: String, language: String, child: ChildProfile?, onLanguage: (String) -> Unit, onProfiles: () -> Unit, onSubscription: () -> Unit, onOrders: () -> Unit, onSupport: () -> Unit, onOffline: () -> Unit, onPrivacy: () -> Unit, onUnavailable: (String) -> Unit, onCreateAccount: () -> Unit) {
    MainPage(title = stringResource(R.string.parent_account), subtitle = "Manage your family’s LittlePages.") {
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(if (guest) "G" else (parentName.firstOrNull()?.uppercase() ?: "P")); Column(Modifier.padding(start = PagesSpacing.Compact)) { AppText(if (guest) "Guest access" else if (previewAccount) parentName.ifBlank { "Parent preview" } else "Parent account", fontWeight = FontWeight.Bold); AppText(if (guest) "Browsing without an account" else if (previewAccount) "${parentEmail.ifBlank { "Sample profile" }} · preview only" else if (authenticated) "${parentEmail.ifBlank { "Signed in" }} · family data stays in this preview" else "Account session unavailable in preview", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        if (guest) {
            AppText("Create an account to save your reading progress across devices, order books, and join the parent community.", color = PagesColors.Muted)
            PrimaryButton("Create a parent account", Icons.Filled.AccountCircle, onCreateAccount)
            OutlineButton("Sign in", Icons.AutoMirrored.Filled.Login, onCreateAccount)
        } else {
            SectionTitle(stringResource(R.string.family_section))
            SettingsRow(Icons.Filled.ChildCare, stringResource(R.string.child_profiles), child?.let { "${it.nickname} · ${it.ageBand}" } ?: "Add a child profile", onProfiles)
            SettingsRow(Icons.Filled.Star, stringResource(R.string.premium_title), "Explore listening and more", onSubscription)
        }
        SectionTitle(stringResource(R.string.account_section))
        SettingsRow(Icons.Filled.Language, stringResource(R.string.language_label), if (language == "bn") stringResource(R.string.language_bangla) else stringResource(R.string.language_english), { onLanguage(if (language == "bn") "en" else "bn") })
        if (!guest) {
            SettingsRow(Icons.Filled.CloudDownload, stringResource(R.string.offline_books), "Manage saved stories", onOffline)
            SettingsRow(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.orders_label), "View purchases and delivery", onOrders)
        }
        SettingsRow(Icons.Filled.SwitchAccount, "Switch parent account", "Return to sign-in options", onCreateAccount)
        SettingsRow(Icons.Filled.SupportAgent, stringResource(R.string.support_label), "We’re here to help", onSupport)
        SettingsRow(Icons.Filled.PrivacyTip, "Privacy and safety", "Family controls and data choices", onPrivacy)
        SettingsRow(Icons.AutoMirrored.Filled.Logout, if (guest) "Exit guest mode" else "Sign out", "", { onUnavailable(if (guest) "" else "There is no signed-in account in preview mode.") })
    }
}

@Composable
private fun ProfilesScreen(children: List<ChildProfile>, selectedChildId: String, progressStore: ReaderProgressStore, onSelectChild: (String) -> Unit, onBack: () -> Unit, onSaveChild: (ChildProfile?, ChildProfile) -> Unit) {
    var editing by remember { mutableStateOf<ChildProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("5 years old") }
    var submitted by remember { mutableStateOf(false) }
    PageScaffold(onBack, "Child profiles") {
        AppText("Preview profiles are held in memory only; account sync and consent settings are not configured.", color = PagesColors.Muted)
        children.forEach { child ->
            val startedBooks = books.count { progressStore.page(child.id, it.title) > 1 }
            val lastReadPage = progressStore.page(child.id, books.first().title).coerceIn(1, 24)
            CardSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedChildId == child.id, onClick = { onSelectChild(child.id) })
                    Avatar(child.nickname.take(1).ifBlank { "?" })
                    Column(Modifier.weight(1f).padding(start = PagesSpacing.Compact)) {
                        AppText(child.nickname, fontWeight = FontWeight.Bold)
                        AppText("${child.ageBand} · English and বাংলা", color = PagesColors.Muted)
                        AppText("$startedBooks stories started · Brave Little Fox page $lastReadPage of 24", color = PagesColors.Forest, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = child; nickname = child.nickname; ageBand = child.ageBand; submitted = false }) { Icon(Icons.Filled.Edit, localizedAppCopy("Edit ${child.nickname} profile"), tint = PagesColors.Forest) }
                }
                TextButton(onClick = { onSelectChild(child.id) }, modifier = Modifier.align(Alignment.End)) { AppText(if (selectedChildId == child.id) "Selected for reading" else "Use this profile", color = PagesColors.Forest) }
            }
        }
        OutlineButton("Add a child profile", Icons.Filled.Add) { adding = true; editing = null; nickname = ""; ageBand = "5 years old"; submitted = false }
        AppText("Parent account controls purchases, subscriptions, and community posts.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (adding || editing != null) AlertDialog(onDismissRequest = { adding = false; editing = null }, title = { AppText(if (adding) "Add child profile" else "Edit child profile") }, text = { Column { OutlinedTextField(nickname, { nickname = it.take(30) }, label = { AppText("Nickname") }, singleLine = true, isError = submitted && nickname.isBlank()); AppText("Age range", modifier = Modifier.padding(top = PagesSpacing.Compact, bottom = PagesSpacing.Dense)); Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.ExtraSmall)) { listOf("3 years old", "5 years old", "7 years old").forEach { choice -> FilterChip(selected = ageBand == choice, onClick = { ageBand = choice }, label = { AppText(choice.substringBefore(' ')) }) } }; AppText("Years", color = PagesColors.Muted, style = MaterialTheme.typography.labelSmall) } }, confirmButton = { TextButton(onClick = { submitted = true; if (nickname.isNotBlank()) { val stableId = editing?.id ?: "child-${System.currentTimeMillis()}"; onSaveChild(editing, ChildProfile(nickname.trim(), ageBand, stableId)); adding = false; editing = null } }) { AppText("Save preview") } }, dismissButton = { TextButton(onClick = { adding = false; editing = null }) { AppText("Cancel") } })
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    var selectedAction by remember { mutableStateOf<String?>(null) }
    PageScaffold(onBack, "Privacy and safety") {
        EmptyState(Icons.Filled.FamilyRestroom, "Parents stay in control", "This prototype keeps child profiles and community activity in local preview state. Account sync, consent records, and deletion requests are not connected.")
        SectionTitle("Family data")
        SettingsRow(Icons.Filled.ChildCare, "Child profiles", "Nickname, age band, and reading progress", { selectedAction = "Child profiles" })
        SettingsRow(Icons.Filled.Groups, "Community activity", "Posts and replies are preview-only", { selectedAction = "Community activity" })
        SettingsRow(Icons.Filled.Book, "Reading history", "Book position and bookmarks on this device", { selectedAction = "Reading history" })
        SectionTitle("Parent choices")
        OutlineButton("Review parent consent", Icons.Filled.VerifiedUser) { selectedAction = "Parent consent" }
        OutlineButton("Request a copy of family data", Icons.Filled.FileDownload) { selectedAction = "Data copy" }
        OutlineButton("Request account deletion", Icons.Filled.DeleteForever) { selectedAction = "Account deletion" }
        AppText("Final privacy wording, retention periods, and consent requirements need approval before launch.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
    selectedAction?.let { action -> AlertDialog(onDismissRequest = { selectedAction = null }, title = { AppText(action) }, text = { AppText("This preview does not send or store privacy requests. The account and consent service is not configured.") }, confirmButton = { TextButton(onClick = { selectedAction = null }) { AppText("Got it") } }) }
}

@Composable
private fun SubscriptionScreen(premium: Boolean, onBack: () -> Unit, onSubscribe: () -> Unit, onPreview: () -> Unit, onPaymentRecovery: () -> Unit) {
    val statuses = listOf("Not subscribed", "Active", "Renewing", "Cancelled", "Expired", "Payment issue")
    var previewStatus by remember { mutableStateOf(if (premium) "Active" else "Not subscribed") }
    PageScaffold(onBack, "LittlePages Premium") {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(PagesShapes.Hero)).background(Brush.linearGradient(listOf(PagesColors.Sage, PagesColors.Peach))).padding(PagesSpacing.Large)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { AppText("✨", fontSize = 40.sp); AppText("Stories come alive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); AppText("A little extra magic for family reading time.", textAlign = TextAlign.Center, color = PagesColors.Muted, modifier = Modifier.padding(top = PagesSpacing.Small)) }
        }
        listOf("Word-by-word narrated read-along", "Warm voices and adjustable speed", "More stories for your family", "Offline listening on supported books").forEach { BenefitRow(it) }
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { AppText("Family plan", fontWeight = FontWeight.Bold); AppText("Sample billing terms · not final", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) }; AppText("৳399 / mo · preview", fontWeight = FontWeight.Bold, color = PagesColors.Forest) } }
        SectionTitle("Subscription state preview")
        AppText("Choose a sample state to review the interface. This does not change a subscription or payment.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        ChipRow(statuses, selectedIndex = statuses.indexOf(previewStatus), onSelect = { previewStatus = statuses[it] })
        CardSurface(Modifier.fillMaxWidth(), color = PagesColors.Sage) {
            AppText(previewStatus, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            AppText(when (previewStatus) {
                "Active" -> "Premium is active in this sample view. Renewal date and receipts will come from verified billing."
                "Renewing" -> "A renewal is pending in this sample view. No charge or renewal was attempted."
                "Cancelled" -> "Cancellation is shown as a sample state. Entitlement end date needs verified billing data."
                "Expired" -> "Premium access is expired in this sample view. Real access is controlled by verified entitlements."
                "Payment issue" -> "A sample payment problem needs attention. No payment was attempted."
                else -> "No subscription is active in this sample view."
            }, color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(if (premium || previewStatus == "Active") "Manage Premium · preview" else "Start Premium", Icons.Filled.Star, onSubscribe)
        if (previewStatus == "Payment issue") OutlineButton("Open payment recovery", Icons.Filled.Payment, onPaymentRecovery)
        OutlineButton("Try a one-page read-along sample", Icons.Filled.Headphones, onPreview)
        AppText("Billing, renewal, cancellation, and entitlements are not connected in this prototype.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CheckoutScreen(book: Book, onBack: () -> Unit, onComplete: (format: String, method: String, name: String, address: String, phone: String) -> Unit, onPaymentRecovery: () -> Unit) {
    var format by remember(book.title) { mutableStateOf(if (book.pdfAvailable) "PDF · Read in app" else "Printed book") }
    var paymentMethod by remember { mutableStateOf("bKash") }
    var fullName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val validAddress = format != "Printed book" || (fullName.isNotBlank() && address.isNotBlank() && phone.filter(Char::isDigit).length >= 8)
    PageScaffold(onBack, "Choose a format") {
        CardSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { BookCover(book, Modifier.size(PagesSizes.CheckoutBookCover)); Column(Modifier.padding(start = PagesSpacing.Compact)) { AppText(book.title, fontWeight = FontWeight.Bold); AppText("Choose how your family will read", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall) } } }
        if (book.pdfAvailable) FormatChoice("PDF · Read in app", "৳120", "Instant access. Downloading and exporting are disabled.", format == "PDF · Read in app") { format = "PDF · Read in app" }
        if (book.physicalAvailable) FormatChoice("Printed book", "৳450", "Physical edition available · delivery address required", format == "Printed book") { format = "Printed book" }
        if (format == "Printed book") {
            SectionTitle("Delivery address")
            OutlinedTextField(fullName, { fullName = it }, label = { AppText("Full name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && fullName.isBlank())
            OutlinedTextField(address, { address = it }, label = { AppText("Address and area") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && address.isBlank())
            OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(15) }, label = { AppText("Phone number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && phone.length < 8, supportingText = { if (submitted && phone.length < 8) AppText("Enter a valid phone number.") })
        }
        SectionTitle("Payment")
        Row(horizontalArrangement = Arrangement.spacedBy(PagesSpacing.Small)) { listOf("bKash", "Visa").forEach { method -> FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { AppText(method) }) } }
        PrimaryButton("Place preview order", Icons.Filled.Lock) { submitted = true; if (validAddress) onComplete(format, paymentMethod, fullName.trim(), address.trim(), phone) }
        AppText("Preview only: no payment is taken and no order is sent. PDFs remain in-app only.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        TextButton(onClick = onPaymentRecovery, modifier = Modifier.align(Alignment.CenterHorizontally)) { AppText("Preview payment recovery", color = PagesColors.Forest) }
    }
}

@Composable
private fun OrderConfirmationScreen(order: PurchasePreview?, onViewOrders: () -> Unit, onContinueReading: () -> Unit) {
    PageScaffold(onBack = onContinueReading, title = "Order preview") {
        EmptyState(Icons.Filled.CheckCircle, "Preview order created", "This is a local confirmation only. No payment was taken and no order was sent to a store.")
        order?.let {
            CardSurface(Modifier.fillMaxWidth()) {
                AppText(it.bookTitle, fontWeight = FontWeight.Bold)
                AppText("${it.format} · ${it.paymentMethod}", color = PagesColors.Muted)
                AppText("Reference ${it.id}", color = PagesColors.Forest, style = MaterialTheme.typography.bodySmall)
                if (it.format == "Printed book" && it.address.isNotBlank()) AppText("Delivery to ${it.fullName}: ${it.address} · ${it.phone}", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        PrimaryButton("View order history", Icons.AutoMirrored.Filled.ReceiptLong, onViewOrders)
    }
}

@Composable
private fun OrdersScreen(orders: List<PurchasePreview>, onBack: () -> Unit) {
    PageScaffold(onBack, "Your orders") {
        if (orders.isEmpty()) EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Order history is empty", "Preview orders will appear here after you complete the sample checkout.")
        orders.forEach { order ->
            CardSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = PagesColors.Forest)
                    Column(Modifier.weight(1f).padding(start = PagesSpacing.Compact)) {
                        AppText(order.bookTitle, fontWeight = FontWeight.Bold)
                        AppText("${order.format} · ${order.paymentMethod}", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
                        AppText("Preview only · no charge or fulfillment", color = PagesColors.Forest, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRecoveryScreen(type: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val (title, message) = when (type) {
        "payment" -> "Payment needs attention" to "The payment provider is not connected. No charge was made and your order was not created."
        "offline" -> "Offline sync is unavailable" to "Private book storage and sync are not configured. No book file or reading progress was changed."
        else -> "Book access is unavailable" to "The book access service is not connected. Your saved reading position and bookmarks are unchanged."
    }
    PageScaffold(onBack, "Try again") {
        RemoteErrorState(title, message, onRetry)
        AppText("If this continues, contact support from Parent Profile.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = PagesSpacing.Medium))
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
        OutlinedTextField(email, { email = it }, label = { AppText("Your email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PagesShapes.Control), singleLine = true, isError = submitted && !validEmail, supportingText = { if (submitted && !validEmail) AppText("Enter a valid email address.") })
        OutlinedTextField(message, { message = it.take(2000) }, label = { AppText("How can we help?") }, modifier = Modifier.fillMaxWidth().height(PagesSizes.SupportInputHeight), shape = RoundedCornerShape(PagesShapes.Control), isError = submitted && message.isBlank())
        PrimaryButton("Send support request", Icons.AutoMirrored.Filled.Send) { submitted = true; if (validEmail && message.isNotBlank()) onSubmit() }
        AppText("This preview does not send or store support messages.", color = PagesColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit, onSubscribe: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PagesColors.Paper,
        shape = RoundedCornerShape(PagesShapes.Hero),
        icon = { AppText("🎧✨", fontSize = 30.sp) },
        title = { AppText("Make story time magical", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = { AppText("Listen to the story with each word lighting up as it’s read. Premium is managed by a parent.", textAlign = TextAlign.Center, color = PagesColors.Muted) },
        confirmButton = { Button(onClick = onSubscribe, shape = RoundedCornerShape(PagesShapes.Control), colors = ButtonDefaults.buttonColors(containerColor = PagesColors.Forest)) { AppText("Explore Premium") } },
        dismissButton = { TextButton(onClick = onDismiss) { AppText("Not now", color = PagesColors.Forest) } }
    )
}
