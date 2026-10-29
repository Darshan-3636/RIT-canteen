package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** Existing-item edits only. The backend has no create-item endpoint. Invoke on Dispatchers.IO. */
object DebugSellerCatalogApi {
    data class Result(val success: Boolean, val message: String)

    fun saveItem(item: MenuItem): Result {
        val original = PreviewCatalog.items.find { it.id == item.id && it.sellerId == item.sellerId }
            ?: return Result(false, "Adding new items is not supported by the current backend.")
        if (item.kind != original.kind || item.categoryId != original.categoryId)
            return Result(false, "Changing item kind or category is not supported by the current backend.")
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val body = JSONObject().put("name", item.name).put("description", item.description)
                .put("pricePaise", item.price.rupees.toLong() * 100L)
                .put("published", item.published).put("dietary", item.dietary.name)
                .put("unavailableVisibility", item.unavailableVisibility.name)
            val response = DebugBackendApi.request("PATCH",
                "/api/v1/sellers/" + item.sellerId + "/menu/items/" + item.id, body, token)
            if (response.status != 200) Result(false, response.error ?: "Item update failed.")
            else if (item.availability != original.availability) {
                val availability = DebugBackendApi.request("PATCH",
                    "/api/v1/sellers/" + item.sellerId + "/menu/items/" + item.id + "/availability",
                    JSONObject().put("availability", item.availability.name), token)
                if (availability.status == 200) Result(true, "Item and availability saved on server.")
                else Result(false, availability.error ?: "Item details saved, but availability failed. Refresh the menu.")
            } else Result(true, "Item saved on server.")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun setAvailability(sellerId: String, itemId: String, available: Boolean,
                        published: Boolean, showUnavailable: Boolean): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val path = "/api/v1/sellers/" + sellerId + "/menu/items/" + itemId
            val state = DebugBackendApi.request("PATCH", path + "/availability",
                JSONObject().put("availability", if (available) "AVAILABLE" else "UNAVAILABLE"), token)
            if (state.status != 200) return Result(false, state.error ?: "Availability update failed.")
            val visibility = DebugBackendApi.request("PATCH", path,
                JSONObject().put("published", published)
                    .put("unavailableVisibility", if (showUnavailable) "SHOW" else "HIDE"), token)
            if (visibility.status == 200) Result(true, "Visibility saved on server.")
            else Result(false, visibility.error ?: "Visibility update failed; availability may have changed. Refresh the menu.")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun saveMinimum(sellerId: String, minimum: Int): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val response = DebugBackendApi.request("PATCH", "/api/v1/sellers/" + sellerId + "/bulk-policy",
                JSONObject().put("minimumUnits", minimum), token)
            if (response.status == 200) Result(true, "Minimum saved on server.")
            else Result(false, response.error ?: "Minimum update failed.")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun saveHours(sellerId: String, policy: SellerBulkPolicy): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        val rows = JSONArray()
        policy.weeklyHours.forEach { (day, windows) -> windows.forEach { window ->
            rows.put(JSONObject().put("dayOfWeek", day.value)
                .put("opensAt", window.opens.toString()).put("closesAt", window.closes.toString()))
        } }
        return try {
            val response = DebugBackendApi.request("PUT", "/api/v1/sellers/" + sellerId + "/hours",
                JSONObject().put("hours", rows), token)
            if (response.status == 204) Result(true, "Weekly hours saved on server.")
            else Result(false, response.error ?: "Hours update failed.")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun setClosure(sellerId: String, date: LocalDate, reason: String?): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val response = if (reason == null) DebugBackendApi.request("DELETE",
                "/api/v1/sellers/" + sellerId + "/closures/" + date, token = token)
            else DebugBackendApi.request("PUT", "/api/v1/sellers/" + sellerId + "/closures/" + date,
                JSONObject().put("reason", reason), token)
            if (response.status in 200..204) Result(true, "Closure saved on server.")
            else Result(false, response.error ?: "Closure update failed.")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }
}
