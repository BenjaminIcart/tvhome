package fr.gateau.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val BLACK = Color.BLACK
    val BAND = Color.argb(0xB0, 0x26, 0x26, 0x2A)
    val TILE = Color.parseColor("#1C1C1E")
    val TILE_ON = Color.parseColor("#3A3320")
    val ON = Color.parseColor("#FFD60A")
    val MUTED = Color.parseColor("#8E8E93")
    val GLASS = Color.argb(0x2E, 0xFF, 0xFF, 0xFF)
    val PILL = Color.argb(0x24, 0xFF, 0xFF, 0xFF)

    var THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    var LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    var REGULAR: Typeface = Typeface.DEFAULT
    var MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    var SEMIBOLD: Typeface = Typeface.DEFAULT_BOLD
    private var ready = false

    /** Charge la police Outfit et les icônes (à appeler avant de construire l'UI). */
    fun init(c: Context) {
        Icons.init(c)
        if (ready) return
        fun f(n: String, fb: Typeface) = try { Typeface.createFromAsset(c.assets, "fonts/Outfit_$n.ttf") } catch (e: Exception) { fb }
        THIN = f("200ExtraLight", THIN)
        LIGHT = f("300Light", LIGHT)
        REGULAR = f("400Regular", REGULAR)
        MEDIUM = f("500Medium", MEDIUM)
        SEMIBOLD = f("600SemiBold", SEMIBOLD)
        ready = true
    }

    /** Fond « verre » : blanc translucide + liseré fin. */
    fun glassBg(c: Context, radiusDp: Int = 20, alpha: Int = 0x16): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(c, radiusDp).toFloat()
        setColor(Color.argb(alpha, 0xFF, 0xFF, 0xFF))
        setStroke(dp(c, 1), Color.argb(0x1F, 0xFF, 0xFF, 0xFF))
    }

    /** Pastille ronde contenant une icône (couleur unie). */
    fun iconBadge(c: Context, icon: String, sizeDp: Int, iconSp: Float, bg: Int, fg: Int): android.widget.TextView =
        Icons.view(c, icon, iconSp, fg).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
            layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
        }

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, sp: Float, color: Int = Color.WHITE, tf: Typeface = LIGHT): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = tf
            includeFontPadding = false
        }

    fun section(c: Context, title: String): TextView =
        text(c, title, 18f, MUTED, MEDIUM).apply {
            setPadding(dp(c, 6), dp(c, 8), 0, 0)
        }

    fun pill(c: Context, s: String): TextView = text(c, s, 18f, Color.WHITE, REGULAR).apply {
        setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 50).toFloat()
            setColor(PILL)
        }
    }

    /** Pilule givrée focusable (boutons du haut). */
    fun focusPill(c: Context, s: String, key: String, onClick: () -> Unit): TextView {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(c, 50).toFloat()
            setColor(PILL)
        }
        return text(c, s, 18f, Color.WHITE, REGULAR).apply {
            tag = key
            setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9))
            background = bg
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                bg.setColor(if (has) Color.WHITE else PILL)
                setTextColor(if (has) Color.BLACK else Color.WHITE)
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
            }
        }
    }

    /** Pilule icône + texte. Focusable si [onClick] est fourni. Renvoie (pilule, icône, texte). */
    fun iconPill(c: Context, icon: String, s: String, key: String? = null, onClick: (() -> Unit)? = null):
        Triple<LinearLayout, TextView, TextView> {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(c, 50).toFloat()
            setColor(PILL)
            setStroke(dp(c, 1), Color.argb(0x1A, 0xFF, 0xFF, 0xFF))
        }
        val ic = Icons.view(c, icon, 20f, Color.WHITE)
        val tx = text(c, s, 18f, Color.WHITE, REGULAR)
        val pill = LinearLayout(c).apply {
            tag = key
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 16), dp(c, 10), dp(c, 18), dp(c, 10))
            background = bg
            addView(ic)
            addView(tx, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(c, 10)
            })
            if (onClick != null) {
                isFocusable = true
                isClickable = true
                setOnClickListener { onClick() }
                setOnFocusChangeListener { v, has ->
                    bg.setColor(if (has) Color.WHITE else PILL)
                    ic.setTextColor(if (has) Color.BLACK else Color.WHITE)
                    tx.setTextColor(if (has) Color.BLACK else Color.WHITE)
                    v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
                }
            }
        }
        return Triple(pill, ic, tx)
    }

    fun hrow(c: Context, padV: Int = 18): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 20), dp(c, padV), dp(c, 20), dp(c, padV))
            clipChildren = false
            clipToPadding = false
        }
        val hsv = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(row)
        }
        return hsv to row
    }

    /** Carte focusable : agrandissement + reflet « verre » au focus, pas de bordure par tuile. */
    fun card(
        c: Context, wDp: Int, hDp: Int, color: Int = TILE, key: String? = null,
        onFocus: ((Boolean) -> Unit)? = null, onClick: () -> Unit,
    ): FrameLayout {
        val radius = dp(c, 20).toFloat()
        val glass = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(0x40, 0xFF, 0xFF, 0xFF), Color.argb(0x0A, 0xFF, 0xFF, 0xFF))
        ).apply {
            cornerRadius = radius
            setStroke(dp(c, 1), Color.argb(0x70, 0xFF, 0xFF, 0xFF))
        }
        return FrameLayout(c).apply {
            tag = key
            layoutParams = LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)).apply { marginEnd = dp(c, 18) }
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(color)
            }
            clipToOutline = true
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                foreground = if (has) glass else null
                v.animate().scaleX(if (has) 1.15f else 1f).scaleY(if (has) 1.15f else 1f)
                    .translationY(if (has) -dp(c, 6).toFloat() else 0f)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                    .setDuration(220).start()
                v.elevation = if (has) 24f else 0f
                onFocus?.invoke(has)
            }
        }
    }

    fun banner(c: Context, pkg: String): Drawable? {
        val pm = c.packageManager
        return try {
            pm.getApplicationBanner(pkg)
                ?: pm.getLeanbackLaunchIntentForPackage(pkg)?.let { pm.getActivityBanner(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun label(c: Context, pkg: String): String? = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    fun icon(c: Context, pkg: String): Drawable? = try {
        c.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    fun launch(c: Context, pkg: String): Boolean {
        // "paquet/.Activite" : lance une activité précise (ex. la TV en direct Bbox)
        android.content.ComponentName.unflattenFromString(pkg)?.takeIf { pkg.contains('/') }?.let { cn ->
            return try {
                c.startActivity(Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                launch(c, cn.packageName)
            }
        }
        val pm = c.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Couleur dominante (moyenne saturée) d'un drawable, pour la lueur ambiante. */
    fun dominantColor(d: Drawable?): Int {
        if (d == null) return Color.parseColor("#3A6FF7")
        return try {
            val bmp = Bitmap.createBitmap(12, 7, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            d.setBounds(0, 0, 12, 7)
            d.draw(cv)
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (x in 0 until 12) for (y in 0 until 7) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
            }
            if (n == 0L) return Color.parseColor("#3A6FF7")
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.4f).coerceAtMost(1f)
            hsv[2] = hsv[2].coerceAtLeast(0.75f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            Color.parseColor("#3A6FF7")
        }
    }

    fun appCard(
        c: Context, pkg: String, wDp: Int = 240, hDp: Int = 135,
        onFocus: ((Boolean) -> Unit)? = null,
    ): FrameLayout? {
        val name = label(c, pkg) ?: return null
        val card = card(c, wDp, hDp, TILE, key = pkg, onFocus = onFocus) { launch(c, pkg) }
        val b = banner(c, pkg)
        if (b != null) {
            card.addView(ImageView(c).apply {
                setImageDrawable(b)
                scaleType = ImageView.ScaleType.FIT_XY
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            card.addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(c).apply { setImageDrawable(icon(c, pkg)) },
                    LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)))
                addView(text(c, name, 13f).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(c, 8), 0, 0)
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return card
    }

    fun glyphCard(
        c: Context, icon: String, title: String, wDp: Int, hDp: Int, key: String,
        onFocus: ((Boolean) -> Unit)? = null, onClick: () -> Unit,
    ): FrameLayout = card(c, wDp, hDp, Color.TRANSPARENT, key, onFocus, onClick).apply {
        background = glassBg(c)
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(iconBadge(c, icon, 40, 22f, Color.argb(0x26, 0xFF, 0xFF, 0xFF), Color.WHITE))
            addView(text(c, title, 14f, Color.WHITE, MEDIUM).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 8), 0, 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Petite icône TV vectorielle (écran + pied), sans emoji. */
    fun tvIcon(c: Context): View = object : View(c) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = dp(c, 2).toFloat()
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val s = p.strokeWidth
            val r = dp(c, 4).toFloat()
            canvas.drawRoundRect(s, s, w - s, h * 0.78f, r, r, p)
            canvas.drawLine(w * 0.32f, h - s, w * 0.68f, h - s, p)
            canvas.drawLine(w * 0.5f, h * 0.78f, w * 0.5f, h - s, p)
        }
    }

    fun background(): Drawable = GradientDrawable().apply { setColor(BLACK) }

    fun gone(v: View, hide: Boolean) { v.visibility = if (hide) View.GONE else View.VISIBLE }
}
