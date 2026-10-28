package edu.msrit.canteen.backend.features.integration

import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import edu.msrit.canteen.backend.features.commerce.seedCommerceFixtures
import edu.msrit.canteen.backend.features.engagement.ItemLookup
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Uses a unique schema per run inside explicitly disposable PostgreSQL databases; never wipes existing rows. */
class CatalogParityIntegrationTest {
    private fun config(kind: String): BackendConfig {
        val env = System.getenv()
        val url = env["RIT_TEST_${kind}_DATABASE_URL"]
        assumeTrue(env["RIT_INTEGRATION_DISPOSABLE_${kind}_DB"] == "YES" && url != null &&
            env["RIT_TEST_DATABASE_PASSWORD"] != null, "Dedicated disposable $kind DB required")
        require(Regex("^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/rit_canteen_${kind.lowercase()}$").matches(url!!))
        val user = env.getValue("RIT_TEST_DATABASE_USER")
        val password = env.getValue("RIT_TEST_DATABASE_PASSWORD")
        val schema = "itest_${UUID.randomUUID().toString().replace("-", "")}"
        DriverManager.getConnection(url, user, password).use { db ->
            db.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        return BackendConfig(RuntimeEnvironment.LOCAL, "127.0.0.1", 8080,
            "$url?currentSchema=$schema", user, password, demoSeedEnabled = true)
    }

    @Test fun freshCatalogHasExactParityAndSellerEditSurvivesRestart() {
        val config = config("FRESH")
        BackendBootstrap.open(config).also { context ->
            try {
                seedCommerceFixtures(context)
                assertCatalogCounts(context)
                context.dataSource.connection.use { db ->
                    db.createStatement().use { sql ->
                        sql.executeUpdate("UPDATE menu_items SET name='Seller edited dosa',price_paise=4300,availability='UNAVAILABLE',updated_at=now() WHERE id='plain-dosa'")
                    }
                }
            } finally { context.close() }
        }
        BackendBootstrap.open(config).also { context ->
            try {
                seedCommerceFixtures(context)
                assertCatalogCounts(context)
                context.dataSource.connection.use { db ->
                    db.createStatement().use { sql ->
                        sql.executeQuery("SELECT name,price_paise,availability FROM menu_items WHERE id='plain-dosa'").use { rows ->
                            assertTrue(rows.next()); assertEquals("Seller edited dosa", rows.getString(1))
                            assertEquals(4300, rows.getInt(2)); assertEquals("UNAVAILABLE", rows.getString(3))
                        }
                        sql.executeQuery("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank").use { rows ->
                            val versions = buildList { while (rows.next()) { assertTrue(rows.getBoolean(2)); add(rows.getString(1)) } }
                            assertEquals(listOf("001","002","003","004","005","020","021","022","040","041","042","043","044"), versions)
                        }
                    }
                }
            } finally { context.close() }
        }
    }

    @Test fun legacyTwelveItemSeedUpgradesOnlyPristineRows() {
        val config = config("LEGACY")
        BackendBootstrap.open(config).also { context ->
            try {
                val rows = checkNotNull(javaClass.getResourceAsStream("/demo/catalog-items-v1-legacy.tsv"))
                    .bufferedReader().use { it.readLines().filter(String::isNotBlank).map { row -> row.split('\t') } }
                assertEquals(12, rows.size)
                context.dataSource.connection.use { db ->
                    db.autoCommit = false
                    db.prepareStatement("INSERT INTO menu_categories(id,name) VALUES (?,?) ON CONFLICT DO NOTHING").use { sql ->
                        listOf("dosa" to "Dosa", "rice" to "Rice", "snacks" to "Snacks", "drinks" to "Beverages", "packs" to "Packaged").forEach { (id, name) ->
                            sql.setString(1,id); sql.setString(2,name); sql.addBatch()
                        }; sql.executeBatch()
                    }
                    db.prepareStatement("INSERT INTO seller_categories(seller_id,category_id) VALUES (?,?) ON CONFLICT DO NOTHING").use { sql ->
                        rows.map { it[1] to it[2] }.distinct().forEach { (seller, category) ->
                            sql.setString(1,seller); sql.setString(2,category); sql.addBatch()
                        }; sql.executeBatch()
                    }
                    db.prepareStatement("""INSERT INTO menu_items(id,seller_id,category_id,name,description,price_paise,kind,dietary,availability,published,unavailable_visibility,image_key)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""").use { sql ->
                        rows.forEach { row ->
                            row.forEachIndexed { index, value ->
                                when (index) {
                                    5 -> sql.setLong(index + 1, value.toLong())
                                    9 -> sql.setBoolean(index + 1, value.toBooleanStrict())
                                    else -> sql.setString(index + 1, value)
                                }
                            }; sql.addBatch()
                        }; sql.executeBatch()
                    }
                    db.createStatement().use { it.executeUpdate("UPDATE menu_items SET price_paise=9100,updated_at=now() WHERE id='veg-biryani'") }
                    db.commit()
                }
                seedCommerceFixtures(context)
                seedCommerceFixtures(context)
                assertCatalogCounts(context)
                context.dataSource.connection.use { db ->
                    db.createStatement().use { sql ->
                        sql.executeQuery("SELECT category_id,name,price_paise FROM menu_items WHERE id='idli'").use { rows2 ->
                            assertTrue(rows2.next()); assertEquals("breakfast", rows2.getString(1)); assertEquals("Idli with Sambar", rows2.getString(2))
                        }
                        sql.executeQuery("SELECT price_paise FROM menu_items WHERE id='veg-biryani'").use { rows2 ->
                            assertTrue(rows2.next()); assertEquals(9100, rows2.getInt(1))
                        }
                    }
                }
            } finally { context.close() }
        }
    }

    @Test fun postgresCommercePortUsesVisibleItemsAndActualSellerScopedRequests() {
        val config = config("FRESH")
        BackendBootstrap.open(config).also { context ->
            try {
                seedCommerceFixtures(context)
                val port = PostgresCommerceReadPort(context)
                assertEquals(ItemLookup.Found("campus-pantry"), port.lookupItem("pantry-juice"))
                assertEquals(ItemLookup.Missing, port.lookupItem("not-a-real-item"))
                context.dataSource.connection.use { db ->
                    db.prepareStatement("UPDATE menu_items SET published=FALSE WHERE id='pantry-water'").use { it.executeUpdate() }
                    assertEquals(ItemLookup.Missing, port.lookupItem("pantry-water"))
                    val user = db.createStatement().use { sql ->
                        sql.executeQuery("SELECT id FROM users WHERE email='student.arya@demo.ritcanteen.test'").use { rows ->
                            assertTrue(rows.next()); rows.getObject(1, UUID::class.java)
                        }
                    }
                    db.prepareStatement("""INSERT INTO bulk_requests(id,customer_user_id,seller_id,current_version,status,idempotency_key,idempotency_hash)
                        VALUES (?,?,?,1,'SUBMITTED',?,?)""").use { sql ->
                        listOf("main-canteen", "north-counter").forEachIndexed { index, seller ->
                            sql.setObject(1, UUID.randomUUID()); sql.setObject(2, user); sql.setString(3, seller)
                            sql.setString(4, "port-fixture-$index"); sql.setBytes(5, ByteArray(32) { index.toByte() }); sql.addBatch()
                        }; sql.executeBatch()
                    }
                }
                val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                assertEquals(1, port.demoRequestCounts("main-canteen", today, today).total)
                assertEquals(1, port.demoRequestCounts("north-counter", today, today).total)
                assertEquals(2, port.demoRequestCounts(null, today, today).total)
                assertEquals(0, port.demoRequestCounts("main-canteen", today.plusDays(1), null).total)
            } finally { context.close() }
        }
    }

    private fun assertCatalogCounts(context: edu.msrit.canteen.backend.core.BackendContext) {
        context.dataSource.connection.use { db ->
            db.createStatement().use { sql ->
                sql.executeQuery("SELECT count(*) FROM menu_items").use { rows -> assertTrue(rows.next()); assertEquals(72,rows.getInt(1)) }
                sql.executeQuery("SELECT seller_id,count(*) FROM menu_items GROUP BY seller_id").use { rows ->
                    val counts = buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) }
                    assertEquals(mapOf("main-canteen" to 18,"north-counter" to 18,"coffee-corner" to 18,"campus-pantry" to 18), counts)
                }
                sql.executeQuery("SELECT count(*) FROM menu_items WHERE image_key=id AND price_paise>=0").use { rows ->
                    assertTrue(rows.next()); assertEquals(72, rows.getInt(1))
                }
                sql.executeQuery("SELECT count(*) FROM menu_items WHERE seller_id='campus-pantry' AND category_id='drinks' AND id IN ('pantry-juice','pantry-water') AND kind='PACKAGED'").use { rows ->
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1))
                }
            }
        }
    }
}
