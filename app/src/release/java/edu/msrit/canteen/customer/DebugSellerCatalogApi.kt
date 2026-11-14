package edu.msrit.canteen.customer

import android.content.Context
import java.time.LocalDate

/** No debug server URL or cleartext seller mutation in release. */
object DebugSellerCatalogApi {
    data class Result(val success: Boolean, val message: String)
    fun saveProfile(sellerId: String, name: String, description: String) =
        Result(false, "Local demo unavailable in release.")
    fun setArchived(sellerId: String, itemId: String, archive: Boolean) =
        Result(false, "Local demo unavailable in release.")
    fun saveItem(item: MenuItem, context: Context) = Result(false, "Local demo unavailable in release.")
    fun setAvailability(sellerId: String, itemId: String, available: Boolean,
                        published: Boolean, showUnavailable: Boolean) =
        Result(false, "Local demo unavailable in release.")
    fun saveMinimum(sellerId: String, minimum: Int) = Result(false, "Local demo unavailable in release.")
    fun saveIndividualEnabled(sellerId: String, enabled: Boolean) = Result(false, "Local demo unavailable in release.")
    fun saveHours(sellerId: String, policy: SellerBulkPolicy) = Result(false, "Local demo unavailable in release.")
    fun setClosure(sellerId: String, date: LocalDate, reason: String?) =
        Result(false, "Local demo unavailable in release.")
}
