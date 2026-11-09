package edu.msrit.canteen.backend.features.management

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MenuImageStorageTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output)
        output.toByteArray()
    }

    @Test fun validPngIsResizedAndStoredAsSmallJpeg() {
        val normalized = normalizeMenuImage(png(2400, 1200), "image/png")
        assertEquals(1600, normalized.width)
        assertEquals(800, normalized.height)
        assertTrue(normalized.bytes.size < 2 * 1024 * 1024)
        val decoded = ImageIO.read(normalized.bytes.inputStream())
        assertEquals(1600, decoded.width)
        assertEquals(800, decoded.height)
    }

    @Test fun rejectsTypeMismatchAndOversizedInput() {
        val png = png(8, 8)
        assertFailsWith<InvalidMenuImage> { normalizeMenuImage(png, "image/jpeg") }
        assertFailsWith<InvalidMenuImage> {
            normalizeMenuImage(ByteArray(MAX_MENU_IMAGE_UPLOAD_BYTES + 1), "image/png")
        }
    }

    @Test fun missingStorageConfigurationFailsClosed() {
        assertFailsWith<MenuImageStorageUnavailable> {
            MenuImageStorage(emptyMap()).upload("main-canteen", NormalizedMenuImage(byteArrayOf(1), 1, 1))
        }
    }
}
