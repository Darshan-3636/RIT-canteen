package edu.msrit.canteen.customer

import android.content.Context

/** Release builds cannot read, seed, or mutate demo favorites. */
class DevelopmentFavoritesRepository(@Suppress("UNUSED_PARAMETER") context: Context) : FavoritesRepository {
    val cloudError: String? = null
    suspend fun activateCloud(@Suppress("UNUSED_PARAMETER") userId: String) = Unit
    suspend fun refreshCloud(@Suppress("UNUSED_PARAMETER") userId: String) = Unit
    fun clearCloud() = Unit
    override fun itemIds(customerId: String): Set<String> = emptySet()
    override fun sellerIds(customerId: String): Set<String> = emptySet()
    override fun toggleItem(customerId: String, itemId: String) = Unit
    override fun toggleSeller(customerId: String, sellerId: String) = Unit
    fun seedSample(customerId: String) = Unit
    fun clearCustomer(customerId: String) = Unit
    override fun resetAll() = Unit
}
