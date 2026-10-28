package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONObject
import java.time.LocalDate

/** Seller-scoped, capability-checked server data. Never manufactures revenue. */
object DebugSellerAnalyticsApi {
    data class Result(val value: SellerCloudAnalytics? = null, val error: String? = null)

    fun summary(sellerId: String, from: LocalDate, to: LocalDate): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try {
            val response = DebugBackendApi.request("GET",
                "/api/v1/sellers/$sellerId/analytics/summary?from=$from&to=$to", token = token)
            if (response.status != 200) Result(error = response.error ?: "Could not load seller analytics.")
            else Result(value = parse(response.body!!))
        } catch (_: Exception) { Result(error = "Cloud analytics unavailable. Retry later.") }
    }

    private fun parse(row: JSONObject): SellerCloudAnalytics {
        val counts = row.optJSONObject("demoRequestCounts")
        val statuses = counts?.optJSONObject("byStatus")
        val simulated = row.optJSONObject("simulatedSales")
        val byStatus = if (statuses == null) emptyMap() else statuses.keys().asSequence().associateWith(statuses::getInt)
        return SellerCloudAnalytics(counts?.getInt("total") ?: 0, byStatus,
            row.getString("demoRequestCountsStatus"),
            row.optString("from").takeUnless { it.isBlank() || it == "null" },
            row.optString("to").takeUnless { it.isBlank() || it == "null" },
            simulated?.optInt("orderCount") ?: 0, simulated?.optLong("totalPaise") ?: 0L)
    }
}
