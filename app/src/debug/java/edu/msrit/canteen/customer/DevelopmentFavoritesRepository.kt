package edu.msrit.canteen.customer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Separate, explicitly demo-only SharedPreferences; never a production account store. */
class DevelopmentFavoritesRepository(context: Context) : FavoritesRepository {
    private val preferences = context.getSharedPreferences("rit_debug_preview_favorites", Context.MODE_PRIVATE)
    private var revision by mutableIntStateOf(0)

    private fun key(customerId: String, kind: String): String = "${customerId.length}:$customerId:$kind"
    private fun read(customerId: String, kind: String): Set<String> {
        revision // observe local updates in Compose
        return preferences.getStringSet(key(customerId, kind), emptySet()).orEmpty().toSet()
    }

    override fun itemIds(customerId: String): Set<String> = read(customerId, "items")
    override fun sellerIds(customerId: String): Set<String> = read(customerId, "sellers")

    private fun toggle(customerId: String, kind: String, id: String) {
        val next = read(customerId, kind).toMutableSet()
        if (!next.add(id)) next.remove(id)
        preferences.edit { putStringSet(key(customerId, kind), next) }
        revision++
    }

    override fun toggleItem(customerId: String, itemId: String) = toggle(customerId, "items", itemId)
    override fun toggleSeller(customerId: String, sellerId: String) = toggle(customerId, "sellers", sellerId)

    fun seedSample(customerId: String) {
        preferences.edit {
            putStringSet(key(customerId, "items"), setOf("masala-dosa", "veg-biryani"))
            putStringSet(key(customerId, "sellers"), setOf(PreviewCatalog.MAIN_ID))
        }
        revision++
    }

    fun clearCustomer(customerId: String) {
        preferences.edit {
            remove(key(customerId, "items"))
            remove(key(customerId, "sellers"))
        }
        revision++
    }

    override fun resetAll() {
        preferences.edit { clear() }
        revision++
    }
}
