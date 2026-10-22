package edu.msrit.canteen.onboarding

import android.content.Context
import edu.msrit.canteen.R

/** This local flag predates the three-slide redesign. Keep both keys for existing installs. */
interface OnboardingPreference {
    fun hasCompleted(): Boolean
    fun markCompleted(): Boolean
}

class SharedPreferencesOnboardingPreference(context: Context) : OnboardingPreference {
    private val preferences = context.applicationContext.getSharedPreferences("rit_onboarding", Context.MODE_PRIVATE)
    override fun hasCompleted(): Boolean = preferences.getBoolean("completed", false)
    override fun markCompleted(): Boolean = preferences.edit().putBoolean("completed", true).commit()
}

enum class ThemeFamily { Game, Education }
enum class ThemeId { Minecraft, Valorant, Pubg, AmongUs, Codm, Aristotle, Newton, Turing, Stoic, Einstein }

data class ThemeArtwork(
    val id: ThemeId,
    val title: String,
    val family: ThemeFamily,
    val artwork: Int,
    val description: String,
    /** Legacy references remain catalogued, but are not approved as full-screen production art. */
    val cleanFullScreen: Boolean,
)

object ThemeCatalog {
    val all = listOf(
        ThemeArtwork(ThemeId.Minecraft, "Minecraft", ThemeFamily.Game, R.drawable.onboarding_minecraft_shader, "Shader-lit voxel students selecting dishes at a canteen counter", true),
        ThemeArtwork(ThemeId.Valorant, "Valorant", ThemeFamily.Game, R.drawable.onboarding_valorant_game, "Game-style group and seller planning a large food request", true),
        ThemeArtwork(ThemeId.Pubg, "PUBG", ThemeFamily.Game, R.drawable.onboarding_pubg_clean, "Parachuting above a campus", true),
        ThemeArtwork(ThemeId.AmongUs, "Among Us", ThemeFamily.Game, R.drawable.onboarding_among_us_map, "Top-down crew exploring three connected food-court seller rooms", true),
        ThemeArtwork(ThemeId.Codm, "CODM", ThemeFamily.Game, R.drawable.onboarding_codm, "Tactical campus view", false),
        ThemeArtwork(ThemeId.Aristotle, "Aristotle", ThemeFamily.Education, R.drawable.onboarding_aristotle, "Illustrated philosopher", false),
        ThemeArtwork(ThemeId.Newton, "Newton", ThemeFamily.Education, R.drawable.onboarding_newton, "Illustrated scientist", false),
        ThemeArtwork(ThemeId.Turing, "Turing", ThemeFamily.Education, R.drawable.onboarding_turing_clean, "Editorial Turing illustration", true),
        ThemeArtwork(ThemeId.Stoic, "Stoic", ThemeFamily.Education, R.drawable.onboarding_stoic, "Philosopher at a canteen", false),
        ThemeArtwork(ThemeId.Einstein, "Einstein", ThemeFamily.Education, R.drawable.onboarding_einstein, "Physics-inspired campus", false),
    )
    fun get(id: ThemeId): ThemeArtwork = all.first { it.id == id }
}

enum class SlideId { OrderFood, BulkOrders, ExploreSellers }
data class OnboardingContent(val id: SlideId, val title: String, val subtitle: String)
data class OnboardingPage(val content: OnboardingContent, val theme: ThemeArtwork) {
    val id: SlideId get() = content.id
}

/** Product content and artwork assignments are intentionally independent. */
object OnboardingManifest {
    val content = listOf(
        OnboardingContent(SlideId.OrderFood, "Order Food", "Discover the main canteen's prepared-food menu and see what's available on campus."),
        OnboardingContent(SlideId.BulkOrders, "Bulk Orders", "Plan food for clubs, meetings and events. Agree on items, quantities, pickup time and price before a final quote and payment."),
        OnboardingContent(SlideId.ExploreSellers, "Explore Sellers", "Browse participating stalls, menus, categories and offers. Save your favourites; ordering options vary by seller."),
    )
    val themeAssignments = mapOf(
        SlideId.OrderFood to ThemeId.Minecraft,
        SlideId.BulkOrders to ThemeId.Valorant,
        SlideId.ExploreSellers to ThemeId.AmongUs,
    )
    val pages: List<OnboardingPage> = content.map { OnboardingPage(it, ThemeCatalog.get(themeAssignments.getValue(it.id))) }
    fun nextIndex(current: Int): Int = (current + 1).coerceAtMost(pages.lastIndex)
    fun previousIndex(current: Int): Int = (current - 1).coerceAtLeast(0)
}

class OnboardingFlow(private val preference: OnboardingPreference) {
    fun shouldShowOnboarding(): Boolean = !preference.hasCompleted()
    fun finish(): Boolean = preference.markCompleted()
}
