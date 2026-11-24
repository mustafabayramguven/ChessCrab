package dev.chesscrab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.fmtScore
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.chesscrab_ffi.AnalysisProgress
import uniffi.chesscrab_ffi.analyzePgn
import kotlin.math.exp

fun winPercent(cp: Int): Float {
    val c = cp.coerceIn(-1000, 1000)
    return (50 + 50 * (2 / (1 + exp(-0.00368208 * c)) - 1)).toFloat()
}

class AnalysisModel {
    var pgn by mutableStateOf("")
    var gameId: String? = null
    var depth by mutableIntStateOf(Crab.setting("depth", "12").toInt())
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var result by mutableStateOf<JSONObject?>(null)
    var error by mutableStateOf<String?>(null)
    var ply by mutableIntStateOf(0)
    var whiteBottom by mutableStateOf(true)
    var pendingAutoRun = false

    fun load(text: String, id: String?) {
        pgn = text; gameId = id; result = null; ply = 0; error = null
        pendingAutoRun = true
    }

    suspend fun run() {
        if (running || pgn.isBlank()) return
        running = true; error = null; progress = 0f
        Crab.setSetting("depth", depth.toString())
        try {
            val cb = object : AnalysisProgress {
                override fun onProgress(done: UInt, total: UInt) { progress = done.toFloat() / total.toFloat() }
            }
            val json = withContext(Dispatchers.Default) { analyzePgn(pgn, depth.toUInt(), 0u, cb) }
            val r = JSONObject(json)
            result = r; ply = 0
            // Oyunlarım listesindeki kayda doğrulukları ekle
            gameId?.let { id ->
                Crab.store.get("games", id)?.let { g ->
                    val o = JSONObject(g)
                    r.getJSONObject("white").opt("accuracy")?.let { o.put("white_accuracy", it) }
                    r.getJSONObject("black").opt("accuracy")?.let { o.put("black_accuracy", it) }
                    Crab.store.put("games", id, o.toString())
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            running = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(m: AnalysisModel, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    if (m.pendingAutoRun) { m.pendingAutoRun = false; scope.launch { m.run() } }
    val r = m.result

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (r == null) {
            Text("Oyun analizi", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = m.pgn, onValueChange = { m.pgn = it },
                label = { Text("PGN (lichess / chess.com dışa aktarımı)") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("Derinlik")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in listOf(10, 12, 14, 16, 18)) FilterChip(selected = m.depth == d, onClick = { m.depth = d }, label = { Text("$d") })
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = !m.running && m.pgn.isNotBlank(), onClick = { scope.launch { m.run() } }) { Text("Analiz et") }
            if (m.running) {
                Spacer(Modifier.height(10.dp))
                Text("Analiz ediliyor… %${(m.progress * 100).toInt()}")
                LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth())
            }
            m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Text(
                "İpucu: lichess veya chess.com uygulamasında bir oyunu 'Paylaş → ChessCrab' ile gönderirsen doğrudan buraya gelir. Tüm hesap telefonda, çevrimdışı yapılır.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val evals = r.getJSONArray("evals").objects()
        val moves = r.getJSONArray("moves").objects()
        val ply = m.ply.coerceIn(0, moves.size)
        val ev = evals[ply]
        val mv = if (ply > 0) moves[ply - 1] else null
        val next = moves.getOrNull(ply)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.optJSONArray("opening")?.let { "${it.getString(0)} · ${it.getString(1)}" } ?: "Analiz", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { m.result = null }) { Text("Yeni") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SideCard("Beyaz", r.getJSONObject("white"), Modifier.weight(1f))
            SideCard("Siyah", r.getJSONObject("black"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ChessBoard(
            fen = ev.getString("fen"), whiteBottom = m.whiteBottom, legal = emptyList(),
            lastMove = mv?.getString("uci"),
            arrows = if (next != null && next.getString("class") != "book") listOfNotNull(next.optStr("best_uci")) else emptyList(),
        )
        EvalBar(winPercent(ev.getInt("cp")))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { m.ply = 0 }) { Icon(Icons.Filled.FirstPage, "başa") }
            IconButton(onClick = { m.ply = (ply - 1).coerceAtLeast(0) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "geri") }
            IconButton(onClick = { m.ply = (ply + 1).coerceAtMost(moves.size) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "ileri") }
            IconButton(onClick = { m.ply = moves.size }) { Icon(Icons.AutoMirrored.Filled.LastPage, "sona") }
            IconButton(onClick = { m.whiteBottom = !m.whiteBottom }) { Icon(Icons.Filled.SwapVert, "çevir") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (mv != null) {
                    val ci = CLASS_INFO[mv.getString("class")]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${mv.getInt("move_number")}${if (mv.getBoolean("white")) "." else "..."} ${mv.getString("san")}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        if (ci != null) Text(ci.first, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(ci.second, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("doğruluk ${mv.getDouble("accuracy").toInt()} · ${fmtScore(mv.optJSONObject("eval_after"))}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(mv.getString("comment"))
                    val cls = mv.getString("class")
                    val line = mv.optJSONArray("best_line")?.strings().orEmpty()
                    if (line.isNotEmpty() && cls !in listOf("best", "book", "forced", "brilliant", "great")) {
                        Text("En iyi devam: ${line.joinToString(" ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Başlangıç · ${fmtScore(ev.optJSONObject("score"))}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        EvalGraph(evals.map { winPercent(it.getInt("cp")) }, moves, ply) { m.ply = it }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            moves.forEachIndexed { i, x ->
                val ci = CLASS_INFO[x.getString("class")]
                val sel = ply == i + 1
                val label = (if (x.getBoolean("white")) "${x.getInt("move_number")}. " else "") + x.getString("san") + (ci?.third?.takeIf { it.isNotEmpty() && it != "★" } ?: "")
                Text(
                    label,
                    color = if (ci != null && ci.third.isNotEmpty() && ci.third != "★") ci.second else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (sel) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { m.ply = i + 1 }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${r.getString("engine")} · ${r.getLong("total_nodes") / 1_000_000}M düğüm · ${r.getLong("time_ms")} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideCard(name: String, s: JSONObject, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.bodySmall)
            val acc = if (s.isNull("accuracy")) "-" else "%.1f".format(s.getDouble("accuracy"))
            Text("%$acc", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            val elo = if (s.isNull("estimated_elo")) "" else " · ~${s.getInt("estimated_elo")} Elo"
            Text("ACPL ${s.getDouble("acpl").toInt()}$elo", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val counts = s.getJSONArray("counts")
                for (i in 0 until counts.length()) {
                    val pair = counts.getJSONArray(i)
                    val ci = CLASS_INFO[pair.getString(0)] ?: continue
                    Text("${ci.first} ${pair.getInt(1)}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp).background(ci.second, RoundedCornerShape(50)).padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
fun EvalBar(whitePct: Float) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).background(Color(0xFF333333), RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(whitePct / 100f).height(8.dp).background(Color(0xFFF2F2F2), RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun EvalGraph(wins: List<Float>, moves: List<JSONObject>, ply: Int, onPly: (Int) -> Unit) {
    val n = (wins.size - 1).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1C1B19), RoundedCornerShape(8.dp))
            .pointerInput(wins.size) { detectTapGestures { o -> onPly(((o.x / size.width) * n).toInt().coerceIn(0, n)) } }
    ) {
        val w = size.width; val h = size.height
        val x = { i: Int -> i / n.toFloat() * w }
        val y = { p: Float -> h - p / 100f * h }
        val path = Path().apply {
            moveTo(0f, h)
            wins.forEachIndexed { i, p -> lineTo(x(i), y(p)) }
            lineTo(w, h); close()
        }
        drawPath(path, Color(0xFFE8E6E3))
        drawLine(Color(0xFF777777), Offset(0f, h / 2), Offset(w, h / 2))
        moves.forEach { mv ->
            val c = mv.getString("class")
            if (c in listOf("blunder", "mistake", "miss", "brilliant", "great")) {
                val p = mv.getInt("ply")
                drawCircle(CLASS_INFO[c]!!.second, 6f, Offset(x(p), y(wins[p])))
            }
        }
        drawLine(Color(0xFFE07B39), Offset(x(ply), 0f), Offset(x(ply), h), strokeWidth = 3f)
    }
}
