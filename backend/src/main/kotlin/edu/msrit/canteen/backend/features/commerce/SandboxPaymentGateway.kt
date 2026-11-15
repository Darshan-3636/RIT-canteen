package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.RuntimeEnvironment
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Credentials and provider calls never cross into Android or the database migration. */
interface SandboxPaymentGateway {
    val merchantId: String?
    fun create(merchantOrderId: String, amountPaise: Long): SandboxCreatedOrder
    fun status(merchantOrderId: String): SandboxProviderStatus
}

data class SandboxCreatedOrder(val providerOrderId: String, val sdkToken: String,
                               val providerState: String, val expiresAt: Instant)
data class SandboxProviderStatus(val providerState: String, val amountPaise: Long,
                                 val transactionReference: String? = null)

object UnavailableSandboxPaymentGateway : SandboxPaymentGateway {
    override val merchantId: String? = null
    override fun create(merchantOrderId: String, amountPaise: Long): SandboxCreatedOrder =
        throw SandboxGatewayUnavailable()
    override fun status(merchantOrderId: String): SandboxProviderStatus = throw SandboxGatewayUnavailable()
}

class SandboxGatewayUnavailable : RuntimeException("PhonePe UAT is not configured or temporarily unavailable")

/** PhonePe's documented Standard Checkout mobile API. This client has no production endpoint. */
class PhonePeUatGateway private constructor(
    override val merchantId: String,
    private val clientId: String,
    private val clientVersion: String,
    private val clientSecret: String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : SandboxPaymentGateway {
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant = Instant.EPOCH

    @Synchronized private fun authToken(): String {
        val now = Instant.now()
        cachedToken?.takeIf { tokenExpiresAt.isAfter(now.plusSeconds(30)) }?.let { return it }
        val form = mapOf("client_id" to clientId, "client_version" to clientVersion,
            "client_secret" to clientSecret, "grant_type" to "client_credentials")
            .entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
        val response = send("/v1/oauth/token", form, "application/x-www-form-urlencoded")
        val token = response["access_token"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: throw SandboxGatewayUnavailable()
        val expiry = response["expires_at"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: throw SandboxGatewayUnavailable()
        cachedToken = token
        tokenExpiresAt = Instant.ofEpochSecond(expiry)
        return token
    }

    override fun create(merchantOrderId: String, amountPaise: Long): SandboxCreatedOrder {
        require(merchantOrderId.matches(Regex("[A-Za-z0-9_-]{1,63}")) && amountPaise >= 100)
        val body = buildJsonObject {
            put("merchantOrderId", merchantOrderId)
            put("amount", amountPaise)
            put("expireAfter", 1200)
            put("paymentFlow", buildJsonObject { put("type", "PG_CHECKOUT") })
        }.toString()
        val row = send("/checkout/v2/sdk/order", body, "application/json", authToken())
        val orderId = row["orderId"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: throw SandboxGatewayUnavailable()
        val sdkToken = row["token"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: throw SandboxGatewayUnavailable()
        val state = row["state"]?.jsonPrimitive?.content ?: throw SandboxGatewayUnavailable()
        if (state !in setOf("CREATED", "PENDING")) throw SandboxGatewayUnavailable()
        val expiry = row["expireAt"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: throw SandboxGatewayUnavailable()
        return SandboxCreatedOrder(orderId, sdkToken, state, Instant.ofEpochMilli(expiry))
    }

    override fun status(merchantOrderId: String): SandboxProviderStatus {
        require(merchantOrderId.matches(Regex("[A-Za-z0-9_-]{1,63}")))
        val row = send("/checkout/v2/order/$merchantOrderId/status", null, "application/json", authToken())
        val state = row["state"]?.jsonPrimitive?.content ?: throw SandboxGatewayUnavailable()
        if (state !in setOf("PENDING", "FAILED", "COMPLETED")) throw SandboxGatewayUnavailable()
        val amount = row["amount"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: throw SandboxGatewayUnavailable()
        val transaction = row["paymentDetails"]?.let { element ->
            element as? kotlinx.serialization.json.JsonArray
        }?.firstOrNull()?.jsonObject?.get("transactionId")?.jsonPrimitive?.content
        return SandboxProviderStatus(state, amount, transaction)
    }

    private fun send(path: String, body: String?, contentType: String, token: String? = null) = try {
        val request = HttpRequest.newBuilder(URI("$BASE$path"))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "O-Bearer $token") }
            .apply {
                if (body == null) GET()
                else header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body))
            }.build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw SandboxGatewayUnavailable()
        Json.parseToJsonElement(response.body()).jsonObject
    } catch (_: Exception) {
        // Do not include provider responses or tokens in application errors or logs.
        throw SandboxGatewayUnavailable()
    }

    companion object {
        private const val BASE = "https://api-preprod.phonepe.com/apis/pg-sandbox"
        private fun urlEncode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

        fun fromEnvironment(environment: RuntimeEnvironment, env: Map<String, String> = System.getenv()): SandboxPaymentGateway {
            if (environment != RuntimeEnvironment.CLOUD_DEMO) return UnavailableSandboxPaymentGateway
            val merchant = env["PHONEPE_UAT_MERCHANT_ID"]?.trim()?.takeIf(String::isNotBlank)
            val id = env["PHONEPE_UAT_CLIENT_ID"]?.trim()?.takeIf(String::isNotBlank)
            val version = env["PHONEPE_UAT_CLIENT_VERSION"]?.trim()?.takeIf(String::isNotBlank)
            val secret = env["PHONEPE_UAT_CLIENT_SECRET"]?.takeIf(String::isNotBlank)
            if (merchant == null || id == null || version == null || secret == null)
                return UnavailableSandboxPaymentGateway
            return PhonePeUatGateway(merchant, id, version, secret)
        }
    }
}
