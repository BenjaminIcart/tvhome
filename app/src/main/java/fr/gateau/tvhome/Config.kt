package fr.gateau.tvhome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Tile(val entity: String, val name: String)

data class Config(
    val haUrl: String,
    val haToken: String,
    val weatherEntity: String,
    val liveTvPackage: String,
    val favorites: List<String>,
    val tiles: List<Tile>,
    val serverPort: Int,
    val serverKey: String,
    val saverMinutes: Int,
)

object ConfigStore {
    private val DEFAULT_FAVS = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "fr.tf1.mytf1",
        "fr.m6.m6replay.by",
        "org.videolan.vlc",
    )

    fun file(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "config.json")

    fun save(ctx: Context, text: String) {
        JSONObject(text) // valide le JSON avant d'écrire
        file(ctx).writeText(text)
    }

    private fun strings(a: JSONArray?): List<String>? =
        a?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }

    fun load(ctx: Context): Config {
        val f = file(ctx)
        val j = try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        val tiles = j.optJSONArray("tiles")?.let { arr ->
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val e = o.getString("entity")
                Tile(e, o.optString("name", e))
            }
        } ?: emptyList()
        return Config(
            haUrl = j.optString("ha_url", ""),
            haToken = j.optString("ha_token", ""),
            weatherEntity = j.optString("weather_entity", ""),
            liveTvPackage = j.optString("live_tv_package", "fr.bouyguestelecom.tv.bbui"),
            favorites = strings(j.optJSONArray("favorites")) ?: DEFAULT_FAVS,
            tiles = tiles,
            serverPort = j.optInt("server_port", 8765),
            serverKey = j.optString("server_key", ""),
            saverMinutes = j.optInt("screensaver_minutes", 5),
        )
    }
}
