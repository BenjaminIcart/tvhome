package fr.gateau.tvhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import fr.iki.elonen.NanoHTTPD
import fr.iki.elonen.NanoHTTPD.IHTTPSession
import fr.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject

/**
 * Petit serveur HTTP sur la box, appelé par Home Assistant :
 *  GET  /state   -> {"screen_on":true,"app":"com.netflix.ninja","app_name":"Netflix",...}
 *  POST /notify  {"title":"...","message":"...","duration":8}
 *  POST /launch  {"package":"com.netflix.ninja"}
 *  POST /config  (contenu complet de config.json)
 */
class BridgeService : Service() {
    private var server: Bridge? = null
    private var port = 0

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wanted = ConfigStore.load(this).serverPort
        if (server == null || port != wanted) {
            server?.stop()
            server = try {
                Bridge(wanted).also { it.start(5000, true) }
            } catch (e: Exception) {
                null
            }
            port = wanted
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("bridge", "Pont Home Assistant", NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, "bridge")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Accueil TV")
            .setContentText("Pont Home Assistant actif")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun stateJson(): JSONObject {
        val pkg = AppTracker.foreground(this)
        return JSONObject()
            .put("screen_on", AppTracker.screenOn(this))
            .put("app", pkg ?: "inconnu")
            .put("app_name", pkg?.let { Ui.label(this, it) } ?: "inconnu")
            .put("usage_access", AppTracker.hasUsageAccess(this))
            .put("version", 1)
    }

    inner class Bridge(port: Int) : NanoHTTPD(port) {
        private fun json(status: Response.IStatus, text: String): Response =
            NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", text)

        private fun ok() = json(Response.Status.OK, """{"ok":true}""")

        private fun body(s: IHTTPSession): String {
            val len = s.headers["content-length"]?.toIntOrNull() ?: 0
            if (len <= 0) return ""
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = s.inputStream.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
            return String(buf, 0, read, Charsets.UTF_8)
        }

        override fun serve(s: IHTTPSession): Response {
            val ctx = this@BridgeService
            val key = ConfigStore.load(ctx).serverKey
            if (key.isNotEmpty()) {
                @Suppress("DEPRECATION")
                val given = s.headers["x-key"] ?: s.parms["key"]
                if (given != key) return json(Response.Status.UNAUTHORIZED, """{"error":"bad key"}""")
            }
            return try {
                when (s.uri) {
                    "/state" -> json(Response.Status.OK, stateJson().toString())
                    "/notify" -> {
                        val b = JSONObject(body(s).ifBlank { "{}" })
                        Notifier.show(ctx, b.optString("title", "Home Assistant"),
                            b.optString("message", ""), b.optInt("duration", 8))
                        ok()
                    }
                    "/launch" -> {
                        val b = JSONObject(body(s))
                        if (Ui.launch(ctx, b.getString("package"))) ok()
                        else json(Response.Status.NOT_FOUND, """{"error":"app introuvable"}""")
                    }
                    "/config" -> {
                        if (s.method != NanoHTTPD.Method.POST) {
                            json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"POST uniquement"}""")
                        } else {
                            ConfigStore.save(ctx, body(s))
                            ok()
                        }
                    }
                    else -> json(Response.Status.NOT_FOUND, """{"error":"inconnu"}""")
                }
            } catch (e: Exception) {
                json(Response.Status.BAD_REQUEST, JSONObject().put("error", e.toString()).toString())
            }
        }
    }
}
