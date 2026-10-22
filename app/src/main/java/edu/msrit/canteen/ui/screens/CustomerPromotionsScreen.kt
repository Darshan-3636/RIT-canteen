package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.DevelopmentPromotions
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.Promotion
import edu.msrit.canteen.customer.PromotionSource
import edu.msrit.canteen.customer.PromotionKind
import edu.msrit.canteen.customer.PromotionStatus
import edu.msrit.canteen.customer.presentable
import edu.msrit.canteen.customer.statusAt
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val offerDate = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

internal fun offerBenefit(promotion: Promotion): String = when (promotion.kind) {
    PromotionKind.PERCENT_DISCOUNT -> "${promotion.discountValue}% OFF"
    PromotionKind.FIXED_DISCOUNT -> "₹${promotion.discountValue} OFF"
    PromotionKind.TODAYS_SPECIAL -> "TODAY'S PICK"
    PromotionKind.NEW_ARRIVAL -> "JUST ADDED"
    PromotionKind.FEATURED -> "FEATURED"
    PromotionKind.BANNER -> "SELLER PICK"
}

private fun Promotion.hasVisibleTarget(catalog: CustomerCatalogSource): Boolean {
    if (itemIds.isEmpty() && categoryIds.isEmpty()) return true
    val seller = catalog.sellers.find { it.id == sellerId } ?: return false
    return itemIds.any { id -> catalog.visibleItems(sellerId).any { it.id == id } } ||
        categoryIds.any { it in seller.categoryIds }
}

private fun Promotion.eligibilityLabel(catalog: CustomerCatalogSource): String {
    val sellerItems = catalog.visibleItems(sellerId)
    val names = sellerItems.filter { it.id in itemIds }.map { it.name }
    if (names.isNotEmpty()) return "For ${names.take(2).joinToString(" · ")}"
    val categories = catalog.categories.filter { it.id in categoryIds }.map { it.name }
    if (categories.isNotEmpty()) return "For ${categories.take(2).joinToString(" · ")}"
    return "Seller feature · preview only"
}

@Composable
fun CustomerPromotionsScreen(onBack: () -> Unit, onPromotion: (String) -> Unit,
                             source: PromotionSource = DevelopmentPromotions,
                             catalog: CustomerCatalogSource = PreviewCatalog,
                             now: Instant = Instant.now()) {
    val colors = RitThemeTokens.colors
    val promotions = source.presentable(now = now).filter { it.hasVisibleTarget(catalog) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("promotions_screen")) {
        CatalogHeader("Offers", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("Good things on the menu", RitType.heading)
            RitText("Discover seller specials and featured food.", RitType.body, colors.muted)
            Spacer(Modifier.height(10.dp))
            RitStatusIndicator("Demo offers · not redeemable")
            Spacer(Modifier.height(14.dp))
        }
        if (promotions.isEmpty()) RitEmptyState("No demo offers", "There are no published preview promotions.")
        else LazyColumn(modifier = Modifier.testTag("offers_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            items(promotions, key = { it.id }) { promotion ->
                val seller = catalog.sellers.find { it.id == promotion.sellerId }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    PromotionCard(promotion, seller?.name ?: "Seller unavailable", now, { onPromotion(promotion.id) }, catalog,
                        Modifier.testTag("promotion_${promotion.id}"))
                }
            }
        }
    }
}

@Composable
internal fun PromotionCard(promotion: Promotion, sellerName: String, now: Instant, onClick: () -> Unit,
                           catalog: CustomerCatalogSource = PreviewCatalog, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    val status = promotion.statusAt(now)
    val item = catalog.items.find { it.sellerId == promotion.sellerId && it.id in promotion.itemIds } ?: catalog.items.find {
        it.sellerId == promotion.sellerId && it.categoryId in promotion.categoryIds }
    Column(modifier.fillMaxWidth().border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card))
        .clickable(role = Role.Button, onClick = onClick).padding(12.dp)) {
        FoodPhotoContent(item?.photo, "${item?.name ?: promotion.title} illustrative offer image",
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(RitRadii.small)), item?.localImageUri)
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                RitText(offerBenefit(promotion), RitType.heading, colors.accent,
                    modifier = Modifier.testTag("offer_benefit_${promotion.id}"))
                RitText(promotion.title.replace(" · demo", ""), RitType.title)
                RitText(sellerName, RitType.small, colors.muted)
                Spacer(Modifier.height(8.dp))
                RitText(promotion.eligibilityLabel(catalog), RitType.body, colors.foreground,
                    modifier = Modifier.testTag("offer_eligibility_${promotion.id}"))
                Spacer(Modifier.height(6.dp))
                RitText(when (status) {
                    PromotionStatus.ACTIVE -> "Ends ${offerDate.format(promotion.endsAt)}"
                    PromotionStatus.UPCOMING -> "Starts ${offerDate.format(promotion.startsAt)}"
                    PromotionStatus.EXPIRED -> "Ended ${offerDate.format(promotion.endsAt)}"
                }, RitType.small, colors.muted)
        }
        Spacer(Modifier.height(12.dp))
        RitSecondaryButton("View Offer", onClick, Modifier.fillMaxWidth().testTag("offer_action_${promotion.id}"))
    }
}

@Composable
fun CustomerPromotionDetailScreen(id: String, onBack: () -> Unit, onSeller: (String) -> Unit,
                                  onFood: (String) -> Unit, source: PromotionSource = DevelopmentPromotions,
                                  catalog: CustomerCatalogSource = PreviewCatalog, now: Instant = Instant.now()) {
    val colors = RitThemeTokens.colors
    val promotion = source.presentable(now = now).find { it.id == id && it.hasVisibleTarget(catalog) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("promotion_detail")) {
        CatalogHeader("Offer details", onBack)
        if (promotion == null) {
            RitEmptyState("Offer not found", "This demo placement is unavailable.")
        } else {
            val seller = catalog.sellers.find { it.id == promotion.sellerId }
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                item {
                    RitStatusIndicator("Demo only · no checkout discount")
                    Spacer(Modifier.height(15.dp))
                    RitText(promotion.title, RitType.heading)
                    RitText(promotion.description, RitType.body, colors.muted)
                    Spacer(Modifier.height(18.dp))
                    RitMessageCard("Validity", when (promotion.statusAt(now)) {
                        PromotionStatus.ACTIVE -> "Within the sample schedule, but not a verified campus offer."
                        PromotionStatus.UPCOMING -> "Sample placement has not started."
                        PromotionStatus.EXPIRED -> "Sample placement has expired."
                    } + " ${offerDate.format(promotion.startsAt)} – ${offerDate.format(promotion.endsAt)}")
                    Spacer(Modifier.height(18.dp))
                    RitText("Seller", RitType.title)
                    RitText(seller?.name ?: "Seller unavailable", RitType.body, colors.muted)
                    if (seller != null) {
                        Spacer(Modifier.height(10.dp))
                        RitSecondaryButton("View seller profile", { onSeller(seller.id) }, Modifier.fillMaxWidth().testTag("promotion_open_seller"))
                    }
                    Spacer(Modifier.height(18.dp))
                    RitText("Related preview food", RitType.title)
                }
                val related = catalog.visibleItems(sellerId = promotion.sellerId)
                    .filter { it.id in promotion.itemIds || it.categoryId in promotion.categoryIds }
                if (related.isEmpty()) item { RitText("No currently visible related items.", RitType.body, colors.muted) }
                else items(related, key = { it.id }) { food ->
                    RitSecondaryButton(food.name, { onFood(food.id) },
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("promotion_item_${food.id}"))
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    RitText("No coupon redemption, effective price or personalization is calculated in this preview.",
                        RitType.small, colors.muted)
                }
            }
        }
    }
}
