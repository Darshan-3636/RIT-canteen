package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts

/** Account-scoped favorites. Call on Dispatchers.IO only. */
internal object DebugFavoritesApi {
    data class Snapshot(val items: Set<String>, val sellers: Set<String>)
    data class Result(val snapshot: Snapshot? = null, val error: String? = null)

    fun load(): Result = withToken { token ->
        val response = DebugBackendApi.request("GET", "/api/v1/me/favorites", token = token)
        if (response.status != 200) return@withToken Result(error = response.error ?: "Could not load favourites.")
        val rows = response.body!!.getJSONArray("items")
        val items = mutableSetOf<String>()
        val sellers = mutableSetOf<String>()
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            when (row.getString("targetType")) {
                "ITEM" -> items += row.getString("targetId")
                "SELLER" -> sellers += row.getString("targetId")
            }
        }
        Result(snapshot = Snapshot(items, sellers))
    }

    fun set(type: String, id: String, saved: Boolean): Result = withToken { token ->
        require(type == "ITEM" || type == "SELLER")
        require(Regex("^[a-z0-9-]+$").matches(id))
        val response = DebugBackendApi.request(if (saved) "PUT" else "DELETE",
            "/api/v1/me/favorites/$type/$id", token = token)
        if (response.status !in 200..299) Result(error = response.error ?: "Could not update favourites.")
        else load()
    }

    private inline fun withToken(action: (String) -> Result): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try { action(token) } catch (_: Exception) { Result(error = "Could not reach the service. Try again.") }
    }
}
