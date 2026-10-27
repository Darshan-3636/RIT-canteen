package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import edu.msrit.canteen.customer.BulkDraftSession
import edu.msrit.canteen.customer.BulkPolicySource
import edu.msrit.canteen.customer.CartMode
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.IndividualCartSession
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.checkBulkDraft
import edu.msrit.canteen.customer.checkIndividualCart
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDateTime

@Composable
fun RitConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
                     confirmLabel: String = "Clear Cart & Switch") {
    val colors = RitThemeTokens.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(RitRadii.card))
            .padding(22.dp).testTag("cart_confirmation")) {
            RitText(title, RitType.title)
            Spacer(Modifier.height(9.dp))
            RitText(body, RitType.body, colors.muted)
            Spacer(Modifier.height(20.dp))
            RitSecondaryButton("Keep Current Cart", onDismiss, Modifier.fillMaxWidth().testTag("cart_keep"))
            Spacer(Modifier.height(8.dp))
            RitPrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth().testTag("cart_clear_confirm"))
        }
    }
}

@Composable
private fun QuantityStepper(name: String, quantity: Int, onChange: (Int) -> Unit, tag: String) {
    val colors = RitThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(colors.surface)
            .clickable(role = Role.Button) { onChange(quantity - 1) }
            .semantics { contentDescription = "Remove one $name" }.testTag("${tag}_minus"),
            contentAlignment = Alignment.Center) {
            RitText("−", RitType.title, colors.accent)
        }
        RitText("$quantity", RitType.label, modifier = Modifier.testTag("${tag}_quantity"))
        Box(Modifier.size(48.dp).clip(CircleShape).background(colors.surface)
            .clickable(role = Role.Button) { if (quantity < Int.MAX_VALUE) onChange(quantity + 1) }
            .semantics { contentDescription = "Add one $name" }.testTag("${tag}_plus"),
            contentAlignment = Alignment.Center) {
            RitText("+", RitType.title, colors.accent)
        }
    }
}

@Composable
fun CustomerCartScreen(individual: IndividualCartSession, bulk: BulkDraftSession,
                       policies: BulkPolicySource, initialMode: CartMode, onBack: () -> Unit,
                       onBrowse: () -> Unit, onBulkItems: () -> Unit, onChooseSeller: () -> Unit,
                       onIndividualReview: () -> Unit, onBulkPickup: () -> Unit,
                       catalog: CustomerCatalogSource = PreviewCatalog,
                       onBottom: ((RitBottomItem) -> Unit)? = null,
                       cartCount: Int = 0) {
    val colors = RitThemeTokens.colors
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var clearMode by remember { mutableStateOf<CartMode?>(null) }
    val individualCheck = checkIndividualCart(individual.draft, catalog)
    val bulkCheck = checkBulkDraft(bulk.draft, catalog, policies, LocalDateTime.now(RIT_TIME_ZONE))
    val bulkSeller = catalog.sellers.find { it.id == bulk.draft.sellerId }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_cart")) {
        CatalogHeader("Cart", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitChip("Individual · ${individual.draft.totalUnits}", mode == CartMode.INDIVIDUAL,
                { mode = CartMode.INDIVIDUAL }, Modifier.weight(1f).testTag("cart_tab_individual"))
            RitChip("Bulk · ${bulk.draft.lines.sumOf { it.quantity }}", mode == CartMode.BULK,
                { mode = CartMode.BULK }, Modifier.weight(1f).testTag("cart_tab_bulk"))
        }
        LazyColumn(Modifier.weight(1f).testTag("cart_items"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            if (mode == CartMode.INDIVIDUAL) {
                item {
                    RitText("Main Canteen", RitType.title)
                    RitText("Prepared food only · demo selection, not a placed order", RitType.small, colors.muted)
                    Spacer(Modifier.height(12.dp))
                }
                if (individual.draft.lines.isEmpty()) item {
                    RitEmptyState("Your individual cart is empty", "Add a dosa, coffee or another prepared item from Main Canteen.")
                    RitSecondaryButton("Browse prepared food", onBrowse, Modifier.fillMaxWidth())
                }
                items(individual.draft.lines, key = { it.itemId }) { line ->
                    val item = catalog.items.find { it.id == line.itemId }
                    if (item == null || line.itemId in individualCheck.unavailableItemIds) {
                        RitText("An item is no longer available", RitType.body, colors.accent)
                    } else {
                        CartLine(item.name, item.price.display, item.photo, line.quantity,
                            item.price.rupees.toLong() * line.quantity * 100L,
                            { individual.setQuantity(item, it) }, "individual_${item.id}", item.localImageUri)
                    }
                }
                if (individual.draft.lines.isNotEmpty()) item {
                    Spacer(Modifier.height(14.dp))
                    RitText("Subtotal  ${displayPaise(individualCheck.subtotalPaise)}", RitType.title,
                        modifier = Modifier.testTag("individual_subtotal"))
                    RitText("Illustrative price. Real payment and kitchen submission are unavailable; local demo review is available.", RitType.small, colors.muted)
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Clear individual cart", { clearMode = CartMode.INDIVIDUAL }, Modifier.fillMaxWidth())
                }
            } else {
                item {
                    RitText(bulkSeller?.name ?: "Choose a seller", RitType.title)
                    RitText("One seller per bulk request · demo estimate only", RitType.small, colors.muted)
                    Spacer(Modifier.height(12.dp))
                }
                if (bulk.draft.lines.isEmpty()) item {
                    RitEmptyState("Your bulk cart is empty", "Choose a seller, then add eligible items for a group request.")
                }
                items(bulk.draft.lines, key = { it.itemId }) { line ->
                    val item = catalog.items.find { it.id == line.itemId && it.sellerId == bulk.draft.sellerId }
                    if (item == null) RitText("An item is no longer available", RitType.body, colors.accent)
                    else CartLine(item.name, item.price.display, item.photo, line.quantity,
                        item.price.rupees.toLong() * line.quantity * 100L,
                        { bulk.setQuantity(item, it) }, "bulk_${item.id}", item.localImageUri)
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    RitText("${bulkCheck.totalUnits} units · minimum ${bulkCheck.minimum}", RitType.title,
                        modifier = Modifier.testTag("cart_bulk_minimum"))
                    RitText("Estimated ${displayPaise(bulkCheck.provisionalPaise)} · seller may revise the quote", RitType.body, colors.muted)
                    Spacer(Modifier.height(12.dp))
                    RitSecondaryButton(if (bulkSeller == null) "Choose a seller" else "Change seller", onChooseSeller, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    RitSecondaryButton("Edit bulk items", onBulkItems, Modifier.fillMaxWidth())
                    if (bulk.draft.lines.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        RitSecondaryButton("Clear bulk cart", { clearMode = CartMode.BULK }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (mode == CartMode.INDIVIDUAL) {
            RitPrimaryButton("Review Order", onIndividualReview, Modifier.fillMaxWidth().padding(20.dp)
                .testTag("cart_review_individual"), enabled = individualCheck.readyForReview)
        } else {
            RitPrimaryButton("Continue to pickup", onBulkPickup, Modifier.fillMaxWidth().padding(20.dp)
                .testTag("cart_review_bulk"), enabled = bulkCheck.totalUnits >= bulkCheck.minimum &&
                bulkCheck.issues.none { it in setOf(edu.msrit.canteen.customer.BulkDraftIssue.NO_ITEMS,
                    edu.msrit.canteen.customer.BulkDraftIssue.INVALID_QUANTITY,
                    edu.msrit.canteen.customer.BulkDraftIssue.ITEM_UNAVAILABLE,
                    edu.msrit.canteen.customer.BulkDraftIssue.CROSS_SELLER_ITEM,
                    edu.msrit.canteen.customer.BulkDraftIssue.SELLER_NOT_BULK) })
        }
        if (onBottom != null) RitBottomNavigation(RitBottomItem.Cart, onBottom, cartCount = cartCount)
    }
    clearMode?.let { target -> RitConfirmDialog("Clear ${if (target == CartMode.BULK) "bulk" else "individual"} cart?",
        "Only this cart will be cleared. Your other cart stays as it is.", { clearMode = null }, {
            if (target == CartMode.BULK) bulk.clear() else individual.clear()
            clearMode = null
        }, "Clear this cart") }
}

@Composable
private fun CartLine(name: String, price: String, photo: edu.msrit.canteen.customer.FoodPhoto?,
                     quantity: Int, totalPaise: Long, onQuantity: (Int) -> Unit, tag: String,
                     localImageUri: String?) {
    val colors = RitThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).border(1.dp, colors.outline,
        RoundedCornerShape(RitRadii.card)).background(colors.card, RoundedCornerShape(RitRadii.card))
        .padding(12.dp).testTag("cart_line_$tag"), verticalAlignment = Alignment.CenterVertically) {
        FoodPhotoContent(photo, "$name illustrative food photo",
            Modifier.width(84.dp).height(84.dp).clip(RoundedCornerShape(RitRadii.small)), localImageUri)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RitText(name, RitType.label)
            RitText("$price each", RitType.small, colors.muted)
            RitText("Line total  ${displayPaise(totalPaise)}", RitType.label)
            QuantityStepper(name, quantity, onQuantity, tag)
        }
    }
}

@Composable
fun IndividualReviewScreen(individual: IndividualCartSession, onBack: () -> Unit,
                           onSaveDemo: () -> Unit, catalog: CustomerCatalogSource = PreviewCatalog) {
    val colors = RitThemeTokens.colors
    val check = checkIndividualCart(individual.draft, catalog)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("individual_review")) {
        CatalogHeader("Review Order", onBack)
        Column(Modifier.weight(1f).padding(20.dp)) {
            RitText("Main Canteen", RitType.heading)
            RitText("Individual prepared-food selection", RitType.body, colors.muted)
            Spacer(Modifier.height(20.dp))
            check.lines.forEach { (item, quantity) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    RitText("${quantity} × ${item.name}", RitType.body, modifier = Modifier.weight(1f))
                    RitText(displayPaise(item.price.rupees.toLong() * quantity * 100L), RitType.body)
                }
            }
            Spacer(Modifier.height(14.dp))
            RitText("Subtotal  ${displayPaise(check.subtotalPaise)}", RitType.title)
            Spacer(Modifier.height(15.dp))
            BulkInfoCard("Demo only", "This creates a local simulated seller request. No kitchen is notified, no food is reserved and no money is charged.")
        }
        RitPrimaryButton("Submit demo request", onSaveDemo, Modifier.fillMaxWidth().padding(20.dp)
            .testTag("individual_save_demo"), enabled = check.readyForReview)
    }
}
