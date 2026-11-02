package edu.msrit.canteen.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.ItemAvailability
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogCategories
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.components.SellerPhotoContent
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Standalone local presentation flow. The existing online navigation and PhonePe SDK remain unchanged. */
@Composable
fun PresentationApp() {
    val context = LocalContext.current
    val store = remember(context) { PresentationStore(context) }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(store.activeAccount()) }
    var revision by remember { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("sellers") }
    var sellerId by rememberSaveable { mutableStateOf(PreviewCatalog.MAIN_ID) }
    var itemId by rememberSaveable { mutableStateOf("") }
    var orderId by rememberSaveable { mutableStateOf("") }
    var orderReturn by rememberSaveable { mutableStateOf("orders") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checkoutKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    fun back() {
        page = when (page) {
            "food" -> "menu"
            "cart" -> "menu"
            "order" -> orderReturn
            "menu" -> "sellers"
            "orders" -> "sellers"
            "history" -> "queue"
            "pending" -> "queue"
            else -> if (account?.role == LocalRole.SELLER) "queue" else "sellers"
        }
        message = null
    }

    fun <T> action(block: () -> T, onSuccess: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            busy = false
            result.onSuccess(onSuccess).onFailure { message = it.message ?: "Please try again." }
            if (result.isSuccess) revision++
        }
    }

    BackHandler(account != null && page !in listOf("sellers", "queue")) { back() }
    val current = account
    if (current == null) {
        PresentationAuth(store, busy, message, onAuthenticated = { account = it; page = if (it.role == LocalRole.SELLER) "queue" else "sellers" },
            onError = { message = it }, onBusy = { busy = it })
        return
    }
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        when (current.role) {
            LocalRole.CUSTOMER -> when (page) {
                "menu" -> {
                    val seller = PreviewCatalog.sellers.firstOrNull { it.id == sellerId }
                    CatalogHeader(seller?.name ?: "Menu", ::back)
                    var category by rememberSaveable(sellerId) { mutableStateOf<String?>(null) }
                    val all = PreviewCatalog.items.filter { it.sellerId == sellerId && it.published && it.availability == ItemAvailability.AVAILABLE }
                    val categories = PreviewCatalog.categories.filter { cat -> all.any { it.categoryId == cat.id } }
                    Column(Modifier.fillMaxSize()) {
                        CatalogCategories(categories, category) { category = it }
                        LazyColumn(Modifier.weight(1f).testTag("presentation_menu")) {
                            items(all.filter { category == null || it.categoryId == category }, key = { it.id }) { item ->
                                PresentationCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clickable {
                                    itemId = item.id; page = "food"
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FoodPhotoContent(item.photo, item.name, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            RitText(item.name, RitType.title)
                                            RitText(item.price.display, RitType.label, colors.accent)
                                        }
                                    }
                                }
                            }
                        }
                        RitPrimaryButton("View cart", { page = "cart" }, Modifier.fillMaxWidth().padding(20.dp).testTag("presentation_view_cart"))
                    }
                }
                "food" -> {
                    val item = PreviewCatalog.items.firstOrNull { it.id == itemId }
                    CatalogHeader(item?.name ?: "Food", ::back)
                    if (item != null) {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            FoodPhotoContent(item.photo, item.name, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)))
                            Spacer(Modifier.height(18.dp))
                            RitText(item.name, RitType.heading)
                            RitText(item.description, RitType.body, colors.muted)
                            Spacer(Modifier.height(12.dp))
                            RitText(item.price.display, RitType.title, colors.accent)
                            Spacer(Modifier.weight(1f))
                            RitSecondaryButton("Add to cart", { action({
                                val old = store.cart(current.id).firstOrNull { it.item.id == item.id }?.quantity ?: 0
                                store.setQuantity(current.id, item.id, old + 1)
                            }, { page = "menu" }) }, Modifier.fillMaxWidth().testTag("presentation_add_cart"), enabled = !busy)
                            Spacer(Modifier.height(8.dp))
                            RitPrimaryButton("Buy Now", { action({
                                val old = store.cart(current.id).firstOrNull { it.item.id == item.id }?.quantity ?: 0
                                store.setQuantity(current.id, item.id, old + 1)
                            }, { page = "cart" }) }, Modifier.fillMaxWidth().testTag("presentation_buy_now"), enabled = !busy)
                        }
                    }
                }
                "cart" -> {
                    CatalogHeader("Your cart", ::back)
                    val lines = remember(revision, current.id) { store.cart(current.id) }
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        if (lines.isEmpty()) RitText("Your cart is empty.", RitType.body, colors.muted)
                        LazyColumn(Modifier.weight(1f)) {
                            items(lines, key = { it.item.id }) { line ->
                                PresentationCard(Modifier.padding(bottom = 10.dp)) {
                                    RitText(line.item.name, RitType.title)
                                    RitText("${line.item.price.display} × ${line.quantity}", RitType.body, colors.muted)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        RitSecondaryButton("−", { action(block = { store.setQuantity(current.id, line.item.id, line.quantity - 1) }) }, Modifier.testTag("cart_decrease_${line.item.id}"), enabled = !busy)
                                        RitSecondaryButton("+", { action(block = { store.setQuantity(current.id, line.item.id, line.quantity + 1) }) }, Modifier.testTag("cart_increase_${line.item.id}"), enabled = !busy)
                                    }
                                }
                            }
                        }
                        RitText("Total  ${money(lines.sumOf { it.item.price.rupees * it.quantity * 100L })}", RitType.title)
                        Spacer(Modifier.height(12.dp))
                        RitPrimaryButton("Pay Now", { action({
                            store.placeUnpaidOrder(current.id, checkoutKey)
                        }, { order ->
                            orderId = order.id
                            orderReturn = "orders"
                            checkoutKey = UUID.randomUUID().toString()
                            page = "order"
                        }) }, Modifier.fillMaxWidth().testTag("presentation_pay_now"), enabled = lines.isNotEmpty() && !busy)
                    }
                }
                "orders" -> {
                    CatalogHeader("Your orders", ::back)
                    val orders = remember(revision, current.id) { store.customerOrders(current.id) }
                    LazyColumn(Modifier.fillMaxSize().testTag("presentation_customer_orders")) {
                        if (orders.isEmpty()) item { RitText("No orders yet.", modifier = Modifier.padding(20.dp)) }
                        items(orders, key = { it.id }) { order -> OrderCard(order, { orderId = order.id; orderReturn = "orders"; page = "order" }) }
                    }
                }
                "order" -> {
                    CatalogHeader("Order details", ::back)
                    val order = remember(revision, orderId) { store.order(orderId) }
                    if (order != null && order.customerId == current.id) OrderDetails(order, remember(revision, orderId) { store.orderLines(orderId) }, false)
                }
                else -> {
                    CatalogHeader("Choose a canteen", null)
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            RitText("Hi, ${current.name.substringBefore(' ')}", RitType.heading)
                            RitSecondaryButton("Sign out", { store.logout(); account = null; page = "sellers" })
                        }
                        LazyColumn(Modifier.weight(1f).testTag("presentation_sellers")) {
                            items(PreviewCatalog.sellers, key = { it.id }) { seller ->
                                PresentationCard(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).clickable {
                                    sellerId = seller.id; page = "menu"
                                }.testTag("presentation_seller_${seller.id}")) {
                                    SellerPhotoContent(seller.photo, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)))
                                    Spacer(Modifier.height(8.dp))
                                    RitText(seller.name.substringBefore(" · demo"), RitType.title)
                                    RitText(seller.description.substringBefore(" · demo"), RitType.small, colors.muted, maxLines = 2)
                                }
                            }
                        }
                        RitSecondaryButton("Your orders", { page = "orders" }, Modifier.fillMaxWidth().padding(20.dp).testTag("presentation_orders_button"))
                    }
                }
            }
            LocalRole.SELLER -> when (page) {
                "order" -> {
                    CatalogHeader("Order details", ::back)
                    val order = remember(revision, orderId) { store.order(orderId) }
                    if (order != null && order.sellerId == current.sellerId) {
                        Column(Modifier.fillMaxSize()) {
                            OrderDetails(order, remember(revision, orderId) { store.orderLines(orderId) }, true, Modifier.weight(1f))
                            if (order.fulfillmentState == "NOT_READY") {
                                RitPrimaryButton("Mark ready", { action(block = { require(store.markReady(current, order.id)) { "Order state changed. Refresh and try again." } }) },
                                    Modifier.fillMaxWidth().padding(20.dp).testTag("presentation_mark_ready"), enabled = !busy)
                            }
                            if (order.fulfillmentState == "READY") {
                                var code by rememberSaveable(order.id) { mutableStateOf("") }
                                RitTextField(code, { code = it }, "Pickup code", "Enter customer's code",
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp), inputTag = "presentation_pickup_input")
                                RitPrimaryButton("Complete pickup", { action(block = {
                                    require(store.complete(current, order.id, code)) { "Code does not match, or this order is no longer ready." }
                                }) }, Modifier.fillMaxWidth().padding(20.dp).testTag("presentation_complete_pickup"), enabled = !busy)
                            }
                        }
                    }
                }
                "history" -> {
                    CatalogHeader("Completed orders", ::back)
                    val orders = remember(revision, current.sellerId) { store.sellerOrders(current) }
                    LazyColumn {
                        items(orders.filter { it.fulfillmentState == "COMPLETED" }, key = { it.id }) { order ->
                            OrderCard(order, { orderId = order.id; orderReturn = "history"; page = "order" })
                        }
                    }
                }
                "pending" -> {
                    CatalogHeader("Awaiting payment", ::back)
                    val orders = remember(revision, current.sellerId) { store.sellerOrders(current) }
                    LazyColumn(Modifier.testTag("presentation_seller_pending")) {
                        val pending = orders.filter { it.fulfillmentState == "AWAITING_PAYMENT" }
                        if (pending.isEmpty()) item { RitText("No orders awaiting payment.", modifier = Modifier.padding(20.dp)) }
                        items(pending, key = { it.id }) { order ->
                            OrderCard(order, { orderId = order.id; orderReturn = "pending"; page = "order" })
                        }
                    }
                }
                else -> {
                    val seller = PreviewCatalog.sellers.firstOrNull { it.id == current.sellerId }
                    CatalogHeader(seller?.name?.substringBefore(" · demo") ?: "Orders", null)
                    var tab by rememberSaveable { mutableStateOf("NOT_READY") }
                    val orders = remember(revision, current.sellerId) { store.sellerOrders(current) }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            RitText("Receiving Orders", RitType.heading)
                            RitSecondaryButton("Sign out", { store.logout(); account = null; page = "sellers" })
                        }
                        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RitChip("Not Ready", tab == "NOT_READY", { tab = "NOT_READY" })
                            RitChip("Ready", tab == "READY", { tab = "READY" })
                        }
                        val active = orders.filter { it.fulfillmentState == tab }
                        LazyColumn(Modifier.weight(1f).testTag("presentation_seller_queue")) {
                            if (active.isEmpty()) item { RitText("No orders here right now.", modifier = Modifier.padding(20.dp)) }
                            items(active, key = { it.id }) { order -> OrderCard(order, { orderId = order.id; orderReturn = "queue"; page = "order" }) }
                        }
                        RitSecondaryButton("Awaiting payment (${orders.count { it.fulfillmentState == "AWAITING_PAYMENT" }})",
                            { page = "pending" }, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                        RitSecondaryButton("Completed orders", { page = "history" }, Modifier.fillMaxWidth().padding(20.dp))
                    }
                }
            }
        }
        message?.let { RitText(it, RitType.small, colors.accent, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) }
    }
}

@Composable
private fun PresentationAuth(store: PresentationStore, busy: Boolean, error: String?, onAuthenticated: (LocalAccount) -> Unit,
                             onError: (String?) -> Unit, onBusy: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = RitThemeTokens.colors
    var signup by rememberSaveable { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf(LocalRole.CUSTOMER) }
    var sellerId by rememberSaveable { mutableStateOf(PreviewCatalog.MAIN_ID) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        RitText("RIT Canteen", RitType.display)
        RitText(if (signup) "Create your account" else "Welcome back", RitType.heading, colors.muted)
        Spacer(Modifier.height(24.dp))
        if (signup) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RitChip("Customer", role == LocalRole.CUSTOMER, { role = LocalRole.CUSTOMER })
                RitChip("Seller", role == LocalRole.SELLER, { role = LocalRole.SELLER })
            }
            Spacer(Modifier.height(12.dp))
            RitTextField(name, { name = it }, "Full name", "First and last name", Modifier.fillMaxWidth(), inputTag = "presentation_name")
            Spacer(Modifier.height(12.dp))
            if (role == LocalRole.SELLER) {
                RitText("Your canteen", RitType.label)
                PreviewCatalog.sellers.forEach { seller ->
                    RitChip(seller.name.substringBefore(" · demo"), seller.id == sellerId, { sellerId = seller.id })
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        RitTextField(email, { email = it }, "Email", "you@example.com", Modifier.fillMaxWidth(), KeyboardType.Email, "presentation_email")
        Spacer(Modifier.height(12.dp))
        RitTextField(password, { password = it }, "Password", "At least 10 characters", Modifier.fillMaxWidth(),
            KeyboardType.Password, "presentation_password", visualTransformation = PasswordVisualTransformation())
        error?.let { RitText(it, RitType.small, colors.accent, Modifier.padding(vertical = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        RitPrimaryButton(if (signup) "Create account" else "Sign in", {
            if (busy) return@RitPrimaryButton
            onBusy(true); onError(null)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (signup) store.register(name, email, password, role, if (role == LocalRole.SELLER) sellerId else null)
                        else store.login(email, password)
                    }
                }
                onBusy(false)
                result.onSuccess(onAuthenticated).onFailure { onError(it.message ?: "Please try again.") }
            }
        }, Modifier.fillMaxWidth().testTag("presentation_auth_submit"), enabled = !busy)
        Spacer(Modifier.height(10.dp))
        RitSecondaryButton(if (signup) "Already have an account? Sign in" else "Create an account", {
            signup = !signup; onError(null)
        }, Modifier.fillMaxWidth().testTag("presentation_auth_switch"))
    }
}

@Composable
private fun PresentationCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.warmCard).padding(14.dp)) { content() }
}

@Composable
private fun OrderCard(order: LocalOrder, onClick: () -> Unit) {
    val seller = PreviewCatalog.sellers.firstOrNull { it.id == order.sellerId }
    val colors = RitThemeTokens.colors
    PresentationCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clickable(onClick = onClick)
        .testTag("presentation_order_${order.id}")) {
        RitText(seller?.name?.substringBefore(" · demo") ?: "Order", RitType.title)
        RitText("${order.id.take(8)}  ·  ${money(order.totalPaise)}", RitType.body)
        RitText(fulfillmentLabel(order), RitType.small, colors.muted)
    }
}

@Composable
private fun OrderDetails(order: LocalOrder, lines: List<LocalOrderLine>, sellerView: Boolean, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(modifier.fillMaxWidth().padding(20.dp)) {
        RitText("Order ${order.id.take(8)}", RitType.heading)
        RitText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(order.createdAt)), RitType.small, colors.muted)
        Spacer(Modifier.height(18.dp))
        lines.forEach { RitText("${it.quantity} × ${it.name}  ${money(it.quantity * it.unitPaise)}", RitType.body) }
        Spacer(Modifier.height(12.dp))
        RitText("Total  ${money(order.totalPaise)}", RitType.title)
        Spacer(Modifier.height(18.dp))
        RitText(fulfillmentLabel(order), RitType.label)
        RitText(when (order.paymentState) {
            "PENDING" -> "Payment has not completed. This order has not been sent to the canteen."
            else -> "Payment was not recorded for this order."
        }, RitType.body, colors.muted)
        if (!sellerView && order.paymentState != "PENDING") {
            Spacer(Modifier.height(16.dp))
            RitText("Pickup code: ${order.pickupCode}", RitType.title)
        }
        if (sellerView && order.fulfillmentState == "READY") {
            Spacer(Modifier.height(16.dp))
            RitText("Ask the customer for their pickup code.", RitType.body, colors.muted)
        }
    }
}

private fun fulfillmentLabel(order: LocalOrder): String = when (order.fulfillmentState) {
    "AWAITING_PAYMENT" -> "Awaiting payment"
    "NOT_READY" -> "Not Ready"
    "READY" -> "Ready for pickup"
    "COMPLETED" -> "Completed"
    else -> order.fulfillmentState
}

private fun money(paise: Long): String = "₹${paise / 100}${if (paise % 100L == 0L) "" else ".${(paise % 100L).toString().padStart(2, '0')}"}"
