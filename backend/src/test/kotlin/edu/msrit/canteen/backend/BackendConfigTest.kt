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
}
