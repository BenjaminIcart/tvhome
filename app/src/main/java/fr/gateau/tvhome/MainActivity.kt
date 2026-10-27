package fr.gateau.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var cfg: Config
    private var ha: HaClient? = null
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var tilesTitle: TextView
    private lateinit var tilesBox: android.view.View
    private lateinit var tilesRow: LinearLayout

    private class TileView(val card: FrameLayout, val state: TextView)
    private val tileViews = mutableMapOf<String, TileView>()

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = clockFmt.format(now)
            date.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            ui.postDelayed(this, 1000)
        }
    }
    private val haTick = object : Runnable {
        override fun run() {
            refreshHa()
            ui.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        startBridge()
    }

    override fun onResume() {
        super.onResume()
        cfg = ConfigStore.load(this)
        ha = if (cfg.haUrl.isNotBlank() && cfg.haToken.isNotBlank()) HaClient(cfg.haUrl, cfg.haToken) else null
        populate()
        ui.post(clockTick)
        ui.post(haTick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(clockTick)
        ui.removeCallbacks(haTick)
    }

    @Deprecated("Launcher : Retour ne quitte pas l'accueil")
    override fun onBackPressed() { /* rien */ }

    private fun startBridge() {
        val i = Intent(this, BridgeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) { /* ignoré */ }
    }

    private fun buildLayout() {
        val c = this
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 56), Ui.dp(c, 32), Ui.dp(c, 56), Ui.dp(c, 32))
            clipChildren = false
        }

        // En-tête : heure + date à gauche, météo à droite
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        clock = Ui.text(c, "--:--", 64f, bold = true)
        date = Ui.text(c, "", 20f, Ui.MUTED)
        left.addView(clock)
        left.addView(date)
        header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        weather = Ui.text(c, "", 28f).apply { gravity = Gravity.END }
        header.addView(weather)
        content.addView(header)

        content.addView(Ui.section(c, "Favoris"))
        val (favBox, fr) = Ui.hrow(c)
        favRow = fr
        content.addView(favBox)

        tilesTitle = Ui.section(c, "Maison")
        content.addView(tilesTitle)
        val (tb, tr) = Ui.hrow(c)
        tilesBox = tb
        tilesRow = tr
        content.addView(tilesBox)

        val scroll = ScrollView(c).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            addView(content)
        }
        val root = FrameLayout(c).apply {
            background = Ui.background()
            addView(scroll)
        }
        setContentView(root)
    }

    private fun populate() {
        val c = this
        favRow.removeAllViews()
        favRow.addView(Ui.labelCard(c, "📺  TV en direct", 300, 170, Ui.ACCENT) {
            if (!Ui.launch(c, cfg.liveTvPackage)) toast("Impossible d'ouvrir ${cfg.liveTvPackage}")
        })
        for (pkg in cfg.favorites) {
            Ui.appCard(c, pkg)?.let { favRow.addView(it) }
        }
        favRow.addView(Ui.labelCard(c, "⋯  Toutes les applis", 300, 170, Ui.CARD) {
            startActivity(Intent(c, AppsActivity::class.java))
        })

        tilesRow.removeAllViews()
        tileViews.clear()
        val show = cfg.tiles.isNotEmpty()
        tilesTitle.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        tilesBox.visibility = tilesTitle.visibility
        for (t in cfg.tiles) {
            val state = Ui.text(c, "…", 26f, bold = true)
            val card = Ui.card(c, 230, 130) { onTile(t) }
            val col = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0)
                addView(Ui.text(c, t.name, 17f, Ui.MUTED))
                addView(state)
            }
            card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tilesRow.addView(card)
            tileViews[t.entity] = TileView(card, state)
        }

        if (favRow.childCount > 0 && currentFocus == null) favRow.getChildAt(0).requestFocus()
    }

    private fun onTile(t: Tile) {
        val h = ha ?: return toast("Home Assistant non configuré")
        if (!HaClient.isActionable(t.entity)) return
        thread {
            val ok = h.toggle(t.entity)
            Thread.sleep(400)
            val st = h.state(t.entity)
            runOnUiThread {
                if (!ok) toast("Échec : ${t.entity}")
                st?.let { applyTile(t.entity, it) }
            }
        }
    }

    private fun refreshHa() {
        val h = ha
        if (h == null) {
            weather.text = if (cfg.haUrl.isBlank()) "" else "HA : token manquant"
            return
        }
        val weatherEntity = cfg.weatherEntity
        val entities = cfg.tiles.map { it.entity }
        thread {
            val w = if (weatherEntity.isNotBlank()) h.state(weatherEntity) else null
            val states = entities.associateWith { h.state(it) }
            runOnUiThread {
                if (weatherEntity.isNotBlank()) weather.text = w?.let { formatWeather(it) } ?: "Météo indispo"
                states.forEach { (e, s) ->
                    if (s != null) applyTile(e, s) else tileViews[e]?.state?.text = "Hors ligne"
                }
            }
        }
    }

    private fun applyTile(entity: String, js: JSONObject) {
        val tv = tileViews[entity] ?: return
        val st = js.optString("state")
        val unit = js.optJSONObject("attributes")?.optString("unit_of_measurement", "") ?: ""
        val on = st == "on" || st == "open" || st == "playing"
        tv.state.text = when (st) {
            "on" -> "Allumé"
            "off" -> "Éteint"
            "open" -> "Ouvert"
            "closed" -> "Fermé"
            "unavailable" -> "Indispo"
            "unknown" -> "?"
            else -> {
                val n = st.toDoubleOrNull()
                val v = if (n != null && st.contains('.')) String.format(Locale.FRANCE, "%.1f", n) else st
                if (unit.isNotEmpty()) "$v $unit" else v
            }
        }
        (tv.card.background as? android.graphics.drawable.GradientDrawable)
            ?.setColor(if (on) Ui.CARD_ON else Ui.CARD)
    }

    private fun formatWeather(js: JSONObject): String {
        val cond = js.optString("state")
        val a = js.optJSONObject("attributes")
        val temp = a?.opt("temperature")?.toString() ?: "?"
        val unit = a?.optString("temperature_unit", "°C") ?: "°C"
        val (emoji, fr) = when (cond) {
            "sunny" -> "☀️" to "Ensoleillé"
            "clear-night" -> "🌙" to "Nuit claire"
            "partlycloudy" -> "⛅" to "Éclaircies"
            "cloudy" -> "☁️" to "Nuageux"
            "rainy" -> "🌧️" to "Pluie"
            "pouring" -> "🌧️" to "Forte pluie"
            "lightning", "lightning-rainy" -> "⛈️" to "Orage"
            "snowy", "snowy-rainy" -> "❄️" to "Neige"
            "fog" -> "🌫️" to "Brouillard"
            "windy", "windy-variant" -> "💨" to "Venteux"
            "hail" -> "🌨️" to "Grêle"
            else -> "" to cond
        }
        return "$emoji $temp $unit\n$fr"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
