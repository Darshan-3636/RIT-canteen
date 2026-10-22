package edu.msrit.canteen.navigation

/** The UI uses these scopes once a server-issued session exists. No role is self-selected. */
sealed interface AppMode {
    data object Customer : AppMode
    data class SellerOwner(val sellerId: String) : AppMode
    data class SellerManager(val sellerId: String) : AppMode
    data class OperationalStaff(val sellerId: String) : AppMode
    data object PlatformAdministrator : AppMode
}

data class AuthorizedIdentity(
    val userId: String,
    val modes: Set<AppMode>,
    val capabilitiesBySeller: Map<String, Set<String>> = emptyMap(),
)

sealed interface ProtectedArea {
    data object Customer : ProtectedArea
    data class Seller(val sellerId: String, val capability: String) : ProtectedArea
    data object Administrator : ProtectedArea
}

/** A client-side guard for presentation only; future backend endpoints must enforce authority. */
object PresentationAccess {
    fun allows(identity: AuthorizedIdentity?, area: ProtectedArea): Boolean {
        if (identity == null) return false
        return when (area) {
            ProtectedArea.Customer -> AppMode.Customer in identity.modes
            ProtectedArea.Administrator -> AppMode.PlatformAdministrator in identity.modes
            is ProtectedArea.Seller -> {
                val member = identity.modes.any { mode ->
                    when (mode) {
                        is AppMode.SellerOwner -> mode.sellerId == area.sellerId
                        is AppMode.SellerManager -> mode.sellerId == area.sellerId
                        is AppMode.OperationalStaff -> mode.sellerId == area.sellerId
                        else -> false
                    }
                }
                member && area.capability in identity.capabilitiesBySeller[area.sellerId].orEmpty()
            }
        }
    }
}
