package fr.gateau.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast

object Notifier {
    private val main = Handler(Looper.getMainLooper())

    fun show(ctx: Context, title: String, message: String, seconds: Int) {
        main.post {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "$title\n$message", Toast.LENGTH_LONG).show()
                return@post
            }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 18), Ui.dp(ctx, 24), Ui.dp(ctx, 18))
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(ctx, 16).toFloat()
                    setColor(Color.parseColor("#E6141C2C"))
                    setStroke(Ui.dp(ctx, 2), Ui.CARD_ON)
                }
                addView(Ui.text(ctx, title, 22f, Ui.CARD_ON, bold = true).apply { maxWidth = Ui.dp(ctx, 520) })
                if (message.isNotBlank()) {
                    addView(Ui.text(ctx, message, 18f).apply {
                        maxWidth = Ui.dp(ctx, 520)
                        setPadding(0, Ui.dp(ctx, 6), 0, 0)
                    })
                }
                alpha = 0f
            }
            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = Ui.dp(ctx, 40)
                y = Ui.dp(ctx, 40)
            }
            try {
                wm.addView(box, lp)
                box.animate().alpha(1f).setDuration(200).start()
                main.postDelayed({ try { wm.removeView(box) } catch (e: Exception) {} },
                    seconds.coerceIn(2, 120) * 1000L)
            } catch (e: Exception) {
                Toast.makeText(ctx, "$title\n$message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
