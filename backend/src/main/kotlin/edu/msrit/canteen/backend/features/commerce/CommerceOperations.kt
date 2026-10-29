package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal class CommerceFault(val http: Int, val code: String, override val message: String): RuntimeException(message)
internal data class PricedLine(val itemId:String,val name:String,val quantity:Int,val price:Long)
private fun bad(code:String,message:String)=CommerceFault(400,code,message)
private fun rule(code:String,message:String)=CommerceFault(422,code,message)
private fun conflict(code:String,message:String)=CommerceFault(409,code,message)

/** Recheck the authenticated user's live session on the same DB connection as customer writes. */
private fun requireLiveUser(c:Connection,principal:AuthenticatedPrincipal,context:BackendContext) {
    val live=c.prepareStatement("""SELECT 1 FROM sessions s JOIN users u ON u.id=s.user_id
        WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='ACTIVE'""").use { s ->
        s.setObject(1,principal.sessionId);s.setObject(2,principal.userId);s.setTimestamp(3,Timestamp.from(context.clock.instant()));s.executeQuery().use { it.next() }
    }
    if(!live) throw CommerceFault(401,"UNAUTHENTICATED","A valid bearer session is required")
}

private fun totalPaise(lines:List<PricedLine>):Long = try {
    lines.fold(0L) { total,line -> Math.addExact(total,Math.multiplyExact(line.price,line.quantity.toLong())) }
} catch (_:ArithmeticException) { throw rule("TOTAL_TOO_LARGE","The request total exceeds the supported amount") }

/** Lazy server-clock expiry is committed separately so a subsequent 409 cannot roll it back. */
internal fun expireDue(context:BackendContext) = context.transaction { c ->
    val now=context.clock.instant()
    val expired=c.prepareStatement("""SELECT r.id,r.current_version,r.customer_user_id FROM bulk_requests r JOIN bulk_quote_revisions q
        ON q.request_id=r.id AND q.version=r.current_version WHERE r.status IN ('SUBMITTED','COUNTEROFFER') AND q.expires_at<=? FOR UPDATE OF r""").use { s ->
        s.setTimestamp(1,Timestamp.from(now));s.executeQuery().use { rows -> buildList { while(rows.next()) add(Triple(rows.getObject(1,UUID::class.java),rows.getInt(2),rows.getObject(3,UUID::class.java))) } }
    }
    expired.forEach { (id,version,customer) ->
        c.prepareStatement("UPDATE bulk_requests SET status='EXPIRED',updated_at=? WHERE id=? AND current_version=?").use { s -> s.setTimestamp(1,Timestamp.from(now));s.setObject(2,id);s.setInt(3,version);s.executeUpdate() }
        c.prepareStatement("UPDATE bulk_quote_revisions SET status='EXPIRED' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,version);s.executeUpdate() }
        event(c,id,version,customer,"EXPIRED")
    }
}

private fun parseInstant(value:String):Instant = try { Instant.parse(value) } catch (_: Exception) { throw bad("INVALID_PICKUP_TIME","pickupAt must be an ISO-8601 UTC instant") }

internal fun validateAndPrice(connection:Connection,context:BackendContext,sellerId:String,lines:List<DraftLine>,pickupText:String,reason:String?):Pair<List<PricedLine>,Instant> {
    if (lines.isEmpty() || lines.size > 100) throw bad("INVALID_LINES","Provide between 1 and 100 item lines")
    if (lines.map { it.itemId }.toSet().size != lines.size) throw bad("DUPLICATE_ITEMS","Each item may appear only once")
    if (lines.any { it.itemId.isBlank() || it.quantity !in 1..999 }) throw bad("INVALID_QUANTITY","Each quantity must be between 1 and 999")
    val pickup=parseInstant(pickupText); val now=context.clock.instant()
    if (!pickup.isAfter(now)) throw rule("PICKUP_IN_PAST","Pickup time must be in the future")
    val seller=connection.prepareStatement("SELECT active,is_main_canteen FROM sellers WHERE id=? FOR SHARE").use { s -> s.setString(1,sellerId); s.executeQuery().use { r -> if(r.next()) r.getBoolean(1) to r.getBoolean(2) else null } } ?: throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found")
    if (!seller.first) throw rule("SELLER_UNAVAILABLE","Seller is inactive")
    val policy=connection.prepareStatement("SELECT bulk_minimum_units,special_pickup_enabled FROM seller_ordering_policies WHERE seller_id=? FOR SHARE").use { s -> s.setString(1,sellerId); s.executeQuery().use { r -> if(r.next()) r.getInt(1) to r.getBoolean(2) else 8 to false } }
    val priced=lines.map { line ->
        connection.prepareStatement("""SELECT i.name,i.price_paise FROM menu_items i JOIN seller_categories c ON c.seller_id=i.seller_id AND c.category_id=i.category_id
            WHERE i.id=? AND i.seller_id=? AND i.published AND i.availability='AVAILABLE' FOR SHARE OF i""").use { s ->
            s.setString(1,line.itemId); s.setString(2,sellerId); s.executeQuery().use { r ->
                if(!r.next()) throw rule("ITEM_UNAVAILABLE","One or more items are unavailable for this seller")
                PricedLine(line.itemId,r.getString(1),line.quantity,r.getLong(2))
            }
        }
    }
    if (priced.sumOf { it.quantity } < policy.first) throw rule("BULK_MINIMUM_NOT_MET","Request does not meet the seller's combined-unit minimum")
    val zone=context.config.sellerZone; val local=pickup.atZone(zone)
    val special=reason?.trim()?.takeIf(String::isNotEmpty)
    if (special != null) {
        if (special.length > 500) throw bad("SPECIAL_REASON_TOO_LONG","Special pickup reason must be at most 500 characters")
        if (!policy.second) throw rule("SPECIAL_PICKUP_UNAVAILABLE","Seller has not enabled special pickup requests")
    } else {
        val closed=connection.prepareStatement("SELECT 1 FROM seller_closures WHERE seller_id=? AND local_date=?").use { s ->
            s.setString(1,sellerId); s.setObject(2,LocalDate.from(local)); s.executeQuery().use { it.next() }
        }
        if (closed) throw rule("SELLER_CLOSED","Seller is closed on the requested date")
        val hours=connection.prepareStatement("SELECT opens_at,closes_at FROM seller_hours WHERE seller_id=? AND day_of_week=?").use { s ->
            s.setString(1,sellerId); s.setInt(2,local.dayOfWeek.value); s.executeQuery().use { r -> if(r.next()) LocalTime.parse(r.getString(1)) to LocalTime.parse(r.getString(2)) else null }
        } ?: throw rule("HOURS_UNKNOWN","Regular pickup cannot be validated because seller hours are unknown")
        if (local.toLocalTime().isBefore(hours.first) || !local.toLocalTime().isBefore(hours.second)) throw rule("OUTSIDE_SELLER_HOURS","Use a special pickup request for a time outside seller hours")
    }
    return priced to pickup
}

internal fun createBulk(context:BackendContext,principal:AuthenticatedPrincipal,sellerId:String,lines:List<DraftLine>,pickupAt:String,reason:String?,instructions:String,key:String):BulkRequestDto {
    expireDue(context)
    if(key.length !in 1..128 || key.any(Char::isISOControl)) throw bad("INVALID_IDEMPOTENCY_KEY","Idempotency-Key must contain 1 to 128 visible characters")
    val note = instructions.trim()
    if (note.length > 500) throw bad("INSTRUCTIONS_TOO_LONG","Pickup instructions must be at most 500 characters")
    val digest=MessageDigest.getInstance("SHA-256").digest(buildString {
        append(sellerId).append('|').append(pickupAt).append('|').append(reason.orEmpty())
        // Preserve the pre-instructions digest for retries of existing requests.
        if (note.isNotEmpty()) append("|instructions:").append(note)
        lines.sortedBy { it.itemId }.forEach { append('|').append(it.itemId).append(':').append(it.quantity) }
    }.toByteArray())
    return context.transaction { c ->
        requireLiveUser(c,principal,context)
        val now=context.clock.instant()
        val prior=c.prepareStatement("SELECT id,idempotency_hash FROM bulk_requests WHERE customer_user_id=? AND idempotency_key=?").use { s ->
            s.setObject(1,principal.userId);s.setString(2,key);s.executeQuery().use { r -> if(r.next()) UUIDRow(r.getObject(1,UUID::class.java),r.getBytes(2)) else null }
        }
        if(prior!=null) {
            if(!MessageDigest.isEqual(prior.hash,digest)) throw conflict("IDEMPOTENCY_KEY_REUSED","This idempotency key was used with different request data")
            return@transaction loadBulk(c,prior.id) ?: throw conflict("IDEMPOTENCY_RESULT_MISSING","Previously saved request could not be loaded")
        }
        val priced=validateAndPrice(c,context,sellerId,lines,pickupAt,reason)
        val id=UUID.randomUUID(); val expiry=now.plusSeconds(24*60*60L)
        val inserted=c.prepareStatement("""INSERT INTO bulk_requests(id,customer_user_id,seller_id,current_version,status,idempotency_key,idempotency_hash,instructions,created_at,updated_at)
             VALUES (?,?,?,1,'SUBMITTED',?,?,?,?,?) ON CONFLICT(customer_user_id,idempotency_key) DO NOTHING""").use { s ->
            s.setObject(1,id); s.setObject(2,principal.userId); s.setString(3,sellerId); s.setString(4,key); s.setBytes(5,digest); s.setString(6,note); s.setTimestamp(7,Timestamp.from(now)); s.setTimestamp(8,Timestamp.from(now)); s.executeUpdate()==1
        }
        if (!inserted) {
            val previous=c.prepareStatement("SELECT id,idempotency_hash FROM bulk_requests WHERE customer_user_id=? AND idempotency_key=?").use { s -> s.setObject(1,principal.userId); s.setString(2,key); s.executeQuery().use { r -> if(r.next()) UUIDRow(r.getObject(1,UUID::class.java),r.getBytes(2)) else null } } ?: throw conflict("IDEMPOTENCY_CONFLICT","Request key is currently being processed")
            if (!MessageDigest.isEqual(previous.hash,digest)) throw conflict("IDEMPOTENCY_KEY_REUSED","This idempotency key was used with different request data")
            return@transaction loadBulk(c,previous.id) ?: throw conflict("IDEMPOTENCY_RESULT_MISSING","Previously saved request could not be loaded")
        }
        insertRevision(c,id,1,"SUBMITTED",pickupAt,reason,priced.first,principal.userId,now,expiry)
        event(c,id,1,principal.userId,"SUBMITTED")
        loadBulk(c,id)!!
    }
}
private data class UUIDRow(val id:UUID,val hash:ByteArray)

private fun insertRevision(c:Connection,id:UUID,version:Int,status:String,pickupAt:String,reason:String?,lines:List<PricedLine>,actor:UUID,now:Instant,expiry:Instant) {
    val subtotal=totalPaise(lines)
    c.prepareStatement("""INSERT INTO bulk_quote_revisions(request_id,version,status,pickup_at,pickup_kind,special_pickup_reason,subtotal_paise,expires_at,created_by,created_at)
        VALUES (?,?,?,?,?,?,?,?,?,?)""").use { s -> s.setObject(1,id);s.setInt(2,version);s.setString(3,status);s.setTimestamp(4,Timestamp.from(parseInstant(pickupAt)));s.setString(5,if(reason.isNullOrBlank()) "REGULAR" else "SPECIAL");s.setString(6,reason?.trim()?.takeIf(String::isNotEmpty));s.setLong(7,subtotal);s.setTimestamp(8,Timestamp.from(expiry));s.setObject(9,actor);s.setTimestamp(10,Timestamp.from(now));s.executeUpdate() }
    c.prepareStatement("INSERT INTO bulk_quote_lines(request_id,version,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise) VALUES (?,?,?,?,?,?,?)").use { s ->
        val seller=c.prepareStatement("SELECT seller_id FROM bulk_requests WHERE id=?").use { q -> q.setObject(1,id);q.executeQuery().use { r -> r.next();r.getString(1) } }
        lines.forEach { s.setObject(1,id);s.setInt(2,version);s.setString(3,seller);s.setString(4,it.itemId);s.setString(5,it.name);s.setInt(6,it.quantity);s.setLong(7,it.price);s.addBatch() };s.executeBatch()
    }
    c.prepareStatement("UPDATE bulk_requests SET current_version=?,status=?,updated_at=? WHERE id=?").use { s -> s.setInt(1,version);s.setString(2,status);s.setTimestamp(3,Timestamp.from(now));s.setObject(4,id);s.executeUpdate() }
}

private fun event(c:Connection,id:UUID,version:Int,actor:UUID,type:String) {
    c.prepareStatement("INSERT INTO bulk_request_events(request_id,version,actor_user_id,event_type) VALUES (?,?,?,?)").use { s -> s.setObject(1,id);s.setInt(2,version);s.setObject(3,actor);s.setString(4,type);s.executeUpdate() }
}

internal fun counteroffer(context:BackendContext,principal:AuthenticatedPrincipal,sellerId:String,id:UUID,terms:QuoteTermsRequest):BulkRequestDto {
    expireDue(context)
    val expected=terms.expectedVersion ?: throw bad("EXPECTED_VERSION_REQUIRED","expectedVersion is required")
    return context.transaction { c ->
        if(!context.authorization.hasSellerCapability(c,principal,sellerId,"bulk.respond")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
        val current=lock(c,id,sellerId); checkEditable(c,id,current,expected,context)
        val priced=validateAndPrice(c,context,sellerId,terms.lines,terms.pickupAt,terms.specialPickupReason)
        val version=current.first+1; val now=context.clock.instant(); val expiry=now.plusSeconds(24*60*60L)
        insertRevision(c,id,version,"COUNTEROFFER",terms.pickupAt,terms.specialPickupReason,priced.first,principal.userId,now,expiry)
        event(c,id,version,principal.userId,"COUNTEROFFER")
        loadBulk(c,id)!!
    }
}
private fun lock(c:Connection,id:UUID,sellerId:String):Pair<Int,String> = c.prepareStatement("SELECT current_version,status FROM bulk_requests WHERE id=? AND seller_id=? FOR UPDATE").use { s ->
    s.setObject(1,id);s.setString(2,sellerId);s.executeQuery().use { r -> if(r.next()) r.getInt(1) to r.getString(2) else throw CommerceFault(404,"REQUEST_NOT_FOUND","Bulk request not found") }
}
private fun lockCustomer(c:Connection,id:UUID,user:UUID):Pair<Int,String> = c.prepareStatement("SELECT current_version,status FROM bulk_requests WHERE id=? AND customer_user_id=? FOR UPDATE").use { s ->
    s.setObject(1,id);s.setObject(2,user);s.executeQuery().use { r -> if(r.next()) r.getInt(1) to r.getString(2) else throw CommerceFault(404,"REQUEST_NOT_FOUND","Bulk request not found") }
}
private fun checkEditable(c:Connection,id:UUID,row:Pair<Int,String>,expected:Int,context:BackendContext) {
    if(row.first!=expected) throw conflict("STALE_REVISION","Request changed; refresh the current revision")
    if(row.second in setOf("EXPIRED","REJECTED","WITHDRAWN","AGREED_AWAITING_PAYMENT")) throw conflict("REQUEST_NOT_EDITABLE","This request can no longer be changed")
    val expires=c.prepareStatement("SELECT expires_at FROM bulk_quote_revisions WHERE request_id=? AND version=?").use { s ->
        s.setObject(1,id);s.setInt(2,row.first);s.executeQuery().use { r -> if(r.next()) r.getTimestamp(1).toInstant() else throw CommerceFault(404,"REVISION_NOT_FOUND","Current revision not found") }
    }
    if(!expires.isAfter(context.clock.instant())) throw conflict("REQUEST_EXPIRED","This quote revision has expired")
}

internal fun approveBulk(context:BackendContext,principal:AuthenticatedPrincipal,id:UUID,expected:Int,sellerId:String?=null):BulkRequestDto {
    expireDue(context)
    return context.transaction { c ->
    val row=if(sellerId==null) lockCustomer(c,id,principal.userId) else {
        if(!context.authorization.hasSellerCapability(c,principal,sellerId,"bulk.respond")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
        lock(c,id,sellerId)
    }
    if(sellerId==null) requireLiveUser(c,principal,context)
    checkEditable(c,id,row,expected,context)
    val revision=c.prepareStatement("SELECT status,expires_at,pickup_kind FROM bulk_quote_revisions WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeQuery().use { r -> if(r.next()) Triple(r.getString(1),r.getTimestamp(2).toInstant(),r.getString(3)) else throw CommerceFault(404,"REVISION_NOT_FOUND","Current revision not found") } }
    if(!revision.second.isAfter(context.clock.instant())) { expire(c,id,expected);throw conflict("REQUEST_EXPIRED","This quote revision has expired") }
    val kind=if(sellerId==null) "CUSTOMER" else "SELLER"
    if(kind=="SELLER") {
        val customer=c.prepareStatement("SELECT customer_user_id FROM bulk_requests WHERE id=?").use { s -> s.setObject(1,id);s.executeQuery().use { r -> r.next();r.getObject(1,UUID::class.java) } }
        if(customer==principal.userId) throw CommerceFault(403,"SEPARATE_APPROVAL_REQUIRED","The request customer cannot provide seller approval")
    }
    c.prepareStatement("INSERT INTO bulk_quote_approvals(request_id,version,actor_kind,actor_user_id) VALUES (?,?,?,?) ON CONFLICT(request_id,version,actor_kind) DO NOTHING").use { s -> s.setObject(1,id);s.setInt(2,expected);s.setString(3,kind);s.setObject(4,principal.userId);s.executeUpdate() }
    event(c,id,expected,principal.userId,if(kind=="CUSTOMER")"CUSTOMER_APPROVED" else "SELLER_APPROVED")
    val approvals=c.prepareStatement("SELECT actor_kind FROM bulk_quote_approvals WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeQuery().use { r -> buildSet { while(r.next()) add(r.getString(1)) } } }
    val specialApproved=if(revision.third=="SPECIAL") c.prepareStatement("SELECT approved FROM bulk_special_pickup_decisions WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeQuery().use { r -> r.next() && r.getBoolean(1) } } else true
    if(approvals.containsAll(setOf("CUSTOMER","SELLER")) && specialApproved) {
        c.prepareStatement("UPDATE bulk_requests SET status='AGREED_AWAITING_PAYMENT',updated_at=? WHERE id=?").use { s -> s.setTimestamp(1,Timestamp.from(context.clock.instant()));s.setObject(2,id);s.executeUpdate() }
        c.prepareStatement("UPDATE bulk_quote_revisions SET status='AGREED_AWAITING_PAYMENT' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeUpdate() }
        event(c,id,expected,principal.userId,"AGREED")
    }
    loadBulk(c,id)!!
    }
}

internal fun decideSpecialPickup(context:BackendContext,principal:AuthenticatedPrincipal,sellerId:String,id:UUID,expected:Int,approved:Boolean):BulkRequestDto {
    expireDue(context)
    return context.transaction { c ->
    if(!context.authorization.hasSellerCapability(c,principal,sellerId,"bulk.respond")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
    val row=lock(c,id,sellerId);checkEditable(c,id,row,expected,context)
    val owner=c.prepareStatement("SELECT customer_user_id FROM bulk_requests WHERE id=?").use { s -> s.setObject(1,id);s.executeQuery().use { r -> r.next();r.getObject(1,UUID::class.java) } }
    if(owner==principal.userId) throw CommerceFault(403,"SEPARATE_APPROVAL_REQUIRED","The request customer cannot decide seller pickup approval")
    val kind=c.prepareStatement("SELECT pickup_kind,expires_at FROM bulk_quote_revisions WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeQuery().use { r -> if(r.next()) r.getString(1) to r.getTimestamp(2).toInstant() else throw CommerceFault(404,"REVISION_NOT_FOUND","Current revision not found") } }
    if(kind.first!="SPECIAL") throw rule("NOT_SPECIAL_PICKUP","The current revision does not request special pickup")
    if(!kind.second.isAfter(context.clock.instant())) { expire(c,id,expected);throw conflict("REQUEST_EXPIRED","This quote revision has expired") }
    c.prepareStatement("INSERT INTO bulk_special_pickup_decisions(request_id,version,seller_user_id,approved) VALUES (?,?,?,?) ON CONFLICT(request_id,version) DO UPDATE SET seller_user_id=EXCLUDED.seller_user_id,approved=EXCLUDED.approved,decided_at=now()").use { s -> s.setObject(1,id);s.setInt(2,expected);s.setObject(3,principal.userId);s.setBoolean(4,approved);s.executeUpdate() }
    if(!approved) {
        c.prepareStatement("UPDATE bulk_requests SET status='REJECTED',updated_at=? WHERE id=?").use { s -> s.setTimestamp(1,Timestamp.from(context.clock.instant()));s.setObject(2,id);s.executeUpdate() }
        c.prepareStatement("UPDATE bulk_quote_revisions SET status='REJECTED' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeUpdate() }
        event(c,id,expected,principal.userId,"REJECTED")
    } else {
        event(c,id,expected,principal.userId,"SPECIAL_PICKUP_DECIDED")
        val bothApproved=c.prepareStatement("SELECT count(*)=2 FROM bulk_quote_approvals WHERE request_id=? AND version=? AND actor_kind IN ('CUSTOMER','SELLER')").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeQuery().use { r -> r.next() && r.getBoolean(1) } }
        if(bothApproved) {
            c.prepareStatement("UPDATE bulk_requests SET status='AGREED_AWAITING_PAYMENT',updated_at=? WHERE id=?").use { s -> s.setTimestamp(1,Timestamp.from(context.clock.instant()));s.setObject(2,id);s.executeUpdate() }
            c.prepareStatement("UPDATE bulk_quote_revisions SET status='AGREED_AWAITING_PAYMENT' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeUpdate() }
            event(c,id,expected,principal.userId,"AGREED")
        }
    }
    loadBulk(c,id)!!
    }
}

internal fun withdrawBulk(context:BackendContext,principal:AuthenticatedPrincipal,id:UUID,expected:Int):BulkRequestDto {
    expireDue(context)
    return context.transaction { c ->
    requireLiveUser(c,principal,context)
    val row=lockCustomer(c,id,principal.userId);checkEditable(c,id,row,expected,context)
    c.prepareStatement("UPDATE bulk_requests SET status='WITHDRAWN',updated_at=? WHERE id=?").use { s -> s.setTimestamp(1,Timestamp.from(context.clock.instant()));s.setObject(2,id);s.executeUpdate() }
    c.prepareStatement("UPDATE bulk_quote_revisions SET status='WITHDRAWN' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,expected);s.executeUpdate() }
    event(c,id,expected,principal.userId,"WITHDRAWN");loadBulk(c,id)!!
    }
}
private fun expire(c:Connection,id:UUID,version:Int) {
    c.prepareStatement("UPDATE bulk_requests SET status='EXPIRED',updated_at=now() WHERE id=?").use { s -> s.setObject(1,id);s.executeUpdate() }
    c.prepareStatement("UPDATE bulk_quote_revisions SET status='EXPIRED' WHERE request_id=? AND version=?").use { s -> s.setObject(1,id);s.setInt(2,version);s.executeUpdate() }
}

internal fun loadBulk(c:Connection,id:UUID):BulkRequestDto? = c.prepareStatement("""SELECT r.id,r.seller_id,r.current_version,r.status,r.instructions,q.pickup_at,q.special_pickup_reason,q.subtotal_paise,q.expires_at,p.bulk_minimum_units
    FROM bulk_requests r JOIN bulk_quote_revisions q ON q.request_id=r.id AND q.version=r.current_version LEFT JOIN seller_ordering_policies p ON p.seller_id=r.seller_id WHERE r.id=?""").use { s ->
    s.setObject(1,id);s.executeQuery().use { r -> if(!r.next()) null else {
        val version=r.getInt("current_version")
        val approvals=c.prepareStatement("SELECT actor_kind FROM bulk_quote_approvals WHERE request_id=? AND version=?").use { q -> q.setObject(1,id);q.setInt(2,version);q.executeQuery().use { rs -> buildSet { while(rs.next()) add(rs.getString(1)) } } }
        val special=c.prepareStatement("SELECT approved FROM bulk_special_pickup_decisions WHERE request_id=? AND version=?").use { q -> q.setObject(1,id);q.setInt(2,version);q.executeQuery().use { rs -> if(rs.next()) rs.getBoolean(1) else null } }
        val lines=c.prepareStatement("SELECT item_id,item_name_snapshot,quantity,unit_price_paise FROM bulk_quote_lines WHERE request_id=? AND version=? ORDER BY item_id").use { q -> q.setObject(1,id);q.setInt(2,version);q.executeQuery().use { rs -> buildList { while(rs.next()) add(BulkQuoteLineDto(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getLong(4))) } } }
        BulkRequestDto(id.toString(),r.getString("seller_id"),version,r.getString("status"),r.getTimestamp("pickup_at").toInstant().toString(),r.getString("special_pickup_reason"),r.getLong("subtotal_paise"),r.getInt("bulk_minimum_units").takeIf { it>0 } ?: 8,r.getTimestamp("expires_at").toInstant().toString(),"CUSTOMER" in approvals,"SELLER" in approvals,special,lines,r.getString("instructions"))
    } }
}

internal fun customerBulk(context:BackendContext,user:UUID):BulkRequestListDto { expireDue(context);return context.dataSource.connection.use { c ->
    val ids=c.prepareStatement("SELECT id FROM bulk_requests WHERE customer_user_id=? ORDER BY created_at DESC").use { s -> s.setObject(1,user);s.executeQuery().use { r -> buildList { while(r.next()) add(r.getObject(1,UUID::class.java)) } } }
    BulkRequestListDto(ids.mapNotNull { loadBulk(c,it) })
} }
internal fun sellerBulk(context:BackendContext,sellerId:String,status:String?):BulkRequestListDto { expireDue(context);return context.dataSource.connection.use { c ->
    val ids=c.prepareStatement("SELECT id FROM bulk_requests WHERE seller_id=? AND (? IS NULL OR status=?) ORDER BY created_at DESC").use { s -> s.setString(1,sellerId);s.setString(2,status);s.setString(3,status);s.executeQuery().use { r -> buildList { while(r.next()) add(r.getObject(1,UUID::class.java)) } } }
    BulkRequestListDto(ids.mapNotNull { loadBulk(c,it) })
} }

internal fun createIndividual(context:BackendContext,principal:AuthenticatedPrincipal,lines:List<DraftLine>):IndividualDemoOrderDto = context.transaction { c ->
    requireLiveUser(c,principal,context)
    if(lines.isEmpty() || lines.size>100) throw bad("INVALID_LINES","Provide between 1 and 100 item lines")
    if(lines.map { it.itemId }.toSet().size!=lines.size) throw bad("DUPLICATE_ITEMS","Each item may appear only once")
    if(lines.any { it.quantity !in 1..999 }) throw bad("INVALID_QUANTITY","Each quantity must be between 1 and 999")
    val seller=c.prepareStatement("SELECT id FROM sellers WHERE is_main_canteen AND active").use { s -> s.executeQuery().use { r -> if(r.next()) r.getString(1) else throw rule("MAIN_CANTEEN_UNAVAILABLE","Main Canteen is unavailable") } }
    val priced=lines.map { line -> c.prepareStatement("SELECT name,price_paise FROM menu_items WHERE id=? AND seller_id=? AND kind='PREPARED' AND published AND availability='AVAILABLE' FOR SHARE").use { s -> s.setString(1,line.itemId);s.setString(2,seller);s.executeQuery().use { r -> if(r.next()) PricedLine(line.itemId,r.getString(1),line.quantity,r.getLong(2)) else throw rule("INDIVIDUAL_ITEM_UNAVAILABLE","Only available Main Canteen prepared food can be saved individually") } } }
    val id=UUID.randomUUID();val sum=totalPaise(priced);val now=context.clock.instant()
    c.prepareStatement("INSERT INTO individual_demo_orders(id,customer_user_id,seller_id,subtotal_paise,created_at) VALUES (?,?,?,?,?)").use { s -> s.setObject(1,id);s.setObject(2,principal.userId);s.setString(3,seller);s.setLong(4,sum);s.setTimestamp(5,Timestamp.from(now));s.executeUpdate() }
    c.prepareStatement("INSERT INTO individual_demo_order_lines(order_id,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise) VALUES (?,?,?,?,?,?)").use { s -> priced.forEach { s.setObject(1,id);s.setString(2,seller);s.setString(3,it.itemId);s.setString(4,it.name);s.setInt(5,it.quantity);s.setLong(6,it.price);s.addBatch() };s.executeBatch() }
    IndividualDemoOrderDto(id.toString(),seller,"DEMO_SAVED",true,false,sum,now.toString(),priced.map { IndividualDemoLineDto(it.itemId,it.name,it.quantity,it.price) })
}
internal fun customerIndividual(context:BackendContext,user:UUID):IndividualDemoOrderListDto = context.dataSource.connection.use { c ->
    val ids=c.prepareStatement("SELECT id FROM individual_demo_orders WHERE customer_user_id=? ORDER BY created_at DESC").use { s -> s.setObject(1,user);s.executeQuery().use { r -> buildList { while(r.next()) add(r.getObject(1,UUID::class.java)) } } }
    IndividualDemoOrderListDto(ids.mapNotNull { loadDemoIndividual(c,it) })
}
