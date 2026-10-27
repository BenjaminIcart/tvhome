package fr.gateau.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
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
    private lateinit var weather: TextView
    private lateinit var homeTitle: TextView
    private lateinit var homeBox: View
    private lateinit var homeRow: LinearLayout
    private lateinit var focusName: TextView
    private lateinit var dockRow: LinearLayout
    private lateinit var glow: GradientDrawable
    private lateinit var wallpaper: ImageView
    private lateinit var scrim: View
    private var wallpaperStamp = -1L
    private var pendingSaver = false

    private class TileView(val card: FrameLayout, val state: TextView)
    private val tileViews = mutableMapOf<String, TileView>()
    private val camViews = mutableMapOf<String, ImageView>()
    private var lastKey: String? = null
    private var saver: FrameLayout? = null
    private lateinit var saverClock: TextView
    private lateinit var saverDate: TextView
    private val idle = Runnable { showSaver() }
    private val saverMove = object : Runnable {
        override fun run() {
            val s = saver ?: return
            val now = Date()
            saverClock.text = clockFmt.format(now)
            saverDate.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            val box = s.getChildAt(0)
            val maxX = (s.width - box.width).coerceAtLeast(1)
            val maxY = (s.height - box.height).coerceAtLeast(1)
            // Déplacement lent toutes les 30 s pour éviter le marquage de l'écran
            box.animate().x((Math.random() * maxX).toFloat()).y((Math.random() * maxY).toFloat())
                .setDuration(3000).start()
            ui.postDelayed(this, 30_000)
        }
    }
    private var camCounter = 0

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = clockFmt.format(now)
            date.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greet.text = when (h) { in 5..11 -> "Bonjour"; in 12..17 -> "Bon après-midi"; else -> "Bonsoir" }
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

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (saver != null) {
            // Une touche réveille l'accueil sans déclencher d'action
            if (event.action == android.view.KeyEvent.ACTION_UP) { hideSaver(); resetIdle() }
            return true
        }
        resetIdle()
        return super.dispatchKeyEvent(event)
    }

    private fun resetIdle() {
        ui.removeCallbacks(idle)
        if (cfg.saverMinutes > 0) ui.postDelayed(idle, cfg.saverMinutes * 60_000L)
    }

    private fun showSaver() {
        if (saver != null) return
        val c = this
        val s = FrameLayout(c).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        saverClock = Ui.text(c, "", 110f, Color.argb(0xD0, 0xFF, 0xFF, 0xFF), Ui.THIN)
        saverDate = Ui.text(c, "", 22f, Color.argb(0x90, 0xFF, 0xFF, 0xFF), Ui.LIGHT)
        box.addView(saverClock); box.addView(saverDate)
        s.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(s, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        saver = s
        s.animate().alpha(1f).setDuration(1500).start()
        ui.removeCallbacks(haTick)   // pas de requêtes HA pendant la veille
        ui.post(saverMove)
    }

    private fun hideSaver() {
        val s = saver ?: return
        saver = null
        ui.removeCallbacks(saverMove)
        root.removeView(s)
        ui.removeCallbacks(haTick)
        ui.post(haTick)
    }

    @Deprecated("Launcher : Retour ne quitte pas l'accueil")
    override fun onBackPressed() {
        dockRow.getChildAt(0)?.requestFocus()
    }

    private fun startBridge() {
        val i = Intent(this, BridgeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) { /* ignoré */ }
    }

    private fun buildLayout() {
        val c = this
        root = FrameLayout(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0E1328"), Color.parseColor("#05060C"), Color.BLACK))
        }
        wallpaper = ImageView(c).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addAurora(c, Color.parseColor("#5E5CE6"), 0x50, 620, Gravity.TOP or Gravity.END, -160, -220, 70f, 40f, 38_000)
        addAurora(c, Color.parseColor("#30D5C8"), 0x30, 520, Gravity.TOP or Gravity.START, -200, 60, -50f, 60f, 46_000)
        addAurora(c, Color.parseColor("#BF5AF2"), 0x28, 480, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 90f, -30f, 52_000)
        scrim = View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(0x66, 0, 0, 0), Color.argb(0x22, 0, 0, 0), Color.argb(0xCC, 0, 0, 0)))
            visibility = View.GONE
        }
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Lueur ambiante en bas, couleur de l'appli sélectionnée
        glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = Ui.dp(c, 520).toFloat()
            setGradientCenter(0.3f, 1.0f)
            colors = intArrayOf(Color.argb(0x55, 0x3A, 0x6F, 0xF7), Color.TRANSPARENT)
        }
        root.addView(View(c).apply { background = glow },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 48), Ui.dp(c, 28), Ui.dp(c, 48), Ui.dp(c, 22))
            clipChildren = false
            clipToPadding = false
        }

        // Haut : salutation, grande heure fine, date | pilule météo
        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        greet = Ui.text(c, "", 16f, Ui.MUTED, Ui.LIGHT)
        clock = Ui.text(c, "--:--", 64f, Color.WHITE, Ui.THIN).apply { setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4)) }
        date = Ui.text(c, "", 17f, Color.WHITE, Ui.LIGHT)
        left.addView(greet); left.addView(clock); left.addView(date)
        top.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pills = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weather = Ui.pill(c, "")
        pills.addView(weather)
        pills.addView(Ui.focusPill(c, "⚙  Paramètres", "settings") { openSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(c, 12)
            })
        top.addView(pills)
        content.addView(top)

        // Milieu : maison
        content.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        homeTitle = Ui.section(c, "Maison")
        content.addView(homeTitle)
        val (hb, hr) = Ui.hrow(c, padV = 12)
        homeBox = hb; homeRow = hr
        content.addView(homeBox)

        // Bas : nom de l'appli sélectionnée + dock
        focusName = Ui.text(c, "", 28f, Color.WHITE, Ui.LIGHT).apply {
            setPadding(Ui.dp(c, 8), Ui.dp(c, 10), 0, Ui.dp(c, 6))
            setShadowLayer(Ui.dp(c, 18).toFloat(), 0f, 0f, Color.argb(0x99, 0x3A, 0x6F, 0xF7))
        }
        content.addView(focusName)

        val band = FrameLayout(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(c, 28).toFloat()
                setColor(Ui.BAND)
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

        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun onDockFocus(key: String, name: String, banner: android.graphics.drawable.Drawable?, color: Int?) = { has: Boolean ->
        if (has) {
            lastKey = key
            focusName.text = name
            val col = color ?: Ui.dominantColor(banner)
            glow.colors = intArrayOf(Color.argb(0x66, Color.red(col), Color.green(col), Color.blue(col)), Color.TRANSPARENT)
            focusName.setShadowLayer(Ui.dp(this, 22).toFloat(), 0f, 0f, Color.argb(0xCC, Color.red(col), Color.green(col), Color.blue(col)))
        }
    }

    private fun populate() {
        val c = this
        dockRow.removeAllViews()

        val tvColor = Color.parseColor("#0A84FF")
        dockRow.addView(liveCard(tvColor))
        for (pkg in cfg.favorites) {
            val name = Ui.label(c, pkg) ?: continue
            val b = Ui.banner(c, pkg) ?: Ui.icon(c, pkg)
            Ui.appCard(c, pkg, 176, 99, onFocus = onDockFocus(pkg, name, b, null))?.let { dockRow.addView(it) }
        }
        val gray = Color.parseColor("#48484A")
        dockRow.addView(Ui.glyphCard(c, "▦", "Toutes les applis", 176, 99, Ui.TILE, "all",
            onDockFocus("all", "Toutes les applis", null, gray)) {
            startActivity(Intent(c, AppsActivity::class.java))
        })

        homeRow.removeAllViews()
        tileViews.clear()
        camViews.clear()
        val hide = cfg.tiles.isEmpty()
        Ui.gone(homeTitle, hide); Ui.gone(homeBox, hide)
        for (t in cfg.tiles) {
            if (t.entity.startsWith("camera.") || t.entity.startsWith("image.")) homeRow.addView(cameraTile(t)) else homeRow.addView(stateTile(t))
        }

        val target = lastKey?.let { root.findViewWithTag<View>(it) } ?: dockRow.getChildAt(0)
        target?.requestFocus()
    }

    private fun addAurora(c: android.content.Context, color: Int, alpha: Int, sizeDp: Int, gravity: Int,
                          offX: Int, offY: Int, dx: Float, dy: Float, period: Long) {
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
        // Dérive très lente (une seule animation matérielle, aucun coût réseau)
        android.animation.ObjectAnimator.ofPropertyValuesHolder(v,
            android.animation.PropertyValuesHolder.ofFloat("translationX", 0f, Ui.dp(c, dx.toInt()).toFloat()),
            android.animation.PropertyValuesHolder.ofFloat("translationY", 0f, Ui.dp(c, dy.toInt()).toFloat()),
        ).apply {
            duration = period
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Fond d'écran perso : fichier wallpaper.jpg à côté de config.json. */
    private fun loadWallpaper() {
        val f = java.io.File(ConfigStore.file(this).parentFile, "wallpaper.jpg")
        val stamp = if (f.exists()) f.lastModified() else 0L
        if (stamp == wallpaperStamp) return
        wallpaperStamp = stamp
        val bmp = if (f.exists()) try {
            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
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

    private fun liveCard(tvColor: Int): View {
        val c = this
        val card = Ui.card(c, 176, 99, Color.TRANSPARENT, "live",
            onDockFocus("live", "TV en direct", null, tvColor)) {
            if (!Ui.launch(c, cfg.liveTvPackage)) toast("Impossible d'ouvrir ${cfg.liveTvPackage}")
        }
        card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0A84FF"), Color.parseColor("#5E5CE6"), Color.parseColor("#BF5AF2"))).apply {
            cornerRadius = Ui.dp(c, 16).toFloat()
        }
        // Reflet léger en haut
        card.addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(0x38, 0xFF, 0xFF, 0xFF), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 45), Gravity.TOP))
        // Badge « EN DIRECT » avec point rouge qui pulse
        val dot = View(c).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF453A")) }
        }
        val badge = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 9), Ui.dp(c, 3))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(c, 20).toFloat()
                setColor(Color.argb(0x59, 0, 0, 0))
            }
            addView(dot, LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)).apply { marginEnd = Ui.dp(c, 5) })
            addView(Ui.text(c, "EN DIRECT", 9f, Color.WHITE, Ui.MEDIUM).apply { letterSpacing = 0.12f })
        }
        card.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { leftMargin = Ui.dp(c, 10); topMargin = Ui.dp(c, 10) })
        android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f).apply {
            duration = 900
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
        // Icône TV dessinée + titre
        card.addView(Ui.tvIcon(c), FrameLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 28),
            Gravity.TOP or Gravity.END).apply { rightMargin = Ui.dp(c, 12); topMargin = Ui.dp(c, 10) })
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(c, "TV en direct", 17f, Color.WHITE, Ui.MEDIUM))
            addView(Ui.text(c, "Bouygues Telecom", 11f, Color.argb(0xC0, 0xFF, 0xFF, 0xFF), Ui.LIGHT).apply {
                setPadding(0, Ui.dp(c, 2), 0, 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START).apply { leftMargin = Ui.dp(c, 12); bottomMargin = Ui.dp(c, 10) })
        return card
    }

    private fun homeFocus(key: String, name: String) = { has: Boolean ->
        if (has) {
            lastKey = key
            focusName.text = name
            glow.colors = intArrayOf(Color.argb(0x40, 0xFF, 0xD6, 0x0A), Color.TRANSPARENT)
            focusName.setShadowLayer(Ui.dp(this, 22).toFloat(), 0f, 0f, Color.argb(0x99, 0xFF, 0xD6, 0x0A))
        }
    }

    private fun stateTile(t: Tile): View {
        val c = this
        val glyph = when (t.entity.substringBefore('.')) {
            "light" -> "💡"; "switch" -> "🔌"; "vacuum" -> "🧹"; "input_boolean" -> "🌙"; "cover" -> "🚗"
            else -> if (t.entity.contains("energ")) "🔋" else if (t.entity.contains("puissance") || t.entity.contains("power")) "⚡" else "📈"
        }
        val state = Ui.text(c, "…", 20f, Color.WHITE, Ui.LIGHT)
        val card = Ui.card(c, 150, 104, Ui.TILE, t.entity, homeFocus(t.entity, t.name)) { onTile(t) }
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            addView(Ui.text(c, glyph, 20f))
            addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
            addView(Ui.text(c, t.name, 13f, Ui.MUTED, Ui.MEDIUM))
            addView(state)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tileViews[t.entity] = TileView(card, state)
        return card
    }

    private fun cameraTile(t: Tile): View {
        val c = this
        val card = Ui.card(c, 185, 104, Ui.TILE, t.entity, homeFocus(t.entity, t.name)) {
            startActivity(Intent(c, CameraActivity::class.java)
                .putExtra("entity", t.entity).putExtra("name", t.name))
        }
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(0xC0, 0, 0, 0), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 60), Gravity.BOTTOM))
        card.addView(Ui.text(c, "🔔  ${t.name}", 15f, Color.WHITE, Ui.MEDIUM).apply {
            setPadding(Ui.dp(c, 14), 0, 0, Ui.dp(c, 12))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        camViews[t.entity] = img
        return card
    }

    private var armedEntity: String? = null
    private var armedAt = 0L

    private fun onTile(t: Tile) {
        val h = ha ?: return toast("Home Assistant non configuré")
        if (!HaClient.isActionable(t.entity)) return
        if (t.entity.startsWith("cover.")) {
            val now = System.currentTimeMillis()
            if (armedEntity != t.entity || now - armedAt > 3000) {
                armedEntity = t.entity
                armedAt = now
                tileViews[t.entity]?.state?.text = "Encore OK ?"
                tileViews[t.entity]?.state?.setTextColor(Color.parseColor("#FF9F0A"))
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
            weather.text = if (cfg.haUrl.isBlank()) "Configurer Home Assistant" else "HA : token manquant"
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
                Ui.gone(weather, weatherEntity.isBlank())
                if (weatherEntity.isNotBlank()) weather.text = w?.let { formatWeather(it) } ?: "Météo indispo"
                states.forEach { (e, s) ->
                    if (s != null) applyTile(e, s) else tileViews[e]?.state?.text = "Hors ligne"
                }
                shots.forEach { (e, bmp) -> if (bmp != null) camViews[e]?.setImageBitmap(bmp) }
            }
        }
    }

    private fun applyTile(entity: String, js: JSONObject) {
        val tv = tileViews[entity] ?: return
        val st = js.optString("state")
        val unit = js.optJSONObject("attributes")?.optString("unit_of_measurement", "") ?: ""
        val on = st == "on" || st == "open" || st == "opening" || st == "playing" || st == "cleaning"
        tv.state.text = formatState(st, unit)
        (tv.card.background as? GradientDrawable)?.setColor(if (on) Ui.TILE_ON else Ui.TILE)
        tv.state.setTextColor(if (on) Ui.ON else Color.WHITE)
    }

    private fun formatState(st: String, unit: String): String {
        when (st) {
            "on" -> return "Allumé"
            "off" -> return "Éteint"
            "open" -> return "Ouvert"
            "closed" -> return "Fermé"
                "cleaning" -> return "Nettoie"
            "opening" -> return "Ouverture…"
            "closing" -> return "Fermeture…"
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

    private fun formatWeather(js: JSONObject): String {
        val cond = js.optString("state")
        val a = js.optJSONObject("attributes")
        val t = a?.optDouble("temperature")
        val temp = if (t == null || t.isNaN()) "?" else String.format(Locale.FRANCE, "%.0f", t)
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
        return "$emoji  $temp$unit · $fr"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
