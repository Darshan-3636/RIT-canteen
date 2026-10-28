package edu.msrit.canteen.customer

/** Release cannot use the debug local-backend transport. */
object DebugCommerceApi {
    data class Result<T>(val value: T? = null, val error: String? = "Local demo unavailable in release")
    fun individualCart(): Result<IndividualServerCart> = Result()
    fun replaceIndividualCart(expectedVersion: Long, lines: List<IndividualLine>): Result<IndividualServerCart> = Result()
    fun checkoutIndividualCart(expectedVersion: Long, idempotencyKey: String): Result<IndividualOrderPreview> = Result()
    fun individualOrders(): Result<List<IndividualOrderPreview>> = Result()
    fun sellerIndividualOrders(sellerId: String): Result<List<IndividualOrderPreview>> = Result()
    fun simulateIndividualPayment(id: String, outcome: String): Result<IndividualOrderPreview> = Result()
    fun transitionIndividual(sellerId: String, id: String, status: String): Result<IndividualOrderPreview> = Result()
    fun submitIndividual(draft: IndividualDraft): Result<IndividualOrderPreview> = Result()
    fun bulkRequests(sellerId: String? = null): Result<List<BulkRequestRecord>> = Result()
    fun submitBulk(draft: BulkDraft, idempotencyKey: String): Result<BulkRequestRecord> = Result()
    fun customerApprove(requestId: String, expectedVersion: Int): Result<BulkRequestRecord> = Result()
    fun sellerApprove(sellerId: String, requestId: String, expectedVersion: Int): Result<BulkRequestRecord> = Result()
    fun specialDecision(sellerId: String, requestId: String, expectedVersion: Int,
                        approved: Boolean): Result<BulkRequestRecord> = Result()
    fun sellerCounteroffer(sellerId: String, requestId: String, expectedVersion: Int,
                           proposal: BulkProposal): Result<BulkRequestRecord> = Result()
    fun withdraw(requestId: String, expectedVersion: Int): Result<BulkRequestRecord> = Result()
}
