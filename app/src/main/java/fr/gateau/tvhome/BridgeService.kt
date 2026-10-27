package fr.gateau.tvhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Petit serveur HTTP sur la box, appelé par Home Assistant :
 *  GET  /state   -> {"screen_on":true,"app":"com.netflix.ninja","app_name":"Netflix",...}
 *  POST /notify  {"title":"...","message":"...","duration":8}
 *  POST /launch  {"package":"com.netflix.ninja"}
 *  POST /config  (contenu complet de config.json)
 */
private val HOME_TOKEN = Any()

class BridgeService : Service() {
    private var server: Bridge? = null
    private var port = 0

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Au réveil (ou au démarrage), la Bbox ouvre sa TV en direct : on ramène notre accueil. */
    private val screenOn = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { scheduleHome(3000) }
    }

    private fun scheduleHome(delay: Long) {
        main.removeCallbacksAndMessages(HOME_TOKEN)
        main.postAtTime({ goHomeIfBbox() }, HOME_TOKEN, android.os.SystemClock.uptimeMillis() + delay)
    }

    private fun goHomeIfBbox() {
        val cfg = ConfigStore.load(this)
        if (!cfg.powerOnHome) return
        val live = cfg.liveTvPackage.substringBefore('/')
        val fg = AppTracker.foreground(this)
        if (fg == null || fg == live || fg == "com.android.tv.settings") {
            try {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (e: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        Ui.init(this)
        startForeground(1, buildNotification())
        registerReceiver(screenOn, android.content.IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wanted = ConfigStore.load(this).serverPort
        if (server == null || port != wanted) {
            server?.stop()
            server = try {
                Bridge(wanted).also { it.start() }
            } catch (e: Exception) {
                null
            }
            port = wanted
        }
        if (intent?.getBooleanExtra("boot", false) == true) scheduleHome(8000)
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOn) } catch (e: Exception) {}
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

    private class Req(val method: String, val path: String, val query: Map<String, String>,
                      val headers: Map<String, String>, val body: String)

    /** Mini serveur HTTP/1.1 (une requête par connexion). */
    inner class Bridge(private val port: Int) {
        private var socket: ServerSocket? = null
        private val pool = Executors.newFixedThreadPool(4)

        fun start() {
            val ss = ServerSocket(port)
            socket = ss
            thread(isDaemon = true, name = "bridge") {
                while (!ss.isClosed) {
                    val client = try { ss.accept() } catch (e: Exception) { break }
                    pool.execute { handle(client) }
                }
            }
        }

        fun stop() {
            try { socket?.close() } catch (e: Exception) {}
            pool.shutdownNow()
        }

        private fun handle(client: Socket) {
            client.use { sock ->
                try {
                    sock.soTimeout = 5000
                    val input = BufferedInputStream(sock.getInputStream())
                    val req = parse(input) ?: return
                    val (code, text) = route(req)
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val reason = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Bad Request" }
                    val out = sock.getOutputStream()
                    out.write(("HTTP/1.1 $code $reason\r\nContent-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8))
                    out.write(bytes)
                    out.flush()
                } catch (e: Exception) { /* connexion ignorée */ }
            }
        }

        private fun readLine(input: BufferedInputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(b.toChar())
            }
        }

        private fun parse(input: BufferedInputStream): Req? {
            val first = readLine(input) ?: return null
            val parts = first.split(" ")
            if (parts.size < 2) return null
            val headers = mutableMapOf<String, String>()
            while (true) {
                val l = readLine(input) ?: break
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val len = headers["content-length"]?.toIntOrNull() ?: 0
            val buf = ByteArray(len.coerceIn(0, 1_000_000))
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }
                .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            return Req(parts[0].uppercase(), path, query, headers, String(buf, 0, read, Charsets.UTF_8))
        }

        private fun route(r: Req): Pair<Int, String> {
            val ctx = this@BridgeService
            val key = ConfigStore.load(ctx).serverKey
            if (key.isNotEmpty() && (r.headers["x-key"] ?: r.query["key"]) != key) {
                return 401 to """{"error":"bad key"}"""
            }
            val ok = 200 to """{"ok":true}"""
            return try {
                when (r.path) {
                    "/state" -> 200 to stateJson().toString()
                    "/notify" -> {
                        val b = JSONObject(r.body.ifBlank { "{}" })
                        Notifier.show(ctx, b.optString("title", "Home Assistant"),
                            b.optString("message", ""), b.optInt("duration", 8), b.optString("camera", ""))
                        ok
                    }
                    "/launch" -> {
                        val b = JSONObject(r.body)
                        if (Ui.launch(ctx, b.getString("package"))) ok
                        else 404 to """{"error":"app introuvable"}"""
                    }
                    "/config" -> {
                        if (r.method != "POST") 405 to """{"error":"POST uniquement"}"""
                        else { ConfigStore.save(ctx, r.body); ok }
                    }
                    else -> 404 to """{"error":"inconnu"}"""
                }
            } catch (e: Exception) {
                400 to JSONObject().put("error", e.toString()).toString()
            }
        }
    }
}
