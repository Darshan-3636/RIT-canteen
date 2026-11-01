package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import java.sql.Connection
import java.util.UUID

internal class AdministrationService(private val context: BackendContext) {
    private val tx=ManagementTransactions(context)
    fun seller(id: String)=context.read { it.sellerState(id) }
    fun user(id: UUID)=context.read { it.userState(id) }
    fun draft(id: UUID)=context.read { it.draftState(id) }

    fun sellerStatus(actor: AuthenticatedPrincipal,id: String,active: Boolean,request: VersionRequest): SellerState {
        val reason=text(request.reason,500,"reason")
        return tx.write(actor,admin=true,lifecycle=true) { db ->
            val old=db.sellerState(id); version(old.version,request.expectedVersion)
            db.update("UPDATE sellers SET active=?,version=version+1,updated_at=now() WHERE id=?",active,id)
            db.requireViableOwner(id)
            audit(db,actor,true,if(active) "admin.seller.activate" else "admin.seller.suspend","SELLER",id,id,reason)
            db.sellerState(id)
        }
    }

    fun userStatus(actor: AuthenticatedPrincipal,id: UUID,request: UserStatusWrite): UserState {
        if(request.status !in setOf("ACTIVE","SUSPENDED","DISABLED")) throw badRequest("INVALID_STATUS","Unknown user status")
        val reason=text(request.reason,500,"reason")
        return tx.write(actor,admin=true,lifecycle=true) { db ->
            val old=db.userState(id); version(old.version,request.expectedVersion)
            db.update("UPDATE users SET status=?,version=version+1,updated_at=now() WHERE id=?",request.status,id)
            if(old.platformAdmin) db.requireAdminRemains()
            db.query("SELECT seller_id FROM seller_memberships WHERE user_id=? AND role='OWNER' AND active ORDER BY seller_id",id) { it.getString(1) }
                .forEach { db.requireViableOwner(it) }
            if(request.status!="ACTIVE") db.update("UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=?",id)
            audit(db,actor,true,"admin.user.status","USER",id.toString(),reason=reason)
            db.userState(id)
        }
    }

    fun adminFlag(actor: AuthenticatedPrincipal,id: UUID,request: AdminFlagWrite): UserState {
        val reason=text(request.reason,500,"reason")
        return tx.write(actor,admin=true,lifecycle=true) { db ->
            val old=db.userState(id); version(old.version,request.expectedVersion)
            if(request.enabled && old.status!="ACTIVE") throw conflict("USER_INACTIVE","Administrator must be active")
            db.update("UPDATE users SET is_platform_admin=?,version=version+1,updated_at=now() WHERE id=?",request.enabled,id)
            db.requireAdminRemains()
            audit(db,actor,true,"admin.user.admin_flag","USER",id.toString(),reason=reason)
            db.userState(id)
        }
    }

    fun decide(actor: AuthenticatedPrincipal,id: UUID,approve: Boolean,request: DraftDecision): DraftState {
        val reason=text(request.reason,500,"reason")
        val owner=request.ownerUserId?.let(::uuid)
        if(approve && owner==null) throw badRequest("OWNER_REQUIRED","Approval requires an existing active ownerUserId")
        return tx.write(actor,admin=true,lifecycle=true) { db ->
            db.queryOne("SELECT id FROM admin_setup_drafts WHERE id=? FOR UPDATE",id) { true } ?: throw notFound()
            val old=db.draftState(id); version(old.version,request.expectedVersion)
            if(old.status!="DRAFT" || old.decision!=null) throw conflict("DRAFT_CLOSED","Only an undecided draft can be reviewed")
            if(approve) {
                if(db.queryOne("SELECT status FROM users WHERE id=?",owner) { it.getString(1) }!="ACTIVE")
                    throw badRequest("INVALID_OWNER","Owner must be an existing active account")
                db.update("INSERT INTO sellers(id,name,description,active,is_main_canteen) VALUES (?,?,'',false,false)",old.proposedSellerId,old.sellerName)
                db.update("INSERT INTO seller_memberships(id,user_id,seller_id,role) VALUES (?,?,?,'OWNER')",UUID.randomUUID(),owner,old.proposedSellerId)
                db.update("INSERT INTO seller_ordering_policies(seller_id) VALUES (?)",old.proposedSellerId)
            }
            db.update("""UPDATE admin_setup_drafts SET status='ARCHIVED',decision=?,owner_user_id=?,created_seller_id=?,
                reviewed_by=?,reviewed_at=now(),decision_reason=?,version=version+1,updated_at=now() WHERE id=?""",
                if(approve) "APPROVED" else "REJECTED",if(approve) owner else null,if(approve) old.proposedSellerId else null,actor.userId,reason,id)
            audit(db,actor,true,if(approve) "admin.setup_draft.approve" else "admin.setup_draft.reject","SETUP_DRAFT",id.toString(),
                if(approve) old.proposedSellerId else null,reason)
            db.draftState(id)
        }
    }

    private fun Connection.requireAdminRemains() {
        if(count("SELECT count(*) FROM users WHERE status='ACTIVE' AND is_platform_admin")==0L)
            throw conflict("LAST_ACTIVE_ADMIN","At least one active platform administrator is required")
    }
    private fun Connection.userState(id: UUID)=queryOne("SELECT * FROM users WHERE id=?",id) {
        UserState(id.toString(),it.getString("status"),it.getBoolean("is_platform_admin"),it.getLong("version"))
    } ?: throw notFound()
    private fun Connection.draftState(id: UUID)=queryOne("SELECT * FROM admin_setup_drafts WHERE id=?",id) {
        DraftState(id.toString(),it.getString("proposed_seller_id"),it.getString("seller_name"),it.getString("status"),
            it.getString("decision"),it.getString("owner_user_id"),it.getString("created_seller_id"),it.getLong("version"))
    } ?: throw notFound()
}
