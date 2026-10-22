package edu.msrit.canteen.customer

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Client-side demo vocabulary. Backend authorization must independently enforce these grants. */
enum class SellerCapability(val wire: String, val label: String) {
    VIEW_REQUESTS("bulk.view", "View requests"), RESPOND("bulk.respond", "Respond to requests"),
    HISTORY("operations.history", "View history"), CATALOG("catalog.edit", "Manage items and categories"),
    AVAILABILITY("availability.edit", "Change item availability"), HOURS("hours.edit", "Edit hours and closures"),
    PROMOTIONS("promotions.manage", "Manage promotions"), EMPLOYEES("employees.manage", "Manage team"),
    PROFILE("profile.edit", "Edit public profile"), ANALYTICS("analytics.view", "View sales insights"),
    REPORTS("reports.export", "Configure reports");
    companion object { fun fromWire(value: String) = entries.firstOrNull { it.wire == value } }
}

enum class SellerEmployeeRole { OWNER, MANAGER, STAFF }

data class SellerEmployee(
    val id: String, val sellerId: String, val name: String, val role: SellerEmployeeRole,
    val supervisorId: String? = null, val granted: Set<SellerCapability> = emptySet(),
    val active: Boolean = true,
)

/** Capabilities are intersected at every link, so revoking a manager grant cascades to staff. */
fun effectiveCapabilities(employee: SellerEmployee, directory: List<SellerEmployee>): Set<SellerCapability> {
    if (!employee.active) return emptySet()
    if (employee.role == SellerEmployeeRole.OWNER) return SellerCapability.entries.toSet()
    val parent = directory.find { it.id == employee.supervisorId && it.sellerId == employee.sellerId }
        ?: return emptySet()
    if (parent.id == employee.id || parent.role.ordinal >= employee.role.ordinal) return emptySet()
    return employee.granted intersect effectiveCapabilities(parent, directory)
}

fun canManageEmployee(actor: SellerEmployee?, target: SellerEmployee?, directory: List<SellerEmployee>): Boolean {
    if (actor == null || !actor.active || target == null || actor.id == target.id || actor.sellerId != target.sellerId) return false
    if (SellerCapability.EMPLOYEES !in effectiveCapabilities(actor, directory)) return false
    return when (actor.role) {
        SellerEmployeeRole.OWNER -> target.role != SellerEmployeeRole.OWNER
        SellerEmployeeRole.MANAGER -> target.role == SellerEmployeeRole.STAFF && target.supervisorId == actor.id
        SellerEmployeeRole.STAFF -> false
    }
}

data class SellerPublicProfile(val name: String, val description: String, val location: String,
                               val localImageUri: String? = null)
data class SellerClosure(val date: LocalDate, val reason: String, val holiday: Boolean)

/** Pure validation used by UI and debug repository. Category IDs are seller-scoped through Seller.categoryIds. */
fun validateSellerItem(item: MenuItem, seller: Seller?, categories: List<MenuCategory>): String? = when {
    seller == null || item.sellerId != seller.id -> "Seller mismatch"
    item.name.isBlank() -> "Item name is required"
    item.price.rupees < 0 -> "Price must be nonnegative"
    item.categoryId !in seller.categoryIds || categories.none { it.id == item.categoryId } -> "Choose this seller's category"
    else -> null
}

fun validHours(hours: Map<DayOfWeek, List<BulkTimeWindow>>): Boolean =
    hours.values.all { windows -> windows.zipWithNext().all { (a, b) -> a.closes <= b.opens } }

/** Read-only impact list. Existing revisions are never rewritten by a later schedule edit. */
fun sellerPickupConflicts(records: List<BulkRequestRecord>, sellerId: String,
                          proposed: SellerBulkPolicy, now: LocalDateTime): List<String> = records
    .filter { it.sellerId == sellerId && it.thread.stage in setOf(QuoteStage.SUBMITTED,
        QuoteStage.COUNTEROFFER, QuoteStage.AGREED_AWAITING_PAYMENT, QuoteStage.PAID_CONFIRMED) }
    .filter { it.thread.specialAcceptedVersion != it.latest.version &&
        pickupWindow(proposed, it.latest.pickup, now) == PickupWindow.SPECIAL_REQUIRED }
    .map { it.reference }
