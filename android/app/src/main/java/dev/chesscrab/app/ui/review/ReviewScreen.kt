package dev.chesscrab.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.optStr
import dev.chesscrab.app.strings
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.CLASS_ORDER
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.board.*
import dev.chesscrab.app.ui.kare.*
import dev.chesscrab.app.ui.play.MoveStream
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun ReviewScreen(m: ReviewModel, modifier: Modifier) {
    val r = m.result
    Column(modifier.fillMaxSize()) {
        TopBar("Analiz", "ANL 089", trailing = if (r != null) ({
            Box(Modifier.size(TOUCH).press(scale = 0.9f) { m.reset() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Plus, "Yeni analiz", tint = K.Ink, modifier = Modifier.size(20.dp))
            }
        }) else null)
        Box(Modifier.weight(1f)) {
            when {
                r == null -> ImportView(m)
                m.phase == Phase.Summary -> SummaryView(m, r)
                else -> StepsView(m, r)
            }
        }
    }
}

@Composable
private fun ImportView(m: ReviewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (m.running) {
            CoachStrip("Analiz ediliyor", "Oyun hamle hamle inceleniyor; telefonunun bütün çekirdekleri çalışıyor.")
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("İLERLEME", style = KType.label, color = K.Ink2, modifier = Modifier.weight(1f))
                    Text("%${(m.progress * 100).toInt()}", style = KType.mono, color = K.Ink)
                }
                ProgressTrack(m.progress)
                ScanPulse()
            }
            return@Column
        }
        CoachStrip("Oyun incelemesi", "lichess ya da chess.com'dan bir oyunun PGN'ini yapıştır (ya da o uygulamadan Paylaş → ChessCrab). Her hamle cihazında, internetsiz kodlanır.")
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KTextField(m.pgn, { m.pgn = it }, "PGN", Modifier.height(200.dp), singleLine = false, mono = true)
            Text("DERİNLİK", style = KType.label, color = K.Ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in listOf(10, 12, 14, 16, 18)) BranchButton("$d", m.depth == d, Modifier.weight(1f)) { m.depth = d }
            }
            Text("Yüksek derinlik daha doğru, daha yavaş.", style = KType.desc, color = K.Ink3)
            m.error?.let { Text(it, style = KType.desc, color = K.Red) }
            SavilleButton("Analiz et", Modifier.fillMaxWidth(), cap = K.Blue, enabled = m.pgn.isNotBlank()) { m.run() }
        }
    }
}

private fun summaryLine(acc: Double, bad: Int): String = when {
    acc >= 90 -> "Muhteşem bir oyun; neredeyse motor gibi oynadın."
    acc >= 80 -> "Çok iyi oynadın. Birkaç anı birlikte inceleyelim."
    acc >= 65 -> "Fena değil. $bad önemli an var, onlardan öğrenebiliriz."
    else -> "Zorlu bir oyundu; en çok böyle oyunlardan öğrenilir."
}

private fun pawns(cp: Int) = (cp.coerceIn(-1000, 1000) / 100f)

@Composable
private fun SummaryView(m: ReviewModel, r: JSONObject) {
    val me = SyncClient.username.ifEmpty { "Sen" }
    val myWhite = m.tag("Black") != me
    val moves = m.moves
    val evals = m.evals
    val bad = moves.count { it.getBoolean("white") == myWhite && it.getString("class") in ReviewModel.BAD }
    val mine = r.getJSONObject(if (myWhite) "white" else "black")
    val myAcc = mine.optDouble("accuracy", 0.0)
    val values = remember(evals) { evals.map { pawns(it.getInt("cp")) } }
    val brill = moves.count { it.getString("class") in setOf("brilliant", "great") }
    val blund = moves.count { it.getString("class") in setOf("blunder", "miss") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // kutupsal panel
        Row(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PolarPulsar(values.takeLast(48).ifEmpty { listOf(0f) }, (values.size - 1).coerceIn(0, 47))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (white in listOf(true, false)) {
                    val acc = r.getJSONObject(if (white) "white" else "black").optDouble("accuracy", Double.NaN)
                    StatLine("Doğruluk · ${(m.tag(if (white) "White" else "Black") ?: if (white) "Beyaz" else "Siyah").take(14)}", if (acc.isNaN()) "—" else "%" + countUp(acc.toFloat()))
                }
                StatLine("Performans", if (!mine.isNull("estimated_elo")) "~${mine.getInt("estimated_elo")}" else "—")
                StatLine("Parlak / hata", "$brill !! · $blund ??")
            }
        }
        EvalTrack(evals.lastOrNull()?.getInt("cp") ?: 0)
        Column(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row { Text("OYUN PULSARI", style = KType.label.copy(fontWeight = FontWeight.SemiBold), color = K.Ink2, modifier = Modifier.weight(1f)); Text("${moves.size} HAMLE", style = KType.code, color = K.Ink) }
            LinearPulsar(values, values.size - 1, height = 52.dp)
        }
        CoachStrip(summaryLine(myAcc, bad), "${m.tag("White") ?: "Beyaz"} – ${m.tag("Black") ?: "Siyah"} · ${m.tag("Result") ?: ""}") {
            SavilleButton("Adım adım", cap = K.Blue) { m.phase = Phase.Steps; m.go(1) }
        }

        // hamle kodları tablosu
        SectionLabel("Hamle kodları", trailing = "beyaz · siyah")
        for (c in CLASS_ORDER) {
            val info = CLASS_INFO.getValue(c)
            val w = moves.count { it.getBoolean("white") && it.getString("class") == c }
            val b = moves.count { !it.getBoolean("white") && it.getString("class") == c }
            Row(Modifier.fillMaxWidth().background(K.Base).hairlineTop().heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Stamp(c)
                Spacer(Modifier.width(10.dp))
                Text(info.label, style = KType.caption, color = K.Ink, modifier = Modifier.weight(1f))
                Text("$w", style = KType.mono.copy(fontWeight = FontWeight.Bold), color = if (w > 0) K.Ink else K.Ink3, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text("$b", style = KType.mono.copy(fontWeight = FontWeight.Bold), color = if (b > 0) K.Ink else K.Ink3, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Hairline()
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bad > 0) SavilleButton("Hatalarından öğren ($bad)", Modifier.fillMaxWidth(), cap = K.Red) { m.learnFromMistakes(myWhite) }
            SavilleButton("İncelemeye başla", Modifier.fillMaxWidth(), cap = K.Blue) { m.phase = Phase.Steps; m.go(1) }
        }
        r.optJSONArray("opening")?.let { Text("${it.getString(0)} · ${it.getString(1)}".upTr(), style = KType.code, color = K.Ink3, modifier = Modifier.padding(horizontal = 14.dp)) }
        Text("${r.getString("engine")} · ${r.getLong("total_nodes") / 1_000_000}M düğüm · ${r.getLong("time_ms")} ms · cihazında".upTr(),
            style = KType.code.copy(fontSize = 10.sp), color = K.Ink3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

private fun whiteCp(score: JSONObject, whiteToMove: Boolean): Int {
    val v = score.optInt("value")
    val cp = if (score.optString("type") == "mate") (if (v > 0) 10000 - v * 10 else -10000 - v * 10) else v
    return if (whiteToMove) cp else -cp
}

@Composable
private fun StepsView(m: ReviewModel, r: JSONObject) {
    val moves = m.moves
    val evals = m.evals
    val ply = m.ply.coerceIn(0, moves.size)
    val ev = evals[ply]
    val mv = if (ply > 0) moves[ply - 1] else null
    val retry = m.retry
    val explore = m.explore
    val me = SyncClient.username.ifEmpty { "Sen" }
    val myWhite = m.tag("Black") != me
    val values = remember(evals) { evals.map { pawns(it.getInt("cp")) } }
    var auto by remember { mutableStateOf(false) }
    LaunchedEffect(auto) {
        while (auto) {
            delay(950)
            if (m.ply >= moves.size) { auto = false; break }
            m.go(m.ply + 1)
        }
    }
    val brill = moves.count { it.getString("class") in setOf("brilliant", "great") }
    val blund = moves.count { it.getString("class") in setOf("blunder", "miss") }
    val cp = when {
        explore != null -> m.exploreLines?.getJSONObject("score")?.let { s -> whiteCp(s, m.exploreView?.turnWhite ?: true) } ?: ev.getInt("cp")
        else -> ev.getInt("cp")
    }
    val cls = mv?.getString("class")
    val info = CLASS_INFO[cls]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // kutupsal panel
        Row(Modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PolarPulsar(values.ifEmpty { listOf(0f) }, ply.coerceAtMost(values.size - 1))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatLine("Doğruluk", mv?.let { "%" + "%.1f".format(it.getDouble("accuracy")) } ?: "—")
                StatLine("Değerlendirme", scoreLabel(cp))
                StatLine("Parlak / hata", "$brill !! · $blund ??")
                StatLine("Aktif hamle", mv?.let { "${it.getInt("move_number")}${if (it.getBoolean("white")) "." else "..."} ${it.getString("san")}${info?.symbol?.takeIf { s -> s in setOf("!!", "!", "?!", "?", "??") } ?: ""}" } ?: "başlangıç")
            }
        }
        EvalTrack(cp)

        // tahta: %80 genişlik, dikey kılçizgiler arasında ortalı
        Box(Modifier.fillMaxWidth().background(K.Base), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.8f).border(1.dp, K.LineSubtle)) {
                when {
                    retry != null -> ChessBoard(
                        fen = retry.shownFen ?: retry.fen, whiteBottom = m.whiteBottom, legal = if (retry.ok == true) emptyList() else retry.legal,
                        lastMove = retry.lastMove, arrows = listOfNotNull(retry.arrow?.let { Arrow(it, ARROW_HINT) }), hint = retry.arrow,
                        flash = m.flash, shake = m.shake, onMove = { m.retryMove(it) },
                    )
                    explore != null -> {
                        val ev2 = m.exploreView!!
                        val best = m.exploreLines?.optStr("best")
                        ChessBoard(
                            fen = ev2.fen, whiteBottom = m.whiteBottom, legal = if (m.playingLine) emptyList() else ev2.legal, lastMove = ev2.lastMove, check = ev2.check,
                            arrows = listOfNotNull(best?.takeIf { !m.playingLine }?.let { Arrow(it, ARROW_BEST) }), onMove = { m.exploreMove(it) },
                        )
                    }
                    else -> {
                        val arrows = if (mv != null && cls in ReviewModel.BAD) listOfNotNull(
                            mv.optStr("best_uci")?.let { Arrow(it, ARROW_BEST) },
                            mv.optStr("refutation_uci")?.let { Arrow(it, ARROW_BAD, dashed = true) },
                        ) else emptyList()
                        val fx = if (mv != null && (cls == "brilliant" || cls == "great")) BoardFx(mv.getString("uci").substring(2, 4), "brilliant", ply) else null
                        ChessBoard(
                            fen = ev.getString("fen"), whiteBottom = m.whiteBottom, legal = emptyList(),
                            lastMove = mv?.getString("uci"), arrows = arrows, fx = fx,
                            badge = if (mv != null && cls != null) SquareBadge(mv.getString("uci").substring(2, 4), cls) else null,
                        )
                    }
                }
            }
        }
        Hairline()

        // hamle zaman çizelgesi
        MoveStream(moves.map { it.getString("san") }, ply, { p -> moves.getOrNull(p - 1)?.getString("class") }, compact = false) { m.go(it) }

        // varyant kutusu
        AnimatedContent(Triple(ply, retry?.text, explore != null), transitionSpec = {
            fadeIn(tween(if (Motion.reduced) 120 else Motion.STATE, easing = Motion.Expo)) togetherWith fadeOut(tween(90))
        }, label = "var") { _ ->
            Column(Modifier.fillMaxWidth().background(K.Surface).hairlineBottom().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    retry != null -> {
                        VariationHead(when (retry.ok) { true -> "Doğru"; false -> "Tekrar dene"; null -> "Daha iyisini dene" }, if (retry.ok == true) "! iyi" else "?! deneme", if (retry.ok == true) K.Blue else K.Yellow)
                        Text(retry.text, style = KType.desc, color = K.Ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (retry.ok == true) BranchButton("Sonraki hata →", true) { m.nextMistake(myWhite) }
                            BranchButton("Vazgeç") { m.closeRetry() }
                        }
                    }
                    explore != null -> {
                        VariationHead(if (m.playingLine) "En iyi devam oynanıyor" else "Keşif", "motor", K.Blue)
                        val lines = m.exploreLines
                        val turnWhite = m.exploreView?.turnWhite ?: true
                        if (lines == null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Hesaplanıyor", style = KType.desc, color = K.Ink2, modifier = Modifier.weight(1f)); ScanPulse() }
                        else {
                            for (l in lines.getJSONArray("lines").objects()) {
                                val lcp = whiteCp(l.getJSONObject("score"), turnWhite)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(scoreLabel(lcp), style = KType.mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = if (lcp >= 0) K.Base else K.Ink,
                                        modifier = Modifier.width(56.dp).background(if (lcp >= 0) K.Ink else K.Base).border(1.dp, K.LineStrong).padding(vertical = 3.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(Modifier.width(8.dp))
                                    Text(l.getJSONArray("pv_san").strings().take(7).joinToString(" "), style = KType.mono.copy(fontSize = 11.sp), color = K.Ink, maxLines = 1)
                                }
                            }
                            Text("DERİNLİK ${lines.getInt("depth")}", style = KType.code, color = K.Ink3)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BranchButton("Geri al") { m.exploreUndo() }
                            BranchButton("Kapat") { m.closeExplore() }
                        }
                    }
                    mv == null -> {
                        VariationHead("Başlangıç pozisyonu", "ply 0", K.Industrial)
                        Text("Sonraki ile hamle hamle ilerle ya da zaman çizelgesinden bir ana atla.", style = KType.desc, color = K.Ink2)
                    }
                    else -> {
                        VariationHead("${mv.getInt("move_number")}${if (mv.getBoolean("white")) "." else "..."} ${mv.getString("san")} — ${info?.label ?: ""}", "${info?.symbol ?: ""} ${info?.label ?: ""}", info?.color ?: K.Industrial)
                        Text(mv.getString("comment"), style = KType.desc, color = K.Ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            val line = mv.optJSONArray("best_line")?.strings().orEmpty()
                            if (line.isNotEmpty() && cls !in setOf("book", "forced")) {
                                val bestSan = mv.optStr("best_san") ?: line.first()
                                BranchButton(if (cls in ReviewModel.BAD) "Önerilen: $bestSan" else "Devam: ${line.take(3).joinToString(" ")}", true) { m.playLine(evals[ply - 1].getString("fen"), line) }
                            }
                            if (cls in ReviewModel.BAD) BranchButton("Daha iyisini dene") { m.startRetry() }
                            BranchButton("Keşfet") { m.startExplore(ev.getString("fen")) }
                        }
                    }
                }
            }
        }

        ActionRow {
            MiniAction(KIcons.Prev, "Önceki", Modifier.weight(1f), enabled = ply > 0) { auto = false; m.go(ply - 1) }
            MiniAction(if (auto) KIcons.Pause else KIcons.PlayTri, if (auto) "Durdur" else "Oynat", Modifier.weight(1f), on = auto) {
                if (!auto && m.ply >= moves.size) m.go(0)
                auto = !auto
            }
            MiniAction(KIcons.Next, "Sonraki", Modifier.weight(1f), enabled = ply < moves.size) { auto = false; m.go(ply + 1) }
        }
        ActionRow(Modifier.padding(bottom = 6.dp)) {
            MiniAction(KIcons.First, "Başa", Modifier.weight(1f)) { auto = false; m.go(0) }
            MiniAction(KIcons.Flip, "Çevir", Modifier.weight(1f)) { m.whiteBottom = !m.whiteBottom }
            MiniAction(KIcons.Summary, "Özet", Modifier.weight(1f)) { auto = false; m.phase = Phase.Summary; m.closeRetry(); m.closeExplore() }
        }
    }
}

@Composable
private fun VariationHead(title: String, badge: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KType.caption.copy(fontWeight = FontWeight.Bold), color = K.Ink, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(8.dp))
        CatalogCode(badge.trim(), color = if (color == K.Industrial) K.Ink2 else color, border = if (color == K.Industrial) K.LineSubtle else color)
    }
}
