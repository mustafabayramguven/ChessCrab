package dev.chesscrab.app.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.Streak
import dev.chesscrab.app.objects
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.board.*
import dev.chesscrab.app.ui.kare.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.ChessGame
import uniffi.chesscrab_ffi.puzzlesJson

private const val RUSH_MS = 180_000L
private const val LIVES = 3

/** Bulmaca sırası: kısadan uzuna, her turda karışık */
private fun rushQueue(): List<JSONObject> =
    JSONArray(puzzlesJson()).objects().shuffled().sortedBy { it.getJSONArray("moves").length() }

/** Bulmaca Fırtınası: 3 dakika, 3 can; süre izi erir, doğru çözümde skor yukarı kayar */
@Composable
fun RushScreen(modifier: Modifier, onExit: () -> Unit, startRunning: Boolean = false) {
    var phase by remember { mutableStateOf(if (startRunning) "run" else "ready") }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(LIVES) }
    var left by remember { mutableLongStateOf(RUSH_MS) }
    var queue by remember { mutableStateOf(rushQueue()) }
    var qi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(runCatching { Crab.setting("rush_best", "0").toInt() }.getOrDefault(0)) }
    var record by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Boolean>() }

    fun finish() {
        if (phase != "run") return
        phase = "over"
        record = score > best
        if (record) { best = score; runCatching { Crab.setSetting("rush_best", "$score") } }
        Streak.mark()
        Sounds.play(Sounds.S.END)
    }

    LaunchedEffect(phase) {
        if (phase == "run") {
            while (left > 0 && phase == "run") { delay(100); left -= 100 }
            finish()
        }
    }

    Column(modifier.fillMaxSize()) {
        TopBar("Fırtına", "STR 180", onBack = onExit)
        when (phase) {
            "ready" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().background(K.Surface).hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BULMACA FIRTINASI", style = KType.headline, color = K.Ink, modifier = Modifier.weight(1f))
                        Tag("3 dk · 3 can")
                    }
                    Text("Bulmacalar kolaydan zora gider. Her doğru çözüm +1; her yanlış bir can götürür. Süre bitince ya da canlar tükenince fırtına diner.", style = KType.desc, color = K.Ink2)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatLine("Rekor", "$best")
                        StatLine("Havuz", "${queue.size} bulmaca")
                    }
                    SavilleButton("Fırtınayı başlat", Modifier.fillMaxWidth(), cap = K.Red) {
                        score = 0; lives = LIVES; left = RUSH_MS; queue = rushQueue(); qi = 0; results.clear(); phase = "run"
                    }
                }
            }
            "run" -> RushRun(
                puzzle = queue[qi % queue.size], key = qi, score = score, lives = lives, left = left, results = results,
                onSolved = { score++; results.add(true); qi++ },
                onFailed = { lives--; results.add(false); if (lives <= 0) finish() else qi++ },
                modifier = Modifier.weight(1f),
            )
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().background(K.Surface).hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (record) "Yeni rekor" else "Fırtına dindi").upTr(), style = KType.headline, color = K.Ink, modifier = Modifier.weight(1f))
                        CatalogCode(if (record) "!! rekor" else "sonuç", color = if (record) K.Ink else K.Ink2, border = if (record) K.Ink else K.LineSubtle)
                    }
                    Text(countUp(score.toFloat(), 0), style = KType.mono.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold), color = K.Ink)
                    StatLine("Çözülen", "$score")
                    StatLine("Yanlış", "${results.count { !it }}")
                    StatLine("Rekor", "$best")
                    Spacer(Modifier.height(4.dp))
                    SavilleButton("Tekrar", Modifier.fillMaxWidth(), cap = K.Red) { phase = "ready" }
                    SavilleButton("Kapat", Modifier.fillMaxWidth(), cap = K.Industrial, onClick = onExit)
                }
            }
        }
    }
}

@Composable
private fun RushRun(
    puzzle: JSONObject, key: Int, score: Int, lives: Int, left: Long, results: List<Boolean>,
    onSolved: () -> Unit, onFailed: () -> Unit, modifier: Modifier,
) {
    val solution = remember(key) { puzzle.getJSONArray("moves").strings() }
    val game = remember(key) { ChessGame.fromFen(puzzle.getString("fen")) }
    var view by remember(key) { mutableStateOf(GameView.parse(game.stateJson())) }
    val myWhite = remember(key) { view.turnWhite }
    var idx by remember(key) { mutableIntStateOf(0) }
    var busy by remember(key) { mutableStateOf(false) }
    var flash by remember(key) { mutableStateOf<Flash?>(null) }
    var fx by remember { mutableStateOf<BoardFx?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val urgent = left < 30_000

    Column(modifier.verticalScroll(rememberScrollState())) {
        // sinyal okuma: süre, skor, canlar
        Row(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d:%02d".format(left / 60_000, (left / 1000) % 60), style = KType.clock.copy(fontSize = 20.sp), color = if (urgent) K.Red else K.Ink)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bulmaca ${key + 1} · ${if (myWhite) "Beyaz" else "Siyah"} oynar", style = KType.bodyCompact, color = K.Ink, maxLines = 1)
                Text(puzzle.getString("lesson_title"), style = KType.desc.copy(fontSize = 11.sp), color = K.Ink2, maxLines = 1)
            }
            AnimatedContent(score, transitionSpec = {
                if (Motion.reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                else (slideInVertically(tween(Motion.STATE, easing = Motion.Expo)) { it } + fadeIn()) togetherWith (slideOutVertically(tween(Motion.STATE)) { -it } + fadeOut())
            }, label = "score") { s ->
                Text("$s", style = KType.mono.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = K.Ink, modifier = Modifier.padding(horizontal = 8.dp))
            }
            // canlar: dolu = tebeşir, kayıp = kızıl çerçeve
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until LIVES) {
                    val alive = i < lives
                    Box(Modifier.size(12.dp).background(if (alive) K.Ink else K.CellOff).border(1.dp, if (alive) K.Ink else K.Red))
                }
            }
        }
        // eriyen süre izi
        ProgressTrack(left / RUSH_MS.toFloat(), color = if (urgent) K.Red else K.Yellow)
        // son sonuçlar şeridi
        Row(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val last = results.takeLast(20)
            for (r in last) Box(Modifier.size(width = 10.dp, height = 6.dp).background(if (r) K.Ink else K.Red))
            if (last.isEmpty()) Text("SONUÇ ŞERİDİ", style = KType.code, color = K.Ink3)
        }
        ChessBoard(
            fen = view.fen, whiteBottom = myWhite, legal = if (busy) emptyList() else view.legal, lastMove = view.lastMove, check = view.check,
            flash = flash, fx = fx, shake = shake,
            onMove = { uci ->
                token++
                if (uci == solution[idx]) {
                    view = GameView.parse(game.play(uci)); idx++
                    flash = Flash(uci.substring(2, 4), FLASH_GOOD, token)
                    busy = true
                    scope.launch {
                        if (idx < solution.size) {
                            Sounds.forSan(view.sans.lastOrNull())
                            delay(if (Motion.reduced) 150 else 380)
                            view = GameView.parse(game.play(solution[idx])); idx++
                            Sounds.forSan(view.sans.lastOrNull())
                            busy = false
                        } else {
                            Sounds.play(Sounds.S.CORRECT)
                            fx = BoardFx(uci.substring(2, 4), "brilliant", token)
                            delay(if (Motion.reduced) 150 else 420)
                            onSolved()
                        }
                    }
                } else {
                    Sounds.play(Sounds.S.WRONG); shake++
                    flash = Flash(uci.substring(2, 4), FLASH_BAD, token)
                    busy = true
                    scope.launch { delay(if (Motion.reduced) 200 else 650); onFailed() }
                }
            },
        )
        CoachStrip(
            if (results.lastOrNull() == false) "Bir can gitti" else "Taktiği bul",
            puzzle.optString("prompt").ifBlank { "En iyi hamleyi oyna." },
            code = when (results.lastOrNull()) { true -> "brilliant"; false -> "blunder"; null -> null },
        )
    }
}
