package edu.msrit.canteen.backend.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import edu.msrit.canteen.backend.auth.DemoIdentitySeed
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

object BackendBootstrap {
    fun open(config: BackendConfig): BackendContext {
        val pool = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePassword
            maximumPoolSize = 8
            minimumIdle = 1
            connectionTimeout = 10_000
            connectionInitSql = "SET TIME ZONE 'UTC'"
            poolName = "rit-canteen-backend"
        })
        try {
            Flyway.configure()
                .dataSource(pool)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load()
                .migrate()
            if (config.demoSeedEnabled) seedPreviewSellers(pool)
            val context = BackendContext(pool, config)
            if (config.demoSeedEnabled) DemoIdentitySeed.seed(context)
            return context
        } catch (failure: Throwable) {
            pool.close()
            throw failure
        }
    }

    fun isReady(dataSource: DataSource): Boolean = try {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1").use { it.next() && it.getInt(1) == 1 }
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun seedPreviewSellers(dataSource: DataSource) {
        val fixtures = listOf(
            SellerFixture("main-canteen", "Main Canteen", "Campus meals and familiar favourites.", true),
            SellerFixture("north-counter", "North Counter · demo", "Prepared lunch and snacks for group requests.", false),
            SellerFixture("coffee-corner", "Coffee Corner · demo", "Drinks and light bites for group requests.", false),
            SellerFixture("campus-pantry", "Campus Pantry · demo", "Packaged snacks and drinks for group requests.", false),
        )
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """INSERT INTO sellers (id, name, description, is_main_canteen)
                       VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING"""
                ).use { statement ->
                    fixtures.forEach { fixture ->
                        statement.setString(1, fixture.id)
                        statement.setString(2, fixture.name)
                        statement.setString(3, fixture.description)
                        statement.setBoolean(4, fixture.isMain)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private data class SellerFixture(val id: String, val name: String, val description: String, val isMain: Boolean)
}

fun BackendContext.close() { (dataSource as? HikariDataSource)?.close() }
