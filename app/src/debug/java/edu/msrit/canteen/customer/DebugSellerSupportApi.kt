package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONObject

/** Seller-scoped customer ticket transport. The backend enforces support.view/respond. */
object DebugSellerSupportApi {
    data class Result(val value: JSONObject? = null, val error: String? = null)
    private fun request(method: String, path: String, body: JSONObject? = null): Result {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request(method,path,body,token)
            if(response.status !in 200..201) Result(error=response.error ?: "Support request failed (${response.status}).")
            else Result(value=response.body ?: JSONObject())
        } catch (_: Exception) { Result(error="The support service could not be reached.") }
    }
    fun list(sellerId: String)=request("GET","/api/v1/sellers/$sellerId/support/tickets?limit=100")
    fun detail(sellerId: String, ticketId: String)=request("GET","/api/v1/sellers/$sellerId/support/tickets/$ticketId")
    fun reply(sellerId: String, ticketId: String, body: String, requestId: String)=request("POST",
        "/api/v1/sellers/$sellerId/support/tickets/$ticketId/messages",
        JSONObject().put("body",body).put("clientMessageId",requestId))
    fun escalate(sellerId: String, ticketId: String)=request("POST",
        "/api/v1/sellers/$sellerId/support/tickets/$ticketId/escalate")
}
