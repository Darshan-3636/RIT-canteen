package edu.msrit.canteen

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.ui.components.foodPhotoResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogAssetsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun debugPackageHasOnlyNormalRitCanteenLauncher() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
        @Suppress("DEPRECATION")
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        assertEquals(1, activities.size)
        assertEquals(MainActivity::class.java.name, activities.single().activityInfo.name)
        assertEquals("RIT Canteen", activities.single().loadLabel(context.packageManager).toString())
    }

    @Test fun allSeventyTwoSeedImagesResolveDecodeAndHaveUniqueResources() {
        val resources = context.resources
        val imageIds = PreviewCatalog.items.map { item ->
            val resId = foodPhotoResource(item.photo)
            assertNotNull("Missing image mapping for ${item.id}", resId)
            val id = requireNotNull(resId)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, id, bounds)
            assertTrue("Bad image dimensions for ${item.id}", bounds.outWidth > 0 && bounds.outHeight > 0)
            val bitmap = BitmapFactory.decodeResource(resources, id,
                BitmapFactory.Options().apply { inSampleSize = 8 })
            assertNotNull("Undecodable image for ${item.id}", bitmap)
            bitmap?.recycle()
            id
        }
        assertEquals(72, imageIds.size)
        assertEquals(72, imageIds.toSet().size)
    }
}
