package com.littlepages.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PublishedContentManifest(
    val schemaVersion: Int,
    val publicationRevision: Long,
    val configurationRevision: Long,
    val catalogRevision: Long,
    val supportedLocales: Set<String>,
    val contentHash: String,
    val minimumClientVersions: Map<String, String> = emptyMap()
)

data class VersionedStorefrontSnapshot(
    val manifest: PublishedContentManifest,
    val configuration: CatalogConfiguration,
    val products: List<ManagedProduct> = emptyList(),
    val promotions: List<ManagedPromotion> = emptyList()
)

/** Read-only boundary to the server-published configuration. Client apps never publish admin data. */
interface PublishedStorefrontConfigurationRemote {
    suspend fun fetchCurrent(): VersionedStorefrontSnapshot
}

/** Persistence boundary for the last valid published configuration, normally app-private storage. */
interface StorefrontConfigurationCache {
    suspend fun readLastValid(): VersionedStorefrontSnapshot?
    suspend fun saveLastValid(snapshot: VersionedStorefrontSnapshot)
}

interface PublishedStorefrontContentSource : StorefrontConfigurationSource {
    val publishedContent: StateFlow<VersionedStorefrontSnapshot?>
}

sealed interface ConfigurationRefreshResult {
    data class Updated(val revision: Long) : ConfigurationRefreshResult
    data class UsingCached(val revision: Long) : ConfigurationRefreshResult
    data class UsingFallback(val configurationRevision: Long) : ConfigurationRefreshResult
    data object NoConfigurationAvailable : ConfigurationRefreshResult
}

/**
 * Shared client-side repository policy, independent of Firebase SDK types. A Firestore adapter and
 * durable cache can implement the two source interfaces after project registration. Only valid,
 * supported, non-stale revisions are exposed to UI code.
 */
class VersionedStorefrontConfigurationRepository(
    private val remote: PublishedStorefrontConfigurationRemote,
    private val cache: StorefrontConfigurationCache,
    fallback: CatalogConfiguration,
    private val androidClientVersion: String
) : PublishedStorefrontContentSource {
    private val _configuration = MutableStateFlow(fallback)
    private val _publishedContent = MutableStateFlow<VersionedStorefrontSnapshot?>(null)
    private val refreshMutex = Mutex()
    private var activeManifest: PublishedContentManifest? = null
    override val configuration: StateFlow<CatalogConfiguration> = _configuration
    override val publishedContent: StateFlow<VersionedStorefrontSnapshot?> = _publishedContent

    suspend fun initialize(): ConfigurationRefreshResult {
        val cached = cache.readLastValid()
        if (cached != null && isSupportedAndValid(cached) && cached.configuration.revision >= _configuration.value.revision) {
            _configuration.value = cached.configuration
            _publishedContent.value = cached
            activeManifest = cached.manifest
        }
        return refresh()
    }

    suspend fun refresh(): ConfigurationRefreshResult = refreshMutex.withLock {
        try {
            val snapshot = remote.fetchCurrent()
            require(isSupportedAndValid(snapshot)) { "Unsupported or invalid storefront configuration" }
            val currentManifest = activeManifest
            val activeRevision = currentManifest?.publicationRevision ?: 0L
            when {
                snapshot.manifest.publicationRevision < activeRevision -> ConfigurationRefreshResult.UsingCached(activeRevision)
                snapshot.manifest.publicationRevision == activeRevision && currentManifest != null -> {
                    require(snapshot.manifest.contentHash == currentManifest.contentHash) {
                        "A publication revision cannot change its content hash"
                    }
                    ConfigurationRefreshResult.UsingCached(activeRevision)
                }
                else -> {
                    cache.saveLastValid(snapshot)
                    _configuration.value = snapshot.configuration
                    _publishedContent.value = snapshot
                    activeManifest = snapshot.manifest
                    ConfigurationRefreshResult.Updated(snapshot.manifest.publicationRevision)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val cached = cache.readLastValid()
            val activeRevision = activeManifest?.publicationRevision ?: 0L
            if (cached != null && isSupportedAndValid(cached) && cached.manifest.publicationRevision >= activeRevision) {
                _configuration.value = cached.configuration
                _publishedContent.value = cached
                activeManifest = cached.manifest
                ConfigurationRefreshResult.UsingCached(cached.manifest.publicationRevision)
            } else if (activeManifest != null) {
                ConfigurationRefreshResult.UsingCached(activeRevision)
            } else if (_configuration.value.revision > 0) {
                ConfigurationRefreshResult.UsingFallback(_configuration.value.revision)
            } else {
                ConfigurationRefreshResult.NoConfigurationAvailable
            }
        }
    }

    private fun isSupportedAndValid(snapshot: VersionedStorefrontSnapshot): Boolean {
        val config = snapshot.configuration
        val manifest = snapshot.manifest
        if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION || config.revision < 1) return false
        if (manifest.publicationRevision < 1 || manifest.catalogRevision < 1 || manifest.configurationRevision != config.revision) return false
        if (manifest.supportedLocales.isEmpty() || manifest.supportedLocales.any { it !in SUPPORTED_LOCALES }) return false
        if (!manifest.contentHash.matches(Regex("^[a-f0-9]{64}$"))) return false
        val minimumAndroidVersion = manifest.minimumClientVersions[ANDROID_PLATFORM]
        if (minimumAndroidVersion != null && !meetsMinimumVersion(androidClientVersion, minimumAndroidVersion)) return false
        if (config.productTypes.map { it.id }.toSet().size != config.productTypes.size) return false
        if (config.storefronts.map { it.id }.toSet().size != config.storefronts.size) return false
        if (config.storePages.map { it.id }.toSet().size != config.storePages.size) return false
        val typeIds = config.productTypes.filter { it.enabled }.map { it.id }.toSet()
        val allTypeIds = config.productTypes.map { it.id }.toSet()
        val storeIds = config.storefronts.map { it.id }.toSet()
        val pageIds = config.storePages.filter { it.enabled }.map { it.id }.toSet()
        val categories = (config.productCategories.ifEmpty { config.bookCategories }).map { it.id }.toSet()
        val productIds = snapshot.products.map { it.id }.toSet()
        val categoriesById = (config.productCategories.ifEmpty { config.bookCategories }).associateBy { it.id }
        val productTypesById = config.productTypes.associateBy { it.id }
        val storesById = config.storefronts.associateBy { it.id }
        if (snapshot.promotions.map { it.id }.toSet().size != snapshot.promotions.size) return false
        val promotionsValid = snapshot.promotions.all { promotion ->
            val startsAt = runCatching { java.time.Instant.parse(promotion.startsAt) }.getOrNull()
            val endsAt = runCatching { java.time.Instant.parse(promotion.endsAt) }.getOrNull()
            val targetValid = when (promotion.targetKind) {
                "all" -> promotion.targetProductId == null && promotion.targetCategoryIds.isEmpty()
                "product" -> promotion.targetProductId?.let(productIds::contains) == true && promotion.targetCategoryIds.isEmpty()
                "categories" -> promotion.targetProductId == null && promotion.targetCategoryIds.isNotEmpty() && promotion.targetCategoryIds.all(categoriesById::containsKey)
                else -> false
            }
            val destinationValid = when (promotion.destinationKind) {
                "product" -> promotion.destinationId in productIds
                "category" -> promotion.destinationId in categoriesById
                "storefront" -> promotion.destinationId in storesById
                else -> false
            }
            promotion.id.isNotBlank() && promotion.status in setOf("scheduled", "active", "paused", "archived") &&
                promotion.name.english.isNotBlank() && promotion.name.bangla.isNotBlank() && promotion.bannerText.english.isNotBlank() && promotion.bannerText.bangla.isNotBlank() &&
                startsAt != null && endsAt != null && endsAt > startsAt && targetValid && destinationValid &&
                (promotion.discountType == "percentage" && promotion.discountValue in 1L..99L || promotion.discountType == "fixed_amount" && promotion.discountValue > 0 && promotion.currency == "BDT") &&
                promotion.stackingPolicy == "best_single_discount" && promotion.priceFloorMinor == 1L
        }
        val storesValid = config.storefronts.filter { it.enabled }.all { store ->
            store.productTypeIds.all(typeIds::contains) &&
                (store.landingPageId == null || store.landingPageId in pageIds) &&
                store.categoryIds.all { it in categories }
        }
        val pagesValid = config.storePages.filter { it.enabled }.all { page ->
            val blockIds = page.blocks.map { it.id }
            blockIds.toSet().size == blockIds.size && page.blocks.all { block ->
                when (block) {
                    is ManagedPageBlock.EditorialText -> true
                    is ManagedPageBlock.CategoryRail -> block.categoryIds.all { it in categories }
                    is ManagedPageBlock.ProductCollection ->
                        (block.storefrontId == null || block.storefrontId in storeIds) &&
                            block.categoryIds.all { it in categories } &&
                            block.productTypeIds.all { it in allTypeIds } &&
                            block.productIds.all { it in productIds }
                    is ManagedPageBlock.Hero -> when (block.actionKind) {
                        null -> block.actionTargetId == null
                        "storefront" -> block.actionTargetId?.let { it in storeIds } == true
                        "category" -> block.actionTargetId?.let { it in categories } == true
                        "product" -> block.actionTargetId?.let { it in productIds } == true
                        else -> false
                    }
                }
            }
        }
        val productsValid = snapshot.products.filter { it.published }.all { product ->
            val bookDetails = product.bookDetails
            val bookDetailsValid = if (productTypesById[product.productTypeId]?.renderer != ProductRenderer.BOOK_V1) true else {
                bookDetails != null && bookDetails.ageBand in setOf("0-2", "3-5", "6-8", "9-12") &&
                    bookDetails.readingLevel.isNotBlank() && bookDetails.pageCount > 0 && bookDetails.contentVersion > 0 &&
                    bookDetails.access in setOf("free", "premium", "paid") && bookDetails.pdfInAppEnabled &&
                    bookDetails.locales.isNotEmpty() && bookDetails.locales.all { it in manifest.supportedLocales } &&
                    (!bookDetails.physicalEditionAvailable || bookDetails.physicalEditionVariantId?.let { variantId -> product.variants.any { it.id == variantId } } == true)
            }
            product.id.isNotBlank() && product.productTypeId in typeIds &&
                product.categoryIds.all { it in categories } &&
                bookDetailsValid &&
                product.variants.isNotEmpty() && product.variants.all { variant ->
                    variant.id.isNotBlank() && variant.priceMinor >= 0 && variant.currency == "BDT" &&
                        (variant.stockQuantity == null || variant.stockQuantity >= 0)
                }
        }
        return storesValid && pagesValid && productsValid && promotionsValid && productIds.size == snapshot.products.size
    }

    private fun meetsMinimumVersion(currentVersion: String, minimumVersion: String): Boolean {
        fun parse(version: String): List<Int>? {
            if (!VERSION_PATTERN.matches(version)) return null
            val parts = version.split('.').map { it.toIntOrNull() ?: return null }.toMutableList()
            while (parts.size < 3) parts += 0
            return parts
        }

        val current = parse(currentVersion) ?: return false
        val minimum = parse(minimumVersion) ?: return false
        for (index in 0..2) {
            if (current[index] != minimum[index]) return current[index] > minimum[index]
        }
        return true
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val ANDROID_PLATFORM = "android"
        val VERSION_PATTERN = Regex("^[0-9]+(?:\\.[0-9]+){0,2}$")
        val SUPPORTED_LOCALES = setOf("en", "bn")
    }
}
