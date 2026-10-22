package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Resettable, in-memory seller administration. No invitation, upload, or merchant request leaves the device. */
object DevelopmentSellerBusiness : SellerBusinessRepository {
    private const val SELLER = "north-counter"
    private val fixtures = listOf(
        SellerEmployee("debug-owner", SELLER, "North Counter Owner · demo", SellerEmployeeRole.OWNER),
        SellerEmployee("debug-manager", SELLER, "North Counter Manager · demo", SellerEmployeeRole.MANAGER,
            "debug-owner", setOf(SellerCapability.VIEW_REQUESTS, SellerCapability.RESPOND,
                SellerCapability.HISTORY, SellerCapability.CATALOG, SellerCapability.AVAILABILITY,
                SellerCapability.HOURS, SellerCapability.PROMOTIONS, SellerCapability.EMPLOYEES)),
        SellerEmployee("debug-staff", SELLER, "North Counter Staff · view-only demo", SellerEmployeeRole.STAFF,
            "debug-manager", setOf(SellerCapability.VIEW_REQUESTS)),
        SellerEmployee("debug-staff-ops", SELLER, "Operations Staff · delegated demo", SellerEmployeeRole.STAFF,
            "debug-manager", setOf(SellerCapability.VIEW_REQUESTS, SellerCapability.RESPOND,
                SellerCapability.AVAILABILITY)),
    )
    override var employees by mutableStateOf(fixtures)
        private set
    override var policyOverrides by mutableStateOf<Map<String, SellerBulkPolicy>>(emptyMap())
        private set
    override var closureReasons by mutableStateOf<Map<String, List<SellerClosure>>>(emptyMap())
        private set
    private var hiddenCategories by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    override val hiddenCategoryIds: Map<String, Set<String>> get() = hiddenCategories

    override fun capabilities(userId: String, sellerId: String): Set<SellerCapability> = employees
        .find { it.id == userId && it.sellerId == sellerId }
        ?.let { effectiveCapabilities(it, employees) }.orEmpty()

    private fun allowed(userId: String, sellerId: String, capability: SellerCapability) =
        capability in capabilities(userId, sellerId)

    override fun saveItem(userId: String, item: MenuItem): Boolean {
        if (!allowed(userId, item.sellerId, SellerCapability.CATALOG)) return false
        val prior = PreviewCatalog.items.find { it.id == item.id && it.sellerId == item.sellerId }
        if (prior != null && !allowed(userId, item.sellerId, SellerCapability.AVAILABILITY) &&
            (prior.availability != item.availability || prior.published != item.published ||
                prior.unavailableVisibility != item.unavailableVisibility)) return false
        val seller = PreviewCatalog.sellers.find { it.id == item.sellerId }
        if (validateSellerItem(item, seller, PreviewCatalog.categories) != null) return false
        if (PreviewCatalog.items.any { it.id == item.id && it.sellerId != item.sellerId }) return false
        val updated = PreviewCatalog.items.filterNot { it.id == item.id } + item.copy(name = item.name.trim())
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers, PreviewCatalog.categories, updated)
        return true
    }

    override fun setItemState(userId: String, sellerId: String, itemId: String, available: Boolean,
                              published: Boolean, showUnavailable: Boolean): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.AVAILABILITY)) return false
        val item = PreviewCatalog.items.find { it.id == itemId && it.sellerId == sellerId } ?: return false
        val revised = item.copy(availability = if (available) ItemAvailability.AVAILABLE else ItemAvailability.UNAVAILABLE,
            published = published, unavailableVisibility = if (showUnavailable) UnavailableVisibility.SHOW else UnavailableVisibility.HIDE)
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers, PreviewCatalog.categories,
            PreviewCatalog.items.map { if (it.id == itemId) revised else it })
        return true
    }

    override fun saveCategory(userId: String, sellerId: String, categoryId: String?, name: String): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.CATALOG) || name.isBlank()) return false
        val seller = PreviewCatalog.sellers.find { it.id == sellerId } ?: return false
        val old = categoryId?.let { id -> PreviewCatalog.categories.find { it.id == id &&
            (id in seller.categoryIds || id in hiddenCategories[sellerId].orEmpty()) } }
        if (categoryId != null && old == null) return false
        if (old == null) {
            val id = "${sellerId}-category-${java.util.UUID.randomUUID()}"
            PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == sellerId)
                it.copy(categoryIds = it.categoryIds + id) else it },
                PreviewCatalog.categories + MenuCategory(id, name.trim()), PreviewCatalog.items)
        } else if (PreviewCatalog.sellers.any { it.id != sellerId && old.id in it.categoryIds }) {
            // Shared seed categories must not be renamed for another seller.
            val id = "${sellerId}-category-${java.util.UUID.randomUUID()}"
            if (old.id in hiddenCategories[sellerId].orEmpty()) hiddenCategories = hiddenCategories +
                (sellerId to ((hiddenCategories[sellerId].orEmpty() - old.id) + id))
            PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == sellerId)
                it.copy(categoryIds = it.categoryIds.map { existing -> if (existing == old.id) id else existing }.toSet()) else it },
                PreviewCatalog.categories + MenuCategory(id, name.trim()),
                PreviewCatalog.items.map { if (it.sellerId == sellerId && it.categoryId == old.id) it.copy(categoryId = id) else it })
            DevelopmentPromotions.replaceDemo(DevelopmentPromotions.promotions.map { promotion ->
                if (promotion.sellerId == sellerId && old.id in promotion.categoryIds)
                    promotion.copy(categoryIds = promotion.categoryIds - old.id + id) else promotion
            })
        } else {
            PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers,
                PreviewCatalog.categories.map { if (it.id == old.id) it.copy(name = name.trim()) else it }, PreviewCatalog.items)
        }
        return true
    }

    override fun moveCategory(userId: String, sellerId: String, categoryId: String, delta: Int): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.CATALOG)) return false
        val seller = PreviewCatalog.sellers.find { it.id == sellerId } ?: return false
        val ids = seller.categoryIds.toMutableList()
        val old = ids.indexOf(categoryId)
        val next = old + delta
        if (old < 0 || next !in ids.indices) return false
        java.util.Collections.swap(ids, old, next)
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == sellerId)
            it.copy(categoryIds = ids.toCollection(linkedSetOf())) else it }, PreviewCatalog.categories, PreviewCatalog.items)
        return true
    }

    override fun hideCategory(userId: String, sellerId: String, categoryId: String, hidden: Boolean): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.CATALOG)) return false
        val seller = PreviewCatalog.sellers.find { it.id == sellerId } ?: return false
        if (PreviewCatalog.categories.none { it.id == categoryId } ||
            (categoryId !in seller.categoryIds && categoryId !in hiddenCategories[sellerId].orEmpty())) return false
        hiddenCategories = hiddenCategories + (sellerId to if (hidden)
            hiddenCategories[sellerId].orEmpty() + categoryId else hiddenCategories[sellerId].orEmpty() - categoryId)
        val ids = if (hidden) seller.categoryIds - categoryId else seller.categoryIds + categoryId
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == sellerId)
            it.copy(categoryIds = ids) else it }, PreviewCatalog.categories, PreviewCatalog.items)
        return true
    }

    override fun removeCategory(userId: String, sellerId: String, categoryId: String): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.CATALOG) ||
            PreviewCatalog.items.any { it.sellerId == sellerId && it.categoryId == categoryId }) return false
        val seller = PreviewCatalog.sellers.find { it.id == sellerId } ?: return false
        if (categoryId !in seller.categoryIds && categoryId !in hiddenCategories[sellerId].orEmpty()) return false
        hiddenCategories = hiddenCategories + (sellerId to (hiddenCategories[sellerId].orEmpty() - categoryId))
        val sellers = PreviewCatalog.sellers.map { if (it.id == sellerId)
            it.copy(categoryIds = it.categoryIds - categoryId) else it }
        PreviewCatalog.applyDemoSnapshot(sellers, PreviewCatalog.categories.filter { category ->
            category.id != categoryId || sellers.any { category.id in it.categoryIds } }, PreviewCatalog.items)
        return true
    }

    override fun savePolicy(userId: String, policy: SellerBulkPolicy, reasons: List<SellerClosure>): Boolean {
        if (!allowed(userId, policy.sellerId, SellerCapability.HOURS) || !validHours(policy.weeklyHours) ||
            reasons.any { it.reason.isBlank() || (it.holiday && it.date !in policy.holidays) ||
                (!it.holiday && it.date !in policy.temporaryClosures) }) return false
        policyOverrides = policyOverrides + (policy.sellerId to policy)
        closureReasons = closureReasons + (policy.sellerId to reasons)
        val hours = policy.weeklyHours.entries.sortedBy { it.key.value }.joinToString(" · ") {
            "${it.key.name.take(3)} ${it.value.joinToString { window -> "${window.opens}–${window.closes}" }}" }
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == policy.sellerId)
            it.copy(hours = SellerHours(hours.ifEmpty { "Closed weekly" })) else it },
            PreviewCatalog.categories, PreviewCatalog.items)
        return true
    }

    override fun saveProfile(userId: String, sellerId: String, profile: SellerPublicProfile): Boolean {
        if (!allowed(userId, sellerId, SellerCapability.PROFILE) || profile.name.isBlank()) return false
        if (PreviewCatalog.sellers.none { it.id == sellerId }) return false
        PreviewCatalog.applyDemoSnapshot(PreviewCatalog.sellers.map { if (it.id == sellerId)
            it.copy(name = profile.name.trim(), description = profile.description.trim(),
                location = profile.location.trim().ifEmpty { null }, localImageUri = profile.localImageUri) else it }, PreviewCatalog.categories, PreviewCatalog.items)
        return true
    }

    override fun savePromotion(userId: String, promotion: Promotion): Boolean {
        if (!allowed(userId, promotion.sellerId, SellerCapability.PROMOTIONS) || promotion.title.isBlank() ||
            PreviewCatalog.sellers.none { it.id == promotion.sellerId } ||
            promotion.itemIds.any { id -> PreviewCatalog.items.none { it.id == id && it.sellerId == promotion.sellerId } } ||
            promotion.categoryIds.any { id -> id !in PreviewCatalog.sellers.first { it.id == promotion.sellerId }.categoryIds } ||
            DevelopmentPromotions.promotions.any { it.id == promotion.id && it.sellerId != promotion.sellerId }) return false
        DevelopmentPromotions.replaceDemo(DevelopmentPromotions.promotions.filterNot { it.id == promotion.id } +
            promotion.copy(title = promotion.title.trim()))
        return true
    }

    override fun saveEmployee(userId: String, employee: SellerEmployee): Boolean {
        val actor = employees.find { it.id == userId && it.sellerId == employee.sellerId } ?: return false
        val previous = employees.find { it.id == employee.id }
        if (previous != null && !canManageEmployee(actor, previous, employees)) return false
        if (previous == null && (actor.role == SellerEmployeeRole.STAFF ||
            (actor.role == SellerEmployeeRole.MANAGER && employee.role != SellerEmployeeRole.STAFF))) return false
        if (previous != null && previous.role != employee.role) return false
        val owner = employees.find { it.sellerId == employee.sellerId && it.role == SellerEmployeeRole.OWNER }
        if (employee.role == SellerEmployeeRole.OWNER || employee.id == userId ||
            employee.sellerId != actor.sellerId || employee.name.isBlank() ||
            (employee.role == SellerEmployeeRole.MANAGER && employee.supervisorId != owner?.id) ||
            (employee.role == SellerEmployeeRole.STAFF && actor.role == SellerEmployeeRole.MANAGER && employee.supervisorId != actor.id) ||
            employee.granted.any { it !in capabilities(employee.supervisorId.orEmpty(), employee.sellerId) }) return false
        if (employee.role == SellerEmployeeRole.STAFF && employees.none { it.id == employee.supervisorId &&
            it.role == SellerEmployeeRole.MANAGER && it.sellerId == employee.sellerId }) return false
        employees = employees.filterNot { it.id == employee.id } + employee
        return true
    }

    override fun removeEmployee(userId: String, sellerId: String, employeeId: String): Boolean {
        val actor = employees.find { it.id == userId && it.sellerId == sellerId }
        val target = employees.find { it.id == employeeId && it.sellerId == sellerId }
        if (!canManageEmployee(actor, target, employees)) return false
        employees = employees.map { if (it.id == employeeId) it.copy(active = false) else it }
        return true
    }

    override fun reset() {
        employees = fixtures
        policyOverrides = emptyMap()
        closureReasons = emptyMap()
        hiddenCategories = emptyMap()
        PreviewCatalog.resetDemoSnapshot()
        DevelopmentPromotions.resetDemo()
    }
}
