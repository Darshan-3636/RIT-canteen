package edu.msrit.canteen.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.auth.AuthEntryIntent
import edu.msrit.canteen.auth.DemoAccount
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.onboarding.OnboardingFlow
import edu.msrit.canteen.onboarding.SharedPreferencesOnboardingPreference
import edu.msrit.canteen.ui.screens.CustomerMenuScreen
import edu.msrit.canteen.ui.screens.ChooseSellerScreen
import edu.msrit.canteen.ui.screens.SellerScopedMenuScreen
import edu.msrit.canteen.ui.screens.ExploreSellersScreen
import edu.msrit.canteen.ui.screens.SellerProfileScreen
import edu.msrit.canteen.ui.screens.FoodDetailsScreen
import edu.msrit.canteen.ui.screens.CustomerUnavailableScreen
import edu.msrit.canteen.ui.screens.CustomerSearchScreen
import edu.msrit.canteen.ui.screens.CustomerFavoritesScreen
import edu.msrit.canteen.ui.screens.CustomerPromotionsScreen
import edu.msrit.canteen.ui.screens.CustomerPromotionDetailScreen
import edu.msrit.canteen.ui.screens.CustomerQuickReorderScreen
import edu.msrit.canteen.customer.DevelopmentFavoritesRepository
import edu.msrit.canteen.customer.DevelopmentSampleHistory
import edu.msrit.canteen.customer.BulkDraftSession
import edu.msrit.canteen.customer.BulkDraft
import edu.msrit.canteen.customer.BulkLine
import edu.msrit.canteen.customer.CartMode
import edu.msrit.canteen.customer.IndividualCartSession
import edu.msrit.canteen.customer.DevelopmentIndividualOrders
import edu.msrit.canteen.customer.DevelopmentDemoCheckout
import edu.msrit.canteen.customer.DemoActionResult
import edu.msrit.canteen.customer.DevelopmentBulkPreview
import edu.msrit.canteen.customer.DevelopmentSellerOperations
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.DevelopmentCustomerAccount
import edu.msrit.canteen.customer.CustomerProfile
import edu.msrit.canteen.ui.theme.AppearancePreference
import edu.msrit.canteen.ui.screens.CustomerProfileScreen
import edu.msrit.canteen.ui.screens.CustomerProfileEditScreen
import edu.msrit.canteen.ui.screens.CustomerSettingsScreen
import edu.msrit.canteen.ui.screens.CustomerAppearanceScreen
import edu.msrit.canteen.ui.screens.CustomerPreferenceScreen
import edu.msrit.canteen.ui.screens.CustomerAccountManagementScreen
import edu.msrit.canteen.ui.screens.CustomerNotificationsScreen
import edu.msrit.canteen.ui.screens.CustomerSupportScreen
import edu.msrit.canteen.ui.screens.CustomerTicketFormScreen
import edu.msrit.canteen.ui.screens.CustomerTicketDetailScreen
import edu.msrit.canteen.ui.screens.CustomerTemplatesScreen
import edu.msrit.canteen.ui.screens.CustomerTemplateEditorScreen
import edu.msrit.canteen.ui.screens.CustomerCartScreen
import edu.msrit.canteen.ui.screens.IndividualReviewScreen
import edu.msrit.canteen.ui.screens.CustomerOrdersScreen
import edu.msrit.canteen.ui.screens.IndividualOrderDetailScreen
import edu.msrit.canteen.ui.screens.DemoCheckoutScreen
import edu.msrit.canteen.ui.screens.DemoOrderDetailScreen
import edu.msrit.canteen.ui.screens.SellerDemoIndividualQueue
import edu.msrit.canteen.ui.screens.SellerDemoPickupScreen
import edu.msrit.canteen.ui.screens.SellerDemoOrderDetailScreen
import edu.msrit.canteen.ui.screens.RitConfirmDialog
import edu.msrit.canteen.ui.screens.CustomerOfflineInfoScreen
import edu.msrit.canteen.ui.screens.RitStoryDetailScreen
import edu.msrit.canteen.onboarding.ThemeId
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.ui.screens.SellerDailyOperationsOverview
import edu.msrit.canteen.ui.screens.SellerInboxScreen
import edu.msrit.canteen.ui.screens.SellerDailyRequestDetailPresentation
import edu.msrit.canteen.ui.screens.SellerRevisionCompareScreen
import edu.msrit.canteen.ui.screens.SellerFinalApprovalScreen
import edu.msrit.canteen.ui.screens.SellerSpecialPickupScreen
import edu.msrit.canteen.ui.screens.SellerPickupSchedulePresentation
import edu.msrit.canteen.ui.screens.SellerHistoryScreen
import edu.msrit.canteen.ui.screens.SellerBusinessHomeScreen
import edu.msrit.canteen.ui.screens.SellerMenuManagementScreen
import edu.msrit.canteen.ui.screens.SellerItemEditorScreen
import edu.msrit.canteen.ui.screens.SellerAvailabilityScreen
import edu.msrit.canteen.ui.screens.SellerCategoriesScreen
import edu.msrit.canteen.ui.screens.SellerBusinessSettingsScreen
import edu.msrit.canteen.ui.screens.SellerProfileEditorScreen
import edu.msrit.canteen.ui.screens.SellerHoursScreen
import edu.msrit.canteen.ui.screens.SellerClosuresScreen
import edu.msrit.canteen.ui.screens.SellerBulkMinimumScreen
import edu.msrit.canteen.ui.screens.SellerEmployeesScreen
import edu.msrit.canteen.ui.screens.SellerEmployeeEditorScreen
import edu.msrit.canteen.ui.screens.SellerPromotionsScreen
import edu.msrit.canteen.ui.screens.SellerPromotionEditorScreen
import edu.msrit.canteen.ui.screens.SellerInsightPage
import edu.msrit.canteen.ui.screens.SellerInsightScreen
import edu.msrit.canteen.ui.screens.AdminPage
import edu.msrit.canteen.ui.screens.PlatformAdminScreen
import edu.msrit.canteen.ui.screens.CustomerDemoRequestsScreen
import edu.msrit.canteen.ui.screens.CustomerDemoRequestScreen
import edu.msrit.canteen.ui.screens.DemoQuoteEditorScreen
import edu.msrit.canteen.ui.screens.BulkSellerPickerScreen
import edu.msrit.canteen.ui.screens.BulkCartScreen
import edu.msrit.canteen.ui.screens.BulkPickupScreen
import edu.msrit.canteen.ui.screens.BulkReviewScreen
import edu.msrit.canteen.ui.screens.BulkOrdersScreen
import edu.msrit.canteen.ui.screens.BulkNegotiationScreen
import edu.msrit.canteen.ui.screens.BulkCompareScreen
import edu.msrit.canteen.ui.screens.BulkCounterproposalScreen
import edu.msrit.canteen.ui.screens.BulkFinalQuoteScreen
import edu.msrit.canteen.ui.screens.BulkPaymentEligibilityScreen
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.SellerBottomItem
import edu.msrit.canteen.ui.components.SellerBottomNavigation
import edu.msrit.canteen.ui.screens.EmailEntryScreen
import edu.msrit.canteen.ui.screens.DemoLoginScreen
import edu.msrit.canteen.ui.screens.DemoSignupScreen
import edu.msrit.canteen.ui.screens.DemoRoleAccountScreen
import edu.msrit.canteen.ui.screens.ModeSelectionPreviewScreen
import edu.msrit.canteen.ui.screens.OnboardingScreen
import edu.msrit.canteen.ui.screens.OtpPreviewScreen
import edu.msrit.canteen.ui.screens.ProfileSetupPreviewScreen
import edu.msrit.canteen.ui.screens.SplashScreen
import edu.msrit.canteen.ui.screens.WelcomeScreen
import edu.msrit.canteen.ui.screens.DeveloperPanelScreen
import edu.msrit.canteen.ui.screens.RitStoriesPreviewScreen
import edu.msrit.canteen.ui.theme.RitThemeTokens

internal object Route {
    const val Splash = "startup"
    const val Onboarding = "onboarding"
    const val Welcome = "welcome"
    const val EmailEntry = "email-entry/{intent}"
    const val DemoAccountPage = "debug/demo-account"
    fun emailEntry(intent: AuthEntryIntent) = "email-entry/${intent.routeValue}"
    const val DeveloperPanel = "debug/developer-panel"
    const val StoriesPreview = "debug/rit-stories"
    const val StoryDetail = "debug/rit-stories/{theme}"
    fun storyDetail(theme: ThemeId) = "debug/rit-stories/${theme.name}"
    const val CustomerAccount = "debug/customer/account/{page}/{id}"
    fun customerAccount(page: String, id: String = "none") = "debug/customer/account/$page/$id"
    const val CustomerPreview = "debug/customer-home-preview"
    const val OrderFood = "debug/order-food"
    const val Sellers = "debug/sellers"
    const val SellerProfile = "debug/sellers/{sellerId}"
    const val SellerMenu = "debug/sellers/{sellerId}/menu"
    const val FoodDetails = "debug/items/{itemId}"
    const val CustomerUnavailable = "debug/customer-unavailable/{section}"
    const val Search = "debug/search?categoryId={categoryId}&filters={filters}"
    fun search(categoryId: String? = null, filters: Boolean = false) = "debug/search?categoryId=${categoryId.orEmpty()}&filters=$filters"
    const val Favorites = "debug/favorites"
    const val Promotions = "debug/promotions"
    const val PromotionDetail = "debug/promotions/{promotionId}"
    fun promotionDetail(id: String) = "debug/promotions/$id"
    const val QuickReorderEmpty = "debug/quick-reorder/empty"
    const val QuickReorderSample = "debug/quick-reorder/sample"
    const val Cart = "debug/cart/{mode}"
    fun cart(mode: CartMode = CartMode.INDIVIDUAL) = "debug/cart/${mode.name.lowercase()}"
    const val IndividualReview = "debug/individual/review"
    const val Orders = "debug/orders"
    const val IndividualOrderDetail = "debug/orders/individual/{id}"
    fun individualOrderDetail(id: String) = "debug/orders/individual/$id"
    const val DemoOrderDetail = "debug/demo-orders/{reference}"
    fun demoOrderDetail(reference: String) = "debug/demo-orders/$reference"
    const val DemoCheckout = "debug/demo-orders/{reference}/checkout"
    fun demoCheckout(reference: String) = "debug/demo-orders/$reference/checkout"
    const val BulkSellers = "debug/bulk/sellers"
    const val BulkCart = "debug/bulk/cart"
    const val BulkPickup = "debug/bulk/pickup"
    const val BulkReview = "debug/bulk/review"
    const val BulkOrders = "debug/bulk/orders"
    const val BulkOrdersDemo = "debug/bulk/orders/demo"
    const val BulkNegotiation = "debug/bulk/negotiation"
    const val BulkCompare = "debug/bulk/compare"
    const val BulkCounter = "debug/bulk/counter"
    const val BulkFinal = "debug/bulk/final"
    const val BulkPayment = "debug/bulk/payment"
    const val BulkExpired = "debug/bulk/expired"
    const val SellerOverview = "debug/seller/overview"
    const val SellerInbox = "debug/seller/inbox"
    const val SellerIndividualQueue = "debug/seller/individual"
    const val SellerDemoPickup = "debug/seller/pickup-verify"
    const val SellerDemoOrderDetail = "debug/seller/demo-orders/{reference}"
    fun sellerDemoOrderDetail(reference: String) = "debug/seller/demo-orders/$reference"
    const val SellerDetail = "debug/seller/requests/{reference}"
    const val SellerCounter = "debug/seller/requests/{reference}/counter"
    const val SellerCompare = "debug/seller/requests/{reference}/compare"
    const val SellerApproval = "debug/seller/requests/{reference}/approval"
    const val SellerSpecial = "debug/seller/requests/{reference}/special"
    const val SellerSchedule = "debug/seller/schedule"
    const val SellerHistory = "debug/seller/history"
    const val SellerManagement = "debug/seller/management"
    const val SellerManageMenu = "debug/seller/management/menu"
    const val SellerManageItem = "debug/seller/management/items/{itemId}"
    const val SellerManageAvailability = "debug/seller/management/items/{itemId}/availability"
    const val SellerManageCategories = "debug/seller/management/categories"
    const val SellerManageSettings = "debug/seller/management/settings"
    const val SellerManageProfile = "debug/seller/management/profile"
    const val SellerManageHours = "debug/seller/management/hours"
    const val SellerManageClosures = "debug/seller/management/closures"
    const val SellerManageMinimum = "debug/seller/management/minimum"
    const val SellerManageEmployees = "debug/seller/management/employees"
    const val SellerManageEmployee = "debug/seller/management/employees/{employeeId}"
    const val SellerManagePromotions = "debug/seller/management/promotions"
    const val SellerManagePromotion = "debug/seller/management/promotions/{promotionId}"
    const val SellerInsights = "debug/seller/insights/{page}/{id}?sample={sample}"
    fun sellerInsight(page: SellerInsightPage, id: String = "none", sample: Boolean = true) =
        "debug/seller/insights/${page.name}/$id?sample=$sample"
    const val Admin = "debug/admin/{page}/{id}?sample={sample}"
    fun admin(page: AdminPage, id: String = "none", sample: Boolean = true) =
        "debug/admin/${page.name}/$id?sample=$sample"
    fun manageItem(id: String?) = "debug/seller/management/items/${id ?: "new"}"
    fun manageAvailability(id: String) = "debug/seller/management/items/$id/availability"
    fun manageEmployee(id: String?) = "debug/seller/management/employees/${id ?: "new"}"
    fun managePromotion(id: String?) = "debug/seller/management/promotions/${id ?: "new"}"
    const val CustomerDemoList = "debug/customer/demo-requests"
    const val CustomerDemoDetail = "debug/customer/demo-requests/{reference}"
    const val CustomerDemoCounter = "debug/customer/demo-requests/{reference}/counter"
    const val CustomerDemoCompare = "debug/customer/demo-requests/{reference}/compare"
    fun sellerDetail(reference: String) = "debug/seller/requests/$reference"
    fun sellerCounter(reference: String) = "debug/seller/requests/$reference/counter"
    fun sellerCompare(reference: String) = "debug/seller/requests/$reference/compare"
    fun sellerApproval(reference: String) = "debug/seller/requests/$reference/approval"
    fun sellerSpecial(reference: String) = "debug/seller/requests/$reference/special"
    fun customerDemoDetail(reference: String) = "debug/customer/demo-requests/$reference"
    fun customerDemoCounter(reference: String) = "debug/customer/demo-requests/$reference/counter"
    fun customerDemoCompare(reference: String) = "debug/customer/demo-requests/$reference/compare"
    fun sellerProfile(id: String) = "debug/sellers/$id"
    fun sellerMenu(id: String) = "debug/sellers/$id/menu"
    fun foodDetails(id: String) = "debug/items/$id"
    fun unavailable(section: String) = "debug/customer-unavailable/$section"
    const val OtpPreview = "debug/otp-preview"
    const val ProfilePreview = "debug/profile-preview"
    const val ModePreview = "debug/mode-preview"

    val debugOnly = setOf(DeveloperPanel, StoriesPreview, StoryDetail, CustomerAccount, CustomerPreview, OrderFood, Sellers, SellerProfile, SellerMenu,
        FoodDetails, CustomerUnavailable, Search, Favorites, Promotions, PromotionDetail, QuickReorderEmpty,
        QuickReorderSample, Cart, IndividualReview, Orders, IndividualOrderDetail, DemoOrderDetail, DemoCheckout,
        BulkSellers, BulkCart, BulkPickup, BulkReview, BulkOrders, BulkOrdersDemo,
        BulkNegotiation, BulkCompare, BulkCounter, BulkFinal, BulkPayment, BulkExpired,
        SellerOverview, SellerInbox, SellerIndividualQueue, SellerDemoPickup, SellerDemoOrderDetail,
        SellerDetail, SellerCounter, SellerCompare, SellerApproval,
        SellerSpecial, SellerSchedule, SellerHistory, CustomerDemoList, CustomerDemoDetail,
        CustomerDemoCounter, CustomerDemoCompare,
        SellerManagement, SellerManageMenu, SellerManageItem, SellerManageAvailability,
        SellerManageCategories, SellerManageSettings, SellerManageProfile, SellerManageHours,
        SellerManageClosures, SellerManageMinimum, SellerManageEmployees, SellerManageEmployee,
        SellerManagePromotions, SellerManagePromotion, SellerInsights, Admin,
        OtpPreview, ProfilePreview, ModePreview, DemoAccountPage)
}

@Composable
fun RitAppNavigation(startInDeveloperHub: Boolean = false, appearance: AppearancePreference) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val onboarding = remember(context) { OnboardingFlow(SharedPreferencesOnboardingPreference(context)) }
    val demoFavorites = remember(context) { DevelopmentFavoritesRepository(context) }
    val customerAccount = remember { DevelopmentCustomerAccount }
    remember(context) {
        if (BuildConfig.DEBUG) {
            DevelopmentDemoAccounts.initialize(context)
            DevelopmentCustomerAccount.initialize(context)
            DevelopmentDemoAccounts.activeAccount?.let { account ->
                if (account.role == DevelopmentRole.Customer) {
                    DevelopmentCustomerAccount.activate(account.id, CustomerProfile(account.name, account.email,
                        requireNotNull(account.affiliation), account.department))
                    DevelopmentIndividualOrders.activate(account.id)
                } else { DevelopmentCustomerAccount.deactivate(); DevelopmentIndividualOrders.deactivate() }
            }
        }
        true
    }
    val bulkDraft = remember { BulkDraftSession() }
    val individualCart = remember { IndividualCartSession() }
    var selectedCustomerSellerId by rememberSaveable { mutableStateOf<String?>(null) }
    val cartCount = individualCart.draft.totalUnits + bulkDraft.draft.lines.sumOf { it.quantity }
    val demoCustomerId = DevelopmentPreviewAccess.activeIdentity?.authorization?.takeIf {
        AppMode.Customer in it.modes }?.userId.orEmpty()
    fun homeFor(account: DemoAccount): String = when (account.role) {
        DevelopmentRole.Customer -> Route.CustomerPreview
        DevelopmentRole.PlatformAdministrator -> Route.admin(AdminPage.DASHBOARD)
        else -> Route.SellerOverview
    }
    fun finishDemoSignIn(account: DemoAccount) {
        DevelopmentPreviewAccess.clearHubSelection()
        if (account.role == DevelopmentRole.Customer) {
            DevelopmentCustomerAccount.activate(account.id, CustomerProfile(account.name, account.email,
                requireNotNull(account.affiliation), account.department))
            DevelopmentIndividualOrders.activate(account.id)
        } else { DevelopmentCustomerAccount.deactivate(); DevelopmentIndividualOrders.deactivate() }
        individualCart.clear(); bulkDraft.clear(); selectedCustomerSellerId = null
        navController.navigate(homeFor(account)) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    fun signOutDemo() {
        DevelopmentDemoAccounts.signOut(); DevelopmentPreviewAccess.clearHubSelection()
        DevelopmentCustomerAccount.deactivate(); DevelopmentIndividualOrders.deactivate()
        individualCart.clear(); bulkDraft.clear(); selectedCustomerSellerId = null
        navController.navigate(Route.Welcome) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    fun returnToAuthorizedHome() {
        val account = DevelopmentDemoAccounts.activeAccount
        val destination = account?.let(::homeFor) ?: Route.Welcome
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    fun openAsDemoCustomer(route: String) {
        DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
        navController.navigate(route)
    }
    fun activeSellerId(): String? = DevelopmentPreviewAccess.activeIdentity?.authorization?.modes?.firstNotNullOfOrNull {
        when (it) {
            is AppMode.SellerOwner -> it.sellerId
            is AppMode.SellerManager -> it.sellerId
            is AppMode.OperationalStaff -> it.sellerId
            else -> null
        }
    }
    fun scopedSellerRecord(reference: String): BulkRequestRecord? = DevelopmentSellerOperations.records.find {
        it.reference == reference && it.sellerId == activeSellerId()
    }
    fun openCustomerBottom(item: RitBottomItem) {
        if (item == RitBottomItem.Cart && navController.currentDestination?.route == Route.Cart) return
        val destination = when (item) {
            RitBottomItem.Home -> Route.CustomerPreview
            RitBottomItem.Cart -> Route.cart()
            RitBottomItem.Favourites -> Route.Favorites
            RitBottomItem.Orders -> Route.Orders
            RitBottomItem.Profile -> Route.customerAccount("profile")
        }
        navController.navigate(destination) {
            popUpTo(Route.CustomerPreview)
            launchSingleTop = true
        }
    }
    fun openSellerBottom(item: SellerBottomItem) {
        val destination = when (item) {
            SellerBottomItem.Dashboard -> Route.SellerOverview
            SellerBottomItem.Orders -> Route.SellerInbox
            SellerBottomItem.Menu -> Route.SellerManageMenu
            SellerBottomItem.Insights -> Route.sellerInsight(SellerInsightPage.OVERVIEW)
            SellerBottomItem.Account -> Route.DemoAccountPage
        }
        if (item == SellerBottomItem.Insights && activeSellerId()?.let {
            !DevelopmentPreviewAccess.allows(ProtectedArea.Seller(it, "analytics.view")) } == true) return
        navController.navigate(destination) {
            popUpTo(Route.SellerOverview)
            launchSingleTop = true
        }
    }
    NavHost(navController = navController,
        startDestination = if (BuildConfig.DEBUG && startInDeveloperHub) Route.DeveloperPanel else Route.Splash) {
        composable(Route.Splash) {
            InsetScreen {
                SplashScreen(
                    onStartupComplete = {
                        navController.navigate(DevelopmentDemoAccounts.activeAccount?.takeIf { BuildConfig.DEBUG }
                            ?.let(::homeFor) ?: if (onboarding.shouldShowOnboarding()) Route.Onboarding else Route.Welcome) {
                            popUpTo(Route.Splash) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(Route.Onboarding) {
            OnboardingScreen(onFinish = {
                if (onboarding.finish()) {
                    navController.navigate(Route.Welcome) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            })
        }
        composable(Route.Welcome) {
            InsetScreen {
                WelcomeScreen(
                    onGetStarted = { navController.navigate(Route.emailEntry(AuthEntryIntent.GetStarted)) },
                    onLogin = { navController.navigate(Route.emailEntry(AuthEntryIntent.Login)) },
                )
            }
        }
        composable(Route.EmailEntry, arguments = listOf(navArgument("intent") { type = NavType.StringType })) { entry ->
            val entryIntent = AuthEntryIntent.fromRoute(entry.arguments?.getString("intent"))
            InsetScreen {
                if (BuildConfig.DEBUG) {
                    if (entryIntent == AuthEntryIntent.Login) DemoLoginScreen(
                        onBack = { navController.popBackStack() },
                        onCreateAccount = { navController.navigate(Route.emailEntry(AuthEntryIntent.GetStarted)) },
                        onAuthenticated = ::finishDemoSignIn)
                    else DemoSignupScreen(onBack = { navController.popBackStack() },
                        onAuthenticated = ::finishDemoSignIn)
                } else EmailEntryScreen(entryIntent = entryIntent, onBack = { navController.popBackStack() })
            }
        }
        if (BuildConfig.DEBUG) {
            composable(Route.DeveloperPanel) {
                InsetScreen {
                    DeveloperPanelScreen(
                        onBack = { if (!navController.popBackStack()) navController.navigate(Route.Welcome) },
                        onWelcome = { navController.navigate(Route.Welcome) },
                        onLogin = { navController.navigate(Route.emailEntry(AuthEntryIntent.Login)) },
                        onReplayOnboarding = { navController.navigate(Route.Onboarding) },
                        onOtp = { navController.navigate(Route.OtpPreview) },
                        onProfile = { navController.navigate(Route.ProfilePreview) },
                        onMode = { navController.navigate(Route.ModePreview) },
                        onCustomer = { navController.navigate(Route.CustomerPreview) },
                        onStories = { openAsDemoCustomer(Route.StoriesPreview) },
                        onCustomerPage = { openAsDemoCustomer(Route.customerAccount(it)) },
                        onSeedCustomerExamples = { DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
                            DevelopmentCustomerAccount.seedExamples(); navController.navigate(Route.customerAccount("support")) },
                        onOrderFood = { navController.navigate(Route.OrderFood) },
                        onSellers = { navController.navigate(Route.Sellers) },
                        onSellerProfile = { navController.navigate(Route.sellerProfile(edu.msrit.canteen.customer.PreviewCatalog.MAIN_ID)) },
                        onSellerMenu = {
                            selectedCustomerSellerId = PreviewCatalog.MAIN_ID
                            navController.navigate(Route.sellerMenu(PreviewCatalog.MAIN_ID))
                        },
                        onFoodDetails = { navController.navigate(Route.foodDetails("masala-dosa")) },
                        onUnifiedCart = { openAsDemoCustomer(Route.cart()) },
                        onSeedDemoCarts = {
                            DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
                            individualCart.clear(); bulkDraft.clear()
                            PreviewCatalog.items.firstOrNull { it.id == "masala-dosa" }?.let { individualCart.setQuantity(it, 2) }
                            PreviewCatalog.items.firstOrNull { it.id == "filter-coffee" }?.let { individualCart.setQuantity(it, 1) }
                            bulkDraft.selectSeller("north-counter")
                            PreviewCatalog.items.firstOrNull { it.id == "north-lunch" }?.let { bulkDraft.setQuantity(it, 6) }
                            PreviewCatalog.items.firstOrNull { it.id == "north-snack" }?.let { bulkDraft.setQuantity(it, 4) }
                            if (customerAccount.templates.none { it.name == "Club lunch sample" })
                                customerAccount.saveTemplate("Club lunch sample", BulkDraft("north-counter",
                                    listOf(BulkLine("north-lunch", 6), BulkLine("north-snack", 4))), "debug-customer")
                            openAsDemoCustomer(Route.cart())
                        },
                        onUnifiedOrders = { openAsDemoCustomer(Route.Orders) },
                        onTemplateCreate = { openAsDemoCustomer(Route.customerAccount("template-new")) },
                        onSearch = { openAsDemoCustomer(Route.search()) },
                        onFilters = { openAsDemoCustomer(Route.search(filters = true)) },
                        onFavoritesPopulated = {
                            demoFavorites.seedSample("debug-customer")
                            openAsDemoCustomer(Route.Favorites)
                        },
                        onFavoritesEmpty = {
                            demoFavorites.clearCustomer("debug-customer")
                            openAsDemoCustomer(Route.Favorites)
                        },
                        onPromotions = { selectedCustomerSellerId = null; openAsDemoCustomer(Route.Promotions) },
                        onQuickReorderEmpty = { openAsDemoCustomer(Route.QuickReorderEmpty) },
                        onQuickReorderSample = { openAsDemoCustomer(Route.QuickReorderSample) },
                        onBulkCart = {
                            bulkDraft.clear(); bulkDraft.selectSeller(PreviewCatalog.MAIN_ID)
                            openAsDemoCustomer(Route.BulkCart)
                        },
                        onBulkMinimumOverride = {
                            bulkDraft.clear(); bulkDraft.selectSeller("north-counter")
                            openAsDemoCustomer(Route.BulkCart)
                        },
                        onBulkPickup = {
                            bulkDraft.clear(); bulkDraft.selectSeller(PreviewCatalog.MAIN_ID)
                            bulkDraft.setQuantity(PreviewCatalog.items.first { it.id == "masala-dosa" }, 3)
                            bulkDraft.setQuantity(PreviewCatalog.items.first { it.id == "water-bottle" }, 5)
                            openAsDemoCustomer(Route.BulkPickup)
                        },
                        onBulkReview = {
                            bulkDraft.clear(); bulkDraft.selectSeller(PreviewCatalog.MAIN_ID)
                            bulkDraft.setQuantity(PreviewCatalog.items.first { it.id == "masala-dosa" }, 3)
                            bulkDraft.setQuantity(PreviewCatalog.items.first { it.id == "water-bottle" }, 5)
                            bulkDraft.setPickup(DevelopmentBulkPreview.sample(QuoteStage.SUBMITTED)
                                ?.revisions?.firstOrNull()?.pickup)
                            openAsDemoCustomer(Route.BulkReview)
                        },
                        onBulkOrdersEmpty = { openAsDemoCustomer(Route.BulkOrders) },
                        onBulkNegotiation = { openAsDemoCustomer(Route.BulkNegotiation) },
                        onBulkCompare = { openAsDemoCustomer(Route.BulkCompare) },
                        onBulkCounter = { openAsDemoCustomer(Route.BulkCounter) },
                        onBulkFinal = { openAsDemoCustomer(Route.BulkFinal) },
                        onBulkPayment = { openAsDemoCustomer(Route.BulkPayment) },
                        onBulkExpired = { openAsDemoCustomer(Route.BulkExpired) },
                        onSellerOverview = { navController.navigate(Route.SellerOverview) },
                        onSellerInbox = { navController.navigate(Route.SellerInbox) },
                        onSellerRequest = { navController.navigate(Route.sellerDetail("DEMO-NC-NEW")) },
                        onSellerCounter = { navController.navigate(Route.sellerCounter("DEMO-NC-NEW")) },
                        onSellerCompare = { navController.navigate(Route.sellerCompare("DEMO-NC-CUSTOMER")) },
                        onSellerApproval = { navController.navigate(Route.sellerApproval("DEMO-NC-NEW")) },
                        onSellerSpecial = { navController.navigate(Route.sellerSpecial("DEMO-NC-SPECIAL")) },
                        onSellerSchedule = { navController.navigate(Route.SellerSchedule) },
                        onSellerHistory = { navController.navigate(Route.SellerHistory) },
                        onSellerManagement = { navController.navigate(Route.SellerManagement) },
                        onSellerManageMenu = { navController.navigate(Route.SellerManageMenu) },
                        onSellerNewItem = { navController.navigate(Route.manageItem(null)) },
                        onSellerEditItem = { navController.navigate(Route.manageItem("north-lunch")) },
                        onSellerAvailability = { navController.navigate(Route.manageAvailability("north-lunch")) },
                        onSellerCategories = { navController.navigate(Route.SellerManageCategories) },
                        onSellerHours = { navController.navigate(Route.SellerManageHours) },
                        onSellerClosures = { navController.navigate(Route.SellerManageClosures) },
                        onSellerMinimum = { navController.navigate(Route.SellerManageMinimum) },
                        onSellerEmployees = { navController.navigate(Route.SellerManageEmployees) },
                        onSellerManagerPermissions = { navController.navigate(Route.manageEmployee("debug-manager")) },
                        onSellerStaffPermissions = { navController.navigate(Route.manageEmployee("debug-staff")) },
                        onSellerUnauthorized = { navController.navigate(Route.manageEmployee("debug-manager")) },
                        onSellerPromotions = { navController.navigate(Route.SellerManagePromotions) },
                        onSellerSettings = { navController.navigate(Route.SellerManageSettings) },
                        onSellerInsight = { page, sample -> navController.navigate(Route.sellerInsight(page, sample = sample)) },
                        onAdminPage = { page -> navController.navigate(Route.admin(page, when (page) {
                            AdminPage.SELLER_DETAIL, AdminPage.SELLER_ANALYTICS -> "north-counter"
                            AdminPage.TICKET_DETAIL -> "SAMPLE-T-103"
                            else -> "none"
                        })) },
                        onSellerPending = { navController.navigate(Route.sellerDetail("DEMO-NC-WAITING")) },
                        onSellerExpired = { navController.navigate(Route.sellerDetail("DEMO-NC-EXPIRED")) },
                        onSellerWithdrawn = { navController.navigate(Route.sellerDetail("DEMO-NC-WITHDRAWN")) },
                        onSellerPaid = { navController.navigate(Route.sellerDetail("DEMO-NC-PAID")) },
                        onCustomerDemo = { openAsDemoCustomer(Route.CustomerDemoList) },
                        identities = DevelopmentPreviewAccess.identities.map { if (it.role == DevelopmentPreviewAccess.activeRole)
                            DevelopmentPreviewAccess.activeIdentity ?: it else it },
                        activeRole = DevelopmentPreviewAccess.activeRole,
                        onSelectRole = DevelopmentPreviewAccess::select,
                        onReset = {
                            demoFavorites.resetAll(); bulkDraft.clear(); individualCart.clear(); DevelopmentIndividualOrders.reset(); DevelopmentSellerOperations.reset(); DevelopmentDemoCheckout.reset()
                            DevelopmentSellerBusiness.reset(); DevelopmentInsights.reset(); customerAccount.reset()
                            selectedCustomerSellerId = null
                            DevelopmentPreviewAccess.reset()
                        },
                    )
                }
            }
            composable(Route.DemoAccountPage) {
                val account = DevelopmentDemoAccounts.activeAccount
                InsetScreen {
                    if (account == null || account.role == DevelopmentRole.Customer) {
                        CustomerUnavailableScreen("account", { navController.navigate(Route.Welcome) {
                            popUpTo(navController.graph.id) { inclusive = false }
                        } })
                    } else {
                        val accountContent: @Composable () -> Unit = { DemoRoleAccountScreen(account, appearance,
                        onBack = { navController.popBackStack() },
                        onDashboard = { navController.navigate(homeFor(account)) { launchSingleTop = true } },
                        onSettings = { navController.navigate(if (account.role == DevelopmentRole.PlatformAdministrator)
                            Route.admin(AdminPage.SETTINGS) else Route.SellerManageSettings) },
                        onSupport = { navController.navigate(if (account.role == DevelopmentRole.PlatformAdministrator)
                            Route.admin(AdminPage.SUPPORT) else Route.sellerInsight(SellerInsightPage.SUPPORT)) },
                        onLogout = ::signOutDemo) }
                        if (account.role == DevelopmentRole.PlatformAdministrator) accountContent()
                        else SellerTabFrame(SellerBottomItem.Account, ::openSellerBottom, accountContent)
                    }
                }
            }
            composable(Route.StoriesPreview) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    RitStoriesPreviewScreen(onBack = { navController.popBackStack() },
                        onTheme = { navController.navigate(Route.storyDetail(it)) })
                } }
            }
            composable(Route.StoryDetail, arguments = listOf(navArgument("theme") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val theme = ThemeId.entries.find { it.name == entry.arguments?.getString("theme") }
                    if (theme == null) CustomerUnavailableScreen("story", { navController.popBackStack() })
                    else RitStoryDetailScreen(theme, { navController.popBackStack() })
                } }
            }
            composable(Route.CustomerAccount, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val page = entry.arguments?.getString("page").orEmpty()
                    val id = entry.arguments?.getString("id").orEmpty()
                    val userId = DevelopmentPreviewAccess.activeIdentity?.authorization?.userId
                    val back: () -> Unit = { navController.popBackStack() }
                    val go: (String) -> Unit = { navController.navigate(Route.customerAccount(it)) }
                    when (page) {
                        "profile" -> CustomerProfileScreen(customerAccount, back, ::openCustomerBottom,
                            { go("edit") }, { navController.navigate(Route.Favorites) },
                            { navController.navigate(Route.QuickReorderEmpty) },
                            { navController.navigate(Route.Orders) }, { go("templates") }, { go("support") },
                            { go("settings") }, { navController.navigate(Route.StoriesPreview) }, { go("notifications") },
                            cartCount = cartCount, onAccount = { go("account") })
                        "edit" -> CustomerProfileEditScreen(customerAccount, userId, back)
                        "settings" -> CustomerSettingsScreen(back, { go("appearance") }, { go("preferences") },
                            { go("privacy") }, { go("account") }, { go("support") })
                        "appearance" -> CustomerAppearanceScreen(appearance, back)
                        "preferences" -> CustomerPreferenceScreen(customerAccount, userId, false, back)
                        "privacy" -> CustomerPreferenceScreen(customerAccount, userId, true, back)
                        "account" -> CustomerAccountManagementScreen(customerAccount.profile, back, ::signOutDemo)
                        "notifications" -> CustomerNotificationsScreen(customerAccount, back)
                        "support" -> CustomerSupportScreen(customerAccount, back, { go("ticket-new") },
                            { navController.navigate(Route.customerAccount("ticket", it)) })
                        "ticket-new" -> CustomerTicketFormScreen(customerAccount, userId, back,
                            { navController.navigate(Route.customerAccount("ticket", it)) })
                        "ticket" -> CustomerTicketDetailScreen(customerAccount.tickets.find { it.id == id },
                            customerAccount, userId, back)
                        "templates" -> CustomerTemplatesScreen(customerAccount, userId, bulkDraft, back,
                            { navController.navigate(Route.cart(CartMode.BULK)) },
                            { go("template-new") },
                            { navController.navigate(Route.customerAccount("template-edit", it)) })
                        "template-new" -> CustomerTemplateEditorScreen(customerAccount, userId, null, back,
                            { navController.popBackStack() })
                        "template-edit" -> CustomerTemplateEditorScreen(customerAccount, userId, id, back,
                            { navController.popBackStack() })
                        "offline" -> CustomerOfflineInfoScreen(back)
                        else -> CustomerUnavailableScreen("access", back)
                    }
                } }
            }
            composable(Route.OtpPreview) {
                InsetScreen {
                    OtpPreviewScreen(
                        onBack = { navController.popBackStack() },
                        onProfilePreview = { navController.navigate(Route.ProfilePreview) },
                    )
                }
            }
            composable(Route.ProfilePreview) {
                InsetScreen {
                    ProfileSetupPreviewScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Route.ModePreview) {
                InsetScreen {
                    ModeSelectionPreviewScreen(
                        identity = null,
                        onBack = { navController.popBackStack() },
                        onCustomer = { navController.navigate(if (DevelopmentPreviewAccess.allows(ProtectedArea.Customer)) Route.CustomerPreview else Route.DeveloperPanel) },
                    )
                }
            }
            composable(Route.CustomerPreview) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    ChooseSellerScreen(
                        onSellerChosen = { sellerId ->
                            selectedCustomerSellerId = sellerId
                            navController.navigate(Route.sellerMenu(sellerId))
                        },
                        onBack = null,
                        onBottom = ::openCustomerBottom,
                        cartCount = cartCount,
                        favorites = demoFavorites,
                        customerId = demoCustomerId,
                    )
                } }
            }
            composable(Route.OrderFood) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerMenuScreen(edu.msrit.canteen.customer.PreviewCatalog.MAIN_ID, preparedOnly = true,
                        onBack = { navController.popBackStack() }, onFood = { navController.navigate(Route.foodDetails(it)) },
                        favorites = demoFavorites, customerId = demoCustomerId)
                } }
            }
            composable(Route.Sellers) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) { ExploreSellersScreen(
                    onBack = { navController.popBackStack() }, onSeller = { navController.navigate(Route.sellerProfile(it)) },
                    onBottom = { item ->
                        if (item == RitBottomItem.Home) {
                            if (!navController.popBackStack(Route.CustomerPreview, false)) navController.navigate(Route.CustomerPreview)
                        } else openCustomerBottom(item)
                    },
                    favorites = demoFavorites, customerId = demoCustomerId, cartCount = cartCount,
                ) } }
            }
            composable(Route.SellerProfile, arguments = listOf(navArgument("sellerId") { type = NavType.StringType })) { entry ->
                var pendingSeller by remember { mutableStateOf<String?>(null) }
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) { SellerProfileScreen(entry.arguments?.getString("sellerId").orEmpty(),
                    onBack = { navController.popBackStack() }, onMenu = {
                        selectedCustomerSellerId = it
                        navController.navigate(Route.sellerMenu(it))
                    },
                    onFood = { navController.navigate(Route.foodDetails(it)) },
                    onBulk = { sellerId ->
                        if (bulkDraft.selectSeller(sellerId)) navController.navigate(Route.BulkCart)
                        else pendingSeller = sellerId
                    },
                    favorites = demoFavorites, customerId = demoCustomerId) } }
                pendingSeller?.let { target -> RitConfirmDialog("Switch seller?",
                    "Your bulk cart contains items from another seller. Clear only the bulk cart to start with this seller?",
                    { pendingSeller = null }, {
                        bulkDraft.clear(); bulkDraft.selectSeller(target); pendingSeller = null
                        navController.navigate(Route.BulkCart)
                    }) }
            }
            composable(Route.SellerMenu, arguments = listOf(navArgument("sellerId") { type = NavType.StringType })) { entry ->
                val sellerId = entry.arguments?.getString("sellerId").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    SellerScopedMenuScreen(sellerId,
                        onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        favorites = demoFavorites, customerId = demoCustomerId,
                        onChooseSeller = {
                            if (!navController.popBackStack(Route.CustomerPreview, false)) navController.navigate(Route.CustomerPreview)
                        },
                        onOffers = {
                            selectedCustomerSellerId = sellerId
                            navController.navigate(Route.Promotions)
                        },
                        onBottom = ::openCustomerBottom, cartCount = cartCount)
                } }
            }
            composable(Route.FoodDetails, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                var pendingBulkItem by remember { mutableStateOf<String?>(null) }
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    FoodDetailsScreen(entry.arguments?.getString("itemId").orEmpty(), onBack = { navController.popBackStack() },
                        onAddIndividual = { itemId -> PreviewCatalog.items.firstOrNull { it.id == itemId }?.let(individualCart::add) },
                        onViewCart = { navController.navigate(Route.cart()) },
                        onAddBulk = { itemId ->
                            val item = PreviewCatalog.items.firstOrNull { it.id == itemId }
                            if (item != null) {
                                if (bulkDraft.selectSeller(item.sellerId)) {
                                    val existing = bulkDraft.draft.lines.firstOrNull { it.itemId == item.id }?.quantity ?: 0
                                    bulkDraft.setQuantity(item, existing + 1)
                                    navController.navigate(Route.cart(CartMode.BULK))
                                } else pendingBulkItem = item.id
                            }
                        },
                        favorites = demoFavorites, customerId = demoCustomerId) } }
                pendingBulkItem?.let { itemId -> RitConfirmDialog("Switch seller?",
                    "This food belongs to a different seller. Clear only your bulk cart to start with this seller?",
                    { pendingBulkItem = null }, {
                        val item = PreviewCatalog.items.find { it.id == itemId }
                        if (item != null) {
                            bulkDraft.clear(); bulkDraft.selectSeller(item.sellerId); bulkDraft.setQuantity(item, 1)
                            navController.navigate(Route.cart(CartMode.BULK))
                        }
                        pendingBulkItem = null
                    }) }
            }
            composable(Route.Search, arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
                navArgument("filters") { type = NavType.BoolType; defaultValue = false },
            )) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerSearchScreen(
                        initialCategoryId = entry.arguments?.getString("categoryId")?.takeIf(String::isNotEmpty),
                        initiallyShowFilters = entry.arguments?.getBoolean("filters") == true,
                        onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        favorites = demoFavorites, customerId = demoCustomerId,
                    )
                } }
            }
            composable(Route.Favorites) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerFavoritesScreen(demoCustomerId, demoFavorites,
                        onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        onBottom = ::openCustomerBottom, cartCount = cartCount)
                } }
            }
            composable(Route.Promotions) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerPromotionsScreen(onBack = { navController.popBackStack() },
                        onPromotion = { navController.navigate(Route.promotionDetail(it)) },
                        sellerId = selectedCustomerSellerId)
                } }
            }
            composable(Route.PromotionDetail, arguments = listOf(navArgument("promotionId") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerPromotionDetailScreen(entry.arguments?.getString("promotionId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        onFood = { navController.navigate(Route.foodDetails(it)) })
                } }
            }
            composable(Route.QuickReorderEmpty) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerQuickReorderScreen(onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) })
                } }
            }
            composable(Route.QuickReorderSample) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerQuickReorderScreen(onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        history = DevelopmentSampleHistory)
                } }
            }
            composable(Route.Cart, arguments = listOf(navArgument("mode") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerCartScreen(individualCart, bulkDraft, DevelopmentBulkPreview,
                        if (entry.arguments?.getString("mode") == "bulk") CartMode.BULK else CartMode.INDIVIDUAL,
                        onBack = { navController.popBackStack() },
                        onBrowse = { navController.navigate(Route.OrderFood) },
                        onBulkItems = { navController.navigate(Route.BulkCart) },
                        onChooseSeller = { navController.navigate(Route.BulkSellers) },
                        onIndividualReview = { navController.navigate(Route.IndividualReview) },
                        onBulkPickup = { navController.navigate(Route.BulkPickup) },
                        onBottom = ::openCustomerBottom, cartCount = cartCount)
                } }
            }
            composable(Route.IndividualReview) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    IndividualReviewScreen(individualCart, { navController.popBackStack() }, {
                        val (result, reference) = DevelopmentDemoCheckout.submitIndividual(individualCart.draft,
                            DevelopmentPreviewAccess.activeIdentity?.authorization)
                        if (result == DemoActionResult.APPLIED && reference != null) {
                            individualCart.clear(); navController.navigate(Route.demoOrderDetail(reference))
                        }
                    })
                } }
            }
            composable(Route.Orders) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerOrdersScreen(DevelopmentIndividualOrders.records, DevelopmentSellerOperations.records,
                        { navController.popBackStack() },
                        { navController.navigate(Route.individualOrderDetail(it)) },
                        { navController.navigate(Route.customerDemoDetail(it)) },
                        { navController.navigate(Route.cart()) }, ::openCustomerBottom, cartCount,
                        customerUserId = demoCustomerId,
                        demoOrders = DevelopmentDemoCheckout.orders,
                        onDemoOrder = { navController.navigate(Route.demoOrderDetail(it)) })
                } }
            }
            composable(Route.IndividualOrderDetail, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    IndividualOrderDetailScreen(DevelopmentIndividualOrders.records.find { it.id == entry.arguments?.getString("id") },
                        { navController.popBackStack() })
                } }
            }
            composable(Route.DemoOrderDetail, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val order = DevelopmentDemoCheckout.orders.find {
                        it.reference == reference && it.customerId == demoCustomerId }
                    if (order == null) CustomerUnavailableScreen("order", { navController.popBackStack() })
                    else DemoOrderDetailScreen(order, { navController.popBackStack() },
                        { navController.navigate(Route.demoCheckout(reference)) },
                        { navController.navigate(Route.Orders) },
                        { navController.navigate(Route.CustomerPreview) })
                } }
            }
            composable(Route.DemoCheckout, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val actor = DevelopmentPreviewAccess.activeIdentity?.authorization
                    val order = DevelopmentDemoCheckout.orders.find {
                        it.reference == reference && it.customerId == demoCustomerId }
                        ?: DevelopmentDemoCheckout.previewBulk(reference, actor)
                    if (order == null) CustomerUnavailableScreen("checkout", { navController.popBackStack() })
                    else DemoCheckoutScreen(order, { navController.popBackStack() },
                        { DevelopmentDemoCheckout.simulatePayment(reference, it, actor) },
                        { navController.navigate(Route.demoOrderDetail(reference)) {
                            popUpTo(Route.DemoCheckout) { inclusive = true }
                        } })
                } }
            }
            composable(Route.BulkSellers) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkSellerPickerScreen(bulkDraft, DevelopmentBulkPreview,
                        onBack = { navController.popBackStack() }, onSelected = { navController.navigate(Route.BulkCart) })
                } }
            }
            composable(Route.BulkCart) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkCartScreen(bulkDraft, DevelopmentBulkPreview, onBack = { navController.popBackStack() },
                        onChooseSeller = { navController.navigate(Route.BulkSellers) },
                        onPickup = { navController.navigate(Route.BulkPickup) },
                        onViewCart = { navController.navigate(Route.cart(CartMode.BULK)) })
                } }
            }
            composable(Route.BulkPickup) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkPickupScreen(bulkDraft, DevelopmentBulkPreview, onBack = { navController.popBackStack() },
                        onReview = { navController.navigate(Route.BulkReview) })
                } }
            }
            composable(Route.BulkReview) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkReviewScreen(bulkDraft, DevelopmentBulkPreview,
                        onBack = { navController.popBackStack() },
                        onEditItems = { navController.navigate(Route.BulkCart) },
                        onEditPickup = { navController.navigate(Route.BulkPickup) },
                        onDemoNegotiation = { navController.navigate(Route.BulkNegotiation) },
                        onSubmitLocalDemo = {
                            val (result, reference) = DevelopmentSellerOperations.submitLocalDemo(
                                bulkDraft.draft, DevelopmentPreviewAccess.activeIdentity?.authorization,
                                java.time.LocalDateTime.now(RIT_TIME_ZONE))
                            if (result == edu.msrit.canteen.customer.DemoTransitionResult.APPLIED && reference != null)
                                navController.navigate(Route.customerDemoDetail(reference))
                        })
                } }
            }
            composable(Route.BulkOrders) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkOrdersScreen(onBack = { navController.popBackStack() },
                        onDraft = { navController.navigate(Route.BulkSellers) },
                        onQuickReorder = { navController.navigate(Route.QuickReorderEmpty) },
                        onSampleNegotiation = { navController.navigate(Route.BulkNegotiation) },
                        onDemoRequests = { navController.navigate(Route.CustomerDemoList) })
                } }
            }
            composable(Route.BulkOrdersDemo) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkOrdersScreen(onBack = { navController.popBackStack() },
                        onDraft = { navController.navigate(Route.BulkSellers) },
                        onQuickReorder = { navController.navigate(Route.QuickReorderSample) },
                        onSampleNegotiation = { navController.navigate(Route.BulkNegotiation) },
                        onDemoRequests = { navController.navigate(Route.CustomerDemoList) }, showSample = true)
                } }
            }
            composable(Route.BulkNegotiation) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkNegotiationScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        onBack = { navController.popBackStack() },
                        onCompare = { navController.navigate(Route.BulkCompare) },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) },
                        onFinal = { navController.navigate(Route.BulkFinal) })
                } }
            }
            composable(Route.BulkCompare) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkCompareScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        onBack = { navController.popBackStack() },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) })
                } }
            }
            composable(Route.BulkCounter) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkCounterproposalScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        DevelopmentBulkPreview, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.BulkFinal) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkFinalQuoteScreen(DevelopmentBulkPreview.sample(QuoteStage.AGREED_AWAITING_PAYMENT),
                        onBack = { navController.popBackStack() },
                        onPayment = { navController.navigate(Route.BulkPayment) })
                } }
            }
            composable(Route.BulkPayment) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkPaymentEligibilityScreen(DevelopmentBulkPreview.sample(QuoteStage.AGREED_AWAITING_PAYMENT),
                        onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.BulkExpired) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    BulkNegotiationScreen(DevelopmentBulkPreview.sample(QuoteStage.EXPIRED),
                        onBack = { navController.popBackStack() },
                        onCompare = { navController.navigate(Route.BulkCompare) },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) },
                        onFinal = { navController.navigate(Route.BulkFinal) })
                } }
            }
            composable(Route.SellerOverview) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerDailyOperationsOverview(
                        records = DevelopmentSellerOperations.records, sellerId = sellerId,
                        sellerName = PreviewCatalog.sellers.find { it.id == sellerId }?.name ?: "Seller",
                        roleLabel = DevelopmentPreviewAccess.activeIdentity?.label ?: "Seller",
                        canRespond = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")),
                        onBack = { navController.popBackStack() },
                        onOpenRequests = { navController.navigate(Route.SellerInbox) },
                        onOpenSchedule = { navController.navigate(Route.SellerSchedule) },
                        onOpenRequest = { navController.navigate(Route.sellerDetail(it)) },
                        onOpenMenu = { navController.navigate(Route.SellerManageMenu) },
                        onOpenSales = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "analytics.view")))
                            ({ navController.navigate(Route.sellerInsight(SellerInsightPage.OVERVIEW)) }) else null,
                        onOpenReports = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "reports.export")))
                            ({ navController.navigate(Route.sellerInsight(SellerInsightPage.REPORT_BUILDER)) }) else null,
                        onOpenManagement = { navController.navigate(Route.SellerManagement) },
                        onOpenHistory = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "operations.history")))
                            ({ navController.navigate(Route.SellerHistory) }) else null,
                        onOpenSupport = { navController.navigate(Route.sellerInsight(SellerInsightPage.SUPPORT)) },
                        onOpenAccount = if (DevelopmentDemoAccounts.activeAccount != null)
                            ({ navController.navigate(Route.DemoAccountPage) }) else null,
                        onBottom = ::openSellerBottom,
                        canViewInsights = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "analytics.view")),
                        demoOrders = DevelopmentDemoCheckout.orders,
                        onOpenIndividual = { navController.navigate(Route.SellerIndividualQueue) },
                        onVerifyPickup = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")))
                            ({ navController.navigate(Route.SellerDemoPickup) }) else null,
                        onOpenHours = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "hours.edit")))
                            ({ navController.navigate(Route.SellerManageHours) }) else null,
                        onOpenTeam = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "employees.manage")))
                            ({ navController.navigate(Route.SellerManageEmployees) }) else null,
                        onOpenPromotions = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "promotions.manage")))
                            ({ navController.navigate(Route.SellerManagePromotions) }) else null)
                } }
            }
            composable(Route.SellerIndividualQueue) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerDemoIndividualQueue(DevelopmentDemoCheckout.orders.filter { it.sellerId == sellerId },
                        onBack = { navController.popBackStack() },
                        onRespond = { reference, accept ->
                            DevelopmentDemoCheckout.respondIndividual(reference, accept,
                                DevelopmentPreviewAccess.activeIdentity?.authorization)
                        }, onVerify = { navController.navigate(Route.SellerDemoPickup) },
                        canRespond = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")),
                        onOrder = { navController.navigate(Route.sellerDemoOrderDetail(it)) })
                } }
            }
            composable(Route.SellerDemoOrderDetail,
                arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    val order = DevelopmentDemoCheckout.orders.find {
                        it.reference == entry.arguments?.getString("reference") && it.sellerId == sellerId }
                    if (order == null) CustomerUnavailableScreen("order", { navController.popBackStack() })
                    else SellerDemoOrderDetailScreen(order, { navController.popBackStack() },
                        { navController.navigate(Route.SellerDemoPickup) },
                        canVerify = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")))
                } }
            }
            composable(Route.SellerDemoPickup) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.respond", { returnToAuthorizedHome() }) {
                    SellerDemoPickupScreen(onBack = { navController.popBackStack() },
                        onVerify = { DevelopmentDemoCheckout.verifyCode(it,
                            DevelopmentPreviewAccess.activeIdentity?.authorization) },
                        onCollect = { DevelopmentDemoCheckout.collect(it,
                            DevelopmentPreviewAccess.activeIdentity?.authorization) })
                } }
            }
            composable(Route.SellerInbox) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerTabFrame(SellerBottomItem.Orders, ::openSellerBottom) {
                    SellerInboxScreen(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.sellerDetail(it)) },
                        onIndividual = { navController.navigate(Route.SellerIndividualQueue) },
                        newIndividualCount = DevelopmentDemoCheckout.orders.count {
                            it.sellerId == sellerId && it.kind == edu.msrit.canteen.customer.DemoOrderKind.INDIVIDUAL &&
                                it.state == edu.msrit.canteen.customer.DemoOrderState.SUBMITTED },
                        onVerifyPickup = if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")))
                            ({ navController.navigate(Route.SellerDemoPickup) }) else null)
                    }
                } }
            }
            composable(Route.SellerDetail, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerDailyRequestDetailPresentation(record,
                        DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")),
                        onBack = { navController.popBackStack() },
                        onPrimaryAction = { navController.navigate(Route.sellerApproval(reference)) },
                        onReviewSpecialPickup = { navController.navigate(Route.sellerSpecial(reference)) },
                        onCompareRevisions = { navController.navigate(Route.sellerCompare(reference)) },
                        onCounteroffer = { navController.navigate(Route.sellerCounter(reference)) },
                        onReject = { DevelopmentSellerOperations.reject(reference,
                            DevelopmentPreviewAccess.activeIdentity?.authorization) })
                } }
            }
            composable(Route.SellerCounter, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.respond", { returnToAuthorizedHome() }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else DemoQuoteEditorScreen(record, true, DevelopmentBulkPreview,
                        onBack = { navController.popBackStack() },
                        onSubmit = { DevelopmentSellerOperations.propose(reference, record.latest.version, it,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) },
                        onApplied = { navController.popBackStack() })
                } }
            }
            composable(Route.SellerCompare, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerRevisionCompareScreen(record, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.SellerApproval, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerFinalApprovalScreen(record,
                        DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")),
                        onBack = { navController.popBackStack() },
                        onSpecial = { navController.navigate(Route.sellerSpecial(reference)) },
                        onApprove = { DevelopmentSellerOperations.sellerApprove(reference, record.latest.version,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) })
                } }
            }
            composable(Route.SellerSpecial, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerSpecialPickupScreen(record,
                        DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")),
                        onBack = { navController.popBackStack() },
                        onAccept = { DevelopmentSellerOperations.acceptSpecial(reference, record.latest.version,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) },
                        onReject = { DevelopmentSellerOperations.rejectSpecial(reference, record.latest.version,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) })
                } }
            }
            composable(Route.SellerSchedule) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerPickupSchedulePresentation(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onOpenRequest = { navController.navigate(Route.sellerDetail(it)) },
                        canRespond = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")))
                } }
            }
            composable(Route.SellerHistory) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "operations.history", { returnToAuthorizedHome() }) {
                    SellerHistoryScreen(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.sellerDetail(it)) },
                        demoOrders = DevelopmentDemoCheckout.orders,
                        onDemoOrder = { navController.navigate(Route.sellerDemoOrderDetail(it)) })
                } }
            }
            composable(Route.SellerManagement) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerBusinessHomeScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.SellerInbox) }, { navController.navigate(Route.SellerSchedule) },
                        { navController.navigate(Route.SellerManageMenu) }, { navController.navigate(Route.SellerManageCategories) },
                        { navController.navigate(Route.SellerManagePromotions) }, { navController.navigate(Route.SellerManageEmployees) },
                        { navController.navigate(Route.SellerManageSettings) })
                } }
            }
            composable(Route.SellerManageMenu) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerTabFrame(SellerBottomItem.Menu, ::openSellerBottom) {
                    SellerMenuManagementScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.manageItem(it)) }, { navController.navigate(Route.SellerManageCategories) },
                        { navController.navigate(Route.manageAvailability(it)) }, rootTab = true)
                    }
                } }
            }
            composable(Route.SellerManageItem, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "catalog.edit", { returnToAuthorizedHome() }) {
                    SellerItemEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("itemId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageAvailability, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "availability.edit", { returnToAuthorizedHome() }) {
                    SellerAvailabilityScreen(sellerId, entry.arguments?.getString("itemId").orEmpty(),
                        DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(), DevelopmentSellerBusiness,
                        { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageCategories) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "catalog.edit", { returnToAuthorizedHome() }) {
                    SellerCategoriesScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageSettings) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { returnToAuthorizedHome() }) {
                    SellerBusinessSettingsScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.SellerManageProfile) }, { navController.navigate(Route.SellerManageHours) },
                        { navController.navigate(Route.SellerManageClosures) }, { navController.navigate(Route.SellerManageMinimum) },
                        { navController.navigate(Route.SellerManageEmployees) }, { navController.navigate(Route.SellerManagePromotions) })
                } }
            }
            composable(Route.SellerManageProfile) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "profile.edit", { returnToAuthorizedHome() }) {
                    SellerProfileEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageHours) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { returnToAuthorizedHome() }) {
                    SellerHoursScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageClosures) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { returnToAuthorizedHome() }) {
                    SellerClosuresScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageMinimum) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { returnToAuthorizedHome() }) {
                    SellerBulkMinimumScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageEmployees) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "employees.manage", { returnToAuthorizedHome() }) {
                    SellerEmployeesScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.manageEmployee(it)) })
                } }
            }
            composable(Route.SellerManageEmployee, arguments = listOf(navArgument("employeeId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "employees.manage", { returnToAuthorizedHome() }) {
                    SellerEmployeeEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("employeeId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManagePromotions) {
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "promotions.manage", { returnToAuthorizedHome() }) {
                    SellerPromotionsScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.managePromotion(it)) })
                } }
            }
            composable(Route.SellerManagePromotion, arguments = listOf(navArgument("promotionId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: ""
                InsetScreen { DebugSellerGate(sellerId, "promotions.manage", { returnToAuthorizedHome() }) {
                    SellerPromotionEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("promotionId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerInsights, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType },
                navArgument("sample") { type = NavType.BoolType; defaultValue = true })) { entry ->
                val sellerId = activeSellerId() ?: ""
                val page = runCatching { SellerInsightPage.valueOf(entry.arguments?.getString("page").orEmpty()) }
                    .getOrDefault(SellerInsightPage.OVERVIEW)
                val capability = when (page) {
                    SellerInsightPage.OVERVIEW, SellerInsightPage.TRENDS, SellerInsightPage.PRODUCTS,
                    SellerInsightPage.CUSTOMERS, SellerInsightPage.PROMOTIONS, SellerInsightPage.FORECAST -> "analytics.view"
                    SellerInsightPage.REPORT_BUILDER, SellerInsightPage.REPORT_HISTORY -> "reports.export"
                    else -> "bulk.view"
                }
                InsetScreen { DebugSellerGate(sellerId, capability, { returnToAuthorizedHome() }) {
                    SellerTabFrame(SellerBottomItem.Insights, ::openSellerBottom) {
                    SellerInsightScreen(page, sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization,
                        entry.arguments?.getBoolean("sample") != false,
                        entry.arguments?.getString("id")?.takeUnless { it == "none" },
                        onBack = { navController.popBackStack() },
                        onNavigate = { navController.navigate(Route.sellerInsight(it)) },
                        onTicket = { navController.navigate(Route.sellerInsight(SellerInsightPage.TICKET_DETAIL, it)) })
                    }
                } }
            }
            composable(Route.Admin, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType },
                navArgument("sample") { type = NavType.BoolType; defaultValue = true })) { entry ->
                val page = runCatching { AdminPage.valueOf(entry.arguments?.getString("page").orEmpty()) }
                    .getOrDefault(AdminPage.DASHBOARD)
                val id = entry.arguments?.getString("id")?.takeUnless { it == "none" }
                InsetScreen { DebugAdminGate({ returnToAuthorizedHome() }) {
                    PlatformAdminScreen(page, DevelopmentPreviewAccess.activeIdentity?.authorization,
                        entry.arguments?.getBoolean("sample") != false,
                        if (page == AdminPage.SELLER_DETAIL || page == AdminPage.SELLER_ANALYTICS) id else null,
                        if (page == AdminPage.TICKET_DETAIL) id else null,
                        onBack = { navController.popBackStack() },
                        onNavigate = { navController.navigate(Route.admin(it)) },
                        onSeller = { navController.navigate(Route.admin(AdminPage.SELLER_DETAIL, it)) },
                        onSellerAnalytics = { navController.navigate(Route.admin(AdminPage.SELLER_ANALYTICS, it)) },
                        onTicket = { navController.navigate(Route.admin(AdminPage.TICKET_DETAIL, it)) },
                        onAccount = if (DevelopmentDemoAccounts.activeAccount != null)
                            ({ navController.navigate(Route.DemoAccountPage) }) else null)
                } }
            }
            composable(Route.CustomerDemoList) {
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerDemoRequestsScreen(DevelopmentSellerOperations.records, demoCustomerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.customerDemoDetail(it)) })
                } }
            }
            composable(Route.CustomerDemoDetail, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val record = DevelopmentSellerOperations.records.find {
                        it.reference == reference && it.customerUserId == demoCustomerId }
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else CustomerDemoRequestScreen(record,
                        onBack = { navController.popBackStack() },
                        onCounter = { navController.navigate(Route.customerDemoCounter(reference)) },
                        onCompare = { navController.navigate(Route.customerDemoCompare(reference)) },
                        onApprove = { DevelopmentSellerOperations.customerApprove(reference, record.latest.version,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) },
                        onWithdraw = { DevelopmentSellerOperations.withdraw(reference,
                            DevelopmentPreviewAccess.activeIdentity?.authorization) },
                        onCheckout = { navController.navigate(Route.demoCheckout(reference)) },
                        demoOrder = DevelopmentDemoCheckout.orders.find {
                            it.reference == reference && it.customerId == demoCustomerId },
                        onTrack = { navController.navigate(Route.demoOrderDetail(reference)) })
                } }
            }
            composable(Route.CustomerDemoCounter, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val record = DevelopmentSellerOperations.records.find {
                        it.reference == reference && it.customerUserId == demoCustomerId }
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else DemoQuoteEditorScreen(record, false, DevelopmentBulkPreview,
                        onBack = { navController.popBackStack() },
                        onSubmit = { DevelopmentSellerOperations.propose(reference, record.latest.version, it,
                            DevelopmentPreviewAccess.activeIdentity?.authorization,
                            java.time.LocalDateTime.now(RIT_TIME_ZONE)) },
                        onApplied = { navController.popBackStack() })
                } }
            }
            composable(Route.CustomerDemoCompare, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    val record = DevelopmentSellerOperations.records.find {
                        it.reference == reference && it.customerUserId == demoCustomerId }
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerRevisionCompareScreen(record, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.CustomerUnavailable, arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { returnToAuthorizedHome() }) {
                    CustomerUnavailableScreen(entry.arguments?.getString("section").orEmpty(), onBack = { navController.popBackStack() }) } }
            }
        }
    }
}

@Composable
private fun SellerTabFrame(selected: SellerBottomItem, onSelect: (SellerBottomItem) -> Unit,
                           content: @Composable () -> Unit) {
    val sellerId = DevelopmentPreviewAccess.activeIdentity?.authorization?.modes?.firstNotNullOfOrNull {
        when (it) {
            is AppMode.SellerOwner -> it.sellerId
            is AppMode.SellerManager -> it.sellerId
            is AppMode.OperationalStaff -> it.sellerId
            else -> null
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        SellerBottomNavigation(selected, onSelect, showInsights = sellerId != null &&
            DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "analytics.view")))
    }
}

@Composable
private fun DebugCustomerGate(onDenied: () -> Unit, content: @Composable () -> Unit) {
    if (DevelopmentPreviewAccess.allows(ProtectedArea.Customer)) content()
    else CustomerUnavailableScreen("access", onDenied)
}

@Composable
private fun DebugSellerGate(sellerId: String, capability: String, onDenied: () -> Unit,
                            content: @Composable () -> Unit) {
    if (DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, capability))) content()
    else CustomerUnavailableScreen("access", onDenied)
}

@Composable
private fun DebugAdminGate(onDenied: () -> Unit, content: @Composable () -> Unit) {
    if (DevelopmentPreviewAccess.allows(ProtectedArea.Administrator)) content()
    else CustomerUnavailableScreen("access", onDenied)
}

@Composable
private fun InsetScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(RitThemeTokens.colors.background).windowInsetsPadding(WindowInsets.systemBars)) { content() }
}
