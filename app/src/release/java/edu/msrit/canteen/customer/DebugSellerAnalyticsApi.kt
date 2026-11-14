package edu.msrit.canteen.customer

/** Release has no debug cloud transport or privileged fixture data. */
object DebugSellerAnalyticsApi {
    data class Result(val value: SellerCloudAnalytics? = null, val error: String? = "Unavailable.")
    data class CsvResult(val csv: String? = null, val error: String? = "Unavailable.")
    fun summary(sellerId: String, from: java.time.LocalDate, to: java.time.LocalDate): Result = Result()
    fun exportCsv(sellerId: String, from: java.time.LocalDate, to: java.time.LocalDate): CsvResult = CsvResult()
}
