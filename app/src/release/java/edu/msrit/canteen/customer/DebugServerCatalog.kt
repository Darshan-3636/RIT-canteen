package edu.msrit.canteen.customer

/** Release has no local-loopback backend adapter or debug catalog mutation. */
object DebugServerCatalog {
    data class Snapshot(val sellers: List<Seller>, val categories: List<MenuCategory>, val items: List<MenuItem>)
    fun fetch(policySellerId: String? = null, force: Boolean = false): Snapshot =
        error("Local demo backend is unavailable in release")
    fun policy(sellerId: String, force: Boolean = false): Pair<SellerBulkPolicy, List<SellerClosure>> =
        error("Local demo backend is unavailable in release")
    fun applyPolicy(value: Pair<SellerBulkPolicy, List<SellerClosure>>) = Unit
    fun apply(snapshot: Snapshot) = Unit
    fun clear() = Unit
}
