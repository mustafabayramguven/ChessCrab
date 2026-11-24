package dev.chesscrab.app.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.Streak
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.Display
import dev.chesscrab.app.ui.Mono
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.components.CountUpInt
import dev.chesscrab.app.ui.components.Overline
import dev.chesscrab.app.ui.components.ProgressRing
import dev.chesscrab.app.ui.components.SectionTitle
import dev.chesscrab.app.ui.components.TrendChart
import dev.chesscrab.app.ui.components.staggerIn
import org.json.JSONArray
import org.json.JSONObject
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
        val res = d.optString("result")
        val outcome = when (res) { "1-0" -> if (white) 1 else -1; "0-1" -> if (white) -1 else 1; "1/2-1/2" -> 0; else -> 2 }
        MyGame(acc, outcome, white, d.optString("opening").takeUnless { it.isBlank() || it == "null" } ?: "Bilinmeyen açılış",
            if (white) d.optString("black") else d.optString("white"), d.optLong("created_ms", g.optLong("updated_ms")))
    }.sortedBy { it.ms }
}

@Composable
fun ProgressScreen(modifier: Modifier, onBack: () -> Unit) {
    val games = remember { myGames() }
    val days = remember(Streak.version) { Streak.days() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 22.sp, color = Crabs.Muted, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp))
            Column {
                Overline("Senin denizin")
                Text("İlerleme", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        StreakCard(days, Modifier.staggerIn(0))

        SectionTitle("Doğruluk", Modifier.staggerIn(1))
        val accs = games.mapNotNull { it.accuracy?.toFloat() }.takeLast(20)
        Card(Modifier.staggerIn(2)) {
            if (accs.size < 2) {
                Text("Doğruluk eğrisi için birkaç oyunu analiz ettir. Oyun bitince koç otomatik inceler.", color = Crabs.Muted, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(accs.average()), fontFamily = Display, fontSize = 40.sp, color = Crabs.Coral)
                    Spacer(Modifier.width(8.dp))
                    Text("son ${accs.size} oyun ortalaması", color = Crabs.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    val delta = accs.takeLast(5).average() - accs.take(5).average()
                    Text((if (delta >= 0) "▲ " else "▼ ") + "%.1f".format(kotlin.math.abs(delta)), fontFamily = Mono, fontSize = 13.sp,
                        color = if (delta >= 0) Crabs.Sea else Crabs.Coral, modifier = Modifier.padding(bottom = 10.dp))
                }
                Spacer(Modifier.height(8.dp))
                TrendChart(accs)
                val w = games.filter { it.white }.mapNotNull { it.accuracy }; val b = games.filter { !it.white }.mapNotNull { it.accuracy }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("beyazla " + (if (w.isEmpty()) "–" else "%.0f".format(w.average())), color = Crabs.Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                    Text("siyahla " + (if (b.isEmpty()) "–" else "%.0f".format(b.average())), color = Crabs.Muted, fontSize = 12.sp, fontFamily = Mono)
                }
            }
        }

        SectionTitle("Sonuçlar", Modifier.staggerIn(3))
        val wins = games.count { it.outcome == 1 }; val draws = games.count { it.outcome == 0 }; val losses = games.count { it.outcome == -1 }
        Card(Modifier.staggerIn(4)) {
            Row {
                ResultStat(wins, "galibiyet", Crabs.Sea, Modifier.weight(1f))
                ResultStat(draws, "beraberlik", Crabs.Muted, Modifier.weight(1f))
                ResultStat(losses, "mağlubiyet", Crabs.Coral, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            SegmentBar(listOf(wins to Crabs.Sea, draws to Crabs.Faint, losses to Crabs.Coral))
        }

        val openings = games.groupBy { it.opening }.entries.sortedByDescending { it.value.size }.take(5)
        if (openings.isNotEmpty()) {
            SectionTitle("En çok oynadığın açılışlar", Modifier.staggerIn(5))
            Card(Modifier.staggerIn(6)) {
                openings.forEachIndexed { i, (name, gs) ->
                    if (i > 0) Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Crabs.Line))
                    val score = gs.count { it.outcome == 1 } + gs.count { it.outcome == 0 } * 0.5
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontFamily = Display, fontSize = 20.sp, color = Crabs.Sand, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            Text("${gs.size} oyun · puan %${(score * 100 / gs.size).toInt()}", color = Crabs.Muted, fontSize = 12.sp)
                        }
                        GrowBar((score / gs.size).toFloat(), Modifier.width(70.dp), Crabs.Sea)
                    }
                }
            }
        }

        val bots = games.groupBy { it.opponent }.entries.sortedByDescending { it.value.size }.take(6)
        if (bots.isNotEmpty()) {
            SectionTitle("Rakipler", Modifier.staggerIn(7))
            Card(Modifier.staggerIn(8)) {
                bots.forEachIndexed { i, (name, gs) ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${gs.count { it.outcome == 1 }}–${gs.count { it.outcome == 0 }}–${gs.count { it.outcome == -1 }}", fontFamily = Mono, fontSize = 13.sp, color = Crabs.Muted)
                    }
                }
            }
        }

        SectionTitle("Öğrenme", Modifier.staggerIn(9))
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
        Card(Modifier.staggerIn(10)) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                RingStat(lessonsDone, lessonsAll, "ders", Crabs.Coral)
                RingStat(linesLearned, linesAll, "repertuvar", Crabs.Sea)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(64.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        Text(rush, fontFamily = Display, fontSize = 30.sp, color = Crabs.Gold)
                    }
                    Overline("fırtına rekoru")
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(22.dp)).padding(16.dp)) { content() }
}

@Composable
private fun StreakCard(days: Set<Long>, modifier: Modifier) {
    val cur = Streak.current(days); val best = Streak.best(days)
    val flame = if (Motion.ambient && cur > 0) {
        rememberInfiniteTransition(label = "flame").animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "f").value
    } else 1f
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 40.sp, modifier = Modifier.graphicsLayer { scaleX = flame; scaleY = flame; alpha = if (cur > 0) 1f else 0.35f })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(CountUpInt(cur), fontFamily = Display, fontSize = 44.sp, color = Crabs.Coral)
                    Spacer(Modifier.width(8.dp))
                    Text("günlük seri", color = Crabs.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 9.dp))
                }
                Text(if (Streak.today() in days) "Bugün tamam. Yarın da gel!" else if (cur > 0) "Seriyi korumak için bugün bir şey yap." else "Bir oyun, ders ya da bulmaca seriyi başlatır.", color = Crabs.Muted, fontSize = 12.5.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$best", fontFamily = Display, fontSize = 24.sp)
                Overline("en iyi")
            }
        }
        Spacer(Modifier.height(14.dp))
        // son 5 hafta: her kare bir gün, aktif günler sırayla dolar
        val today = Streak.today()
        val start = today - 34
        val fill = remember { Animatable(0f) }
        LaunchedEffect(Unit) { fill.animateTo(35f, tween(900, easing = FastOutSlowInEasing)) }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (week in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in 0 until 7) {
                    val idx = week * 7 + d
                    val day = start + idx
                    val on = day in days && idx < fill.value
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (on) Crabs.Coral else Crabs.Line)
                            .border(if (day == today) 1.5.dp else 0.dp, if (day == today) Crabs.Sand else Color.Transparent, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultStat(n: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(CountUpInt(n), fontFamily = Display, fontSize = 30.sp, color = color)
        Overline(label)
    }
}

/** Oranlara göre bölünmüş, açılarak dolan şerit */
@Composable
private fun SegmentBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Crabs.Line)) {
        for ((n, c) in parts) if (n > 0) Box(Modifier.weight(n.toFloat() / total * a.value + 0.0001f).height(10.dp).background(c))
        if (a.value < 1f) Box(Modifier.weight(1f - a.value + 0.0001f))
    }
}

@Composable
private fun GrowBar(f: Float, modifier: Modifier, color: Color) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(f) { a.animateTo(f.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing)) }
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Crabs.Line)) {
        Box(Modifier.fillMaxWidth(a.value).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun RingStat(done: Int, all: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(if (all == 0) 0f else done / all.toFloat(), 64.dp, color, "$done")
        Spacer(Modifier.height(4.dp))
        Overline("$label / $all")
    }
}

@Suppress("unused")
private fun JSONObject.str(k: String) = optString(k)
