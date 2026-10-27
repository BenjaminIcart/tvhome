package fr.gateau.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.concurrent.thread

object Notifier {
    private val main = Handler(Looper.getMainLooper())

    /** Si [camera] est renseigné, l'instantané HA est récupéré puis affiché dans la notif. */
    fun show(ctx: Context, title: String, message: String, seconds: Int, camera: String = "") {
        if (camera.isBlank()) {
            display(ctx, title, message, seconds, null)
            return
        }
        thread {
            val cfg = ConfigStore.load(ctx)
            val bmp = if (cfg.haUrl.isNotBlank() && cfg.haToken.isNotBlank())
                HaClient(cfg.haUrl, cfg.haToken).snapshot(camera) else null
            display(ctx, title, message, seconds, bmp)
        }
    }

    private fun display(ctx: Context, title: String, message: String, seconds: Int, image: Bitmap?) {
        main.post {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "$title\n$message", Toast.LENGTH_LONG).show()
                return@post
            }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val w = Ui.dp(ctx, if (image != null) 440 else 400)
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(ctx, 22).toFloat()
                    setColor(Color.argb(0xE8, 0x1C, 0x1C, 0x1E))
                    setStroke(Ui.dp(ctx, 1), Color.argb(0x50, 0xFF, 0xFF, 0xFF))
                }
                clipToOutline = true
                if (image != null) {
                    addView(ImageView(ctx).apply {
                        setImageBitmap(image)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }, LinearLayout.LayoutParams(w, w * 9 / 16))
                }
                val txt = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 16), Ui.dp(ctx, 22), Ui.dp(ctx, 18))
                    addView(Ui.text(ctx, title, 20f, Color.WHITE, Ui.MEDIUM).apply { maxWidth = w })
                    if (message.isNotBlank()) {
                        addView(Ui.text(ctx, message, 16f, Ui.MUTED, Ui.LIGHT).apply {
                            maxWidth = w
                            setPadding(0, Ui.dp(ctx, 6), 0, 0)
                        })
                    }
                }
                addView(txt, LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT))
                alpha = 0f
                translationX = Ui.dp(ctx, 40).toFloat()
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
                box.animate().alpha(1f).translationX(0f).setDuration(260).start()
                main.postDelayed({
                    box.animate().alpha(0f).setDuration(250).withEndAction {
                        try { wm.removeView(box) } catch (e: Exception) {}
                    }.start()
                }, seconds.coerceIn(2, 120) * 1000L)
            } catch (e: Exception) {
                Toast.makeText(ctx, "$title\n$message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
