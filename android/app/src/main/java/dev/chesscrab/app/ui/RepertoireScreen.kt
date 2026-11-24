package dev.chesscrab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.RepertoireDrill
import uniffi.chesscrab_ffi.repertoiresJson
import uniffi.chesscrab_ffi.srsReviewJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun cardOf(id: String): JSONObject? = Crab.store.get("srs_cards", id)?.let { JSONObject(it) }
private fun isDue(cardId: String, now: Long) = cardOf(cardId)?.let { it.getLong("due_ms") <= now } ?: true

@Composable
fun RepertoireScreen(modifier: Modifier) {
    var drill by remember { mutableStateOf<Pair<JSONObject, Int>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val reps = remember { JSONArray(repertoiresJson()).objects() }
    val d = drill
    if (d != null) {
        BackHandler { drill = null; refresh++ }
        DrillScreen(d.first, d.second, modifier,
            onNext = { rep, cur ->
                val now = System.currentTimeMillis()
                val lines = rep.getJSONArray("lines").objects()
                val nxt = lines.indexOfFirst { it.getInt("index") != cur && isDue(it.getString("card_id"), now) }
                drill = if (nxt >= 0) rep to nxt else null
                refresh++
            },
            onExit = { drill = null; refresh++ })
        return
    }
    val now = System.currentTimeMillis()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Text("Repertuvar çalışması", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("Rakibin hamleleri otomatik oynanır, sen kendi hamlelerini hatırla. Her hat aralıklı tekrarla (SRS) planlanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (r in reps) {
            item(key = r.getString("id")) {
                val lines = r.getJSONArray("lines").objects()
                val due = refresh.let { lines.count { isDue(it.getString("card_id"), now) } }
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.getString("title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Button(enabled = due > 0, onClick = {
                                val i = lines.indexOfFirst { isDue(it.getString("card_id"), now) }
                                if (i >= 0) drill = r to i
                            }) { Text("Çalış ($due)") }
                        }
                        Text(r.getString("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (l in lines) {
                            val c = refresh.let { cardOf(l.getString("card_id")) }
                            val st = when {
                                c == null -> "yeni"
                                c.getLong("due_ms") <= now -> "tekrar zamanı"
                                else -> "sonraki: " + SimpleDateFormat("d MMM", Locale("tr")).format(Date(c.getLong("due_ms")))
                            }
                            Row(Modifier.fillMaxWidth().clickable { drill = r to l.getInt("index") }.padding(vertical = 6.dp)) {
                                Text(l.getString("name"), modifier = Modifier.weight(1f))
                                Text(st, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DrillScreen(rep: JSONObject, line: Int, modifier: Modifier, onNext: (JSONObject, Int) -> Unit, onExit: () -> Unit) {
    val drill = remember(rep.getString("id"), line) { RepertoireDrill(rep.getString("id"), line.toUInt()) }
    var view by remember(drill) { mutableStateOf(JSONObject(drill.viewJson())) }
    var msg by remember(drill) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var finished by remember(drill) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lineObj = rep.getJSONArray("lines").getJSONObject(line)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lineObj.getString("name"), style = MaterialTheme.typography.titleLarge)
                Text(rep.getString("title"), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onExit) { Text("Kapat") }
        }
        ChessBoard(
            fen = view.getString("fen"),
            whiteBottom = view.getString("orientation") == "white",
            legal = view.getJSONArray("legal_moves").strings(),
            lastMove = view.optStr("last_move"),
            onMove = { uci ->
                val fb = JSONObject(drill.submit(uci))
                view = JSONObject(drill.viewJson())
                msg = if (!fb.getBoolean("correct")) false to "Repertuvardaki hamle: ${fb.optStr("expected_san")}. Onu oyna."
                else true to ("Doğru!" + (fb.optStr("reply_san")?.let { " Rakip: $it" } ?: ""))
                if (fb.getBoolean("done") && !finished) {
                    finished = true
                    val now = System.currentTimeMillis()
                    val card = srsReviewJson(Crab.store.get("srs_cards", drill.cardId()), drill.cardId(), drill.grade(), now)
                    Crab.store.put("srs_cards", drill.cardId(), card)
                    val days = ((JSONObject(card).getLong("due_ms") - now) / 86_400_000L)
                    val note = view.optString("note")
                    msg = (drill.grade() != "again") to ("Hat bitti · ${view.getInt("mistakes")} hata · sonraki tekrar: " +
                        (if (days <= 0) "bugün" else "$days gün sonra") + if (note.isNotEmpty()) "\n$note" else "")
                    if (SyncClient.loggedIn) syncQuietly(scope)
                }
            },
        )
        msg?.let { (ok, text) ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = if (ok) Color(0x3398BC49) else Color(0x33CA3431))) {
                Text(text, Modifier.padding(10.dp))
            }
        }
        if (finished) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNext(rep, line) }) { Text("Sıradaki hat") }
        }
    }
}
