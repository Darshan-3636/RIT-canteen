package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AuthorizedIdentity
import java.time.LocalDateTime

/** Debug repository contract. A future authenticated API must enforce the same rules server-side. */
interface BulkOperationsRepository {
    val records: List<BulkRequestRecord>
    fun reset()
    fun submitLocalDemo(draft: BulkDraft, actor: AuthorizedIdentity?, now: LocalDateTime): Pair<DemoTransitionResult, String?>
    fun propose(reference: String, expectedVersion: Int, proposal: BulkProposal,
                actor: AuthorizedIdentity?, now: LocalDateTime): DemoTransitionResult
    fun acceptSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                      now: LocalDateTime): DemoTransitionResult
    fun rejectSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                      now: LocalDateTime): DemoTransitionResult
    fun sellerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                      now: LocalDateTime): DemoTransitionResult
    fun customerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                        now: LocalDateTime): DemoTransitionResult
    fun withdraw(reference: String, actor: AuthorizedIdentity?): DemoTransitionResult
    fun reject(reference: String, actor: AuthorizedIdentity?): DemoTransitionResult
    fun expire(reference: String, now: LocalDateTime): DemoTransitionResult
}
