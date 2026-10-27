package fr.gateau.tvhome

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.concurrent.thread

/** Caméra HA en plein écran, instantané rafraîchi chaque seconde. Retour pour quitter. */
class CameraActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var img: ImageView
    private var ha: HaClient? = null
    private var entity = ""
    private var busy = false

    private val tick = object : Runnable {
        override fun run() {
            val h = ha
            if (h != null && !busy) {
                busy = true
                thread {
                    val bmp = h.snapshot(entity)
                    runOnUiThread {
                        if (bmp != null) img.setImageBitmap(bmp)
                        busy = false
                    }
                }
            }
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        entity = intent.getStringExtra("entity") ?: ""
        val cfg = ConfigStore.load(this)
        if (cfg.haUrl.isNotBlank() && cfg.haToken.isNotBlank()) ha = HaClient(cfg.haUrl, cfg.haToken)
        img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(Ui.pill(this@CameraActivity, "🔔  ${intent.getStringExtra("name") ?: entity}"),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START).apply {
                    leftMargin = Ui.dp(this@CameraActivity, 40); topMargin = Ui.dp(this@CameraActivity, 30)
                })
        }
        setContentView(root)
    }

    override fun onResume() { super.onResume(); ui.post(tick) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick) }
}
