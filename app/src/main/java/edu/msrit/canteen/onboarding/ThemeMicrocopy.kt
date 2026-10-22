package edu.msrit.canteen.onboarding

enum class CopyContext { Discovery, EmptySearch, BulkPlanning, SellerBrowse, WaitingForCatalog, Transaction }
enum class CopyTrigger { Idle, NoResults, Planning, Browsing, NetworkWait, Payment, Auth }
enum class CopySurface { Hint, EmptyState, InlineLoading }

data class ThemeLine(
    val id: String,
    val text: String,
    val theme: ThemeId,
    val context: CopyContext,
    val trigger: CopyTrigger,
    val surface: CopySurface,
    /** Minimum number of selection events before this line may be shown again. */
    val frequency: Int = 5,
)

/** Non-transactional copy only. Selection is deterministic, context-bound, and avoids recent repeats. */
object ThemeMicrocopy {
    private fun line(id: String, text: String, theme: ThemeId, context: CopyContext, trigger: CopyTrigger, surface: CopySurface) =
        ThemeLine(id, text, theme, context, trigger, surface)

    val lines = listOf(
        line("mc1", "A good lunch starts with a good find.", ThemeId.Minecraft, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("mc2", "Nothing in this patch of the menu yet.", ThemeId.Minecraft, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("mc3", "Building your options, one stall at a time.", ThemeId.Minecraft, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("mc4", "Loading the next block of choices.", ThemeId.Minecraft, CopyContext.WaitingForCatalog, CopyTrigger.NetworkWait, CopySurface.InlineLoading),
        line("val1", "Your next food stop is on the map.", ThemeId.Valorant, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("val2", "No matches on this route. Try another search.", ThemeId.Valorant, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("val3", "Scout a few more stalls before you choose.", ThemeId.Valorant, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("pubg1", "Drop in and discover what's nearby.", ThemeId.Pubg, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("pubg2", "No landing spot for that search yet.", ThemeId.Pubg, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("pubg3", "Explore the map of campus flavours.", ThemeId.Pubg, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("pubg4", "Checking the next landing zone of listings.", ThemeId.Pubg, CopyContext.WaitingForCatalog, CopyTrigger.NetworkWait, CopySurface.InlineLoading),
        line("among1", "Good food is better with your crew.", ThemeId.AmongUs, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("among2", "No menu matches in this crew's search.", ThemeId.AmongUs, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("among3", "Planning a meal for the whole crew?", ThemeId.AmongUs, CopyContext.BulkPlanning, CopyTrigger.Planning, CopySurface.Hint),
        line("codm1", "New objective: find a great lunch.", ThemeId.Codm, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("codm2", "No matches for this objective.", ThemeId.Codm, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("codm3", "Recon the participating stalls.", ThemeId.Codm, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("ari1", "A little curiosity goes well with lunch.", ThemeId.Aristotle, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("ari2", "No results for that line of inquiry.", ThemeId.Aristotle, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("ari3", "Consider a few menus before deciding.", ThemeId.Aristotle, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("new1", "Every good meal has its own momentum.", ThemeId.Newton, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("new2", "No results in this orbit. Try a new term.", ThemeId.Newton, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("new3", "The menu is still coming into view.", ThemeId.Newton, CopyContext.WaitingForCatalog, CopyTrigger.NetworkWait, CopySurface.InlineLoading),
        line("tur1", "A better lunch decision starts with options.", ThemeId.Turing, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("tur2", "No result for that input. Try another.", ThemeId.Turing, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("tur3", "Gather the details before a group quote.", ThemeId.Turing, CopyContext.BulkPlanning, CopyTrigger.Planning, CopySurface.Hint),
        line("tur4", "Comparing the available campus menus.", ThemeId.Turing, CopyContext.WaitingForCatalog, CopyTrigger.NetworkWait, CopySurface.InlineLoading),
        line("sto1", "Take your time. The menu can wait.", ThemeId.Stoic, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("sto2", "No matches here. A different search may help.", ThemeId.Stoic, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("sto3", "Choose what works for your group.", ThemeId.Stoic, CopyContext.BulkPlanning, CopyTrigger.Planning, CopySurface.Hint),
        line("ein1", "A short break deserves a good find.", ThemeId.Einstein, CopyContext.Discovery, CopyTrigger.Idle, CopySurface.Hint),
        line("ein2", "No matches in this corner of the menu.", ThemeId.Einstein, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState),
        line("ein3", "Explore a new frame of campus flavours.", ThemeId.Einstein, CopyContext.SellerBrowse, CopyTrigger.Browsing, CopySurface.Hint),
        line("ein4", "Fetching the latest menu view.", ThemeId.Einstein, CopyContext.WaitingForCatalog, CopyTrigger.NetworkWait, CopySurface.InlineLoading),
    )

    fun select(theme: ThemeId, context: CopyContext, trigger: CopyTrigger, surface: CopySurface,
               eventNumber: Int, lastShown: Map<String, Int> = emptyMap()): ThemeLine? {
        if (context == CopyContext.Transaction || trigger == CopyTrigger.Payment || trigger == CopyTrigger.Auth) return null
        val matches = lines.filter { it.theme == theme && it.context == context && it.trigger == trigger && it.surface == surface &&
            eventNumber - (lastShown[it.id] ?: Int.MIN_VALUE / 2) >= it.frequency }
        return matches.getOrNull(Math.floorMod(eventNumber, matches.size.coerceAtLeast(1)))
    }
}
