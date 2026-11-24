package dev.chesscrab.app.ui.learn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.board.ARROW_HINT
import dev.chesscrab.app.ui.board.Arrow
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.board.Flash
import dev.chesscrab.app.ui.board.Sounds
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.CoachBubble
import dev.chesscrab.app.ui.components.Confetti
import dev.chesscrab.app.ui.components.ProgressRing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import uniffi.chesscrab_ffi.ChessGame
import uniffi.chesscrab_ffi.LessonPlayer
import uniffi.chesscrab_ffi.RepertoireDrill
import uniffi.chesscrab_ffi.coursesJson
import uniffi.chesscrab_ffi.puzzlesJson
import uniffi.chesscrab_ffi.repertoiresJson
import uniffi.chesscrab_ffi.srsReviewJson

fun progressOf(id: String): JSONObject? = Crab.store.get("lesson_progress", id)?.let { JSONObject(it) }
fun cardOf(id: String): JSONObject? = Crab.store.get("srs_cards", id)?.let { JSONObject(it) }
fun isDue(cardId: String, now: Long = System.currentTimeMillis()) = cardOf(cardId)?.let { it.getLong("due_ms") <= now } ?: true

private fun syncLater() { if (SyncClient.loggedIn) CoroutineScope(Dispatchers.IO).launch { SyncClient.sync() } }

private val COURSE_COLORS = listOf(listOf(Color(0xFF5D8A35), Color(0xFF81B64C)), listOf(Color(0xFF8A4B2B), Color(0xFFE07B39)))

/** Kurs listesi: kurs kartları, bölümler açılır-kapanır, dersler yıldızlı */
@Composable
fun LessonsList(modifier: Modifier, onOpen: (String) -> Unit) {
    val courses = remember { JSONArray(coursesJson()).objects() }
    var expanded by rememberSaveable { mutableStateOf("prensipler") }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        courses.forEachIndexed { ci, c ->
            item(key = c.getString("id")) {
                val all = c.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() }
                val done = all.count { progressOf(it.getString("id"))?.optBoolean("completed") == true }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(COURSE_COLORS[ci % 2])).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.getString("title"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(c.getString("description"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 3)
                    }
                    Spacer(Modifier.width(10.dp))
                    ProgressRing(if (all.isEmpty()) 0f else done / all.size.toFloat(), 54.dp, Color.White, "$done/${all.size}")
                }
            }
            for (ch in c.getJSONArray("chapters").objects()) {
                val chId = ch.getString("id")
                val lessons = ch.getJSONArray("lessons").objects()
                item(key = "$chId-h") {
                    val done = lessons.count { progressOf(it.getString("id"))?.optBoolean("completed") == true }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Crabs.Card)
                            .clickable { expanded = if (expanded == chId) "" else chId }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.getString("title"), fontWeight = FontWeight.Bold)
                            Text("$done / ${lessons.size} ders", fontSize = 12.sp, color = if (done == lessons.size) Crabs.Green else Crabs.Muted)
                        }
                        Icon(if (expanded == chId) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Crabs.Muted)
                    }
                }
                if (expanded == chId) {
                    item(key = "$chId-d") { Text(ch.getString("description"), fontSize = 12.sp, color = Crabs.Muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) }
                    lessons.forEachIndexed { li, l ->
                        item(key = l.getString("id")) {
                            val p = progressOf(l.getString("id"))
                            val doneL = p?.optBoolean("completed") == true
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Crabs.Surface)
                                    .clickable { onOpen(l.getString("id")) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (doneL) Crabs.Green else Crabs.Card),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (doneL) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    else Text("${li + 1}", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(l.getString("title"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(l.getString("summary"), fontSize = 12.sp, color = Crabs.Muted, maxLines = 2)
                                }
                                if (doneL) {
                                    val s = p!!.optInt("stars")
                                    Text("★".repeat(s) + "☆".repeat(3 - s), color = Crabs.Gold, fontSize = 13.sp)
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

/** İlk tamamlanmamış ders (ana sayfa "derse devam et" kartı için) */
fun nextLesson(): Pair<String, String>? {
    for (c in JSONArray(coursesJson()).objects()) for (ch in c.getJSONArray("chapters").objects()) for (l in ch.getJSONArray("lessons").objects()) {
        if (progressOf(l.getString("id"))?.optBoolean("completed") != true) return l.getString("id") to l.getString("title")
    }
    return null
}

@Composable
fun LessonPlayerScreen(lessonId: String, modifier: Modifier, onExit: () -> Unit, onNext: (String) -> Unit) {
    val player = remember(lessonId) { LessonPlayer(lessonId) }
    var view by remember(lessonId) { mutableStateOf(JSONObject(player.viewJson())) }
    var wrong by remember(lessonId) { mutableStateOf<String?>(null) }
    var hint by remember(lessonId) { mutableStateOf<String?>(null) }
    var flash by remember(lessonId) { mutableStateOf<Flash?>(null) }
    var shake by remember(lessonId) { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    var saved by remember(lessonId) { mutableStateOf(false) }

    fun reload() { view = JSONObject(player.viewJson()) }
    val mode = view.getString("mode")
    if (mode == "done" && !saved) {
        saved = true
        Sounds.play(Sounds.S.END)
        val prev = progressOf(lessonId)
        val stars = maxOf(player.stars().toInt(), prev?.optInt("stars") ?: 0)
        Crab.store.put("lesson_progress", lessonId, JSONObject().put("lesson_id", lessonId).put("completed", true).put("stars", stars)
            .put("best_mistakes", minOf(player.mistakes().toInt(), prev?.optInt("best_mistakes", 99) ?: 99)).put("completed_ms", System.currentTimeMillis()).toString())
        syncLater()
    }
    val total = view.getInt("total").coerceAtLeast(1)
    val prog by animateFloatAsState(if (mode == "done") 1f else view.getInt("index") / total.toFloat(), tween(500), label = "p")

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✕", fontSize = 20.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onExit).padding(end = 12.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Crabs.Card)) {
                    Box(Modifier.fillMaxWidth(prog).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Crabs.Green))
                }
                Spacer(Modifier.width(10.dp))
                Text("${minOf(view.getInt("index") + 1, total)}/$total", color = Crabs.Muted, fontSize = 13.sp)
            }
            Text(player.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(horizontal = 8.dp)) {
                ChessBoard(
                    fen = view.getString("fen"),
                    whiteBottom = view.getString("orientation") == "white",
                    legal = view.getJSONArray("legal_moves").strings(),
                    lastMove = view.optStr("last_move"),
                    arrows = view.getJSONArray("arrows").strings().map { Arrow(it) },
                    highlights = view.getJSONArray("highlights").strings(),
                    flash = flash, shake = shake,
                    onMove = { uci ->
                        val fb = JSONObject(player.submitMove(uci))
                        val verdict = fb.getString("verdict")
                        token++
                        if (verdict == "correct" || verdict == "also_good") {
                            wrong = null; hint = null
                            Sounds.play(Sounds.S.CORRECT)
                            flash = Flash(uci.substring(2, 4), Crabs.Green, token)
                        } else {
                            Sounds.play(Sounds.S.WRONG)
                            flash = Flash(uci.substring(2, 4), Color(0xFFFA412D), token)
                            shake++
                            wrong = fb.getString("message")
                        }
                        reload()
                    },
                )
            }
            Column(Modifier.padding(14.dp)) {
                val feedback = view.optStr("feedback")
                val quiz = view.optJSONObject("quiz")
                when {
                    mode == "done" -> CoachBubble("Dersi bitirdin! " + "★".repeat(player.stars().toInt()) + "☆".repeat(3 - player.stars().toInt()) +
                        if (player.mistakes() == 0u) " Hiç hata yapmadın, harika!" else " ${player.mistakes()} hata yaptın; tekrar edip 3 yıldız alabilirsin.", title = "Tebrikler!")
                    feedback != null -> CoachBubble(feedback, cls = "best", title = "Doğru!")
                    wrong != null -> CoachBubble(wrong!!, cls = "mistake", title = "Tekrar dene")
                    hint != null -> CoachBubble(hint!!, title = "İpucu")
                    else -> CoachBubble(view.getString("text"))
                }
                if (quiz != null && feedback == null && mode != "done") {
                    Spacer(Modifier.height(10.dp))
                    Text(quiz.getString("question"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    quiz.getJSONArray("options").strings().forEachIndexed { i, o ->
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Crabs.Card)
                                .border(1.dp, Color(0xFF4F4C48), RoundedCornerShape(10.dp))
                                .clickable {
                                    val r = JSONObject(player.answer(i.toUInt()))
                                    if (r.getBoolean("correct")) { Sounds.play(Sounds.S.CORRECT); wrong = null } else { Sounds.play(Sounds.S.WRONG); shake++; wrong = r.getString("message") }
                                    reload()
                                }.padding(14.dp)
                        ) { Text(o, fontSize = 15.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                when {
                    mode == "done" -> {
                        val next = nextLesson()
                        if (next != null && next.first != lessonId) BigButton("Sonraki ders: ${next.second}") { onNext(next.first) }
                        Spacer(Modifier.height(8.dp))
                        BigButton("Derslere dön", color = Crabs.Card, onClick = onExit)
                    }
                    mode == "read" || feedback != null -> BigButton("Devam") { player.next(); wrong = null; hint = null; reload() }
                    view.getBoolean("has_hint") -> BigButton("İpucu", color = Crabs.Card) { hint = player.hint(); reload() }
                }
            }
        }
        if (mode == "done") Confetti()
    }
}

/** Repertuvar listesi */
@Composable
fun RepertoireList(modifier: Modifier, onDrill: (String, Int) -> Unit) {
    val reps = remember { JSONArray(repertoiresJson()).objects() }
    val now = System.currentTimeMillis()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            CoachBubble("Rakibin hamleleri kendiliğinden oynanır; sen repertuvarındaki hamleleri hatırla. Her hattı unutmadan hemen önce tekrar ettiririm.", modifier = Modifier.padding(top = 14.dp))
        }
        for (r in reps) {
            item(key = r.getString("id")) {
                val lines = r.getJSONArray("lines").objects()
                val due = lines.count { isDue(it.getString("card_id"), now) }
                val learned = lines.count { cardOf(it.getString("card_id")) != null }
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Crabs.Card).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(learned / lines.size.toFloat(), 44.dp, label = "$learned")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.getString("title"), fontWeight = FontWeight.Bold)
                            Text(if (due > 0) "$due hat tekrar bekliyor" else "Bugünlük bitti ✓", fontSize = 12.sp, color = if (due > 0) Crabs.Orange else Crabs.Green)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    BigButton(if (due > 0) "Çalış ($due)" else "Yine de çalış", color = if (due > 0) Crabs.Green else Crabs.CardHi) {
                        val i = lines.indexOfFirst { isDue(it.getString("card_id"), now) }.let { if (it < 0) 0 else it }
                        onDrill(r.getString("id"), i)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DrillScreen(repId: String, line: Int, modifier: Modifier, onNext: (Int?) -> Unit, onExit: () -> Unit) {
    val rep = remember(repId) { JSONArray(repertoiresJson()).objects().first { it.getString("id") == repId } }
    val drill = remember(repId, line) { RepertoireDrill(repId, line.toUInt()) }
    var view by remember(drill) { mutableStateOf(JSONObject(drill.viewJson())) }
    var say by remember(drill) { mutableStateOf<Pair<String?, String>>(null to "Sıra sende: repertuvarındaki hamleyi oyna.") }
    var flash by remember(drill) { mutableStateOf<Flash?>(null) }
    var shake by remember(drill) { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    var finished by remember(drill) { mutableStateOf(false) }
    val lineObj = rep.getJSONArray("lines").getJSONObject(line)
    val prog = view.getJSONArray("progress")

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✕", fontSize = 20.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onExit).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(lineObj.getString("name"), fontWeight = FontWeight.Bold)
                Text(rep.getString("title"), fontSize = 12.sp, color = Crabs.Muted)
            }
            Text("${prog.getInt(0)}/${prog.getInt(1)}", color = Crabs.Muted)
        }
        Box(Modifier.padding(horizontal = 8.dp)) {
            ChessBoard(
                fen = view.getString("fen"), whiteBottom = view.getString("orientation") == "white",
                legal = view.getJSONArray("legal_moves").strings(), lastMove = view.optStr("last_move"),
                flash = flash, shake = shake,
                onMove = { uci ->
                    val fb = JSONObject(drill.submit(uci))
                    token++
                    if (!fb.getBoolean("correct")) {
                        Sounds.play(Sounds.S.WRONG); shake++
                        flash = Flash(uci.substring(2, 4), Color(0xFFFA412D), token)
                        say = "mistake" to "Repertuvarındaki hamle ${fb.optStr("expected_san")}. Onu oyna."
                    } else {
                        Sounds.play(Sounds.S.MOVE)
                        flash = Flash(uci.substring(2, 4), Crabs.Green, token)
                        say = "best" to ("Doğru!" + (fb.optStr("reply_san")?.let { " Rakip: $it" } ?: ""))
                    }
                    view = JSONObject(drill.viewJson())
                    if (fb.getBoolean("done") && !finished) {
                        finished = true
                        Sounds.play(Sounds.S.END)
                        val now = System.currentTimeMillis()
                        val card = srsReviewJson(Crab.store.get("srs_cards", drill.cardId()), drill.cardId(), drill.grade(), now)
                        Crab.store.put("srs_cards", drill.cardId(), card)
                        val days = (JSONObject(card).getLong("due_ms") - now) / 86_400_000L
                        say = (if (drill.grade() == "again") "mistake" else "best") to "Hat bitti · ${view.getInt("mistakes")} hata · sonraki tekrar: ${if (days <= 0) "bugün" else "$days gün sonra"}. ${view.optString("note")}"
                        syncLater()
                    }
                },
            )
        }
        Column(Modifier.padding(14.dp)) {
            CoachBubble(say.second, cls = say.first, title = say.first?.let { if (it == "best") "Doğru" else "Dikkat" })
            if (finished) {
                Spacer(Modifier.height(12.dp))
                val lines = rep.getJSONArray("lines").objects()
                val nxt = lines.indexOfFirst { it.getInt("index") != line && isDue(it.getString("card_id")) }
                if (nxt >= 0) BigButton("Sıradaki hat") { onNext(nxt) }
                Spacer(Modifier.height(8.dp))
                BigButton("Bitir", color = Crabs.Card, onClick = onExit)
            }
        }
    }
}

/** Derslerden seçilen günün bulmacası */
fun dailyPuzzle(dayOffset: Int = 0): JSONObject {
    val ps = JSONArray(puzzlesJson()).objects()
    val day = (System.currentTimeMillis() / 86_400_000L).toInt() + dayOffset
    return ps[Math.floorMod(day * 7, ps.size)]
}

@Composable
fun PuzzleScreen(modifier: Modifier, onExit: () -> Unit) {
    var offset by remember { mutableIntStateOf(0) }
    val pz = remember(offset) { dailyPuzzle(offset) }
    val solution = remember(pz) { pz.getJSONArray("moves").strings() }
    val game = remember(pz) { ChessGame.fromFen(pz.getString("fen")) }
    var view by remember(pz) { mutableStateOf(GameView.parse(game.stateJson())) }
    var idx by remember(pz) { mutableIntStateOf(0) }
    var say by remember(pz) { mutableStateOf<Pair<String?, String>>(null to pz.getString("prompt").ifBlank { "En iyi hamleyi bul." }) }
    var flash by remember(pz) { mutableStateOf<Flash?>(null) }
    var shake by remember(pz) { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    var showSol by remember(pz) { mutableStateOf(false) }
    val solved = idx >= solution.size
    val myWhite = remember(pz) { GameView.parse(ChessGame.fromFen(pz.getString("fen")).stateJson()).turnWhite }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember(pz) { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✕", fontSize = 20.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onExit).padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (offset == 0) "Günün bulmacası" else "Bulmaca", fontWeight = FontWeight.Bold)
                    Text("${if (myWhite) "Beyaz" else "Siyah"} oynar · ${pz.getString("lesson_title")}", fontSize = 12.sp, color = Crabs.Muted)
                }
            }
            Box(Modifier.padding(horizontal = 8.dp)) {
                ChessBoard(
                    fen = view.fen, whiteBottom = myWhite, legal = if (solved || busy) emptyList() else view.legal, lastMove = view.lastMove, check = view.check,
                    arrows = if (showSol && !solved) listOf(Arrow(solution[idx], ARROW_HINT)) else emptyList(),
                    flash = flash, shake = shake,
                    onMove = { uci ->
                        token++
                        if (uci == solution[idx]) {
                            view = GameView.parse(game.play(uci)); idx++
                            flash = Flash(uci.substring(2, 4), Crabs.Green, token)
                            if (idx < solution.size) {
                                Sounds.forSan(view.sans.lastOrNull())
                                say = "best" to "Doğru!"
                                busy = true
                                scope.launch {
                                    kotlinx.coroutines.delay(550)
                                    view = GameView.parse(game.play(solution[idx])); idx++
                                    Sounds.forSan(view.sans.lastOrNull())
                                    say = "best" to "Doğru! Rakip ${view.sans.last()} oynadı. Devam et."
                                    busy = false
                                }
                            } else {
                                Sounds.play(Sounds.S.CORRECT)
                                say = "brilliant" to "Çözdün! ${view.sans.lastOrNull() ?: ""}"
                            }
                            showSol = false
                        } else {
                            Sounds.play(Sounds.S.WRONG); shake++
                            flash = Flash(uci.substring(2, 4), Color(0xFFFA412D), token)
                            say = "mistake" to "Bu değil. Şahları, alımları ve tehditleri kontrol et."
                        }
                    },
                )
            }
            Column(Modifier.padding(14.dp)) {
                CoachBubble(say.second, cls = say.first)
                Spacer(Modifier.height(12.dp))
                if (solved) BigButton("Yeni bulmaca") { offset++ }
                else BigButton("Çözümü göster", color = Crabs.Card) { showSol = true }
            }
        }
        if (solved) Confetti()
    }
}

