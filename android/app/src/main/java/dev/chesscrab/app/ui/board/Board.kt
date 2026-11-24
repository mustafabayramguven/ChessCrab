package dev.chesscrab.app.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.R
import dev.chesscrab.app.ui.BoardTheme
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.boardTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class Arrow(val uci: String, val color: Color = Color(0xCC15781B))
data class SquareBadge(val square: String, val cls: String)
/** Kare üzerinde kısa süreli renk parlaması (doğru/yanlış geri bildirimi). `token` her yeni parlamada artmalı. */
data class Flash(val square: String, val color: Color, val token: Int)

val ARROW_BEST = Color(0xCC15781B)
val ARROW_HINT = Color(0xCC3A86C8)
val ARROW_BAD = Color(0xB3E2563B)

private val LAST_MOVE = Color(0x80F5F682)
private val SELECTED = Color(0x99F5F682)

fun parseBoard(fen: String): Map<String, Char> {
    val out = HashMap<String, Char>()
    val rows = fen.substringBefore(' ').split('/')
    for ((ri, row) in rows.withIndex()) {
        var f = 0
        for (c in row) {
            if (c.isDigit()) f += c.digitToInt() else {
                if (f < 8 && ri < 8) out["${'a' + f}${8 - ri}"] = c
                f++
            }
        }
    }
    return out
}

@Composable
fun rememberPieces(): Map<Char, ImageBitmap> {
    val ids = mapOf(
        'K' to R.drawable.piece_wk, 'Q' to R.drawable.piece_wq, 'R' to R.drawable.piece_wr,
        'B' to R.drawable.piece_wb, 'N' to R.drawable.piece_wn, 'P' to R.drawable.piece_wp,
        'k' to R.drawable.piece_bk, 'q' to R.drawable.piece_bq, 'r' to R.drawable.piece_br,
        'b' to R.drawable.piece_bb, 'n' to R.drawable.piece_bn, 'p' to R.drawable.piece_bp,
    )
    return ids.mapValues { ImageBitmap.imageResource(it.value) }
}

fun DrawScope.drawPieceImage(img: ImageBitmap, topLeft: Offset, size: Float, alpha: Float = 1f) {
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(size.roundToInt(), size.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

private data class Slide(val piece: Char, val from: String, val to: String)
private class AnimPlan(val slides: List<Slide>, val captured: Pair<String, Char>?)

private class Holder {
    var lastBoard: Map<String, Char>? = null
    var skipNext = false
}

private fun sq(f: Int, r: Int) = "${'a' + f}${r + 1}"

private fun planFor(prev: Map<String, Char>?, cur: Map<String, Char>, lastMove: String?, skip: Boolean): AnimPlan? {
    if (prev == null || lastMove == null || lastMove.length < 4 || skip || prev == cur) return null
    val from = lastMove.substring(0, 2)
    val to = lastMove.substring(2, 4)
    val moving = prev[from] ?: return null
    if (cur[from] != null) return null // geri alma vb.: canlandırma yok
    val piece = cur[to] ?: return null
    val slides = mutableListOf(Slide(piece, from, to))
    var captured: Pair<String, Char>? = prev[to]?.let { to to it }
    val fd = (to[0] - from[0])
    if (moving.lowercaseChar() == 'k' && kotlin.math.abs(fd) == 2) {
        val r = from[1] - '1'
        if (fd > 0) slides += Slide(cur[sq(5, r)] ?: 'R', sq(7, r), sq(5, r))
        else slides += Slide(cur[sq(3, r)] ?: 'R', sq(0, r), sq(3, r))
        captured = null
    }
    if (moving.lowercaseChar() == 'p' && fd != 0 && prev[to] == null) {
        val epSq = "${to[0]}${from[1]}"
        prev[epSq]?.let { captured = epSq to it }
    }
    return AnimPlan(slides, captured)
}

/**
 * Animasyonlu satranç tahtası: dokun-dokun ya da sürükle-bırak, kayan taşlar,
 * hamle kalitesi rozeti, oklar, parlama ve yanlış hamlede sallanma.
 */
@Composable
fun ChessBoard(
    fen: String,
    whiteBottom: Boolean,
    legal: List<String>,
    modifier: Modifier = Modifier,
    lastMove: String? = null,
    check: Boolean = false,
    arrows: List<Arrow> = emptyList(),
    highlights: List<String> = emptyList(),
    badge: SquareBadge? = null,
    flash: Flash? = null,
    shake: Int = 0,
    theme: BoardTheme = boardTheme(dev.chesscrab.app.ui.Prefs.boardTheme),
    coordinates: Boolean = true,
    onMove: (String) -> Unit = {},
) {
    val pieces = rememberPieces()
    val board = remember(fen) { parseBoard(fen) }
    val holder = remember { Holder() }
    val plan = remember(fen) { planFor(holder.lastBoard, board, lastMove, holder.skipNext) }
    SideEffect { holder.lastBoard = board; holder.skipNext = false }
    val progress = remember(fen) { Animatable(if (plan == null) 1f else 0f) }
    LaunchedEffect(fen) { if (plan != null) progress.animateTo(1f, tween(190, easing = FastOutSlowInEasing)) }

    val badgeScale = remember(badge) { Animatable(if (badge == null) 1f else 0f) }
    LaunchedEffect(badge) { if (badge != null) badgeScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    val flashAlpha = remember(flash?.token) { Animatable(if (flash == null) 0f else 0.75f) }
    LaunchedEffect(flash?.token) { if (flash != null) flashAlpha.animateTo(0f, tween(900)) }
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) shakeX.animateTo(0f, keyframes { durationMillis = 360; -18f at 50; 16f at 110; -11f at 170; 8f at 230; -4f at 290 })
    }

    var selected by remember(fen) { mutableStateOf<String?>(null) }
    var dragFrom by remember(fen) { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var promo by remember(fen) { mutableStateOf<List<String>?>(null) }
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val turnWhite = fen.split(' ').getOrNull(1) != "b"

    fun squareAt(o: Offset, size: Float): String? {
        val x = (o.x / size).toInt(); val y = (o.y / size).toInt()
        if (x !in 0..7 || y !in 0..7) return null
        val f = if (whiteBottom) x else 7 - x
        val r = if (whiteBottom) 7 - y else y
        return sq(f, r)
    }
    fun topLeft(s: String, size: Float): Offset {
        val f = s[0] - 'a'; val r = s[1] - '1'
        val x = if (whiteBottom) f else 7 - f
        val y = if (whiteBottom) 7 - r else r
        return Offset(x * size, y * size)
    }
    fun tryMove(from: String, to: String, dragged: Boolean) {
        val c = legal.filter { it.startsWith(from + to) }
        selected = null
        when {
            c.size > 1 -> promo = c
            c.size == 1 -> {
                holder.skipNext = dragged
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMove(c[0])
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .offset { IntOffset(shakeX.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(4.dp))
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(fen, legal, whiteBottom) {
                    detectTapGestures { o ->
                        val s = squareAt(o, size.width / 8f) ?: return@detectTapGestures
                        val sel = selected
                        if (sel != null && legal.any { it.startsWith(sel + s) }) tryMove(sel, s, false)
                        else selected = if (legal.any { it.startsWith(s) }) s else null
                    }
                }
                .pointerInput(fen, legal, whiteBottom) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val s = squareAt(o, size.width / 8f)
                            if (s != null && legal.any { it.startsWith(s) }) { dragFrom = s; selected = s; dragPos = o }
                        },
                        onDrag = { ch, d -> if (dragFrom != null) { ch.consume(); dragPos += d } },
                        onDragEnd = {
                            val from = dragFrom; dragFrom = null
                            val to = squareAt(dragPos, size.width / 8f)
                            if (from != null && to != null && to != from) tryMove(from, to, true)
                        },
                        onDragCancel = { dragFrom = null },
                    )
                }
        ) {
            val s = size.width / 8f
            val p = progress.value
            // kareler
            for (x in 0..7) for (y in 0..7) {
                val f = if (whiteBottom) x else 7 - x
                val r = if (whiteBottom) 7 - y else y
                val name = sq(f, r)
                val tl = Offset(x * s, y * s)
                val light = (f + r) % 2 == 1
                drawRect(if (light) theme.light else theme.dark, tl, Size(s, s))
                if (lastMove != null && lastMove.length >= 4 && (lastMove.startsWith(name) || lastMove.substring(2, 4) == name)) drawRect(LAST_MOVE, tl, Size(s, s))
                if (name in highlights) drawRect(Color(0x664C9BE8), tl, Size(s, s))
                if (selected == name) drawRect(SELECTED, tl, Size(s, s))
                if (flash != null && flash.square == name && flashAlpha.value > 0f) drawRect(flash.color.copy(alpha = flashAlpha.value), tl, Size(s, s))
            }
            // koordinatlar (karenin karşıt renginde)
            if (coordinates) {
                val style = TextStyle(fontSize = (s * 0.17f / density).sp, fontWeight = FontWeight.Bold)
                val rb = if (whiteBottom) 0 else 7
                val fl0 = if (whiteBottom) 0 else 7
                for (i in 0..7) {
                    val f = if (whiteBottom) i else 7 - i
                    val lightF = (f + rb) % 2 == 1
                    val fl = measurer.measure("${'a' + f}", style.copy(color = if (lightF) theme.dark else theme.light))
                    drawText(fl, topLeft = Offset(i * s + s - fl.size.width - s * 0.05f, 8 * s - fl.size.height - s * 0.01f))
                    val r = if (whiteBottom) 7 - i else i
                    val lightR = (fl0 + r) % 2 == 1
                    val rl = measurer.measure("${r + 1}", style.copy(color = if (lightR) theme.dark else theme.light))
                    drawText(rl, topLeft = Offset(s * 0.05f, i * s + s * 0.02f))
                }
            }
            // şah tehdidi
            if (check) {
                val k = if (turnWhite) 'K' else 'k'
                board.entries.firstOrNull { it.value == k }?.let { e ->
                    val c = topLeft(e.key, s) + Offset(s / 2, s / 2)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF0000), Color(0xAAE70000), Color.Transparent), center = c, radius = s * 0.72f), radius = s * 0.72f, center = c)
                }
            }
            // taşlar
            val moving = plan?.slides?.map { it.to }?.toSet().orEmpty()
            for ((sqName, pc) in board) {
                if (sqName == dragFrom) continue
                if (p < 1f && sqName in moving) continue
                pieces[pc]?.let { drawPieceImage(it, topLeft(sqName, s), s) }
            }
            if (plan != null && p < 1f) {
                plan.captured?.let { (cs, cp) -> pieces[cp]?.let { drawPieceImage(it, topLeft(cs, s), s, alpha = 1f - p) } }
                for (sl in plan.slides) {
                    val a = topLeft(sl.from, s); val b = topLeft(sl.to, s)
                    pieces[sl.piece]?.let { drawPieceImage(it, Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p), s) }
                }
            }
            // yasal hamle noktaları
            selected?.let { sel ->
                legal.filter { it.startsWith(sel) }.map { it.substring(2, 4) }.distinct().forEach { t ->
                    val c = topLeft(t, s) + Offset(s / 2, s / 2)
                    if (board.containsKey(t)) drawCircle(Color(0x33000000), s * 0.45f, c, style = Stroke(s * 0.09f))
                    else drawCircle(Color(0x33000000), s * 0.16f, c)
                }
            }
            // oklar
            for (a in arrows) {
                if (a.uci.length < 4) continue
                val p1 = topLeft(a.uci.substring(0, 2), s) + Offset(s / 2, s / 2)
                val p2 = topLeft(a.uci.substring(2, 4), s) + Offset(s / 2, s / 2)
                val ang = atan2(p2.y - p1.y, p2.x - p1.x)
                val len = hypot(p2.x - p1.x, p2.y - p1.y)
                val head = s * 0.42f
                val end = Offset(p1.x + cos(ang) * (len - head), p1.y + sin(ang) * (len - head))
                drawLine(a.color, p1, end, strokeWidth = s * 0.2f, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(p2.x, p2.y)
                    lineTo(end.x + cos(ang + 1.5708f) * head * 0.62f, end.y + sin(ang + 1.5708f) * head * 0.62f)
                    lineTo(end.x + cos(ang - 1.5708f) * head * 0.62f, end.y + sin(ang - 1.5708f) * head * 0.62f)
                    close()
                }
                drawPath(path, a.color)
            }
            // hamle kalitesi rozeti (chess.com tarzı)
            if (badge != null && p >= 1f) CLASS_INFO[badge.cls]?.let { info ->
                val tl = topLeft(badge.square, s)
                val r = s * 0.2f
                val c = Offset(tl.x + s - r * 0.75f, tl.y + r * 0.75f)
                scale(badgeScale.value, pivot = c) {
                    drawCircle(Color(0x55000000), r * 1.08f, c + Offset(0f, r * 0.12f))
                    drawCircle(info.color, r, c)
                    drawCircle(Color.White, r, c, style = Stroke(r * 0.14f))
                    val t = measurer.measure(info.symbol, TextStyle(color = Color.White, fontSize = (r * (if (info.symbol.length > 1) 0.95f else 1.15f) / density).sp, fontWeight = FontWeight.Black))
                    drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
                }
            }
            // sürüklenen taş
            dragFrom?.let { d -> board[d]?.let { pc -> pieces[pc]?.let { drawPieceImage(it, dragPos - Offset(s * 0.65f, s * 0.9f), s * 1.3f) } } }
        }
    }

    promo?.let { cands ->
        AlertDialog(
            onDismissRequest = { promo = null },
            confirmButton = {},
            title = { Text("Terfi") },
            text = {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    for (pc in listOf('q', 'r', 'b', 'n')) {
                        val uci = cands.firstOrNull { it.endsWith(pc) } ?: continue
                        val img = pieces[if (turnWhite) pc.uppercaseChar() else pc] ?: continue
                        TextButton(onClick = { promo = null; holder.skipNext = false; onMove(uci) }) {
                            Canvas(Modifier.size(52.dp)) { drawPieceImage(img, Offset.Zero, size.width) }
                        }
                    }
                }
            },
        )
    }
}

/** Alınan taşlar ve materyal farkı (FEN'den). Dönüş: (beyazın aldığı siyah taşlar, siyahın aldığı beyaz taşlar, beyaz açısından fark) */
fun capturedPieces(fen: String): Triple<List<Char>, List<Char>, Int> {
    val start = mapOf('p' to 8, 'n' to 2, 'b' to 2, 'r' to 2, 'q' to 1)
    val value = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)
    val b = parseBoard(fen).values
    val byWhite = mutableListOf<Char>(); val byBlack = mutableListOf<Char>()
    var diff = 0
    for ((pc, n) in start) {
        val w = b.count { it == pc.uppercaseChar() }
        val k = b.count { it == pc }
        repeat((n - k).coerceAtLeast(0)) { byWhite += pc }
        repeat((n - w).coerceAtLeast(0)) { byBlack += pc.uppercaseChar() }
        diff += (w - k) * value.getValue(pc)
    }
    val order = "qrbnp"
    return Triple(byWhite.sortedBy { order.indexOf(it.lowercaseChar()) }, byBlack.sortedBy { order.indexOf(it.lowercaseChar()) }, diff)
}
