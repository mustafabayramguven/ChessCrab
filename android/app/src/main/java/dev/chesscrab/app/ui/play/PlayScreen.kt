package dev.chesscrab.app.ui.play

import dev.chesscrab.app.ui.components.bounceClick
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Brush
import dev.chesscrab.app.ui.Display
import dev.chesscrab.app.ui.Mono
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.chesscrab.app.Crab
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.board.ARROW_HINT
import dev.chesscrab.app.ui.board.Arrow
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.board.SquareBadge
import dev.chesscrab.app.ui.board.capturedPieces
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.ClassBadge
import dev.chesscrab.app.ui.components.Pill
import dev.chesscrab.app.ui.components.CoachAvatar
import dev.chesscrab.app.ui.components.CoachBubble
import dev.chesscrab.app.ui.components.CountUp
import dev.chesscrab.app.ui.components.EvalBar
import dev.chesscrab.app.ui.components.EvalGraph
import dev.chesscrab.app.ui.components.PlayerBar
import kotlinx.coroutines.delay
import org.json.JSONObject

fun fmtClock(ms: Long): String {
    val m = ms.coerceAtLeast(0); val s = m / 1000
    return if (m < 10_000) "%d:%02d.%d".format(s / 60, s % 60, (m % 1000) / 100) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayScreen(m: PlayModel, modifier: Modifier, onNewGame: () -> Unit, onReview: (pgn: String, review: String?, id: String?) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(m.started, m.timeCtl) {
        while (m.timeCtl.baseMs > 0) { delay(200); tick++; m.checkClock() }
    }
    val v = m.view
    val (byWhite, byBlack, diff) = remember(v.fen) { capturedPieces(v.fen) }
    val me = dev.chesscrab.app.SyncClient.username.ifEmpty { "Sen" }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            // üst: rakip (ya da tahta çevrildiyse sen)
            val topIsBot = m.whiteBottom != m.engineWhite
            val topWhite = !m.whiteBottom
            tick.let {
                if (topIsBot) PlayerBar(m.bot.name, "(${m.bot.elo})", m.bot.emoji, m.bot.color,
                    if (topWhite) byWhite else byBlack, if (topWhite) diff else -diff,
                    clock = if (m.timeCtl.baseMs > 0) fmtClock(m.remaining(topWhite)) else null,
                    clockActive = m.started && !v.over && v.turnWhite == topWhite, thinking = m.thinking)
                else PlayerBar(me, "", me.first().uppercase(), Crabs.Coral, if (topWhite) byWhite else byBlack, if (topWhite) diff else -diff,
                    clock = if (m.timeCtl.baseMs > 0) fmtClock(m.remaining(topWhite)) else null, clockActive = m.started && !v.over && v.turnWhite == topWhite)
            }
            BoardWithEvalBar(m.coachOn, m.whiteCp, m.whiteBottom) { boardMod ->
                val ply = v.sans.size
                ChessBoard(
                    fen = v.fen,
                    whiteBottom = m.whiteBottom,
                    legal = if (m.myTurn) v.legal else emptyList(),
                    lastMove = v.lastMove,
                    check = v.check,
                    arrows = listOfNotNull(m.hintArrow?.let { Arrow(it, ARROW_HINT) }),
                    badge = if (m.coachOn) m.classAt(ply)?.let { c -> v.lastMove?.let { SquareBadge(it.substring(2, 4), c) } } else null,
                    shake = m.shake,
                    modifier = boardMod,
                    onMove = { m.userMove(it) },
                )
            }
            val botBottom = m.whiteBottom == m.engineWhite
            val bottomWhite = m.whiteBottom
            tick.let {
                if (botBottom) PlayerBar(m.bot.name, "(${m.bot.elo})", m.bot.emoji, m.bot.color, if (bottomWhite) byWhite else byBlack, if (bottomWhite) diff else -diff,
                    clock = if (m.timeCtl.baseMs > 0) fmtClock(m.remaining(bottomWhite)) else null, clockActive = m.started && !v.over && v.turnWhite == bottomWhite, thinking = m.thinking)
                else PlayerBar(me, "", me.first().uppercase(), Crabs.Coral, if (bottomWhite) byWhite else byBlack, if (bottomWhite) diff else -diff,
                    clock = if (m.timeCtl.baseMs > 0) fmtClock(m.remaining(bottomWhite)) else null, clockActive = m.started && !v.over && v.turnWhite == bottomWhite)
            }
        }
        MoveStrip(v.sans) { m.classAt(it) }
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (m.coachOn && v.sans.isNotEmpty()) {
                EvalGraph(m.wins.toList(), List(v.sans.size) { m.classAt(it + 1) }, selected = m.wins.size - 1, height = 64.dp)
                Spacer(Modifier.height(10.dp))
            }
            if (!m.started) {
                CoachBubble("Bir bot seç ve oyuna başla. Oyun boyunca her hamleni anında yorumlayacağım.", title = "Hoş geldin!")
                Spacer(Modifier.height(12.dp))
                BigButton("Oyna", onClick = onNewGame)
            } else if (m.coachOn) {
                CoachBubble(m.say.text, cls = m.say.cls, title = m.say.title)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionIcon(Icons.Filled.Add, "Yeni", onClick = onNewGame)
                ActionIcon(Icons.AutoMirrored.Filled.Undo, "Geri al", enabled = m.started && !v.over && v.sans.isNotEmpty() && !m.thinking) { m.undo() }
                ActionIcon(Icons.Filled.Lightbulb, "İpucu", enabled = m.myTurn) { m.hint() }
                ActionIcon(Icons.Filled.SwapVert, "Çevir") { m.whiteBottom = !m.whiteBottom }
                ActionIcon(Icons.Filled.Flag, "Terk", enabled = m.started && !v.over) { m.resign() }
                ActionIcon(Icons.Filled.Insights, "İncele", enabled = v.sans.size >= 2) { onReview(m.game.pgn(), m.finalReview, m.id) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("Canlı koç", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = m.coachOn, onCheckedChange = { m.coachOn = it; Crab.setSetting("coach", if (it) "1" else "0") },
                    colors = SwitchDefaults.colors(checkedTrackColor = Crabs.Coral, checkedThumbColor = Crabs.Ink, uncheckedTrackColor = Crabs.Card))
            }
            v.opening?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Crabs.Muted) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (m.gameOver) GameOverDialog(m, onDismiss = { m.gameOver = false }, onNew = { m.gameOver = false; onNewGame() },
        onReview = { m.gameOver = false; onReview(m.game.pgn(), m.finalReview, m.id) })
}

@Composable
fun MoveStrip(sans: List<String>, current: Int? = null, onClick: ((Int) -> Unit)? = null, classOf: (Int) -> String?) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val target = current ?: sans.size
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(sans.size, target) {
        if (sans.isEmpty()) return@LaunchedEffect
        val idx = ((target - 1) / 2 - 1).coerceAtLeast(0)
        if (first) state.scrollToItem(idx) else state.animateScrollToItem(idx)
        first = false
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sans.isEmpty()) item { Text("Hamleler burada görünecek", color = Crabs.Faint, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
        items((sans.size + 1) / 2, key = { it }) { pair ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                Text("${pair + 1}", color = Crabs.Faint, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(start = 8.dp, end = 3.dp))
                for (ply in listOf(pair * 2 + 1, pair * 2 + 2)) {
                    if (ply > sans.size) continue
                    val sel = ply == target
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) Crabs.CardHi else Color.Transparent)
                            .border(1.dp, if (sel) Crabs.Coral else Color.Transparent, RoundedCornerShape(50))
                            .then(if (onClick != null) Modifier.clickable { onClick(ply) } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cls = classOf(ply)
                        if (cls != null && cls in setOf("brilliant", "great", "best", "inaccuracy", "mistake", "miss", "blunder", "book")) {
                            ClassBadge(cls, 14.dp); Spacer(Modifier.width(4.dp))
                        }
                        Text(sans[ply - 1], fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.5.sp, fontFamily = Mono, color = if (sel) Crabs.Text else Crabs.Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.bounceClick(enabled = enabled, scaleTo = 0.88f, onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Crabs.Card).border(1.dp, Crabs.Line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (enabled) Crabs.Sand else Crabs.Faint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = if (enabled) Crabs.Muted else Crabs.Faint)
    }
}

@Composable
private fun GameOverDialog(m: PlayModel, onDismiss: () -> Unit, onNew: () -> Unit, onReview: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val sc by animateFloatAsState(if (shown) 1f else 0.85f, label = "pop")
        Column(
            Modifier.scale(sc).clip(RoundedCornerShape(28.dp)).background(Crabs.Surface).border(1.dp, Crabs.Line, RoundedCornerShape(28.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dev.chesscrab.app.ui.components.Overline("Oyun bitti")
            Text(m.resultText, style = MaterialTheme.typography.headlineMedium)
            Text(m.view.termination ?: "", color = Crabs.Muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CoachAvatar(56.dp, if (m.view.winner == (if (m.engineWhite) "black" else "white")) "🏆" else "🦀")
                Text("vs", color = Crabs.Muted)
                CoachAvatar(56.dp, m.bot.emoji, m.bot.color)
            }
            Spacer(Modifier.height(14.dp))
            val r = m.finalReview?.let { JSONObject(it) }
            if (r != null) {
                val myKey = if (m.engineWhite) "black" else "white"
                val acc = r.getJSONObject(myKey).optDouble("accuracy", Double.NaN)
                if (!acc.isNaN()) {
                    Text("Doğruluğun", color = Crabs.Muted)
                    Text(CountUp(acc.toFloat()), fontSize = 56.sp, fontFamily = Display, color = Crabs.Coral)
                }
            } else {
                Text("Koç oyunu inceliyor…", color = Crabs.Muted)
            }
            Spacer(Modifier.height(16.dp))
            BigButton("Oyun İncelemesi", onClick = onReview)
            Spacer(Modifier.height(8.dp))
            BigButton("Yeni Oyun", color = Crabs.Card, onClick = onNew)
        }
    }
}

/** Rakip seçimi: deniz canlıları */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotSelectScreen(m: PlayModel, modifier: Modifier, onStart: () -> Unit, onBack: () -> Unit) {
    var bot by remember { mutableStateOf(m.bot) }
    var color by remember { mutableStateOf("white") }
    var tc by remember { mutableStateOf(m.timeCtl) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                dev.chesscrab.app.ui.components.Overline("Yeni oyun")
                Text("Rakibini seç", style = MaterialTheme.typography.headlineMedium)
            }
            Text("Kapat", color = Crabs.Muted, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        // seçili bot kartı
        androidx.compose.animation.AnimatedContent(bot, label = "bot", transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
        }) { b ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(b.color.copy(alpha = 0.28f), Crabs.Card, Crabs.Card)))
                    .border(1.dp, Crabs.Line, RoundedCornerShape(26.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(86.dp).clip(CircleShape).background(Brush.radialGradient(listOf(b.color.copy(alpha = 0.45f), b.color.copy(alpha = 0.08f))))
                        .border(2.dp, b.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(b.emoji, fontSize = 44.sp) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(b.name, style = MaterialTheme.typography.titleLarge)
                    Text("${b.elo} · seviye ${b.level}", color = b.color, fontFamily = Mono, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("“${b.blurb}”", fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 16.sp, color = Crabs.Sand, lineHeight = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (b in BOTS) {
                val sel = b == bot
                val sc by animateFloatAsState(if (sel) 1.08f else 1f, label = "botsc")
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable { bot = b }) {
                    Box(
                        Modifier.scale(sc).size(60.dp).clip(CircleShape).background(if (sel) b.color.copy(alpha = 0.3f) else Crabs.Card)
                            .border(if (sel) 2.dp else 1.dp, if (sel) b.color else Crabs.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(b.emoji, fontSize = 28.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text("${b.elo}", fontSize = 11.sp, color = if (sel) Crabs.Text else Crabs.Faint, fontFamily = Mono)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        dev.chesscrab.app.ui.components.Overline("Rengin")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((k, label, bg) in listOf(Triple("white", "Beyaz", Crabs.Ivory), Triple("random", "Rastgele", Crabs.Faint), Triple("black", "Siyah", Crabs.Abyss))) {
                val sel = color == k
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(if (sel) Crabs.CardHi else Color.Transparent)
                        .border(1.dp, if (sel) Crabs.Coral else Crabs.Line, RoundedCornerShape(50)).clickable { color = k },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(bg).border(1.dp, Crabs.Faint, CircleShape))
                        Spacer(Modifier.width(6.dp)); Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        dev.chesscrab.app.ui.components.Overline("Süre")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in TIME_CONTROLS) {
                val sel = tc == t
                Pill(t.label, sel, mono = true) { tc = t }
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton("Oyna") {
            val meWhite = when (color) { "white" -> true; "black" -> false; else -> Math.random() < 0.5 }
            m.newGame(bot, meWhite, tc)
            onStart()
        }
        Spacer(Modifier.height(10.dp))
        Text("Oyun sırasında canlı koç her hamleni değerlendirir; istersen oyun ekranından kapatabilirsin.", color = Crabs.Muted, fontSize = 12.sp)
    }
}

@Composable
fun ClassChip(cls: String) {
    val info = CLASS_INFO[cls] ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClassBadge(cls, 18.dp); Spacer(Modifier.width(4.dp)); Text(info.label, color = info.color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Tahta + solunda değerlendirme çubuğu (çubuk tahta yüksekliğinde). */
@Composable
fun BoardWithEvalBar(showBar: Boolean, whiteCp: Int, whiteBottom: Boolean, board: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barW = if (showBar) 21.dp else 0.dp
        val side = maxWidth - barW
        Row {
            if (showBar) { EvalBar(whiteCp, whiteBottom, Modifier.height(side)); Spacer(Modifier.width(5.dp)) }
            board(Modifier.size(side))
        }
    }
}
