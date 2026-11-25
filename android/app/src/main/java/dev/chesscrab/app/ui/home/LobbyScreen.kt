package dev.chesscrab.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.Streak
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.kare.*
import dev.chesscrab.app.ui.learn.dailyPuzzle
import dev.chesscrab.app.ui.learn.isDue
import dev.chesscrab.app.ui.learn.nextLesson
import dev.chesscrab.app.ui.play.PlayModel
import dev.chesscrab.app.ui.play.TIME_CONTROLS
import org.json.JSONArray
import uniffi.chesscrab_ffi.repertoiresJson

/** Lobi: hızlı oyun, günlük seri, günün görevleri, aktif ve son maçlar */
@Composable
fun LobbyScreen(
    play: PlayModel,
    modifier: Modifier,
    onPlay: () -> Unit,
    onContinueGame: () -> Unit,
    onLesson: (String) -> Unit,
    onPuzzle: () -> Unit,
    onRush: () -> Unit,
    onProgress: () -> Unit,
    onRepertoire: () -> Unit,
    onReviewPgn: () -> Unit,
    onOpenGame: (String, String) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopBar("Lobi", "LBY 001")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---- hızlı oyun kahraman bandı
            val quick = remember { listOf(TIME_CONTROLS[1], TIME_CONTROLS[3], TIME_CONTROLS[0]) }
            var tc by remember { mutableStateOf(quick.firstOrNull { it == play.timeCtl } ?: quick[1]) }
            Column(
                Modifier.fillMaxWidth().background(K.Surface).hairlineBottom().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HIZLI OYUN", style = KType.headline, color = K.Ink, modifier = Modifier.weight(1f))
                    Tag("Çevrimdışı motor")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in quick) TcTile(if (t.baseMs == 0L) "∞" else t.label, dev.chesscrab.app.ui.play.tcName(t), tc == t, Modifier.weight(1f)) { tc = t }
                }
                SavilleButton("Rakip seç (${if (tc.baseMs == 0L) "süresiz" else tc.label})", Modifier.fillMaxWidth(), cap = K.Red) {
                    play.timeCtl = tc; onPlay()
                }
                if (play.started && !play.view.over) {
                    Row(
                        Modifier.fillMaxWidth().border(1.dp, K.Blue).background(K.BlueSoft).press(scale = 0.98f, onClick = onContinueGame)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Süren oyun · ${play.bot.name} · hamle ${play.view.sans.size / 2 + 1} · ${if (play.myTurn) "sıra sende" else "rakip düşünüyor"}",
                            style = KType.desc, color = K.Ink, modifier = Modifier.weight(1f), maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        ScanPulse()
                    }
                }
            }

            // ---- günlük seri
            val days = remember(Streak.version) { Streak.days() }
            val cur = Streak.current(days)
            val today = Streak.today()
            SectionLabel("Günlük seri", trailing = "en iyi ${Streak.best(days)}")
            SignalReadout(
                title = if (cur > 0) "$cur gün" else "Seri başlamadı",
                meta = if (today in days) "Bugün tamam · yarın da gel" else "Bir oyun, ders ya da bulmaca seriyi sürdürür",
                modifier = Modifier.hairlineTop().press(scale = 0.99f, onClick = onProgress),
            ) {
                val last7 = (6 downTo 0).map { today - it }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in last7) {
                        val on = d in days
                        val isToday = d == today
                        Box(
                            Modifier.size(12.dp).background(if (on) K.Ink else if (isToday) K.Yellow else K.CellOff)
                                .border(1.dp, if (on) K.Ink else if (isToday) K.Yellow else K.CellLine),
                        )
                    }
                }
            }

            // ---- günün görevi
            SectionLabel("Günün görevi")
            val next = remember { nextLesson() }
            val due = remember { JSONArray(repertoiresJson()).objects().sumOf { r -> r.getJSONArray("lines").objects().count { isDue(it.getString("card_id")) } } }
            val pz = remember { dailyPuzzle() }
            val rushBest = remember { runCatching { Crab.setting("rush_best", "0") }.getOrDefault("0") }
            GameRow(
                title = next?.second ?: "Tüm dersler bitti",
                sub = if (next != null) "Sıradaki ders · çevrimdışı kurs" else "Tekrar için Öğren sekmesine göz at",
                badge = if (next != null) "Başla" else "Tamam", badgeColor = if (next != null) K.Yellow else K.LineStrong,
                badgeText = if (next != null) K.Yellow else K.Ink,
            ) { next?.let { onLesson(it.first) } }
            GameRow(
                title = "Günün bulmacası",
                sub = pz.getString("lesson_title"),
                badge = "Çöz", badgeColor = K.Yellow, badgeText = K.Yellow,
                leading = {
                    Box(Modifier.size(44.dp)) {
                        ChessBoard(pz.getString("fen"), pz.getString("fen").split(' ').getOrNull(1) != "b", emptyList(), coordinates = false)
                    }
                },
                onClick = onPuzzle,
            )
            GameRow(
                title = "Repertuvar tekrarı",
                sub = if (due > 0) "$due hat tekrar bekliyor" else "Bugünlük tamam",
                badge = if (due > 0) "$due tekrar" else "Tamam", badgeColor = if (due > 0) K.Yellow else K.LineStrong,
                badgeText = if (due > 0) K.Yellow else K.Ink, onClick = onRepertoire,
            )
            GameRow(
                title = "Bulmaca fırtınası",
                sub = "3 dakika · 3 can · rekor $rushBest",
                badge = "Başla", badgeColor = K.Red, onClick = onRush,
            )
            Hairline()

            // ---- aktif ve son maçlar
            val games = remember { JSONArray(Crab.store.listJson("games")).objects().take(6) }
            val me = SyncClient.username.ifEmpty { "Sen" }
            SectionLabel("Aktif ve son maçlar", trailing = "${games.size}")
            if (play.started && !play.view.over) {
                GameRow(
                    title = "${play.bot.name} (${play.bot.elo})",
                    sub = "${if (play.timeCtl.baseMs == 0L) "Süresiz" else play.timeCtl.label} · hamle ${play.view.sans.size / 2 + 1} · ${scoreLabel(play.whiteCp)} beyaz",
                    badge = "Devam", badgeColor = K.Blue, onClick = onContinueGame,
                )
            }
            for (g in games) {
                val d = g.getJSONObject("data")
                val iAmWhite = d.optString("white") == me
                val opp = if (iAmWhite) d.optString("black") else d.optString("white")
                val acc = if (d.has("white_accuracy")) " · %.1f doğruluk".format(if (iAmWhite || d.optString("black") != me) d.getDouble("white_accuracy") else d.getDouble("black_accuracy")) else ""
                val res = d.optString("result")
                GameRow(
                    title = opp.ifBlank { "Bilinmeyen rakip" },
                    sub = "$res$acc · ${d.optString("opening", "").takeIf { it.isNotBlank() && it != "null" } ?: "açılış yok"}",
                    badge = "İncele", badgeColor = K.Ink,
                ) { onOpenGame(d.getString("pgn"), g.getString("id")) }
            }
            GameRow(title = "PGN incele", sub = "lichess / chess.com oyununu yapıştır, cihazında analiz edilsin", badge = "Aç", onClick = onReviewPgn)
            GameRow(title = "İlerleme", sub = "Seri takvimi, doğruluk eğrisi, açılışlar, rakipler", badge = "Aç", onClick = onProgress)
            Hairline()
            Spacer(Modifier.height(16.dp))
        }
    }
}
