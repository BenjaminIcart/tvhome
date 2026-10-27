package fr.gateau.tvhome

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import java.io.File

/** Infos système légères de la box (aucun coût notable). */
object SysInfo {
    /** RAM utilisée en %, et Mo disponibles. */
    fun ram(c: Context): Pair<Int, Long> {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val usedPct = if (mi.totalMem > 0) (100 - mi.availMem * 100 / mi.totalMem).toInt() else 0
        return usedPct to mi.availMem / (1024 * 1024)
    }

    /** Température max des zones thermiques lisibles, en °C (null si inaccessible). */
    fun tempC(): Double? {
        var best: Double? = null
        try {
            File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { z ->
                val raw = try { File(z, "temp").readText().trim().toDouble() } catch (e: Exception) { return@forEach }
                val t = if (raw > 1000) raw / 1000.0 else raw
                if (t in 5.0..130.0 && (best == null || t > best!!)) best = t
            }
        } catch (e: Exception) {}
        return best
    }

    fun uptimeMs(): Long = SystemClock.elapsedRealtime()

    fun uptimeText(): String {
        val m = uptimeMs() / 60_000
        val d = m / 1440; val h = (m % 1440) / 60; val mn = m % 60
        return when {
            d > 0 -> "${d} j ${h} h"
            h > 0 -> "${h} h ${mn} min"
            else -> "${mn} min"
        }
    }
}
