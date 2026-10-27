package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.DebugCommerceApi
import edu.msrit.canteen.customer.IndividualDraft
import edu.msrit.canteen.customer.IndividualLine
import edu.msrit.canteen.customer.BulkDraft
import edu.msrit.canteen.customer.BulkLine
import edu.msrit.canteen.customer.BulkProposal
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.navigation.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test

/** Requires a live local Ktor/PostgreSQL server and adb reverse tcp:8080 tcp:8080. */
class FullstackLocalDemoDeviceTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val launchRules = RuleChain.outerRule(ResetDemoBeforeLaunchRule()).around(composeRule)
    private val rule get() = composeRule

    private fun welcome() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText("Skip").performClick()
    }

    @Test fun customerLoginUsesServerIdentityAndSeventyTwoItemCatalog() {
        welcome()
        rule.onNodeWithTag("welcome_login").performScrollTo().performClick()
        rule.onNodeWithTag("demo_login_email").performTextInput("student.arya@demo.ritcanteen.test")
        rule.onNodeWithTag("demo_login_password").performTextInput("RitDemo!Student01")
        rule.onNodeWithTag("demo_login_submit").performScrollTo().performClick()
        rule.waitUntil(25_000) {
            rule.onAllNodesWithTag("choose_seller").fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnUiThread {
            assertEquals(72, PreviewCatalog.items.size)
            assertEquals(4, PreviewCatalog.sellers.size)
            assertTrue(AppMode.Customer in DevelopmentDemoAccounts.serverIdentity!!.modes)
            assertTrue(DevelopmentDemoAccounts.activeAccount!!.id != "hint-arya")
        }
        // The same authenticated Android repository must round-trip a durable server order.
        val created = runBlocking(Dispatchers.IO) {
            DebugCommerceApi.submitIndividual(IndividualDraft(listOf(IndividualLine("masala-dosa", 1))))
        }
        assertTrue(created.error ?: "submit succeeded", created.value != null)
        val saved = runBlocking(Dispatchers.IO) { DebugCommerceApi.individualOrders() }
        assertTrue(saved.error ?: "history loaded", saved.value?.any { it.id == created.value!!.id } == true)
    }

    @Test fun customerAndSellerQuoteRoundTripUsesServerVersions() {
        val pickup = LocalDateTime.now(RIT_TIME_ZONE).plusDays(3).withHour(12).withMinute(0)
        runBlocking(Dispatchers.IO) {
            val customer = DevelopmentDemoAccounts.signIn("student.kiran@demo.ritcanteen.test", "RitDemo!Student02")
            assertTrue(customer.error ?: "customer login", customer.account != null)
            val submitted = DebugCommerceApi.submitBulk(BulkDraft("north-counter",
                listOf(BulkLine("north-lunch", 8)), pickup), UUID.randomUUID().toString())
            assertTrue(submitted.error ?: "bulk submitted", submitted.value != null)
            val id = submitted.value!!.reference
            assertTrue(DebugCommerceApi.bulkRequests().value?.any { it.reference == id } == true)

            DevelopmentDemoAccounts.signOut()
            val owner = DevelopmentDemoAccounts.signIn("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01")
            assertTrue(owner.error ?: "seller login", owner.account != null)
            val sellerRecord = DebugCommerceApi.bulkRequests("north-counter").value!!.first { it.reference == id }
            val proposed = BulkProposal.from(sellerRecord.latest).copy(lines = listOf(
                QuoteLineSnapshot("north-snack", "North Snack", 8, 6_500)))
            val counter = DebugCommerceApi.sellerCounteroffer("north-counter", id, sellerRecord.latest.version, proposed)
            assertTrue(counter.error ?: "counteroffer", counter.value?.latest?.version == 2)

            DevelopmentDemoAccounts.signOut()
            assertTrue(DevelopmentDemoAccounts.signIn("student.kiran@demo.ritcanteen.test", "RitDemo!Student02").account != null)
            val refreshed = DebugCommerceApi.bulkRequests().value!!.first { it.reference == id }
            assertEquals(2, refreshed.latest.version)
            val approved = DebugCommerceApi.customerApprove(id, 2)
            assertTrue(approved.error ?: "customer approval", approved.value != null)

            DevelopmentDemoAccounts.signOut()
            assertTrue(DevelopmentDemoAccounts.signIn("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01").account != null)
            val agreed = DebugCommerceApi.sellerApprove("north-counter", id, 2)
            assertEquals(QuoteStage.AGREED_AWAITING_PAYMENT, agreed.value?.thread?.stage)
            DevelopmentDemoAccounts.signOut()
        }
    }
}
