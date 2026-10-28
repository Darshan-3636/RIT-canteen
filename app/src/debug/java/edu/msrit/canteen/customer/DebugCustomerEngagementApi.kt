package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONArray
import org.json.JSONObject

/** Existing authenticated customer-engagement API; invoke from Dispatchers.IO. */
internal object DebugCustomerEngagementApi {
    data class Result<T>(val value: T? = null, val error: String? = null)

    fun preferences(): Result<CustomerPreferences> = request("GET", "/api/v1/me/preferences", parse = ::preferences)
    fun savePreferences(value: CustomerPreferences): Result<CustomerPreferences> = request("PATCH", "/api/v1/me/preferences",
        JSONObject().put("operationalNotifications", value.operationalAlerts)
            .put("transactionalNotifications", value.transactionalEmail)
            .put("promotionalNotifications", value.promotionalAlerts)
            .put("personalizationConsent", value.personalizationConsent), ::preferences)
    private fun preferences(row: JSONObject) = CustomerPreferences(row.getBoolean("operationalNotifications"),
        row.getBoolean("promotionalNotifications"), row.getBoolean("transactionalNotifications"),
        row.getBoolean("personalizationConsent"))

    fun notifications(): Result<List<CustomerNotification>> = request("GET", "/api/v1/me/notifications?limit=100") { row ->
        row.getJSONArray("items").objects().map {
            CustomerNotification(it.getString("id"), it.getString("title"), it.getString("body"), sample = false)
        }
    }
    fun templates(): Result<List<NamedBulkTemplate>> = request("GET", "/api/v1/me/bulk-templates") { row ->
        row.getJSONArray("items").objects().map(::template)
    }
    fun saveTemplate(name: String, draft: BulkDraft, existingId: String? = null): Result<NamedBulkTemplate> {
        val body = JSONObject().put("name", name.trim()).put("lines", JSONArray().apply {
            draft.lines.forEach { put(JSONObject().put("itemId", it.itemId).put("quantity", it.quantity)) }
        })
        val path = if (existingId == null) {
            body.put("sellerId", draft.sellerId)
            "/api/v1/me/bulk-templates"
        } else "/api/v1/me/bulk-templates/$existingId"
        return request(if (existingId == null) "POST" else "PATCH", path, body, ::template)
    }
    fun removeTemplate(id: String): Result<Boolean> = request("DELETE", "/api/v1/me/bulk-templates/$id") { true }
    private fun template(row: JSONObject) = NamedBulkTemplate(row.getString("id"), row.getString("name"),
        row.getString("sellerId"), row.getJSONArray("lines").objects().map {
            BulkLine(it.getString("itemId"), it.getInt("quantity"))
        })

    fun tickets(): Result<List<CustomerSupportTicket>> = request("GET", "/api/v1/me/support/tickets?limit=100") { row ->
        row.getJSONArray("items").objects().map(::summary)
    }
    fun ticket(id: String): Result<CustomerSupportTicket> = request("GET", "/api/v1/me/support/tickets/$id", parse = ::detail)
    fun createTicket(category: CustomerIssueCategory, subject: String, description: String,
                     reference: String?, sellerId: String?, clientRequestId: String): Result<CustomerSupportTicket> =
        request("POST", "/api/v1/me/support/tickets", JSONObject()
            .put("category", category.name).put("subject", subject.trim()).put("message", description.trim())
            .put("sellerId", sellerId ?: JSONObject.NULL)
            .put("requestReference", reference?.trim()?.takeIf(String::isNotEmpty) ?: JSONObject.NULL)
            .put("clientRequestId", clientRequestId), ::detail)
    fun reply(id: String, message: String, clientMessageId: String): Result<Boolean> =
        request("POST", "/api/v1/me/support/tickets/$id/messages",
            JSONObject().put("body", message.trim()).put("clientMessageId", clientMessageId)) { true }
    fun escalate(id: String): Result<CustomerSupportTicket> =
        request("POST", "/api/v1/me/support/tickets/$id/escalate", parse = ::detail)

    private fun summary(row: JSONObject): CustomerSupportTicket {
        val status = row.getString("status")
        return CustomerSupportTicket(row.getString("id"), CustomerIssueCategory.valueOf(row.getString("category")),
            row.getString("subject"), "Open this ticket to load its conversation.",
            row.optString("requestReference").takeUnless { it.isBlank() || it == "null" },
            when (status) {
                "RESOLVED" -> CustomerTicketStatus.RESOLVED
                "CLOSED" -> CustomerTicketStatus.CLOSED
                "ESCALATED" -> CustomerTicketStatus.IN_PROGRESS
                else -> CustomerTicketStatus.OPEN
            }, localOnly = false, escalated = status == "ESCALATED")
    }
    private fun detail(row: JSONObject): CustomerSupportTicket {
        val ticket = summary(row.getJSONObject("ticket"))
        val messages = row.getJSONArray("messages").objects()
        return ticket.copy(description = messages.firstOrNull()?.optString("body").orEmpty(),
            replies = messages.drop(1).map { "${it.getString("authorRole")} · ${it.getString("body")}" })
    }
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    private fun <T> request(method: String, path: String, body: JSONObject? = null,
                            parse: (JSONObject) -> T): Result<T> {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try {
            val response = DebugBackendApi.request(method, path, body, token)
            if (response.status !in 200..299) Result(error = response.error ?: "Service request failed (${response.status}).")
            else Result(value = parse(response.body ?: JSONObject()))
        } catch (_: Exception) { Result(error = "The service did not confirm this request. Refresh before retrying.") }
    }
}
