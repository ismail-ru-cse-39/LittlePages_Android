package com.littlepages.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VersionedStorefrontSnapshot(
    val schemaVersion: Int,
    val configuration: CatalogConfiguration
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

sealed interface ConfigurationRefreshResult {
    data class Updated(val revision: Long) : ConfigurationRefreshResult
    data class UsingCached(val revision: Long) : ConfigurationRefreshResult
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
    fallback: CatalogConfiguration
) : CatalogConfigurationSource {
    private val _configuration = MutableStateFlow(fallback)
    override val configuration: StateFlow<CatalogConfiguration> = _configuration

    suspend fun initialize(): ConfigurationRefreshResult {
        val cached = cache.readLastValid()
        if (cached != null && isSupportedAndValid(cached) && cached.configuration.revision >= _configuration.value.revision) {
            _configuration.value = cached.configuration
        }
        return refresh()
    }

    suspend fun refresh(): ConfigurationRefreshResult {
        return try {
            val snapshot = remote.fetchCurrent()
            require(isSupportedAndValid(snapshot)) { "Unsupported or invalid storefront configuration" }
            if (snapshot.configuration.revision < _configuration.value.revision) {
                return ConfigurationRefreshResult.UsingCached(_configuration.value.revision)
            }
            cache.saveLastValid(snapshot)
            _configuration.value = snapshot.configuration
            ConfigurationRefreshResult.Updated(snapshot.configuration.revision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val cached = cache.readLastValid()
            if (cached != null && isSupportedAndValid(cached) && cached.configuration.revision >= _configuration.value.revision) {
                _configuration.value = cached.configuration
                ConfigurationRefreshResult.UsingCached(cached.configuration.revision)
            } else if (_configuration.value.revision > 0) {
                ConfigurationRefreshResult.UsingCached(_configuration.value.revision)
            } else {
                ConfigurationRefreshResult.NoConfigurationAvailable
            }
        }
    }

    private fun isSupportedAndValid(snapshot: VersionedStorefrontSnapshot): Boolean {
        val config = snapshot.configuration
        if (snapshot.schemaVersion != SUPPORTED_SCHEMA_VERSION || config.revision < 1) return false
        if (config.productTypes.map { it.id }.toSet().size != config.productTypes.size) return false
        if (config.storefronts.map { it.id }.toSet().size != config.storefronts.size) return false
        if (config.storePages.map { it.id }.toSet().size != config.storePages.size) return false
        val typeIds = config.productTypes.filter { it.enabled }.map { it.id }.toSet()
        val pageIds = config.storePages.filter { it.enabled }.map { it.id }.toSet()
        val categories = (config.productCategories.ifEmpty { config.bookCategories }).map { it.id }.toSet()
        return config.storefronts.filter { it.enabled }.all { store ->
            store.productTypeIds.all(typeIds::contains) &&
                (store.landingPageId == null || store.landingPageId in pageIds) &&
                store.categoryIds.all { it in categories }
        }
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
