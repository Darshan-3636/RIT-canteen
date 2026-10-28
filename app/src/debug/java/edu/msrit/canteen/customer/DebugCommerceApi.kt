package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.BuildConfig
import java.time.Instant
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/** Blocking local-demo commerce calls. Invoke only from Dispatchers.IO. */
object DebugCommerceApi {
    data class Result<T>(val value: T? = null, val error: String? = null)

    fun individualCart(): Result<IndividualServerCart> = withToken { token ->
        val response = DebugBackendApi.request("GET", "/api/v1/me/cart/individual", token = token)
        if (response.status != 200) Result(error = response.error ?: "Could not load your cart.")
        else Result(value = parseIndividualCart(response.body!!))
    }

    fun replaceIndividualCart(expectedVersion: Long, lines: List<IndividualLine>): Result<IndividualServerCart> =
        withToken { token ->
            val rows = JSONArray()
            lines.forEach { rows.put(JSONObject().put("itemId", it.itemId).put("quantity", it.quantity)) }
            val response = DebugBackendApi.request("PUT", "/api/v1/me/cart/individual",
                JSONObject().put("expectedVersion", expectedVersion).put("lines", rows), token)
            if (response.status != 200) Result(error = response.error ?: "Cart changed. Refresh and try again.")
            else Result(value = parseIndividualCart(response.body!!))
        }

    fun checkoutIndividualCart(expectedVersion: Long, idempotencyKey: String): Result<IndividualOrderPreview> =
        withToken { token ->
            val response = DebugBackendApi.request("POST", "/api/v1/me/cart/individual/checkout",
                JSONObject().put("expectedVersion", expectedVersion), token, idempotencyKey)
            if (response.status !in 200..201) Result(error = response.error ?: "Could not save the demo order.")
            else Result(value = parseIndividualOrder(response.body!!))
        }

    private fun parseIndividualCart(row: JSONObject): IndividualServerCart {
        val items = row.getJSONArray("lines")
        return IndividualServerCart(row.getLong("version"), row.getLong("subtotalPaise"),
            row.getBoolean("readyForCheckout"), (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                IndividualServerCartLine(item.getString("itemId"), item.getInt("quantity"),
                    item.getLong("unitPricePaise"), item.getBoolean("available"))
            })
    }

    fun individualOrders(): Result<List<IndividualOrderPreview>> = withToken { token ->
        val response = DebugBackendApi.request("GET", "/api/v1/me/individual-demo-orders", token = token)
        if (response.status != 200) Result(error = response.error ?: "Could not load saved orders.")
        else Result(value = parseIndividualOrders(response.body!!.getJSONArray("orders")))
    }

    fun sellerIndividualOrders(sellerId: String): Result<List<IndividualOrderPreview>> = withToken { token ->
        val response = DebugBackendApi.request("GET", "/api/v1/sellers/$sellerId/individual-demo-orders", token = token)
        if (response.status != 200) Result(error = response.error ?: "Could not load seller demo orders.")
        else Result(value = parseIndividualOrders(response.body!!.getJSONArray("orders")))
    }

    fun simulateIndividualPayment(id: String, outcome: String): Result<IndividualOrderPreview> = withToken { token ->
        val response = DebugBackendApi.request("POST", "/api/v1/me/individual-demo-orders/$id/simulate-payment",
            JSONObject().put("outcome", outcome), token)
        if (response.status != 200) Result(error = response.error ?: "Demo payment simulation failed.")
        else Result(value = parseIndividualOrder(response.body!!))
    }

    fun transitionIndividual(sellerId: String, id: String, status: String): Result<IndividualOrderPreview> = withToken { token ->
        val response = DebugBackendApi.request("POST", "/api/v1/sellers/$sellerId/individual-demo-orders/$id/demo-status",
            JSONObject().put("status", status), token)
        if (response.status != 200) Result(error = response.error ?: "Demo order transition failed.")
        else Result(value = parseIndividualOrder(response.body!!))
    }

    fun submitIndividual(draft: IndividualDraft): Result<IndividualOrderPreview> = withToken { token ->
        val lines = JSONArray()
        draft.lines.forEach { lines.put(JSONObject().put("itemId", it.itemId).put("quantity", it.quantity)) }
        val response = DebugBackendApi.request("POST", "/api/v1/me/individual-demo-orders",
            JSONObject().put("lines", lines), token)
        if (response.status !in 200..201) Result(error = response.error ?: "Could not save the demo order.")
        else Result(value = parseIndividualOrder(response.body!!))
    }

    fun bulkRequests(sellerId: String? = null): Result<List<BulkRequestRecord>> = withToken { token ->
        val path = if (sellerId == null) "/api/v1/me/bulk/requests"
            else "/api/v1/sellers/" + sellerId + "/bulk/requests"
        val response = DebugBackendApi.request("GET", path, token = token)
        if (response.status != 200) Result(error = response.error ?: "Could not load bulk requests.")
        else {
            val rows = response.body!!.getJSONArray("requests")
            Result(value = (0 until rows.length()).map { parseBulk(rows.getJSONObject(it)) })
        }
    }

    fun submitBulk(draft: BulkDraft, idempotencyKey: String): Result<BulkRequestRecord> = withToken { token ->
        val sellerId = draft.sellerId ?: return@withToken Result(error = "Choose one seller.")
        val pickup = draft.pickup ?: return@withToken Result(error = "Choose a pickup time.")
        val lines = JSONArray()
        draft.lines.forEach { lines.put(JSONObject().put("itemId", it.itemId).put("quantity", it.quantity)) }
        val body = JSONObject().put("sellerId", sellerId).put("lines", lines)
            .put("pickupAt", pickup.atZone(RIT_TIME_ZONE).toInstant().toString())
        if (draft.specialPickup) body.put("specialPickupReason", draft.specialReason)
        val response = DebugBackendApi.request("POST", "/api/v1/bulk/requests", body, token,
            idempotencyKey = idempotencyKey)
        if (response.status !in 200..201) Result(error = response.error ?: "Could not submit bulk request.")
        else Result(value = parseBulk(response.body!!))
    }

    fun customerApprove(requestId: String, expectedVersion: Int): Result<BulkRequestRecord> =
        bulkMutation("/api/v1/bulk/requests/" + requestId + "/customer-approval",
            JSONObject().put("expectedVersion", expectedVersion))

    fun sellerApprove(sellerId: String, requestId: String, expectedVersion: Int): Result<BulkRequestRecord> =
        bulkMutation("/api/v1/sellers/" + sellerId + "/bulk/requests/" + requestId + "/seller-approval",
            JSONObject().put("expectedVersion", expectedVersion))

    fun specialDecision(sellerId: String, requestId: String, expectedVersion: Int,
                        approved: Boolean): Result<BulkRequestRecord> =
        bulkMutation("/api/v1/sellers/" + sellerId + "/bulk/requests/" + requestId + "/special-pickup-decision",
            JSONObject().put("expectedVersion", expectedVersion).put("approved", approved))

    fun sellerCounteroffer(sellerId: String, requestId: String, expectedVersion: Int,
                           proposal: BulkProposal): Result<BulkRequestRecord> {
        val lines = JSONArray()
        proposal.lines.forEach { lines.put(JSONObject().put("itemId", it.itemId).put("quantity", it.quantity)) }
        val body = JSONObject().put("expectedVersion", expectedVersion).put("lines", lines)
            .put("pickupAt", proposal.pickup.atZone(RIT_TIME_ZONE).toInstant().toString())
        if (proposal.specialPickup) body.put("specialPickupReason", proposal.specialReason)
        return bulkMutation("/api/v1/sellers/" + sellerId + "/bulk/requests/" + requestId + "/counteroffers", body)
    }

    fun withdraw(requestId: String, expectedVersion: Int): Result<BulkRequestRecord> =
        bulkMutation("/api/v1/bulk/requests/" + requestId + "/withdraw",
            JSONObject().put("expectedVersion", expectedVersion))

    private fun bulkMutation(path: String, body: JSONObject): Result<BulkRequestRecord> = withToken { token ->
        val response = DebugBackendApi.request("POST", path, body, token)
        if (response.status != 200) Result(error = response.error ?: "Request update failed.")
        else Result(value = parseBulk(response.body!!))
    }

    private fun parseIndividualOrders(rows: JSONArray): List<IndividualOrderPreview> =
        (0 until rows.length()).map { parseIndividualOrder(rows.getJSONObject(it)) }

    private fun parseIndividualOrder(row: JSONObject): IndividualOrderPreview {
        check(row.optBoolean("demoOnly") && !row.optBoolean("paymentVerified"))
        val rows = row.getJSONArray("lines")
        return IndividualOrderPreview(row.getString("id"), local(row.getString("createdAt")),
            (0 until rows.length()).map { index ->
                val line = rows.getJSONObject(index)
                QuoteLineSnapshot(line.getString("itemId"), line.getString("itemName"),
                    line.getInt("quantity"), line.getLong("unitPricePaise"))
            }, row.getLong("subtotalPaise"), locallySaved = true, status = row.getString("status"),
            demoPaymentOutcome = row.optString("demoPaymentOutcome", "NOT_ATTEMPTED"))
    }

    private fun parseBulk(row: JSONObject): BulkRequestRecord {
        val sellerId = row.getString("sellerId")
        val pickup = local(row.getString("pickupAt"))
        val version = row.getInt("version")
        val status = QuoteStage.valueOf(row.getString("status"))
        val reason = row.optString("specialPickupReason").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val rows = row.getJSONArray("lines")
        val lines = (0 until rows.length()).map { index ->
            val line = rows.getJSONObject(index)
            QuoteLineSnapshot(line.getString("itemId"), line.getString("itemName"),
                line.getInt("quantity"), line.getLong("unitPricePaise"))
        }
        val revision = QuoteRevision(version, if (version == 1) QuoteActor.CUSTOMER else QuoteActor.SELLER,
            lines, pickup, reason.isNotBlank(), row.getLong("subtotalPaise"), "", LocalDateTime.now(RIT_TIME_ZONE),
            reason)
        val sellerApproved = row.getBoolean("sellerApproved")
        val customerApproved = row.getBoolean("customerApproved")
        val specialApproved = if (row.isNull("specialPickupApproved")) null else row.getBoolean("specialPickupApproved")
        val thread = QuoteThread(sellerId, row.getString("id"), listOf(revision), status,
            if (sellerApproved) version else null, if (customerApproved) version else null,
            local(row.getString("expiresAt")), if (specialApproved == true) version else null)
        return BulkRequestRecord(thread, DevelopmentDemoAccounts.serverIdentity?.userId.orEmpty(),
            "Customer", BulkDraft(sellerId, lines.map { BulkLine(it.itemId, it.quantity) }, pickup,
                reason.isNotBlank(), reason), row.getInt("minimumUnits"), LocalDateTime.now(RIT_TIME_ZONE))
    }

    private fun local(instant: String): LocalDateTime =
        LocalDateTime.ofInstant(Instant.parse(instant), RIT_TIME_ZONE)

    private inline fun <T> withToken(action: (String) -> Result<T>): Result<T> {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try { action(token) } catch (_: Exception) {
            Result(error = if (BuildConfig.RIT_CLOUD_DEMO) "Cloud service unavailable. Retry shortly."
                else "Local backend unavailable.")
        }
    }
}
