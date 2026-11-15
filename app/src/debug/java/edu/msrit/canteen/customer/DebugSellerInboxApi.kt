package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts

/** Authenticated account inbox shared by customer and seller roles. Call from Dispatchers.IO. */
object DebugSellerInboxApi {
    data class Notice(val id: String, val title: String, val body: String, val read: Boolean)
    data class Result<T>(val value: T? = null, val error: String? = null)

    fun list(): Result<List<Notice>> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("GET","/api/v1/me/notifications?limit=100",token=token)
            if(response.status!=200) Result(error=response.error ?: "Could not load notifications.")
            else {
                val rows=response.body!!.getJSONArray("items")
                Result(value=(0 until rows.length()).map { index ->
                    val row=rows.getJSONObject(index)
                    Notice(row.getString("id"),row.getString("title"),row.getString("body"),row.getBoolean("read"))
                })
            }
        } catch (_: Exception) { Result(error="The notification inbox could not be reached.") }
    }

    fun markRead(id: String): Result<Boolean> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("PATCH","/api/v1/me/notifications/$id/read",token=token)
            if(response.status!=200) Result(error=response.error ?: "Could not mark notification as read.")
            else Result(value=true)
        } catch (_: Exception) { Result(error="The server did not confirm the change.") }
    }
}
