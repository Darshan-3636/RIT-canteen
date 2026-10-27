package edu.msrit.canteen.backend.core

import java.time.ZoneId

enum class RuntimeEnvironment { LOCAL, TEST, CLOUD_DEMO, PRODUCTION }

data class BackendConfig(
    val environment: RuntimeEnvironment,
    val httpHost: String,
    val httpPort: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val demoSeedEnabled: Boolean,
    val cloudDemoSeedEnabled: Boolean = false,
    val cloudDemoCustomerEmail: String? = null,
    val cloudDemoCustomerPassword: String? = null,
    val cloudDemoOwnerEmail: String? = null,
    val cloudDemoOwnerPassword: String? = null,
    val databasePoolSize: Int = 2,
) {
    val sellerZone: ZoneId = ZoneId.of("Asia/Kolkata")

    override fun toString(): String = "BackendConfig(environment=$environment, httpHost=$httpHost, httpPort=$httpPort, " +
        "databaseUrl=<redacted>, databaseUser=<redacted>, databasePassword=<redacted>, " +
        "demoSeedEnabled=$demoSeedEnabled, cloudDemoSeedEnabled=$cloudDemoSeedEnabled, " +
        "cloudDemoCustomerEmail=<redacted>, cloudDemoCustomerPassword=<redacted>, " +
        "cloudDemoOwnerEmail=<redacted>, cloudDemoOwnerPassword=<redacted>, databasePoolSize=$databasePoolSize)"

    init {
        require(httpHost.isNotBlank()) { "HTTP_HOST is required" }
        require(httpPort in 1..65535) { "HTTP_PORT must be 1..65535" }
        require(databaseUrl.startsWith("jdbc:postgresql://")) { "DATABASE_URL must be a PostgreSQL JDBC URL" }
        require(databaseUser.isNotBlank()) { "DATABASE_USER is required" }
        require(databasePassword.isNotBlank() && databasePassword != "replace-with-a-local-random-password") {
            "Set a local DATABASE_PASSWORD"
        }
        require(databasePoolSize in 1..4) { "DB_POOL_SIZE must be between 1 and 4" }
        require(!demoSeedEnabled || environment == RuntimeEnvironment.LOCAL) {
            "DEMO_SEED_ENABLED is permitted only with RIT_ENV=local"
        }
        require(!cloudDemoSeedEnabled || environment == RuntimeEnvironment.CLOUD_DEMO) {
            "RIT_CLOUD_DEMO_SEED_ENABLED is permitted only with RIT_ENV=cloud_demo"
        }
        if (environment == RuntimeEnvironment.CLOUD_DEMO) {
            require(cloudDemoSeedEnabled) { "Cloud demo bootstrap must be explicitly enabled" }
            require(httpHost == "0.0.0.0") { "HTTP_HOST must be 0.0.0.0 in cloud_demo mode" }
            require("sslmode=require" in databaseUrl.lowercase()) { "Cloud demo DATABASE_URL must require PostgreSQL SSL" }
            require(!databaseUrl.contains("localhost", ignoreCase = true) && !databaseUrl.contains("127.0.0.1")) {
                "Cloud demo DATABASE_URL must use the private hosted database endpoint"
            }
            requireStrongCloudPassword(cloudDemoCustomerPassword, "RIT_CLOUD_DEMO_CUSTOMER_PASSWORD")
            requireStrongCloudPassword(cloudDemoOwnerPassword, "RIT_CLOUD_DEMO_OWNER_PASSWORD")
            require(cloudDemoCustomerEmail.isValidConfiguredEmail() && cloudDemoOwnerEmail.isValidConfiguredEmail()) {
                "Cloud demo customer and owner email settings must be valid private addresses"
            }
            require(!cloudDemoCustomerEmail.equals(cloudDemoOwnerEmail, ignoreCase = true)) {
                "Cloud demo customer and owner must be different accounts"
            }
            require(cloudDemoCustomerPassword != cloudDemoOwnerPassword) {
                "Cloud demo customer and owner passwords must be different"
            }
        }
    }

    companion object {
        internal val RESERVED_LOCAL_DEMO_EMAILS = setOf(
            "student.arya@demo.ritcanteen.test", "student.kiran@demo.ritcanteen.test",
            "faculty.maya@demo.ritcanteen.test", "owner.main@demo.ritcanteen.test",
            "owner.secondary@demo.ritcanteen.test", "manager.main@demo.ritcanteen.test",
            "staff.main@demo.ritcanteen.test", "admin.platform@demo.ritcanteen.test",
        )

        fun fromEnvironment(env: Map<String, String> = System.getenv()): BackendConfig {
            val mode = when (env["RIT_ENV"]?.lowercase() ?: "local") {
                "local" -> RuntimeEnvironment.LOCAL
                "test" -> RuntimeEnvironment.TEST
                "cloud_demo" -> RuntimeEnvironment.CLOUD_DEMO
                "production" -> RuntimeEnvironment.PRODUCTION
                else -> error("RIT_ENV must be local, test, cloud_demo, or production")
            }
            val seed = when (env["DEMO_SEED_ENABLED"]?.lowercase() ?: "false") {
                "true" -> true
                "false" -> false
                else -> error("DEMO_SEED_ENABLED must be true or false")
            }
            return BackendConfig(
                environment = mode,
                httpHost = env["HTTP_HOST"] ?: if (mode == RuntimeEnvironment.CLOUD_DEMO) "0.0.0.0" else "127.0.0.1",
                httpPort = (env["PORT"] ?: env["HTTP_PORT"] ?: "8080").toIntOrNull()
                    ?: error("PORT or HTTP_PORT must be an integer"),
                databaseUrl = env["DATABASE_URL"] ?: error("DATABASE_URL is required"),
                databaseUser = env["DATABASE_USER"] ?: error("DATABASE_USER is required"),
                databasePassword = env["DATABASE_PASSWORD"] ?: error("DATABASE_PASSWORD is required"),
                demoSeedEnabled = seed,
                cloudDemoSeedEnabled = parseBoolean(env["RIT_CLOUD_DEMO_SEED_ENABLED"] ?: "false", "RIT_CLOUD_DEMO_SEED_ENABLED"),
                cloudDemoCustomerEmail = env["RIT_CLOUD_DEMO_CUSTOMER_EMAIL"],
                cloudDemoCustomerPassword = env["RIT_CLOUD_DEMO_CUSTOMER_PASSWORD"],
                cloudDemoOwnerEmail = env["RIT_CLOUD_DEMO_OWNER_EMAIL"],
                cloudDemoOwnerPassword = env["RIT_CLOUD_DEMO_OWNER_PASSWORD"],
                databasePoolSize = (env["DB_POOL_SIZE"] ?: "2").toIntOrNull()
                    ?: error("DB_POOL_SIZE must be an integer"),
            )
        }

        private fun parseBoolean(value: String, name: String): Boolean = when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> error("$name must be true or false")
        }

        private fun String?.isValidConfiguredEmail(): Boolean = this != null &&
            matches(Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,63}$")) &&
            this !in RESERVED_LOCAL_DEMO_EMAILS

        private fun requireStrongCloudPassword(password: String?, name: String) {
            require(password != null && password.length in 16..128 &&
                password.any(Char::isLowerCase) && password.any(Char::isUpperCase) &&
                password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }) {
                "$name must be a unique 16–128 character password with upper/lowercase, a digit, and a symbol"
            }
            require(!password.startsWith("RitDemo!")) {
                "$name must not reuse a public local fixture password"
            }
        }
    }
}
