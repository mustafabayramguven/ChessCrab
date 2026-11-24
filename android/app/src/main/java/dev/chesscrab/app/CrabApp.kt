package dev.chesscrab.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.CrabEngine
import uniffi.chesscrab_ffi.LiveCoach
import uniffi.chesscrab_ffi.SyncStore
import java.io.File

class CrabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crab.init(this)
        dev.chesscrab.app.ui.Prefs.load()
    }
}

/** Uygulama genelindeki Rust nesneleri. */
object Crab {
    lateinit var store: SyncStore
        private set

    /** Oyun motoru (oyna ekranı). Analiz kendi motorlarını açar. */
    val engine: CrabEngine by lazy { CrabEngine(32u) }

    /** Analiz tahtası / keşif için ayrı motor (oyun motoruyla çakışmasın). */
    val evalEngine: CrabEngine by lazy { CrabEngine(32u) }

    /** Canlı koç: oyunda her hamleyi anında değerlendirir. */
    val coach: LiveCoach by lazy { LiveCoach(setting("coach_depth", "12").toUInt()) }

    /** İnceleme ekranındaki "daha iyisini dene" karşılaştırmaları için */
    val reviewCoach: LiveCoach by lazy { LiveCoach(12u) }

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

/** Günlük seri: etkinlik olan günler (oyun, ders, bulmaca, tekrar) */
object Streak {
    /** Değişince ekranlar yenilensin diye sayaç */
    var version by androidx.compose.runtime.mutableIntStateOf(0)

    fun today(): Long = java.time.LocalDate.now().toEpochDay()

    fun days(): Set<Long> = runCatching { Crab.setting("active_days").split(',').mapNotNull { it.trim().toLongOrNull() }.toSet() }.getOrDefault(emptySet())

    fun mark() {
        runCatching {
            val d = days()
            if (today() in d) return
            Crab.setSetting("active_days", (d + today()).sorted().takeLast(400).joinToString(","))
            version++
        }
    }

    /** Bugün (ya da henüz bugün oynanmadıysa dün) biten ardışık gün sayısı */
    fun current(d: Set<Long> = days()): Int {
        var t = today()
        if (t !in d) t -= 1
        var n = 0
        while (t in d) { n++; t-- }
        return n
    }

    fun best(d: Set<Long> = days()): Int {
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (x in d.sorted()) { run = if (x == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = x }
        return best
    }
}
