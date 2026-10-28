package edu.msrit.canteen.backend

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rehearses the actual V046 -> V047 path without touching a shared or hosted schema. */
class SandboxMigrationUpgradeTest {
    @Test fun existingV046DataSurvivesV047AndSecondStartup() {
        val env = System.getenv()
        val baseUrl = env["RIT_TEST_FRESH_DATABASE_URL"]
        assumeTrue(env["RIT_INTEGRATION_DISPOSABLE_FRESH_DB"] == "YES" &&
            baseUrl != null && env["RIT_TEST_DATABASE_USER"] != null &&
            env["RIT_TEST_DATABASE_PASSWORD"] != null, "Dedicated disposable PostgreSQL required")
        require(Regex("^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/rit_canteen_fresh$").matches(baseUrl!!))
        val user = env.getValue("RIT_TEST_DATABASE_USER")
        val password = env.getValue("RIT_TEST_DATABASE_PASSWORD")
        val schema = "itest_v047_${UUID.randomUUID().toString().replace("-", "")}"
        DriverManager.getConnection(baseUrl, user, password).use { db ->
            db.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        try {
            val url = "$baseUrl?currentSchema=$schema"
            fun flyway() = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").validateMigrationNaming(true)
            flyway().target(MigrationVersion.fromVersion("046")).load().migrate()
            val customer = UUID.randomUUID()
            val order = UUID.randomUUID()
            val request = UUID.randomUUID()
            DriverManager.getConnection(url, user, password).use { db ->
                db.createStatement().use { sql ->
                    sql.executeUpdate("""INSERT INTO users(id,email,password_hash,full_name,affiliation)
                        VALUES ('$customer','v047-test@example.test','test-hash-which-is-long-enough','Migration Customer','STUDENT')""")
                    sql.executeUpdate("""INSERT INTO sellers(id,name,description,is_main_canteen)
                        VALUES ('main-canteen','Main Canteen','[DEMO / SYNTHETIC]',TRUE)""")
                    sql.executeUpdate("INSERT INTO menu_categories(id,name) VALUES ('meals','Meals')")
                    sql.executeUpdate("INSERT INTO seller_categories(seller_id,category_id) VALUES ('main-canteen','meals')")
                    sql.executeUpdate("""INSERT INTO menu_items(id,seller_id,category_id,name,price_paise,kind,dietary,image_key)
                        VALUES ('demo-meal','main-canteen','meals','[DEMO / SYNTHETIC] Meal',10000,'PREPARED','VEGETARIAN','demo-meal')""")
                    sql.executeUpdate("INSERT INTO individual_customer_carts(customer_user_id,version) VALUES ('$customer',3)")
                    sql.executeUpdate("""INSERT INTO individual_customer_cart_lines(customer_user_id,seller_id,item_id,quantity)
                        VALUES ('$customer','main-canteen','demo-meal',2)""")
                    sql.executeUpdate("""INSERT INTO bulk_customer_drafts(customer_user_id,seller_id,version)
                        VALUES ('$customer','main-canteen',2)""")
                    sql.executeUpdate("""INSERT INTO individual_demo_orders(id,customer_user_id,seller_id,subtotal_paise)
                        VALUES ('$order','$customer','main-canteen',20000)""")
                    sql.executeUpdate("""INSERT INTO individual_demo_order_lines
                        (order_id,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise)
                        VALUES ('$order','main-canteen','demo-meal','[DEMO / SYNTHETIC] Meal',2,10000)""")
                    sql.executeUpdate("""INSERT INTO bulk_requests
                        (id,customer_user_id,seller_id,current_version,status,idempotency_key,idempotency_hash)
                        VALUES ('$request','$customer','main-canteen',1,'SUBMITTED','v047-test',decode(repeat('00',32),'hex'))""")
                }
            }
            flyway().load().migrate()
            flyway().load().migrate()
            DriverManager.getConnection(url, user, password).use { db ->
                db.createStatement().use { sql ->
                    fun count(table: String) = sql.executeQuery("SELECT count(*) FROM $table").use { rows ->
                        assertTrue(rows.next()); rows.getInt(1)
                    }
                    assertEquals(1, count("users"))
                    assertEquals(1, count("individual_customer_carts"))
                    assertEquals(1, count("individual_customer_cart_lines"))
                    assertEquals(1, count("bulk_customer_drafts"))
                    assertEquals(1, count("individual_demo_orders"))
                    assertEquals(1, count("individual_demo_order_lines"))
                    assertEquals(1, count("bulk_requests"))
                    assertEquals(0, count("sandbox_payment_attempts"))
                    sql.executeQuery("SELECT payment_verified,demo_only,subtotal_paise FROM individual_demo_orders WHERE id='$order'").use { rows ->
                        assertTrue(rows.next()); assertFalse(rows.getBoolean(1)); assertTrue(rows.getBoolean(2))
                        assertEquals(20000L, rows.getLong(3))
                    }
                    sql.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='047' AND success").use { rows ->
                        assertTrue(rows.next()); assertEquals(1, rows.getInt(1))
                    }
                }
            }
        } finally {
            // This exact schema was generated above inside the explicitly disposable local database.
            DriverManager.getConnection(baseUrl, user, password).use { db ->
                db.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
