package edu.msrit.canteen.backend.features.engagement

import java.time.LocalDate

/**
 * Read-only seam to the Commerce module (Backend 2). The lead wires a real adapter at integration;
 * until then [UnavailableCommerceReadPort] makes item validation fail closed and analytics report no data.
 */
interface CommerceReadPort {
    fun lookupItem(itemId: String): ItemLookup

    /** Demo bulk request counts in seller-local dates (inclusive), or null when no source is wired. */
    fun demoRequestCounts(sellerId: String?, from: LocalDate?, to: LocalDate?): DemoRequestCounts?
}

sealed interface ItemLookup {
    data class Found(val sellerId: String) : ItemLookup
    data object Missing : ItemLookup
    data object Unavailable : ItemLookup
}

data class DemoRequestCounts(val total: Int, val byStatus: Map<String, Int>)

object UnavailableCommerceReadPort : CommerceReadPort {
    override fun lookupItem(itemId: String): ItemLookup = ItemLookup.Unavailable
    override fun demoRequestCounts(sellerId: String?, from: LocalDate?, to: LocalDate?): DemoRequestCounts? = null
}
