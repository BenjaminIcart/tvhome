package fr.gateau.tvhome

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HaClient(base: String, private val token: String) {
    private val base = base.trimEnd('/')

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        private val READ_ONLY = setOf(
            "sensor", "binary_sensor", "weather", "person", "device_tracker", "sun", "zone", "image", "camera"
        )

        fun isActionable(entity: String) = entity.substringBefore('.') !in READ_ONLY && !entity.startsWith("camera.")
        fun httpClient(): OkHttpClient = http
    }

    fun baseUrl() = base
    fun authToken() = token

    fun state(entity: String): JSONObject? = try {
        val req = Request.Builder()
            .url("$base/api/states/$entity")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else JSONObject(r.body?.string() ?: "{}")
        }
    } catch (e: Exception) {
        null
    }

    fun toggle(entity: String): Boolean {
        val domain = entity.substringBefore('.')
        val service = when (domain) {
            "light", "switch", "fan", "input_boolean", "automation", "media_player", "cover" -> "$domain/toggle"
            "script", "scene" -> "$domain/turn_on"
            "button", "input_button" -> "$domain/press"
            "vacuum" -> "vacuum/start"
            else -> "homeassistant/toggle"
        }
        return try {
            val body = JSONObject().put("entity_id", entity).toString().toRequestBody(JSON_TYPE)
            val req = Request.Builder()
                .url("$base/api/services/$service")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}

/** Instantané d'une caméra HA (camera_proxy). */
fun HaClient.snapshot(entity: String): android.graphics.Bitmap? = try {
    val req = okhttp3.Request.Builder()
        .url("${baseUrl()}/api/${if (entity.startsWith("image.")) "image_proxy" else "camera_proxy"}/$entity")
        .header("Authorization", "Bearer ${authToken()}")
        .build()
    HaClient.httpClient().newCall(req).execute().use { r ->
        if (!r.isSuccessful) null
        else r.body?.byteStream()?.let { android.graphics.BitmapFactory.decodeStream(it) }
    }
} catch (e: Exception) {
    null
}
