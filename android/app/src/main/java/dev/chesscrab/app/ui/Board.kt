package dev.chesscrab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val LIGHT = Color(0xFFEBD8B7)
private val DARK = Color(0xFFA87B55)
private val LAST = Color(0x8CCDD26A)
private val SEL = Color(0x8CE07B39)
private val HL = Color(0x6150A0FF)
private val ARROW = Color(0xC015781B)

/** Özgün, basit taş çizimleri (100x100 kutuda). */
private val PIECE_PATHS = mapOf(
    'p' to "M37 32a13 13 0 1 0 26 0a13 13 0 1 0 -26 0z M38 48h24l6 30H32z M26 78h48v10h-48z",
    'n' to "M30 88h46l-3-12c-2-10 1-24 4-34 3-14-6-26-22-28l-4-6-5 8c-8 4-16 14-20 24l-2 8 8 4 8-6 6 2c-6 8-12 18-14 28z",
    'b' to "M44 16a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M50 22c-12 8-18 20-16 32 2 8 6 12 8 14h16c2-2 6-6 8-14 2-12-4-24-16-32z M36 70h28l6 10H30z M24 80h52v9h-52z",
    'r' to "M26 14h10v8h8v-8h12v8h8v-8h10v20l-8 6v34l8 6v8H26v-8l8-6V40l-8-6z",
    'q' to "M10 26a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M27 18a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M44 15a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M61 18a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M78 26a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z M18 32l12 38h40l12-38-14 18-6-26-12 24-12-24-6 26z M28 70h44l4 10H24z M22 80h56v9h-56z",
    'k' to "M46 4h8v8h8v8h-8v8h-8v-8h-8v-8h8z M50 30c-16 0-28 8-28 20 0 10 6 16 10 22h36c4-6 10-12 10-22 0-12-12-20-28-20z M30 72h40l4 8H26z M24 80h52v9h-52z",
)

private val PIECES: Map<Char, Path> = PIECE_PATHS.mapValues { PathParser().parsePathString(it.value).toPath() }

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

fun DrawScope.drawPiece(piece: Char, topLeft: Offset, size: Float) {
    val path = PIECES[piece.lowercaseChar()] ?: return
    val white = piece.isUpperCase()
    val fill = if (white) Color(0xFFF8F6F1) else Color(0xFF2A2826)
    val stroke = if (white) Color(0xFF3A332C) else Color(0xFF0D0C0B)
    val inset = size * 0.04f
    translate(topLeft.x + inset, topLeft.y + inset) {
        scale((size - 2 * inset) / 100f, (size - 2 * inset) / 100f, pivot = Offset.Zero) {
            drawPath(path, fill)
            drawPath(path, stroke, style = Stroke(width = 3.2f))
            if (piece.lowercaseChar() == 'n') drawCircle(stroke, radius = 3f, center = Offset(46f, 30f))
        }
    }
}

/**
 * Satranç tahtası: dokun-dokun ya da sürükle-bırak ile hamle.
 * [legal] boşsa tahta etkileşimsizdir. Hamleler UCI ile bildirilir (terfi: "e7e8q").
 */
@Composable
fun ChessBoard(
    fen: String,
    whiteBottom: Boolean,
    legal: List<String>,
    modifier: Modifier = Modifier,
    lastMove: String? = null,
    check: Boolean = false,
    arrows: List<String> = emptyList(),
    highlights: List<String> = emptyList(),
    onMove: (String) -> Unit = {},
) {
    val pieces = remember(fen) { parseBoard(fen) }
    var selected by remember(fen) { mutableStateOf<String?>(null) }
    var dragFrom by remember(fen) { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var promo by remember(fen) { mutableStateOf<List<String>?>(null) }
    val turnWhite = fen.split(' ').getOrNull(1) != "b"
    val legalRef = remember(legal) { legal }

    fun squareAt(o: Offset, sq: Float): String? {
        val x = (o.x / sq).toInt(); val y = (o.y / sq).toInt()
        if (x !in 0..7 || y !in 0..7) return null
        val f = if (whiteBottom) x else 7 - x
        val r = if (whiteBottom) 7 - y else y
        return "${'a' + f}${r + 1}"
    }
    fun topLeft(s: String, sq: Float): Offset {
        val f = s[0] - 'a'; val r = s[1] - '1'
        val x = if (whiteBottom) f else 7 - f
        val y = if (whiteBottom) 7 - r else r
        return Offset(x * sq, y * sq)
    }
    fun tryMove(from: String, to: String) {
        val c = legalRef.filter { it.startsWith(from + to) }
        selected = null
        when {
            c.size > 1 -> promo = c
            c.size == 1 -> onMove(c[0])
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val sqPx = constraints.maxWidth / 8f
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(fen, legal, whiteBottom) {
                    detectTapGestures { o ->
                        val s = squareAt(o, size.width / 8f) ?: return@detectTapGestures
                        val sel = selected
                        if (sel != null && legalRef.any { it.startsWith(sel + s) }) tryMove(sel, s)
                        else selected = if (legalRef.any { it.startsWith(s) }) s else null
                    }
                }
                .pointerInput(fen, legal, whiteBottom) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val s = squareAt(o, size.width / 8f)
                            if (s != null && legalRef.any { it.startsWith(s) }) { dragFrom = s; selected = s; dragPos = o }
                        },
                        onDrag = { ch, d -> if (dragFrom != null) { ch.consume(); dragPos += d } },
                        onDragEnd = {
                            val from = dragFrom; dragFrom = null
                            val to = squareAt(dragPos, size.width / 8f)
                            if (from != null && to != null && to != from) tryMove(from, to)
                        },
                        onDragCancel = { dragFrom = null },
                    )
                }
        ) {
            val sq = size.width / 8f
            for (x in 0..7) for (y in 0..7) {
                val f = if (whiteBottom) x else 7 - x
                val r = if (whiteBottom) 7 - y else y
                val name = "${'a' + f}${r + 1}"
                val tl = Offset(x * sq, y * sq)
                drawRect(if ((f + r) % 2 == 0) DARK else LIGHT, tl, Size(sq, sq))
                if (lastMove != null && (lastMove.startsWith(name) || lastMove.substring(2, 4) == name)) drawRect(LAST, tl, Size(sq, sq))
                if (name in highlights) drawRect(HL, tl, Size(sq, sq))
                if (selected == name) drawRect(SEL, tl, Size(sq, sq))
            }
            if (check) {
                val k = if (turnWhite) 'K' else 'k'
                pieces.entries.firstOrNull { it.value == k }?.let { e ->
                    val tl = topLeft(e.key, sq)
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xDDFF0000), Color(0x80E70000), Color.Transparent), center = tl + Offset(sq / 2, sq / 2), radius = sq * 0.7f),
                        radius = sq * 0.7f, center = tl + Offset(sq / 2, sq / 2),
                    )
                }
            }
            for ((s, p) in pieces) {
                if (s == dragFrom) continue
                drawPiece(p, topLeft(s, sq), sq)
            }
            // hedef noktaları
            selected?.let { sel ->
                legalRef.filter { it.startsWith(sel) }.map { it.substring(2, 4) }.distinct().forEach { t ->
                    val c = topLeft(t, sq) + Offset(sq / 2, sq / 2)
                    if (pieces.containsKey(t)) drawCircle(Color(0x47141414), sq * 0.46f, c, style = Stroke(sq * 0.08f))
                    else drawCircle(Color(0x47141414), sq * 0.15f, c)
                }
            }
            for (a in arrows) {
                if (a.length < 4) continue
                val p1 = topLeft(a.substring(0, 2), sq) + Offset(sq / 2, sq / 2)
                val p2 = topLeft(a.substring(2, 4), sq) + Offset(sq / 2, sq / 2)
                val ang = atan2(p2.y - p1.y, p2.x - p1.x)
                val len = hypot(p2.x - p1.x, p2.y - p1.y)
                val head = sq * 0.38f
                val end = Offset(p1.x + cos(ang) * (len - head), p1.y + sin(ang) * (len - head))
                drawLine(ARROW, p1, end, strokeWidth = sq * 0.18f, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(p2.x, p2.y)
                    lineTo(end.x + cos(ang + 1.5708f) * head * 0.6f, end.y + sin(ang + 1.5708f) * head * 0.6f)
                    lineTo(end.x + cos(ang - 1.5708f) * head * 0.6f, end.y + sin(ang - 1.5708f) * head * 0.6f)
                    close()
                }
                drawPath(path, ARROW)
            }
            dragFrom?.let { s -> pieces[s]?.let { drawPiece(it, dragPos - Offset(sq * 0.6f, sq * 0.6f), sq * 1.2f) } }
        }
    }

    promo?.let { cands ->
        AlertDialog(
            onDismissRequest = { promo = null },
            confirmButton = {},
            title = { Text("Terfi") },
            text = {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    for (p in listOf('q', 'r', 'b', 'n')) {
                        val uci = cands.firstOrNull { it.endsWith(p) } ?: continue
                        TextButton(onClick = { promo = null; onMove(uci) }) {
                            Canvas(Modifier.size(48.dp)) { drawPiece(if (turnWhite) p.uppercaseChar() else p, Offset.Zero, size.width) }
                        }
                    }
                }
            },
        )
    }
}
