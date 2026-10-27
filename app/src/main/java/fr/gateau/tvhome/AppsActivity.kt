package fr.gateau.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

class AppsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        val c = this
        val pm = packageManager
        val pkgs = linkedSetOf<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            pm.queryIntentActivities(q, 0).forEach { pkgs.add(it.activityInfo.packageName) }
        }
        pkgs.remove(packageName)
        val sorted = pkgs.mapNotNull { p -> Ui.label(c, p)?.let { p to it } }
            .sortedBy { it.second.lowercase() }

        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 56), Ui.dp(c, 32), Ui.dp(c, 56), Ui.dp(c, 32))
            clipChildren = false
        }
        content.addView(Ui.text(c, "Toutes les applis", 40f, android.graphics.Color.WHITE, Ui.THIN).apply { setPadding(Ui.dp(c, 12), 0, 0, Ui.dp(c, 12)) })

        var row: LinearLayout? = null
        sorted.forEachIndexed { i, (pkg, _) ->
            if (i % 6 == 0) {
                row = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14))
                    clipChildren = false
                    clipToPadding = false
                }
                content.addView(row)
            }
            Ui.appCard(c, pkg, 176, 99)?.let { row?.addView(it) }
        }

        val scroll = ScrollView(c).apply {
            clipChildren = false
            addView(content)
        }
        setContentView(FrameLayout(c).apply {
            background = Ui.background()
            addView(scroll)
        })
    }
}
