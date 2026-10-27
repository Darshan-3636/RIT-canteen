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
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.customer.visibleSellers
import edu.msrit.canteen.ui.components.CatalogCategories
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogPill
import edu.msrit.canteen.ui.components.CatalogSellerCard
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitLoadingIndicator
import edu.msrit.canteen.ui.components.RitSearchField
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.SellerPhotoContent
import edu.msrit.canteen.ui.components.sellerOrderingBadge
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/** Seller-first customer entry. The caller owns routing and supplies the seller-selection callback. */
@Composable
fun ChooseSellerScreen(
    onSellerChosen: (String) -> Unit,
    onBack: (() -> Unit)?,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    isLoading: Boolean = false,
    onBottom: ((RitBottomItem) -> Unit)? = null,
    cartCount: Int = 0,
) {
    val colors = RitThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    val sellers = catalog.visibleSellers(query, null)

    Column(Modifier.fillMaxSize().background(colors.background).testTag("choose_seller")) {
        CatalogHeader("Choose Seller", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("What are you craving?", RitType.heading)
            RitText("Pick a campus seller first. Their menu and search stay together.", RitType.body, colors.muted)
            Spacer(Modifier.height(12.dp))
            RitStatusIndicator("Sample sellers · hours and live availability unverified")
            Spacer(Modifier.height(14.dp))
            RitSearchField(query, { query = it }, hint = "Find a seller…", onClearClick = { query = "" })
            Spacer(Modifier.height(14.dp))
        }
        when {
            isLoading -> RitLoadingIndicator(Modifier.padding(24.dp))
            sellers.isEmpty() -> RitEmptyState("No sellers match", "Try another seller name.")
            else -> LazyColumn(
                modifier = Modifier.weight(1f).testTag("choose_seller_results"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                items(sellers, key = { it.id }) { seller ->
                    val categoryNames = catalog.categories.filter { it.id in seller.categoryIds }
                        .joinToString(" · ") { it.name }
                    CatalogSellerCard(
                        seller = seller,
                        categoryNames = categoryNames,
                        onClick = { onSellerChosen(seller.id) },
                        isFavorite = favorites != null && customerId != null && seller.id in favorites.sellerIds(customerId),
                        onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleSeller(customerId, seller.id) }) else null,
                        modifier = Modifier.padding(vertical = 7.dp).testTag("choose_seller_card_${seller.id}"),
                    )
                }
            }
        }
        if (onBottom != null) RitBottomNavigation(RitBottomItem.Home, onBottom, cartCount = cartCount)
    }
}

/** Menu and search for one selected seller. Results are never mixed with another seller's catalog. */
@Composable
fun SellerScopedMenuScreen(
    sellerId: String,
    onBack: () -> Unit,
    onFood: (String) -> Unit,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    onChooseSeller: (() -> Unit)? = null,
    onOffers: (() -> Unit)? = null,
    onBottom: ((RitBottomItem) -> Unit)? = null,
    cartCount: Int = 0,
) {
    val colors = RitThemeTokens.colors
    val seller = catalog.sellers.find { it.id == sellerId }
    var query by rememberSaveable(sellerId) { mutableStateOf("") }
    var categoryId by rememberSaveable(sellerId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_scoped_menu")) {
        CatalogHeader(seller?.name ?: "Seller Menu", onBack)
        if (seller == null) {
            RitEmptyState("Seller not found", "This seller is not in the current preview catalog.")
            return@Column
        }

        val categories = catalog.categories.filter { it.id in seller.categoryIds }
        val foods = catalog.visibleItems(sellerId = seller.id, categoryId = categoryId, query = query)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SellerPhotoContent(
                        seller.photo,
                        Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(RitRadii.hero)),
                        seller.localImageUri,
                    )
                    Spacer(Modifier.height(9.dp))
                    RitText(seller.name, RitType.heading)
                    RitText(seller.description, RitType.body, colors.muted, maxLines = 2)
                    Spacer(Modifier.height(9.dp))
                    CatalogPill(sellerOrderingBadge(seller))
                    Spacer(Modifier.height(7.dp))
                    RitText(seller.location ?: "Location not verified", RitType.small, colors.muted)
                    RitText(seller.hours?.display?.let { "Preview hours: $it · live status unverified" }
                        ?: "Hours and live availability unverified", RitType.small, colors.muted)
                    Spacer(Modifier.height(16.dp))
                    RitStatusIndicator("Illustrative menu · prices and availability are preview data")
                    Spacer(Modifier.height(13.dp))
                    RitSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Search ${seller.name} menu…",
                        onClearClick = { query = "" },
                    )
                    Spacer(Modifier.height(13.dp))
                    if (onChooseSeller != null) {
                        RitSecondaryButton("Choose another seller", onChooseSeller,
                            Modifier.fillMaxWidth().testTag("seller_menu_change_seller"))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (onOffers != null) {
                        RitSecondaryButton("Offers from ${seller.name}", onOffers,
                            Modifier.fillMaxWidth().testTag("seller_menu_offers"))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                CatalogCategories(categories, categoryId) { categoryId = it }
                Spacer(Modifier.height(10.dp))
                val selectedName = categories.firstOrNull { it.id == categoryId }?.name
                RitText(if (selectedName == null) "${seller.name} menu" else "$selectedName at ${seller.name}",
                    RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        .testTag("seller_menu_results_heading"))
            }
            if (foods.isEmpty()) {
                item {
                    RitEmptyState("No menu items match", "Try another food name or category in this seller's menu.")
                }
            } else {
                items(foods, key = { it.id }) { food ->
                    CatalogFoodCard(
                        item = food,
                        seller = seller,
                        onClick = { onFood(food.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("seller_menu_item_${food.id}"),
                        isFavorite = favorites != null && customerId != null && food.id in favorites.itemIds(customerId),
                        onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleItem(customerId, food.id) }) else null,
                    )
                }
            }
        }
        if (onBottom != null) RitBottomNavigation(RitBottomItem.Home, onBottom, cartCount = cartCount)
    }
}
