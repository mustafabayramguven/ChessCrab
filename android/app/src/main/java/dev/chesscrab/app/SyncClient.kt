package dev.chesscrab.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** ChessCrab sunucusuyla konuşan küçük HTTP istemcisi. Veri birleştirme Rust'ta. */
object SyncClient {
    val server: String get() = Crab.setting("server").trimEnd('/')
    val token: String get() = Crab.setting("token")
    val username: String get() = Crab.setting("username")
    val loggedIn: Boolean get() = token.isNotEmpty() && server.isNotEmpty()

    private fun post(path: String, body: String, auth: Boolean): String {
        val c = URL(server + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        c.readTimeout = 60_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (auth) c.setRequestProperty("Authorization", "Bearer $token")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getString("error") }.getOrDefault("HTTP $code")
            throw RuntimeException(msg)
        }
        return text
    }

    /** kind: "login" | "register" */
    suspend fun auth(serverUrl: String, kind: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Crab.setSetting("server", serverUrl.trim().trimEnd('/'))
            val body = JSONObject().put("username", user).put("password", pass).put("device", "android").toString()
            val r = JSONObject(post("/api/auth/$kind", body, auth = false))
            Crab.setSetting("token", r.getString("token"))
            Crab.setSetting("username", r.getString("username"))
            // Yeni hesap/sunucu: yerel her şeyi gönder, sunucudakileri baştan al
            Crab.store.resetSync()
            sync().getOrThrow()
            r.getString("username")
        }
    }

    fun logout() {
        Crab.setSetting("token", "")
    }

    /** Senkronize et; yerelde değişen kayıt sayısını döndürür. */
    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!loggedIn) throw IllegalStateException("Önce giriş yap")
            val req = Crab.store.prepareSyncJson()
            val resp = post("/api/sync", req, auth = true)
            val n = Crab.store.applySyncJson(req, resp).toInt()
            Crab.setSetting("last_sync", System.currentTimeMillis().toString())
            n
        }
    }
}
