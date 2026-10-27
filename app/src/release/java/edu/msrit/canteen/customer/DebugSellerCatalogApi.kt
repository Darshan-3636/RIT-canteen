package edu.msrit.canteen.customer

import java.time.LocalDate

/** No debug server URL or cleartext seller mutation in release. */
object DebugSellerCatalogApi {
    data class Result(val success: Boolean, val message: String)
    fun saveItem(item: MenuItem) = Result(false, "Local demo unavailable in release.")
    fun setAvailability(sellerId: String, itemId: String, available: Boolean,
                        published: Boolean, showUnavailable: Boolean) =
        Result(false, "Local demo unavailable in release.")
    fun saveMinimum(sellerId: String, minimum: Int) = Result(false, "Local demo unavailable in release.")
    fun saveHours(sellerId: String, policy: SellerBulkPolicy) = Result(false, "Local demo unavailable in release.")
    fun setClosure(sellerId: String, date: LocalDate, reason: String?) =
        Result(false, "Local demo unavailable in release.")
}
