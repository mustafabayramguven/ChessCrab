package dev.chesscrab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.ChessGame
import uniffi.chesscrab_ffi.engineLevelsJson
import java.util.UUID

data class TimeCtl(val label: String, val baseMs: Long, val incMs: Long)

val TIME_CONTROLS = listOf(
    TimeCtl("Süresiz", 0, 0), TimeCtl("3+2", 180_000, 2_000), TimeCtl("5+0", 300_000, 0),
    TimeCtl("10+0", 600_000, 0), TimeCtl("15+10", 900_000, 10_000),
)

/** Oyun durumu; sekmeler arasında kaybolmasın diye App seviyesinde tutulur. */
class PlayModel {
    var game: ChessGame = ChessGame()
    var view by mutableStateOf(GameView.parse(game.stateJson()))
    var engineWhite by mutableStateOf(false)
    var whiteBottom by mutableStateOf(true)
    var level by mutableIntStateOf(Crab.setting("level", "4").toInt())
    var thinking by mutableStateOf(false)
    var started by mutableStateOf(false)
    var timeCtl by mutableStateOf(TIME_CONTROLS[0])
    var whiteMs by mutableLongStateOf(0L)
    var blackMs by mutableLongStateOf(0L)
    var turnStart = 0L
    var lastEngineEval: String? by mutableStateOf(null)
    var id: String = UUID.randomUUID().toString().replace("-", "")
    var saved = false

    fun refresh() { view = GameView.parse(game.stateJson()) }

    fun newGame(engineIsWhite: Boolean) {
        Crab.engine.stop()
        game = ChessGame()
        Crab.engine.newGame()
        engineWhite = engineIsWhite
        whiteBottom = !engineIsWhite
        id = UUID.randomUUID().toString().replace("-", "")
        saved = false
        started = true
        lastEngineEval = null
        whiteMs = timeCtl.baseMs; blackMs = timeCtl.baseMs
        turnStart = System.currentTimeMillis()
        Crab.setSetting("level", level.toString())
        refresh()
    }

    private fun spendClock(): Long? {
        if (timeCtl.baseMs == 0L) return null
        val now = System.currentTimeMillis()
        val used = now - turnStart
        turnStart = now
        return if (view.turnWhite) { whiteMs = (whiteMs - used).coerceAtLeast(0) + timeCtl.incMs; whiteMs }
        else { blackMs = (blackMs - used).coerceAtLeast(0) + timeCtl.incMs; blackMs }
    }

    fun play(uci: String) {
        val clk = spendClock()
        runCatching { if (clk != null) game.playWithClock(uci, clk.toULong()) else game.play(uci) }
        refresh()
        if (view.over) save()
    }

    fun remaining(white: Boolean): Long {
        val base = if (white) whiteMs else blackMs
        return if (!view.over && started && view.turnWhite == white && view.ucis.isNotEmpty()) base - (System.currentTimeMillis() - turnStart) else base
    }

    /** Kendi hamleni geri al (gerekirse motorunkini de). */
    fun undo() {
        if (!game.undo()) return
        if (GameView.parse(game.stateJson()).turnWhite == engineWhite && game.ply() > 0u) game.undo()
        refresh()
    }

    suspend fun engineMove() {
        if (view.over || view.turnWhite != engineWhite) return
        thinking = true
        val movetime = if (timeCtl.baseMs > 0) (remaining(engineWhite) / 30).coerceIn(200, 3000) else null
        val g = game
        val mv = try {
            withContext(Dispatchers.Default) { Crab.engine.bestMove(g, level.toUByte(), movetime?.toULong()) }
        } finally {
            thinking = false
        }
        if (mv != null && g === game) {
            val o = JSONObject(mv)
            lastEngineEval = if (o.getBoolean("from_book")) "kitap" else dev.chesscrab.app.fmtScore(o.getJSONObject("score"))
            play(o.getString("uci"))
        }
    }

    fun save() {
        if (saved || view.sans.size < 2) return
        saved = true
        val levelName = JSONArray(engineLevelsJson()).objects().firstOrNull { it.getInt("level") == level }?.getString("name") ?: ""
        val me = SyncClient.username.ifEmpty { "Sen" }
        val bot = "CrabFish $level ($levelName)"
        val white = if (engineWhite) bot else me
        val black = if (engineWhite) me else bot
        game.setTag("White", white); game.setTag("Black", black)
        game.setTag("Date", java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US).format(java.util.Date()))
        if (timeCtl.baseMs > 0) game.setTag("TimeControl", "${timeCtl.baseMs / 1000}+${timeCtl.incMs / 1000}")
        refresh()
        val data = JSONObject()
            .put("id", id).put("pgn", game.pgn()).put("white", white).put("black", black)
            .put("result", view.result).put("mode", "engine").put("engine_level", level)
            .put("created_ms", System.currentTimeMillis()).put("opening", view.opening ?: JSONObject.NULL)
        Crab.store.put("games", id, data.toString())
    }
}

@Composable
fun PlayScreen(m: PlayModel, modifier: Modifier, onAnalyse: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(!m.started) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(m.view, m.started) { if (m.started) m.engineMove() }
    LaunchedEffect(m.started, m.timeCtl) {
        while (true) {
            delay(200); tick++
            if (m.started && m.timeCtl.baseMs > 0 && !m.view.over && m.view.ucis.isNotEmpty()) {
                val white = m.view.turnWhite
                if (m.remaining(white) <= 0) { m.game.timeout(white); m.refresh(); m.save() }
            }
        }
    }
    LaunchedEffect(m.view.over) { if (m.view.over && SyncClient.loggedIn) syncQuietly(scope) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (m.timeCtl.baseMs > 0) { tick.let { ClockRow(m, top = true) } }
        val myTurn = m.started && !m.view.over && m.view.turnWhite != m.engineWhite && !m.thinking
        ChessBoard(
            fen = m.view.fen,
            whiteBottom = m.whiteBottom,
            legal = if (myTurn) m.view.legal else emptyList(),
            lastMove = m.view.lastMove,
            check = m.view.check,
            onMove = { m.play(it) },
        )
        if (m.timeCtl.baseMs > 0) { tick.let { ClockRow(m, top = false) } }
        Spacer(Modifier.height(10.dp))
        val status = when {
            !m.started -> "Yeni oyun başlat"
            m.view.over -> (when (m.view.winner) { "white" -> "Beyaz kazandı"; "black" -> "Siyah kazandı"; else -> "Berabere" }) + " · " + (m.view.termination ?: "")
            m.thinking -> "CrabFish düşünüyor…"
            else -> "Sıra sende"
        }
        Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        m.view.opening?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        m.lastEngineEval?.let { Text("CrabFish: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(6.dp))
        MoveStrip(m.view.sans)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick = { showNew = true }) { Text("Yeni oyun") }
            OutlinedButton(enabled = !m.thinking && m.view.sans.isNotEmpty() && !m.view.over, onClick = { m.undo() }) { Text("Geri al") }
            OutlinedButton(onClick = { m.whiteBottom = !m.whiteBottom }) { Text("Çevir") }
            OutlinedButton(enabled = m.started && !m.view.over, onClick = { m.game.resign(!m.engineWhite); m.refresh(); m.save() }) { Text("Terk") }
            OutlinedButton(enabled = m.view.sans.isNotEmpty(), onClick = { m.save(); onAnalyse(m.game.pgn(), if (m.saved) m.id else null) }) { Text("Analiz") }
        }
    }

    if (showNew) NewGameDialog(m, onDismiss = { showNew = false }) { engineWhite ->
        showNew = false
        m.newGame(engineWhite)
    }
}

fun syncQuietly(scope: CoroutineScope) {
    scope.launch { SyncClient.sync() }
}

@Composable
private fun ClockRow(m: PlayModel, top: Boolean) {
    val white = if (top) !m.whiteBottom else m.whiteBottom
    val ms = m.remaining(white).coerceAtLeast(0)
    val s = ms / 1000
    val txt = if (ms < 10_000) "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100) else "%d:%02d".format(s / 60, s % 60)
    val running = m.started && !m.view.over && m.view.turnWhite == white && m.view.ucis.isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
        Text(
            txt,
            fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun MoveStrip(sans: List<String>, current: Int? = null, onClick: ((Int) -> Unit)? = null) {
    val scroll = rememberScrollState()
    LaunchedEffect(sans.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        sans.forEachIndexed { i, s ->
            if (i % 2 == 0) Text("${i / 2 + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            val sel = current == i + 1
            TextButton(onClick = { onClick?.invoke(i + 1) }, enabled = onClick != null) {
                Text(s, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun NewGameDialog(m: PlayModel, onDismiss: () -> Unit, onStart: (Boolean) -> Unit) {
    val levels = remember { JSONArray(engineLevelsJson()).objects() }
    var color by remember { mutableStateOf("white") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CrabFish'e karşı") },
        text = {
            Column {
                val l = levels.firstOrNull { it.getInt("level") == m.level }
                Text("Seviye ${m.level}: ${l?.getString("name") ?: ""} (~${l?.getInt("elo") ?: 0} Elo)")
                Slider(value = m.level.toFloat(), onValueChange = { m.level = it.toInt() }, valueRange = 1f..8f, steps = 6)
                Text("Renk")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((k, v) in listOf("white" to "Beyaz", "black" to "Siyah", "random" to "Rastgele")) {
                        FilterChip(selected = color == k, onClick = { color = k }, label = { Text(v) })
                    }
                }
                Text("Süre")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (tc in TIME_CONTROLS) FilterChip(selected = m.timeCtl == tc, onClick = { m.timeCtl = tc }, label = { Text(tc.label) })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val meWhite = when (color) { "white" -> true; "black" -> false; else -> Math.random() < 0.5 }
                onStart(!meWhite)
            }) { Text("Başla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
