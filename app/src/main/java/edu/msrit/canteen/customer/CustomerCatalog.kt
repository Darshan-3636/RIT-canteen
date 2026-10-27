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
enum class FoodPhoto { DOSA, BIRYANI, COFFEE, IDLI, LEMON_RICE, WATER_BOTTLE, PLAIN_DOSA, SET_DOSA, RAVA_IDLI, VADA_SAMBAR, VEN_PONGAL, POORI_SAGU, VEG_PULAO, CURD_RICE, BISI_BELE_BATH, CHAPATI_MEAL, VEG_THALI, TOMATO_RICE, NORTH_LUNCH, NORTH_SNACK, VEG_SAMOSA, KHASTA_KACHORI, ALOO_PUFF, VEG_CUTLET, ONION_PAKORA, BHEL_PURI, SEV_PURI, MASALA_PURI, PANEER_ROLL, VEG_SANDWICH, GRILLED_CHEESE_SANDWICH, CORN_CHAAT, BREAD_PAKORA, BATATA_VADA, VEG_MOMOS, MASALA_FRIES, GROUP_COFFEE, BISCUITS, BAKERY_VEG_PUFF, BAKERY_PANEER_PUFF, BAKERY_MUSHROOM_PUFF, BUTTER_CROISSANT, CHOCOLATE_CROISSANT, CINNAMON_ROLL, BANANA_BREAD, GARLIC_BUN, CHEESE_BUN, BLUEBERRY_MUFFIN, CHOCOLATE_MUFFIN, FUDGE_BROWNIE, VANILLA_PASTRY, EGGLESS_CHOCOLATE_CAKE, SHORTBREAD_COOKIES, ALMOND_COOKIES, JUICE_CARTON, PANTRY_WATER, ORANGE_JUICE_BOTTLE, MANGO_JUICE_BOTTLE, APPLE_JUICE_BOTTLE, LEMON_ICED_TEA, COLD_COFFEE_BOTTLE, COCONUT_WATER_PACK, SALTED_LASSI_PACK, SPARKLING_WATER, TRAIL_MIX, ROASTED_PEANUTS, POTATO_CHIPS, BANANA_CHIPS, PANTRY_BISCUIT_PACK, GRANOLA_BAR, ENERGY_BAR, CRACKER_PACK }
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
        "plain-dosa" to FoodPhoto.PLAIN_DOSA,
        "set-dosa" to FoodPhoto.SET_DOSA,
        "rava-idli" to FoodPhoto.RAVA_IDLI,
        "vada-sambar" to FoodPhoto.VADA_SAMBAR,
        "ven-pongal" to FoodPhoto.VEN_PONGAL,
        "poori-sagu" to FoodPhoto.POORI_SAGU,
        "veg-pulao" to FoodPhoto.VEG_PULAO,
        "curd-rice" to FoodPhoto.CURD_RICE,
        "bisi-bele-bath" to FoodPhoto.BISI_BELE_BATH,
        "chapati-meal" to FoodPhoto.CHAPATI_MEAL,
        "veg-thali" to FoodPhoto.VEG_THALI,
        "tomato-rice" to FoodPhoto.TOMATO_RICE,
        "north-lunch" to FoodPhoto.NORTH_LUNCH,
        "north-snack" to FoodPhoto.NORTH_SNACK,
        "veg-samosa" to FoodPhoto.VEG_SAMOSA,
        "khasta-kachori" to FoodPhoto.KHASTA_KACHORI,
        "aloo-puff" to FoodPhoto.ALOO_PUFF,
        "veg-cutlet" to FoodPhoto.VEG_CUTLET,
        "onion-pakora" to FoodPhoto.ONION_PAKORA,
        "bhel-puri" to FoodPhoto.BHEL_PURI,
        "sev-puri" to FoodPhoto.SEV_PURI,
        "masala-puri" to FoodPhoto.MASALA_PURI,
        "paneer-roll" to FoodPhoto.PANEER_ROLL,
        "veg-sandwich" to FoodPhoto.VEG_SANDWICH,
        "grilled-cheese-sandwich" to FoodPhoto.GRILLED_CHEESE_SANDWICH,
        "corn-chaat" to FoodPhoto.CORN_CHAAT,
        "bread-pakora" to FoodPhoto.BREAD_PAKORA,
        "batata-vada" to FoodPhoto.BATATA_VADA,
        "veg-momos" to FoodPhoto.VEG_MOMOS,
        "masala-fries" to FoodPhoto.MASALA_FRIES,
        "corner-coffee" to FoodPhoto.GROUP_COFFEE,
        "corner-biscuits" to FoodPhoto.BISCUITS,
        "bakery-veg-puff" to FoodPhoto.BAKERY_VEG_PUFF,
        "bakery-paneer-puff" to FoodPhoto.BAKERY_PANEER_PUFF,
        "bakery-mushroom-puff" to FoodPhoto.BAKERY_MUSHROOM_PUFF,
        "butter-croissant" to FoodPhoto.BUTTER_CROISSANT,
        "chocolate-croissant" to FoodPhoto.CHOCOLATE_CROISSANT,
        "cinnamon-roll" to FoodPhoto.CINNAMON_ROLL,
        "banana-bread" to FoodPhoto.BANANA_BREAD,
        "garlic-bun" to FoodPhoto.GARLIC_BUN,
        "cheese-bun" to FoodPhoto.CHEESE_BUN,
        "blueberry-muffin" to FoodPhoto.BLUEBERRY_MUFFIN,
        "chocolate-muffin" to FoodPhoto.CHOCOLATE_MUFFIN,
        "fudge-brownie" to FoodPhoto.FUDGE_BROWNIE,
        "vanilla-pastry" to FoodPhoto.VANILLA_PASTRY,
        "eggless-chocolate-cake" to FoodPhoto.EGGLESS_CHOCOLATE_CAKE,
        "shortbread-cookies" to FoodPhoto.SHORTBREAD_COOKIES,
        "almond-cookies" to FoodPhoto.ALMOND_COOKIES,
        "pantry-juice" to FoodPhoto.JUICE_CARTON,
        "pantry-water" to FoodPhoto.PANTRY_WATER,
        "orange-juice-bottle" to FoodPhoto.ORANGE_JUICE_BOTTLE,
        "mango-juice-bottle" to FoodPhoto.MANGO_JUICE_BOTTLE,
        "apple-juice-bottle" to FoodPhoto.APPLE_JUICE_BOTTLE,
        "lemon-iced-tea" to FoodPhoto.LEMON_ICED_TEA,
        "cold-coffee-bottle" to FoodPhoto.COLD_COFFEE_BOTTLE,
        "coconut-water-pack" to FoodPhoto.COCONUT_WATER_PACK,
        "salted-lassi-pack" to FoodPhoto.SALTED_LASSI_PACK,
        "sparkling-water" to FoodPhoto.SPARKLING_WATER,
        "trail-mix" to FoodPhoto.TRAIL_MIX,
        "roasted-peanuts" to FoodPhoto.ROASTED_PEANUTS,
        "potato-chips" to FoodPhoto.POTATO_CHIPS,
        "banana-chips" to FoodPhoto.BANANA_CHIPS,
        "pantry-biscuit-pack" to FoodPhoto.PANTRY_BISCUIT_PACK,
        "granola-bar" to FoodPhoto.GRANOLA_BAR,
        "energy-bar" to FoodPhoto.ENERGY_BAR,
        "cracker-pack" to FoodPhoto.CRACKER_PACK,
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
        MenuCategory("dosa", "Dosa"),
        MenuCategory("breakfast", "Breakfast"),
        MenuCategory("rice", "Rice Dishes"),
        MenuCategory("meals", "Meals & Thalis"),
        MenuCategory("snacks", "Snacks"),
        MenuCategory("chaat", "Chaat"),
        MenuCategory("rolls", "Rolls"),
        MenuCategory("sandwiches", "Sandwiches"),
        MenuCategory("combos", "Combos"),
        MenuCategory("puffs", "Puffs"),
        MenuCategory("breads", "Breads & Buns"),
        MenuCategory("cakes", "Cakes & Pastries"),
        MenuCategory("cookies", "Cookies & Biscuits"),
        MenuCategory("drinks", "Beverages"),
        MenuCategory("packs", "Packaged Snacks"),
    )
    private val initialSellers = listOf(
        Seller(MAIN_ID, "Main Canteen", "Campus South Indian breakfasts, rice and cooked meals · demo menu.", "RIT campus · exact location to be confirmed", OrderingCapabilities(true, true), linkedSetOf("dosa", "breakfast", "rice", "meals", "drinks"), isMainCanteen = true),
        Seller("north-counter", "North Counter · demo", "Savoury snacks, chaat and quick bites for group requests.", null, OrderingCapabilities(false, true), linkedSetOf("snacks", "chaat", "rolls", "sandwiches", "combos")),
        Seller("coffee-corner", "Coffee Corner · Bakery · demo", "Bakery puffs, buns, pastries and coffee for group requests.", null, OrderingCapabilities(false, true), linkedSetOf("puffs", "breads", "cakes", "cookies", "drinks")),
        Seller("campus-pantry", "Campus Pantry · demo", "Sealed drinks and grab-and-go packaged snacks.", null, OrderingCapabilities(false, true), linkedSetOf("drinks", "packs")),
    ).map { it.copy(photo = CatalogArtwork.sellerPhoto(it.id)) }
    private val initialItems = listOf(
        MenuItem("masala-dosa", MAIN_ID, "dosa", "Masala Dosa", "Crisp dosa with spiced potato filling, sambar and chutney.", Price(50), FoodKind.PREPARED, Dietary.VEGETARIAN, featured = true),
        MenuItem("veg-biryani", MAIN_ID, "rice", "Veg Biryani", "Fragrant rice with vegetables and warm spices.", Price(85), FoodKind.PREPARED, Dietary.VEGETARIAN, featured = true),
        MenuItem("filter-coffee", MAIN_ID, "drinks", "Filter Coffee", "South Indian-style coffee served hot.", Price(30), FoodKind.PREPARED, Dietary.VEGETARIAN, featured = true),
        MenuItem("idli", MAIN_ID, "breakfast", "Idli with Sambar", "Steamed idlis served with sambar and coconut chutney.", Price(35), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("lemon-rice", MAIN_ID, "rice", "Lemon Rice", "Rice tempered with lemon, curry leaves and peanuts.", Price(45), FoodKind.PREPARED, Dietary.VEGETARIAN, availability = ItemAvailability.UNAVAILABLE),
        MenuItem("water-bottle", MAIN_ID, "drinks", "Bottled Water", "Sealed unbranded drinking water for group requests.", Price(20), FoodKind.PACKAGED, Dietary.VEGETARIAN),
        MenuItem("plain-dosa", MAIN_ID, "dosa", "Plain Dosa", "Thin golden dosa with coconut chutney and sambar.", Price(40), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("set-dosa", MAIN_ID, "dosa", "Set Dosa", "Soft set of small dosas served with chutney and sambar.", Price(55), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("rava-idli", MAIN_ID, "breakfast", "Rava Idli", "Steamed semolina idli with spices, chutney and sambar.", Price(40), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("vada-sambar", MAIN_ID, "breakfast", "Vada Sambar", "Crisp lentil vadas with warm sambar and chutney.", Price(45), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("ven-pongal", MAIN_ID, "breakfast", "Ven Pongal", "Comforting rice and lentil pongal tempered with pepper and cashew.", Price(50), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("poori-sagu", MAIN_ID, "breakfast", "Poori Sagu", "Puffed pooris with a mildly spiced vegetable sagu.", Price(60), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("veg-pulao", MAIN_ID, "rice", "Veg Pulao", "Fluffy rice cooked with vegetables and aromatic whole spices.", Price(70), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("curd-rice", MAIN_ID, "rice", "Curd Rice", "Creamy curd rice with a South Indian mustard and curry-leaf tempering.", Price(40), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("bisi-bele-bath", MAIN_ID, "rice", "Bisi Bele Bath", "Karnataka-style spiced lentil rice with vegetables.", Price(65), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("chapati-meal", MAIN_ID, "meals", "Chapati Meal", "Chapatis with a vegetable curry, dal and simple sides.", Price(80), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("veg-thali", MAIN_ID, "meals", "Veg Thali", "Rice, chapati and a selection of small vegetarian curries and sides.", Price(110), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("tomato-rice", MAIN_ID, "rice", "Tomato Rice", "Tangy tomato rice tempered with curry leaves and mustard.", Price(50), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("north-lunch", "north-counter", "combos", "North Counter Lunch Combo", "A sample quick lunch combo with savoury North Counter bites.", Price(90), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("north-snack", "north-counter", "snacks", "Assorted Savoury Snack Box", "Assorted prepared savoury snacks for sharing.", Price(65), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("veg-samosa", "north-counter", "snacks", "Veg Samosa", "Crisp pastry filled with spiced potato and peas.", Price(25), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("khasta-kachori", "north-counter", "snacks", "Khasta Kachori", "Flaky kachori with a spiced savoury filling.", Price(30), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("aloo-puff", "north-counter", "snacks", "Aloo Puff", "Flaky savoury puff pastry with potato filling.", Price(30), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("veg-cutlet", "north-counter", "snacks", "Veg Cutlet", "Crisp vegetable cutlet with a lightly spiced centre.", Price(35), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("onion-pakora", "north-counter", "snacks", "Onion Pakora", "Golden onion fritters with a chutney side.", Price(40), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("bhel-puri", "north-counter", "chaat", "Bhel Puri", "Puffed rice chaat with vegetables, chutneys and sev.", Price(50), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("sev-puri", "north-counter", "chaat", "Sev Puri", "Crisp puris topped with potato, chutneys and sev.", Price(55), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("masala-puri", "north-counter", "chaat", "Masala Puri", "Crisp puris with a warm spiced pea gravy and toppings.", Price(55), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("paneer-roll", "north-counter", "rolls", "Paneer Roll", "Soft wrap filled with spiced paneer and vegetables.", Price(85), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("veg-sandwich", "north-counter", "sandwiches", "Veg Sandwich", "Vegetable sandwich with fresh cucumber, tomato and chutney.", Price(55), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("grilled-cheese-sandwich", "north-counter", "sandwiches", "Grilled Cheese Sandwich", "Toasted sandwich with melted cheese and a crisp exterior.", Price(75), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("corn-chaat", "north-counter", "chaat", "Sweet Corn Chaat", "Sweet corn tossed with onion, tomato, herbs and tangy spice.", Price(50), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("bread-pakora", "north-counter", "snacks", "Bread Pakora", "Bread slices in a crisp gram-flour coating.", Price(40), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("batata-vada", "north-counter", "snacks", "Batata Vada", "Spiced potato fritter with a crisp gram-flour shell.", Price(30), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("veg-momos", "north-counter", "snacks", "Steamed Veg Momos", "Steamed vegetable dumplings with a dipping sauce.", Price(75), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("masala-fries", "north-counter", "snacks", "Masala Fries", "Fries tossed with a tangy savoury masala.", Price(65), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("corner-coffee", "coffee-corner", "drinks", "Fresh Coffee", "Freshly prepared coffee for a group request.", Price(35), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("corner-biscuits", "coffee-corner", "cookies", "Butter Biscuits", "Sealed butter biscuits for a quick bakery snack.", Price(25), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("bakery-veg-puff", "coffee-corner", "puffs", "Bakery Veg Puff", "Flaky bakery puff filled with seasoned vegetables.", Price(35), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("bakery-paneer-puff", "coffee-corner", "puffs", "Paneer Puff", "Flaky puff pastry with a spiced paneer filling.", Price(45), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("bakery-mushroom-puff", "coffee-corner", "puffs", "Mushroom Puff", "Flaky bakery puff with a savoury mushroom filling.", Price(45), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("butter-croissant", "coffee-corner", "breads", "Butter Croissant", "Layered buttery croissant with a crisp golden crust.", Price(65), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("chocolate-croissant", "coffee-corner", "breads", "Chocolate Croissant", "Flaky croissant with a chocolate centre.", Price(80), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("cinnamon-roll", "coffee-corner", "breads", "Cinnamon Roll", "Soft spiral bun with cinnamon filling and light glaze.", Price(70), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("banana-bread", "coffee-corner", "breads", "Banana Bread Slice", "Moist slice of banana bread with a tender crumb.", Price(60), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("garlic-bun", "coffee-corner", "breads", "Garlic Bun", "Soft baked bun with garlic and herbs.", Price(50), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("cheese-bun", "coffee-corner", "breads", "Cheese Bun", "Golden baked bun with a cheese filling.", Price(55), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("blueberry-muffin", "coffee-corner", "cakes", "Blueberry Muffin", "Bakery muffin studded with blueberries.", Price(75), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("chocolate-muffin", "coffee-corner", "cakes", "Chocolate Muffin", "Soft chocolate muffin with chocolate pieces.", Price(70), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("fudge-brownie", "coffee-corner", "cakes", "Fudge Brownie", "Dense chocolate brownie square with a fudgy centre.", Price(85), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("vanilla-pastry", "coffee-corner", "cakes", "Vanilla Pastry", "Layered vanilla cream pastry slice.", Price(90), FoodKind.PREPARED, Dietary.NOT_SPECIFIED),
        MenuItem("eggless-chocolate-cake", "coffee-corner", "cakes", "Eggless Chocolate Cake Slice", "Chocolate cake slice made without egg in this demo description.", Price(95), FoodKind.PREPARED, Dietary.VEGETARIAN),
        MenuItem("shortbread-cookies", "coffee-corner", "cookies", "Shortbread Cookies", "Sealed pack of crumbly shortbread cookies.", Price(45), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("almond-cookies", "coffee-corner", "cookies", "Almond Cookies", "Sealed almond cookie pack with visible almond pieces.", Price(55), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("pantry-juice", "campus-pantry", "drinks", "Fruit Juice Carton", "Sealed unbranded fruit juice carton for group requests.", Price(30), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("pantry-water", "campus-pantry", "drinks", "Water Bottle", "Sealed unbranded drinking water bottle.", Price(20), FoodKind.PACKAGED, Dietary.VEGETARIAN, tags = setOf("cold drinks", "drinks")),
        MenuItem("orange-juice-bottle", "campus-pantry", "drinks", "Orange Juice Bottle", "Sealed unbranded orange juice bottle.", Price(45), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("mango-juice-bottle", "campus-pantry", "drinks", "Mango Juice Bottle", "Sealed unbranded mango juice bottle.", Price(45), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("apple-juice-bottle", "campus-pantry", "drinks", "Apple Juice Bottle", "Sealed unbranded apple juice bottle.", Price(50), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("lemon-iced-tea", "campus-pantry", "drinks", "Lemon Iced Tea", "Sealed unbranded lemon iced tea bottle.", Price(55), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("cold-coffee-bottle", "campus-pantry", "drinks", "Cold Coffee Bottle", "Sealed unbranded ready-to-drink cold coffee.", Price(60), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("coconut-water-pack", "campus-pantry", "drinks", "Coconut Water Pack", "Sealed unbranded coconut water pack.", Price(55), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("salted-lassi-pack", "campus-pantry", "drinks", "Salted Lassi Pack", "Sealed unbranded salted lassi pack.", Price(40), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("sparkling-water", "campus-pantry", "drinks", "Sparkling Water", "Sealed unbranded sparkling water bottle.", Price(45), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED, tags = setOf("cold drinks", "drinks")),
        MenuItem("trail-mix", "campus-pantry", "packs", "Trail Mix Pouch", "Sealed pouch of mixed nuts, seeds and dried fruit.", Price(70), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("roasted-peanuts", "campus-pantry", "packs", "Roasted Peanuts", "Sealed pack of roasted peanuts.", Price(35), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("potato-chips", "campus-pantry", "packs", "Potato Chips", "Sealed pack of crisp potato chips.", Price(30), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("banana-chips", "campus-pantry", "packs", "Banana Chips", "Sealed pack of crisp banana chips.", Price(45), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("pantry-biscuit-pack", "campus-pantry", "packs", "Biscuit Pack", "Sealed assorted biscuit pack.", Price(25), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("granola-bar", "campus-pantry", "packs", "Granola Bar", "Individually wrapped oat and fruit granola bar.", Price(50), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("energy-bar", "campus-pantry", "packs", "Energy Bar", "Individually wrapped snack energy bar.", Price(70), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
        MenuItem("cracker-pack", "campus-pantry", "packs", "Savoury Crackers", "Sealed pack of savoury crackers.", Price(35), FoodKind.PACKAGED, Dietary.NOT_SPECIFIED),
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
