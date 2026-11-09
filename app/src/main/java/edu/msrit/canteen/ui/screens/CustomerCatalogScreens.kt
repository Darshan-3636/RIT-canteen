package edu.msrit.canteen.ui.screens

import edu.msrit.canteen.BuildConfig
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
import edu.msrit.canteen.ui.components.RitDropdownField
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
            RitText(seller?.name ?: if (BuildConfig.RIT_CLOUD_DEMO) "Across campus sellers" else "Across preview sellers", RitType.heading)
            RitText(when {
                preparedOnly -> "Meals from the main canteen"
                seller != null -> "Browse this canteen's menu"
                else -> "Browse campus canteen menus"
            }, RitType.body, colors.muted)
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(4.dp))
            RitSearchField(query, { query = it }, hint = "Search these items…")
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
    var selectedCanteen by rememberSaveable { mutableStateOf("") }
    val sellers = catalog.visibleSellers(query, categoryId)
    val selectedSeller = sellers.firstOrNull { it.id == selectedCanteen }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("explore_sellers")) {
        CatalogHeader("Explore Sellers", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("Browse campus canteen menus.", RitType.body, colors.muted)
            Spacer(Modifier.height(8.dp))
            RitDropdownField(
                value = selectedSeller?.name.orEmpty(),
                label = "Choose Canteen",
                placeholder = SELECT_CANTEEN,
                options = sellers.map { it.name },
                onValueChange = { name -> selectedCanteen = sellers.firstOrNull { it.name == name }?.id.orEmpty() },
                modifier = Modifier.fillMaxWidth(),
                inputTag = "explore_canteen_selector",
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))
            RitSearchField(query, { query = it }, hint = "Search sellers…")
            Spacer(Modifier.height(14.dp))
        }
        CatalogCategories(catalog.categories, categoryId) { categoryId = it }
        Spacer(Modifier.height(10.dp))
        when {
            isLoading -> RitLoadingIndicator(Modifier.padding(24.dp))
            selectedSeller == null -> RitEmptyState("Choose a canteen", "Select a canteen to see its details and menu.")
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                items(listOf(selectedSeller), key = { it.id }) { seller ->
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
