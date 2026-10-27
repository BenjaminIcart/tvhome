package fr.gateau.tvhome

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var cfg: Config
    private var ha: HaClient? = null
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var greet: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherPill: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherText: TextView
    private lateinit var homeBox: View
    private lateinit var homeRow: LinearLayout
    private lateinit var focusName: TextView
    private lateinit var dockRow: LinearLayout
    private lateinit var glow: GradientDrawable
    private lateinit var wallpaper: ImageView
    private lateinit var scrim: View

    private class TileView(val card: FrameLayout, val badge: TextView, val state: TextView)
    private val tileViews = mutableMapOf<String, TileView>()
    private val camViews = mutableMapOf<String, ImageView>()
    private var lastKey: String? = null
    private var camCounter = 0
    private var wallpaperStamp = -1L
    private var pendingSaver = false
    private var armedEntity: String? = null
    private var armedAt = 0L
    private var lastWeather: Pair<String, String>? = null   // (icône, texte)

    // Écran de veille
    private var saver: FrameLayout? = null
    private lateinit var saverClock: TextView
    private lateinit var saverDate: TextView
    private lateinit var saverWeather: TextView
    private lateinit var saverWeatherIcon: TextView
    private val idle = Runnable { showSaver() }

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            val t = clockFmt.format(now)
            val d = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            clock.text = t
            date.text = d
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greet.text = when (h) { in 5..11 -> "Bonjour"; in 12..17 -> "Bon après-midi"; else -> "Bonsoir" }
            if (saver != null) { saverClock.text = t; saverDate.text = d }
            ui.postDelayed(this, 1000)
        }
    }
    private val haTick = object : Runnable {
        override fun run() {
            refreshHa(withCameras = camCounter % 2 == 0)
            camCounter++
            ui.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        buildLayout()
        startBridge()
    }

    override fun onResume() {
        super.onResume()
        cfg = ConfigStore.load(this)
        ha = if (cfg.haUrl.isNotBlank() && cfg.haToken.isNotBlank()) HaClient(cfg.haUrl, cfg.haToken) else null
        populate()
        loadWallpaper()
        camCounter = 0
        ui.post(clockTick)
        ui.post(haTick)
        resetIdle()
        if (pendingSaver || intent?.getBooleanExtra("saver", false) == true) {
            pendingSaver = false
            intent?.removeExtra("saver")
            ui.postDelayed({ showSaver() }, 300)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("saver", false)) pendingSaver = true
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(clockTick)
        ui.removeCallbacks(haTick)
        ui.removeCallbacks(idle)
        hideSaver()
    }

    @Deprecated("Launcher : Retour ne quitte pas l'accueil")
    override fun onBackPressed() {
        dockRow.getChildAt(0)?.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (saver != null) {
            if (event.action == KeyEvent.ACTION_UP) { hideSaver(); resetIdle() }
            return true
        }
        resetIdle()
        return super.dispatchKeyEvent(event)
    }

    private fun startBridge() {
        val i = Intent(this, BridgeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) { /* ignoré */ }
    }

    // ------------------------------------------------------------------ Mise en page

    private fun buildLayout() {
        val c = this
        root = FrameLayout(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0E1328"), Color.parseColor("#05060C"), Color.BLACK))
        }
        wallpaper = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        root.addView(wallpaper, matchParent())
        addAurora(Color.parseColor("#5E5CE6"), 0x50, 620, Gravity.TOP or Gravity.END, -160, -220, 70f, 40f, 38_000)
        addAurora(Color.parseColor("#30D5C8"), 0x30, 520, Gravity.TOP or Gravity.START, -200, 60, -50f, 60f, 46_000)
        addAurora(Color.parseColor("#BF5AF2"), 0x28, 480, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 90f, -30f, 52_000)
        scrim = View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(0x66, 0, 0, 0), Color.argb(0x22, 0, 0, 0), Color.argb(0xCC, 0, 0, 0)))
            visibility = View.GONE
        }
        root.addView(scrim, matchParent())

        glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = Ui.dp(c, 520).toFloat()
            setGradientCenter(0.3f, 1.0f)
            colors = intArrayOf(Color.argb(0x55, 0x0A, 0x84, 0xFF), Color.TRANSPARENT)
        }
        root.addView(View(c).apply { background = glow }, matchParent())

        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 52), Ui.dp(c, 30), Ui.dp(c, 52), Ui.dp(c, 22))
            clipChildren = false
            clipToPadding = false
        }

        // Haut : salutation / heure / date  |  météo + paramètres
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        greet = Ui.text(c, "", 26f, Color.argb(0xB3, 0xFF, 0xFF, 0xFF), Ui.REGULAR)
        clock = Ui.text(c, "--:--", 100f, Color.WHITE, Ui.LIGHT).apply {
            setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2))
            letterSpacing = -0.02f
        }
        date = Ui.text(c, "", 24f, Color.WHITE, Ui.REGULAR)
        left.addView(greet); left.addView(clock); left.addView(date)
        top.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val pills = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val (wp, wi, wt) = Ui.iconPill(c, "weather-cloudy", "")
        weatherPill = wp; weatherIcon = wi; weatherText = wt
        pills.addView(wp)
        val (sp, _, _) = Ui.iconPill(c, "cog-outline", "Paramètres", "settings") { openSettings() }
        pills.addView(sp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = Ui.dp(c, 12)
        })
        top.addView(pills)
        content.addView(top)

        // Milieu : maison
        content.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        val (hb, hr) = Ui.hrow(c, padV = 12)
        homeBox = hb; homeRow = hr
        content.addView(homeBox)

        // Bas : nom de l'élément sélectionné + dock
        focusName = Ui.text(c, "", 26f, Color.WHITE, Ui.MEDIUM).apply {
            setPadding(Ui.dp(c, 8), Ui.dp(c, 10), 0, Ui.dp(c, 8))
        }
        content.addView(focusName)
        val band = FrameLayout(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(c, 30).toFloat()
                setColor(Color.argb(0x8C, 0x20, 0x22, 0x2C))
                setStroke(Ui.dp(c, 1), Color.argb(0x1A, 0xFF, 0xFF, 0xFF))
            }
            clipToOutline = true
            clipChildren = true
        }
        val (db, dr) = Ui.hrow(c, padV = 18)
        db.isHorizontalFadingEdgeEnabled = true
        db.setFadingEdgeLength(Ui.dp(c, 48))
        dockRow = dr
        band.addView(db)
        content.addView(band, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(content, matchParent())
        setContentView(root)
    }

    private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun addAurora(color: Int, alpha: Int, sizeDp: Int, gravity: Int, offX: Int, offY: Int, dx: Float, dy: Float, period: Long) {
        val c = this
        val size = Ui.dp(c, sizeDp)
        val v = View(c).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size / 2f
                colors = intArrayOf(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT)
            }
        }
        root.addView(v, FrameLayout.LayoutParams(size, size, gravity).apply {
            leftMargin = Ui.dp(c, offX); rightMargin = Ui.dp(c, offX)
            topMargin = Ui.dp(c, offY); bottomMargin = Ui.dp(c, -sizeDp / 3)
        })
        ObjectAnimator.ofPropertyValuesHolder(v,
            PropertyValuesHolder.ofFloat("translationX", 0f, Ui.dp(c, dx.toInt()).toFloat()),
            PropertyValuesHolder.ofFloat("translationY", 0f, Ui.dp(c, dy.toInt()).toFloat()),
        ).apply {
            duration = period
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun loadWallpaper() {
        val f = File(ConfigStore.file(this).parentFile, "wallpaper.jpg")
        val stamp = if (f.exists()) f.lastModified() else 0L
        if (stamp == wallpaperStamp) return
        wallpaperStamp = stamp
        val bmp = if (f.exists()) try {
            android.graphics.BitmapFactory.decodeFile(f.absolutePath,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })
        } catch (e: Exception) { null } else null
        wallpaper.setImageBitmap(bmp)
        Ui.gone(wallpaper, bmp == null)
        Ui.gone(scrim, bmp == null)
    }

    private fun openSettings() {
        val tries = listOf(
            Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
        )
        for (i in tries) {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) {}
        }
        toast("Paramètres introuvables")
    }

    // ------------------------------------------------------------------ Contenu

    private fun setGlow(col: Int, a: Int = 0x66) {
        glow.colors = intArrayOf(Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)), Color.TRANSPARENT)
        focusName.setShadowLayer(Ui.dp(this, 22).toFloat(), 0f, 0f, Color.argb(0xCC, Color.red(col), Color.green(col), Color.blue(col)))
    }

    private fun onDockFocus(key: String, name: String, banner: Drawable?, color: Int?) = { has: Boolean ->
        if (has) {
            lastKey = key
            focusName.text = name
            setGlow(color ?: Ui.dominantColor(banner))
        }
    }

    private fun homeFocus(key: String, name: String) = { has: Boolean ->
        if (has) {
            lastKey = key
            focusName.text = name
            setGlow(Ui.ON, 0x40)
        }
    }

    private fun populate() {
        val c = this
        dockRow.removeAllViews()
        dockRow.addView(liveCard())
        for (pkg in cfg.favorites) {
            val name = Ui.label(c, pkg) ?: continue
            val b = Ui.banner(c, pkg) ?: Ui.icon(c, pkg)
            Ui.appCard(c, pkg, 176, 99, onFocus = onDockFocus(pkg, name, b, null))?.let { dockRow.addView(it) }
        }
        dockRow.addView(Ui.glyphCard(c, "view-grid-outline", "Toutes les applis", 176, 99, "all",
            onDockFocus("all", "Toutes les applis", null, Color.parseColor("#8E8E93"))) {
            startActivity(Intent(c, AppsActivity::class.java))
        })

        homeRow.removeAllViews()
        tileViews.clear()
        camViews.clear()
        Ui.gone(homeBox, cfg.tiles.isEmpty())
        for (t in cfg.tiles) {
            homeRow.addView(if (t.entity.startsWith("camera.") || t.entity.startsWith("image.")) cameraTile(t) else stateTile(t))
        }
        val target = lastKey?.let { root.findViewWithTag<View>(it) } ?: dockRow.getChildAt(0)
        target?.requestFocus()
    }

    private fun liveCard(): View {
        val c = this
        val blue = Color.parseColor("#0A84FF")
        val card = Ui.card(c, 176, 99, Color.TRANSPARENT, "live", onDockFocus("live", "TV en direct", null, blue)) {
            if (!Ui.launch(c, cfg.liveTvPackage)) toast("Impossible d'ouvrir ${cfg.liveTvPackage}")
        }
        card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(blue, Color.parseColor("#5E5CE6"), Color.parseColor("#BF5AF2"))).apply {
            cornerRadius = Ui.dp(c, 20).toFloat()
        }
        card.addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(0x38, 0xFF, 0xFF, 0xFF), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 45), Gravity.TOP))

        val dot = View(c).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF453A")) }
        }
        val badge = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 9), Ui.dp(c, 3))
            background = GradientDrawable().apply { cornerRadius = Ui.dp(c, 20).toFloat(); setColor(Color.argb(0x59, 0, 0, 0)) }
            addView(dot, LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)).apply { marginEnd = Ui.dp(c, 5) })
            addView(Ui.text(c, "EN DIRECT", 9f, Color.WHITE, Ui.SEMIBOLD).apply { letterSpacing = 0.12f })
        }
        card.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { leftMargin = Ui.dp(c, 10); topMargin = Ui.dp(c, 10) })
        ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f).apply {
            duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
        }
        card.addView(Icons.view(c, "television-play", 26f, Color.WHITE), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ).apply { rightMargin = Ui.dp(c, 12); topMargin = Ui.dp(c, 9) })
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(c, "TV en direct", 17f, Color.WHITE, Ui.SEMIBOLD))
            addView(Ui.text(c, "Bouygues Telecom", 11f, Color.argb(0xC0, 0xFF, 0xFF, 0xFF), Ui.REGULAR).apply {
                setPadding(0, Ui.dp(c, 2), 0, 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START).apply { leftMargin = Ui.dp(c, 12); bottomMargin = Ui.dp(c, 10) })
        return card
    }

    private fun stateTile(t: Tile): View {
        val c = this
        val badge = Ui.iconBadge(c, Icons.forEntity(t.entity), 34, 19f, Color.argb(0x26, 0xFF, 0xFF, 0xFF), Color.WHITE)
        val state = Ui.text(c, "…", 21f, Color.WHITE, Ui.SEMIBOLD)
        val card = Ui.card(c, 156, 104, Color.TRANSPARENT, t.entity, homeFocus(t.entity, t.name)) { onTile(t) }
        card.background = Ui.glassBg(c)
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            addView(badge)
            addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
            addView(Ui.text(c, t.name, 13f, Color.argb(0xA6, 0xFF, 0xFF, 0xFF), Ui.MEDIUM))
            addView(state)
        }, matchParent())
        tileViews[t.entity] = TileView(card, badge, state)
        return card
    }

    private fun cameraTile(t: Tile): View {
        val c = this
        val card = Ui.card(c, 185, 104, Color.TRANSPARENT, t.entity, homeFocus(t.entity, t.name)) {
            startActivity(Intent(c, CameraActivity::class.java).putExtra("entity", t.entity).putExtra("name", t.name))
        }
        card.background = Ui.glassBg(c)
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(img, matchParent())
        card.addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.argb(0xC0, 0, 0, 0), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 56), Gravity.BOTTOM))
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Icons.view(c, "doorbell-video", 17f, Color.WHITE))
            addView(Ui.text(c, t.name, 14f, Color.WHITE, Ui.SEMIBOLD).apply { setPadding(Ui.dp(c, 7), 0, 0, 0) })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = Ui.dp(c, 12); bottomMargin = Ui.dp(c, 10)
        })
        camViews[t.entity] = img
        return card
    }

    // ------------------------------------------------------------------ Home Assistant

    private fun onTile(t: Tile) {
        val h = ha ?: return toast("Home Assistant non configuré")
        if (!HaClient.isActionable(t.entity)) return
        if (t.entity.startsWith("cover.")) {
            val now = System.currentTimeMillis()
            if (armedEntity != t.entity || now - armedAt > 3000) {
                armedEntity = t.entity
                armedAt = now
                tileViews[t.entity]?.state?.apply { text = "Encore OK ?"; setTextColor(Color.parseColor("#FF9F0A")) }
                return
            }
            armedEntity = null
        }
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

    private fun refreshHa(withCameras: Boolean) {
        val h = ha
        if (h == null) {
            weatherText.text = if (cfg.haUrl.isBlank()) "Configurer Home Assistant" else "HA : token manquant"
            return
        }
        val weatherEntity = cfg.weatherEntity
        val entities = tileViews.keys.toList()
        val cams = if (withCameras) camViews.keys.toList() else emptyList()
        thread {
            val w = if (weatherEntity.isNotBlank()) h.state(weatherEntity) else null
            val states = entities.associateWith { h.state(it) }
            val shots = cams.associateWith { h.snapshot(it) }
            runOnUiThread {
                Ui.gone(weatherPill, weatherEntity.isBlank())
                if (w != null) {
                    val wf = formatWeather(w)
                    lastWeather = wf
                    Icons.set(weatherIcon, wf.first)
                    weatherText.text = wf.second
                } else if (weatherEntity.isNotBlank()) weatherText.text = "Météo indispo"
                states.forEach { (e, s) -> if (s != null) applyTile(e, s) else tileViews[e]?.state?.text = "Hors ligne" }
                shots.forEach { (e, bmp) -> if (bmp != null) camViews[e]?.setImageBitmap(bmp) }
            }
        }
    }

    private fun applyTile(entity: String, js: JSONObject) {
        val tv = tileViews[entity] ?: return
        val st = js.optString("state")
        val attrs = js.optJSONObject("attributes")
        val unit = attrs?.optString("unit_of_measurement", "") ?: ""
        val on = st == "on" || st == "open" || st == "opening" || st == "playing" || st == "cleaning"
        tv.state.text = formatState(st, unit)
        tv.state.setTextColor(if (on) Ui.ON else Color.WHITE)
        val icon = attrs?.optString("icon", "")?.takeIf { it.startsWith("mdi:") } ?: Icons.forEntity(entity, st)
        Icons.set(tv.badge, icon)
        (tv.badge.background as? GradientDrawable)?.setColor(if (on) Ui.ON else Color.argb(0x26, 0xFF, 0xFF, 0xFF))
        tv.badge.setTextColor(if (on) Color.BLACK else Color.WHITE)
        tv.card.background = if (on) Ui.glassBg(this, alpha = 0x26).apply { setStroke(Ui.dp(this@MainActivity, 1), Color.argb(0x66, 0xFF, 0xD6, 0x0A)) }
        else Ui.glassBg(this)
    }

    private fun formatState(st: String, unit: String): String {
        when (st) {
            "on" -> return "Allumé"
            "off" -> return "Éteint"
            "open" -> return "Ouvert"
            "closed" -> return "Fermé"
            "opening" -> return "Ouverture…"
            "closing" -> return "Fermeture…"
            "cleaning" -> return "Nettoie"
            "docked" -> return "À la base"
            "unavailable" -> return "Indispo"
            "unknown" -> return "—"
        }
        val n = st.toDoubleOrNull() ?: return if (unit.isNotEmpty()) "$st $unit" else st
        if (unit == "W" && n >= 1000) return String.format(Locale.FRANCE, "%.2f kW", n / 1000)
        val v = when {
            kotlin.math.abs(n) >= 100 -> String.format(Locale.FRANCE, "%.0f", n)
            st.contains('.') -> String.format(Locale.FRANCE, "%.1f", n)
            else -> st
        }
        return if (unit.isNotEmpty()) "$v $unit" else v
    }

    private fun formatWeather(js: JSONObject): Pair<String, String> {
        val cond = js.optString("state")
        val a = js.optJSONObject("attributes")
        val t = a?.optDouble("temperature")
        val temp = if (t == null || t.isNaN()) "?" else String.format(Locale.FRANCE, "%.0f", t)
        val unit = a?.optString("temperature_unit", "°C") ?: "°C"
        val fr = when (cond) {
            "sunny" -> "Ensoleillé"; "clear-night" -> "Nuit claire"; "partlycloudy" -> "Éclaircies"
            "cloudy" -> "Nuageux"; "rainy" -> "Pluie"; "pouring" -> "Forte pluie"
            "lightning", "lightning-rainy" -> "Orage"; "snowy", "snowy-rainy" -> "Neige"
            "fog" -> "Brouillard"; "windy", "windy-variant" -> "Venteux"; "hail" -> "Grêle"
            else -> cond
        }
        return Icons.weather(cond) to "$temp$unit · $fr"
    }

    // ------------------------------------------------------------------ Écran de veille

    private fun resetIdle() {
        ui.removeCallbacks(idle)
        if (cfg.saverMinutes > 0) ui.postDelayed(idle, cfg.saverMinutes * 60_000L)
    }

    private fun showSaver() {
        if (saver != null) return
        val c = this
        val s = FrameLayout(c).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val now = Date()
        saverClock = Ui.text(c, clockFmt.format(now), 160f, Color.WHITE, Ui.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            letterSpacing = -0.03f
        }
        saverDate = Ui.text(c, dateFmt.format(now).replaceFirstChar { it.uppercase() }, 30f,
            Color.argb(0xCC, 0xFF, 0xFF, 0xFF), Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        val wRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(c, 22), 0, 0)
        }
        saverWeatherIcon = Icons.view(c, lastWeather?.first ?: "weather-cloudy", 26f, Color.argb(0x99, 0xFF, 0xFF, 0xFF))
        saverWeather = Ui.text(c, lastWeather?.second ?: "", 22f, Color.argb(0x99, 0xFF, 0xFF, 0xFF), Ui.REGULAR).apply {
            setPadding(Ui.dp(c, 10), 0, 0, 0)
        }
        wRow.addView(saverWeatherIcon); wRow.addView(saverWeather)
        Ui.gone(wRow, lastWeather == null)
        box.addView(saverClock); box.addView(saverDate); box.addView(wRow)
        s.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(s, matchParent())
        saver = s
        s.animate().alpha(1f).setDuration(1500).start()
        ui.removeCallbacks(haTick)   // pas de requêtes HA pendant la veille
    }

    private fun hideSaver() {
        val s = saver ?: return
        saver = null
        root.removeView(s)
        ui.removeCallbacks(haTick)
        ui.post(haTick)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
