package edu.msrit.canteen.navigation

import androidx.compose.foundation.layout.Box
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
import edu.msrit.canteen.ui.screens.EmailEntryScreen
import edu.msrit.canteen.ui.screens.DemoLoginScreen
import edu.msrit.canteen.ui.screens.DemoSignupScreen
import edu.msrit.canteen.ui.screens.DemoRoleAccountScreen
import edu.msrit.canteen.ui.screens.SellerOnboardingEntryScreen
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
    const val SellerOnboardingEntry = "seller-onboarding-entry"
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
        QuickReorderSample, Cart, IndividualReview, Orders, IndividualOrderDetail,
        BulkSellers, BulkCart, BulkPickup, BulkReview, BulkOrders, BulkOrdersDemo,
        BulkNegotiation, BulkCompare, BulkCounter, BulkFinal, BulkPayment, BulkExpired,
        SellerOverview, SellerInbox, SellerDetail, SellerCounter, SellerCompare, SellerApproval,
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
        when (item) {
            RitBottomItem.Home -> navController.navigate(Route.CustomerPreview) { launchSingleTop = true }
            RitBottomItem.Cart -> navController.navigate(Route.cart()) { launchSingleTop = true }
            RitBottomItem.Favourites -> navController.navigate(Route.Favorites) { launchSingleTop = true }
            RitBottomItem.Orders -> navController.navigate(Route.Orders) { launchSingleTop = true }
            RitBottomItem.Profile -> navController.navigate(Route.customerAccount("profile")) { launchSingleTop = true }
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
                    onGetStartedAsSeller = { navController.navigate(Route.SellerOnboardingEntry) },
                    onLogin = { navController.navigate(Route.emailEntry(AuthEntryIntent.Login)) },
                )
            }
        }
        composable(Route.SellerOnboardingEntry) {
            InsetScreen {
                SellerOnboardingEntryScreen(
                    onBack = { navController.popBackStack() },
                    onInvitedLogin = { navController.navigate(Route.emailEntry(AuthEntryIntent.Login)) },
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
                            demoFavorites.resetAll(); bulkDraft.clear(); individualCart.clear(); DevelopmentIndividualOrders.reset(); DevelopmentSellerOperations.reset()
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
                    } else DemoRoleAccountScreen(account, appearance,
                        onBack = { navController.popBackStack() },
                        onDashboard = { navController.navigate(homeFor(account)) { launchSingleTop = true } },
                        onSettings = { navController.navigate(if (account.role == DevelopmentRole.PlatformAdministrator)
                            Route.admin(AdminPage.SETTINGS) else Route.SellerManageSettings) },
                        onSupport = { navController.navigate(if (account.role == DevelopmentRole.PlatformAdministrator)
                            Route.admin(AdminPage.SUPPORT) else Route.sellerInsight(SellerInsightPage.SUPPORT)) },
                        onLogout = ::signOutDemo)
                }
            }
            composable(Route.StoriesPreview) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    RitStoriesPreviewScreen(onBack = { navController.popBackStack() },
                        onTheme = { navController.navigate(Route.storyDetail(it)) })
                } }
            }
            composable(Route.StoryDetail, arguments = listOf(navArgument("theme") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    val theme = ThemeId.entries.find { it.name == entry.arguments?.getString("theme") }
                    if (theme == null) CustomerUnavailableScreen("story", { navController.popBackStack() })
                    else RitStoryDetailScreen(theme, { navController.popBackStack() })
                } }
            }
            composable(Route.CustomerAccount, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    ChooseSellerScreen(
                        onSellerChosen = { sellerId ->
                            selectedCustomerSellerId = sellerId
                            navController.navigate(Route.sellerMenu(sellerId))
                        },
                        onBack = { navController.popBackStack() },
                        onBottom = ::openCustomerBottom,
                        cartCount = cartCount,
                        favorites = demoFavorites,
                        customerId = demoCustomerId,
                    )
                } }
            }
            composable(Route.OrderFood) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerMenuScreen(edu.msrit.canteen.customer.PreviewCatalog.MAIN_ID, preparedOnly = true,
                        onBack = { navController.popBackStack() }, onFood = { navController.navigate(Route.foodDetails(it)) },
                        favorites = demoFavorites, customerId = demoCustomerId)
                } }
            }
            composable(Route.Sellers) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) { ExploreSellersScreen(
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) { SellerProfileScreen(entry.arguments?.getString("sellerId").orEmpty(),
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerFavoritesScreen(demoCustomerId, demoFavorites,
                        onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        onBottom = ::openCustomerBottom, cartCount = cartCount)
                } }
            }
            composable(Route.Promotions) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerPromotionsScreen(onBack = { navController.popBackStack() },
                        onPromotion = { navController.navigate(Route.promotionDetail(it)) },
                        sellerId = selectedCustomerSellerId)
                } }
            }
            composable(Route.PromotionDetail, arguments = listOf(navArgument("promotionId") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerPromotionDetailScreen(entry.arguments?.getString("promotionId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        onFood = { navController.navigate(Route.foodDetails(it)) })
                } }
            }
            composable(Route.QuickReorderEmpty) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerQuickReorderScreen(onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) })
                } }
            }
            composable(Route.QuickReorderSample) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerQuickReorderScreen(onBack = { navController.popBackStack() },
                        onFood = { navController.navigate(Route.foodDetails(it)) },
                        onSeller = { navController.navigate(Route.sellerProfile(it)) },
                        history = DevelopmentSampleHistory)
                } }
            }
            composable(Route.Cart, arguments = listOf(navArgument("mode") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerCartScreen(individualCart, bulkDraft, DevelopmentBulkPreview,
                        if (entry.arguments?.getString("mode") == "bulk") CartMode.BULK else CartMode.INDIVIDUAL,
                        onBack = { navController.popBackStack() },
                        onBrowse = { navController.navigate(Route.OrderFood) },
                        onBulkItems = { navController.navigate(Route.BulkCart) },
                        onChooseSeller = { navController.navigate(Route.BulkSellers) },
                        onIndividualReview = { navController.navigate(Route.IndividualReview) },
                        onBulkPickup = { navController.navigate(Route.BulkPickup) })
                } }
            }
            composable(Route.IndividualReview) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    IndividualReviewScreen(individualCart, { navController.popBackStack() }, {
                        if (DevelopmentIndividualOrders.saveLocal(individualCart.draft)) {
                            individualCart.clear(); navController.navigate(Route.Orders)
                        }
                    })
                } }
            }
            composable(Route.Orders) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerOrdersScreen(DevelopmentIndividualOrders.records, DevelopmentSellerOperations.records,
                        { navController.popBackStack() },
                        { navController.navigate(Route.individualOrderDetail(it)) },
                        { navController.navigate(Route.customerDemoDetail(it)) },
                        { navController.navigate(Route.cart()) }, ::openCustomerBottom, cartCount,
                        customerUserId = demoCustomerId)
                } }
            }
            composable(Route.IndividualOrderDetail, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    IndividualOrderDetailScreen(DevelopmentIndividualOrders.records.find { it.id == entry.arguments?.getString("id") },
                        { navController.popBackStack() })
                } }
            }
            composable(Route.BulkSellers) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkSellerPickerScreen(bulkDraft, DevelopmentBulkPreview,
                        onBack = { navController.popBackStack() }, onSelected = { navController.navigate(Route.BulkCart) })
                } }
            }
            composable(Route.BulkCart) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkCartScreen(bulkDraft, DevelopmentBulkPreview, onBack = { navController.popBackStack() },
                        onChooseSeller = { navController.navigate(Route.BulkSellers) },
                        onPickup = { navController.navigate(Route.BulkPickup) },
                        onViewCart = { navController.navigate(Route.cart(CartMode.BULK)) })
                } }
            }
            composable(Route.BulkPickup) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkPickupScreen(bulkDraft, DevelopmentBulkPreview, onBack = { navController.popBackStack() },
                        onReview = { navController.navigate(Route.BulkReview) })
                } }
            }
            composable(Route.BulkReview) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkOrdersScreen(onBack = { navController.popBackStack() },
                        onDraft = { navController.navigate(Route.BulkSellers) },
                        onQuickReorder = { navController.navigate(Route.QuickReorderEmpty) },
                        onSampleNegotiation = { navController.navigate(Route.BulkNegotiation) },
                        onDemoRequests = { navController.navigate(Route.CustomerDemoList) })
                } }
            }
            composable(Route.BulkOrdersDemo) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkOrdersScreen(onBack = { navController.popBackStack() },
                        onDraft = { navController.navigate(Route.BulkSellers) },
                        onQuickReorder = { navController.navigate(Route.QuickReorderSample) },
                        onSampleNegotiation = { navController.navigate(Route.BulkNegotiation) },
                        onDemoRequests = { navController.navigate(Route.CustomerDemoList) }, showSample = true)
                } }
            }
            composable(Route.BulkNegotiation) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkNegotiationScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        onBack = { navController.popBackStack() },
                        onCompare = { navController.navigate(Route.BulkCompare) },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) },
                        onFinal = { navController.navigate(Route.BulkFinal) })
                } }
            }
            composable(Route.BulkCompare) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkCompareScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        onBack = { navController.popBackStack() },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) })
                } }
            }
            composable(Route.BulkCounter) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkCounterproposalScreen(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER),
                        DevelopmentBulkPreview, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.BulkFinal) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkFinalQuoteScreen(DevelopmentBulkPreview.sample(QuoteStage.AGREED_AWAITING_PAYMENT),
                        onBack = { navController.popBackStack() },
                        onPayment = { navController.navigate(Route.BulkPayment) })
                } }
            }
            composable(Route.BulkPayment) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkPaymentEligibilityScreen(DevelopmentBulkPreview.sample(QuoteStage.AGREED_AWAITING_PAYMENT),
                        onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.BulkExpired) {
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    BulkNegotiationScreen(DevelopmentBulkPreview.sample(QuoteStage.EXPIRED),
                        onBack = { navController.popBackStack() },
                        onCompare = { navController.navigate(Route.BulkCompare) },
                        onCounterDraft = { navController.navigate(Route.BulkCounter) },
                        onFinal = { navController.navigate(Route.BulkFinal) })
                } }
            }
            composable(Route.SellerOverview) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
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
                            ({ navController.navigate(Route.DemoAccountPage) }) else null)
                } }
            }
            composable(Route.SellerInbox) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerInboxScreen(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.sellerDetail(it)) })
                } }
            }
            composable(Route.SellerDetail, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
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
                val sellerId = activeSellerId() ?: "north-counter"
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.respond", { navController.navigate(Route.DeveloperPanel) }) {
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
                val sellerId = activeSellerId() ?: "north-counter"
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    val record = scopedSellerRecord(reference)
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerRevisionCompareScreen(record, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.SellerApproval, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
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
                val sellerId = activeSellerId() ?: "north-counter"
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
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
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerPickupSchedulePresentation(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onOpenRequest = { navController.navigate(Route.sellerDetail(it)) },
                        canRespond = DevelopmentPreviewAccess.allows(ProtectedArea.Seller(sellerId, "bulk.respond")))
                } }
            }
            composable(Route.SellerHistory) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "operations.history", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerHistoryScreen(DevelopmentSellerOperations.records, sellerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.sellerDetail(it)) })
                } }
            }
            composable(Route.SellerManagement) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerBusinessHomeScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.SellerInbox) }, { navController.navigate(Route.SellerSchedule) },
                        { navController.navigate(Route.SellerManageMenu) }, { navController.navigate(Route.SellerManageCategories) },
                        { navController.navigate(Route.SellerManagePromotions) }, { navController.navigate(Route.SellerManageEmployees) },
                        { navController.navigate(Route.SellerManageSettings) })
                } }
            }
            composable(Route.SellerManageMenu) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerMenuManagementScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.manageItem(it)) }, { navController.navigate(Route.SellerManageCategories) },
                        { navController.navigate(Route.manageAvailability(it)) })
                } }
            }
            composable(Route.SellerManageItem, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "catalog.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerItemEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("itemId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageAvailability, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "availability.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerAvailabilityScreen(sellerId, entry.arguments?.getString("itemId").orEmpty(),
                        DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(), DevelopmentSellerBusiness,
                        { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageCategories) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "catalog.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerCategoriesScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageSettings) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "bulk.view", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerBusinessSettingsScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.SellerManageProfile) }, { navController.navigate(Route.SellerManageHours) },
                        { navController.navigate(Route.SellerManageClosures) }, { navController.navigate(Route.SellerManageMinimum) },
                        { navController.navigate(Route.SellerManageEmployees) }, { navController.navigate(Route.SellerManagePromotions) })
                } }
            }
            composable(Route.SellerManageProfile) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "profile.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerProfileEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageHours) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerHoursScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageClosures) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerClosuresScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageMinimum) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "hours.edit", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerBulkMinimumScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManageEmployees) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "employees.manage", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerEmployeesScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.manageEmployee(it)) })
                } }
            }
            composable(Route.SellerManageEmployee, arguments = listOf(navArgument("employeeId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "employees.manage", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerEmployeeEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("employeeId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerManagePromotions) {
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "promotions.manage", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerPromotionsScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        DevelopmentSellerBusiness, { navController.popBackStack() },
                        { navController.navigate(Route.managePromotion(it)) })
                } }
            }
            composable(Route.SellerManagePromotion, arguments = listOf(navArgument("promotionId") { type = NavType.StringType })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                InsetScreen { DebugSellerGate(sellerId, "promotions.manage", { navController.navigate(Route.DeveloperPanel) }) {
                    SellerPromotionEditorScreen(sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId.orEmpty(),
                        entry.arguments?.getString("promotionId")?.takeUnless { it == "new" }, DevelopmentSellerBusiness,
                        { navController.popBackStack() }, { navController.popBackStack() })
                } }
            }
            composable(Route.SellerInsights, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType },
                navArgument("sample") { type = NavType.BoolType; defaultValue = true })) { entry ->
                val sellerId = activeSellerId() ?: "north-counter"
                val page = runCatching { SellerInsightPage.valueOf(entry.arguments?.getString("page").orEmpty()) }
                    .getOrDefault(SellerInsightPage.OVERVIEW)
                val capability = when (page) {
                    SellerInsightPage.OVERVIEW, SellerInsightPage.TRENDS, SellerInsightPage.PRODUCTS,
                    SellerInsightPage.CUSTOMERS, SellerInsightPage.PROMOTIONS, SellerInsightPage.FORECAST -> "analytics.view"
                    SellerInsightPage.REPORT_BUILDER, SellerInsightPage.REPORT_HISTORY -> "reports.export"
                    else -> "bulk.view"
                }
                InsetScreen { DebugSellerGate(sellerId, capability, { navController.navigate(Route.DeveloperPanel) }) {
                    SellerInsightScreen(page, sellerId, DevelopmentPreviewAccess.activeIdentity?.authorization,
                        entry.arguments?.getBoolean("sample") != false,
                        entry.arguments?.getString("id")?.takeUnless { it == "none" },
                        onBack = { navController.popBackStack() },
                        onNavigate = { navController.navigate(Route.sellerInsight(it)) },
                        onTicket = { navController.navigate(Route.sellerInsight(SellerInsightPage.TICKET_DETAIL, it)) })
                } }
            }
            composable(Route.Admin, arguments = listOf(
                navArgument("page") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType },
                navArgument("sample") { type = NavType.BoolType; defaultValue = true })) { entry ->
                val page = runCatching { AdminPage.valueOf(entry.arguments?.getString("page").orEmpty()) }
                    .getOrDefault(AdminPage.DASHBOARD)
                val id = entry.arguments?.getString("id")?.takeUnless { it == "none" }
                InsetScreen { DebugAdminGate({ navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerDemoRequestsScreen(DevelopmentSellerOperations.records, demoCustomerId,
                        onBack = { navController.popBackStack() },
                        onRequest = { navController.navigate(Route.customerDemoDetail(it)) })
                } }
            }
            composable(Route.CustomerDemoDetail, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                            DevelopmentPreviewAccess.activeIdentity?.authorization) })
                } }
            }
            composable(Route.CustomerDemoCounter, arguments = listOf(navArgument("reference") { type = NavType.StringType })) { entry ->
                val reference = entry.arguments?.getString("reference").orEmpty()
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
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
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    val record = DevelopmentSellerOperations.records.find {
                        it.reference == reference && it.customerUserId == demoCustomerId }
                    if (record == null) CustomerUnavailableScreen("request", { navController.popBackStack() })
                    else SellerRevisionCompareScreen(record, onBack = { navController.popBackStack() })
                } }
            }
            composable(Route.CustomerUnavailable, arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                InsetScreen { DebugCustomerGate(onDenied = { navController.navigate(Route.DeveloperPanel) }) {
                    CustomerUnavailableScreen(entry.arguments?.getString("section").orEmpty(), onBack = { navController.popBackStack() }) } }
            }
        }
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
