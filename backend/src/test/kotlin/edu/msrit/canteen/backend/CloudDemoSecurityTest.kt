package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import javax.sql.DataSource
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudDemoSecurityTest {
    @Test fun publicRegistrationIsRejectedBeforeTouchingTheDatabase() = testApplication {
        val config = BackendConfig(
            environment = RuntimeEnvironment.CLOUD_DEMO,
            httpHost = "0.0.0.0",
            httpPort = 10000,
            databaseUrl = "jdbc:postgresql://pool.example:5432/postgres?sslmode=require",
            databaseUser = "pool-user",
            databasePassword = "test-only-dummy-database-password",
            demoSeedEnabled = false,
            cloudDemoSeedEnabled = true,
            cloudDemoCustomerEmail = "demo.customer@example.test",
            cloudDemoCustomerPassword = "Unique-Customer-Password-2048!",
            cloudDemoOwnerEmail = "demo.owner@example.test",
            cloudDemoOwnerPassword = "Unique-Owner-Password-2048!",
        )
        val dataSource = Proxy.newProxyInstance(DataSource::class.java.classLoader,
            arrayOf(DataSource::class.java)) { _, _, _ -> error("Registration policy must not access the database") } as DataSource
        val context = BackendContext(dataSource, config)
        application { backendModule(context) }

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"fullName":"Demo Person","email":"person@example.test","password":"AValidPassword123","affiliation":"STUDENT"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("REGISTRATION_DISABLED"))
    }
}
