package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime

/** Debug fixture only. Saved examples never leave this process or reach a seller. */
object DevelopmentIndividualOrders {
    private fun examples(now: LocalDateTime) = listOf(
        IndividualOrderPreview("SAMPLE-FOOD-01", now.minusDays(2), listOf(
            QuoteLineSnapshot("masala-dosa", "Masala Dosa", 2, 5_000),
            QuoteLineSnapshot("filter-coffee", "Filter Coffee", 1, 3_000)), 13_000),
        IndividualOrderPreview("SAMPLE-FOOD-02", now.minusDays(5), listOf(
            QuoteLineSnapshot("veg-biryani", "Veg Biryani", 1, 8_500),
            QuoteLineSnapshot("idli", "Idli", 2, 3_500)), 15_500),
        IndividualOrderPreview("SAMPLE-FOOD-03", now.minusDays(9), listOf(
            QuoteLineSnapshot("filter-coffee", "Filter Coffee", 3, 3_000)), 9_000),
    )
    var records by mutableStateOf(examples(LocalDateTime.now(RIT_TIME_ZONE)))
        private set

    fun saveLocal(draft: IndividualDraft, catalog: CustomerCatalogSource = PreviewCatalog): Boolean {
        val check = checkIndividualCart(draft, catalog)
        if (!check.readyForReview) return false
        val lines = check.lines.map { (item, quantity) ->
            QuoteLineSnapshot(item.id, item.name, quantity, item.price.rupees * 100L)
        }
        records = listOf(IndividualOrderPreview("LOCAL-FOOD-${records.count { it.locallySaved } + 1}",
            LocalDateTime.now(RIT_TIME_ZONE), lines, check.subtotalPaise, locallySaved = true)) + records
        return true
    }

    fun reset() { records = examples(LocalDateTime.now(RIT_TIME_ZONE)) }
}
