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
        put("promotions", JSONArray().apply { snapshot.promotions.forEach { put(encodePromotion(it)) } })
    }

    private fun decodeSnapshot(json: JSONObject) = VersionedStorefrontSnapshot(
        manifest = decodeManifest(json.getJSONObject("manifest")),
        configuration = decodeConfiguration(json.getJSONObject("configuration")),
        products = json.getJSONArray("products").objects().map(::decodeProduct),
        promotions = (json.optJSONArray("promotions") ?: JSONArray()).objects().map(::decodePromotion)
    )

    /** Converts canonical Firestore records into the stable local cache model before strict decoding. */
    internal fun decodeFirestoreSnapshot(
        manifest: Map<String, Any?>,
        configuration: Map<String, Any?>,
        products: List<Map<String, Any?>>,
        promotions: List<Map<String, Any?>> = emptyList()
    ): VersionedStorefrontSnapshot = decodeSnapshot(JSONObject().apply {
        put("manifest", JSONObject(manifest))
        val canonicalConfiguration = JSONObject(configuration)
        val nativeConfiguration = canonicalConfigurationToNative(canonicalConfiguration)
        put("configuration", nativeConfiguration)
        put("products", JSONArray().apply { products.forEach { put(canonicalProductToNative(JSONObject(it), canonicalConfiguration)) } })
        put("promotions", JSONArray().apply { promotions.forEach { put(encodePromotion(decodeFirestorePromotion(it))) } })
    })

    /** Maps the shared storefront contract into the stable Android cache model. */
    private fun canonicalConfigurationToNative(configuration: JSONObject): JSONObject {
        if (configuration.has("bookCategories")) return configuration
        val catalog = configuration.getJSONObject("catalog")
        val community = configuration.getJSONObject("communityPolicy")
        fun categories(key: String): JSONArray = JSONArray().apply {
            catalog.getJSONArray(key).objects().forEach { category ->
                put(JSONObject().apply {
                    put("id", category.getString("id")); put("englishLabel", category.getJSONObject("label").getString("en"))
                    put("banglaLabel", category.getJSONObject("label").getString("bn")); put("sortOrder", category.getInt("sortOrder"))
                    put("enabled", category.getBoolean("enabled"))
                })
            }
        }
        return JSONObject().apply {
            put("revision", configuration.getLong("revision"))
            put("bookCategories", categories("bookCategories"))
            put("communityCategories", categories("communityCategories"))
            put("maximumCommunityImageBytes", community.getLong("maximumImageBytes"))
            put("allowedCommunityImageMimeTypes", community.getJSONArray("allowedImageMimeTypes"))
            put("productTypes", JSONArray().apply {
                catalog.getJSONArray("productTypes").objects().forEach { type ->
                    val label = type.getJSONObject("label")
                    put(JSONObject().apply {
                        put("id", type.getString("id")); put("englishLabel", label.getString("en")); put("banglaLabel", label.getString("bn"))
                        put("renderer", type.getString("renderer").uppercase()); put("schemaVersion", type.getInt("schemaVersion")); put("enabled", type.getBoolean("enabled"))
                        put("attributes", JSONArray().apply {
                            type.getJSONArray("attributes").objects().forEach { definition ->
                                val definitionLabel = definition.getJSONObject("label")
                                put(JSONObject().apply {
                                    put("id", definition.getString("id")); put("englishLabel", definitionLabel.getString("en")); put("banglaLabel", definitionLabel.getString("bn"))
                                    put("valueType", definition.getString("valueType").uppercase()); put("required", definition.getBoolean("required"))
                                    put("options", JSONArray().apply {
                                        (definition.optJSONArray("options") ?: JSONArray()).objects().forEach { option ->
                                            val optionLabel = option.getJSONObject("label")
                                            put(JSONObject().put("value", option.getString("value")).put("englishLabel", optionLabel.getString("en")).put("banglaLabel", optionLabel.getString("bn")))
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            })
            put("productCategories", categories("productCategories"))
            put("storefronts", JSONArray().apply {
                catalog.getJSONArray("storefronts").objects().forEach { store ->
                    val title = store.getJSONObject("title")
                    put(JSONObject().apply {
                        put("id", store.getString("id")); put("slug", store.getString("slug")); put("englishTitle", title.getString("en")); put("banglaTitle", title.getString("bn"))
                        put("productTypeIds", store.getJSONArray("productTypeIds")); put("categoryIds", store.optJSONArray("categoryIds") ?: JSONArray())
                        put("landingPageId", if (store.isNull("landingPageId")) JSONObject.NULL else store.getString("landingPageId"))
                        put("navigationVisible", store.optBoolean("navigationVisible", true)); put("enabled", store.getBoolean("enabled"))
                        put("sortOrder", store.getInt("sortOrder")); put("regions", store.optJSONArray("regions") ?: JSONArray())
                    })
                }
            })
            put("storePages", JSONArray().apply {
                catalog.getJSONArray("storePages").objects().forEach { page ->
                    val title = page.getJSONObject("title")
                    put(JSONObject().apply {
                        put("id", page.getString("id")); put("englishTitle", title.getString("en")); put("banglaTitle", title.getString("bn"))
                        put("schemaVersion", page.getInt("schemaVersion")); put("enabled", page.getBoolean("enabled"))
                        put("blocks", JSONArray().apply { page.getJSONArray("blocks").objects().forEach { block -> put(canonicalBlockToNative(block)) } })
                    })
                }
            })
        }
    }

    private fun canonicalBlockToNative(block: JSONObject): JSONObject {
        val type = block.getString("type")
        return JSONObject().apply {
            put("id", block.getString("id"))
            when (type) {
                "editorial_text_v1" -> {
                    val body = block.getJSONObject("body")
                    put("kind", "editorial"); put("englishBody", body.getString("en")); put("banglaBody", body.getString("bn"))
                }
                "category_rail_v1" -> {
                    val title = block.getJSONObject("title")
                    put("kind", "category"); put("englishTitle", title.getString("en")); put("banglaTitle", title.getString("bn")); put("categoryIds", block.getJSONArray("categoryIds"))
                }
                "product_collection_v1" -> {
                    val title = block.getJSONObject("title")
                    val query = block.optJSONObject("query") ?: JSONObject()
                    put("kind", "collection"); put("englishTitle", title.getString("en")); put("banglaTitle", title.getString("bn"))
                    put("storefrontId", if (query.isNull("storefrontId")) JSONObject.NULL else query.optString("storefrontId"))
                    put("categoryIds", query.optJSONArray("categoryIds") ?: JSONArray())
                    put("productTypeIds", query.optJSONArray("productTypeIds") ?: JSONArray())
                    put("productIds", query.optJSONArray("productIds") ?: JSONArray())
                    put("layout", block.getString("layout").uppercase())
                }
                "hero_v1" -> {
                    val title = block.getJSONObject("title")
                    val action = block.optJSONObject("action")
                    put("kind", "hero"); put("englishTitle", title.getString("en")); put("banglaTitle", title.getString("bn"))
                    put("imageAssetId", if (block.isNull("imageAssetId")) JSONObject.NULL else block.optString("imageAssetId"))
                    put("actionKind", if (action == null) JSONObject.NULL else action.getString("kind"))
                    put("actionTargetId", if (action == null) JSONObject.NULL else action.getString("targetId"))
                }
                else -> error("Unsupported published page block: $type")
            }
        }
    }

    private fun canonicalProductToNative(product: JSONObject, configuration: JSONObject): JSONObject {
        if (product.has("productTypeId")) return product
        val typeId = product.getString("typeId")
        val type = configuration.getJSONObject("catalog").getJSONArray("productTypes").objects().firstOrNull { it.getString("id") == typeId }
        val definitions = (type?.optJSONArray("attributes") ?: JSONArray()).objects().associateBy { it.getString("id") }
        return JSONObject().apply {
            put("id", product.getString("id")); put("productTypeId", typeId)
            put("title", localizedCopy(product.optJSONObject("title"))); put("description", localizedCopy(product.optJSONObject("description")))
            put("categoryIds", product.optJSONArray("categoryIds") ?: JSONArray()); put("imageAssetIds", product.optJSONArray("imageAssetIds") ?: JSONArray())
            put("published", product.optString("status") == "published")
            put("variants", JSONArray().apply {
                product.getJSONArray("variants").objects().forEach { variant ->
                    val label = variant.getJSONObject("label")
                    val availability = when (variant.getString("stockState")) {
                        "in_stock" -> "IN_STOCK"; "out_of_stock" -> "OUT_OF_STOCK"; "preorder" -> "PREORDER"; "not_applicable" -> "NOT_APPLICABLE"
                        else -> error("Unsupported stock state")
                    }
                    put(JSONObject().apply {
                        put("id", variant.getString("id")); put("label", JSONObject().put("english", label.getString("en")).put("bangla", label.getString("bn")))
                        put("priceMinor", variant.getLong("priceMinor")); put("currency", variant.getString("currency")); put("availability", availability)
                        put("stockQuantity", if (variant.isNull("stockQuantity") || !variant.has("stockQuantity")) JSONObject.NULL else variant.getInt("stockQuantity"))
                    })
                }
            })
            put("attributes", JSONObject().apply {
                val attributes = product.optJSONObject("attributes") ?: JSONObject()
                attributes.keys().forEach { key ->
                    val value = attributes.get(key)
                    val valueType = definitions[key]?.optString("valueType") ?: when (value) {
                        is Boolean -> "boolean"; is Number -> "number"; is JSONArray -> "text_list"; else -> "text"
                    }
                    val kind = if (valueType == "enum") "text" else valueType
                    val jsonValue = when (value) {
                        is JSONArray, is JSONObject -> value
                        is List<*> -> JSONArray(value)
                        else -> value
                    }
                    put(key, JSONObject().put("kind", kind).put("value", jsonValue))
                }
            })
            product.optJSONObject("bookDetails")?.let { details ->
                val edition = details.optJSONObject("physicalEdition") ?: JSONObject()
                put("bookDetails", JSONObject().apply {
                    put("ageBand", details.getString("ageBand")); put("readingLevel", details.getString("readingLevel"))
                    put("seriesId", if (details.isNull("seriesId")) JSONObject.NULL else details.optString("seriesId"))
                    put("seriesOrder", if (details.isNull("seriesOrder")) JSONObject.NULL else details.optInt("seriesOrder"))
                    put("pageCount", details.getInt("pageCount")); put("locales", details.getJSONArray("locales")); put("contentVersion", details.getInt("contentVersion"))
                    put("access", details.getString("access")); put("pdfInAppEnabled", details.getBoolean("pdfInAppEnabled"))
                    put("physicalEdition", JSONObject().put("available", edition.optBoolean("available", false)).put("variantId", if (edition.isNull("variantId")) JSONObject.NULL else edition.optString("variantId")))
                })
            }
        }
    }

    private fun localizedCopy(copy: JSONObject?): JSONObject = JSONObject()
        .put("english", copy?.optString("en", "") ?: "")
        .put("bangla", copy?.optString("bn", "") ?: "")

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
        product.bookDetails?.let { details -> put("bookDetails", encodeBookDetails(details)) }
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
        attributes = json.getJSONObject("attributes").keys().asSequence().associateWith { key -> decodeAttribute(json.getJSONObject("attributes").getJSONObject(key)) },
        bookDetails = json.optJSONObject("bookDetails")?.let(::decodeBookDetails)
    )

    private fun encodeBookDetails(details: ManagedBookDetails) = JSONObject().apply {
        put("ageBand", details.ageBand); put("readingLevel", details.readingLevel)
        put("seriesId", details.seriesId ?: JSONObject.NULL); put("seriesOrder", details.seriesOrder ?: JSONObject.NULL)
        put("pageCount", details.pageCount); put("locales", JSONArray(details.locales)); put("contentVersion", details.contentVersion)
        put("access", details.access); put("pdfInAppEnabled", details.pdfInAppEnabled)
        put("physicalEdition", JSONObject().apply {
            put("available", details.physicalEditionAvailable); put("variantId", details.physicalEditionVariantId ?: JSONObject.NULL)
        })
    }

    private fun decodeBookDetails(json: JSONObject): ManagedBookDetails {
        val physicalEdition = json.getJSONObject("physicalEdition")
        return ManagedBookDetails(
            ageBand = json.getString("ageBand"), readingLevel = json.getString("readingLevel"),
            seriesId = json.nullableString("seriesId"), seriesOrder = json.nullableInt("seriesOrder"),
            pageCount = json.getInt("pageCount"), locales = json.getJSONArray("locales").strings(),
            contentVersion = json.getInt("contentVersion"), access = json.getString("access"),
            pdfInAppEnabled = json.getBoolean("pdfInAppEnabled"), physicalEditionAvailable = physicalEdition.getBoolean("available"),
            physicalEditionVariantId = physicalEdition.nullableString("variantId")
        )
    }

    private fun encodePromotion(promotion: ManagedPromotion) = JSONObject().apply {
        put("id", promotion.id); put("status", promotion.status); put("name", encodeCopy(promotion.name))
        put("targetKind", promotion.targetKind); put("targetProductId", promotion.targetProductId ?: JSONObject.NULL)
        put("targetCategoryIds", JSONArray(promotion.targetCategoryIds)); put("discountType", promotion.discountType)
        put("discountValue", promotion.discountValue); put("currency", promotion.currency ?: JSONObject.NULL)
        put("startsAt", promotion.startsAt); put("endsAt", promotion.endsAt); put("bannerText", encodeCopy(promotion.bannerText))
        put("bannerImageAssetId", promotion.bannerImageAssetId ?: JSONObject.NULL); put("destinationKind", promotion.destinationKind)
        put("destinationId", promotion.destinationId); put("stackingPolicy", promotion.stackingPolicy); put("priceFloorMinor", promotion.priceFloorMinor)
    }

    private fun decodePromotion(json: JSONObject) = ManagedPromotion(
        id = json.getString("id"), status = json.getString("status"), name = decodeCopy(json.getJSONObject("name")),
        targetKind = json.getString("targetKind"), targetProductId = json.nullableString("targetProductId"),
        targetCategoryIds = json.getJSONArray("targetCategoryIds").strings(), discountType = json.getString("discountType"),
        discountValue = json.getLong("discountValue"), currency = json.nullableString("currency"),
        startsAt = json.getString("startsAt"), endsAt = json.getString("endsAt"), bannerText = decodeCopy(json.getJSONObject("bannerText")),
        bannerImageAssetId = json.nullableString("bannerImageAssetId"), destinationKind = json.getString("destinationKind"),
        destinationId = json.getString("destinationId"), stackingPolicy = json.getString("stackingPolicy"), priceFloorMinor = json.getLong("priceFloorMinor")
    )

    /** Parses the canonical public Firestore promotion shape into the stable local cache projection. */
    internal fun decodeFirestorePromotion(document: Map<String, Any?>): ManagedPromotion {
        val json = JSONObject(document)
        val targeting = json.getJSONObject("targeting")
        val discount = json.getJSONObject("discount")
        val schedule = json.getJSONObject("schedule")
        val banner = json.getJSONObject("banner")
        val destination = banner.getJSONObject("destination")
        return ManagedPromotion(
            id = json.getString("id"), status = json.getString("status"), name = decodeCopy(json.getJSONObject("name")),
            targetKind = targeting.getString("kind"), targetProductId = targeting.nullableString("productId"),
            targetCategoryIds = (targeting.optJSONArray("categoryIds") ?: JSONArray()).strings(),
            discountType = discount.getString("type"), discountValue = discount.getLong("value"), currency = discount.nullableString("currency"),
            startsAt = schedule.getString("startsAt"), endsAt = schedule.getString("endsAt"),
            bannerText = decodeCopy(banner.getJSONObject("text")), bannerImageAssetId = banner.nullableString("imageAssetId"),
            destinationKind = destination.getString("kind"), destinationId = destination.getString("targetId"),
            stackingPolicy = json.getString("stackingPolicy"), priceFloorMinor = json.getLong("priceFloorMinor")
        )
    }

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
