package fr.gateau.tvhome

import android.content.Context
import android.content.Intent
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
    val BG_TOP = Color.parseColor("#0B1220")
    val BG_BOTTOM = Color.parseColor("#1A2438")
    val CARD = Color.parseColor("#232E45")
    val CARD_ON = Color.parseColor("#F5A623")
    val ACCENT = Color.parseColor("#1565C0")
    val MUTED = Color.parseColor("#9AA7C0")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun section(c: Context, title: String): TextView =
        text(c, title, 22f, MUTED, bold = true).apply {
            setPadding(dp(c, 4), dp(c, 24), 0, dp(c, 4))
        }

    /** Ligne horizontale défilante. Renvoie (conteneur à ajouter, ligne où mettre les cartes). */
    fun hrow(c: Context): Pair<View, LinearLayout> {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16))
            clipChildren = false
            clipToPadding = false
        }
        val hsv = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            addView(row)
        }
        return hsv to row
    }

    fun card(c: Context, wDp: Int, hDp: Int, color: Int = CARD, onClick: () -> Unit): FrameLayout {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(c, 14).toFloat()
            setColor(color)
        }
        return FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)).apply {
                marginEnd = dp(c, 22)
            }
            background = bg
            clipToOutline = true
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                bg.setStroke(if (has) dp(c, 3) else 0, Color.WHITE)
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
                    .setDuration(120).start()
                v.elevation = if (has) 16f else 0f
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

    fun appCard(c: Context, pkg: String, wDp: Int = 300, hDp: Int = 170): FrameLayout? {
        val name = label(c, pkg) ?: return null
        val card = card(c, wDp, hDp) { launch(c, pkg) }
        val b = banner(c, pkg)
        if (b != null) {
            card.addView(ImageView(c).apply {
                setImageDrawable(b)
                scaleType = ImageView.ScaleType.FIT_XY
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val col = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(c).apply { setImageDrawable(icon(c, pkg)) },
                    LinearLayout.LayoutParams(dp(c, 72), dp(c, 72)))
                addView(text(c, name, 16f).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(c, 8), 0, 0)
                })
            }
            card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return card
    }

    fun labelCard(c: Context, title: String, wDp: Int, hDp: Int, color: Int, onClick: () -> Unit): FrameLayout =
        card(c, wDp, hDp, color, onClick).apply {
            addView(text(c, title, 22f, bold = true).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

    fun background(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))
}
