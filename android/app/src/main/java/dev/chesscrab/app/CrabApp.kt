package dev.chesscrab.app

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.CrabEngine
import uniffi.chesscrab_ffi.SyncStore
import java.io.File

class CrabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crab.init(this)
    }
}

/** Uygulama genelindeki Rust nesneleri. */
object Crab {
    lateinit var store: SyncStore
        private set

    /** Oyun motoru (oyna ekranı). Analiz kendi motorlarını açar. */
    val engine: CrabEngine by lazy { CrabEngine(32u) }

    /** Analiz tahtası için ayrı motor (oyun motoruyla çakışmasın). */
    val evalEngine: CrabEngine by lazy { CrabEngine(32u) }

    fun init(app: Application) {
        store = SyncStore.open(File(app.filesDir, "chesscrab.db").absolutePath)
    }

    fun setting(key: String, def: String = ""): String = store.meta("pref.$key") ?: def
    fun setSetting(key: String, value: String) = store.setMeta("pref.$key", value)
}

// ---- JSON yardımcıları ----

fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
fun JSONObject.optStr(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

/** Rust GameState JSON'ının Kotlin karşılığı */
data class GameView(
    val fen: String,
    val turnWhite: Boolean,
    val legal: List<String>,
    val sans: List<String>,
    val ucis: List<String>,
    val check: Boolean,
    val over: Boolean,
    val winner: String?,
    val result: String,
    val termination: String?,
    val opening: String?,
    val pgn: String,
) {
    val lastMove: String? get() = ucis.lastOrNull()

    companion object {
        fun parse(json: String): GameView {
            val o = JSONObject(json)
            val st = o.getJSONObject("status")
            val op = o.optJSONObject("opening")
            return GameView(
                fen = o.getString("fen"),
                turnWhite = o.getString("turn") == "white",
                legal = o.getJSONArray("legal").strings(),
                sans = o.getJSONArray("sans").strings(),
                ucis = o.getJSONArray("ucis").strings(),
                check = o.getBoolean("check"),
                over = st.getString("state") != "ongoing",
                winner = st.optStr("winner"),
                result = o.getString("result"),
                termination = o.optStr("termination"),
                opening = op?.let { "${it.getString("eco")} · ${it.getString("name")}" },
                pgn = o.getString("pgn"),
            )
        }
    }
}

fun fmtScore(score: JSONObject?): String {
    if (score == null) return ""
    val v = score.optInt("value")
    return if (score.optString("type") == "mate") {
        if (v == 0) "#" else if (v > 0) "M$v" else "-M${-v}"
    } else {
        (if (v >= 0) "+" else "") + String.format(java.util.Locale.US, "%.2f", v / 100.0)
    }
}
