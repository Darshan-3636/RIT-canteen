package edu.msrit.canteen.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import edu.msrit.canteen.BuildConfig

/** Explicit local transport or HTTPS cloud-demo transport, injected at build time. Absent from release. */
internal object DebugBackendApi {
    private val base: String = validateBaseUrl(BuildConfig.RIT_API_BASE_URL, BuildConfig.RIT_CLOUD_DEMO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(if (BuildConfig.RIT_CLOUD_DEMO) 20 else 5, TimeUnit.SECONDS)
        .readTimeout(if (BuildConfig.RIT_CLOUD_DEMO) 165 else 8, TimeUnit.SECONDS)
        .callTimeout(if (BuildConfig.RIT_CLOUD_DEMO) 180 else 12, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Response(val status: Int, val body: JSONObject?) {
        val error: String? get() = body?.optJSONObject("error")?.optString("message")
    }

    fun request(method: String, path: String, body: JSONObject? = null, token: String? = null,
                idempotencyKey: String? = null): Response {
        require(path.startsWith("/") && !path.startsWith("//"))
        val request = Request.Builder().url(base + path).header("Accept", "application/json").apply {
            if (token != null) header("Authorization", "Bearer $token")
            if (idempotencyKey != null) header("Idempotency-Key", idempotencyKey)
            method(method, body?.toString()?.toRequestBody(jsonType)
                ?: if (method == "POST" || method == "PUT" || method == "PATCH")
                    ByteArray(0).toRequestBody(null) else null)
        }.build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401 && token != null) DevelopmentDemoAccounts.invalidateIfCurrent(token)
            return Response(response.code, raw.takeIf { it.isNotBlank() }?.let(::JSONObject))
        }
    }

    private fun validateBaseUrl(value: String, cloud: Boolean): String {
        val uri = runCatching { java.net.URI(value) }.getOrElse { error("Invalid RIT API base URL") }
        if (cloud) require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty()) {
            "Cloud demo requires a valid HTTPS origin"
        }
        else require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == 8080) {
            "Local debug mode may connect only to 127.0.0.1:8080"
        }
        return value.trimEnd('/')
    }
}

/** Android Keystore encrypted bearer; no plaintext token in preferences or logs. */
internal class DebugBearerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rit_backend_session_v1", Context.MODE_PRIVATE)
    private val alias = "rit_local_demo_bearer_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val value = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("bearer", Base64.encodeToString(value, Base64.NO_WRAP)).apply()
    }

    fun read(): String? = runCatching {
        val encoded = prefs.getString("bearer", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun clear() { prefs.edit().remove("bearer").apply() }
}
