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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogSellerCard
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CustomerFavoritesScreen(
    customerId: String,
    favorites: FavoritesRepository,
    onBack: () -> Unit,
    onFood: (String) -> Unit,
    onSeller: (String) -> Unit,
    onBottom: (RitBottomItem) -> Unit,
    cartCount: Int = 0,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    val itemIds = favorites.itemIds(customerId)
    val sellerIds = favorites.sellerIds(customerId)
    val foods = catalog.visibleItems().filter { it.id in itemIds }
    val sellers = catalog.sellers.filter { it.id in sellerIds }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("favorites_screen")) {
        CatalogHeader("Favourites", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("Saved for later", RitType.heading)
            RitText("Only on this device in the debug preview.", RitType.body, colors.muted)
            Spacer(Modifier.height(10.dp))
            RitStatusIndicator("Demo Customer favourites · not account-synced")
        }
        if (foods.isEmpty() && sellers.isEmpty()) {
            Column(Modifier.weight(1f).testTag("favorites_empty")) {
                RitEmptyState("No favourites yet", "Save a food or seller while browsing the preview catalog.")
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (foods.isNotEmpty()) {
                    item { RitText("Food", RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                    items(foods, key = { "food:${it.id}" }) { food ->
                        val seller = catalog.sellers.first { it.id == food.sellerId }
                        CatalogFoodCard(food, seller, { onFood(food.id) },
                            Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("favorite_saved_item_${food.id}"),
                            isFavorite = true, onFavoriteClick = { favorites.toggleItem(customerId, food.id) })
                    }
                }
                if (sellers.isNotEmpty()) {
                    item { RitText("Sellers", RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                    items(sellers, key = { "seller:${it.id}" }) { seller ->
                        val categories = catalog.categories.filter { it.id in seller.categoryIds }.joinToString(" · ") { it.name }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("favorite_saved_seller_${seller.id}")) {
                            CatalogSellerCard(seller, categories, { onSeller(seller.id) },
                                isFavorite = true, onFavoriteClick = { favorites.toggleSeller(customerId, seller.id) })
                        }
                    }
                }
                val staleItems = itemIds - foods.map { it.id }.toSet()
                val staleSellers = sellerIds - sellers.map { it.id }.toSet()
                if (staleItems.isNotEmpty() || staleSellers.isNotEmpty()) item {
                    RitText("Some saved entries are no longer available in this preview catalog.", RitType.small,
                        colors.muted, Modifier.padding(20.dp).fillMaxWidth())
                }
            }
        }
        RitBottomNavigation(RitBottomItem.Favourites, onBottom, cartCount = cartCount)
    }
}
