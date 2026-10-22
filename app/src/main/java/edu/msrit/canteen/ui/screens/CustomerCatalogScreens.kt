package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.FoodKind
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.customer.visibleSellers
import edu.msrit.canteen.ui.components.CatalogCategories
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogSellerCard
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitLoadingIndicator
import edu.msrit.canteen.ui.components.RitSearchField
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CustomerMenuScreen(
    sellerId: String?,
    initialCategoryId: String? = null,
    preparedOnly: Boolean = false,
    onBack: () -> Unit,
    onFood: (String) -> Unit,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    val seller = catalog.sellers.find { it.id == sellerId }
    val category = catalog.categories.find { it.id == initialCategoryId }
    var selectedCategory by rememberSaveable(sellerId, initialCategoryId) { mutableStateOf(initialCategoryId) }
    var query by rememberSaveable(sellerId) { mutableStateOf("") }
    val categories = if (seller != null) catalog.categories.filter { it.id in seller.categoryIds && (!preparedOnly || it.id != "packs") } else catalog.categories
    val visible = catalog.visibleItems(sellerId, selectedCategory, query).filter { !preparedOnly || it.kind == FoodKind.PREPARED }
    val title = when {
        preparedOnly -> "Order Food"
        seller != null -> "Seller Menu"
        category != null -> category.name
        else -> "Browse Food"
    }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_menu")) {
        CatalogHeader(title, onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText(seller?.name ?: "Across preview sellers", RitType.heading)
            RitText(when {
                preparedOnly -> "Prepared dishes · individual ordering is not live"
                seller != null -> "${if (seller.capabilities.futureIndividualPrepared) "Prepared food planned + bulk" else "Bulk orders only"} · illustrative prices"
                else -> "Category results across sellers · preview data"
            }, RitType.body, colors.muted)
            Spacer(Modifier.height(12.dp))
            RitStatusIndicator("Sample catalog · no orders can be placed")
            Spacer(Modifier.height(16.dp))
            RitSearchField(query, { query = it }, hint = "Search these preview items…")
            Spacer(Modifier.height(14.dp))
        }
        CatalogCategories(categories, selectedCategory) { selectedCategory = it }
        Spacer(Modifier.height(10.dp))
        if (visible.isEmpty()) {
            RitEmptyState("No items here yet", "Try another category or food name.")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                items(visible, key = { it.id }) { food ->
                    val owner = catalog.sellers.first { it.id == food.sellerId }
                    CatalogFoodCard(food, owner, { onFood(food.id) }, Modifier.padding(vertical = 5.dp),
                        isFavorite = favorites != null && customerId != null && food.id in favorites.itemIds(customerId),
                        onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleItem(customerId, food.id) }) else null)
                }
            }
        }
    }
}

@Composable
fun ExploreSellersScreen(
    onBack: () -> Unit,
    onSeller: (String) -> Unit,
    onBottom: (RitBottomItem) -> Unit,
    cartCount: Int = 0,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    isLoading: Boolean = false,
) {
    val colors = RitThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val sellers = catalog.visibleSellers(query, categoryId)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("explore_sellers")) {
        CatalogHeader("Explore Sellers", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("Campus sellers", RitType.heading)
            RitText("Browse menus from independent preview sellers", RitType.body, colors.muted)
            Spacer(Modifier.height(8.dp))
            RitStatusIndicator("Seller identities and hours unverified")
            Spacer(Modifier.height(14.dp))
            RitSearchField(query, { query = it }, hint = "Search preview sellers…")
            Spacer(Modifier.height(14.dp))
        }
        CatalogCategories(catalog.categories, categoryId) { categoryId = it }
        Spacer(Modifier.height(10.dp))
        when {
            isLoading -> RitLoadingIndicator(Modifier.padding(24.dp))
            sellers.isEmpty() -> RitEmptyState("No sellers found", "Try another name or category.")
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                items(sellers, key = { it.id }) { seller ->
                    val names = catalog.categories.filter { it.id in seller.categoryIds }.joinToString(" · ") { it.name }
                    Column(Modifier.padding(vertical = 6.dp)) { CatalogSellerCard(seller, names, { onSeller(seller.id) },
                        isFavorite = favorites != null && customerId != null && seller.id in favorites.sellerIds(customerId),
                        onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleSeller(customerId, seller.id) }) else null) }
                }
            }
        }
        RitBottomNavigation(RitBottomItem.Home, onBottom, cartCount = cartCount)
    }
}
