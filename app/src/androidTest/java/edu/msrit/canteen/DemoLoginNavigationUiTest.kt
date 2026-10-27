package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import java.util.UUID
import org.json.JSONArray
import org.junit.After
import org.junit.Rule
import org.junit.Test

class DemoLoginNavigationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var createdEmail: String? = null
    private var createdId: String? = null

    @After fun cleanup() {
        rule.runOnUiThread { DevelopmentDemoAccounts.signOut() }
        createdEmail?.let { email ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("rit_debug_demo_accounts_v1", Context.MODE_PRIVATE)
            val entries = JSONArray(prefs.getString("registered", "[]"))
            val kept = JSONArray()
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                if (entry.optString("email") != email) kept.put(entry)
            }
            prefs.edit().putString("registered", kept.toString()).apply()
            createdId?.let { id -> context.getSharedPreferences("rit_debug_customer_accounts_v1", Context.MODE_PRIVATE)
                .edit().remove(id).apply() }
        }
    }

    private fun welcome() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
    }

    private fun login(email: String, password: String) {
        rule.onNodeWithTag("welcome_login").performScrollTo().performClick()
        rule.onNodeWithTag("demo_login_screen").assertExists()
        rule.onNodeWithTag("demo_login_email").performTextInput(email)
        rule.onNodeWithTag("demo_login_password").performTextInput(password)
        rule.onNodeWithTag("demo_login_submit").performScrollTo().performClick()
    }

    @Test fun studentCanLoginFromLauncherSeeProfileAndSafelyLogout() {
        welcome()
        login("student.arya@demo.ritcanteen.test", "RitDemo!Student01")
        rule.onNodeWithTag("choose_seller").assertExists()
        rule.onNodeWithTag("customer_bottom_profile").performClick()
        rule.onNodeWithTag("customer_profile").assertExists()
        rule.onNodeWithText("Arya Rao").assertExists()
        rule.onNodeWithTag("profile_account").performScrollTo().performClick()
        rule.onNodeWithTag("account_mock_signout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("choose_seller").assertDoesNotExist()
    }

    @Test fun ownerAndAdminReachCorrectDashboardsWithoutHub() {
        welcome()
        login("owner.main@demo.ritcanteen.test", "RitDemo!OwnerMain01")
        rule.onNodeWithTag("seller_daily_overview").assertExists()
        rule.onNodeWithTag("seller_daily_account").performScrollTo().performClick()
        rule.onNodeWithText("Seller: Main Canteen").assertExists()
        rule.onNodeWithTag("role_account_logout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
        login("admin.platform@demo.ritcanteen.test", "RitDemo!Admin01")
        rule.onNodeWithTag("admin_dashboard").assertExists()
        rule.onNodeWithTag("admin_my_account").performScrollTo().performClick()
        rule.onNodeWithText("Platform administrator").assertExists()
        rule.onNodeWithTag("role_account_logout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
    }

    @Test fun wrongPasswordNeverLeavesLogin() {
        welcome()
        login("student.arya@demo.ritcanteen.test", "wrong-password")
        rule.onNodeWithTag("demo_login_error").assertExists()
        rule.onNodeWithTag("choose_seller").assertDoesNotExist()
    }

    @Test fun signedInCustomerSurvivesActivityRecreation() {
        welcome()
        login("faculty.maya@demo.ritcanteen.test", "RitDemo!Faculty01")
        rule.onNodeWithTag("choose_seller").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("choose_seller").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("choose_seller").assertExists()
        rule.onNodeWithTag("customer_bottom_profile").performClick()
        rule.onNodeWithText("Dr Maya Iyer").assertExists()
        rule.onNodeWithTag("profile_account").performScrollTo().performClick()
        rule.onNodeWithTag("account_mock_signout").performScrollTo().performClick()
    }

    @Test fun newFacultySignsUpLogsOutAndSignsInAgainFromLauncher() {
        welcome()
        val email = "ui-${UUID.randomUUID()}@example.test"
        createdEmail = email
        rule.onNodeWithTag("welcome_get_started").performScrollTo().performClick()
        rule.onNodeWithTag("demo_signup_screen").assertExists()
        rule.onNodeWithTag("demo_signup_name").performTextInput("Anita Rao")
        rule.onNodeWithTag("demo_signup_email").performTextInput(email)
        rule.onNodeWithTag("demo_signup_faculty").performScrollTo().performClick()
        rule.onNodeWithTag("demo_signup_password").performScrollTo().performTextInput("DemoPassword123")
        rule.onNodeWithTag("demo_signup_confirmation").performScrollTo().performTextInput("DemoPassword123")
        rule.onNodeWithTag("demo_signup_submit").performScrollTo().performClick()
        createdId = DevelopmentDemoAccounts.activeAccount?.id
        rule.onNodeWithTag("choose_seller").assertExists()
        rule.onNodeWithTag("customer_bottom_profile").performClick()
        rule.onNodeWithText("Anita Rao").assertExists()
        rule.onNodeWithTag("profile_account").performScrollTo().performClick()
        rule.onNodeWithTag("account_mock_signout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
        login(email, "DemoPassword123")
        rule.onNodeWithTag("choose_seller").assertExists()
    }
}
