package edu.msrit.canteen.customer

/** Persisted request counts only. No value here represents verified revenue or sales. */
data class SellerCloudAnalytics(val totalRequests: Int, val byStatus: Map<String, Int>,
                                val status: String, val from: String?, val to: String?,
                                val simulatedOrderCount: Int, val simulatedTotalPaise: Long,
                                val sandboxOrderCount: Int = 0, val sandboxTotalPaise: Long = 0L)
