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
import androidx.compose.runtime.LaunchedEffect
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
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip


/** The canteen's menu with search and category filters. */
@Composable
fun SellerScopedMenuScreen(
    sellerId: String,
    onBack: (() -> Unit)?,
    onFood: (String) -> Unit,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    onOffers: (() -> Unit)? = null,
    onBottom: ((RitBottomItem) -> Unit)? = null,
    cartCount: Int = 0,
) {
    val colors = RitThemeTokens.colors
    val seller = catalog.sellers.find { it.id == sellerId }
    var query by rememberSaveable(sellerId) { mutableStateOf("") }
    var categoryId by rememberSaveable(sellerId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_scoped_menu")) {
        CatalogHeader(seller?.name ?: "Canteen Menu", onBack)
        if (seller == null) {
            RitEmptyState("Canteen not found", "This canteen isn't available.")
            return@Column
        }

        val visibleCategoryIds = catalog.visibleItems(sellerId = seller.id).mapTo(hashSetOf()) { it.categoryId }
        val categories = catalog.categories.filter { it.id in seller.categoryIds && it.id in visibleCategoryIds }
        val selectedCategoryId = categoryId?.takeIf { selected -> categories.any { it.id == selected } }
        LaunchedEffect(seller.id, selectedCategoryId, categories) {
            if (categoryId != selectedCategoryId) categoryId = null
        }
        val foods = catalog.visibleItems(sellerId = seller.id, categoryId = selectedCategoryId, query = query)
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("seller_menu_list"),
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
                    RitText(seller.description.replace(" for group requests.", "."), RitType.body, colors.muted, maxLines = 2)
                    Spacer(Modifier.height(9.dp))
                    RitText(seller.location ?: "On campus", RitType.small, colors.muted)
                    RitText(seller.hours?.display?.let { "Hours: $it" }
                        ?: "Hours not set", RitType.small, colors.muted)
                    Spacer(Modifier.height(16.dp))
                    Spacer(Modifier.height(13.dp))
                    RitSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Search ${seller.name} menu…",
                        onClearClick = { query = "" },
                    )
                    Spacer(Modifier.height(13.dp))
                    if (onOffers != null) {
                        RitSecondaryButton("Offers from ${seller.name}", onOffers,
                            Modifier.fillMaxWidth().testTag("seller_menu_offers"))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                CatalogCategories(categories, selectedCategoryId) { categoryId = it }
                Spacer(Modifier.height(10.dp))
                val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name
                RitText(if (selectedName == null) "${seller.name} menu" else "$selectedName at ${seller.name}",
                    RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        .testTag("seller_menu_results_heading"))
            }
            if (foods.isEmpty()) {
                item {
                    RitEmptyState("No menu items match", "Try another food name or category in this canteen's menu.")
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
