package dev.chesscrab.app.ui.review

import dev.chesscrab.app.ui.board.FLASH_BAD
import dev.chesscrab.app.ui.board.FLASH_GOOD

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.ui.board.Flash
import dev.chesscrab.app.ui.board.Sounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.chesscrab_ffi.AnalysisProgress
import uniffi.chesscrab_ffi.ChessGame
import uniffi.chesscrab_ffi.analyzePgn

enum class Phase { Summary, Steps }

/** "Daha iyisini dene": hatalı hamleden önceki pozisyonda en iyiyi bulmaya çalış. */
data class Retry(val ply: Int, val fen: String, val legal: List<String>, val whiteToMove: Boolean, val tries: Int = 0, val text: String, val ok: Boolean? = null, val arrow: String? = null, val shownFen: String? = null, val lastMove: String? = null)

/** Serbest keşif: incelenen pozisyondan istediğin hamleleri oyna, motor çizgileri anında. */
class Explore(val baseFen: String) {
    val game: ChessGame = ChessGame.fromFen(baseFen)
}

class ReviewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var pgn by mutableStateOf("")
    var gameId: String? = null
    var result by mutableStateOf<JSONObject?>(null)
        private set
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var depth by mutableIntStateOf(Crab.setting("depth", "14").toInt())
    var phase by mutableStateOf(Phase.Summary)
    var ply by mutableIntStateOf(0)
    var whiteBottom by mutableStateOf(true)
    var retry by mutableStateOf<Retry?>(null)
    var flash by mutableStateOf<Flash?>(null)
    var shake by mutableIntStateOf(0)
    private var flashToken = 0

    var explore by mutableStateOf<Explore?>(null)
    var exploreView by mutableStateOf<GameView?>(null)
    var exploreLines by mutableStateOf<JSONObject?>(null)
    private var evalJob: Job? = null
    var playingLine by mutableStateOf(false)

    val moves get() = result?.getJSONArray("moves")?.objects().orEmpty()
    val evals get() = result?.getJSONArray("evals")?.objects().orEmpty()

    fun tag(name: String): String? = Regex("\\[$name \"([^\"]*)\"\\]").find(pgn)?.groupValues?.get(1)

    /** Oyun ekranından: canlı koçun hazırladığı inceleme varsa anında aç */
    fun open(text: String, reviewJson: String?, id: String?) {
        pgn = text; gameId = id; error = null; retry = null; explore = null
        phase = Phase.Summary; ply = 0
        if (reviewJson != null) { result = JSONObject(reviewJson); orient() } else { result = null; run() }
    }

    fun reset() { result = null; pgn = ""; error = null; retry = null; explore = null }

    private fun orient() {
        // Oyuncu siyahsa tahtayı çevir (PGN'de "Sen"/kullanıcı adı siyah tarafta)
        val me = dev.chesscrab.app.SyncClient.username.ifEmpty { "Sen" }
        whiteBottom = tag("Black") != me
    }

    fun run() {
        if (running || pgn.isBlank()) return
        running = true; error = null; progress = 0f
        Crab.setSetting("depth", depth.toString())
        val text = pgn
        scope.launch {
            try {
                val cb = object : AnalysisProgress {
                    override fun onProgress(done: UInt, total: UInt) { progress = done.toFloat() / total.toFloat() }
                }
                val json = withContext(Dispatchers.Default) { analyzePgn(text, depth.toUInt(), 0u, cb) }
                result = JSONObject(json); phase = Phase.Summary; ply = 0; orient()
                gameId?.let { id ->
                    Crab.store.get("games", id)?.let { g ->
                        val o = JSONObject(g); val r = result!!
                        r.getJSONObject("white").opt("accuracy")?.let { o.put("white_accuracy", it) }
                        r.getJSONObject("black").opt("accuracy")?.let { o.put("black_accuracy", it) }
                        Crab.store.put("games", id, o.toString())
                        dev.chesscrab.app.Streak.mark()
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally { running = false }
        }
    }

    fun go(p: Int) {
        retry = null; closeExplore()
        val n = moves.size
        val np = p.coerceIn(0, n)
        if (np == ply + 1 && np > 0) Sounds.forSan(moves[np - 1].getString("san"))
        ply = np
    }

    /** Oyuncunun ilk hatasına git ve "daha iyisini dene"yi başlat */
    fun learnFromMistakes(myWhite: Boolean) {
        phase = Phase.Steps
        val i = moves.indexOfFirst { it.getBoolean("white") == myWhite && it.getString("class") in BAD }
        if (i >= 0) { go(i + 1); startRetry() } else go(1)
    }

    fun nextMistake(myWhite: Boolean) {
        val i = moves.withIndex().firstOrNull { (k, mv) -> k + 1 > ply && mv.getBoolean("white") == myWhite && mv.getString("class") in BAD }?.index
        if (i != null) { go(i + 1); startRetry() } else { retry = null }
    }

    fun startRetry() {
        if (ply == 0) return
        val before = evals[ply - 1].getString("fen")
        val g = ChessGame.fromFen(before)
        val v = GameView.parse(g.stateJson())
        val mv = moves[ply - 1]
        retry = Retry(ply, before, v.legal, v.turnWhite, text = "${mv.getString("san")} yerine daha iyi bir hamle bul. İpucu: ${hintFor(mv.optStr("best_san"))}")
    }

    private fun hintFor(san: String?): String = when (san?.firstOrNull()) {
        null -> "dikkatli bak."
        'N' -> "bir at hamlesi."; 'B' -> "bir fil hamlesi."; 'R' -> "bir kale hamlesi."; 'Q' -> "bir vezir hamlesi."; 'K' -> "şahını oynat."; 'O' -> "rok."
        else -> "bir piyon hamlesi."
    }

    fun retryMove(uci: String) {
        val r = retry ?: return
        val g = ChessGame.fromFen(r.fen)
        val after = runCatching { GameView.parse(g.play(uci)) }.getOrNull() ?: return
        retry = r.copy(shownFen = after.fen, lastMove = uci, legal = emptyList(), text = "Bakıyorum…", ok = null)
        scope.launch {
            val c = JSONObject(withContext(Dispatchers.Default) { Crab.reviewCoach.compare(r.fen, uci) })
            val loss = c.getInt("loss_cp")
            val good = c.getBoolean("is_best") || loss <= 30
            flashToken++
            if (good) {
                Sounds.play(Sounds.S.CORRECT)
                flash = Flash(uci.substring(2, 4), FLASH_GOOD, flashToken)
                retry = r.copy(shownFen = after.fen, lastMove = uci, legal = emptyList(), ok = true,
                    text = if (c.getBoolean("is_best")) "Harika! En iyi hamleyi buldun: ${after.sans.lastOrNull() ?: uci}." else "Güzel! ${after.sans.lastOrNull()} da neredeyse en iyisi kadar iyi (en iyisi ${c.optStr("best_san")}).")
            } else {
                Sounds.play(Sounds.S.WRONG)
                flash = Flash(uci.substring(2, 4), FLASH_BAD, flashToken); shake++
                val tries = r.tries + 1
                val pawns = String.format(java.util.Locale.US, "%.1f", loss / 100.0)
                delay(650)
                val reveal = tries >= 2
                retry = r.copy(tries = tries, shownFen = null, lastMove = null, ok = false,
                    arrow = if (reveal) c.optStr("best_uci") else null,
                    legal = GameView.parse(ChessGame.fromFen(r.fen).stateJson()).legal,
                    text = if (reveal) "Bu da olmadı (−$pawns piyon). Doğrusu ${c.optStr("best_san")}: okla gösterdim, oyna ve gör." else "Olmadı, bu hamle yaklaşık $pawns piyon kaybettiriyor. Bir daha dene!")
            }
        }
    }

    fun closeRetry() { retry = null }

    fun startExplore(fen: String) {
        retry = null
        val e = Explore(fen)
        explore = e
        exploreView = GameView.parse(e.game.stateJson())
        evaluateExplore()
    }

    fun exploreMove(uci: String) {
        val e = explore ?: return
        runCatching { exploreView = GameView.parse(e.game.play(uci)) }
        Sounds.forSan(exploreView?.sans?.lastOrNull())
        evaluateExplore()
    }

    fun exploreUndo() {
        val e = explore ?: return
        if (e.game.undo()) { exploreView = GameView.parse(e.game.stateJson()); evaluateExplore() }
    }

    private fun evaluateExplore() {
        val fen = exploreView?.fen ?: return
        exploreLines = null
        evalJob?.cancel()
        evalJob = scope.launch {
            val j = withContext(Dispatchers.Default) { Crab.evalEngine.evaluate(fen, 16u, 3u, 1500uL) }
            if (exploreView?.fen == fen) exploreLines = JSONObject(j)
        }
    }

    /** Pozisyondan başlayıp en iyi devamı tahtada oynat */
    fun playLine(fen: String, sans: List<String>) {
        if (sans.isEmpty()) return
        startExplore(fen)
        playingLine = true
        scope.launch {
            for (s in sans.take(8)) {
                delay(700)
                val e = explore ?: break
                val ok = runCatching { exploreView = GameView.parse(e.game.play(s)) }.isSuccess
                if (!ok) break
                Sounds.forSan(s)
            }
            playingLine = false
            evaluateExplore()
        }
    }

    fun closeExplore() { explore = null; exploreView = null; exploreLines = null; evalJob?.cancel() }

    companion object {
        val BAD = setOf("inaccuracy", "mistake", "miss", "blunder")
    }
}
