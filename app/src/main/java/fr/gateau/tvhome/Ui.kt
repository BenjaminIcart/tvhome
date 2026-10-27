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

    val THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

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

    fun pill(c: Context, s: String): TextView = text(c, s, 18f, Color.WHITE, LIGHT).apply {
        setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 50).toFloat()
            setColor(PILL)
        }
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
        val radius = dp(c, 16).toFloat()
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
        c: Context, glyph: String, title: String, wDp: Int, hDp: Int, color: Int, key: String,
        onFocus: ((Boolean) -> Unit)? = null, onClick: () -> Unit,
    ): FrameLayout = card(c, wDp, hDp, color, key, onFocus, onClick).apply {
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(text(c, glyph, 26f).apply { gravity = Gravity.CENTER })
            addView(text(c, title, 13f, Color.WHITE, MEDIUM).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 8), 0, 0)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun background(): Drawable = GradientDrawable().apply { setColor(BLACK) }

    fun gone(v: View, hide: Boolean) { v.visibility = if (hide) View.GONE else View.VISIBLE }
}
