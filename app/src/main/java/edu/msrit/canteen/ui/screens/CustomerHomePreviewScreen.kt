package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.onboarding.CopyContext
import edu.msrit.canteen.onboarding.CopySurface
import edu.msrit.canteen.onboarding.CopyTrigger
import edu.msrit.canteen.onboarding.ThemeId
import edu.msrit.canteen.onboarding.ThemeMicrocopy
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitQuickEntry
import edu.msrit.canteen.ui.components.RitSearchEntry
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CustomerHomePreviewScreen(
    onOrderFood: () -> Unit,
    onBulk: () -> Unit,
    onSellers: () -> Unit,
    onFood: (String) -> Unit,
    onSearch: (String?) -> Unit,
    onOffers: () -> Unit,
    onCart: () -> Unit,
    onBottom: (RitBottomItem) -> Unit,
    cartCount: Int = 0,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val availableCategories = catalog.categories.filter { category -> catalog.sellers.any { category.id in it.categoryIds } }
    LaunchedEffect(availableCategories, selectedCategoryId) {
        if (selectedCategoryId != null && availableCategories.none { it.id == selectedCategoryId }) selectedCategoryId = null
    }
    val visibleFood = catalog.visibleItems(categoryId = selectedCategoryId)
    val selectedName = catalog.categories.firstOrNull { it.id == selectedCategoryId }?.name
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_home")) {
        LazyColumn(Modifier.weight(1f).testTag("home_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    RitBrandHeader(onTrailingClick = { onBottom(RitBottomItem.Profile) })
                    Spacer(Modifier.height(12.dp))
                    RitStatusIndicator("Catalog preview · illustrative prices")
                    Spacer(Modifier.height(20.dp))
                    RitText("Good food, close by.", RitType.heading)
                    RitText("Explore meals and sellers at RIT.", RitType.body, colors.muted)
                    Spacer(Modifier.height(16.dp))
                    RitSearchEntry({ onSearch(selectedCategoryId) }, Modifier.fillMaxWidth())
                    RitText(if (cartCount > 0) "View Cart · $cartCount items →" else "Open Cart →",
                        RitType.label, colors.accent, Modifier.fillMaxWidth()
                            .clickable(role = Role.Button, onClick = onCart).testTag("home_cart_action")
                            .padding(vertical = 11.dp))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitQuickEntry("Order Food", "Main canteen", RitIconKind.Food, Modifier.weight(1f), onOrderFood)
                        RitQuickEntry("Bulk Orders", "Groups", RitIconKind.Bulk, Modifier.weight(1f), onBulk)
                        RitQuickEntry("Explore Sellers", "Menus", RitIconKind.Store, Modifier.weight(1f), onSellers)
                    }
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.hero)).background(colors.warmCard)
                        .clickable(role = Role.Button, onClick = onSellers)) {
                        val hero = catalog.items.firstOrNull { it.id == "masala-dosa" }
                        FoodPhotoContent(hero?.photo, "Illustrative masala dosa photograph",
                            Modifier.fillMaxWidth().height(158.dp), hero?.localImageUri)
                        Column(Modifier.padding(16.dp)) {
                            RitText("Discover the campus menu", RitType.title)
                            RitText("Browse the main canteen and group-order sellers", RitType.small, colors.muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    RitText("See demo seller offers →", RitType.label, colors.accent,
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOffers)
                            .testTag("home_offers_entry").padding(vertical = 10.dp))
                    Spacer(Modifier.height(22.dp))
                    RitText("Browse categories", RitType.title)
                    RitText("Choose a category to filter food below", RitType.small, colors.muted)
                    Spacer(Modifier.height(10.dp))
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all") {
                        edu.msrit.canteen.ui.components.RitChip("All", selectedCategoryId == null,
                            { selectedCategoryId = null }, Modifier.testTag("home_category_all"))
                    }
                    items(availableCategories, key = { it.id }) { category ->
                        edu.msrit.canteen.ui.components.RitChip(category.name, selectedCategoryId == category.id,
                            { selectedCategoryId = category.id }, Modifier.testTag("home_category_${category.id}"))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    RitText(if (selectedName == null) "All preview food" else "$selectedName preview food", RitType.title,
                        modifier = Modifier.testTag("home_results_heading"))
                    RitText("Sample items and prices · not a live menu", RitType.small, colors.muted)
                }
            }
            if (visibleFood.isEmpty()) {
                item { RitEmptyState("No matches in ${selectedName ?: "all food"}", ThemeMicrocopy.select(ThemeId.Minecraft, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState, 0)?.text ?: "Try another food name or category.") }
            } else {
                items(visibleFood, key = { it.id }) { food ->
                    CatalogFoodCard(food, catalog.sellers.first { it.id == food.sellerId }, { onFood(food.id) },
                        Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("home_item_${food.id}"),
                        isFavorite = favorites != null && customerId != null && food.id in favorites.itemIds(customerId),
                        onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleItem(customerId, food.id) }) else null)
                }
            }
        }
        RitBottomNavigation(RitBottomItem.Home, onBottom, cartCount = cartCount)
    }
}
