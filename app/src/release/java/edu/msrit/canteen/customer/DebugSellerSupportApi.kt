package edu.msrit.canteen.customer

import org.json.JSONObject

object DebugSellerSupportApi {
    data class Result(val value: JSONObject? = null, val error: String? = null)
    fun list(sellerId: String)=Result(error="Unavailable.")
    fun detail(sellerId: String, ticketId: String)=Result(error="Unavailable.")
    fun reply(sellerId: String, ticketId: String, body: String, requestId: String)=Result(error="Unavailable.")
    fun escalate(sellerId: String, ticketId: String)=Result(error="Unavailable.")
}
