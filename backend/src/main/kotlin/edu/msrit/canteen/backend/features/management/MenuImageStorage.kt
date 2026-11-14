package edu.msrit.canteen.backend.features.management

import java.awt.RenderingHints
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

internal const val MAX_MENU_IMAGE_UPLOAD_BYTES = 5 * 1024 * 1024
private const val MAX_NORMALIZED_IMAGE_BYTES = 2 * 1024 * 1024
private const val MAX_IMAGE_EDGE = 1600
private const val MAX_IMAGE_PIXELS = 24_000_000L

internal data class NormalizedMenuImage(val bytes: ByteArray, val width: Int, val height: Int)

internal fun normalizeMenuImage(bytes: ByteArray, declaredMimeType: String?): NormalizedMenuImage {
    if (bytes.isEmpty() || bytes.size > MAX_MENU_IMAGE_UPLOAD_BYTES)
        throw InvalidMenuImage("Choose an image up to 5 MB.")
    val mime = declaredMimeType?.substringBefore(';')?.trim()?.lowercase()
    if (mime !in setOf("image/jpeg", "image/png"))
        throw InvalidMenuImage("Choose a JPEG or PNG image.")

    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        ?: throw InvalidMenuImage("The selected file is not a valid image.")
    val image: BufferedImage = input.use { stream ->
        val reader: ImageReader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
            ?: throw InvalidMenuImage("The selected file is not a valid JPEG or PNG image.")
        try {
            reader.input = stream
            val format = reader.formatName.lowercase()
            if ((mime == "image/jpeg" && format !in setOf("jpeg", "jpg")) ||
                (mime == "image/png" && format != "png"))
                throw InvalidMenuImage("Image type does not match its file contents.")
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_IMAGE_PIXELS)
                throw InvalidMenuImage("Image dimensions must be at most 24 megapixels.")
            reader.read(0) ?: throw InvalidMenuImage("Could not decode the selected image.")
        } finally { reader.dispose() }
    }

    val scale = minOf(1.0, MAX_IMAGE_EDGE.toDouble() / maxOf(image.width, image.height))
    val width = (image.width * scale).toInt().coerceAtLeast(1)
    val height = (image.height * scale).toInt().coerceAtLeast(1)
    val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = resized.createGraphics()
    try {
        // JPEG has no alpha channel; preserve transparent PNGs on a predictable white background.
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(image, 0, 0, width, height, null)
    } finally { graphics.dispose() }
    val output = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
        ?: error("JPEG encoder is unavailable")
    try {
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = 0.84f
            }
            writer.write(null, javax.imageio.IIOImage(resized, null, null), params)
        }
    } finally { writer.dispose() }
    val encoded = output.toByteArray()
    if (encoded.size > MAX_NORMALIZED_IMAGE_BYTES)
        throw InvalidMenuImage("Compressed image is still over 2 MB. Choose a smaller photo.")
    return NormalizedMenuImage(encoded, width, height)
}

internal class InvalidMenuImage(message: String) : IllegalArgumentException(message)

/** Supabase secret key stays on the server; all object paths are seller-scoped and private. */
internal class MenuImageStorage(private val env: Map<String, String> = System.getenv()) {
    data class StoredImage(val key: String, val width: Int, val height: Int)

    private data class Config(val baseUrl: String, val secretKey: String, val bucket: String)

    fun upload(sellerId: String, normalized: NormalizedMenuImage): StoredImage {
        val config = config() ?: throw MenuImageStorageUnavailable()
        require(sellerId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val key = "custom-${UUID.randomUUID()}"
        val path = "$sellerId/$key.jpg"
        val connection = open(config, path, "POST")
        try {
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("Cache-Control", "3600")
            connection.setFixedLengthStreamingMode(normalized.bytes.size)
            connection.outputStream.use { it.write(normalized.bytes) }
            if (connection.responseCode !in 200..299) throw MenuImageStorageUnavailable()
            return StoredImage(key, normalized.width, normalized.height)
        } finally { connection.disconnect() }
    }

    fun download(sellerId: String, key: String): ByteArray {
        val config = config() ?: throw MenuImageStorageUnavailable()
        require(sellerId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(key.matches(Regex("custom-[0-9a-fA-F-]{36}")))
        val connection = open(config, "$sellerId/$key.jpg", "GET")
        try {
            if (connection.responseCode != 200) throw MenuImageStorageUnavailable()
            val bytes = connection.inputStream.use { it.readNBytes(MAX_NORMALIZED_IMAGE_BYTES + 1) }
            if (bytes.isEmpty() || bytes.size > MAX_NORMALIZED_IMAGE_BYTES) throw MenuImageStorageUnavailable()
            return bytes
        } finally { connection.disconnect() }
    }

    private fun open(config: Config, objectPath: String, method: String): HttpURLConnection {
        val encodedBucket = encode(config.bucket)
        val encodedPath = objectPath.split('/').joinToString("/") { encode(it) }
        val url = URI.create("${config.baseUrl}/storage/v1/object/$encodedBucket/$encodedPath").toURL()
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("apikey", config.secretKey)
            setRequestProperty("Authorization", "Bearer ${config.secretKey}")
            if (method == "POST") doOutput = true
        }
    }

    private fun config(): Config? {
        val base = env["SUPABASE_URL"]?.trim()?.trimEnd('/') ?: return null
        val key = env["SUPABASE_SERVICE_ROLE_KEY"]?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val bucket = env["SUPABASE_MENU_IMAGE_BUCKET"]?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!bucket.matches(Regex("[a-z0-9][a-z0-9_-]{0,62}"))) return null
        val uri = runCatching { URI.create(base) }.getOrNull() ?: return null
        val localHttp = uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost")
        if (uri.userInfo != null || uri.query != null || uri.fragment != null ||
            !(uri.scheme == "https" || localHttp) || uri.host.isNullOrBlank()) return null
        return Config(base, key, bucket)
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

internal class MenuImageStorageUnavailable : RuntimeException("Menu image storage is unavailable. Configure the backend storage settings and retry.")
