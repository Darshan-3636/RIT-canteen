package edu.msrit.canteen.navigation

object ModeSelectionPolicy {
    fun canEnterCustomer(identity: AuthorizedIdentity?): Boolean =
        PresentationAccess.allows(identity, ProtectedArea.Customer)

    fun canEnterSeller(identity: AuthorizedIdentity?): Boolean =
        identity?.modes?.any { mode ->
            mode is AppMode.SellerOwner || mode is AppMode.SellerManager || mode is AppMode.OperationalStaff
        } == true

    fun canEnterAdmin(identity: AuthorizedIdentity?): Boolean =
        PresentationAccess.allows(identity, ProtectedArea.Administrator)
}
