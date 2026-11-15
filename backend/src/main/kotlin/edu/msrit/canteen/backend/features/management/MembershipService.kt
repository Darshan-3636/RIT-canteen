package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import java.sql.Connection
import java.util.UUID

internal class MembershipService(private val context: BackendContext) {
    private val tx=ManagementTransactions(context)

    fun list(seller: String,page: Page): PageDto<MemberDto> = context.read { db ->
        val ids=db.query("SELECT id FROM seller_memberships WHERE seller_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",
            seller,page.limit,page.offset) { it.uuid("id") }
        PageDto(ids.map { db.member(seller,it) },page.limit,page.offset,
            db.count("SELECT count(*) FROM seller_memberships WHERE seller_id=?",seller))
    }

    fun save(actor: AuthenticatedPrincipal,seller: String,id: UUID?,request: MemberWrite,admin: Boolean): MemberDto {
        val reason=text(request.reason,500,"reason")
        if(id!=null && request.expectedVersion==null) throw badRequest("EXPECTED_VERSION_REQUIRED","Membership edits require expectedVersion")
        if(request.role !in setOf("OWNER","MANAGER","STAFF")) throw badRequest("INVALID_ROLE","Unknown membership role")
        validateCaps(request.capabilities)
        val supervisor=request.supervisorMembershipId?.let(::uuid)
        if((request.role=="OWNER") != (supervisor==null)) throw badRequest("INVALID_SUPERVISOR","Role requires the appropriate supervisor")
        return tx.write(actor,seller,"employees.manage",admin,lifecycle=true) { db ->
            db.sellerState(seller)
            val old=id?.let { db.member(seller,it) }
            version(old?.version ?: 0,request.expectedVersion)
            val user=old?.userId?.let(::uuid) ?: request.userId?.let(::uuid)
                ?: throw badRequest("USER_REQUIRED","Existing userId is required")
            if(old!=null && request.userId!=null && request.userId!=old.userId) throw badRequest("IMMUTABLE_ID","Membership user cannot change")
            val account=db.queryOne("SELECT status FROM users WHERE id=?",user) { it.getString(1) } ?: throw notFound("User not found")
            if(request.active && account!="ACTIVE") throw conflict("USER_INACTIVE","An active membership requires an active user")
            val caller=if(admin) null else db.queryOne("SELECT id FROM seller_memberships WHERE user_id=? AND seller_id=?",actor.userId,seller) { it.uuid("id") }
                ?.let { db.member(seller,it) } ?: if(!admin) denied() else null
            if(!admin) {
                if(user==actor.userId || request.role=="OWNER" || old?.role=="OWNER") denied()
                when(caller!!.role) {
                    "OWNER" -> Unit
                    "MANAGER" -> if(request.role!="STAFF" || supervisor.toString()!=caller.id ||
                        (old!=null && (old.role!="STAFF" || old.supervisorMembershipId!=caller.id))) denied()
                    else -> denied()
                }
                if(request.capabilities.any { !context.authorization.hasSellerCapability(db,actor,seller,it) }) denied()
            }
            if(request.role=="OWNER" && request.capabilities.isNotEmpty()) throw badRequest("OWNER_CAPABILITIES","Owners receive all known capabilities automatically")
            val parent=supervisor?.let { db.member(seller,it) }
            if(parent!=null) {
                if(parent.id==id?.toString() || parent.role!=(if(request.role=="MANAGER") "OWNER" else "MANAGER"))
                    throw badRequest("INVALID_SUPERVISOR","Supervisor must have the preceding role in this seller")
                if(request.active && !db.activeChain(seller,uuid(parent.id))) throw conflict("SUPERVISOR_INACTIVE","Supervisor chain must be active")
                if(request.capabilities.any { !db.chainHas(seller,uuid(parent.id),it) })
                    throw badRequest("CAPABILITY_NOT_DELEGABLE","Supervisor must hold every assigned capability")
            }
            if(old!=null && old.role!=request.role && db.count("SELECT count(*) FROM seller_memberships WHERE supervisor_membership_id=?",id)>0)
                throw conflict("MEMBERSHIP_HAS_DEPENDENTS","Reassign dependent memberships before changing this role")
            val memberId=id ?: UUID.randomUUID()
            if(old==null) db.update("""INSERT INTO seller_memberships(id,user_id,seller_id,role,supervisor_membership_id,active)
                VALUES (?,?,?,?,?,?)""",memberId,user,seller,request.role,supervisor,request.active)
            else db.update("""UPDATE seller_memberships SET role=?,supervisor_membership_id=?,active=?,version=version+1,
                updated_at=now() WHERE id=? AND seller_id=?""",request.role,supervisor,request.active,memberId,seller)
            replaceGrants(db,memberId,request.capabilities)
            if(old?.role=="OWNER" || request.role=="OWNER") db.requireViableOwner(seller)
            audit(db,actor,admin,"seller.membership.update","MEMBERSHIP",memberId.toString(),seller,reason)
            db.member(seller,memberId)
        }
    }

    fun grants(actor: AuthenticatedPrincipal,seller: String,id: UUID,request: GrantWrite,admin: Boolean): MemberDto {
        // Read and apply under the same exclusive lifecycle lock; do not compose two transactions.
        text(request.reason,500,"reason"); validateCaps(request.capabilities)
        return tx.write(actor,seller,"employees.manage",admin,lifecycle=true) { db ->
            val old=db.member(seller,id); version(old.version,request.expectedVersion)
            if(old.role=="OWNER") throw badRequest("OWNER_CAPABILITIES","Owner capabilities cannot be edited")
            if(!admin) {
                val caller=db.queryOne("SELECT id FROM seller_memberships WHERE seller_id=? AND user_id=?",seller,actor.userId) { it.uuid("id") }
                    ?.let { db.member(seller,it) } ?: denied()
                if(old.userId==actor.userId.toString() || caller.role=="STAFF") denied()
                if(caller.role=="MANAGER" && (old.role!="STAFF" || old.supervisorMembershipId!=caller.id)) denied()
                if(request.capabilities.any { !context.authorization.hasSellerCapability(db,actor,seller,it) }) denied()
            }
            val parent=old.supervisorMembershipId!!.let(::uuid)
            if(request.capabilities.any { !db.chainHas(seller,parent,it) }) throw badRequest("CAPABILITY_NOT_DELEGABLE","Supervisor must hold every assigned capability")
            replaceGrants(db,id,request.capabilities)
            db.update("UPDATE seller_memberships SET version=version+1,updated_at=now() WHERE seller_id=? AND id=?",seller,id)
            audit(db,actor,admin,"seller.membership.grants_update","MEMBERSHIP",id.toString(),seller,request.reason.trim())
            db.member(seller,id)
        }
    }

    private fun validateCaps(caps: List<String>) {
        if(caps.size!=caps.toSet().size || !SellerCapabilities.ALL.containsAll(caps)) throw badRequest("INVALID_CAPABILITIES","Capabilities must be distinct known names")
    }
    private fun replaceGrants(db: Connection,id: UUID,caps: List<String>) {
        db.update("DELETE FROM seller_membership_capabilities WHERE membership_id=?",id)
        caps.forEach { db.update("INSERT INTO seller_membership_capabilities(membership_id,capability) VALUES (?,?)",id,it) }
    }
    private fun Connection.member(seller: String,id: UUID): MemberDto = queryOne("""SELECT m.*,u.full_name FROM seller_memberships m
        JOIN users u ON u.id=m.user_id WHERE m.seller_id=? AND m.id=?""",seller,id) {
        MemberDto(id.toString(),it.uuid("user_id").toString(),seller,it.getString("full_name"),it.getString("role"),
            it.getString("supervisor_membership_id"),it.getBoolean("active"),
            query("SELECT capability FROM seller_membership_capabilities WHERE membership_id=? ORDER BY capability",id) { row -> row.getString(1) },it.getLong("version"))
    } ?: throw notFound("Membership not found in this seller")

    private fun Connection.activeChain(seller: String,id: UUID): Boolean = chainHas(seller,id,null)
    private fun Connection.chainHas(seller: String,id: UUID,cap: String?): Boolean {
        val seen=mutableSetOf<UUID>(); var current: UUID?=id; var expected: String?=null
        while(current!=null) {
            if(!seen.add(current)) return false
            val m=member(seller,current)
            if(!m.active || (expected!=null && m.role!=expected) ||
                queryOne("SELECT status FROM users WHERE id=?",uuid(m.userId)) { it.getString(1) }!="ACTIVE") return false
            if(m.role=="OWNER") return m.supervisorMembershipId==null
            if(cap!=null && cap !in m.capabilities) return false
            expected=if(m.role=="STAFF") "MANAGER" else "OWNER"
            current=m.supervisorMembershipId?.let(::uuid) ?: return false
        }
        return false
    }
}
