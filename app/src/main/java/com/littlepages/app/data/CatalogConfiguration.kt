package com.littlepages.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Published taxonomy is data so clients do not need a release when admins add labels. */
data class ManagedCategory(
    val id: String,
    val englishLabel: String,
    val banglaLabel: String,
    val sortOrder: Int,
    val enabled: Boolean = true
) {
    fun label(language: String): String = if (language == "bn") banglaLabel else englishLabel
}

data class CatalogConfiguration(
    val revision: Long,
    val bookCategories: List<ManagedCategory>,
    val communityCategories: List<ManagedCategory>,
    val maximumCommunityImageBytes: Long,
    val allowedCommunityImageMimeTypes: Set<String>,
    val productTypes: List<ManagedProductType> = emptyList(),
    val productCategories: List<ManagedCategory> = emptyList(),
    val storefronts: List<ManagedStorefront> = emptyList(),
    val storePages: List<ManagedStorePage> = emptyList()
)

enum class ProductRenderer { BOOK_V1, PHYSICAL_GOOD_V1 }

data class ManagedProductType(
    val id: String,
    val englishLabel: String,
    val banglaLabel: String,
    val renderer: ProductRenderer,
    val schemaVersion: Int = 1,
    val enabled: Boolean = true,
    val attributes: List<ManagedProductAttributeDefinition> = emptyList()
)

/** Admin-declared metadata can add fields to a supported product template without Android code changes. */
data class ManagedProductAttributeDefinition(
    val id: String,
    val englishLabel: String,
    val banglaLabel: String,
    val valueType: ProductAttributeType,
    val required: Boolean = false,
    val options: List<ManagedProductAttributeOption> = emptyList()
)

data class ManagedProductAttributeOption(
    val value: String,
    val englishLabel: String,
    val banglaLabel: String
)

enum class ProductAttributeType { TEXT, NUMBER, BOOLEAN, ENUM, TEXT_LIST }

data class ManagedStorefront(
    val id: String,
    val slug: String,
    val englishTitle: String,
    val banglaTitle: String,
    val productTypeIds: List<String>,
    val categoryIds: List<String>,
    val landingPageId: String? = null,
    val navigationVisible: Boolean = true,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val regions: List<String> = emptyList()
)

sealed interface ManagedPageBlock {
    val id: String
    data class EditorialText(override val id: String, val englishBody: String, val banglaBody: String) : ManagedPageBlock
    data class CategoryRail(override val id: String, val englishTitle: String, val banglaTitle: String, val categoryIds: List<String>) : ManagedPageBlock
    data class ProductCollection(
        override val id: String,
        val englishTitle: String,
        val banglaTitle: String,
        val storefrontId: String? = null,
        val categoryIds: List<String> = emptyList(),
        val productTypeIds: List<String> = emptyList(),
        val productIds: List<String> = emptyList(),
        val layout: CollectionLayout = CollectionLayout.GRID
    ) : ManagedPageBlock
    data class Hero(
        override val id: String,
        val englishTitle: String,
        val banglaTitle: String,
        val imageAssetId: String? = null,
        val actionKind: String? = null,
        val actionTargetId: String? = null
    ) : ManagedPageBlock
}

enum class CollectionLayout { GRID, CAROUSEL }

data class ManagedStorePage(
    val id: String,
    val englishTitle: String,
    val banglaTitle: String,
    val schemaVersion: Int = 1,
    val enabled: Boolean = true,
    val blocks: List<ManagedPageBlock> = emptyList()
)

data class LocalizedCopy(val english: String, val bangla: String)

enum class ProductAvailability { IN_STOCK, OUT_OF_STOCK, PREORDER, NOT_APPLICABLE }

sealed interface ProductAttributeValue {
    data class Text(val value: String) : ProductAttributeValue
    data class NumberValue(val value: Double) : ProductAttributeValue
    data class BooleanValue(val value: Boolean) : ProductAttributeValue
    data class TextList(val values: List<String>) : ProductAttributeValue
}

data class ManagedProductVariant(
    val id: String,
    val label: LocalizedCopy,
    val priceMinor: Long,
    val currency: String = "BDT",
    val availability: ProductAvailability,
    val stockQuantity: Int? = null
)

/** Shared sellable item data. Reader assets and entitlements stay in the book-specific model. */
data class ManagedProduct(
    val id: String,
    val productTypeId: String,
    val title: LocalizedCopy,
    val description: LocalizedCopy,
    val categoryIds: List<String>,
    val imageAssetIds: List<String>,
    val variants: List<ManagedProductVariant>,
    val attributes: Map<String, ProductAttributeValue> = emptyMap(),
    val published: Boolean = true
)

/** Shared storefront view of configuration, retained as a type alias for existing catalogue UI. */
typealias StorefrontConfiguration = CatalogConfiguration

/** UI observes this contract; production will use a Firestore-backed implementation. */
interface CatalogConfigurationSource {
    val configuration: StateFlow<CatalogConfiguration>
}

typealias StorefrontConfigurationSource = CatalogConfigurationSource

class PreviewCatalogConfigurationSource : CatalogConfigurationSource {
    override val configuration = MutableStateFlow(
        CatalogConfiguration(
            revision = 1,
            bookCategories = listOf(
                ManagedCategory("animals", "Animals", "প্রাণী", 10),
                ManagedCategory("friendship", "Friendship", "বন্ধুত্ব", 20),
                ManagedCategory("nature", "Nature", "প্রকৃতি", 30),
                ManagedCategory("bedtime", "Bedtime", "ঘুমের আগে", 40),
                ManagedCategory("courage", "Courage", "সাহস", 50)
            ),
            communityCategories = listOf(
                ManagedCategory("stories", "Stories", "গল্প", 10),
                ManagedCategory("discussions", "Discussions", "আলোচনা", 20),
                ManagedCategory("tips", "Tips", "পরামর্শ", 30),
                ManagedCategory("activities", "Activities", "কার্যক্রম", 40)
            ),
            maximumCommunityImageBytes = 5L * 1024L * 1024L,
            allowedCommunityImageMimeTypes = setOf("image/jpeg", "image/png", "image/webp"),
            productTypes = listOf(
                ManagedProductType("book", "Books", "বই", ProductRenderer.BOOK_V1),
                ManagedProductType("toy", "Toys", "খেলনা", ProductRenderer.PHYSICAL_GOOD_V1)
            ),
            productCategories = listOf(
                ManagedCategory("animals", "Animals", "প্রাণী", 10),
                ManagedCategory("friendship", "Friendship", "বন্ধুত্ব", 20),
                ManagedCategory("nature", "Nature", "প্রকৃতি", 30),
                ManagedCategory("bedtime", "Bedtime", "ঘুমের আগে", 40),
                ManagedCategory("courage", "Courage", "সাহস", 50)
            ),
            storefronts = listOf(
                ManagedStorefront("books", "books", "Books", "বই", listOf("book"), listOf("animals", "friendship", "nature", "bedtime", "courage"), "books-home", sortOrder = 10, regions = listOf("BD")),
                ManagedStorefront("toys", "toys", "Toys", "খেলনা", listOf("toy"), emptyList(), enabled = false, navigationVisible = false, sortOrder = 20, regions = listOf("BD"))
            ),
            storePages = listOf(
                ManagedStorePage(
                    "books-home", "Books", "বই", blocks = listOf(
                        ManagedPageBlock.EditorialText("welcome", "Stories for every little reader.", "প্রতিটি ছোট পাঠকের জন্য গল্প।"),
                        ManagedPageBlock.ProductCollection("featured-books", "Popular this week", "এই সপ্তাহের জনপ্রিয়", storefrontId = "books", layout = CollectionLayout.CAROUSEL)
                    )
                )
            )
        )
    )
}
