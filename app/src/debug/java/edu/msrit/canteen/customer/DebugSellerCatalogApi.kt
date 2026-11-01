package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** Seller catalog commands. Invoke on Dispatchers.IO; server permissions and versions are authoritative. */
object DebugSellerCatalogApi {
    data class Result(val success: Boolean, val message: String)

    fun saveProfile(sellerId: String, name: String, description: String): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val path = "/api/v1/sellers/$sellerId/profile"
            val current = DebugBackendApi.request("GET", path, token = token)
            if (current.status != 200) return Result(false, current.error ?: "Could not load profile version.")
            val result = DebugBackendApi.request("PATCH", path, JSONObject().put("name", name)
                .put("description", description).put("expectedVersion", current.body!!.getLong("version")), token)
            if (result.status == 200) Result(true, "Public profile saved on server.")
            else Result(false, result.error ?: "Profile save failed (${result.status}).")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun setArchived(sellerId: String, itemId: String, archive: Boolean): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val path = "/api/v1/sellers/$sellerId/catalog/items/$itemId"
            val current = DebugBackendApi.request("GET", path, token = token)
            if (current.status != 200) return Result(false, current.error ?: "Could not load item version.")
            val result = DebugBackendApi.request("POST", "$path/${if (archive) "archive" else "restore"}",
                JSONObject().put("expectedVersion", current.body!!.getLong("version")), token)
            if (result.status == 200) Result(true, if (archive) "Item archived on server." else "Item restored unpublished.")
            else Result(false, result.error ?: "Could not change archive state (${result.status}).")
        } catch (_: Exception) { Result(false, "Server unavailable.") }
    }

    fun saveItem(item: MenuItem): Result {
        val original = PreviewCatalog.items.find { it.id == item.id && it.sellerId == item.sellerId }
        val token = DevelopmentDemoAccounts.token() ?: return Result(false, "Sign in again.")
        return try {
            val path = "/api/v1/sellers/${item.sellerId}/catalog/items"
            val current = if (original == null) null else DebugBackendApi.request("GET", "$path/${item.id}", token = token)
            if (current != null && current.status != 200)
                return Result(false, current.error ?: "Could not load the latest item version.")
            val version = current?.body?.getLong("version")
            val body = JSONObject().put("name", item.name).put("description", item.description)
                .put("pricePaise", item.price.rupees.toLong() * 100L)
                .put("published", item.published).put("dietary", item.dietary.name)
                .put("unavailableVisibility", item.unavailableVisibility.name)
                .put("categoryId", item.categoryId).put("kind", item.kind.name)
                .put("imageKey", item.id)
            val response = if (original == null) DebugBackendApi.request("POST", path,
                body.put("id", item.id).put("availability", item.availability.name), token)
            else DebugBackendApi.request("PATCH", "/api/v1/sellers/${item.sellerId}/menu/items/${item.id}",
                body.put("expectedVersion", version), token)
            if (response.status !in 200..201) Result(false, response.error ?: "Item save failed.")
            else if (original != null && item.availability != original.availability) {
                val availability = DebugBackendApi.request("PATCH",
                    "/api/v1/sellers/" + item.sellerId + "/menu/items/" + item.id + "/availability",
                    JSONObject().put("availability", item.availability.name)
                        .put("expectedVersion", version!! + 1), token)
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
            val current = DebugBackendApi.request("GET", "/api/v1/sellers/$sellerId/catalog/items/$itemId", token = token)
            if (current.status != 200) return Result(false, current.error ?: "Could not load the latest item version.")
            val row = current.body!!
            var version = row.getLong("version")
            var availabilityChanged = false
            val desiredAvailability = if (available) "AVAILABLE" else "UNAVAILABLE"
            val desiredVisibility = if (showUnavailable) "SHOW" else "HIDE"
            if (row.getString("availability") != desiredAvailability) {
                val state = DebugBackendApi.request("PATCH", path + "/availability",
                    JSONObject().put("availability", desiredAvailability)
                        .put("expectedVersion", version), token)
                if (state.status != 200) return Result(false, state.error ?: "Availability update failed.")
                version++
                availabilityChanged = true
            }
            val visibility = if (row.getBoolean("published") != published ||
                row.getString("unavailableVisibility") != desiredVisibility) {
                DebugBackendApi.request("PATCH", path,
                    JSONObject().put("published", published)
                        .put("unavailableVisibility", desiredVisibility)
                        .put("expectedVersion", version), token)
            } else null
            if (visibility == null || visibility.status == 200) Result(true, "Availability and visibility saved on server.")
            else Result(false, visibility.error ?: if (availabilityChanged)
                "Visibility failed; availability changed. Refresh the menu." else "Visibility update failed.")
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
