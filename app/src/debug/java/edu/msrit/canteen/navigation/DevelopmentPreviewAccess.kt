package edu.msrit.canteen.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.auth.DemoAccount
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.customer.CustomerProfile
import edu.msrit.canteen.customer.DevelopmentCustomerAccount
import edu.msrit.canteen.customer.DevelopmentIndividualOrders

/** Debug-only mock login. Never writes to or substitutes for a real authenticated session. */
object DevelopmentPreviewAccess {
    private const val SELLER_ID = "north-counter"

    val identities = listOf(
        DevelopmentTestIdentity(DevelopmentRole.Customer, "Customer", "Demo Customer",
            AuthorizedIdentity("debug-customer", setOf(AppMode.Customer))),
        DevelopmentTestIdentity(DevelopmentRole.SellerOwner, "Seller owner", "North Counter Owner · demo",
            AuthorizedIdentity("debug-owner", setOf(AppMode.SellerOwner(SELLER_ID)),
                mapOf(SELLER_ID to setOf("bulk.view", "bulk.respond", "operations.history", "catalog.edit", "reports.export")))),
        DevelopmentTestIdentity(DevelopmentRole.SellerManager, "Seller manager", "North Counter Manager · demo",
            AuthorizedIdentity("debug-manager", setOf(AppMode.SellerManager(SELLER_ID)),
                mapOf(SELLER_ID to setOf("bulk.view", "bulk.respond", "operations.history", "catalog.edit")))),
        DevelopmentTestIdentity(DevelopmentRole.SellerStaff, "Seller staff", "North Counter Staff · demo",
            AuthorizedIdentity("debug-staff", setOf(AppMode.OperationalStaff(SELLER_ID)),
                mapOf(SELLER_ID to setOf("bulk.view")))),
        DevelopmentTestIdentity(DevelopmentRole.SellerStaffOperations, "Seller staff · delegated", "North Counter Operations Staff · demo",
            AuthorizedIdentity("debug-staff-ops", setOf(AppMode.OperationalStaff(SELLER_ID)))),
        DevelopmentTestIdentity(DevelopmentRole.PlatformAdministrator, "Platform administrator", "Demo Administrator",
            AuthorizedIdentity("debug-admin", setOf(AppMode.PlatformAdministrator))),
    )

    var activeRole by mutableStateOf<DevelopmentRole?>(null)
        private set

    val activeIdentity: DevelopmentTestIdentity?
        get() = (DevelopmentDemoAccounts.activeAccount?.let(::asIdentity)
            ?: identities.firstOrNull { it.role == activeRole })?.let { identity ->
            val sellerId = identity.authorization.modes.firstNotNullOfOrNull { mode -> when (mode) {
                is AppMode.SellerOwner -> mode.sellerId
                is AppMode.SellerManager -> mode.sellerId
                is AppMode.OperationalStaff -> mode.sellerId
                else -> null
            } }
            if (sellerId == null) identity else identity.copy(authorization = identity.authorization.copy(
                capabilitiesBySeller = mapOf(sellerId to DevelopmentSellerBusiness.capabilities(
                    identity.authorization.userId, sellerId).map { it.wire }.toSet())))
        }

    private fun asIdentity(account: DemoAccount): DevelopmentTestIdentity {
        val mode = when (account.role) {
            DevelopmentRole.Customer -> AppMode.Customer
            DevelopmentRole.SellerOwner -> AppMode.SellerOwner(requireNotNull(account.sellerId))
            DevelopmentRole.SellerManager -> AppMode.SellerManager(requireNotNull(account.sellerId))
            DevelopmentRole.SellerStaff, DevelopmentRole.SellerStaffOperations ->
                AppMode.OperationalStaff(requireNotNull(account.sellerId))
            DevelopmentRole.PlatformAdministrator -> AppMode.PlatformAdministrator
        }
        val roleLabel = when (account.role) {
            DevelopmentRole.Customer -> "Customer"
            DevelopmentRole.SellerOwner -> "Seller owner"
            DevelopmentRole.SellerManager -> "Seller manager"
            DevelopmentRole.SellerStaff -> "Seller staff"
            DevelopmentRole.SellerStaffOperations -> "Seller staff · delegated"
            DevelopmentRole.PlatformAdministrator -> "Platform administrator"
        }
        return DevelopmentTestIdentity(account.role, roleLabel, account.name,
            AuthorizedIdentity(account.id, setOf(mode)))
    }

    fun select(role: DevelopmentRole) {
        DevelopmentDemoAccounts.signOut(); activeRole = role
        if (role == DevelopmentRole.Customer) {
            DevelopmentCustomerAccount.activate("debug-customer", CustomerProfile("Demo Customer",
                "demo@example.invalid", CustomerAffiliation.STUDENT))
            DevelopmentIndividualOrders.activate("debug-customer")
        } else {
            DevelopmentCustomerAccount.deactivate(); DevelopmentIndividualOrders.deactivate()
        }
    }
    fun clearHubSelection() { activeRole = null }
    fun reset() { activeRole = null; DevelopmentDemoAccounts.signOut() }
    fun allows(area: ProtectedArea): Boolean = PresentationAccess.allows(activeIdentity?.authorization, area)
}
