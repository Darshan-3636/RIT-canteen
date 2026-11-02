package edu.msrit.canteen.presentation

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import edu.msrit.canteen.customer.MenuItem
import edu.msrit.canteen.customer.PreviewCatalog
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Private, offline presentation data. It never creates an authenticated backend principal. */
internal class PresentationStore(context: Context, databaseName: String = "rit_presentation_v1.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    private val prefs = context.getSharedPreferences("rit_presentation_session", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts(id TEXT PRIMARY KEY,email TEXT NOT NULL UNIQUE,name TEXT NOT NULL,role TEXT NOT NULL,seller_id TEXT,salt BLOB NOT NULL,password_hash BLOB NOT NULL)")
        db.execSQL("CREATE TABLE cart(customer_id TEXT NOT NULL,item_id TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),PRIMARY KEY(customer_id,item_id),FOREIGN KEY(customer_id) REFERENCES accounts(id))")
        db.execSQL("CREATE TABLE orders(id TEXT PRIMARY KEY,customer_id TEXT NOT NULL,seller_id TEXT NOT NULL,total_paise INTEGER NOT NULL,payment_state TEXT NOT NULL,fulfillment_state TEXT NOT NULL,pickup_code TEXT NOT NULL UNIQUE,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,checkout_key TEXT UNIQUE,FOREIGN KEY(customer_id) REFERENCES accounts(id))")
        db.execSQL("CREATE TABLE order_lines(order_id TEXT NOT NULL,item_id TEXT NOT NULL,name TEXT NOT NULL,quantity INTEGER NOT NULL,unit_paise INTEGER NOT NULL,PRIMARY KEY(order_id,item_id),FOREIGN KEY(order_id) REFERENCES orders(id))")
        db.execSQL("CREATE INDEX orders_seller_status ON orders(seller_id,fulfillment_state)")
        db.execSQL("CREATE INDEX orders_customer ON orders(customer_id,created_at)")
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        // Seed history exactly once in this database. It is never treated as gateway verified revenue.
        val sampleId = "sample-customer"
        val salt = ByteArray(16).also(random::nextBytes)
        db.insertOrThrow("accounts", null, ContentValues().apply {
            put("id", sampleId); put("email", "sample@local.invalid"); put("name", "Campus Guest")
            put("role", "CUSTOMER"); put("salt", salt); put("password_hash", hash(UUID.randomUUID().toString(), salt))
        })
        val samples = listOf(
            Triple("main-canteen", "masala-dosa", 2),
            Triple("north-counter", "veg-samosa", 3),
            Triple("coffee-corner", "corner-coffee", 2),
            Triple("campus-pantry", "pantry-juice", 4),
        )
        samples.forEachIndexed { index, (sellerId, itemId, quantity) ->
            val item = PreviewCatalog.items.firstOrNull { it.id == itemId } ?: return@forEachIndexed
            listOf("COMPLETED", "NOT_READY", "READY").forEachIndexed { stateIndex, state ->
                val id = UUID.nameUUIDFromBytes("rit-presentation-$index-$stateIndex".toByteArray()).toString()
                val at = Instant.now().minusSeconds((index + stateIndex + 1L) * 86400).toEpochMilli()
                db.insertOrThrow("orders", null, ContentValues().apply {
                    put("id", id); put("customer_id", sampleId); put("seller_id", sellerId)
                    put("total_paise", item.price.rupees * quantity * 100L)
                    put("payment_state", "LOCAL_SAMPLE"); put("fulfillment_state", state)
                    put("pickup_code", "SAMP-${index + 1}${stateIndex + 1}07")
                    put("created_at", at); put("updated_at", at)
                })
                insertLine(db, id, item, quantity)
            }
        }
        db.insertOrThrow("meta", null, ContentValues().apply { put("key", "history_seed"); put("value", "1") })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun activeAccount(): LocalAccount? = prefs.getString("account_id", null)?.let(::account)

    @Synchronized fun register(name: String, email: String, password: String, role: LocalRole, sellerId: String?): LocalAccount {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        require(cleanName.length >= 3) { "Enter your full name." }
        require(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(cleanEmail)) { "Enter a valid email address." }
        require(password.length >= 10 && password.any(Char::isLetter) && password.any(Char::isDigit)) { "Use 10 characters with a letter and a number." }
        require(role == LocalRole.CUSTOMER || PreviewCatalog.sellers.any { it.id == sellerId }) { "Choose your canteen." }
        require(!cleanEmail.endsWith("@local.invalid")) { "Use another email address." }
        require(accountByEmail(cleanEmail) == null) { "This email is already registered." }
        val salt = ByteArray(16).also(random::nextBytes)
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("accounts", null, ContentValues().apply {
            put("id", id); put("email", cleanEmail); put("name", cleanName)
            put("role", role.name); put("seller_id", if (role == LocalRole.SELLER) sellerId else null)
            put("salt", salt); put("password_hash", hash(password, salt))
        })
        prefs.edit().putString("account_id", id).apply()
        return requireNotNull(account(id))
    }

    @Synchronized fun login(email: String, password: String): LocalAccount {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val db = readableDatabase
        val row = db.rawQuery("SELECT id,salt,password_hash FROM accounts WHERE email=?", arrayOf(cleanEmail)).use { cursor ->
            if (!cursor.moveToFirst()) null else Triple(cursor.getString(0), cursor.getBlob(1), cursor.getBlob(2))
        } ?: throw IllegalArgumentException("Email or password is incorrect.")
        require(java.security.MessageDigest.isEqual(row.third, hash(password, row.second))) { "Email or password is incorrect." }
        prefs.edit().putString("account_id", row.first).apply()
        return requireNotNull(account(row.first))
    }

    @Synchronized fun logout() { prefs.edit().remove("account_id").apply() }

    @Synchronized fun account(id: String): LocalAccount? = readableDatabase.rawQuery(
        "SELECT id,email,name,role,seller_id FROM accounts WHERE id=?", arrayOf(id)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.account() else null }

    private fun accountByEmail(email: String): LocalAccount? = readableDatabase.rawQuery(
        "SELECT id,email,name,role,seller_id FROM accounts WHERE email=?", arrayOf(email)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.account() else null }

    @Synchronized fun cart(customerId: String): List<LocalCartLine> = readableDatabase.rawQuery(
        "SELECT item_id,quantity FROM cart WHERE customer_id=? ORDER BY rowid", arrayOf(customerId)
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) PreviewCatalog.items.firstOrNull { it.id == cursor.getString(0) }
            ?.let { add(LocalCartLine(it, cursor.getInt(1))) }
    } }

    @Synchronized fun setQuantity(customerId: String, itemId: String, quantity: Int) {
        require(account(customerId)?.role == LocalRole.CUSTOMER)
        val item = requireNotNull(PreviewCatalog.items.firstOrNull { it.id == itemId })
        require(quantity in 0..99)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val currentSeller = cart(customerId).firstOrNull()?.item?.sellerId
            require(quantity == 0 || currentSeller == null || currentSeller == item.sellerId) { "Finish this canteen's cart before adding another." }
            if (quantity == 0) db.delete("cart", "customer_id=? AND item_id=?", arrayOf(customerId, itemId))
            else db.insertWithOnConflict("cart", null, ContentValues().apply {
                put("customer_id", customerId); put("item_id", itemId); put("quantity", quantity)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized fun placeUnpaidOrder(customerId: String, checkoutKey: String): LocalOrder {
        require(account(customerId)?.role == LocalRole.CUSTOMER)
        require(checkoutKey.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id FROM orders WHERE customer_id=? AND checkout_key=?", arrayOf(customerId, checkoutKey)).use {
                if (it.moveToFirst()) return requireNotNull(order(it.getString(0)))
            }
            val lines = cart(customerId)
            require(lines.isNotEmpty()) { "Your cart is empty." }
            val sellerId = lines.first().item.sellerId
            require(lines.all { it.item.sellerId == sellerId })
            val id = UUID.randomUUID().toString()
            val now = Instant.now().toEpochMilli()
            val code = uniquePickupCode(db)
            db.insertOrThrow("orders", null, ContentValues().apply {
                put("id", id); put("customer_id", customerId); put("seller_id", sellerId)
                put("total_paise", lines.sumOf { it.item.price.rupees * it.quantity * 100L })
                put("payment_state", "PENDING"); put("fulfillment_state", "AWAITING_PAYMENT")
                put("pickup_code", code); put("created_at", now); put("updated_at", now); put("checkout_key", checkoutKey)
            })
            lines.forEach { insertLine(db, id, it.item, it.quantity) }
            db.delete("cart", "customer_id=?", arrayOf(customerId))
            db.setTransactionSuccessful()
            return requireNotNull(order(id))
        } finally { db.endTransaction() }
    }

    @Synchronized fun order(id: String): LocalOrder? = readableDatabase.rawQuery(
        "SELECT id,customer_id,seller_id,total_paise,payment_state,fulfillment_state,pickup_code,created_at,updated_at FROM orders WHERE id=?", arrayOf(id)
    ).use { if (it.moveToFirst()) it.order() else null }

    @Synchronized fun customerOrders(customerId: String): List<LocalOrder> = ordersFor("customer_id", customerId)
    @Synchronized fun sellerOrders(actor: LocalAccount): List<LocalOrder> {
        require(actor.role == LocalRole.SELLER && account(actor.id)?.sellerId == actor.sellerId)
        return ordersFor("seller_id", requireNotNull(actor.sellerId))
    }

    private fun ordersFor(column: String, value: String): List<LocalOrder> = readableDatabase.rawQuery(
        "SELECT id,customer_id,seller_id,total_paise,payment_state,fulfillment_state,pickup_code,created_at,updated_at FROM orders WHERE $column=? ORDER BY created_at DESC", arrayOf(value)
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.order()) } }

    @Synchronized fun orderLines(orderId: String): List<LocalOrderLine> = readableDatabase.rawQuery(
        "SELECT item_id,name,quantity,unit_paise FROM order_lines WHERE order_id=?", arrayOf(orderId)
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(LocalOrderLine(cursor.getString(0), cursor.getString(1), cursor.getInt(2), cursor.getLong(3))) } }

    @Synchronized fun markReady(actor: LocalAccount, orderId: String): Boolean = transition(
        actor, orderId, "NOT_READY", "READY", null
    )

    @Synchronized fun complete(actor: LocalAccount, orderId: String, code: String): Boolean = transition(
        actor, orderId, "READY", "COMPLETED", code
    )

    private fun transition(actor: LocalAccount, id: String, from: String, to: String, code: String?): Boolean {
        if (actor.role != LocalRole.SELLER || account(actor.id)?.sellerId != actor.sellerId) return false
        val sellerId = actor.sellerId ?: return false
        val normalized = code?.uppercase(Locale.ROOT)?.filter { it.isLetterOrDigit() }
        val current = order(id) ?: return false
        if (current.sellerId != sellerId || current.fulfillmentState != from || current.paymentState != "LOCAL_SAMPLE") return false
        if (to == "COMPLETED" && normalized != current.pickupCode.filter { it.isLetterOrDigit() }) return false
        return writableDatabase.update("orders", ContentValues().apply {
            put("fulfillment_state", to); put("updated_at", Instant.now().toEpochMilli())
        }, "id=? AND seller_id=? AND fulfillment_state=?", arrayOf(id, sellerId, from)) == 1
    }

    private fun uniquePickupCode(db: SQLiteDatabase): String {
        while (true) {
            val code = (1..2).joinToString("-") { (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
            db.rawQuery("SELECT 1 FROM orders WHERE pickup_code=?", arrayOf(code)).use { if (!it.moveToFirst()) return code }
        }
    }

    private fun insertLine(db: SQLiteDatabase, id: String, item: MenuItem, quantity: Int) {
        db.insertOrThrow("order_lines", null, ContentValues().apply {
            put("order_id", id); put("item_id", item.id); put("name", item.name)
            put("quantity", quantity); put("unit_paise", item.price.rupees * 100L)
        })
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }

    private fun Cursor.account() = LocalAccount(getString(0), getString(1), getString(2), LocalRole.valueOf(getString(3)), getString(4))
    private fun Cursor.order() = LocalOrder(getString(0), getString(1), getString(2), getLong(3), getString(4), getString(5), getString(6), getLong(7), getLong(8))
}

internal enum class LocalRole { CUSTOMER, SELLER }
internal data class LocalAccount(val id: String, val email: String, val name: String, val role: LocalRole, val sellerId: String?)
internal data class LocalCartLine(val item: MenuItem, val quantity: Int)
internal data class LocalOrder(val id: String, val customerId: String, val sellerId: String, val totalPaise: Long,
    val paymentState: String, val fulfillmentState: String, val pickupCode: String, val createdAt: Long, val updatedAt: Long)
internal data class LocalOrderLine(val itemId: String, val name: String, val quantity: Int, val unitPaise: Long)
