package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Authenticated offer transport. All calls must run on Dispatchers.IO. */
object DebugPromotionsApi {
    data class Result<T>(val value: T? = null, val error: String? = null)
    data class OfferQuote(val subtotalPaise: Long, val discountPaise: Long, val totalPaise: Long)

    fun customerOffers(sellerId: String? = null): Result<List<Promotion>> = get(
        "/api/v1/me/offers" + (sellerId?.let { "?sellerId=$it" } ?: ""))

    fun sellerPromotions(sellerId: String): Result<List<Promotion>> =
        get("/api/v1/sellers/$sellerId/promotions")

    private fun get(path: String): Result<List<Promotion>> {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try {
            val response = DebugBackendApi.request("GET",path,token=token)
            if (response.status != 200) Result(error=response.error ?: "Offers could not be loaded.")
            else Result(value=response.body!!.getJSONArray("items").objects().map(::promotion))
        } catch (_: Exception) { Result(error="The service did not confirm the offer list.") }
    }

    fun save(value: Promotion): Result<Promotion> {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try {
            val path = "/api/v1/sellers/${value.sellerId}/promotions"
            val current = DebugBackendApi.request("GET",path,token=token)
            if (current.status != 200) return Result(error=current.error ?: "Could not load current offers.")
            val existing = current.body!!.getJSONArray("items").objects().firstOrNull { it.getString("id") == value.id }
            val body = JSONObject().put("title",value.title).put("description",value.description)
                .put("kind",value.kind.name).put("audience",value.audience.name)
                .put("startsAt",value.startsAt.toString()).put("endsAt",value.endsAt.toString())
                .put("published",value.published)
                .put("itemIds",JSONArray(value.itemIds.toList()))
                .put("categoryIds",JSONArray(value.categoryIds.toList()))
                .put("discountPercent",if(value.kind==PromotionKind.PERCENT_DISCOUNT) value.discountValue else JSONObject.NULL)
                .put("discountPaise",if(value.kind==PromotionKind.FIXED_DISCOUNT)
                    value.discountValue?.toLong()?.times(100L) else JSONObject.NULL)
            if (existing != null) body.put("expectedVersion",existing.getLong("version"))
            val response = DebugBackendApi.request(if(existing==null) "POST" else "PUT",
                if(existing==null) path else "$path/${value.id}",body,token)
            if (response.status !in 200..201) Result(error=response.error ?: "Could not save promotion.")
            else Result(value=promotion(response.body!!))
        } catch (_: Exception) { Result(error="The service did not confirm the promotion save.") }
    }

    fun quote(promotionId: String): Result<OfferQuote> {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try {
            val response = DebugBackendApi.request("POST","/api/v1/me/cart/individual/offer-quote",
                JSONObject().put("promotionId",promotionId),token)
            if (response.status != 200) Result(error=response.error ?: "Offer does not apply to this cart.")
            else Result(value=OfferQuote(response.body!!.getLong("subtotalPaise"),
                response.body.getLong("discountPaise"),response.body.getLong("totalPaise")))
        } catch (_: Exception) { Result(error="The service did not confirm the offer quote.") }
    }

    private fun promotion(row: JSONObject): Promotion {
        val kind = PromotionKind.valueOf(row.getString("kind"))
        val amount = when(kind) {
            PromotionKind.PERCENT_DISCOUNT -> row.getInt("discountPercent")
            PromotionKind.FIXED_DISCOUNT -> ((row.getLong("discountPaise") + 99L)/100L).toInt()
            else -> null
        }
        return Promotion(row.getString("id"),row.getString("sellerId"),row.getString("title"),
            row.getString("description"),kind,Instant.parse(row.getString("startsAt")),
            Instant.parse(row.getString("endsAt")),row.getBoolean("published"),
            row.getJSONArray("itemIds").strings(),row.getJSONArray("categoryIds").strings(),
            PromotionAudience.valueOf(row.getString("audience")),amount)
    }
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    private fun JSONArray.strings() = (0 until length()).mapTo(mutableSetOf()) { getString(it) }
}
