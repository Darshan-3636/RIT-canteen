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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.FoodKind
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.ItemEligibility
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.itemEligibility
import edu.msrit.canteen.customer.orderingAction
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.CatalogFavoriteButton
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogPill
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.SellerPhotoContent
import edu.msrit.canteen.ui.components.dietaryLabel
import edu.msrit.canteen.ui.components.eligibilityLabel
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun SellerProfileScreen(
    sellerId: String,
    onBack: () -> Unit,
    onMenu: (String) -> Unit,
    onFood: (String) -> Unit,
    onBulk: ((String) -> Unit)? = null,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val seller = catalog.sellers.find { it.id == sellerId }
    if (seller == null) { MissingCatalogEntry("Seller", onBack); return }
    val colors = RitThemeTokens.colors
    val preview = catalog.visibleItems(sellerId).take(3)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_profile")) {
        CatalogHeader("Seller Profile", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SellerPhotoContent(seller.photo, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(RitRadii.hero)), seller.localImageUri)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RitText(seller.name, RitType.heading, modifier = Modifier.weight(1f))
                        if (favorites != null && customerId != null) CatalogFavoriteButton(seller.name,
                            seller.id in favorites.sellerIds(customerId), { favorites.toggleSeller(customerId, seller.id) },
                            "favorite_seller_${seller.id}")
                    }
                    RitText(seller.description, RitType.body, colors.muted)
                    Spacer(Modifier.height(10.dp))
                    CatalogPill(if (seller.capabilities.futureIndividualPrepared) "Individual prepared food planned + Bulk" else "Bulk orders only")
                    Spacer(Modifier.height(12.dp))
                    RitText(seller.location ?: "Location not yet verified", RitType.small, colors.muted)
                    RitText(seller.hours?.display ?: "Operating hours not yet configured", RitType.small, colors.muted)
                    RitText("Live open/closed status unavailable", RitType.small, colors.muted)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.card)).background(colors.warmCard)
                        .clickable(role = Role.Button) { onMenu(seller.id) }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        RitText("View full menu", RitType.label)
                        RitText("→", RitType.label, colors.accent)
                    }
                    if (onBulk != null && seller.capabilities.bulk) {
                        Spacer(Modifier.height(9.dp))
                        RitPrimaryButton("Build a local bulk draft", { onBulk(seller.id) },
                            Modifier.fillMaxWidth().testTag("seller_start_bulk"))
                    }
                    Spacer(Modifier.height(20.dp))
                    RitText("Menu preview", RitType.title)
                    RitText("Illustrative items and prices", RitType.small, colors.muted)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(preview, key = { it.id }) { food ->
                CatalogFoodCard(food, seller, { onFood(food.id) }, Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                    isFavorite = favorites != null && customerId != null && food.id in favorites.itemIds(customerId),
                    onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleItem(customerId, food.id) }) else null)
            }
        }
    }
}

@Composable
fun FoodDetailsScreen(itemId: String, onBack: () -> Unit, onAddBulk: ((String) -> Unit)? = null,
                      onAddIndividual: ((String, (Boolean) -> Unit) -> Unit)? = null, onViewCart: (() -> Unit)? = null,
                      favorites: FavoritesRepository? = null,
                      customerId: String? = null, catalog: CustomerCatalogSource = PreviewCatalog) {
    val item = catalog.items.find { it.id == itemId && it in catalog.visibleItems() }
    if (item == null) { MissingCatalogEntry("Food", onBack); return }
    val seller = catalog.sellers.find { it.id == item.sellerId } ?: run { MissingCatalogEntry("Seller", onBack); return }
    val colors = RitThemeTokens.colors
    val eligibility = itemEligibility(seller, item)
    var expanded by rememberSaveable(itemId) { mutableStateOf(false) }
    var added by rememberSaveable(itemId) { mutableStateOf(false) }
    var adding by remember(itemId) { mutableStateOf(false) }
    var addFailed by remember(itemId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("food_details")) {
        CatalogHeader("Food Details", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
            item {
                FoodPhotoContent(item.photo, item.name + " illustrative preview image", Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(RitRadii.hero)), item.localImageUri)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RitText(item.name, RitType.heading, modifier = Modifier.weight(1f))
                    RitText(item.price.display, RitType.heading, colors.accent)
                    if (favorites != null && customerId != null) CatalogFavoriteButton(item.name,
                        item.id in favorites.itemIds(customerId), { favorites.toggleItem(customerId, item.id) },
                        "favorite_item_${item.id}")
                }
                RitText("By ${seller.name}", RitType.body, colors.muted)
                Spacer(Modifier.height(12.dp))
                CatalogPill(eligibilityLabel(seller, item))
                Spacer(Modifier.height(10.dp))
                RitText(dietaryLabel(item) + " · " + if (item.kind == FoodKind.PACKAGED) "Packaged" else "Prepared", RitType.small, colors.muted)
                Spacer(Modifier.height(18.dp))
                RitText("About this item", RitType.title)
                RitText(item.description, RitType.body, colors.muted, maxLines = if (expanded) Int.MAX_VALUE else 2)
                if (item.description.length > 70) {
                    RitText(if (expanded) "Show less" else "Read more", RitType.label, colors.accent,
                        Modifier.clickable(role = Role.Button) { expanded = !expanded }.heightIn(min = 48.dp).padding(vertical = 10.dp))
                }
                Spacer(Modifier.height(18.dp))
                RitText("Ordering modes", RitType.title)
                RitText(when (eligibility) {
                    ItemEligibility.UNAVAILABLE -> "This item is unavailable for a new request."
                    ItemEligibility.BULK_ONLY -> "Bulk requests only. Individual ordering is not offered for this item."
                    ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK -> "Add prepared food to an individual cart with no minimum, or plan a group bulk request. Checkout remains a demo preview."
                }, RitType.body, colors.muted)
                Spacer(Modifier.height(8.dp))
                RitText("Price is illustrative; confirm the live menu before ordering.", RitType.small, colors.muted)
            }
        }
        if (eligibility == ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK && onAddIndividual != null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (added) RitText("Added to individual cart", RitType.label, colors.accent,
                    Modifier.testTag("food_added_feedback"))
                if (addFailed) RitText("Could not update your cart. Please retry.", RitType.small, colors.accent,
                    Modifier.testTag("food_add_error"))
                RitPrimaryButton(if (adding) "Adding…" else "Add to Cart", {
                    adding = true
                    addFailed = false
                    onAddIndividual(item.id) { success ->
                        adding = false
                        added = success
                        addFailed = !success
                    }
                }, Modifier.fillMaxWidth().testTag("food_add_individual"), enabled = !adding)
                if (added && onViewCart != null) RitSecondaryButton("View Cart", onViewCart,
                    Modifier.fillMaxWidth().testTag("food_view_cart"))
                if (onAddBulk != null) RitSecondaryButton("Add to Bulk Cart", { onAddBulk(item.id) },
                    Modifier.fillMaxWidth().testTag("food_add_bulk"))
            }
        } else if (onAddBulk != null && eligibility != ItemEligibility.UNAVAILABLE) {
            RitPrimaryButton("Add to Bulk Cart", { onAddBulk(item.id) },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).testTag("food_add_bulk"))
        } else {
            val action = orderingAction(eligibility)
            RitPrimaryButton(action.label, {}, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), enabled = action.enabled)
        }
    }
}

@Composable
private fun MissingCatalogEntry(kind: String, onBack: () -> Unit) {
    Column {
        CatalogHeader(kind, onBack)
        RitEmptyState("$kind not found", "This preview entry is no longer available.")
    }
}
