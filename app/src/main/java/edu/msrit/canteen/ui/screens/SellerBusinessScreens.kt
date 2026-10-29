package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkTimeWindow
import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.customer.DevelopmentBulkPreview
import edu.msrit.canteen.customer.DevelopmentPromotions
import edu.msrit.canteen.customer.DevelopmentSellerOperations
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.customer.DebugSellerCatalogApi
import edu.msrit.canteen.customer.DebugServerCatalog
import edu.msrit.canteen.customer.Dietary
import edu.msrit.canteen.customer.FoodKind
import edu.msrit.canteen.customer.ItemAvailability
import edu.msrit.canteen.customer.MenuItem
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.Price
import edu.msrit.canteen.customer.Promotion
import edu.msrit.canteen.customer.PromotionKind
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.SellerBulkPolicy
import edu.msrit.canteen.customer.SellerBusinessRepository
import edu.msrit.canteen.customer.SellerCapability
import edu.msrit.canteen.customer.SellerClosure
import edu.msrit.canteen.customer.SellerEmployee
import edu.msrit.canteen.customer.SellerEmployeeRole
import edu.msrit.canteen.customer.SellerPublicProfile
import edu.msrit.canteen.customer.UnavailableVisibility
import edu.msrit.canteen.customer.statusAt
import edu.msrit.canteen.customer.sellerPickupConflicts
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.SellerPhotoContent
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun BusinessPage(title: String, tag: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        CatalogHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            RitText(if (BuildConfig.RIT_CLOUD_DEMO) "Server-backed demo · unsupported changes are unavailable"
                else "Local backend demo · unsupported changes are not saved", RitType.small, colors.muted)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BusinessCard(title: String, detail: String, onClick: (() -> Unit)? = null, tag: String? = null,
                         food: MenuItem? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card))
        .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
        .then(if (tag == null) Modifier else Modifier.testTag(tag)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (food != null) FoodPhotoContent(food.photo, "${food.name} sample photo",
                Modifier.size(64.dp), food.localImageUri)
            Column {
                RitText(title, RitType.label)
                RitText(detail, RitType.small, colors.muted)
            }
        }
    }
}

@Composable
private fun BusinessMessage(text: String?) {
    if (text != null) {
        RitText(text, RitType.small, RitThemeTokens.colors.accent, Modifier.padding(vertical = 9.dp).testTag("business_message"))
    }
}

@Composable
fun SellerBusinessHomeScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit, onRequests: () -> Unit, onSchedule: () -> Unit,
    onMenu: () -> Unit, onCategories: () -> Unit, onPromotions: () -> Unit,
    onEmployees: () -> Unit, onSettings: () -> Unit) {
    val seller = PreviewCatalog.sellers.find { it.id == sellerId }
    val caps = repo.capabilities(userId, sellerId)
    BusinessPage("Seller management", "seller_management", onBack) {
        RitText(seller?.name ?: "Seller", RitType.heading)
        RitText("Your server-granted capabilities determine available actions.", RitType.body)
        Spacer(Modifier.height(20.dp))
        RitText("Orders & pickup", RitType.title)
        Spacer(Modifier.height(8.dp))
        BusinessCard("Bulk requests", "Review current demo requests", onRequests, "seller_manage_requests")
        BusinessCard("Schedule", "Pickup requests and operational times", onSchedule, "seller_manage_schedule")
        Spacer(Modifier.height(12.dp))
        RitText("Menu & catalog", RitType.title)
        Spacer(Modifier.height(8.dp))
        if (SellerCapability.CATALOG in caps || SellerCapability.AVAILABILITY in caps)
            BusinessCard("Menu management", "Items, availability and categories", onMenu, "seller_manage_menu")
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.CATALOG in caps)
            BusinessCard("Categories", "Organize your menu", onCategories, "seller_manage_categories")
        Spacer(Modifier.height(12.dp))
        RitText("People & promotions", RitType.title)
        Spacer(Modifier.height(8.dp))
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.PROMOTIONS in caps)
            BusinessCard("Promotions", "Drafts, scheduled and expired placements", onPromotions, "seller_manage_promotions")
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.EMPLOYEES in caps)
            BusinessCard("Team and permissions", "Seller-scoped delegated access", onEmployees, "seller_manage_employees")
        Spacer(Modifier.height(12.dp))
        RitText("Business profile & settings", RitType.title)
        Spacer(Modifier.height(8.dp))
        BusinessCard("Business settings", "Profile, hours, closures, bulk minimum", onSettings, "seller_manage_settings")
    }
}

@Composable
fun SellerMenuManagementScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit, onItem: (String?) -> Unit, onCategories: () -> Unit, onAvailability: (String) -> Unit,
    rootTab: Boolean = false) {
    val caps = repo.capabilities(userId, sellerId)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val seller = PreviewCatalog.sellers.find { it.id == sellerId }
    val activeCategoryIds = seller?.categoryIds?.filter { id ->
        PreviewCatalog.items.any { it.sellerId == sellerId && it.categoryId == id }
    }.orEmpty()
    val selectedCategory = category?.takeIf { it in activeCategoryIds }
    LaunchedEffect(sellerId, selectedCategory, activeCategoryIds) {
        if (category != selectedCategory) category = null
    }
    val filteredItems = PreviewCatalog.items.filter { it.sellerId == sellerId &&
        (selectedCategory == null || it.categoryId == selectedCategory) && it.name.contains(query, true) }
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_menu_management")) {
        CatalogHeader("Menu management", if (rootTab) null else onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText(if (BuildConfig.RIT_CLOUD_DEMO) "Server catalog" else "Local backend catalog", RitType.small, colors.muted)
            RitText(seller?.name ?: "Seller", RitType.heading)
            RitText("Prepared and packaged eligibility follows fixed platform rules.", RitType.small)
            Spacer(Modifier.height(12.dp))
            RitTextField(query, { query = it }, "Search menu", "Item name", inputTag = "seller_menu_search")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RitChip("All", selectedCategory == null, { category = null })
                activeCategoryIds.forEach { id ->
                    RitChip(PreviewCatalog.categories.find { it.id == id }?.name ?: id,
                        selectedCategory == id, { category = id })
                }
            }
            Spacer(Modifier.height(13.dp))
            if (SellerCapability.CATALOG in caps) {
                RitPrimaryButton("+ Add food item · unavailable", {}, Modifier.fillMaxWidth().testTag("seller_add_item"),
                    enabled = false)
                Spacer(Modifier.height(9.dp))
                if (!BuildConfig.RIT_CLOUD_DEMO) {
                    RitSecondaryButton("Manage categories", onCategories, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("seller_menu_items"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (filteredItems.isEmpty()) item { BusinessCard("No matching items", "Try another search or category.") }
            items(filteredItems, key = { it.id }) { item ->
                val status = when {
                    !item.published -> "Unpublished · hidden from customers"
                    item.availability == ItemAvailability.UNAVAILABLE && item.unavailableVisibility == UnavailableVisibility.HIDE -> "Unavailable · hidden"
                    item.availability == ItemAvailability.UNAVAILABLE -> "Unavailable · visible, cannot request"
                    else -> "Available · visible"
                }
                BusinessCard("${item.name} · ${item.price.display}",
                    "${item.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · $status",
                    { if (SellerCapability.CATALOG in caps) onItem(item.id) else onAvailability(item.id) },
                    "seller_item_${item.id}", food = item)
            }
        }
    }
}

@Composable
fun SellerItemEditorScreen(sellerId: String, userId: String, itemId: String?, repo: SellerBusinessRepository,
    onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val old = PreviewCatalog.items.find { it.id == itemId && it.sellerId == sellerId }
    val canChangeAvailability = SellerCapability.AVAILABILITY in repo.capabilities(userId, sellerId)
    var name by remember(itemId) { mutableStateOf(old?.name.orEmpty()) }
    var description by remember(itemId) { mutableStateOf(old?.description.orEmpty()) }
    var price by remember(itemId) { mutableStateOf(old?.price?.rupees?.toString().orEmpty()) }
    var category by remember(itemId) { mutableStateOf(old?.categoryId ?: PreviewCatalog.sellers.find { it.id == sellerId }?.categoryIds?.firstOrNull().orEmpty()) }
    var kind by remember(itemId) { mutableStateOf(old?.kind ?: FoodKind.PREPARED) }
    var dietary by remember(itemId) { mutableStateOf(old?.dietary ?: Dietary.NOT_SPECIFIED) }
    var tags by remember(itemId) { mutableStateOf(old?.tags?.joinToString(", ").orEmpty()) }
    var available by remember(itemId) { mutableStateOf(old?.availability != ItemAvailability.UNAVAILABLE) }
    var published by remember(itemId) { mutableStateOf(old?.published ?: false) }
    var showUnavailable by remember(itemId) { mutableStateOf(old?.unavailableVisibility != UnavailableVisibility.HIDE) }
    var photoUri by remember(itemId) { mutableStateOf(old?.localImageUri) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }
    val dirty = name != old?.name.orEmpty() || description != old?.description.orEmpty() ||
        price != old?.price?.rupees?.toString().orEmpty() || category != (old?.categoryId ?: PreviewCatalog.sellers.find { it.id == sellerId }?.categoryIds?.firstOrNull().orEmpty()) ||
        kind != (old?.kind ?: FoodKind.PREPARED) || dietary != (old?.dietary ?: Dietary.NOT_SPECIFIED) ||
        tags != old?.tags?.joinToString(", ").orEmpty() ||
        available != (old?.availability != ItemAvailability.UNAVAILABLE) || published != (old?.published ?: false) ||
        showUnavailable != (old?.unavailableVisibility != UnavailableVisibility.HIDE) || photoUri != old?.localImageUri
    fun back() { if (dirty) confirmDiscard = true else onBack() }
    BackHandler { back() }
    BusinessPage(if (old == null) "Add food item" else "Edit food item", "seller_item_editor", ::back) {
        if (confirmDiscard) {
            BusinessCard("Discard unsaved changes?", "Changes on this screen have not been saved.")
            RitSecondaryButton("Keep editing", { confirmDiscard = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RitPrimaryButton("Discard changes", onBack, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
        RitTextField(name, { name = it }, "Item name", "Required", inputTag = "seller_item_name")
        Spacer(Modifier.height(9.dp))
        RitTextField(description, { description = it }, "Description", "What is included?", singleLine = false)
        Spacer(Modifier.height(9.dp))
        RitTextField(price, { price = it }, "Price in ₹", "0", keyboardType = KeyboardType.Number, inputTag = "seller_item_price")
        Spacer(Modifier.height(16.dp))
        RitText("Primary category", RitType.label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PreviewCatalog.sellers.find { it.id == sellerId }?.categoryIds?.forEach { id ->
                RitChip(PreviewCatalog.categories.find { it.id == id }?.name ?: id, category == id, { category = id })
            }
        }
        Spacer(Modifier.height(9.dp))
        RitText("Product type", RitType.label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodKind.entries.forEach { value -> RitChip(value.name.lowercase().replaceFirstChar(Char::uppercase), kind == value, { kind = value }) }
        }
        RitText(if (kind == FoodKind.PACKAGED) "Packaged goods are bulk-only."
            else if (sellerId == PreviewCatalog.MAIN_ID) "Prepared food: future individual + bulk eligibility."
            else "Prepared food from this seller is bulk-only.", RitType.small, RitThemeTokens.colors.muted)
        Spacer(Modifier.height(12.dp))
        RitText("Dietary classification", RitType.label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Dietary.entries.forEach { value -> RitChip(value.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                dietary == value, { dietary = value }) }
        }
        Spacer(Modifier.height(8.dp))
        RitTextField(tags, { tags = it }, "Optional tags", "Comma-separated discovery labels")
        Spacer(Modifier.height(12.dp))
        FoodPhotoContent(old?.photo, "$name preview", Modifier.fillMaxWidth().height(150.dp), photoUri)
        Spacer(Modifier.height(9.dp))
        RitSecondaryButton(if (photoUri == null) "Choose local preview photo" else "Replace local preview photo", {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.fillMaxWidth().testTag("seller_pick_photo"))
        RitText("Photo remains a local debug preview; it is not uploaded.", RitType.small, RitThemeTokens.colors.muted)
        Spacer(Modifier.height(14.dp))
        if (canChangeAvailability) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RitChip("Available", available, { available = true })
                RitChip("Unavailable", !available, { available = false })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RitChip("Published", published, { published = true })
                RitChip("Unpublished", !published, { published = false })
            }
            if (!available) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RitChip("Visible", showUnavailable, { showUnavailable = true })
                RitChip("Hidden", !showUnavailable, { showUnavailable = false })
            }
        } else {
            BusinessCard("Visibility restricted", "Your role can edit item details but cannot change availability or publication.")
        }
        BusinessMessage(message)
        RitPrimaryButton("Save item", {
            val amount = price.toIntOrNull()
            message = when {
                name.isBlank() -> "Item name is required."
                amount == null || amount < 0 -> "Enter a valid nonnegative price."
                else -> {
                    val item = MenuItem(old?.id ?: "${sellerId}-item-${UUID.randomUUID()}", sellerId, category,
                        name.trim(), description.trim(), Price(amount), kind, dietary,
                        if (available) ItemAvailability.AVAILABLE else ItemAvailability.UNAVAILABLE,
                        published, if (showUnavailable) UnavailableVisibility.SHOW else UnavailableVisibility.HIDE,
                        old?.photo, old?.featured ?: false, photoUri,
                        tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                    if (old == null) "Adding new items is not supported by the current backend."
                    else {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { DebugSellerCatalogApi.saveItem(item) }
                            if (result.success) {
                                val snapshot = runCatching { withContext(Dispatchers.IO) {
                                    DebugServerCatalog.fetch() } }.getOrNull()
                                if (snapshot != null) DebugServerCatalog.apply(snapshot)
                                withContext(Dispatchers.Main.immediate) { onSaved() }
                            } else message = result.message
                        }
                        null
                    }
                }
            }
        }, Modifier.fillMaxWidth().testTag("seller_save_item"))
    }
}

@Composable
fun SellerAvailabilityScreen(sellerId: String, itemId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val item = PreviewCatalog.items.find { it.id == itemId && it.sellerId == sellerId }
    var available by remember(itemId) { mutableStateOf(item?.availability != ItemAvailability.UNAVAILABLE) }
    var published by remember(itemId) { mutableStateOf(item?.published ?: false) }
    var visible by remember(itemId) { mutableStateOf(item?.unavailableVisibility != UnavailableVisibility.HIDE) }
    var message by remember { mutableStateOf<String?>(null) }
    BusinessPage("Availability & visibility", "seller_availability", onBack) {
        if (item == null) BusinessCard("Item unavailable", "This item does not belong to your seller.") else {
            RitText(item.name, RitType.heading)
            RitText("${item.kind.name.lowercase()} · ${item.price.display}", RitType.small)
            Spacer(Modifier.height(14.dp))
            RitText("Can customers request it?", RitType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RitChip("Available", available, { available = true })
                RitChip("Unavailable", !available, { available = false })
            }
            Spacer(Modifier.height(13.dp))
            RitText("Publication", RitType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RitChip("Published", published, { published = true })
                RitChip("Unpublished", !published, { published = false })
            }
            if (!available) {
                Spacer(Modifier.height(13.dp))
                RitText("Customer visibility while unavailable", RitType.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RitChip("Show", visible, { visible = true })
                    RitChip("Hide", !visible, { visible = false })
                }
            }
            Spacer(Modifier.height(16.dp))
            BusinessCard("Customer result", when {
                !published -> "Not listed in customer discovery"
                !available && !visible -> "Hidden until made available again"
                !available -> "Visible but bulk request disabled"
                else -> "Visible and eligible under seller/platform rules"
            })
            BusinessMessage(message)
            RitPrimaryButton("Save visibility", {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        DebugSellerCatalogApi.setAvailability(sellerId, itemId, available, published, visible)
                    }
                    val snapshot = runCatching { withContext(Dispatchers.IO) {
                        DebugServerCatalog.fetch() } }.getOrNull()
                    if (snapshot != null) DebugServerCatalog.apply(snapshot)
                    message = result.message
                }
            }, Modifier.fillMaxWidth().testTag("seller_save_availability"))
        }
    }
}

@Composable
fun SellerCategoriesScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit) {
    val seller = PreviewCatalog.sellers.find { it.id == sellerId }
    val ids = seller?.categoryIds.orEmpty() + repo.hiddenCategoryIds[sellerId].orEmpty()
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    BusinessPage("Categories", "seller_categories", onBack) {
        RitText("Changes update the customer demo menu. Hiding a category hides its items; removal is blocked while any item uses it.",
            RitType.body)
        Spacer(Modifier.height(14.dp))
        ids.forEach { id ->
            val category = PreviewCatalog.categories.find { it.id == id } ?: return@forEach
            val count = PreviewCatalog.items.count { it.sellerId == sellerId && it.categoryId == id }
            BusinessCard(category.name, "$count items · ${if (id in repo.hiddenCategoryIds[sellerId].orEmpty()) "Hidden" else "Visible"}",
                { editingId = id; name = category.name }, "seller_category_$id")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RitChip("↑", false, { if (!repo.moveCategory(userId, sellerId, id, -1)) message = "Cannot move this category." })
                RitChip("↓", false, { if (!repo.moveCategory(userId, sellerId, id, 1)) message = "Cannot move this category." })
                val hidden = id in repo.hiddenCategoryIds[sellerId].orEmpty()
                RitChip(if (hidden) "Show" else "Hide", false, {
                    if (!repo.hideCategory(userId, sellerId, id, !hidden)) message = "Permission denied."
                })
                RitChip("Remove", false, {
                    message = if (repo.removeCategory(userId, sellerId, id)) "Removed from this seller."
                        else "Cannot remove a category with assigned items."
                })
            }
            Spacer(Modifier.height(12.dp))
        }
        RitText(if (editingId == null) "New category" else "Rename category", RitType.title)
        RitTextField(name, { name = it }, "Category name", "Required", inputTag = "seller_category_name")
        Spacer(Modifier.height(9.dp))
        RitPrimaryButton(if (editingId == null) "Create category" else "Save name", {
            message = if (repo.saveCategory(userId, sellerId, editingId, name)) {
                editingId = null; name = ""; "Category saved."
            } else "Enter a name and check your access."
        }, Modifier.fillMaxWidth().testTag("seller_save_category"))
        if (editingId != null) RitSecondaryButton("Cancel rename", { editingId = null; name = "" }, Modifier.fillMaxWidth())
        BusinessMessage(message)
    }
}

@Composable
fun SellerBusinessSettingsScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit, onProfile: () -> Unit, onHours: () -> Unit,
    onClosures: () -> Unit, onMinimum: () -> Unit, onEmployees: () -> Unit, onPromotions: () -> Unit) {
    val caps = repo.capabilities(userId, sellerId)
    BusinessPage("Business settings", "seller_business_settings", onBack) {
        RitText(PreviewCatalog.sellers.find { it.id == sellerId }?.name ?: "Seller", RitType.heading)
        Spacer(Modifier.height(12.dp))
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.PROFILE in caps)
            BusinessCard("Public profile", "Name, description, location and local preview image", onProfile)
        if (SellerCapability.HOURS in caps) {
            BusinessCard("Working hours", "Weekly schedule in Asia/Kolkata", onHours, "seller_settings_hours")
            BusinessCard("Holidays & closures", "Special closed dates and reasons", onClosures)
            BusinessCard("Bulk-order minimum", "Combined eligible units from one seller", onMinimum, "seller_settings_minimum")
        }
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.EMPLOYEES in caps)
            BusinessCard("Team & permissions", "Manage delegated debug employees", onEmployees)
        if (!BuildConfig.RIT_CLOUD_DEMO && SellerCapability.PROMOTIONS in caps)
            BusinessCard("Promotions", "Seller-owned demo placements", onPromotions)
        BusinessCard("Notifications", "Preferences are not connected to a delivery service yet.")
        BusinessCard("Payment configuration", "Not connected. PhonePe merchant eligibility and settlement requirements remain unverified; no credentials or transactions here.")
    }
}

@Composable
fun SellerProfileEditorScreen(sellerId: String, userId: String, repo: SellerBusinessRepository, onBack: () -> Unit) {
    val seller = PreviewCatalog.sellers.find { it.id == sellerId }
    var name by remember(sellerId) { mutableStateOf(seller?.name.orEmpty()) }
    var description by remember(sellerId) { mutableStateOf(seller?.description.orEmpty()) }
    var location by remember(sellerId) { mutableStateOf(seller?.location.orEmpty()) }
    var photoUri by remember(sellerId) { mutableStateOf(seller?.localImageUri) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }
    BusinessPage("Business profile", "seller_profile_editor", onBack) {
        RitTextField(name, { name = it }, "Seller display name", "Required")
        Spacer(Modifier.height(9.dp))
        RitTextField(description, { description = it }, "Description", "Public description", singleLine = false)
        Spacer(Modifier.height(9.dp))
        RitTextField(location, { location = it }, "Location", "Public location details")
        Spacer(Modifier.height(12.dp))
        SellerPhotoContent(seller?.photo, Modifier.fillMaxWidth().height(150.dp), photoUri)
        Spacer(Modifier.height(8.dp))
        RitSecondaryButton("Choose local preview image", {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.fillMaxWidth())
        RitText(if (photoUri == null) "No local image selected." else "Local preview image selected; never uploaded.",
            RitType.small, RitThemeTokens.colors.muted)
        Spacer(Modifier.height(12.dp))
        BusinessCard("Protected business identity", "Legal identity, payout and merchant verification are not editable here.")
        BusinessMessage(message)
        RitPrimaryButton("Save public profile", {
            message = if (repo.saveProfile(userId, sellerId, SellerPublicProfile(name, description, location, photoUri)))
                "Saved in customer demo profile." else "Name is required or access was denied."
        }, Modifier.fillMaxWidth().testTag("seller_save_profile"))
    }
}

@Composable
fun SellerHoursScreen(sellerId: String, userId: String, repo: SellerBusinessRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = DevelopmentBulkPreview.forSeller(sellerId)
    var hours by remember(sellerId) { mutableStateOf(current?.weeklyHours.orEmpty()) }
    var opening by remember { mutableStateOf("09:00") }
    var closing by remember { mutableStateOf("17:00") }
    var selected by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var message by remember { mutableStateOf<String?>(null) }
    BusinessPage("Working hours", "seller_hours", onBack) {
        RitText("Asia/Kolkata · this is a draft schedule until saved. The backend does not expose a read-back hours endpoint.", RitType.body)
        Spacer(Modifier.height(14.dp))
        DayOfWeek.entries.forEach { day ->
            val windows = hours[day].orEmpty()
            BusinessCard(day.name.lowercase().replaceFirstChar(Char::uppercase),
                if (windows.isEmpty()) "Closed" else windows.joinToString { "${it.opens}–${it.closes}" },
                { selected = day; opening = windows.firstOrNull()?.opens?.toString() ?: "09:00";
                    closing = windows.firstOrNull()?.closes?.toString() ?: "17:00" }, "seller_hours_${day.name.lowercase()}")
        }
        RitText("Edit ${selected.name.lowercase().replaceFirstChar(Char::uppercase)}", RitType.title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitChip("Open", selected in hours, { hours = hours + (selected to listOf(BulkTimeWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)))) })
            RitChip("Closed", selected !in hours, { hours = hours - selected })
        }
        if (selected in hours) {
            RitTextField(opening, { opening = it }, "Opens", "HH:mm", inputTag = "seller_hours_open")
            Spacer(Modifier.height(8.dp))
            RitTextField(closing, { closing = it }, "Closes", "HH:mm", inputTag = "seller_hours_close")
            Spacer(Modifier.height(8.dp))
            RitSecondaryButton("Apply time to selected day", {
                val start = runCatching { LocalTime.parse(opening) }.getOrNull()
                val end = runCatching { LocalTime.parse(closing) }.getOrNull()
                if (start == null || end == null || start >= end) message = "Use valid HH:mm times with opening before closing."
                else { hours = hours + (selected to listOf(BulkTimeWindow(start, end))); message = "Time applied; save schedule below." }
            }, Modifier.fillMaxWidth().testTag("seller_apply_hours"))
        }
        Spacer(Modifier.height(13.dp))
        val proposed = current?.copy(weeklyHours = hours) ?: SellerBulkPolicy(sellerId, weeklyHours = hours)
        val conflicts = sellerPickupConflicts(DevelopmentSellerOperations.records, sellerId,
            proposed, LocalDateTime.now(RIT_TIME_ZONE))
        BusinessCard("Existing requests stay unchanged", if (conflicts.isEmpty())
            "No current demo pickup time conflicts with this weekly schedule. Approved terms are never rewritten."
            else "Review affected request(s): ${conflicts.joinToString()}. Original quote terms remain unchanged.")
        BusinessMessage(message)
        RitPrimaryButton("Save weekly hours", {
            val policy = current?.copy(weeklyHours = hours) ?: SellerBulkPolicy(sellerId, weeklyHours = hours)
            scope.launch {
                val result = withContext(Dispatchers.IO) { DebugSellerCatalogApi.saveHours(sellerId, policy) }
                if (result.success) DevelopmentSellerBusiness.applyServerPolicy(policy)
                message = result.message
            }
        }, Modifier.fillMaxWidth().testTag("seller_save_hours"))
    }
}

@Composable
fun SellerClosuresScreen(sellerId: String, userId: String, repo: SellerBusinessRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = DevelopmentBulkPreview.forSeller(sellerId) ?: SellerBulkPolicy(sellerId)
    var date by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var holiday by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val reasons = repo.closureReasons[sellerId].orEmpty()
    val knownDates = (current.holidays + current.temporaryClosures).sorted()
    BusinessPage("Holidays & closures", "seller_closures", onBack) {
        RitText("Closed dates require a special pickup request and explicit seller acceptance. Existing quote terms are preserved.", RitType.body)
        Spacer(Modifier.height(12.dp))
        knownDates.forEach { day ->
            val isHoliday = day in current.holidays
            val detail = reasons.find { it.date == day }?.reason ?: "Sample closed date"
            BusinessCard(day.toString(), "${if (isHoliday) "Holiday" else "Temporary closure"} · $detail")
            RitSecondaryButton("Restore ordinary availability for $day", {
                val updated = current.copy(holidays = current.holidays - day, temporaryClosures = current.temporaryClosures - day)
                scope.launch {
                    val result = withContext(Dispatchers.IO) { DebugSellerCatalogApi.setClosure(sellerId, day, null) }
                    if (result.success) DevelopmentSellerBusiness.applyServerPolicy(updated, reasons.filterNot { it.date == day })
                    message = result.message
                }
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        val conflicts = sellerPickupConflicts(DevelopmentSellerOperations.records, sellerId,
            current, LocalDateTime.now(RIT_TIME_ZONE))
        if (conflicts.isNotEmpty()) BusinessCard("Pickup conflicts to review",
            "${conflicts.joinToString()} · local status only; no request or quote is rewritten.")
        RitTextField(date, { date = it }, "Closed date", "yyyy-MM-dd", inputTag = "seller_closure_date")
        Spacer(Modifier.height(8.dp))
        RitTextField(reason, { reason = it }, "Reason", "Required", inputTag = "seller_closure_reason")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitChip("Holiday", holiday, { holiday = true })
            RitChip("Temporary", !holiday, { holiday = false })
        }
        BusinessMessage(message)
        RitPrimaryButton("Add closed date", {
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            message = when {
                parsed == null -> "Enter a valid yyyy-MM-dd date."
                reason.isBlank() -> "Enter a closure reason."
                parsed in knownDates -> "That date is already closed."
                else -> {
                    val updated = if (holiday) current.copy(holidays = current.holidays + parsed)
                        else current.copy(temporaryClosures = current.temporaryClosures + parsed)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            DebugSellerCatalogApi.setClosure(sellerId, parsed, reason.trim())
                        }
                        if (result.success) {
                            DevelopmentSellerBusiness.applyServerPolicy(updated,
                                reasons + SellerClosure(parsed, reason.trim(), holiday))
                            date = ""; reason = ""
                        }
                        message = result.message
                    }
                    null
                }
            }
        }, Modifier.fillMaxWidth().testTag("seller_add_closure"))
    }
}

@Composable
fun SellerBulkMinimumScreen(sellerId: String, userId: String, repo: SellerBusinessRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = DevelopmentBulkPreview.forSeller(sellerId) ?: SellerBulkPolicy(sellerId)
    var minimum by remember(sellerId) { mutableStateOf(current.minimum.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    BusinessPage("Bulk-order minimum", "seller_bulk_minimum", onBack) {
        RitText("Current minimum: ${current.minimum} combined units", RitType.heading)
        RitText("Applies to the total eligible quantity from this seller, not the number of different products. Cross-seller items never combine.", RitType.body)
        Spacer(Modifier.height(14.dp))
        RitTextField(minimum, { minimum = it }, "Minimum units", "Positive whole number", keyboardType = KeyboardType.Number,
            inputTag = "seller_minimum_input")
        BusinessMessage(message)
        RitPrimaryButton("Save minimum", {
            val parsed = minimum.toIntOrNull()
            message = if (parsed == null || parsed !in 1..999) "Enter a whole number from 1 to 999."
                else {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { DebugSellerCatalogApi.saveMinimum(sellerId, parsed) }
                        if (result.success) {
                            DevelopmentSellerBusiness.applyServerPolicy(current.copy(minimumOverride = parsed))
                            val snapshot = runCatching { withContext(Dispatchers.IO) {
                                DebugServerCatalog.fetch() } }.getOrNull()
                            if (snapshot != null) DebugServerCatalog.apply(snapshot)
                        }
                        message = result.message
                    }
                    null
                }
        }, Modifier.fillMaxWidth().testTag("seller_save_minimum"))
    }
}

@Composable
fun SellerEmployeesScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit, onEmployee: (String?) -> Unit) {
    val own = repo.employees.filter { it.sellerId == sellerId }
    val actor = own.find { it.id == userId }
    BusinessPage("Team members", "seller_employees", onBack) {
        RitText("Local debug assignments only", RitType.heading)
        RitText("Employee access is provisioned by authorized seller management, never public registration. No email is sent.", RitType.body)
        Spacer(Modifier.height(13.dp))
        own.forEach { employee ->
            val effective = repo.capabilities(employee.id, sellerId)
            BusinessCard(employee.name,
                "${employee.role.name.lowercase().replaceFirstChar(Char::uppercase)} · ${if (employee.active) "Active" else "Inactive"} · ${effective.size} effective capabilities",
                { onEmployee(employee.id) }, "seller_employee_${employee.id}")
        }
        if (actor?.role != SellerEmployeeRole.STAFF) {
            Spacer(Modifier.height(12.dp))
            RitPrimaryButton("+ Add local demo employee", { onEmployee(null) },
                Modifier.fillMaxWidth().testTag("seller_add_employee"))
        }
    }
}

@Composable
fun SellerEmployeeEditorScreen(sellerId: String, userId: String, employeeId: String?,
    repo: SellerBusinessRepository, onBack: () -> Unit, onSaved: () -> Unit) {
    val actor = repo.employees.find { it.id == userId && it.sellerId == sellerId }
    val old = repo.employees.find { it.id == employeeId && it.sellerId == sellerId }
    val editable = old?.role != SellerEmployeeRole.OWNER && actor?.id != old?.id &&
        (actor?.role == SellerEmployeeRole.OWNER || (actor?.role == SellerEmployeeRole.MANAGER &&
            (old == null || (old.role == SellerEmployeeRole.STAFF && old.supervisorId == actor?.id))))
    var name by remember(employeeId) { mutableStateOf(old?.name.orEmpty()) }
    var role by remember(employeeId) { mutableStateOf(old?.role ?: if (actor?.role == SellerEmployeeRole.OWNER)
        SellerEmployeeRole.MANAGER else SellerEmployeeRole.STAFF) }
    var supervisor by remember(employeeId) { mutableStateOf(old?.supervisorId ?: if (role == SellerEmployeeRole.MANAGER)
        repo.employees.firstOrNull { it.sellerId == sellerId && it.role == SellerEmployeeRole.OWNER }?.id.orEmpty()
        else if (actor?.role == SellerEmployeeRole.MANAGER) actor?.id.orEmpty() else
            repo.employees.firstOrNull { it.sellerId == sellerId && it.role == SellerEmployeeRole.MANAGER }?.id.orEmpty()) }
    var grants by remember(employeeId) { mutableStateOf(old?.granted.orEmpty()) }
    var active by remember(employeeId) { mutableStateOf(old?.active ?: true) }
    var message by remember { mutableStateOf<String?>(null) }
    val parentCaps = repo.capabilities(supervisor, sellerId)
    BusinessPage(if (old == null) "Add demo employee" else "Employee permissions", "seller_employee_editor", onBack) {
        if (!editable) {
            BusinessCard("Protected access", "You cannot edit yourself, an owner, or someone outside your management scope.")
        } else {
            RitText("No invitation or real account is created. These are local test assignments.", RitType.body)
            Spacer(Modifier.height(12.dp))
            RitTextField(name, { name = it }, "Employee label", "Demo name", inputTag = "seller_employee_name")
            Spacer(Modifier.height(12.dp))
            RitText("Role", RitType.label)
            if (old != null) RitText(role.name.lowercase().replaceFirstChar(Char::uppercase), RitType.body)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actor?.role == SellerEmployeeRole.OWNER) RitChip("Manager", role == SellerEmployeeRole.MANAGER, {
                    role = SellerEmployeeRole.MANAGER
                    supervisor = repo.employees.firstOrNull { it.sellerId == sellerId && it.role == SellerEmployeeRole.OWNER }?.id.orEmpty()
                    grants = emptySet()
                })
                RitChip("Staff", role == SellerEmployeeRole.STAFF, {
                    role = SellerEmployeeRole.STAFF
                    supervisor = if (actor?.role == SellerEmployeeRole.MANAGER) actor?.id.orEmpty() else
                        repo.employees.firstOrNull { it.sellerId == sellerId && it.role == SellerEmployeeRole.MANAGER }?.id.orEmpty()
                    grants = emptySet()
                })
            }
            if (role == SellerEmployeeRole.STAFF) {
                RitText("Supervising manager", RitType.label)
                repo.employees.filter { it.sellerId == sellerId && it.role == SellerEmployeeRole.MANAGER && it.active }
                    .filter { actor?.role == SellerEmployeeRole.OWNER || it.id == actor?.id }.forEach { manager ->
                        RitChip(manager.name, supervisor == manager.id, { supervisor = manager.id; grants = grants intersect repo.capabilities(manager.id, sellerId) },
                            Modifier.padding(end = 7.dp, bottom = 7.dp))
                    }
            }
        Spacer(Modifier.height(12.dp))
        RitText("Delegated capabilities", RitType.title)
            RitText("A manager cannot exceed the owner; staff cannot exceed their supervising manager. Revocation cascades immediately.",
                RitType.small, RitThemeTokens.colors.muted)
            SellerCapability.entries.forEach { cap ->
                if (cap in parentCaps) {
                    RitChip(cap.label, cap in grants, { grants = if (cap in grants) grants - cap else grants + cap },
                        Modifier.padding(end = 7.dp, bottom = 7.dp))
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RitChip("Active", active, { active = true })
                RitChip("Inactive", !active, { active = false })
            }
            BusinessMessage(message)
            RitPrimaryButton("Save local permissions", {
                val employee = SellerEmployee(old?.id ?: "debug-employee-${UUID.randomUUID()}", sellerId,
                    name.trim(), role, supervisor, grants, active)
                message = if (repo.saveEmployee(userId, employee)) { onSaved(); null }
                    else "Access denied or grants exceed supervisor permissions."
            }, Modifier.fillMaxWidth().testTag("seller_save_employee"))
            if (old != null) {
                Spacer(Modifier.height(9.dp))
                RitSecondaryButton("Deactivate access", {
                    message = if (repo.removeEmployee(userId, sellerId, old.id)) "Access deactivated in local demo."
                        else "Access denied."
                }, Modifier.fillMaxWidth().testTag("seller_deactivate_employee"))
            }
        }
    }
}

private val promotionDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun SellerPromotionsScreen(sellerId: String, userId: String, repo: SellerBusinessRepository,
    onBack: () -> Unit, onPromotion: (String?) -> Unit) {
    val own = DevelopmentPromotions.promotions.filter { it.sellerId == sellerId }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Promotion?>(null) }
    val colors = RitThemeTokens.colors
    BusinessPage("Promotions", "seller_promotions", onBack) {
        RitText("Your promotions", RitType.heading)
        RitText("Preview how customers see your sample offers.", RitType.body, colors.muted)
        Spacer(Modifier.height(12.dp))
        RitPrimaryButton("+ Create promotion", { onPromotion(null) }, Modifier.fillMaxWidth().testTag("seller_add_promotion"))
        Spacer(Modifier.height(14.dp))
        if (own.isEmpty()) BusinessCard("No promotions yet", "Create a local draft to preview the flow.")
        own.forEach { promotion ->
            val item = PreviewCatalog.items.find { it.id in promotion.itemIds }
            val targets = promotion.itemIds.mapNotNull { id -> PreviewCatalog.items.find { it.id == id }?.name } +
                promotion.categoryIds.mapNotNull { id -> PreviewCatalog.categories.find { it.id == id }?.name }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).border(1.dp, colors.outline,
                RoundedCornerShape(RitRadii.card)).background(colors.card, RoundedCornerShape(RitRadii.card))
                .padding(12.dp).testTag("seller_promotion_${promotion.id}")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FoodPhotoContent(item?.photo, "${item?.name ?: promotion.title} sample promotion image",
                        Modifier.height(76.dp).weight(0.34f), item?.localImageUri)
                    Column(Modifier.weight(0.66f)) {
                        RitText(offerBenefit(promotion), RitType.title, colors.accent)
                        RitText(promotion.title.replace(" · demo", ""), RitType.label)
                        RitText(if (!promotion.published) "Draft" else promotion.statusAt(Instant.now()).name.lowercase()
                            .replaceFirstChar(Char::uppercase), RitType.small, colors.muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                RitText("For ${targets.ifEmpty { listOf("seller menu") }.joinToString()}", RitType.small, colors.muted)
                RitText("${promotion.startsAt.atZone(RIT_TIME_ZONE).toLocalDate()} – ${promotion.endsAt.atZone(RIT_TIME_ZONE).toLocalDate()}",
                    RitType.small, colors.muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RitSecondaryButton("Edit", { onPromotion(promotion.id) }, Modifier.weight(1f))
                    RitSecondaryButton("Preview", { preview = promotion }, Modifier.weight(1f)
                        .testTag("seller_promotion_preview_${promotion.id}"))
                }
            }
            RitSecondaryButton(if (promotion.published) "Unpublish" else "Publish", {
                message = if (repo.savePromotion(userId, promotion.copy(published = !promotion.published)))
                    "Publication updated in customer offer preview." else "Permission denied."
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        BusinessMessage(message)
    }
    preview?.let { promotion -> androidx.compose.ui.window.Dialog(onDismissRequest = { preview = null }) {
        Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(RitRadii.card))
            .padding(16.dp).testTag("seller_offer_preview")) {
            RitText("Customer preview · demo", RitType.title)
            Spacer(Modifier.height(12.dp))
            PromotionCard(promotion, PreviewCatalog.sellers.find { it.id == sellerId }?.name ?: "Seller",
                Instant.now(), {}, PreviewCatalog)
            Spacer(Modifier.height(12.dp))
            RitSecondaryButton("Close", { preview = null }, Modifier.fillMaxWidth())
        }
    } }
}

@Composable
fun SellerPromotionEditorScreen(sellerId: String, userId: String, promotionId: String?, repo: SellerBusinessRepository,
    onBack: () -> Unit, onSaved: () -> Unit) {
    val old = DevelopmentPromotions.promotions.find { it.id == promotionId && it.sellerId == sellerId }
    val now = LocalDateTime.now(RIT_TIME_ZONE)
    var title by remember(promotionId) { mutableStateOf(old?.title.orEmpty()) }
    var description by remember(promotionId) { mutableStateOf(old?.description.orEmpty()) }
    var kind by remember(promotionId) { mutableStateOf(old?.kind ?: PromotionKind.FEATURED) }
    var value by remember(promotionId) { mutableStateOf(old?.discountValue?.toString().orEmpty()) }
    var starts by remember(promotionId) { mutableStateOf((old?.startsAt?.atZone(RIT_TIME_ZONE)?.toLocalDateTime() ?: now).format(promotionDateFormat)) }
    var ends by remember(promotionId) { mutableStateOf((old?.endsAt?.atZone(RIT_TIME_ZONE)?.toLocalDateTime() ?: now.plusDays(7)).format(promotionDateFormat)) }
    var published by remember(promotionId) { mutableStateOf(old?.published ?: false) }
    var items by remember(promotionId) { mutableStateOf(old?.itemIds.orEmpty()) }
    var categories by remember(promotionId) { mutableStateOf(old?.categoryIds.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    BusinessPage(if (old == null) "Create promotion" else "Edit promotion", "seller_promotion_editor", onBack) {
        RitText("All offers are demo placements. Publishing only changes local customer previews.", RitType.body)
        Spacer(Modifier.height(12.dp))
        RitTextField(title, { title = it }, "Promotion title", "Required", inputTag = "seller_promotion_title")
        Spacer(Modifier.height(8.dp))
        RitTextField(description, { description = it }, "Description", "Customer-facing details", singleLine = false)
        Spacer(Modifier.height(12.dp))
        RitText("Type", RitType.label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PromotionKind.entries.forEach { candidate ->
                RitChip(candidate.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), kind == candidate,
                    { kind = candidate; value = "" })
            }
        }
        if (kind == PromotionKind.PERCENT_DISCOUNT || kind == PromotionKind.FIXED_DISCOUNT) {
            RitTextField(value, { value = it }, if (kind == PromotionKind.PERCENT_DISCOUNT) "Percent (1–100)" else "Fixed amount ₹",
                "Required", keyboardType = KeyboardType.Number)
            RitText("Discount shown for preview only; accepted quote prices are never changed.", RitType.small, RitThemeTokens.colors.muted)
        }
        Spacer(Modifier.height(10.dp))
        RitText("Related items", RitType.label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PreviewCatalog.items.filter { it.sellerId == sellerId }.forEach { item ->
                RitChip(item.name, item.id in items, { items = if (item.id in items) items - item.id else items + item.id })
            }
        }
        RitText("Related categories", RitType.label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PreviewCatalog.sellers.find { it.id == sellerId }?.categoryIds?.forEach { id ->
                RitChip(PreviewCatalog.categories.find { it.id == id }?.name ?: id, id in categories,
                    { categories = if (id in categories) categories - id else categories + id })
            }
        }
        Spacer(Modifier.height(10.dp))
        RitText("Schedule · Asia/Kolkata", RitType.label)
        RitTextField(starts, { starts = it }, "Starts", "yyyy-MM-dd HH:mm", inputTag = "seller_promotion_starts")
        Spacer(Modifier.height(8.dp))
        RitTextField(ends, { ends = it }, "Ends", "yyyy-MM-dd HH:mm", inputTag = "seller_promotion_ends")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitChip("Draft", !published, { published = false })
            RitChip("Published", published, { published = true })
        }
        BusinessMessage(message)
        RitPrimaryButton("Save promotion", {
            val start = runCatching { LocalDateTime.parse(starts, promotionDateFormat).atZone(RIT_TIME_ZONE).toInstant() }.getOrNull()
            val end = runCatching { LocalDateTime.parse(ends, promotionDateFormat).atZone(RIT_TIME_ZONE).toInstant() }.getOrNull()
            val amount = value.toIntOrNull()
            message = when {
                title.isBlank() -> "Promotion title is required."
                start == null || end == null || !end.isAfter(start) -> "Enter valid dates with end after start."
                kind == PromotionKind.PERCENT_DISCOUNT && amount !in 1..100 -> "Percent must be 1–100."
                kind == PromotionKind.FIXED_DISCOUNT && (amount == null || amount <= 0) -> "Enter a positive fixed amount."
                else -> {
                    val promotion = Promotion(old?.id ?: "${sellerId}-promo-${UUID.randomUUID()}", sellerId,
                        title.trim(), description.trim(), kind, start, end, published, items, categories,
                        discountValue = if (kind == PromotionKind.PERCENT_DISCOUNT || kind == PromotionKind.FIXED_DISCOUNT) amount else null)
                    if (repo.savePromotion(userId, promotion)) { onSaved(); null } else "Permission denied or related items are invalid."
                }
            }
        }, Modifier.fillMaxWidth().testTag("seller_save_promotion"))
    }
}
