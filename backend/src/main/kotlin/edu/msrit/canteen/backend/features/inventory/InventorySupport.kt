package edu.msrit.canteen.backend.features.inventory

import edu.msrit.canteen.backend.features.engagement.badRequest
import edu.msrit.canteen.backend.features.engagement.conflict
import edu.msrit.canteen.backend.features.engagement.queryOne
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.util.UUID

internal fun inventoryId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
    ?: throw badRequest("INVALID_ID","Expected a UUID")

/** Serializes retries for one request ID so a concurrent duplicate sees the committed result. */
internal fun Connection.lockInventoryRequest(id: UUID) {
    prepareStatement("SELECT pg_advisory_xact_lock(?)").use { statement ->
        statement.setLong(1, id.mostSignificantBits xor id.leastSignificantBits)
        statement.executeQuery().use { it.next() }
    }
}

internal fun inventoryQuantity(raw: String, positive: Boolean = true): BigDecimal {
    val value = raw.toBigDecimalOrNull() ?: throw badRequest("INVALID_QUANTITY","Quantity must be a decimal number")
    if (value.scale() > 6 || value.precision() > 20 ||
        (positive && value <= BigDecimal.ZERO) || (!positive && value == BigDecimal.ZERO))
        throw badRequest("INVALID_QUANTITY","Quantity must fit six decimal places and be ${if (positive) "positive" else "nonzero"}")
    return value.setScale(6,RoundingMode.UNNECESSARY)
}

/** Stored base quantity is exact to six decimals; inexact conversions are rejected. */
internal fun Connection.convertQuantity(seller: String, material: UUID, unitCode: String,
                                        quantity: BigDecimal): BigDecimal {
    val source = queryOne("SELECT dimension,base_factor FROM inventory_units WHERE code=?",unitCode) {
        it.getString(1) to it.getBigDecimal(2)
    } ?: throw badRequest("INVALID_UNIT","Unknown measurement unit")
    val base = queryOne("""SELECT u.dimension,u.base_factor FROM inventory_materials m
        JOIN inventory_units u ON u.code=m.base_unit_code WHERE m.seller_id=? AND m.id=? AND m.active""",
        seller,material) { it.getString(1) to it.getBigDecimal(2) }
        ?: throw badRequest("INVALID_MATERIAL","Material is missing or inactive for this seller")
    if (source.first != base.first) throw badRequest("UNIT_MISMATCH","Unit dimension does not match the material")
    return try { quantity.multiply(source.second).divide(base.second,6,RoundingMode.UNNECESSARY)
        .also { if(it <= BigDecimal.ZERO || it.precision()>20) throw ArithmeticException() } }
    catch (_: ArithmeticException) { throw badRequest("INEXACT_CONVERSION","Quantity cannot be represented exactly in the base unit") }
}

internal fun Connection.requireInventorySeller(seller: String) {
    if (queryOne("SELECT active FROM sellers WHERE id=?",seller) { it.getBoolean(1) } != true)
        throw conflict("SELLER_UNAVAILABLE","Seller is inactive or missing")
}
