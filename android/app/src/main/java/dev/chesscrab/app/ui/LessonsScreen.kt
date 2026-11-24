package dev.chesscrab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import uniffi.chesscrab_ffi.LessonPlayer
import uniffi.chesscrab_ffi.coursesJson

private fun progressOf(id: String): JSONObject? = Crab.store.get("lesson_progress", id)?.let { JSONObject(it) }

@Composable
fun LessonsScreen(modifier: Modifier) {
    var open by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val id = open
    if (id != null) {
        BackHandler { open = null; refresh++ }
        LessonPlayerScreen(id, modifier) { open = null; refresh++ }
        return
    }
    val courses = remember { JSONArray(coursesJson()).objects() }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        for (c in courses) {
            item(key = c.getString("id")) {
                val all = c.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() }
                val done = refresh.let { all.count { progressOf(it.getString("id"))?.optBoolean("completed") == true } }
                Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    Text(c.getString("title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(c.getString("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { if (all.isEmpty()) 0f else done / all.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Text("$done / ${all.size} ders", style = MaterialTheme.typography.labelSmall)
                }
            }
            for (ch in c.getJSONArray("chapters").objects()) {
                item(key = c.getString("id") + "/" + ch.getString("id")) {
                    Text(ch.getString("title"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                for (l in ch.getJSONArray("lessons").objects()) {
                    item(key = l.getString("id")) {
                        val p = refresh.let { progressOf(l.getString("id")) }
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { open = l.getString("id") },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.getString("title"), fontWeight = FontWeight.SemiBold)
                                    Text(l.getString("summary"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (p?.optBoolean("completed") == true) {
                                    val s = p.optInt("stars")
                                    Text("★".repeat(s) + "☆".repeat(3 - s), color = Color(0xFFF5C542))
                                }
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
fun LessonPlayerScreen(lessonId: String, modifier: Modifier, onExit: () -> Unit) {
    val player = remember(lessonId) { LessonPlayer(lessonId) }
    var view by remember(lessonId) { mutableStateOf(JSONObject(player.viewJson())) }
    var message by remember(lessonId) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var savedDone by remember(lessonId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() { view = JSONObject(player.viewJson()) }

    val mode = view.getString("mode")
    if (mode == "done" && !savedDone) {
        savedDone = true
        val prev = progressOf(lessonId)
        val stars = maxOf(player.stars().toInt(), prev?.optInt("stars") ?: 0)
        val best = minOf(player.mistakes().toInt(), prev?.optInt("best_mistakes", 99) ?: 99)
        Crab.store.put(
            "lesson_progress", lessonId,
            JSONObject().put("lesson_id", lessonId).put("completed", true).put("stars", stars)
                .put("best_mistakes", best).put("completed_ms", System.currentTimeMillis()).toString(),
        )
        if (SyncClient.loggedIn) syncQuietly(scope)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(player.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("Kapat") }
        }
        val total = view.getInt("total").coerceAtLeast(1)
        LinearProgressIndicator(progress = { view.getInt("index") / total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        ChessBoard(
            fen = view.getString("fen"),
            whiteBottom = view.getString("orientation") == "white",
            legal = view.getJSONArray("legal_moves").strings(),
            lastMove = view.optStr("last_move"),
            arrows = view.getJSONArray("arrows").strings(),
            highlights = view.getJSONArray("highlights").strings(),
            onMove = { uci ->
                val fb = JSONObject(player.submitMove(uci))
                val verdict = fb.getString("verdict")
                message = (verdict == "correct" || verdict == "also_good") to fb.getString("message")
                reload()
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(if (mode == "done") "Ders tamamlandı! " + "★".repeat(player.stars().toInt()) else view.getString("text"), style = MaterialTheme.typography.bodyLarge)

        val quiz = view.optJSONObject("quiz")
        val feedback = view.optStr("feedback")
        if (quiz != null && feedback == null) {
            Spacer(Modifier.height(8.dp))
            Text(quiz.getString("question"), fontWeight = FontWeight.Bold)
            quiz.getJSONArray("options").strings().forEachIndexed { i, o ->
                OutlinedButton(onClick = {
                    val r = JSONObject(player.answer(i.toUInt()))
                    message = r.getBoolean("correct") to r.getString("message")
                    reload()
                }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(o, modifier = Modifier.fillMaxWidth()) }
            }
        }
        (feedback?.let { true to it } ?: message?.takeIf { !it.first })?.let { (ok, msg) ->
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (ok) Color(0x3398BC49) else Color(0x33CA3431)),
            ) { Text(msg, Modifier.padding(10.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "done") {
                Button(onClick = onExit) { Text("Derslere dön") }
            } else {
                Button(enabled = mode == "read" || feedback != null, onClick = { player.next(); message = null; reload() }) { Text("Devam") }
                if (view.getBoolean("has_hint") && feedback == null) {
                    OutlinedButton(onClick = { player.hint()?.let { message = false to "💡 $it" }; reload() }) { Text("İpucu") }
                }
            }
            val mistakes = view.getInt("mistakes")
            if (mistakes > 0) Text("$mistakes hata", modifier = Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
