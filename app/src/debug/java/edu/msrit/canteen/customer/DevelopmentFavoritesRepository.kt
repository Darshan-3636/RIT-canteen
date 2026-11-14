package edu.msrit.canteen.customer

import android.content.Context
import edu.msrit.canteen.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Separate, explicitly demo-only SharedPreferences; never a production account store. */
class DevelopmentFavoritesRepository(context: Context) : FavoritesRepository {
    private val preferences = context.getSharedPreferences("rit_debug_preview_favorites", Context.MODE_PRIVATE)
    private var revision by mutableIntStateOf(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationLock = Mutex()
    private var cloudUserId: String? = null
    private var cloudItems: Set<String> = emptySet()
    private var cloudSellers: Set<String> = emptySet()
    var cloudError by mutableStateOf<String?>(null)
        private set

    suspend fun activateCloud(userId: String) {
        if (!BuildConfig.RIT_CLOUD_DEMO) return
        withContext(Dispatchers.Main.immediate) {
            cloudUserId = userId
            cloudItems = emptySet(); cloudSellers = emptySet(); cloudError = null; revision++
        }
        refreshCloud(userId)
    }

    suspend fun refreshCloud(userId: String) {
        if (!BuildConfig.RIT_CLOUD_DEMO) return
        val result = withContext(Dispatchers.IO) { DebugFavoritesApi.load() }
        withContext(Dispatchers.Main.immediate) {
            if (cloudUserId == userId) {
                result.snapshot?.let { cloudItems = it.items; cloudSellers = it.sellers; revision++ }
                cloudError = result.error
            }
        }
    }

    fun clearCloud() {
        cloudUserId = null; cloudItems = emptySet(); cloudSellers = emptySet()
        cloudError = null; revision++
    }

    private fun key(customerId: String, kind: String): String = "${customerId.length}:$customerId:$kind"
    private fun read(customerId: String, kind: String): Set<String> {
        revision // observe local updates in Compose
        if (BuildConfig.RIT_CLOUD_DEMO) return if (customerId == cloudUserId)
            (if (kind == "items") cloudItems else cloudSellers) else emptySet()
        return preferences.getStringSet(key(customerId, kind), emptySet()).orEmpty().toSet()
    }

    override fun itemIds(customerId: String): Set<String> = read(customerId, "items")
    override fun sellerIds(customerId: String): Set<String> = read(customerId, "sellers")

    private fun toggle(customerId: String, kind: String, id: String) {
        if (BuildConfig.RIT_CLOUD_DEMO) {
            if (customerId != cloudUserId) return
            scope.launch {
                mutationLock.withLock {
                    val saved = id !in (if (kind == "items") cloudItems else cloudSellers)
                    val result = DebugFavoritesApi.set(if (kind == "items") "ITEM" else "SELLER", id, saved)
                    withContext(Dispatchers.Main.immediate) {
                        if (customerId == cloudUserId) {
                            result.snapshot?.let { cloudItems = it.items; cloudSellers = it.sellers; revision++ }
                            cloudError = result.error
                        }
                    }
                }
            }
            return
        }
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
