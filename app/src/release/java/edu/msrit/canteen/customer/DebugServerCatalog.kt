package edu.msrit.canteen.customer

/** Release has no local-loopback backend adapter or debug catalog mutation. */
object DebugServerCatalog {
    data class Snapshot(val sellers: List<Seller>, val categories: List<MenuCategory>, val items: List<MenuItem>)
    fun fetch(): Snapshot = error("Local demo backend is unavailable in release")
    fun apply(snapshot: Snapshot) = Unit
    fun clear() = Unit
}
