package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue

/** Read-only presentation data. A server-backed source can replace [PreviewCatalog] later. */
interface CustomerCatalogSource {
    val sellers: List<Seller>
    val categories: List<MenuCategory>
    val items: List<MenuItem>
}

enum class FoodKind { PREPARED, PACKAGED }
enum class Dietary { VEGETARIAN, NON_VEGETARIAN, NOT_SPECIFIED }
enum class ItemAvailability { AVAILABLE, UNAVAILABLE }
enum class UnavailableVisibility { SHOW, HIDE }
enum class SellerAvailability { UNKNOWN, AVAILABLE, UNAVAILABLE }
enum class FoodPhoto { DOSA, BIRYANI, COFFEE, IDLI, LEMON_RICE, WATER_BOTTLE,
    NORTH_LUNCH, NORTH_SNACK, GROUP_COFFEE, BISCUITS, JUICE_CARTON, PANTRY_WATER }
enum class SellerPhoto { CAMPUS_CONTEXT, MAIN_CANTEEN, NORTH_COUNTER, COFFEE_CORNER, CAMPUS_PANTRY }

/** Bundled concept imagery is keyed by stable fixture IDs; new seller-created IDs use a neutral fallback. */
object CatalogArtwork {
    private val sellerPhotos = mapOf(
        "main-canteen" to SellerPhoto.MAIN_CANTEEN,
        "north-counter" to SellerPhoto.NORTH_COUNTER,
        "coffee-corner" to SellerPhoto.COFFEE_CORNER,
        "campus-pantry" to SellerPhoto.CAMPUS_PANTRY,
    )
    private val itemPhotos = mapOf(
        "masala-dosa" to FoodPhoto.DOSA,
        "veg-biryani" to FoodPhoto.BIRYANI,
        "filter-coffee" to FoodPhoto.COFFEE,
        "idli" to FoodPhoto.IDLI,
        "lemon-rice" to FoodPhoto.LEMON_RICE,
        "water-bottle" to FoodPhoto.WATER_BOTTLE,
        "north-lunch" to FoodPhoto.NORTH_LUNCH,
        "north-snack" to FoodPhoto.NORTH_SNACK,
        "corner-coffee" to FoodPhoto.GROUP_COFFEE,
        "corner-biscuits" to FoodPhoto.BISCUITS,
        "pantry-juice" to FoodPhoto.JUICE_CARTON,
        "pantry-water" to FoodPhoto.PANTRY_WATER,
    )
    fun sellerPhoto(id: String): SellerPhoto? = sellerPhotos[id]
    fun itemPhoto(id: String): FoodPhoto? = itemPhotos[id]
}

data class Price(val rupees: Int) {
    init { require(rupees >= 0) }
    val display: String get() = "₹$rupees"
}

data class OrderingCapabilities(val futureIndividualPrepared: Boolean, val bulk: Boolean)
data class SellerHours(val display: String)
data class MenuCategory(val id: String, val name: String)
data class Seller(
    val id: String,
    val name: String,
    val description: String,
    val location: String?,
    val capabilities: OrderingCapabilities,
    val categoryIds: Set<String>,
    val availability: SellerAvailability = SellerAvailability.UNKNOWN,
    val hours: SellerHours? = null,
    val photo: SellerPhoto? = null,
    val isMainCanteen: Boolean = false,
    val localImageUri: String? = null,
)

data class MenuItem(
    val id: String,
    val sellerId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val price: Price,
    val kind: FoodKind,
    val dietary: Dietary,
    val availability: ItemAvailability = ItemAvailability.AVAILABLE,
    val published: Boolean = true,
    val unavailableVisibility: UnavailableVisibility = UnavailableVisibility.SHOW,
    val photo: FoodPhoto? = null,
    val featured: Boolean = false,
    /** Transient content URI selected in the debug photo picker; never an uploaded asset. */
    val localImageUri: String? = null,
    val tags: Set<String> = emptySet(),
)

enum class ItemEligibility { FUTURE_INDIVIDUAL_AND_BULK, BULK_ONLY, UNAVAILABLE }

data class OrderingAction(val label: String, val enabled: Boolean)

/** Prompt 3 has no live transaction flow, regardless of catalog eligibility. */
fun orderingAction(eligibility: ItemEligibility): OrderingAction = OrderingAction(
    if (eligibility == ItemEligibility.UNAVAILABLE) "Item unavailable" else "Ordering unavailable in preview",
    enabled = false,
)

fun itemEligibility(seller: Seller, item: MenuItem): ItemEligibility {
    if (seller.id != item.sellerId || seller.availability == SellerAvailability.UNAVAILABLE ||
        item.availability == ItemAvailability.UNAVAILABLE || !item.published || item.categoryId !in seller.categoryIds
    ) return ItemEligibility.UNAVAILABLE
    return if (seller.isMainCanteen && seller.capabilities.futureIndividualPrepared && item.kind == FoodKind.PREPARED) {
        ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK
    } else ItemEligibility.BULK_ONLY
}

fun CustomerCatalogSource.visibleItems(sellerId: String? = null, categoryId: String? = null, query: String = ""): List<MenuItem> =
    items.filter { item ->
        item.published &&
            sellers.any { it.id == item.sellerId && item.categoryId in it.categoryIds } &&
            (item.availability == ItemAvailability.AVAILABLE || item.unavailableVisibility == UnavailableVisibility.SHOW) &&
            (sellerId == null || item.sellerId == sellerId) &&
            (categoryId == null || item.categoryId == categoryId) &&
            (query.isBlank() || item.name.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true) ||
                categories.find { it.id == item.categoryId }?.name?.contains(query, ignoreCase = true) == true ||
                item.tags.any { it.contains(query, ignoreCase = true) })
    }

fun CustomerCatalogSource.visibleSellers(query: String = "", categoryId: String? = null): List<Seller> =
    sellers.filter { seller ->
        (categoryId == null || categoryId in seller.categoryIds) &&
            (query.isBlank() || seller.name.contains(query, ignoreCase = true) ||
                seller.description.contains(query, ignoreCase = true) ||
                categories.any { it.id in seller.categoryIds && it.name.contains(query, ignoreCase = true) })
    }

/** Fictional identities and illustrative prices; never a live seller catalog. */
object PreviewCatalog : CustomerCatalogSource {
    const val MAIN_ID = "main-canteen"
    private val initialCategories = listOf(
        MenuCategory("dosa", "Dosa"), MenuCategory("rice", "Rice"),
        MenuCategory("snacks", "Snacks"), MenuCategory("drinks", "Beverages"),
        MenuCategory("packs", "Packaged"),
    )
    private val initialSellers = listOf(
        Seller(MAIN_ID, "Main Canteen", "Campus meals and familiar favourites.", "RIT campus · exact location to be confirmed", OrderingCapabilities(true, true), setOf("dosa", "rice", "snacks", "drinks", "packs"), isMainCanteen = true),
        Seller("north-counter", "North Counter · demo", "Prepared lunch and snacks for group requests.", null, OrderingCapabilities(false, true), setOf("rice", "snacks")),
        Seller("coffee-corner", "Coffee Corner · demo", "Drinks and light bites for group requests.", null, OrderingCapabilities(false, true), setOf("drinks", "snacks", "packs")),
        Seller("campus-pantry", "Campus Pantry · demo", "Packaged snacks and drinks for group requests.", null, OrderingCapabilities(false, true), setOf("packs", "drinks")),
    ).map { it.copy(photo = CatalogArtwork.sellerPhoto(it.id)) }
    private val initialItems = listOf(
        MenuItem("masala-dosa", MAIN_ID, "dosa", "Masala Dosa", "Crisp dosa with spiced potato filling, sambar and chutney.", Price(50), FoodKind.PREPARED, Dietary.VEGETARIAN, photo = FoodPhoto.DOSA, featured = true),
        MenuItem("veg-biryani", MAIN_ID, "rice", "Veg Biryani", "Fragrant rice with vegetables and warm spices.", Price(85), FoodKind.PREPARED, Dietary.VEGETARIAN, photo = FoodPhoto.BIRYANI, featured = true),
        MenuItem("filter-coffee", MAIN_ID, "drinks", "Filter Coffee", "South Indian-style coffee served hot.", Price(30), FoodKind.PREPARED, Dietary.VEGETARIAN, photo = FoodPhoto.COFFEE, featured = true),
        MenuItem("idli", MAIN_ID, "snacks", "Idli", "Steamed rice cakes served with sambar and chutney.", Price(35), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("lemon-rice", MAIN_ID, "rice", "Lemon Rice", "Rice tempered with lemon, curry leaves and peanuts.", Price(45), FoodKind.PREPARED, Dietary.VEGETARIAN, availability = ItemAvailability.UNAVAILABLE),
        MenuItem("water-bottle", MAIN_ID, "packs", "Bottled Water", "Sealed drinking water for group requests.", Price(20), FoodKind.PACKAGED, Dietary.VEGETARIAN),
        MenuItem("north-lunch", "north-counter", "rice", "North Counter Lunch", "Illustrative prepared lunch plate.", Price(90), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("north-snack", "north-counter", "snacks", "Savoury Snack Box", "Assorted prepared savoury bites for a group.", Price(65), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("corner-coffee", "coffee-corner", "drinks", "Group Coffee", "Freshly prepared coffee for a group request.", Price(35), FoodKind.PREPARED, Dietary.VEGETARIAN, photo = FoodPhoto.COFFEE),
        MenuItem("corner-biscuits", "coffee-corner", "packs", "Biscuit Pack", "Sealed biscuit pack.", Price(25), FoodKind.PACKAGED, Dietary.VEGETARIAN),
        MenuItem("pantry-juice", "campus-pantry", "packs", "Juice Carton", "Packaged fruit drink for group requests.", Price(30), FoodKind.PACKAGED, Dietary.VEGETARIAN),
        MenuItem("pantry-water", "campus-pantry", "drinks", "Water Bottle", "Sealed drinking water.", Price(20), FoodKind.PACKAGED, Dietary.VEGETARIAN),
    ).map { it.copy(photo = CatalogArtwork.itemPhoto(it.id)) }

    override var categories by mutableStateOf(initialCategories)
        private set
    // Seller category order changes must trigger Compose even though Set equality ignores order.
    override var sellers by mutableStateOf(initialSellers, referentialEqualityPolicy())
        private set
    override var items by mutableStateOf(initialItems)
        private set

    /** Presentation fixtures only. Seller-management routes are absent in release builds. */
    fun applyDemoSnapshot(newSellers: List<Seller>, newCategories: List<MenuCategory>, newItems: List<MenuItem>) {
        sellers = newSellers
        categories = newCategories
        items = newItems
    }

    fun resetDemoSnapshot() = applyDemoSnapshot(initialSellers, initialCategories, initialItems)
}
