package dev.chesscrab.app.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.optStr
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.board.Sounds
import dev.chesscrab.app.ui.components.winPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.chesscrab_ffi.ChessGame
import java.util.UUID

data class Bot(val level: Int, val name: String, val elo: Int, val emoji: String, val color: Color, val blurb: String)

val BOTS = listOf(
    Bot(1, "Yavru Yengeç", 400, "🦀", Color(0xFFFF7A59), "Taşlar nasıl gidiyordu? Birlikte öğrenelim!"),
    Bot(2, "Kaplumbağa Kemal", 700, "🐢", Color(0xFF6FB98A), "Acelem yok… ama arada taş bırakırım."),
    Bot(3, "Martı Mert", 1000, "🐦", Color(0xFF7FB2FF), "Açıkta kalan taşları kaparım!"),
    Bot(4, "Ahtapot Ayla", 1300, "🐙", Color(0xFFD07BB4), "Sekiz kolum var, çatallara dikkat."),
    Bot(5, "Denizatı Deniz", 1600, "🐴", Color(0xFFF2C46D), "Açılışlarımı bilirim, orta oyunda görüşürüz."),
    Bot(6, "Kılıçbalığı Kaan", 1900, "🐟", Color(0xFF54D1BE), "Keskin taktikler, keskin saldırılar."),
    Bot(7, "Köpekbalığı Selin", 2300, "🦈", Color(0xFF8FA3B8), "Bir hata yap, gerisini ben hallederim."),
    Bot(8, "CrabFish", 3000, "👑", Color(0xFFF4E8CF), "Tam güç. Bol şans."),
)

data class TimeCtl(val label: String, val baseMs: Long, val incMs: Long)

val TIME_CONTROLS = listOf(
    TimeCtl("Süresiz", 0, 0), TimeCtl("3+2", 180_000, 2_000), TimeCtl("5+0", 300_000, 0),
    TimeCtl("10+0", 600_000, 0), TimeCtl("15+10", 900_000, 10_000),
)

/** Koçun konuşma balonu içeriği */
data class CoachSay(val text: String, val cls: String? = null, val title: String? = null)

/** Oyun + canlı koç durumu. Uygulama boyunca yaşar (sekme değişince kaybolmaz). */
class PlayModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coachMutex = Mutex()

    var game: ChessGame = ChessGame()
        private set
    var view by mutableStateOf(GameView.parse(game.stateJson()))
        private set
    var bot by mutableStateOf(BOTS[(Crab.setting("level", "4").toIntOrNull() ?: 4).coerceIn(1, 8) - 1])
    var engineWhite by mutableStateOf(false)
    var whiteBottom by mutableStateOf(true)
    var thinking by mutableStateOf(false)
    var started by mutableStateOf(false)
    var timeCtl by mutableStateOf(TIME_CONTROLS[0])
    var whiteMs by mutableLongStateOf(0L)
    var blackMs by mutableLongStateOf(0L)
    private var turnStart = 0L
    var coachOn by mutableStateOf(Crab.setting("coach", "1") == "1")

    /** reviews[i] = (i+1). yarı hamlenin koç değerlendirmesi (MoveAnalysis JSON) */
    val reviews = mutableStateListOf<JSONObject?>()
    /** Her pozisyonun beyaz kazanma yüzdesi (0 = başlangıç) */
    val wins = mutableStateListOf(50f)
    var whiteCp by mutableIntStateOf(20)
        private set
    var say by mutableStateOf(CoachSay("Merhaba! Ben koçun. Her hamleni anında değerlendireceğim. Hazır olunca başla."))
    var hintArrow by mutableStateOf<String?>(null)
    private var hintLevel = 0
    var shake by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var finalReview by mutableStateOf<String?>(null)
    var id: String = newId()
        private set
    private var saved = false
    private var hintsUsed = 0

    private fun newId() = UUID.randomUUID().toString().replace("-", "")

    fun refresh() { view = GameView.parse(game.stateJson()) }

    fun newGame(b: Bot, meWhite: Boolean, tc: TimeCtl) {
        Crab.engine.stop()
        bot = b; timeCtl = tc
        Crab.setSetting("level", b.level.toString())
        game = ChessGame()
        Crab.engine.newGame()
        engineWhite = !meWhite
        whiteBottom = meWhite
        id = newId(); saved = false; hintsUsed = 0
        started = true; gameOver = false; finalReview = null
        reviews.clear(); wins.clear(); wins.add(50f); whiteCp = 20
        hintArrow = null; hintLevel = 0
        whiteMs = tc.baseMs; blackMs = tc.baseMs
        turnStart = System.currentTimeMillis()
        say = CoachSay("${b.name}: \"${b.blurb}\"", title = "${b.emoji} ${b.name} (${b.elo})")
        refresh()
        if (engineWhite) scope.launch { engineMove() }
    }

    fun remaining(white: Boolean): Long {
        val base = if (white) whiteMs else blackMs
        return if (!view.over && started && view.turnWhite == white && view.ucis.isNotEmpty()) base - (System.currentTimeMillis() - turnStart) else base
    }

    private fun spendClock(): Long? {
        if (timeCtl.baseMs == 0L) return null
        val now = System.currentTimeMillis()
        val used = now - turnStart
        turnStart = now
        return if (view.turnWhite) { whiteMs = (whiteMs - used).coerceAtLeast(0) + timeCtl.incMs; whiteMs }
        else { blackMs = (blackMs - used).coerceAtLeast(0) + timeCtl.incMs; blackMs }
    }

    val myTurn: Boolean get() = started && !view.over && view.turnWhite != engineWhite && !thinking

    fun userMove(uci: String) {
        if (!myTurn) return
        hintArrow = null; hintLevel = 0
        play(uci, byUser = true)
        if (!view.over) scope.launch { engineMove() }
    }

    private fun play(uci: String, byUser: Boolean) {
        val clk = spendClock()
        val ok = runCatching { if (clk != null) game.playWithClock(uci, clk.toULong()) else game.play(uci) }.isSuccess
        if (!ok) return
        refresh()
        Sounds.forSan(view.sans.lastOrNull())
        while (reviews.size > view.sans.size) reviews.removeAt(reviews.size - 1)
        while (wins.size > view.sans.size + 1) wins.removeAt(wins.size - 1)
        requestReview(byUser)
        if (view.over) onGameOver()
    }

    private fun requestReview(byUser: Boolean) {
        if (!coachOn) return
        val snap = game.copy()
        val ply = snap.ply().toInt()
        val prevCls = reviews.getOrNull(ply - 2)?.optStr("class")
        if (byUser) say = CoachSay("Hamleni inceliyorum…", title = "Koç")
        scope.launch {
            val json = withContext(Dispatchers.Default) { coachMutex.withLock { Crab.coach.reviewLast(snap, prevCls) } } ?: return@launch
            if (game.ply().toInt() < ply) return@launch // bu arada geri alındı
            val o = JSONObject(json)
            val mv = o.getJSONObject("move")
            while (reviews.size < ply) reviews.add(null)
            reviews[ply - 1] = mv
            while (wins.size < ply + 1) wins.add(wins.last())
            wins[ply] = winPercent(o.getInt("white_cp"))
            if (game.ply().toInt() == ply) whiteCp = o.getInt("white_cp")
            if (byUser) {
                val cls = mv.getString("class")
                say = CoachSay(mv.getString("comment"), cls = cls)
                if (cls == "blunder" || cls == "mistake") shake++
            }
        }
    }

    private suspend fun engineMove() {
        if (view.over || view.turnWhite != engineWhite) return
        thinking = true
        val movetime = if (timeCtl.baseMs > 0) (remaining(engineWhite) / 30).coerceIn(250, 3000) else 450L
        val g = game
        val t0 = System.currentTimeMillis()
        val mv = try {
            val r = withContext(Dispatchers.Default) { Crab.engine.bestMove(g, bot.level.toUByte(), movetime.toULong()) }
            // Doğal his için en az ~0.5 sn "düşün" (animasyon da görünsün)
            if (timeCtl.baseMs == 0L) kotlinx.coroutines.delay((550 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            r
        } finally { thinking = false }
        if (mv != null && g === game && !view.over) {
            play(JSONObject(mv).getString("uci"), byUser = false)
        }
    }

    /** İpucu: önce hangi taş, ikinci basışta tam hamle */
    fun hint() {
        if (!myTurn) return
        val snap = game.copy()
        hintsUsed++
        scope.launch {
            val h = JSONObject(withContext(Dispatchers.Default) { coachMutex.withLock { Crab.coach.hint(snap) } })
            if (snap.ply() != game.ply()) return@launch
            val uci = h.optStr("best_uci") ?: return@launch
            val san = h.optStr("best_san") ?: uci
            hintLevel++
            if (hintLevel == 1) {
                hintArrow = null
                val piece = when (san.first()) { 'N' -> "atını"; 'B' -> "filini"; 'R' -> "kaleni"; 'Q' -> "vezirini"; 'K' -> "şahını"; 'O' -> "rok yapmayı"; else -> "piyonunu" }
                say = CoachSay("${uci.substring(0, 2)} karesindeki $piece düşün. Neyi tehdit edebilir?", title = "İpucu")
            } else {
                hintArrow = uci
                say = CoachSay("En iyi hamle $san. Devamı: ${h.getJSONArray("line_san").let { a -> List(minOf(a.length(), 5)) { a.getString(it) } }.joinToString(" ")}", title = "Çözüm")
            }
        }
    }

    fun undo() {
        if (thinking || view.sans.isEmpty() || view.over) return
        if (!game.undo()) return
        if (GameView.parse(game.stateJson()).turnWhite == engineWhite && game.ply() > 0u) game.undo()
        refresh()
        while (reviews.size > view.sans.size) reviews.removeAt(reviews.size - 1)
        while (wins.size > view.sans.size + 1) wins.removeAt(wins.size - 1)
        hintArrow = null; hintLevel = 0
        say = CoachSay("Geri aldık. Bu sefer daha iyisini bul!", title = "Koç")
        if (view.turnWhite == engineWhite && !view.over) scope.launch { engineMove() }
    }

    fun resign() {
        if (!started || view.over) return
        game.resign(!engineWhite); refresh(); onGameOver()
    }

    fun checkClock() {
        if (!started || timeCtl.baseMs == 0L || view.over || view.ucis.isEmpty()) return
        val white = view.turnWhite
        if (remaining(white) <= 0) { game.timeout(white); refresh(); onGameOver() }
    }

    private fun onGameOver() {
        Sounds.play(Sounds.S.END)
        save()
        gameOver = true
        val snap = game.copy()
        scope.launch {
            val r = withContext(Dispatchers.Default) { coachMutex.withLock { Crab.coach.fullReview(snap) } }
            finalReview = r
            // doğrulukları kaydet
            runCatching {
                val o = JSONObject(r)
                Crab.store.get("games", id)?.let { g ->
                    val d = JSONObject(g)
                    o.getJSONObject("white").opt("accuracy")?.let { d.put("white_accuracy", it) }
                    o.getJSONObject("black").opt("accuracy")?.let { d.put("black_accuracy", it) }
                    Crab.store.put("games", id, d.toString())
                }
            }
            if (SyncClient.loggedIn) SyncClient.sync()
        }
    }

    val resultText: String
        get() {
            val myColor = if (engineWhite) "black" else "white"
            return when (view.winner) {
                null -> "Berabere"
                myColor -> "Kazandın!"
                else -> "${bot.name} kazandı"
            }
        }

    private fun save() {
        if (saved || view.sans.size < 2) return
        saved = true
        val me = SyncClient.username.ifEmpty { "Sen" }
        val botName = "${bot.name} (${bot.elo})"
        val white = if (engineWhite) botName else me
        val black = if (engineWhite) me else botName
        game.setTag("White", white); game.setTag("Black", black)
        game.setTag("Date", java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US).format(java.util.Date()))
        if (timeCtl.baseMs > 0) game.setTag("TimeControl", "${timeCtl.baseMs / 1000}+${timeCtl.incMs / 1000}")
        refresh()
        val data = JSONObject()
            .put("id", id).put("pgn", game.pgn()).put("white", white).put("black", black)
            .put("result", view.result).put("mode", "engine").put("engine_level", bot.level)
            .put("created_ms", System.currentTimeMillis()).put("opening", view.opening ?: JSONObject.NULL)
        Crab.store.put("games", id, data.toString())
        dev.chesscrab.app.Streak.mark()
    }

    fun classAt(ply: Int): String? = reviews.getOrNull(ply - 1)?.optStr("class")

    /** Ekran görüntüsü testleri için: hazır bir oyun durumunu senkron olarak yükle */
    fun loadForPreview(moves: List<String>, b: Bot, analyse: Boolean) {
        newGame(b, true, TIME_CONTROLS[2])
        for (m in moves) { game.play(m) }
        refresh()
        if (analyse) {
            for (p in 1..view.sans.size) {
                val g2 = ChessGame()
                for (m in view.ucis.take(p)) g2.play(m)
                val o = JSONObject(Crab.coach.reviewLast(g2, reviews.lastOrNull()?.optStr("class"))!!)
                reviews.add(o.getJSONObject("move")); wins.add(winPercent(o.getInt("white_cp")))
                whiteCp = o.getInt("white_cp")
            }
            reviews.lastOrNull { it?.optBoolean("white") == true }?.let { say = CoachSay(it.getString("comment"), cls = it.getString("class")) }
        }
    }
}

fun classColor(cls: String?): Color = CLASS_INFO[cls]?.color ?: Color.Gray
