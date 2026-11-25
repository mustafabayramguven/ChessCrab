package dev.chesscrab.app.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.Streak
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.kare.*
import org.json.JSONArray
import uniffi.chesscrab_ffi.coursesJson
import uniffi.chesscrab_ffi.repertoiresJson

/** Kayıtlı bir oyunun "benim" açımdan özeti */
private data class MyGame(val accuracy: Double?, val outcome: Int, val white: Boolean, val opening: String, val opponent: String, val ms: Long)

private fun myGames(): List<MyGame> {
    val me = SyncClient.username.ifEmpty { "Sen" }
    return JSONArray(Crab.store.listJson("games")).objects().mapNotNull { g ->
        val d = g.getJSONObject("data")
        val white = when (me) { d.optString("white") -> true; d.optString("black") -> false; else -> return@mapNotNull null }
        val acc = d.optDouble(if (white) "white_accuracy" else "black_accuracy").takeUnless { it.isNaN() }
        val outcome = when (d.optString("result")) { "1-0" -> if (white) 1 else -1; "0-1" -> if (white) -1 else 1; "1/2-1/2" -> 0; else -> 2 }
        MyGame(acc, outcome, white, d.optString("opening").takeUnless { it.isBlank() || it == "null" } ?: "Bilinmeyen açılış",
            if (white) d.optString("black") else d.optString("white"), d.optLong("created_ms", g.optLong("updated_ms")))
    }.sortedBy { it.ms }
}

@Composable
fun ProgressScreen(modifier: Modifier, onBack: () -> Unit) {
    val games = remember { myGames() }
    val days = remember(Streak.version) { Streak.days() }
    Column(modifier.fillMaxSize()) {
        TopBar("İlerleme", "PRG 007", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---- seri
            val cur = Streak.current(days); val best = Streak.best(days); val today = Streak.today()
            SectionLabel("Günlük seri", trailing = "en iyi $best")
            Column(Modifier.fillMaxWidth().background(K.Surface).hairlineTop().hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(countUp(cur.toFloat(), 0), style = KType.mono.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold), color = K.Ink)
                    Spacer(Modifier.width(8.dp))
                    Text("GÜN", style = KType.label, color = K.Ink2, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text(if (today in days) "Bugün tamam" else if (cur > 0) "Seriyi bugün koru" else "Seri başlamadı", style = KType.desc, color = K.Ink2, modifier = Modifier.padding(bottom = 6.dp))
                }
                // son 5 hafta: her hücre bir gün, aktif günler sırayla dolar
                val start = today - 34
                val fill = remember { Animatable(if (Motion.reduced) 35f else 0f) }
                LaunchedEffect(Unit) { fill.animateTo(35f, tween(700, easing = Motion.Expo)) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (week in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until 7) {
                            val i = week * 7 + d
                            val day = start + i
                            val on = day in days && i < fill.value
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).background(if (on) K.Ink else K.CellOff)
                                    .border(if (day == today) 1.5.dp else 1.dp, if (day == today) K.Yellow else if (on) K.Ink else K.CellLine),
                            )
                        }
                    }
                }
            }

            // ---- doğruluk
            val accs = games.mapNotNull { it.accuracy?.toFloat() }.takeLast(20)
            SectionLabel("Doğruluk", trailing = "son ${accs.size} oyun")
            Column(Modifier.fillMaxWidth().background(K.Base).hairlineTop().hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (accs.size < 2) {
                    Text("Doğruluk eğrisi için birkaç oyunu analiz ettir; oyun bitince koç otomatik inceler.", style = KType.desc, color = K.Ink2)
                } else {
                    TrendChart(accs)
                    Spacer(Modifier.height(6.dp))
                    val delta = accs.takeLast(5).average() - accs.take(5).average()
                    StatLine("Ortalama", "%" + "%.1f".format(accs.average()))
                    StatLine("Son 5 / ilk 5", (if (delta >= 0) "+" else "") + "%.1f".format(delta), valueColor = if (delta >= 0) K.Ink else K.Red)
                    val w = games.filter { it.white }.mapNotNull { it.accuracy }; val b = games.filter { !it.white }.mapNotNull { it.accuracy }
                    StatLine("Beyazla", if (w.isEmpty()) "—" else "%.1f".format(w.average()))
                    StatLine("Siyahla", if (b.isEmpty()) "—" else "%.1f".format(b.average()))
                }
            }

            // ---- sonuçlar
            val wins = games.count { it.outcome == 1 }; val draws = games.count { it.outcome == 0 }; val losses = games.count { it.outcome == -1 }
            SectionLabel("Sonuçlar", trailing = "${games.size} oyun")
            Column(Modifier.fillMaxWidth().background(K.Base).hairlineTop().hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentTrack(listOf(wins to K.Ink, draws to K.Industrial, losses to K.Red))
                StatLine("Galibiyet", "$wins")
                StatLine("Beraberlik", "$draws")
                StatLine("Mağlubiyet", "$losses", valueColor = if (losses > 0) K.Red else K.Ink)
            }

            // ---- açılışlar
            val openings = games.groupBy { it.opening }.entries.sortedByDescending { it.value.size }.take(5)
            if (openings.isNotEmpty()) {
                SectionLabel("En çok oynadığın açılışlar")
                for ((name, gs) in openings) {
                    val score = gs.count { it.outcome == 1 } + gs.count { it.outcome == 0 } * 0.5
                    val pct = (score * 100 / gs.size).toInt()
                    GameRow(title = name, sub = "${gs.size} oyun · puan %$pct", badge = "%$pct", badgeColor = if (pct >= 50) K.Ink else K.LineStrong)
                }
                Hairline()
            }

            // ---- rakipler
            val bots = games.groupBy { it.opponent }.entries.sortedByDescending { it.value.size }.take(6)
            if (bots.isNotEmpty()) {
                SectionLabel("Rakipler", trailing = "G–B–M")
                for ((name, gs) in bots) {
                    GameRow(title = name, sub = "${gs.size} oyun", badge = "${gs.count { it.outcome == 1 }}–${gs.count { it.outcome == 0 }}–${gs.count { it.outcome == -1 }}")
                }
                Hairline()
            }

            // ---- öğrenme
            SectionLabel("Öğrenme")
            val (lessonsDone, lessonsAll) = remember {
                val all = JSONArray(coursesJson()).objects().flatMap { c -> c.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() } }
                val done = JSONArray(Crab.store.listJson("lesson_progress")).objects().count { it.getJSONObject("data").optBoolean("completed") }
                done to all.size
            }
            val (linesLearned, linesAll) = remember {
                val cards = JSONArray(Crab.store.listJson("srs_cards")).objects().map { it.getJSONObject("data") }
                val all = JSONArray(repertoiresJson()).objects().sumOf { it.getJSONArray("lines").length() }
                cards.count { it.optInt("reps") >= 2 } to all
            }
            val rush = remember { Crab.setting("rush_best", "0") }
            Column(Modifier.fillMaxWidth().background(K.Base).hairlineTop().hairlineBottom().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine("Ders", "$lessonsDone / $lessonsAll")
                ProgressTrack(if (lessonsAll == 0) 0f else lessonsDone / lessonsAll.toFloat())
                Spacer(Modifier.height(4.dp))
                StatLine("Repertuvar hattı", "$linesLearned / $linesAll")
                ProgressTrack(if (linesAll == 0) 0f else linesLearned / linesAll.toFloat(), color = K.Blue)
                Spacer(Modifier.height(4.dp))
                StatLine("Fırtına rekoru", rush)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Oranlara bölünmüş, soldan açılan iz */
@Composable
private fun SegmentTrack(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(480, easing = Motion.Expo)) }
    Row(Modifier.fillMaxWidth().height(8.dp).background(K.Track).border(1.dp, K.LineSubtle)) {
        for ((n, c) in parts) if (n > 0) Box(Modifier.weight(n.toFloat() / total * a.value + 0.0001f).fillMaxHeight().background(c))
        if (a.value < 1f) Box(Modifier.weight(1f - a.value + 0.0001f))
    }
}
