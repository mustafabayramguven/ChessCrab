package dev.chesscrab.app.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.Streak
import dev.chesscrab.app.objects
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.Display
import dev.chesscrab.app.ui.Mono
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.board.Flash
import dev.chesscrab.app.ui.board.Sounds
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.Confetti
import dev.chesscrab.app.ui.components.CountUpInt
import dev.chesscrab.app.ui.components.Overline
import dev.chesscrab.app.ui.components.WaveBackdrop
import dev.chesscrab.app.ui.components.staggerIn
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

/**
 * Bulmaca Fırtınası: 3 dakikada çözebildiğin kadar bulmaca, 3 can.
 * Zaman çubuğu erir, doğru çözümde skor zıplar, yanlışta bir yengeç gider.
 */
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

    Box(modifier.fillMaxSize()) {
        when (phase) {
            "ready" -> RushIntro(best, onExit) {
                score = 0; lives = LIVES; left = RUSH_MS; queue = rushQueue(); qi = 0; phase = "run"
            }
            "run" -> RushRun(
                puzzle = queue[qi % queue.size], key = qi, score = score, lives = lives, left = left, onExit = onExit,
                onSolved = { score++; qi++ },
                onFailed = { lives--; if (lives <= 0) finish() else qi++ },
            )
            else -> RushOver(score, best, record, onAgain = { phase = "ready" }, onExit = onExit)
        }
    }
}

@Composable
private fun RushIntro(best: Int, onExit: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("✕", fontSize = 20.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onExit).padding(vertical = 6.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Crabs.Card)
                .background(Brush.linearGradient(listOf(Crabs.Gold.copy(alpha = 0.28f), Crabs.Coral.copy(alpha = 0.10f), Color.Transparent)))
                .border(1.dp, Crabs.Line, RoundedCornerShape(28.dp)),
        ) {
            WaveBackdrop(Modifier.matchParentSize(), color = Crabs.Gold, lines = 5)
            Column(Modifier.padding(22.dp)) {
                Overline("Bulmaca fırtınası", color = Crabs.Gold)
                Spacer(Modifier.height(8.dp))
                Text("3 dakika.\n3 can.\nKaç tane?", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text("Bulmacalar kolaydan zora gider. Her doğru çözüm +1, her yanlış bir yengeç götürür.", color = Crabs.Muted, fontSize = 13.5.sp)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$best", fontFamily = Display, fontSize = 44.sp, color = Crabs.Gold)
                    Spacer(Modifier.width(8.dp))
                    Text("rekorun", color = Crabs.Muted, modifier = Modifier.padding(bottom = 9.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        BigButton("Fırtınayı başlat", modifier = Modifier.staggerIn(1), onClick = onStart)
    }
}

@Composable
private fun RushRun(
    puzzle: JSONObject, key: Int, score: Int, lives: Int, left: Long, onExit: () -> Unit,
    onSolved: () -> Unit, onFailed: () -> Unit,
) {
    val solution = remember(key) { puzzle.getJSONArray("moves").strings() }
    val game = remember(key) { ChessGame.fromFen(puzzle.getString("fen")) }
    var view by remember(key) { mutableStateOf(GameView.parse(game.stateJson())) }
    val myWhite = remember(key) { view.turnWhite }
    var idx by remember(key) { mutableIntStateOf(0) }
    var busy by remember(key) { mutableStateOf(false) }
    var flash by remember(key) { mutableStateOf<Flash?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // üst şerit: kalan süre, skor, canlar
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✕", fontSize = 20.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onExit).padding(end = 14.dp))
            Text("%d:%02d".format(left / 60_000, (left / 1000) % 60), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = if (left < 30_000) Crabs.Coral else Crabs.Text)
            Spacer(Modifier.weight(1f))
            for (i in 0 until LIVES) {
                val alive = i < lives
                val sc by animateFloatAsState(if (alive) 1f else 0.6f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "life")
                Text("🦀", fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (alive) 1f else 0.25f })
            }
        }
        // eriyen zaman çubuğu
        val frac by animateFloatAsState(left / RUSH_MS.toFloat(), tween(100), label = "t")
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Crabs.Line)) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(Crabs.Coral, if (left < 30_000) Crabs.Coral else Crabs.Gold))))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("${if (myWhite) "Beyaz" else "Siyah"} oynar · bulmaca ${key + 1}")
                Text(puzzle.getString("lesson_title"), fontSize = 13.sp, color = Crabs.Muted, maxLines = 1)
            }
            // skor yukarı kayarak değişir
            AnimatedContent(score, transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.5f)) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            }, label = "score") { s ->
                Text("$s", fontFamily = Display, fontSize = 40.sp, color = Crabs.Gold)
            }
        }
        Box(Modifier.padding(horizontal = 8.dp)) {
            ChessBoard(
                fen = view.fen, whiteBottom = myWhite, legal = if (busy) emptyList() else view.legal, lastMove = view.lastMove, check = view.check,
                flash = flash, shake = shake,
                onMove = { uci ->
                    token++
                    if (uci == solution[idx]) {
                        view = GameView.parse(game.play(uci)); idx++
                        flash = Flash(uci.substring(2, 4), Crabs.Sea, token)
                        busy = true
                        scope.launch {
                            if (idx < solution.size) {
                                Sounds.forSan(view.sans.lastOrNull())
                                delay(380)
                                view = GameView.parse(game.play(solution[idx])); idx++
                                Sounds.forSan(view.sans.lastOrNull())
                                busy = false
                            } else {
                                Sounds.play(Sounds.S.CORRECT)
                                delay(420)
                                onSolved()
                            }
                        }
                    } else {
                        Sounds.play(Sounds.S.WRONG); shake++
                        flash = Flash(uci.substring(2, 4), Color(0xFFFF4F64), token)
                        busy = true
                        scope.launch { delay(650); onFailed() }
                    }
                },
            )
        }
    }
}

@Composable
private fun RushOver(score: Int, best: Int, record: Boolean, onAgain: () -> Unit, onExit: () -> Unit) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Overline(if (record) "Yeni rekor!" else "Fırtına dindi", color = if (record) Crabs.Gold else Crabs.Muted)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.size(180.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Crabs.Gold.copy(alpha = 0.35f), Crabs.Gold.copy(alpha = 0.04f))))
                    .border(2.dp, Crabs.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(CountUpInt(score), fontFamily = Display, fontSize = 72.sp, color = Crabs.Gold) }
            Spacer(Modifier.height(14.dp))
            Text("bulmaca çözdün", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
            Text("rekor: $best", color = Crabs.Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(26.dp))
            BigButton("Tekrar", onClick = onAgain)
            Spacer(Modifier.height(10.dp))
            BigButton("Kapat", color = Crabs.Card, onClick = onExit)
        }
        if (record && score > 0) Confetti()
    }
}
