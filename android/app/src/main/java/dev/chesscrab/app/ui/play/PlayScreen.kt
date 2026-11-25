package dev.chesscrab.app.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.CODE_EXPLAIN
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.board.ARROW_HINT
import dev.chesscrab.app.ui.board.Arrow
import dev.chesscrab.app.ui.board.CapturedRow
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.board.SquareBadge
import dev.chesscrab.app.ui.board.Threats
import dev.chesscrab.app.ui.board.capturedPieces
import dev.chesscrab.app.ui.decodeOf
import dev.chesscrab.app.ui.kare.*
import kotlinx.coroutines.delay
import org.json.JSONObject

fun fmtClock(ms: Long): String {
    val m = ms.coerceAtLeast(0); val s = m / 1000
    return if (m < 10_000) "%02d:%02d.%d".format(s / 60, s % 60, (m % 1000) / 100) else "%02d:%02d".format(s / 60, s % 60)
}

private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/** Hamle akışı: kompakt mono çipler, son hamle tebeşir, kalite noktası */
@Composable
fun MoveStream(sans: List<String>, current: Int, classOf: (Int) -> String?, compact: Boolean = true, onClick: ((Int) -> Unit)? = null) {
    val state = rememberLazyListState()
    LaunchedEffect(sans.size, current) {
        if (sans.isEmpty()) return@LaunchedEffect
        val idx = (current - 3).coerceAtLeast(0)
        if (Motion.reduced) state.scrollToItem(idx) else state.animateScrollToItem(idx)
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sans.isEmpty()) item { Text("HAMLE AKIŞI · BOŞ", style = KType.code, color = K.Ink3, modifier = Modifier.padding(vertical = 8.dp)) }
        items(sans.size, key = { it }) { i ->
            val ply = i + 1
            val label = if (ply % 2 == 1) "${(ply + 1) / 2}. ${sans[i]}" else sans[i]
            val cls = classOf(ply)
            PlyChip(label, cls?.let { CLASS_INFO[it]?.color }, ply == current, compact = compact, onClick = onClick?.let { { it(ply) } })
        }
    }
}

@Composable
fun PlayScreen(m: PlayModel, modifier: Modifier, onNewGame: () -> Unit, onReview: (pgn: String, review: String?, id: String?) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(m.started, m.timeCtl) {
        while (m.timeCtl.baseMs > 0) { delay(200); tick++; m.checkClock() }
    }
    val v = m.view
    val (byWhite, byBlack, diff) = remember(v.fen) { capturedPieces(v.fen) }
    val me = SyncClient.username.ifEmpty { "Sen" }
    val myWhite = !m.engineWhite
    var decodeShown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(m.say) { decodeShown = null }

    Column(modifier.fillMaxSize()) {
        TopBar("Oyna", "KRE 042", trailing = {
            Box(Modifier.size(TOUCH).press(scale = 0.9f, onClick = onNewGame), contentAlignment = Alignment.Center) {
                Icon(KIcons.Plus, "Yeni oyun", tint = K.Ink, modifier = Modifier.size(20.dp))
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!m.started) {
                PlayerStrip("?", false, "Rakip bekleniyor", "8 rakip · 400–3000 ELO", null, running = false, active = false)
                ChessBoard(START_FEN, true, emptyList())
                PlayerStrip(me.take(1), true, me, "Beyaz · çevrimdışı", null, running = false, active = true)
                CoachStrip("Rakibini seç", "Canlı koç her hamleni anında kodlar: !! ! ?! ??. Tehdit altındaki taşların kızıl kesikli kutuyla görünür.") {
                    SavilleButton("Yeni oyun", cap = K.Red, onClick = onNewGame)
                }
                return@Column
            }

            val timed = m.timeCtl.baseMs > 0
            @Composable
            fun strip(bottom: Boolean) {
                val white = if (bottom) m.whiteBottom else !m.whiteBottom
                val isBot = white == m.engineWhite
                val running = timed && !v.over && v.turnWhite == white && v.sans.isNotEmpty()
                val turn = !v.over && v.turnWhite == white
                tick.let {
                    PlayerStrip(
                        mark = if (isBot) m.bot.mark else me.take(1),
                        markWhite = white,
                        name = if (isBot) m.bot.name else me,
                        sub = when {
                            isBot && m.thinking -> "${m.bot.elo} ELO · düşünüyor…"
                            isBot -> "${m.bot.elo} ELO · seviye ${m.bot.level}"
                            turn -> "Sıra ${if (white) "beyazda" else "siyahta"} · sende"
                            else -> if (white) "Beyaz" else "Siyah"
                        },
                        clock = if (timed) fmtClock(m.remaining(white)) else null,
                        running = running,
                        active = turn,
                        extra = { CapturedRow(if (white) byWhite else byBlack, if (white) diff else -diff) },
                    )
                }
            }

            strip(bottom = false)
            val ply = v.sans.size
            val threats = remember(v.fen, m.threatsOn) { if (m.threatsOn && !v.over) Threats.of(v.fen, myWhite) else emptySet() }
            ChessBoard(
                fen = v.fen,
                whiteBottom = m.whiteBottom,
                legal = if (m.myTurn) v.legal else emptyList(),
                lastMove = v.lastMove,
                check = v.check,
                arrows = listOfNotNull(m.hintArrow?.let { Arrow(it, ARROW_HINT) }),
                hint = m.hintArrow,
                threats = threats,
                badge = if (m.coachOn) m.classAt(ply)?.let { c -> v.lastMove?.let { SquareBadge(it.substring(2, 4), c) } } else null,
                fx = m.fx,
                shake = m.shake,
                onMove = { m.userMove(it) },
            )
            strip(bottom = true)
            MoveStream(v.sans, ply, { m.classAt(it) })

            if (m.coachOn) {
                val myLast = (ply downTo 1).firstOrNull { p -> (p % 2 == 1) == myWhite && m.classAt(p) != null }
                PulsarModule(
                    cp = m.whiteCp,
                    values = m.wins.map { (it - 50f) / 12f },
                    active = m.wins.size - 1,
                    decodeActive = decodeShown ?: decodeOf(myLast?.let { m.classAt(it) }),
                    onDecode = { decodeShown = it },
                )
            }
            val explain = decodeShown?.let { CODE_EXPLAIN[it] }
            CoachStrip(
                title = explain?.first ?: m.say.title ?: CLASS_INFO[m.say.cls]?.label ?: "Koç",
                desc = explain?.second ?: m.say.text,
                code = if (explain == null) m.say.cls else null,
            ) {
                if (v.over) SavilleButton("İncele", cap = K.Blue) { onReview(m.game.pgn(), m.finalReview, m.id) }
                else SavilleButton("İpucu", cap = K.Yellow, enabled = m.myTurn) { m.hint() }
            }
            ActionRow {
                MiniAction(KIcons.Target, "Tehditler", Modifier.weight(1f), on = m.threatsOn) {
                    m.threatsOn = !m.threatsOn; Crab.setSetting("threats", if (m.threatsOn) "1" else "0")
                }
                MiniAction(KIcons.Coach, "Koç", Modifier.weight(1f), on = m.coachOn) {
                    m.coachOn = !m.coachOn; Crab.setSetting("coach", if (m.coachOn) "1" else "0")
                }
                MiniAction(KIcons.Flip, "Çevir", Modifier.weight(1f)) { m.whiteBottom = !m.whiteBottom }
            }
            ActionRow(Modifier.padding(bottom = 4.dp)) {
                MiniAction(KIcons.Undo, "Geri al", Modifier.weight(1f), enabled = !v.over && v.sans.isNotEmpty() && !m.thinking) { m.undo() }
                MiniAction(KIcons.Flag, "Terk", Modifier.weight(1f), enabled = !v.over) { m.resign() }
                MiniAction(KIcons.Analysis, "İncele", Modifier.weight(1f), enabled = v.sans.size >= 2) { onReview(m.game.pgn(), m.finalReview, m.id) }
            }
            v.opening?.let {
                Text(it.upTr(), style = KType.code, color = K.Ink3, modifier = Modifier.fillMaxWidth().hairlineTop().padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }

    if (m.gameOver) GameOverSheet(m, onDismiss = { m.gameOver = false }, onNew = { m.gameOver = false; onNewGame() },
        onReview = { m.gameOver = false; onReview(m.game.pgn(), m.finalReview, m.id) })
}

/** Oyun sonu: keskin sayfa, katalog başlığı, istatistik satırları, Saville eylemleri */
@Composable
private fun GameOverSheet(m: PlayModel, onDismiss: () -> Unit, onNew: () -> Unit, onReview: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val enter = remember { Animatable(if (Motion.reduced) 1f else 0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.VIEW, easing = Motion.Expo)) }
        Column(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                .graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 12.dp.toPx() }
                .background(K.Surface).border(1.dp, K.LineStrong),
        ) {
            Row(Modifier.fillMaxWidth().background(K.Chrome).hairlineBottom().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CatalogCode("Oyun bitti · ${m.view.result}")
                Spacer(Modifier.weight(1f))
                SavilleStrip()
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.resultText.upTr(), style = KType.headline, color = K.Ink)
                Text(m.view.termination ?: "", style = KType.desc, color = K.Ink2)
                Spacer(Modifier.height(6.dp))
                val r = m.finalReview?.let { JSONObject(it) }
                val myKey = if (m.engineWhite) "black" else "white"
                if (r != null) {
                    val acc = r.getJSONObject(myKey).optDouble("accuracy", Double.NaN)
                    val opp = r.getJSONObject(if (m.engineWhite) "white" else "black").optDouble("accuracy", Double.NaN)
                    StatLine("Doğruluğun", if (acc.isNaN()) "—" else "%" + countUp(acc.toFloat()))
                    StatLine("Rakip doğruluğu", if (opp.isNaN()) "—" else "%.1f".format(opp))
                    r.getJSONObject(myKey).optInt("estimated_elo", 0).takeIf { it > 0 }?.let { StatLine("Performans", "~$it") }
                    val bril = m.reviews.count { it != null && it.optBoolean("white") == !m.engineWhite && it.optString("class") in setOf("brilliant", "great") }
                    val bl = m.reviews.count { it != null && it.optBoolean("white") == !m.engineWhite && it.optString("class") in setOf("blunder", "miss") }
                    StatLine("Parlak / hata", "$bril !! · $bl ??")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Koç oyunu inceliyor", style = KType.desc, color = K.Ink2, modifier = Modifier.weight(1f))
                        ScanPulse()
                    }
                }
                StatLine("Hamle", "${m.view.sans.size}")
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SavilleButton("Oyunu incele", Modifier.fillMaxWidth(), cap = K.Blue, onClick = onReview)
                SavilleButton("Yeni oyun", Modifier.fillMaxWidth(), cap = K.Red, onClick = onNew)
                SavilleButton("Kapat", Modifier.fillMaxWidth(), cap = K.Industrial, onClick = onDismiss)
            }
        }
    }
}

/** Yeni oyun: rakip listesi, renk, süre ve Saville "maç başlat" */
@Composable
fun BotSelectScreen(m: PlayModel, modifier: Modifier, onStart: () -> Unit, onBack: () -> Unit) {
    var bot by remember { mutableStateOf(m.bot) }
    var color by remember { mutableStateOf("white") }
    var tc by remember { mutableStateOf(m.timeCtl) }
    Column(modifier.fillMaxSize()) {
        TopBar("Yeni oyun", "NEW 00${bot.level}", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CoachStrip(bot.name, "“${bot.blurb}”", code = null)
            SectionLabel("Rakip", trailing = "${BOTS.size} bot")
            for ((i, b) in BOTS.withIndex()) {
                val sel = b == bot
                GameRow(
                    title = b.name,
                    sub = "${b.elo} ELO · seviye ${b.level}",
                    badge = if (sel) "Seçili" else null,
                    badgeColor = K.Blue,
                    leading = { Monogram(b.mark, filled = sel) },
                    bg = if (sel) K.Surface else K.Base,
                ) { bot = b }
                if (i == BOTS.lastIndex) Hairline()
            }
            SectionLabel("Rengin")
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((k, label) in listOf("white" to "Beyaz", "random" to "Rastgele", "black" to "Siyah")) {
                    SegButton(label, color == k, Modifier.weight(1f)) { color = k }
                }
            }
            SectionLabel("Süre")
            val rows = TIME_CONTROLS.chunked(3)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in row) TcTile(if (t.baseMs == 0L) "∞" else t.label, tcName(t), tc == t, Modifier.weight(1f)) { tc = t }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().background(K.Base).hairlineTop().padding(12.dp)) {
            SavilleButton("Maç başlat (${if (tc.baseMs == 0L) "süresiz" else tc.label})", Modifier.fillMaxWidth(), cap = K.Red) {
                val meWhite = when (color) { "white" -> true; "black" -> false; else -> Math.random() < 0.5 }
                m.newGame(bot, meWhite, tc)
                onStart()
            }
        }
    }
}

fun tcName(t: TimeCtl): String = when {
    t.baseMs == 0L -> "Öğrenme"
    t.baseMs <= 180_000 -> "Blitz"
    t.baseMs <= 600_000 -> "Hızlı"
    else -> "Klasik"
}
