package dev.chesscrab.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.SectionTitle
import dev.chesscrab.app.ui.learn.dailyPuzzle
import dev.chesscrab.app.ui.learn.isDue
import dev.chesscrab.app.ui.learn.nextLesson
import dev.chesscrab.app.ui.play.PlayModel
import org.json.JSONArray
import uniffi.chesscrab_ffi.repertoiresJson

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
    val name = SyncClient.username.ifEmpty { "" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🦀", fontSize = 30.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("ChessCrab", style = MaterialTheme.typography.headlineMedium)
                Text(if (name.isNotEmpty()) "Hoş geldin, $name" else "Satranç, cebinde ve çevrimdışı", color = Crabs.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (play.started && !play.view.over) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Crabs.Card).clickable(onClick = onContinueGame).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(90.dp)) { ChessBoard(play.view.fen, play.whiteBottom, emptyList(), coordinates = false) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Süren oyun", color = Crabs.Muted, fontSize = 12.sp)
                    Text("vs ${play.bot.emoji} ${play.bot.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${play.view.sans.size / 2 + 1}. hamle · ${if (play.myTurn) "sıra sende" else "rakipte"}", color = Crabs.Green, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        BigButton("Oyna", onClick = onPlay)
        Spacer(Modifier.height(8.dp))
        BigButton("Oyun incele (PGN)", color = Crabs.Card, onClick = onReviewPgn)

        StatsCard()

        SectionTitle("Öğren")
        val next = remember { nextLesson() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(
                Modifier.weight(1f), "📘", if (next != null) "Derse devam" else "Tüm dersler bitti!", next?.second ?: "Tekrar etmek ister misin?",
                listOf(Color(0xFF5D8A35), Color(0xFF81B64C)),
            ) { next?.let { onLesson(it.first) } }
            val due = remember { JSONArray(repertoiresJson()).objects().sumOf { r -> r.getJSONArray("lines").objects().count { isDue(it.getString("card_id")) } } }
            Tile(Modifier.weight(1f), "🧠", "Repertuvar", if (due > 0) "$due hat tekrar bekliyor" else "Bugünlük tamam ✓", listOf(Color(0xFF8A4B2B), Color(0xFFE07B39)), onClick = onRepertoire)
        }
        Spacer(Modifier.height(10.dp))
        val pz = remember { dailyPuzzle() }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Crabs.Card).clickable(onClick = onPuzzle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(96.dp)) {
                val white = pz.getString("fen").split(' ').getOrNull(1) != "b"
                ChessBoard(pz.getString("fen"), white, emptyList(), coordinates = false)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("🧩 Günün bulmacası", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(pz.getString("lesson_title"), color = Crabs.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Çöz →", color = Crabs.Green, fontWeight = FontWeight.Bold)
            }
        }

        val games = remember { JSONArray(Crab.store.listJson("games")).objects().take(5) }
        if (games.isNotEmpty()) {
            SectionTitle("Son oyunlar")
            for (g in games) {
                val d = g.getJSONObject("data")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Crabs.Surface)
                        .clickable { onOpenGame(d.getString("pgn"), g.getString("id")) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val res = d.optString("result")
                    val badge = when (res) { "1-0" -> "1-0"; "0-1" -> "0-1"; "1/2-1/2" -> "½"; else -> "*" }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Crabs.Card), contentAlignment = Alignment.Center) { Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${d.optString("white")} – ${d.optString("black")}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Text(d.optString("opening", ""), color = Crabs.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (d.has("white_accuracy")) Text("%.0f / %.0f".format(d.getDouble("white_accuracy"), d.getDouble("black_accuracy")), color = Crabs.Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Tile(modifier: Modifier, emoji: String, title: String, sub: String, colors: List<Color>, onClick: () -> Unit) {
    Column(
        modifier.height(132.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(emoji, fontSize = 30.sp)
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
            Text(sub, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun StatsCard() {
    val games = remember { JSONArray(Crab.store.listJson("games")).objects().map { it.getJSONObject("data") } }
    val me = SyncClient.username.ifEmpty { "Sen" }
    val accs = games.mapNotNull { d ->
        if (!d.has("white_accuracy")) null
        else if (d.optString("black") == me) d.optDouble("black_accuracy") else d.optDouble("white_accuracy")
    }.filter { !it.isNaN() }
    val wins = games.count { d -> (d.optString("result") == "1-0" && d.optString("white") == me) || (d.optString("result") == "0-1" && d.optString("black") == me) }
    val lessons = remember { JSONArray(Crab.store.listJson("lesson_progress")).length() }
    if (games.isEmpty() && lessons == 0) return
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Crabs.Card).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("${games.size}", "oyun")
        Stat("$wins", "galibiyet")
        Stat(if (accs.isEmpty()) "-" else "%" + "%.0f".format(accs.takeLast(10).average()), "son doğruluk")
        Stat("$lessons", "ders")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(label, color = Crabs.Muted, fontSize = 12.sp)
    }
}
