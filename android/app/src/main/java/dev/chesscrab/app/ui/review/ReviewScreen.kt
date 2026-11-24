package dev.chesscrab.app.ui.review

import dev.chesscrab.app.ui.components.forText
import dev.chesscrab.app.ui.components.staggerIn
import dev.chesscrab.app.ui.components.bounceClick
import androidx.compose.foundation.border
import dev.chesscrab.app.ui.Display
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.CLASS_ORDER
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.board.ARROW_BEST
import dev.chesscrab.app.ui.board.ARROW_HINT
import dev.chesscrab.app.ui.board.Arrow
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.board.SquareBadge
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.ClassBadge
import dev.chesscrab.app.ui.components.CoachBubble
import dev.chesscrab.app.ui.components.CountUp
import dev.chesscrab.app.ui.components.EvalGraph
import dev.chesscrab.app.ui.components.scoreLabel
import dev.chesscrab.app.ui.components.winPercent
import dev.chesscrab.app.ui.play.BoardWithEvalBar
import dev.chesscrab.app.ui.play.MoveStrip
import org.json.JSONObject

@Composable
fun ReviewScreen(m: ReviewModel, modifier: Modifier) {
    val r = m.result
    when {
        r == null -> ImportView(m, modifier)
        m.phase == Phase.Summary -> SummaryView(m, r, modifier)
        else -> StepsView(m, r, modifier)
    }
}

@Composable
private fun ImportView(m: ReviewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        dev.chesscrab.app.ui.components.Overline("Cihazında, internetsiz")
        Text("Oyun incelemesi", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (m.running) {
            CoachBubble("Oyununu hamle hamle inceliyorum. Telefonunun bütün çekirdekleri çalışıyor…", title = "Bir saniye")
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Crabs.Coral, trackColor = Crabs.Card)
            Text("%${(m.progress * 100).toInt()}", color = Crabs.Muted, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        CoachBubble("lichess veya chess.com'dan bir oyunun PGN'ini yapıştır (ya da o uygulamadan \"Paylaş → ChessCrab\"). Her hamleyi değerlendirip sana anlatacağım.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = m.pgn, onValueChange = { m.pgn = it }, label = { Text("PGN") },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Crabs.Coral),
        )
        Spacer(Modifier.height(10.dp))
        Text("Derinlik (yüksek = daha doğru, daha yavaş)", color = Crabs.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            for (d in listOf(10, 12, 14, 16, 18)) {
                dev.chesscrab.app.ui.components.Pill("$d", m.depth == d, mono = true) { m.depth = d }
            }
        }
        m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        BigButton("İncele", enabled = m.pgn.isNotBlank()) { m.run() }
    }
}

private fun summaryLine(acc: Double, bad: Int): String = when {
    acc >= 90 -> "Muhteşem bir oyun! Neredeyse motor gibi oynadın."
    acc >= 80 -> "Çok iyi oynadın. Birkaç anı birlikte inceleyelim."
    acc >= 65 -> "Fena değil! $bad önemli an var, onlardan öğrenebiliriz."
    else -> "Zorlu bir oyundu ama en çok böyle oyunlardan öğrenilir. Hatalarına bakalım."
}

@Composable
private fun SummaryView(m: ReviewModel, r: JSONObject, modifier: Modifier) {
    val me = SyncClient.username.ifEmpty { "Sen" }
    val myWhite = m.tag("Black") != me
    val mine = r.getJSONObject(if (myWhite) "white" else "black")
    val moves = m.moves
    val bad = moves.count { it.getBoolean("white") == myWhite && it.getString("class") in ReviewModel.BAD }
    val myAcc = mine.optDouble("accuracy", 0.0)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                dev.chesscrab.app.ui.components.Overline("Oyun incelemesi")
                Text("${m.tag("White") ?: "Beyaz"} – ${m.tag("Black") ?: "Siyah"}", style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            }
            Text("yeni", color = Crabs.Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.clickable { m.reset() }.padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        CoachBubble(summaryLine(myAcc, bad), title = "Koç")
        Spacer(Modifier.height(14.dp))
        // oyuncular + doğruluk
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (white in listOf(true, false)) {
                val s = r.getJSONObject(if (white) "white" else "black")
                val acc = s.optDouble("accuracy", Double.NaN)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(22.dp)).padding(vertical = 16.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (white) Crabs.Ivory else Crabs.Abyss).border(1.dp, Crabs.Faint, androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(m.tag(if (white) "White" else "Black") ?: if (white) "Beyaz" else "Siyah", fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(contentAlignment = Alignment.Center) {
                        dev.chesscrab.app.ui.components.ProgressRing(if (acc.isNaN()) 0f else acc.toFloat() / 100f, 96.dp, if (white) Crabs.Sea else Crabs.Coral)
                        Text(if (acc.isNaN()) "–" else CountUp(acc.toFloat()), fontSize = 30.sp, fontFamily = Display)
                    }
                    Spacer(Modifier.height(8.dp))
                    dev.chesscrab.app.ui.components.Overline("doğruluk")
                    if (!s.isNull("estimated_elo")) Text("~${s.getInt("estimated_elo")} performans", color = Crabs.Faint, fontSize = 11.sp, fontFamily = dev.chesscrab.app.ui.Mono)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val wins = m.evals.map { winPercent(it.getInt("cp")) }
        EvalGraph(wins, moves.map { it.getString("class") }, selected = null, height = 100.dp) { p -> m.phase = Phase.Steps; m.go(p) }
        Spacer(Modifier.height(14.dp))
        // sınıf tablosu
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(22.dp)).padding(vertical = 8.dp)) {
            for ((ci, c) in CLASS_ORDER.withIndex()) {
                if (ci > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Crabs.Line))
                val info = CLASS_INFO.getValue(c)
                val w = moves.count { it.getBoolean("white") && it.getString("class") == c }
                val b = moves.count { !it.getBoolean("white") && it.getString("class") == c }
                Row(Modifier.staggerIn(ci, 40L).fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$w", color = if (w > 0) info.color.forText() else Crabs.Faint, fontFamily = dev.chesscrab.app.ui.Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        ClassBadge(c, 20.dp); Spacer(Modifier.width(6.dp)); Text(info.label, fontSize = 14.sp)
                    }
                    Text("$b", color = if (b > 0) info.color.forText() else Crabs.Faint, fontFamily = dev.chesscrab.app.ui.Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        r.optJSONArray("opening")?.let { Text("${it.getString(0)} · ${it.getString(1)}", color = Crabs.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(16.dp))
        BigButton("İncelemeye başla") { m.phase = Phase.Steps; m.go(1) }
        if (bad > 0) {
            Spacer(Modifier.height(8.dp))
            BigButton("Hatalarından öğren ($bad)", color = Crabs.Sea) { m.learnFromMistakes(myWhite) }
        }
        Spacer(Modifier.height(8.dp))
        Text("${r.getString("engine")} · ${r.getLong("total_nodes") / 1_000_000}M düğüm · ${r.getLong("time_ms")} ms · cihazında", color = Crabs.Faint, fontSize = 11.sp)
    }
}

@Composable
private fun StepsView(m: ReviewModel, r: JSONObject, modifier: Modifier) {
    val moves = m.moves
    val evals = m.evals
    val ply = m.ply.coerceIn(0, moves.size)
    val ev = evals[ply]
    val mv = if (ply > 0) moves[ply - 1] else null
    val retry = m.retry
    val explore = m.explore
    val me = SyncClient.username.ifEmpty { "Sen" }
    val myWhite = m.tag("Black") != me

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("← özet", color = Crabs.Sand, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.clickable { m.phase = Phase.Summary; m.closeRetry(); m.closeExplore() }.padding(6.dp))
            Spacer(Modifier.weight(1f))
            dev.chesscrab.app.ui.components.Overline(
                when { retry != null -> "Daha iyisini dene"; explore != null -> "Keşif modu"; else -> "Hamle $ply / ${moves.size}" },
            )
        }
        // tahta
        val cp = when {
            explore != null -> m.exploreLines?.getJSONObject("score")?.let { s -> whiteCp(s, m.exploreView?.turnWhite ?: true) } ?: ev.getInt("cp")
            else -> ev.getInt("cp")
        }
        Box(Modifier.padding(horizontal = 8.dp)) {
            BoardWithEvalBar(true, cp, m.whiteBottom) { mod ->
                when {
                    retry != null -> ChessBoard(
                        fen = retry.shownFen ?: retry.fen, whiteBottom = m.whiteBottom, legal = if (retry.ok == true) emptyList() else retry.legal,
                        lastMove = retry.lastMove, arrows = listOfNotNull(retry.arrow?.let { Arrow(it, ARROW_HINT) }),
                        flash = m.flash, shake = m.shake, modifier = mod, onMove = { m.retryMove(it) },
                    )
                    explore != null -> {
                        val ev2 = m.exploreView!!
                        val best = m.exploreLines?.optStr("best")
                        ChessBoard(
                            fen = ev2.fen, whiteBottom = m.whiteBottom, legal = if (m.playingLine) emptyList() else ev2.legal, lastMove = ev2.lastMove, check = ev2.check,
                            arrows = listOfNotNull(best?.takeIf { !m.playingLine }?.let { Arrow(it, ARROW_BEST) }), modifier = mod, onMove = { m.exploreMove(it) },
                        )
                    }
                    else -> {
                        val cls = mv?.getString("class")
                        val arrows = if (mv != null && cls in ReviewModel.BAD) listOfNotNull(mv.optStr("best_uci")?.let { Arrow(it, ARROW_BEST) }) else emptyList()
                        ChessBoard(
                            fen = ev.getString("fen"), whiteBottom = m.whiteBottom, legal = emptyList(),
                            lastMove = mv?.getString("uci"),
                            arrows = arrows,
                            badge = if (mv != null && cls != null) SquareBadge(mv.getString("uci").substring(2, 4), cls) else null,
                            modifier = mod,
                        )
                    }
                }
            }
        }
        // grafik + hamleler
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(horizontal = 12.dp)) {
            EvalGraph(evals.map { winPercent(it.getInt("cp")) }, moves.map { it.getString("class") }, selected = ply, height = 56.dp) { m.go(it) }
        }
        Spacer(Modifier.height(4.dp))
        MoveStrip(moves.map { it.getString("san") }, current = ply, onClick = { m.go(it) }) { p -> moves.getOrNull(p - 1)?.getString("class") }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            AnimatedContent(targetState = Triple(ply, retry?.text, explore != null), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "steps") { _ ->
                when {
                    retry != null -> CoachBubble(retry.text, title = when (retry.ok) { true -> "Doğru!"; false -> "Tekrar dene"; null -> "Daha iyisini dene" }, cls = if (retry.ok == true) "best" else null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (retry.ok == true) LinkText("Sonraki hata →") { m.nextMistake(myWhite) }
                            LinkText("Vazgeç") { m.closeRetry() }
                        }
                    }
                    explore != null -> ExplorePanel(m)
                    mv == null -> CoachBubble("Başlangıç pozisyonu. İleri ok ile hamle hamle ilerle; grafiğe dokunarak istediğin ana atla.", title = "İnceleme")
                    else -> {
                        val cls = mv.getString("class")
                        CoachBubble(
                            mv.getString("comment"),
                            cls = cls,
                            title = "${mv.getInt("move_number")}${if (mv.getBoolean("white")) "." else "..."} ${mv.getString("san")} · ${CLASS_INFO[cls]?.label ?: ""}",
                        ) {
                            Text("Değerlendirme ${scoreLabel(ev.getInt("cp"))} · doğruluk %${mv.getDouble("accuracy").toInt()}", color = Crabs.Faint, fontSize = 11.5.sp, fontFamily = dev.chesscrab.app.ui.Mono, modifier = Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                                if (cls in ReviewModel.BAD) LinkText("Daha iyisini dene") { m.startRetry() }
                                val line = mv.optJSONArray("best_line")?.strings().orEmpty()
                                if (line.isNotEmpty() && cls !in setOf("book", "forced")) LinkText("En iyi devamı oynat") { m.playLine(evals[ply - 1].getString("fen"), line) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (explore != null) {
                    NavBtn(Icons.AutoMirrored.Filled.Undo) { m.exploreUndo() }
                    NavBtn(Icons.AutoMirrored.Filled.ArrowBack) { m.closeExplore() }
                } else {
                    NavBtn(Icons.Filled.FirstPage) { m.go(0) }
                    NavBtn(Icons.AutoMirrored.Filled.ArrowBack, big = true) { m.go(ply - 1) }
                    NavBtn(Icons.AutoMirrored.Filled.ArrowForward, big = true) { m.go(ply + 1) }
                    NavBtn(Icons.AutoMirrored.Filled.LastPage) { m.go(moves.size) }
                    NavBtn(Icons.Filled.Explore) { m.startExplore(ev.getString("fen")) }
                }
                NavBtn(Icons.Filled.SwapVert) { m.whiteBottom = !m.whiteBottom }
            }
        }
    }
}

private fun whiteCp(score: JSONObject, whiteToMove: Boolean): Int {
    val v = score.optInt("value")
    val cp = if (score.optString("type") == "mate") (if (v > 0) 10000 - v * 10 else -10000 - v * 10) else v
    return if (whiteToMove) cp else -cp
}

@Composable
private fun ExplorePanel(m: ReviewModel) {
    val lines = m.exploreLines
    val turnWhite = m.exploreView?.turnWhite ?: true
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(if (m.playingLine) "En iyi devam oynanıyor…" else "Keşif: istediğin hamleyi oyna, CrabFish anında değerlendirsin.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        if (lines == null) Text("Hesaplanıyor…", color = Crabs.Muted, fontSize = 13.sp)
        else {
            for (l in lines.getJSONArray("lines").objects()) {
                val cp = whiteCp(l.getJSONObject("score"), turnWhite)
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(54.dp).clip(RoundedCornerShape(50)).background(if (cp >= 0) Crabs.Ivory else Crabs.Abyss).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Text(scoreLabel(cp), color = if (cp >= 0) Crabs.Abyss else Crabs.Ivory, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, fontFamily = dev.chesscrab.app.ui.Mono)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(l.getJSONArray("pv_san").strings().take(7).joinToString(" "), fontSize = 12.5.sp, maxLines = 1, fontFamily = dev.chesscrab.app.ui.Mono)
                }
            }
            Text("derinlik ${lines.getInt("depth")}", color = Crabs.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text("$text →", color = Crabs.Coral, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, modifier = Modifier.bounceClick(scaleTo = 0.92f, onClick = onClick).clip(RoundedCornerShape(50)).border(1.dp, Crabs.Coral.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun NavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.bounceClick(scaleTo = 0.88f, onClick = onClick).size(if (big) 64.dp else 46.dp, 46.dp).clip(RoundedCornerShape(50)).background(if (big) Crabs.Coral else Crabs.Card)
            .border(1.dp, if (big) Crabs.Coral else Crabs.Line, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (big) Crabs.Ink else Crabs.Sand) }
}

