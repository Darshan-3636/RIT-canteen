package edu.msrit.canteen.backend.core

import java.time.ZoneId

enum class RuntimeEnvironment { LOCAL, TEST, PRODUCTION }

data class BackendConfig(
    val environment: RuntimeEnvironment,
    val httpHost: String,
    val httpPort: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val demoSeedEnabled: Boolean,
) {
    val sellerZone: ZoneId = ZoneId.of("Asia/Kolkata")

    init {
        require(httpHost.isNotBlank()) { "HTTP_HOST is required" }
        require(httpPort in 1..65535) { "HTTP_PORT must be 1..65535" }
        require(databaseUrl.startsWith("jdbc:postgresql://")) { "DATABASE_URL must be a PostgreSQL JDBC URL" }
        require(databaseUser.isNotBlank()) { "DATABASE_USER is required" }
        require(databasePassword.isNotBlank() && databasePassword != "replace-with-a-local-random-password") {
            "Set a local DATABASE_PASSWORD"
        }
        require(!demoSeedEnabled || environment == RuntimeEnvironment.LOCAL) {
            "DEMO_SEED_ENABLED is permitted only with RIT_ENV=local"
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): BackendConfig {
            val mode = when (env["RIT_ENV"]?.lowercase() ?: "local") {
                "local" -> RuntimeEnvironment.LOCAL
                "test" -> RuntimeEnvironment.TEST
                "production" -> RuntimeEnvironment.PRODUCTION
                else -> error("RIT_ENV must be local, test, or production")
            }
            val seed = when (env["DEMO_SEED_ENABLED"]?.lowercase() ?: "false") {
                "true" -> true
                "false" -> false
                else -> error("DEMO_SEED_ENABLED must be true or false")
            }
            return BackendConfig(
                environment = mode,
                httpHost = env["HTTP_HOST"] ?: "127.0.0.1",
                httpPort = (env["HTTP_PORT"] ?: "8080").toIntOrNull()
                    ?: error("HTTP_PORT must be an integer"),
                databaseUrl = env["DATABASE_URL"] ?: error("DATABASE_URL is required"),
                databaseUser = env["DATABASE_USER"] ?: error("DATABASE_USER is required"),
                databasePassword = env["DATABASE_PASSWORD"] ?: error("DATABASE_PASSWORD is required"),
                demoSeedEnabled = seed,
            )
        }
    }
}
