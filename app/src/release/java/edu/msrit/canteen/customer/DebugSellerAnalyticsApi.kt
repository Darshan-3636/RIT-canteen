package edu.msrit.canteen.customer

/** Release has no debug cloud transport or privileged fixture data. */
object DebugSellerAnalyticsApi {
    data class Result(val value: SellerCloudAnalytics? = null, val error: String? = "Unavailable.")
    fun summary(sellerId: String, from: java.time.LocalDate, to: java.time.LocalDate): Result = Result()
}
