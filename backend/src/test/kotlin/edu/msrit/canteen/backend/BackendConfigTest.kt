package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackendConfigTest {
    private val base = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://127.0.0.1:5432/rit_canteen",
        "DATABASE_USER" to "rit_canteen",
        "DATABASE_PASSWORD" to "local-test-only-password",
    )

    @Test fun defaultsBindToLoopbackAndDoNotSeed() {
        val config = BackendConfig.fromEnvironment(base)
        assertEquals("127.0.0.1", config.httpHost)
        assertEquals(8080, config.httpPort)
        assertEquals(RuntimeEnvironment.LOCAL, config.environment)
        assertFalse(config.demoSeedEnabled)
    }

    @Test fun productionCannotEnableDemoSeeds() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(base + mapOf("RIT_ENV" to "production", "DEMO_SEED_ENABLED" to "true"))
        }
    }

    @Test fun placeholderPasswordIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(base + ("DATABASE_PASSWORD" to "replace-with-a-local-random-password"))
        }
    }

    @Test fun invalidPortIsRejected() {
        assertFailsWith<IllegalStateException> {
            BackendConfig.fromEnvironment(base + ("HTTP_PORT" to "not-a-number"))
        }
    }

    @Test fun cloudDemoRequiresExplicitSecurePrivateBootstrap() {
        val required = base + mapOf(
            "RIT_ENV" to "cloud_demo", "PORT" to "10000",
            "DATABASE_URL" to "jdbc:postgresql://pool.example:5432/postgres?sslmode=require",
            "HTTP_HOST" to "0.0.0.0", "RIT_CLOUD_DEMO_SEED_ENABLED" to "true",
            "RIT_CLOUD_DEMO_CUSTOMER_EMAIL" to "demo.customer@example.test",
            "RIT_CLOUD_DEMO_CUSTOMER_PASSWORD" to "Unique-Customer-Password-2048!",
            "RIT_CLOUD_DEMO_OWNER_EMAIL" to "demo.owner@example.test",
            "RIT_CLOUD_DEMO_OWNER_PASSWORD" to "Unique-Owner-Password-2048!",
        )
        val config = BackendConfig.fromEnvironment(required)
        assertEquals(RuntimeEnvironment.CLOUD_DEMO, config.environment)
        assertEquals(10000, config.httpPort)
        assertEquals("0.0.0.0", config.httpHost)
        assertEquals(2, config.databasePoolSize)
        assertFalse(config.toString().contains("Unique-Customer-Password-2048!"))
        assertFalse(config.toString().contains("demo.customer@example.test"))
    }

    @Test fun cloudDemoRejectsMissingOrPublicFixtureCredentialsAndUnsafeDatabase() {
        val required = base + mapOf(
            "RIT_ENV" to "cloud_demo", "HTTP_HOST" to "0.0.0.0",
            "DATABASE_URL" to "jdbc:postgresql://pool.example:5432/postgres?sslmode=require",
            "RIT_CLOUD_DEMO_SEED_ENABLED" to "true",
            "RIT_CLOUD_DEMO_CUSTOMER_EMAIL" to "demo.customer@example.test",
            "RIT_CLOUD_DEMO_CUSTOMER_PASSWORD" to "Unique-Customer-Password-2048!",
            "RIT_CLOUD_DEMO_OWNER_EMAIL" to "demo.owner@example.test",
            "RIT_CLOUD_DEMO_OWNER_PASSWORD" to "Unique-Owner-Password-2048!",
        )
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(required - "RIT_CLOUD_DEMO_OWNER_PASSWORD")
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(required + ("RIT_CLOUD_DEMO_OWNER_PASSWORD" to "RitDemo!OwnerMain01"))
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(required + ("RIT_CLOUD_DEMO_OWNER_PASSWORD" to "RitDemo!Manager01"))
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(required + ("DATABASE_URL" to "jdbc:postgresql://127.0.0.1:5432/db?sslmode=require"))
        }
    }

    @Test fun cloudSeedCannotBeEnabledOutsideCloudDemo() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(base + mapOf("RIT_CLOUD_DEMO_SEED_ENABLED" to "true"))
        }
    }

    @Test fun cloudDemoRejectsEveryPublishedLocalFixtureEmailAndPasswordFamily() {
        val cloud = base + mapOf(
            "RIT_ENV" to "cloud_demo", "HTTP_HOST" to "0.0.0.0",
            "DATABASE_URL" to "jdbc:postgresql://pool.example:5432/postgres?sslmode=require",
            "RIT_CLOUD_DEMO_SEED_ENABLED" to "true",
            "RIT_CLOUD_DEMO_CUSTOMER_EMAIL" to "demo.customer@example.test",
            "RIT_CLOUD_DEMO_CUSTOMER_PASSWORD" to "Unique-Customer-Password-2048!",
            "RIT_CLOUD_DEMO_OWNER_EMAIL" to "demo.owner@example.test",
            "RIT_CLOUD_DEMO_OWNER_PASSWORD" to "Unique-Owner-Password-2048!",
        )
        for (email in BackendConfig.RESERVED_LOCAL_DEMO_EMAILS) {
            assertFailsWith<IllegalArgumentException> {
                BackendConfig.fromEnvironment(cloud + ("RIT_CLOUD_DEMO_OWNER_EMAIL" to email))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(cloud + ("RIT_CLOUD_DEMO_OWNER_PASSWORD" to "RitDemo!NotARealCredential0001!"))
        }
    }
}
