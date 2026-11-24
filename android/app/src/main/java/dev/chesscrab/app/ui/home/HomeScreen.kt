package dev.chesscrab.app.ui.home

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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.Display
import dev.chesscrab.app.ui.Mono
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.components.Overline
import dev.chesscrab.app.ui.components.SectionTitle
import dev.chesscrab.app.ui.components.WaveBackdrop
import dev.chesscrab.app.ui.learn.dailyPuzzle
import dev.chesscrab.app.ui.learn.isDue
import dev.chesscrab.app.ui.learn.nextLesson
import dev.chesscrab.app.ui.play.BOTS
import dev.chesscrab.app.ui.play.PlayModel
import org.json.JSONArray
import uniffi.chesscrab_ffi.repertoiresJson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

@Composable
fun HomeScreen(
    play: PlayModel,
    modifier: Modifier,
    onPlay: () -> Unit,
    onContinueGame: () -> Unit,
    onLesson: (String) -> Unit,
    onPuzzle: () -> Unit,
    onRepertoire: () -> Unit,
    onReviewPgn: () -> Unit,
    onOpenGame: (String, String) -> Unit,
) {
    val name = SyncClient.username
    val now = remember { LocalDateTime.now() }
    val greeting = when (now.hour) { in 5..11 -> "Günaydın"; in 12..17 -> "İyi günler"; in 18..22 -> "İyi akşamlar"; else -> "Gece kuşu" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", TR)))
                Spacer(Modifier.height(4.dp))
                Text(if (name.isNotEmpty()) "$greeting,\n$name." else "$greeting.", style = MaterialTheme.typography.displaySmall)
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Crabs.Card).border(1.dp, Crabs.Line, CircleShape), contentAlignment = Alignment.Center) {
                Text("🦀", fontSize = 26.sp)
            }
        }
        Spacer(Modifier.height(20.dp))

        // Kahraman kartı: yeni oyun
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B4351), Color(0xFF102A35), Color(0xFF0D1F29))))
                .border(1.dp, Crabs.Line, RoundedCornerShape(26.dp))
                .clickable(onClick = onPlay),
        ) {
            WaveBackdrop(Modifier.matchParentSize(), lines = 5)
            Column(Modifier.padding(20.dp)) {
                Overline("Denizde 8 rakip · 400–3000", color = Crabs.Sea)
                Spacer(Modifier.height(8.dp))
                Text("Bir tur atalım mı?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text("Her hamlen anında analiz edilir, gelgit çizgisinde görürsün.", color = Crabs.Muted, fontSize = 13.5.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Crabs.Coral).clickable(onClick = onPlay).padding(horizontal = 22.dp, vertical = 12.dp)) {
                        Text("Oyna  →", color = Crabs.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    // rakip yüzleri üst üste
                    Box(Modifier.height(34.dp).width((BOTS.size * 18 + 16).dp)) {
                        BOTS.forEachIndexed { i, b ->
                            Box(
                                Modifier.offset(x = (i * 18).dp).size(34.dp).clip(CircleShape).background(Color(0xFF0D1F29)).border(1.5.dp, b.color, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(b.emoji, fontSize = 16.sp) }
                        }
                    }
                }
            }
        }

        if (play.started && !play.view.over) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Crabs.Card).border(1.dp, Crabs.Coral.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onContinueGame).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))) { ChessBoard(play.view.fen, play.whiteBottom, emptyList(), coordinates = false) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Overline("Süren oyun", color = Crabs.Coral)
                    Text("${play.bot.emoji} ${play.bot.name}", fontFamily = Display, fontSize = 20.sp)
                    Text("${play.view.sans.size / 2 + 1}. hamle · ${if (play.myTurn) "sıra sende" else "rakip düşünüyor"}", color = Crabs.Muted, fontSize = 13.sp)
                }
            }
        }

        StatsStrip()

        SectionTitle("Bugünün çalışması")
        val next = remember { nextLesson() }
        val due = remember { JSONArray(repertoiresJson()).objects().sumOf { r -> r.getJSONArray("lines").objects().count { isDue(it.getString("card_id")) } } }
        val pz = remember { dailyPuzzle() }
        StudyRow("01", "Ders", next?.second ?: "Tüm dersler bitti, tekrar zamanı", Crabs.Coral) { next?.let { onLesson(it.first) } }
        StudyRow("02", "Repertuvar", if (due > 0) "$due hat tekrar bekliyor" else "Bugünlük tamam ✓", Crabs.Sea, onClick = onRepertoire)
        StudyRow("03", "Günün bulmacası", pz.getString("lesson_title"), Crabs.Gold, onClick = onPuzzle) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(8.dp))) {
                val white = pz.getString("fen").split(' ').getOrNull(1) != "b"
                ChessBoard(pz.getString("fen"), white, emptyList(), coordinates = false)
            }
        }
        StudyRow("04", "Oyun incele", "PGN yapıştır, cihazında analiz edilsin", Crabs.Muted, onClick = onReviewPgn)

        val games = remember { JSONArray(Crab.store.listJson("games")).objects().take(5) }
        if (games.isNotEmpty()) {
            SectionTitle("Son oyunlar")
            for (g in games) {
                val d = g.getJSONObject("data")
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenGame(d.getString("pgn"), g.getString("id")) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val res = d.optString("result")
                    val badge = when (res) { "1-0" -> "1–0"; "0-1" -> "0–1"; "1/2-1/2" -> "½"; else -> "·" }
                    Text(badge, fontFamily = Mono, fontSize = 13.sp, color = Crabs.Sand, modifier = Modifier.width(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${d.optString("white")} – ${d.optString("black")}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Text(d.optString("opening", ""), color = Crabs.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (d.has("white_accuracy")) Text("%.0f · %.0f".format(d.getDouble("white_accuracy"), d.getDouble("black_accuracy")), color = Crabs.Muted, fontSize = 12.sp, fontFamily = Mono)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Crabs.Line))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Numaralı çalışma satırı: büyük serif numara, başlık, alt metin */
@Composable
private fun StudyRow(no: String, title: String, sub: String, accent: Color, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Crabs.Card)
            .border(1.dp, Crabs.Line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(no, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = accent, modifier = Modifier.width(46.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(sub, color = Crabs.Muted, fontSize = 12.5.sp, maxLines = 2)
        }
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() } else Text("→", color = accent, fontSize = 18.sp)
    }
}

@Composable
private fun StatsStrip() {
    val games = remember { JSONArray(Crab.store.listJson("games")).objects().map { it.getJSONObject("data") } }
    val me = SyncClient.username.ifEmpty { "Sen" }
    val accs = games.mapNotNull { d ->
        if (!d.has("white_accuracy")) null
        else if (d.optString("black") == me) d.optDouble("black_accuracy") else d.optDouble("white_accuracy")
    }.filter { !it.isNaN() }
    val wins = games.count { d -> (d.optString("result") == "1-0" && d.optString("white") == me) || (d.optString("result") == "0-1" && d.optString("black") == me) }
    val lessons = remember { JSONArray(Crab.store.listJson("lesson_progress")).length() }
    if (games.isEmpty() && lessons == 0) return
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        val items = listOf("${games.size}" to "oyun", "$wins" to "galibiyet", (if (accs.isEmpty()) "–" else "%.0f".format(accs.takeLast(10).average())) to "doğruluk", "$lessons" to "ders")
        items.forEachIndexed { i, (v, l) ->
            if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(Crabs.Line))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, fontFamily = Display, fontSize = 28.sp, color = if (i == 2) Crabs.Coral else Crabs.Text)
                Overline(l)
            }
        }
    }
}
