package edu.msrit.canteen.customer

/** Customer-scoped presentation port. A real authenticated source can replace the debug adapter. */
interface FavoritesRepository {
    fun itemIds(customerId: String): Set<String>
    fun sellerIds(customerId: String): Set<String>
    fun toggleItem(customerId: String, itemId: String)
    fun toggleSeller(customerId: String, sellerId: String)
    fun resetAll()
}
