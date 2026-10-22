package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AuthorizedIdentity
import java.time.LocalDateTime

/** Release contains no scenario records and cannot authorize mock transitions. */
object DevelopmentSellerOperations : BulkOperationsRepository {
    override val records: List<BulkRequestRecord> = emptyList()
    override fun reset() = Unit
    override fun submitLocalDemo(draft: BulkDraft, actor: AuthorizedIdentity?, now: LocalDateTime) =
        DemoTransitionResult.UNAUTHORIZED to null
    override fun propose(reference: String, expectedVersion: Int, proposal: BulkProposal,
                         actor: AuthorizedIdentity?, now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
    override fun acceptSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
    override fun rejectSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
    override fun sellerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
    override fun customerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                                 now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
    override fun withdraw(reference: String, actor: AuthorizedIdentity?) = DemoTransitionResult.UNAUTHORIZED
    override fun reject(reference: String, actor: AuthorizedIdentity?) = DemoTransitionResult.UNAUTHORIZED
    override fun expire(reference: String, now: LocalDateTime) = DemoTransitionResult.UNAUTHORIZED
}
