package dev.chesscrab.app.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.GameView
import dev.chesscrab.app.Streak
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.board.*
import dev.chesscrab.app.ui.kare.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private fun code(prefix: String, n: Int) = "$prefix ${n.toString().padStart(3, '0')}"

/** İlk tamamlanmamış ders */
fun nextLesson(): Pair<String, String>? {
    for (c in JSONArray(coursesJson()).objects()) for (ch in c.getJSONArray("chapters").objects()) for (l in ch.getJSONArray("lessons").objects()) {
        if (progressOf(l.getString("id"))?.optBoolean("completed") != true) return l.getString("id") to l.getString("title")
    }
    return null
}

/** Derslerden seçilen günün bulmacası */
fun dailyPuzzle(dayOffset: Int = 0): JSONObject {
    val ps = JSONArray(puzzlesJson()).objects()
    val day = (System.currentTimeMillis() / 86_400_000L).toInt() + dayOffset
    return ps[Math.floorMod(day * 7, ps.size)]
}

/** Öğren sekmesi: Dersler / Repertuvar / Bulmaca */
@Composable
fun LearnScreen(
    modifier: Modifier,
    tab: Int,
    onTab: (Int) -> Unit,
    onLesson: (String) -> Unit,
    onDrill: (String, Int) -> Unit,
    onPuzzle: () -> Unit,
    onRush: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopBar("Öğren", "TKT 003")
        Row(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Dersler", "Repertuvar", "Bulmaca").forEachIndexed { i, l -> SegButton(l, tab == i, Modifier.weight(1f)) { onTab(i) } }
        }
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
            fadeIn(tween(if (Motion.reduced) 120 else Motion.VIEW, easing = Motion.Expo)) togetherWith fadeOut(tween(120))
        }, label = "learn") { t ->
            when (t) {
                0 -> LessonsList(Modifier.fillMaxSize(), onLesson)
                1 -> RepertoireList(Modifier.fillMaxSize(), onDrill)
                else -> PuzzleHub(Modifier.fillMaxSize(), onPuzzle, onRush)
            }
        }
    }
}

/** Kurslar: koyu kurs bandı, kılçizgili bölüm satırları, açılınca dersler */
@Composable
fun LessonsList(modifier: Modifier, onOpen: (String) -> Unit) {
    val courses = remember { JSONArray(coursesJson()).objects() }
    var expanded by rememberSaveable { mutableStateOf("prensipler") }
    val next = remember { nextLesson()?.first }
    LazyColumn(modifier.fillMaxSize()) {
        courses.forEachIndexed { ci, c ->
            item(key = c.getString("id")) {
                val all = c.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() }
                val done = all.count { progressOf(it.getString("id"))?.optBoolean("completed") == true }
                Column(Modifier.fillMaxWidth().background(K.Surface).hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatalogCode(code("KRS", ci + 1))
                        Spacer(Modifier.weight(1f))
                        Text("$done/${all.size}", style = KType.mono, color = K.Ink)
                    }
                    Text(c.getString("title").upTr(), style = KType.headline, color = K.Ink)
                    Text(c.getString("description"), style = KType.desc, color = K.Ink2)
                    ProgressTrack(if (all.isEmpty()) 0f else done / all.size.toFloat())
                }
            }
            for ((chi, ch) in c.getJSONArray("chapters").objects().withIndex()) {
                val chId = ch.getString("id")
                val lessons = ch.getJSONArray("lessons").objects()
                item(key = "$chId-h") {
                    val done = lessons.count { progressOf(it.getString("id"))?.optBoolean("completed") == true }
                    val open = expanded == chId
                    val rot by animateFloatAsState(if (open) 90f else 0f, tween(Motion.STATE, easing = Motion.Expo), label = "chev")
                    GameRow(
                        title = ch.getString("title").let { t -> if (t.firstOrNull()?.isDigit() == true) t else "${chi + 1}. $t" },
                        sub = ch.getString("description"),
                        badge = "$done/${lessons.size}",
                        badgeColor = if (done == lessons.size) K.Ink else K.LineStrong,
                        trailing = { Icon(KIcons.Next, null, tint = K.Ink3, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot }) },
                    ) { expanded = if (open) "" else chId }
                }
                if (expanded == chId) {
                    lessons.forEachIndexed { li, l ->
                        item(key = l.getString("id")) {
                            val p = progressOf(l.getString("id"))
                            val doneL = p?.optBoolean("completed") == true
                            val isNext = l.getString("id") == next
                            GameRow(
                                title = l.getString("title"),
                                sub = l.getString("summary"),
                                bg = K.Surface,
                                badge = when { doneL -> "${p!!.optInt("stars")}/3"; isNext -> "Başla"; else -> null },
                                badgeColor = when { doneL -> K.Ink; isNext -> K.Yellow; else -> K.LineStrong },
                                badgeText = if (isNext && !doneL) K.Yellow else K.Ink,
                                leading = { Monogram((li + 1).toString().padStart(2, '0'), filled = doneL) },
                            ) { onOpen(l.getString("id")) }
                        }
                    }
                }
            }
            item { Hairline() }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun LessonPlayerScreen(lessonId: String, modifier: Modifier, onExit: () -> Unit, onNext: (String) -> Unit) {
    val player = remember(lessonId) { LessonPlayer(lessonId) }
    var view by remember(lessonId) { mutableStateOf(JSONObject(player.viewJson())) }
    var wrong by remember(lessonId) { mutableStateOf<String?>(null) }
    var hint by remember(lessonId) { mutableStateOf<String?>(null) }
    var flash by remember(lessonId) { mutableStateOf<Flash?>(null) }
    var fx by remember(lessonId) { mutableStateOf<BoardFx?>(null) }
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
        Streak.mark()
        syncLater()
    }
    val total = view.getInt("total").coerceAtLeast(1)
    val idx = if (mode == "done") total else view.getInt("index")

    Column(modifier.fillMaxSize()) {
        TopBar("Ders", code("DRS", (lessonId.hashCode() and 0x3FF) % 1000), onBack = onExit)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SignalReadout(player.title(), if (mode == "done") "Ders tamamlandı" else "Adım ${minOf(idx + 1, total)} / $total") {
                SignalCells(minOf(total, 10), if (total <= 10) idx else idx * 10 / total, active = if (mode == "done") null else (if (total <= 10) idx else idx * 10 / total))
            }
            ChessBoard(
                fen = view.getString("fen"),
                whiteBottom = view.getString("orientation") == "white",
                legal = view.getJSONArray("legal_moves").strings(),
                lastMove = view.optStr("last_move"),
                arrows = view.getJSONArray("arrows").strings().map { Arrow(it, K.Blue, dashed = true) },
                highlights = view.getJSONArray("highlights").strings(),
                flash = flash, fx = fx, shake = shake,
                onMove = { uci ->
                    val fb = JSONObject(player.submitMove(uci))
                    val verdict = fb.getString("verdict")
                    token++
                    if (verdict == "correct" || verdict == "also_good") {
                        wrong = null; hint = null
                        Sounds.play(Sounds.S.CORRECT)
                        flash = Flash(uci.substring(2, 4), FLASH_GOOD, token)
                    } else {
                        Sounds.play(Sounds.S.WRONG)
                        flash = Flash(uci.substring(2, 4), FLASH_BAD, token)
                        shake++
                        wrong = fb.getString("message")
                    }
                    reload()
                    if (view.getString("mode") == "done") view.optStr("last_move")?.let { fx = BoardFx(it.substring(2, 4), "brilliant", token) }
                },
            )
            val feedback = view.optStr("feedback")
            val quiz = view.optJSONObject("quiz")
            val stars = player.stars().toInt()
            when {
                mode == "done" -> CoachStrip("Ders tamam · $stars/3", if (player.mistakes() == 0u) "Hiç hata yapmadın." else "${player.mistakes()} hata yaptın; tekrar edip 3/3 alabilirsin.", code = "brilliant")
                feedback != null -> CoachStrip("Doğru", feedback, code = "best")
                wrong != null -> CoachStrip("Tekrar dene", wrong!!, code = "mistake")
                hint != null -> CoachStrip("İpucu", hint!!, code = null)
                else -> CoachStrip(player.title(), view.getString("text"))
            }
            if (quiz != null && feedback == null && mode != "done") {
                SectionLabel("Soru")
                Text(quiz.getString("question"), style = KType.bodyCompact, color = K.Ink, modifier = Modifier.padding(horizontal = 14.dp))
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quiz.getJSONArray("options").strings().forEachIndexed { i, o ->
                        BranchButton("${'A' + i}  ·  $o", modifier = Modifier.fillMaxWidth(), mono = false) {
                            val r = JSONObject(player.answer(i.toUInt()))
                            if (r.getBoolean("correct")) { Sounds.play(Sounds.S.CORRECT); wrong = null } else { Sounds.play(Sounds.S.WRONG); shake++; wrong = r.getString("message") }
                            reload()
                        }
                    }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    mode == "done" -> {
                        val nx = nextLesson()
                        if (nx != null && nx.first != lessonId) SavilleButton("Sonraki ders: ${nx.second}", Modifier.fillMaxWidth(), cap = K.Blue) { onNext(nx.first) }
                        SavilleButton("Derslere dön", Modifier.fillMaxWidth(), cap = K.Industrial, onClick = onExit)
                    }
                    mode == "read" || feedback != null -> SavilleButton("Devam", Modifier.fillMaxWidth(), cap = K.Blue) { player.next(); wrong = null; hint = null; reload() }
                    view.getBoolean("has_hint") -> SavilleButton("İpucu", Modifier.fillMaxWidth(), cap = K.Yellow) { hint = player.hint(); reload() }
                }
            }
        }
    }
}

/** Repertuvar listesi */
@Composable
fun RepertoireList(modifier: Modifier, onDrill: (String, Int) -> Unit) {
    val reps = remember { JSONArray(repertoiresJson()).objects() }
    val now = System.currentTimeMillis()
    LazyColumn(modifier.fillMaxSize()) {
        item {
            CoachStrip("Aralıklı tekrar", "Rakibin hamleleri kendiliğinden oynanır; sen repertuvarındakini hatırla. Her hattı unutmadan hemen önce tekrar ettiririm.")
        }
        reps.forEachIndexed { i, r ->
            item(key = r.getString("id")) {
                val lines = r.getJSONArray("lines").objects()
                val due = lines.count { isDue(it.getString("card_id"), now) }
                val learned = lines.count { cardOf(it.getString("card_id")) != null }
                GameRow(
                    title = r.getString("title"),
                    sub = "${lines.size} hat · $learned öğrenildi · ${if (due > 0) "$due tekrar bekliyor" else "bugünlük tamam"}",
                    badge = if (due > 0) "Çalış $due" else "Tekrar",
                    badgeColor = if (due > 0) K.Yellow else K.LineStrong,
                    badgeText = if (due > 0) K.Yellow else K.Ink,
                    leading = { Monogram((i + 1).toString().padStart(2, '0'), filled = due == 0 && learned == lines.size) },
                ) {
                    val li = lines.indexOfFirst { isDue(it.getString("card_id"), now) }.let { if (it < 0) 0 else it }
                    onDrill(r.getString("id"), li)
                }
            }
        }
        item { Hairline(); Spacer(Modifier.height(20.dp)) }
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

    Column(modifier.fillMaxSize()) {
        TopBar("Repertuvar", code("REP", line + 1), onBack = onExit)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SignalReadout(lineObj.getString("name"), "${rep.getString("title")} · ${view.getInt("mistakes")} hata") {
                val total = prog.getInt(1).coerceAtLeast(1)
                Text("${prog.getInt(0)}/$total", style = KType.mono, color = K.Ink)
            }
            ChessBoard(
                fen = view.getString("fen"), whiteBottom = view.getString("orientation") == "white",
                legal = view.getJSONArray("legal_moves").strings(), lastMove = view.optStr("last_move"),
                flash = flash, shake = shake,
                onMove = { uci ->
                    val fb = JSONObject(drill.submit(uci))
                    token++
                    if (!fb.getBoolean("correct")) {
                        Sounds.play(Sounds.S.WRONG); shake++
                        flash = Flash(uci.substring(2, 4), FLASH_BAD, token)
                        say = "mistake" to "Repertuvarındaki hamle ${fb.optStr("expected_san")}. Onu oyna."
                    } else {
                        Sounds.play(Sounds.S.MOVE)
                        flash = Flash(uci.substring(2, 4), FLASH_GOOD, token)
                        say = "best" to ("Doğru." + (fb.optStr("reply_san")?.let { " Rakip: $it" } ?: ""))
                    }
                    view = JSONObject(drill.viewJson())
                    if (fb.getBoolean("done") && !finished) {
                        finished = true
                        Sounds.play(Sounds.S.END)
                        val now = System.currentTimeMillis()
                        val card = srsReviewJson(Crab.store.get("srs_cards", drill.cardId()), drill.cardId(), drill.grade(), now)
                        Crab.store.put("srs_cards", drill.cardId(), card)
                        Streak.mark()
                        val days = (JSONObject(card).getLong("due_ms") - now) / 86_400_000L
                        say = (if (drill.grade() == "again") "mistake" else "best") to "Hat bitti · ${view.getInt("mistakes")} hata · sonraki tekrar ${if (days <= 0) "bugün" else "$days gün sonra"}. ${view.optString("note")}"
                        syncLater()
                    }
                },
            )
            CoachStrip(if (say.first == "mistake") "Dikkat" else if (say.first == "best") "Doğru" else lineObj.getString("name"), say.second, code = say.first)
            if (finished) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lines = rep.getJSONArray("lines").objects()
                    val nxt = lines.indexOfFirst { it.getInt("index") != line && isDue(it.getString("card_id")) }
                    if (nxt >= 0) SavilleButton("Sıradaki hat", Modifier.fillMaxWidth(), cap = K.Yellow) { onNext(nxt) }
                    SavilleButton("Bitir", Modifier.fillMaxWidth(), cap = K.Industrial, onClick = onExit)
                }
            }
        }
    }
}

/** Günün bulmacası: sinyal okuma bandı, adım bandı, tahta, koç şeridi, eylemler */
@Composable
fun PuzzleScreen(modifier: Modifier, onExit: () -> Unit) {
    var offset by remember { mutableIntStateOf(0) }
    var reset by remember { mutableIntStateOf(0) }
    val pz = remember(offset) { dailyPuzzle(offset) }
    val solution = remember(pz) { pz.getJSONArray("moves").strings() }
    val game = remember(pz, reset) { ChessGame.fromFen(pz.getString("fen")) }
    var view by remember(pz, reset) { mutableStateOf(GameView.parse(game.stateJson())) }
    var idx by remember(pz, reset) { mutableIntStateOf(0) }
    var say by remember(pz, reset) { mutableStateOf<Pair<String?, String>>(null to "Doğru taşı seç ya da sürükle; takılırsan ok ipucu al.") }
    var flash by remember(pz) { mutableStateOf<Flash?>(null) }
    var fx by remember(pz) { mutableStateOf<BoardFx?>(null) }
    var shake by remember(pz) { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    var showHint by remember(pz, reset) { mutableStateOf(false) }
    var busy by remember(pz, reset) { mutableStateOf(false) }
    val solved = idx >= solution.size
    val myWhite = remember(pz) { GameView.parse(ChessGame.fromFen(pz.getString("fen")).stateJson()).turnWhite }
    val scope = rememberCoroutineScope()
    val myMoves = (solution.size + 1) / 2
    val myDone = (idx + 1) / 2
    val streak = remember(Streak.version) { Streak.current() }

    fun play(uci: String) {
        token++
        if (uci == solution[idx]) {
            view = GameView.parse(game.play(uci)); idx++
            flash = Flash(uci.substring(2, 4), FLASH_GOOD, token)
            showHint = false
            if (idx < solution.size) {
                Sounds.forSan(view.sans.lastOrNull())
                say = "best" to "Doğru. Rakip cevap veriyor…"
                busy = true
                scope.launch {
                    delay(if (Motion.reduced) 200 else 550)
                    view = GameView.parse(game.play(solution[idx])); idx++
                    Sounds.forSan(view.sans.lastOrNull())
                    say = "best" to "Rakip ${view.sans.last()} oynadı. Devam et."
                    busy = false
                }
            } else {
                Sounds.play(Sounds.S.CORRECT)
                Streak.mark()
                fx = BoardFx(uci.substring(2, 4), if (view.check && view.over) "checkmate" else "brilliant", token)
                say = "brilliant" to "Taktik çözüldü: ${view.sans.lastOrNull() ?: ""}. ${pz.getString("prompt")}"
            }
        } else {
            Sounds.play(Sounds.S.WRONG); shake++
            flash = Flash(uci.substring(2, 4), FLASH_BAD, token)
            say = "mistake" to "Tam değil. Rakibin şahını ya da korumasız taşını hedefleyen hamleyi dene."
        }
    }

    Column(modifier.fillMaxSize()) {
        TopBar("Bulmaca", code("PZL", Math.floorMod(pz.getString("id").hashCode(), 1000)), onBack = onExit)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SignalReadout(
                "${if (offset == 0) "Günün bulmacası" else "Bulmaca ${offset + 1}"} · ${pz.getString("lesson_title")}",
                "${if (myWhite) "Beyaz" else "Siyah"} oynar · seri $streak",
            ) { SignalCells(6, minOf(streak, 6), active = if (solved) null else minOf(streak, 5)) }
            // adım bandı
            Row(Modifier.fillMaxWidth().hairlineBottom()) {
                for (i in 0 until minOf(myMoves, 4)) {
                    val st = when { i < myDone -> "solved"; i == myDone && !solved -> "current"; else -> "" }
                    Row(
                        Modifier.weight(1f).background(when (st) { "solved" -> K.Ink.copy(alpha = 0.1f); "current" -> K.Elevated; else -> K.Surface })
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Hamle ${i + 1}", style = KType.desc.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = when (st) { "solved" -> K.Ink; "current" -> K.Yellow; else -> K.Ink2 }, modifier = Modifier.weight(1f))
                        Text(if (st == "solved") "OK" else "0${i + 1}", style = KType.code, color = when (st) { "solved" -> K.Ink; "current" -> K.Yellow; else -> K.Ink2 })
                    }
                    if (i < minOf(myMoves, 4) - 1) Box(Modifier.width(1.dp).height(34.dp).background(K.LineSubtle))
                }
            }
            ChessBoard(
                fen = view.fen, whiteBottom = myWhite, legal = if (solved || busy) emptyList() else view.legal, lastMove = view.lastMove, check = view.check,
                arrows = if (showHint && !solved) listOf(Arrow(solution[idx], K.Yellow)) else emptyList(),
                hint = if (showHint && !solved) solution[idx] else null,
                flash = flash, fx = fx, shake = shake,
                onMove = { play(it) },
            )
            CoachStrip(
                when (say.first) { "brilliant" -> "Harika! Taktik çözüldü"; "mistake" -> "Tam değil"; "best" -> "Doğru"; else -> pz.getString("prompt").ifBlank { "En iyi hamleyi bul" } },
                say.second, code = say.first,
            ) {
                if (solved) SavilleButton("Sonraki", cap = K.Yellow) { offset++ }
            }
            ActionRow {
                MiniAction(KIcons.Bulb, "Ok ipucu", Modifier.weight(1f), on = showHint, enabled = !solved && !busy) {
                    showHint = true; say = null to "İpucu oku çizildi: ${solution[idx].take(2)} → ${solution[idx].drop(2).take(2)}"
                }
                MiniAction(KIcons.PlayTri, "Çözümü izle", Modifier.weight(1f), enabled = !solved && !busy) {
                    busy = true
                    scope.launch {
                        while (idx < solution.size) {
                            delay(if (Motion.reduced) 200 else 650)
                            view = GameView.parse(game.play(solution[idx])); idx++
                            Sounds.forSan(view.sans.lastOrNull())
                        }
                        say = null to "Çözüm: ${view.sans.joinToString(" ")}"
                        busy = false
                    }
                }
                MiniAction(KIcons.Reset, "Tekrar", Modifier.weight(1f), enabled = !busy) { reset++ }
            }
        }
    }
}

/** Öğren → Bulmaca: günün bulmacası ve fırtına satırları */
@Composable
fun PuzzleHub(modifier: Modifier, onDaily: () -> Unit, onRush: () -> Unit) {
    val pz = remember { dailyPuzzle() }
    val best = remember { runCatching { Crab.setting("rush_best", "0") }.getOrDefault("0") }
    val streak = remember(Streak.version) { Streak.current() }
    Column(modifier.verticalScroll(rememberScrollState())) {
        SignalReadout("Bulmaca serisi", "Her gün bir çözüm seriyi sürdürür") { SignalCells(6, minOf(streak, 6)) }
        SectionLabel("Görevler")
        GameRow(
            title = "Günün bulmacası",
            sub = "${pz.getString("lesson_title")} · ${pz.getString("prompt").take(60)}",
            badge = "Çöz", badgeColor = K.Yellow, badgeText = K.Yellow,
            leading = {
                Box(Modifier.size(64.dp)) { ChessBoard(pz.getString("fen"), pz.getString("fen").split(' ').getOrNull(1) != "b", emptyList(), coordinates = false) }
            },
            onClick = onDaily,
        )
        GameRow(title = "Bulmaca fırtınası", sub = "3 dakika · 3 can · kolaydan zora · rekor $best", badge = "Başla", badgeColor = K.Red, onClick = onRush)
        Hairline()
    }
}

