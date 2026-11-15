package edu.msrit.canteen.customer

/** No development session or inbox transport is present in release builds. */
object DebugSellerInboxApi {
    data class Notice(val id: String, val title: String, val body: String, val read: Boolean)
    data class Result<T>(val value: T? = null, val error: String? = null)
    fun list(): Result<List<Notice>> = Result(error="Unavailable.")
    fun markRead(id: String): Result<Boolean> = Result(error="Unavailable.")
}
