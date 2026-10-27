package edu.msrit.canteen

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.customer.CustomerProfile
import edu.msrit.canteen.customer.DevelopmentCustomerAccount
import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.navigation.DevelopmentRole
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.ui.components.foodPhotoResource
import edu.msrit.canteen.ui.components.sellerPhotoResource
import java.util.UUID
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoAccountsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var registeredEmail: String? = null
    private var registeredId: String? = null

    @Before fun setup() {
        DevelopmentDemoAccounts.initialize(context)
        DevelopmentCustomerAccount.initialize(context)
        DevelopmentPreviewAccess.reset()
    }

    @After fun cleanup() {
        DevelopmentPreviewAccess.reset()
        DevelopmentCustomerAccount.deactivate()
        registeredEmail?.let { email ->
            val prefs = context.getSharedPreferences("rit_debug_demo_accounts_v1", Context.MODE_PRIVATE)
            val existing = JSONArray(prefs.getString("registered", "[]"))
            val kept = JSONArray()
            for (i in 0 until existing.length()) {
                val account = existing.getJSONObject(i)
                if (account.optString("email") != email) kept.put(account)
            }
            prefs.edit().putString("registered", kept.toString()).apply()
        }
        registeredId?.let { id -> context.getSharedPreferences("rit_debug_customer_accounts_v1", Context.MODE_PRIVATE)
            .edit().remove(id).apply() }
    }

    @Test fun allEightSeededAccountsHaveDistinctAuthorizedIdentities() {
        val credentials = listOf(
            Triple("student.arya@demo.ritcanteen.test", "RitDemo!Student01", DevelopmentRole.Customer),
            Triple("student.kiran@demo.ritcanteen.test", "RitDemo!Student02", DevelopmentRole.Customer),
            Triple("faculty.maya@demo.ritcanteen.test", "RitDemo!Faculty01", DevelopmentRole.Customer),
            Triple("owner.main@demo.ritcanteen.test", "RitDemo!OwnerMain01", DevelopmentRole.SellerOwner),
            Triple("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01", DevelopmentRole.SellerOwner),
            Triple("manager.main@demo.ritcanteen.test", "RitDemo!Manager01", DevelopmentRole.SellerManager),
            Triple("staff.main@demo.ritcanteen.test", "RitDemo!Staff01", DevelopmentRole.SellerStaff),
            Triple("admin.platform@demo.ritcanteen.test", "RitDemo!Admin01", DevelopmentRole.PlatformAdministrator),
        )
        val ids = mutableSetOf<String>()
        credentials.forEach { (email, password, role) ->
            val account = DevelopmentDemoAccounts.signIn(email, password).account
            assertNotNull(email, account)
            assertEquals(role, account!!.role)
            assertTrue(ids.add(account.id))
            assertEquals(account.id, DevelopmentPreviewAccess.activeIdentity?.authorization?.userId)
            if (role == DevelopmentRole.Customer) assertEquals(
                if (email.startsWith("faculty")) CustomerAffiliation.FACULTY else CustomerAffiliation.STUDENT,
                account.affiliation)
            if (role == DevelopmentRole.SellerOwner) assertEquals(
                if (email.startsWith("owner.main")) "main-canteen" else "north-counter", account.sellerId)
            DevelopmentDemoAccounts.signOut()
            assertNull(DevelopmentPreviewAccess.activeIdentity)
        }
        assertEquals(8, ids.size)
    }

    @Test fun invalidCredentialsNeverAuthenticate() {
        assertNull(DevelopmentDemoAccounts.signIn("student.arya@demo.ritcanteen.test", "wrong").account)
        assertNull(DevelopmentDemoAccounts.signIn("nobody@example.com", "RitDemo!Student01").account)
        assertNull(DevelopmentPreviewAccess.activeIdentity)
    }

    @Test fun signupPersistsAcrossInitializationAndKeepsCustomerProfilesSeparate() {
        val email = "device-${UUID.randomUUID()}@example.test"
        registeredEmail = email
        assertNull(DevelopmentDemoAccounts.register("Asha Kumar", "bad-email", "Password1234", "Password1234",
            CustomerAffiliation.STUDENT, "CSE").account)
        val created = DevelopmentDemoAccounts.register("Asha Kumar", email, "Password1234", "Password1234",
            CustomerAffiliation.STUDENT, "CSE").account!!
        registeredId = created.id
        assertNull(DevelopmentDemoAccounts.register("Another User", email, "Password1234", "Password1234",
            CustomerAffiliation.FACULTY, "ECE").account)
        DevelopmentCustomerAccount.activate(created.id, CustomerProfile(created.name, created.email,
            CustomerAffiliation.STUDENT, created.department))
        assertTrue(DevelopmentCustomerAccount.updateProfile(CustomerProfile("Asha Edited", email,
            CustomerAffiliation.STUDENT, "CSE"), created.id))
        DevelopmentCustomerAccount.deactivate()
        DevelopmentDemoAccounts.signOut()
        DevelopmentDemoAccounts.initialize(context) // same persistence path as a normal process restart
        assertNull(DevelopmentDemoAccounts.activeAccount)
        assertEquals(created.id, DevelopmentDemoAccounts.signIn(email, "Password1234").account?.id)
        DevelopmentCustomerAccount.activate(created.id, CustomerProfile(created.name, created.email,
            CustomerAffiliation.STUDENT, created.department))
        assertEquals("Asha Edited", DevelopmentCustomerAccount.profile?.fullName)
        DevelopmentCustomerAccount.deactivate()
        val other = DevelopmentDemoAccounts.signIn("student.kiran@demo.ritcanteen.test", "RitDemo!Student02").account!!
        DevelopmentCustomerAccount.activate(other.id, CustomerProfile(other.name, other.email,
            CustomerAffiliation.STUDENT))
        assertEquals("Kiran Shah", DevelopmentCustomerAccount.profile?.fullName)
        assertFalse(DevelopmentCustomerAccount.updateProfile(CustomerProfile("Leaked Name", email,
            CustomerAffiliation.STUDENT), created.id))
    }

    @Test fun sellerPrivilegesAreScopedToTheSignedInSeller() {
        DevelopmentDemoAccounts.signIn("manager.main@demo.ritcanteen.test", "RitDemo!Manager01")
        val manager = DevelopmentPreviewAccess.activeIdentity!!.authorization
        assertTrue(manager.modes.contains(AppMode.SellerManager("main-canteen")))
        assertFalse(manager.capabilitiesBySeller.containsKey("north-counter"))
        DevelopmentDemoAccounts.signOut()
        DevelopmentDemoAccounts.signIn("staff.main@demo.ritcanteen.test", "RitDemo!Staff01")
        val staff = DevelopmentPreviewAccess.activeIdentity!!.authorization
        assertTrue(staff.modes.contains(AppMode.OperationalStaff("main-canteen")))
        assertFalse(staff.capabilitiesBySeller["main-canteen"].orEmpty().contains("reports.export"))
    }

    @Test fun everyCurrentCatalogImageResourceDecodesOnDevice() {
        assertEquals(4, PreviewCatalog.sellers.size)
        assertEquals(12, PreviewCatalog.items.size)
        val ids = PreviewCatalog.sellers.map { requireNotNull(sellerPhotoResource(it.photo)) } +
            PreviewCatalog.items.map { requireNotNull(foodPhotoResource(it.photo)) }
        assertEquals(16, ids.distinct().size)
        ids.forEach { id ->
            val image = BitmapFactory.decodeResource(context.resources, id)
            assertNotNull("Drawable $id must decode", image)
            assertTrue(image!!.width > 0 && image.height > 0)
            image.recycle()
        }
        assertNull(foodPhotoResource(null))
        assertNull(sellerPhotoResource(null))
    }
}
