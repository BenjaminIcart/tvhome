package fr.gateau.tvhome

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView

/** Icônes Material Design (les mêmes que Home Assistant, ex. "mdi:lightbulb"). */
object Icons {
    private var tf: Typeface? = null
    private var map: Map<String, Int> = emptyMap()

    fun init(c: Context) {
        if (tf != null) return
        try {
            tf = Typeface.createFromAsset(c.assets, "fonts/mdi.ttf")
            map = c.assets.open("mdi.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { l ->
                    val i = l.indexOf(' ')
                    if (i <= 0) null else l.substring(0, i) to l.substring(i + 1).trim().toInt(16)
                }.toMap()
            }
        } catch (e: Exception) { /* sans icônes */ }
    }

    fun glyph(name: String): String {
        val cp = map[name.removePrefix("mdi:")] ?: map["help-circle-outline"] ?: return ""
        return String(Character.toChars(cp))
    }

    fun view(c: Context, name: String, sp: Float, color: Int): TextView = TextView(c).apply {
        typeface = tf ?: Typeface.DEFAULT
        text = glyph(name)
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    fun set(v: TextView, name: String) { v.text = glyph(name) }

    fun weather(cond: String): String = when (cond) {
        "sunny" -> "weather-sunny"
        "clear-night" -> "weather-night"
        "partlycloudy" -> "weather-partly-cloudy"
        "cloudy" -> "weather-cloudy"
        "rainy" -> "weather-rainy"
        "pouring" -> "weather-pouring"
        "lightning" -> "weather-lightning"
        "lightning-rainy" -> "weather-lightning-rainy"
        "snowy" -> "weather-snowy"
        "snowy-rainy" -> "weather-snowy-rainy"
        "fog" -> "weather-fog"
        "windy", "windy-variant" -> "weather-windy"
        "hail" -> "weather-hail"
        else -> "weather-cloudy"
    }

    /** Icône par défaut d'une entité quand HA n'en fournit pas. */
    fun forEntity(entity: String, state: String = ""): String {
        val d = entity.substringBefore('.')
        return when {
            d == "light" -> if (state == "on") "lightbulb-on" else "lightbulb-outline"
            d == "switch" -> "power-plug"
            d == "cover" -> if (state == "open" || state == "opening") "garage-open-variant" else "garage-variant"
            d == "vacuum" -> "robot-vacuum"
            d == "input_boolean" -> "toggle-switch-outline"
            d == "camera" || d == "image" -> "doorbell-video"
            entity.contains("energ") -> "lightning-bolt-circle"
            entity.contains("puissance") || entity.contains("power") -> "flash"
            entity.contains("temp") -> "thermometer"
            entity.contains("humid") -> "water-percent"
            else -> "chart-line"
        }
    }
}
