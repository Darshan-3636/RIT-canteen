package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.navigation.DevelopmentRole
import edu.msrit.canteen.navigation.DevelopmentTestIdentity
import edu.msrit.canteen.navigation.PresentationAccess
import edu.msrit.canteen.navigation.ProtectedArea
import edu.msrit.canteen.onboarding.ThemeCatalog
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun DeveloperPanelScreen(
    onBack: () -> Unit,
    onWelcome: () -> Unit,
    onLogin: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOtp: () -> Unit,
    onProfile: () -> Unit,
    onMode: () -> Unit,
    onCustomer: () -> Unit,
    onStories: () -> Unit,
    onOrderFood: () -> Unit,
    onSellers: () -> Unit,
    onSellerProfile: () -> Unit,
    onSellerMenu: () -> Unit,
    onFoodDetails: () -> Unit,
    onUnifiedCart: () -> Unit,
    onSeedDemoCarts: () -> Unit,
    onUnifiedOrders: () -> Unit,
    onTemplateCreate: () -> Unit,
    onSearch: () -> Unit,
    onFilters: () -> Unit,
    onFavoritesPopulated: () -> Unit,
    onFavoritesEmpty: () -> Unit,
    onPromotions: () -> Unit,
    onQuickReorderEmpty: () -> Unit,
    onQuickReorderSample: () -> Unit,
    onBulkCart: () -> Unit,
    onBulkMinimumOverride: () -> Unit,
    onBulkPickup: () -> Unit,
    onBulkReview: () -> Unit,
    onBulkOrdersEmpty: () -> Unit,
    onBulkNegotiation: () -> Unit,
    onBulkCompare: () -> Unit,
    onBulkCounter: () -> Unit,
    onBulkFinal: () -> Unit,
    onBulkPayment: () -> Unit,
    onBulkExpired: () -> Unit,
    onSellerOverview: () -> Unit,
    onSellerInbox: () -> Unit,
    onSellerRequest: () -> Unit,
    onSellerCounter: () -> Unit,
    onSellerCompare: () -> Unit,
    onSellerApproval: () -> Unit,
    onSellerSpecial: () -> Unit,
    onSellerSchedule: () -> Unit,
    onSellerHistory: () -> Unit,
    onSellerManagement: () -> Unit,
    onSellerManageMenu: () -> Unit,
    onSellerNewItem: () -> Unit,
    onSellerEditItem: () -> Unit,
    onSellerAvailability: () -> Unit,
    onSellerCategories: () -> Unit,
    onSellerHours: () -> Unit,
    onSellerClosures: () -> Unit,
    onSellerMinimum: () -> Unit,
    onSellerEmployees: () -> Unit,
    onSellerManagerPermissions: () -> Unit,
    onSellerStaffPermissions: () -> Unit,
    onSellerUnauthorized: () -> Unit,
    onSellerPromotions: () -> Unit,
    onSellerSettings: () -> Unit,
    onSellerInsight: (SellerInsightPage, Boolean) -> Unit,
    onAdminPage: (AdminPage) -> Unit,
    onSellerPending: () -> Unit,
    onSellerExpired: () -> Unit,
    onSellerWithdrawn: () -> Unit,
    onSellerPaid: () -> Unit,
    onCustomerDemo: () -> Unit,
    onCustomerPage: (String) -> Unit,
    onSeedCustomerExamples: () -> Unit,
    identities: List<DevelopmentTestIdentity>,
    activeRole: DevelopmentRole?,
    onSelectRole: (DevelopmentRole) -> Unit,
    onReset: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    val active = identities.firstOrNull { it.role == activeRole }
    val canBrowse = PresentationAccess.allows(active?.authorization, ProtectedArea.Customer)
    val canSellerView = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "bulk.view"))
    val canSellerRespond = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "bulk.respond"))
    val canSellerHistory = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "operations.history"))
    val canCatalog = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "catalog.edit"))
    val canAvailability = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "availability.edit"))
    val canHours = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "hours.edit"))
    val canEmployees = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "employees.manage"))
    val canPromotions = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "promotions.manage"))
    val canAnalytics = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "analytics.view"))
    val canReports = PresentationAccess.allows(active?.authorization,
        ProtectedArea.Seller("north-counter", "reports.export"))
    val canAdmin = PresentationAccess.allows(active?.authorization, ProtectedArea.Administrator)
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(24.dp)
        .testTag("debug_testing_hub")) {
        RitText("Developer Testing Hub", RitType.heading)
        Spacer(Modifier.height(8.dp))
        RitText("Debug build only · mock identities are not real authentication or server authorization.", RitType.body, colors.muted)
        Spacer(Modifier.height(16.dp))
        RitText("Mock login", RitType.title)
        RitText(if (active == null) "No mock identity selected" else "Active: ${active.sampleName}", RitType.body, colors.muted,
            Modifier.testTag("debug_active_identity"))
        identities.forEach { identity ->
            PreviewLink("${if (identity.role == activeRole) "✓ " else ""}${identity.label} · ${identity.sampleName}",
                { onSelectRole(identity.role) }, tag = "debug_role_${identity.role.name.lowercase()}")
        }
        if (active != null) {
            Spacer(Modifier.height(8.dp))
            RitText("Simulated access: customer ${if (canBrowse) "allowed" else "denied"} · seller catalog ${if (PresentationAccess.allows(active.authorization, ProtectedArea.Seller("north-counter", "catalog.edit"))) "allowed" else "denied"} · platform admin ${if (PresentationAccess.allows(active.authorization, ProtectedArea.Administrator)) "allowed" else "denied"}.",
                RitType.small, colors.muted)
        }
        PreviewLink("Reset mock login and preview state", onReset, tag = "debug_reset")
        Spacer(Modifier.height(18.dp))
        RitText("Customer screens", RitType.title)
        RitText("Select the Customer mock identity to open these read-only previews.", RitType.small, colors.muted)
        PreviewLink("Customer Home", onCustomer, enabled = canBrowse, tag = "debug_open_home")
        PreviewLink("Order Food", onOrderFood, enabled = canBrowse, tag = "debug_open_order_food")
        PreviewLink("Explore Sellers", onSellers, enabled = canBrowse, tag = "debug_open_sellers")
        PreviewLink("Main Canteen profile", onSellerProfile, enabled = canBrowse, tag = "debug_open_seller_profile")
        PreviewLink("Main Canteen menu", onSellerMenu, enabled = canBrowse, tag = "debug_open_seller_menu")
        PreviewLink("Masala Dosa details", onFoodDetails, enabled = canBrowse, tag = "debug_open_food_details")
        PreviewLink("Cart · Individual and Bulk", onUnifiedCart, tag = "debug_unified_cart")
        PreviewLink("Load populated demo carts · replaces drafts", onSeedDemoCarts, tag = "debug_seed_carts")
        PreviewLink("Orders · Individual and Bulk", onUnifiedOrders, tag = "debug_unified_orders")
        PreviewLink("Create bulk template from scratch", onTemplateCreate, tag = "debug_template_create")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 9 customer account", RitType.title)
        RitText("These links select Demo Customer. Edits and tickets stay on this device.", RitType.small, colors.muted)
        PreviewLink("Customer profile", { onCustomerPage("profile") }, tag = "debug_customer_profile")
        PreviewLink("Edit customer profile", { onCustomerPage("edit") }, tag = "debug_customer_edit")
        PreviewLink("Customer settings", { onCustomerPage("settings") }, tag = "debug_customer_settings")
        PreviewLink("Customer support · empty", { onCustomerPage("support") }, tag = "debug_customer_support")
        PreviewLink("Customer support · sample ticket", onSeedCustomerExamples, tag = "debug_customer_support_sample")
        PreviewLink("Notification inbox", { onCustomerPage("notifications") }, tag = "debug_customer_notifications")
        PreviewLink("Bulk templates", { onCustomerPage("templates") }, tag = "debug_customer_templates")
        PreviewLink("Offline/online-only state", { onCustomerPage("offline") }, tag = "debug_customer_offline")
        PreviewLink("Optional RIT Stories", onStories, tag = "debug_customer_stories")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 4 previews", RitType.title)
        RitText("These links select Demo Customer automatically. Empty Favourites clears that customer's demo saves.",
            RitType.small, colors.muted)
        PreviewLink("Search", onSearch, tag = "debug_open_search")
        PreviewLink("Advanced filters", onFilters, tag = "debug_open_filters")
        PreviewLink("Favourites with sample saves", onFavoritesPopulated, tag = "debug_open_favorites_seeded")
        PreviewLink("Empty Favourites · clear demo saves", onFavoritesEmpty, tag = "debug_open_favorites_empty")
        PreviewLink("Demo seller offers", onPromotions, tag = "debug_open_promotions")
        PreviewLink("Quick Reorder · no history", onQuickReorderEmpty, tag = "debug_open_reorder_empty")
        PreviewLink("Quick Reorder · sample history", onQuickReorderSample, tag = "debug_open_reorder_sample")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 5 bulk previews", RitType.title)
        RitText("Each link selects Demo Customer. Cart and pickup are local drafts; quote screens are separate fictional scenarios. No request or payment is sent.",
            RitType.small, colors.muted)
        PreviewLink("Main Canteen bulk cart · empty", onBulkCart, tag = "debug_bulk_cart")
        PreviewLink("North Counter bulk cart · override minimum", onBulkMinimumOverride, tag = "debug_bulk_override")
        PreviewLink("Pickup scheduling · 8 units", onBulkPickup, tag = "debug_bulk_pickup")
        PreviewLink("Request review · local draft", onBulkReview, tag = "debug_bulk_review")
        PreviewLink("Bulk requests · honest empty state", onBulkOrdersEmpty, tag = "debug_bulk_orders_empty")
        PreviewLink("Negotiation · demo seller counteroffer", onBulkNegotiation, tag = "debug_bulk_negotiation")
        PreviewLink("Compare demo quote revisions", onBulkCompare, tag = "debug_bulk_compare")
        PreviewLink("Draft customer counterproposal", onBulkCounter, tag = "debug_bulk_counter")
        PreviewLink("Final agreed quote · demo", onBulkFinal, tag = "debug_bulk_final")
        PreviewLink("Payment eligibility · blocked", onBulkPayment, tag = "debug_bulk_payment")
        PreviewLink("Expired quote · demo", onBulkExpired, tag = "debug_bulk_expired")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 6 seller operations", RitType.title)
        RitText("Select a North Counter seller role. Staff is view-only in this configurable sample; every action stays on this device.",
            RitType.small, colors.muted)
        PreviewLink("Seller dashboard", onSellerOverview, canSellerView, "debug_seller_dashboard")
        PreviewLink("Incoming requests", onSellerInbox, canSellerView, "debug_seller_inbox")
        PreviewLink("New request details", onSellerRequest, canSellerView, "debug_seller_request")
        PreviewLink("Seller counteroffer", onSellerCounter, canSellerRespond, "debug_seller_counter")
        PreviewLink("Compare revisions", onSellerCompare, canSellerView, "debug_seller_compare")
        PreviewLink("Final seller approval", onSellerApproval, canSellerView, "debug_seller_approval")
        PreviewLink("Outside-hours special request", onSellerSpecial, canSellerView, "debug_seller_special")
        PreviewLink("Upcoming schedule", onSellerSchedule, canSellerView, "debug_seller_schedule")
        PreviewLink("Search and history", onSellerHistory, canSellerHistory, "debug_seller_history")
        PreviewLink("Pending customer response", onSellerPending, canSellerView, "debug_seller_pending")
        PreviewLink("Expired quote", onSellerExpired, canSellerView, "debug_seller_expired")
        PreviewLink("Withdrawn request", onSellerWithdrawn, canSellerView, "debug_seller_withdrawn")
        PreviewLink("Payment-eligible / paid fixture", onSellerPaid, canSellerView, "debug_seller_paid")
        PreviewLink("Customer side of local scenarios", onCustomerDemo, tag = "debug_customer_scenarios")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 7 seller management", RitType.title)
        RitText("Select a seller mock role. Changes update customer previews and reset with demo state.", RitType.small, colors.muted)
        PreviewLink("Seller management", onSellerManagement, canSellerView, "debug_seller_management")
        PreviewLink("Menu management", onSellerManageMenu, canCatalog || canAvailability, "debug_seller_manage_menu")
        PreviewLink("New food item", onSellerNewItem, canCatalog, "debug_seller_new_item")
        PreviewLink("Edit North Counter Lunch", onSellerEditItem, canCatalog, "debug_seller_edit_item")
        PreviewLink("Item availability", onSellerAvailability, canAvailability, "debug_seller_availability")
        PreviewLink("Categories", onSellerCategories, canCatalog, "debug_seller_categories")
        PreviewLink("Working hours", onSellerHours, canHours, "debug_seller_hours")
        PreviewLink("Holiday and closure dates", onSellerClosures, canHours, "debug_seller_closures")
        PreviewLink("Bulk minimum", onSellerMinimum, canHours, "debug_seller_minimum")
        PreviewLink("Team and permissions", onSellerEmployees, canEmployees, "debug_seller_employees")
        PreviewLink("Manager permissions", onSellerManagerPermissions, canEmployees, "debug_seller_manager_permissions")
        PreviewLink("Staff permissions", onSellerStaffPermissions, canEmployees, "debug_seller_staff_permissions")
        PreviewLink("Unauthorized management attempt", onSellerUnauthorized, canSellerView && !canEmployees,
            "debug_seller_unauthorized")
        PreviewLink("Promotions management", onSellerPromotions, canPromotions, "debug_seller_promotions")
        PreviewLink("Business settings", onSellerSettings, canSellerView, "debug_seller_settings")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 8 seller insights & help", RitType.title)
        RitText("Select Owner, or a delegated role where allowed. Sample sales are synthetic and never provider-verified.", RitType.small, colors.muted)
        PreviewLink("Seller analytics · sample", { onSellerInsight(SellerInsightPage.OVERVIEW, true) }, canAnalytics, "debug_seller_analytics")
        PreviewLink("Seller analytics · empty", { onSellerInsight(SellerInsightPage.OVERVIEW, false) }, canAnalytics, "debug_seller_analytics_empty")
        PreviewLink("Sales trends", { onSellerInsight(SellerInsightPage.TRENDS, true) }, canAnalytics, "debug_sales_trends")
        PreviewLink("Products and categories", { onSellerInsight(SellerInsightPage.PRODUCTS, true) }, canAnalytics, "debug_product_analytics")
        PreviewLink("Customer insights", { onSellerInsight(SellerInsightPage.CUSTOMERS, true) }, canAnalytics, "debug_customer_insights")
        PreviewLink("Promotion performance", { onSellerInsight(SellerInsightPage.PROMOTIONS, true) }, canAnalytics, "debug_promotion_analytics")
        PreviewLink("Forecast · illustrative demo", { onSellerInsight(SellerInsightPage.FORECAST, true) }, canAnalytics, "debug_forecast_sample")
        PreviewLink("Forecast · production empty state", { onSellerInsight(SellerInsightPage.FORECAST, false) }, canAnalytics, "debug_forecast")
        PreviewLink("Configure sample report", { onSellerInsight(SellerInsightPage.REPORT_BUILDER, true) }, canReports, "debug_report_builder")
        PreviewLink("Reports & downloads", { onSellerInsight(SellerInsightPage.REPORT_HISTORY, true) }, canReports, "debug_report_history")
        PreviewLink("Seller support", { onSellerInsight(SellerInsightPage.SUPPORT, true) }, canSellerView, "debug_seller_support")
        PreviewLink("Notification preferences", { onSellerInsight(SellerInsightPage.NOTIFICATIONS, true) }, canSellerView, "debug_seller_notifications")
        PreviewLink("Payment configuration status", { onSellerInsight(SellerInsightPage.PAYMENT_STATUS, true) }, canSellerView, "debug_seller_payment_status")
        Spacer(Modifier.height(18.dp))
        RitText("Prompt 8 platform administration", RitType.title)
        RitText("Select Demo Administrator. Actions are resettable local examples, never real account or provider changes.", RitType.small, colors.muted)
        PreviewLink("Admin dashboard", { onAdminPage(AdminPage.DASHBOARD) }, canAdmin, "debug_admin_dashboard")
        PreviewLink("Seller directory", { onAdminPage(AdminPage.SELLERS) }, canAdmin, "debug_admin_sellers")
        PreviewLink("Seller oversight", { onAdminPage(AdminPage.SELLER_DETAIL) }, canAdmin, "debug_admin_seller_detail")
        PreviewLink("Seller setup draft", { onAdminPage(AdminPage.SELLER_SETUP) }, canAdmin, "debug_admin_setup")
        PreviewLink("User management", { onAdminPage(AdminPage.USERS) }, canAdmin, "debug_admin_users")
        PreviewLink("Platform analytics · sample", { onAdminPage(AdminPage.ANALYTICS) }, canAdmin, "debug_admin_analytics")
        PreviewLink("Seller-level analytics", { onAdminPage(AdminPage.SELLER_ANALYTICS) }, canAdmin, "debug_admin_seller_analytics")
        PreviewLink("Support escalations", { onAdminPage(AdminPage.SUPPORT) }, canAdmin, "debug_admin_support")
        PreviewLink("Ticket detail", { onAdminPage(AdminPage.TICKET_DETAIL) }, canAdmin, "debug_admin_ticket")
        PreviewLink("Content moderation", { onAdminPage(AdminPage.MODERATION) }, canAdmin, "debug_admin_moderation")
        PreviewLink("Platform settings", { onAdminPage(AdminPage.SETTINGS) }, canAdmin, "debug_admin_settings")
        PreviewLink("Audit & system health", { onAdminPage(AdminPage.AUDIT) }, canAdmin, "debug_admin_audit")
        Spacer(Modifier.height(18.dp))
        RitText("Other previews", RitType.title)
        PreviewLink("Replay three-slide onboarding", onReplayOnboarding, tag = "debug_replay_onboarding")
        PreviewLink("RIT Stories theme gallery", onStories)
        PreviewLink("OTP preview", onOtp)
        PreviewLink("Profile setup preview", onProfile)
        PreviewLink("Mode selection preview", onMode)
        PreviewLink("Return to Welcome", onWelcome, tag = "debug_return_welcome")
        PreviewLink("Return to Login", onLogin, tag = "debug_return_login")
    }
}

@Composable
private fun PreviewLink(label: String, onClick: () -> Unit, enabled: Boolean = true, tag: String? = null) {
    val colors = RitThemeTokens.colors
    RitText(label, RitType.label, if (enabled) colors.foreground else colors.muted,
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(colors.warmCard).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(horizontal = 18.dp, vertical = 17.dp))
}

@Composable
fun RitStoriesPreviewScreen(onBack: () -> Unit, onTheme: (edu.msrit.canteen.onboarding.ThemeId) -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
        RitText("RIT Stories", RitType.heading)
        Spacer(Modifier.height(5.dp))
        RitText("Optional games and ideas gallery · development artwork", RitType.body, colors.muted)
        Spacer(Modifier.height(12.dp))
        PreviewLink("Back to developer previews", onBack)
        ThemeCatalog.all.forEach { theme ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onTheme(theme.id) }.testTag("stories_${theme.id.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(theme.artwork), theme.description,
                    Modifier.size(width = 80.dp, height = 106.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    RitText(theme.title, RitType.title)
                    RitText(theme.family.name, RitType.small, colors.muted)
                    RitText(if (theme.cleanFullScreen) "Clean artwork" else "Legacy reference · not full-screen ready",
                        RitType.small, colors.muted)
                }
            }
        }
        RitText("Some legacy references retain embedded text or game HUD. They are shown uncropped for review, not cleared for publication.",
            RitType.small, colors.muted)
    }
}

@Composable
fun RitStoryDetailScreen(id: edu.msrit.canteen.onboarding.ThemeId, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    val artwork = ThemeCatalog.get(id)
    val note = edu.msrit.canteen.onboarding.ThemeMicrocopy.lines.firstOrNull {
        it.theme == id && it.context == edu.msrit.canteen.onboarding.CopyContext.Discovery
    }?.text
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(20.dp).testTag("story_detail_${id.name.lowercase()}")) {
        RitText(artwork.title, RitType.heading)
        Spacer(Modifier.height(5.dp))
        RitText("${artwork.family.name} · optional artwork", RitType.small, colors.muted)
        Spacer(Modifier.height(16.dp))
        Image(painterResource(artwork.artwork), artwork.description,
            Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(14.dp))
        RitText(artwork.description, RitType.body)
        if (note != null) RitText(note, RitType.body, colors.accent, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        RitText(if (artwork.cleanFullScreen) "Concept art; publication and licensing review still required."
            else "Legacy reference with embedded UI/text; not production-ready and shown without masking or reconstruction claims.",
            RitType.small, colors.muted)
        Spacer(Modifier.height(18.dp))
        PreviewLink("Back to RIT Stories", onBack)
    }
}
