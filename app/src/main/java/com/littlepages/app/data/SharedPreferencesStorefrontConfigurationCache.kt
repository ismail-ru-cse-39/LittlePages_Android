package com.littlepages.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** App-private, single-record cache for the last fully validated publication snapshot. */
class SharedPreferencesStorefrontConfigurationCache(context: Context) : StorefrontConfigurationCache {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun readLastValid(): VersionedStorefrontSnapshot? = withContext(Dispatchers.IO) {
        val serialized = preferences.getString(SNAPSHOT_KEY, null) ?: return@withContext null
        runCatching { decodeSnapshot(JSONObject(serialized)) }.getOrNull()
    }

    override suspend fun saveLastValid(snapshot: VersionedStorefrontSnapshot) = withContext(Dispatchers.IO) {
        val serialized = encodeSnapshot(snapshot).toString()
        check(preferences.edit().putString(SNAPSHOT_KEY, serialized).commit()) {
            "Could not persist the last valid storefront snapshot"
        }
    }

    private fun encodeSnapshot(snapshot: VersionedStorefrontSnapshot) = JSONObject().apply {
        put("manifest", encodeManifest(snapshot.manifest))
        put("configuration", encodeConfiguration(snapshot.configuration))
        put("products", JSONArray().apply { snapshot.products.forEach { put(encodeProduct(it)) } })
    }

    private fun decodeSnapshot(json: JSONObject) = VersionedStorefrontSnapshot(
        manifest = decodeManifest(json.getJSONObject("manifest")),
        configuration = decodeConfiguration(json.getJSONObject("configuration")),
        products = json.getJSONArray("products").objects().map(::decodeProduct)
    )

    /** Converts the documented Firestore client projection through the same strict cache decoder. */
    internal fun decodeFirestoreSnapshot(
        manifest: Map<String, Any?>,
        configuration: Map<String, Any?>,
        products: List<Map<String, Any?>>
    ): VersionedStorefrontSnapshot = decodeSnapshot(JSONObject().apply {
        put("manifest", JSONObject(manifest))
        put("configuration", JSONObject(configuration))
        put("products", JSONArray().apply { products.forEach { put(JSONObject(it)) } })
    })

    private fun encodeManifest(manifest: PublishedContentManifest) = JSONObject().apply {
        put("schemaVersion", manifest.schemaVersion)
        put("publicationRevision", manifest.publicationRevision)
        put("configurationRevision", manifest.configurationRevision)
        put("catalogRevision", manifest.catalogRevision)
        put("supportedLocales", JSONArray(manifest.supportedLocales.toList()))
        put("contentHash", manifest.contentHash)
        put("minimumClientVersions", JSONObject(manifest.minimumClientVersions))
    }

    private fun decodeManifest(json: JSONObject) = PublishedContentManifest(
        schemaVersion = json.getInt("schemaVersion"),
        publicationRevision = json.getLong("publicationRevision"),
        configurationRevision = json.getLong("configurationRevision"),
        catalogRevision = json.getLong("catalogRevision"),
        supportedLocales = json.getJSONArray("supportedLocales").strings().toSet(),
        contentHash = json.getString("contentHash"),
        minimumClientVersions = (json.optJSONObject("minimumClientVersions") ?: JSONObject()).stringMap()
    )

    private fun encodeConfiguration(config: CatalogConfiguration) = JSONObject().apply {
        put("revision", config.revision)
        put("bookCategories", JSONArray().apply { config.bookCategories.forEach { put(encodeCategory(it)) } })
        put("communityCategories", JSONArray().apply { config.communityCategories.forEach { put(encodeCategory(it)) } })
        put("maximumCommunityImageBytes", config.maximumCommunityImageBytes)
        put("allowedCommunityImageMimeTypes", JSONArray(config.allowedCommunityImageMimeTypes.toList()))
        put("productTypes", JSONArray().apply { config.productTypes.forEach { put(encodeProductType(it)) } })
        put("productCategories", JSONArray().apply { config.productCategories.forEach { put(encodeCategory(it)) } })
        put("storefronts", JSONArray().apply { config.storefronts.forEach { put(encodeStorefront(it)) } })
        put("storePages", JSONArray().apply { config.storePages.forEach { put(encodePage(it)) } })
    }

    private fun decodeConfiguration(json: JSONObject) = CatalogConfiguration(
        revision = json.getLong("revision"),
        bookCategories = json.getJSONArray("bookCategories").objects().map(::decodeCategory),
        communityCategories = json.getJSONArray("communityCategories").objects().map(::decodeCategory),
        maximumCommunityImageBytes = json.getLong("maximumCommunityImageBytes"),
        allowedCommunityImageMimeTypes = json.getJSONArray("allowedCommunityImageMimeTypes").strings().toSet(),
        productTypes = json.getJSONArray("productTypes").objects().map(::decodeProductType),
        productCategories = json.getJSONArray("productCategories").objects().map(::decodeCategory),
        storefronts = json.getJSONArray("storefronts").objects().map(::decodeStorefront),
        storePages = json.getJSONArray("storePages").objects().map(::decodePage)
    )

    private fun encodeCategory(category: ManagedCategory) = JSONObject().apply {
        put("id", category.id); put("englishLabel", category.englishLabel); put("banglaLabel", category.banglaLabel)
        put("sortOrder", category.sortOrder); put("enabled", category.enabled)
    }

    private fun decodeCategory(json: JSONObject) = ManagedCategory(
        id = json.getString("id"), englishLabel = json.getString("englishLabel"),
        banglaLabel = json.getString("banglaLabel"), sortOrder = json.getInt("sortOrder"),
        enabled = json.getBoolean("enabled")
    )

    private fun encodeProductType(type: ManagedProductType) = JSONObject().apply {
        put("id", type.id); put("englishLabel", type.englishLabel); put("banglaLabel", type.banglaLabel)
        put("renderer", type.renderer.name); put("schemaVersion", type.schemaVersion); put("enabled", type.enabled)
        put("attributes", JSONArray().apply { type.attributes.forEach { definition ->
            put(JSONObject().apply {
                put("id", definition.id); put("englishLabel", definition.englishLabel); put("banglaLabel", definition.banglaLabel)
                put("valueType", definition.valueType.name); put("required", definition.required)
                put("options", JSONArray().apply { definition.options.forEach { option ->
                    put(JSONObject().apply { put("value", option.value); put("englishLabel", option.englishLabel); put("banglaLabel", option.banglaLabel) })
                } })
            })
        } })
    }

    private fun decodeProductType(json: JSONObject) = ManagedProductType(
        id = json.getString("id"), englishLabel = json.getString("englishLabel"), banglaLabel = json.getString("banglaLabel"),
        renderer = ProductRenderer.valueOf(json.getString("renderer")), schemaVersion = json.getInt("schemaVersion"),
        enabled = json.getBoolean("enabled"), attributes = json.getJSONArray("attributes").objects().map { definition ->
            ManagedProductAttributeDefinition(
                id = definition.getString("id"), englishLabel = definition.getString("englishLabel"),
                banglaLabel = definition.getString("banglaLabel"), valueType = ProductAttributeType.valueOf(definition.getString("valueType")),
                required = definition.getBoolean("required"), options = definition.getJSONArray("options").objects().map { option ->
                    ManagedProductAttributeOption(option.getString("value"), option.getString("englishLabel"), option.getString("banglaLabel"))
                }
            )
        }
    )

    private fun encodeStorefront(store: ManagedStorefront) = JSONObject().apply {
        put("id", store.id); put("slug", store.slug); put("englishTitle", store.englishTitle); put("banglaTitle", store.banglaTitle)
        put("productTypeIds", JSONArray(store.productTypeIds)); put("categoryIds", JSONArray(store.categoryIds))
        put("landingPageId", store.landingPageId ?: JSONObject.NULL); put("navigationVisible", store.navigationVisible)
        put("enabled", store.enabled); put("sortOrder", store.sortOrder); put("regions", JSONArray(store.regions))
    }

    private fun decodeStorefront(json: JSONObject) = ManagedStorefront(
        id = json.getString("id"), slug = json.getString("slug"), englishTitle = json.getString("englishTitle"),
        banglaTitle = json.getString("banglaTitle"), productTypeIds = json.getJSONArray("productTypeIds").strings(),
        categoryIds = json.getJSONArray("categoryIds").strings(), landingPageId = json.nullableString("landingPageId"),
        navigationVisible = json.getBoolean("navigationVisible"), enabled = json.getBoolean("enabled"),
        sortOrder = json.getInt("sortOrder"), regions = json.getJSONArray("regions").strings()
    )

    private fun encodePage(page: ManagedStorePage) = JSONObject().apply {
        put("id", page.id); put("englishTitle", page.englishTitle); put("banglaTitle", page.banglaTitle)
        put("schemaVersion", page.schemaVersion); put("enabled", page.enabled)
        put("blocks", JSONArray().apply { page.blocks.forEach { put(encodeBlock(it)) } })
    }

    private fun decodePage(json: JSONObject) = ManagedStorePage(
        id = json.getString("id"), englishTitle = json.getString("englishTitle"), banglaTitle = json.getString("banglaTitle"),
        schemaVersion = json.getInt("schemaVersion"), enabled = json.getBoolean("enabled"),
        blocks = json.getJSONArray("blocks").objects().map(::decodeBlock)
    )

    private fun encodeBlock(block: ManagedPageBlock): JSONObject = JSONObject().apply {
        put("id", block.id)
        when (block) {
            is ManagedPageBlock.EditorialText -> { put("kind", "editorial"); put("englishBody", block.englishBody); put("banglaBody", block.banglaBody) }
            is ManagedPageBlock.CategoryRail -> { put("kind", "category"); put("englishTitle", block.englishTitle); put("banglaTitle", block.banglaTitle); put("categoryIds", JSONArray(block.categoryIds)) }
            is ManagedPageBlock.ProductCollection -> {
                put("kind", "collection"); put("englishTitle", block.englishTitle); put("banglaTitle", block.banglaTitle)
                put("storefrontId", block.storefrontId ?: JSONObject.NULL); put("categoryIds", JSONArray(block.categoryIds))
                put("productTypeIds", JSONArray(block.productTypeIds)); put("productIds", JSONArray(block.productIds)); put("layout", block.layout.name)
            }
            is ManagedPageBlock.Hero -> {
                put("kind", "hero"); put("englishTitle", block.englishTitle); put("banglaTitle", block.banglaTitle)
                put("imageAssetId", block.imageAssetId ?: JSONObject.NULL); put("actionKind", block.actionKind ?: JSONObject.NULL)
                put("actionTargetId", block.actionTargetId ?: JSONObject.NULL)
            }
        }
    }

    private fun decodeBlock(json: JSONObject): ManagedPageBlock = when (json.getString("kind")) {
        "editorial" -> ManagedPageBlock.EditorialText(json.getString("id"), json.getString("englishBody"), json.getString("banglaBody"))
        "category" -> ManagedPageBlock.CategoryRail(json.getString("id"), json.getString("englishTitle"), json.getString("banglaTitle"), json.getJSONArray("categoryIds").strings())
        "collection" -> ManagedPageBlock.ProductCollection(
            id = json.getString("id"), englishTitle = json.getString("englishTitle"), banglaTitle = json.getString("banglaTitle"),
            storefrontId = json.nullableString("storefrontId"), categoryIds = json.getJSONArray("categoryIds").strings(),
            productTypeIds = json.getJSONArray("productTypeIds").strings(), productIds = json.getJSONArray("productIds").strings(),
            layout = CollectionLayout.valueOf(json.getString("layout"))
        )
        "hero" -> ManagedPageBlock.Hero(
            id = json.getString("id"), englishTitle = json.getString("englishTitle"), banglaTitle = json.getString("banglaTitle"),
            imageAssetId = json.nullableString("imageAssetId"), actionKind = json.nullableString("actionKind"),
            actionTargetId = json.nullableString("actionTargetId")
        )
        else -> error("Unknown page block kind")
    }

    private fun encodeProduct(product: ManagedProduct) = JSONObject().apply {
        put("id", product.id); put("productTypeId", product.productTypeId)
        put("title", encodeCopy(product.title)); put("description", encodeCopy(product.description))
        put("categoryIds", JSONArray(product.categoryIds)); put("imageAssetIds", JSONArray(product.imageAssetIds)); put("published", product.published)
        put("variants", JSONArray().apply { product.variants.forEach { variant -> put(JSONObject().apply {
            put("id", variant.id); put("label", encodeCopy(variant.label)); put("priceMinor", variant.priceMinor)
            put("currency", variant.currency); put("availability", variant.availability.name)
            put("stockQuantity", variant.stockQuantity ?: JSONObject.NULL)
        }) } })
        put("attributes", JSONObject().apply { product.attributes.forEach { (id, value) -> put(id, encodeAttribute(value)) } })
    }

    private fun decodeProduct(json: JSONObject) = ManagedProduct(
        id = json.getString("id"), productTypeId = json.getString("productTypeId"), title = decodeCopy(json.getJSONObject("title")),
        description = decodeCopy(json.getJSONObject("description")), categoryIds = json.getJSONArray("categoryIds").strings(),
        imageAssetIds = json.getJSONArray("imageAssetIds").strings(), published = json.getBoolean("published"),
        variants = json.getJSONArray("variants").objects().map { variant ->
            ManagedProductVariant(
                id = variant.getString("id"), label = decodeCopy(variant.getJSONObject("label")), priceMinor = variant.getLong("priceMinor"),
                currency = variant.getString("currency"), availability = ProductAvailability.valueOf(variant.getString("availability")),
                stockQuantity = variant.nullableInt("stockQuantity")
            )
        },
        attributes = json.getJSONObject("attributes").keys().asSequence().associateWith { key -> decodeAttribute(json.getJSONObject("attributes").getJSONObject(key)) }
    )

    private fun encodeAttribute(value: ProductAttributeValue) = JSONObject().apply {
        when (value) {
            is ProductAttributeValue.Text -> { put("kind", "text"); put("value", value.value) }
            is ProductAttributeValue.NumberValue -> { put("kind", "number"); put("value", value.value) }
            is ProductAttributeValue.BooleanValue -> { put("kind", "boolean"); put("value", value.value) }
            is ProductAttributeValue.TextList -> { put("kind", "text_list"); put("value", JSONArray(value.values)) }
        }
    }

    private fun decodeAttribute(json: JSONObject): ProductAttributeValue = when (json.getString("kind")) {
        "text" -> ProductAttributeValue.Text(json.getString("value"))
        "number" -> ProductAttributeValue.NumberValue(json.getDouble("value"))
        "boolean" -> ProductAttributeValue.BooleanValue(json.getBoolean("value"))
        "text_list" -> ProductAttributeValue.TextList(json.getJSONArray("value").strings())
        else -> error("Unknown product attribute kind")
    }

    private fun encodeCopy(copy: LocalizedCopy) = JSONObject().put("english", copy.english).put("bangla", copy.bangla)
    private fun decodeCopy(json: JSONObject) = LocalizedCopy(json.getString("english"), json.getString("bangla"))

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }

    private companion object {
        const val PREFERENCES_NAME = "published_storefront_cache_v1"
        const val SNAPSHOT_KEY = "last_valid_snapshot"
    }
}
