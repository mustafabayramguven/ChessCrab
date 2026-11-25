package dev.chesscrab.app.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.chesscrab.app.ui.BoardTheme
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Mono
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.Prefs
import dev.chesscrab.app.ui.boardTheme
import dev.chesscrab.app.ui.kare.press
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Vektör ok: kobalt (en iyi), kehribar (ipucu), kızıl (tehdit); kesikli = olası/gizli tehdit */
data class Arrow(val uci: String, val color: Color = K.Blue, val dashed: Boolean = false)
data class SquareBadge(val square: String, val cls: String)
/** Kare üzerinde kısa süreli renk parlaması. `token` her yeni parlamada artmalı. */
data class Flash(val square: String, val color: Color, val token: Int)
/** Tuval darbe efekti: "capture" | "brilliant" | "checkmate" */
data class BoardFx(val square: String, val kind: String, val token: Int)

val ARROW_BEST = K.Blue
val ARROW_HINT = K.Yellow
val ARROW_BAD = K.Red
val FLASH_GOOD = Color(0xFF2563EB)
val FLASH_BAD = Color(0xFFDC2626)

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
    if (cur[from] != null) return null
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

/** FLIP uçuşu ölçeği: 1.14 → 1.03 (%82) → 1 */
private fun flightScale(t: Float): Float = if (t < 0.82f) 1.14f + (1.03f - 1.14f) * (t / 0.82f) else 1.03f + (1f - 1.03f) * ((t - 0.82f) / 0.18f)

/** Darbe efekti: genişleyen halka + yönlü kıvılcım çizgileri (konsept tuval motoru) */
private fun DrawScope.drawImpact(center: Offset, s: Float, kind: String, p: Float) {
    val ease = 1f - (1f - p) * (1f - p) * (1f - p)
    val alpha = 1f - p
    val color = when (kind) { "brilliant" -> K.Ink; "capture", "checkmate" -> K.Red; else -> K.Blue }
    val u = s / 90f // konseptte kare 90 tuval birimi
    val mate = kind == "checkmate"
    drawCircle(color.copy(alpha = alpha * 0.92f), (16 + ease * (if (mate) 96 else 58)) * u, center, style = Stroke((if (mate) 4f else 3f) * u))
    if (mate || kind == "brilliant") drawCircle(color.copy(alpha = alpha * 0.6f), (8 + ease * 40) * u, center, style = Stroke(1.5f * u))
    val count = if (mate) 16 else 8
    val speed = if (mate) 4.2f else 2.8f
    val len = if (kind == "brilliant") 16f else 11f
    for (i in 0 until count) {
        val ang = (2 * PI * i / count).toFloat() + if (kind == "brilliant") 0f else 0.2f
        val dist = 12 + ease * speed * 18
        val a = center + Offset(cos(ang), sin(ang)) * (dist * u)
        val b = center + Offset(cos(ang), sin(ang)) * ((dist + len * (1 - p * 0.5f)) * u)
        drawLine(color.copy(alpha = alpha), a, b, 2.5f * u, cap = StrokeCap.Butt)
    }
}

/** İçe çizilen çerçeve (kare kenarına `w` kalınlığında) */
private fun DrawScope.inset(tl: Offset, s: Float, color: Color, w: Float, effect: PathEffect? = null, pad: Float = 0f) {
    drawRect(color, tl + Offset(w / 2 + pad, w / 2 + pad), Size(s - w - 2 * pad, s - w - 2 * pad), style = Stroke(w, pathEffect = effect))
}

/**
 * Öğretici tahta: kenardan kenara 8×8 ızgara, dokun-dokun / sürükle-bırak,
 * FLIP taş uçuşu, kobalt yasal noktalar, kızıl alım halkası, tehdit kutuları,
 * şah sonarı, kehribar ipucu, vektör oklar, hamle damgası ve darbe efektleri.
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
    hint: String? = null,
    threats: Set<String> = emptySet(),
    badge: SquareBadge? = null,
    flash: Flash? = null,
    fx: BoardFx? = null,
    shake: Int = 0,
    theme: BoardTheme? = null,
    coordinates: Boolean = true,
    onMove: (String) -> Unit = {},
) {
    val themeId = Prefs.boardTheme
    val th = theme ?: boardTheme(themeId)
    val drawPiece = rememberPieceDrawer()
    val reduced = Motion.reduced
    val board = remember(fen) { parseBoard(fen) }
    val holder = remember { Holder() }
    val plan = remember(fen) { if (reduced) null else planFor(holder.lastBoard, board, lastMove, holder.skipNext) }
    // alım olduysa (sürüklenerek bile) hedef karede şok dalgası
    val burst = remember(fen) {
        val prev = holder.lastBoard
        if (!reduced && prev != null && lastMove != null && lastMove.length >= 4 && prev.size > board.size) lastMove.substring(2, 4) else null
    }
    SideEffect { holder.lastBoard = board; holder.skipNext = false }

    val flight = remember(fen) { Animatable(if (plan == null) 1f else 0f) }
    LaunchedEffect(fen) { if (plan != null) flight.animateTo(1f, tween(240, easing = LinearEasing)) }
    val burstT = remember(fen) { Animatable(if (burst == null) 1f else 0f) }
    LaunchedEffect(fen) { if (burst != null) burstT.animateTo(1f, tween(480, easing = LinearEasing)) }
    val fxT = remember(fx?.token) { Animatable(if (fx == null || reduced) 1f else 0f) }
    LaunchedEffect(fx?.token) { if (fx != null && !reduced) fxT.animateTo(1f, tween(if (fx.kind == "checkmate") 760 else 480, easing = LinearEasing)) }

    val stampT = remember(badge) { Animatable(if (badge == null || reduced) 1f else 0f) }
    LaunchedEffect(badge) { if (badge != null && !reduced) stampT.animateTo(1f, tween(320, easing = Motion.Expo)) }
    val arrowT = remember(arrows) { Animatable(if (arrows.isEmpty() || reduced) 1f else 0f) }
    LaunchedEffect(arrows) { if (arrows.isNotEmpty() && !reduced) arrowT.animateTo(1f, tween(340, easing = Motion.Expo)) }
    val flashAlpha = remember(flash?.token) { Animatable(if (flash == null) 0f else 0.7f) }
    LaunchedEffect(flash?.token) { if (flash != null) flashAlpha.animateTo(0f, tween(if (reduced) 300 else 900)) }
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0 && !reduced) shakeX.animateTo(0f, keyframes { durationMillis = 280; -5f at 62 using Motion.Expo; 5f at 134; -3f at 202 })
    }
    // şah sonarı (1.1 s, sonsuz)
    val sonar = if (check && Motion.loops) {
        rememberInfiniteTransition(label = "sonar").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "s").value
    } else 0.5f

    var selected by remember(fen) { mutableStateOf<String?>(null) }
    val selAnim = remember { Animatable(1f) }
    LaunchedEffect(selected) { if (selected != null && !reduced) { selAnim.snapTo(0f); selAnim.animateTo(1f, tween(160, easing = Motion.Expo)) } }
    var dragFrom by remember(fen) { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var promo by remember(fen) { mutableStateOf<List<String>?>(null) }
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val turnWhite = fen.split(' ').getOrNull(1) != "b"

    fun squareAt(o: Offset, size: Float): String? {
        val x = (o.x / size).toInt(); val y = (o.y / size).toInt()
        if (o.x < 0 || o.y < 0 || x !in 0..7 || y !in 0..7) return null
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

    Box(modifier.fillMaxWidth().aspectRatio(1f).offset { IntOffset((shakeX.value * density).roundToInt(), 0) }) {
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
            val t = flight.value
            val pos = Motion.Expo.transform(t)
            val lm = lastMove?.takeIf { it.length >= 4 }
            val hintFrom = hint?.take(2); val hintTo = hint?.takeIf { it.length >= 4 }?.substring(2, 4)
            val dropSq = dragFrom?.let { squareAt(dragPos, s) }

            // 1) ızgara: kareler, son hamle sisi, seçim, ipucu, tehdit kutuları
            for (x in 0..7) for (y in 0..7) {
                val f = if (whiteBottom) x else 7 - x
                val r = if (whiteBottom) 7 - y else y
                val name = sq(f, r)
                val tl = Offset(x * s, y * s)
                val light = (f + r) % 2 == 1
                val last = lm != null && (lm.startsWith(name) || lm.substring(2, 4) == name)
                drawRect(if (last) (if (light) th.lastLight else th.lastDark) else (if (light) th.light else th.dark), tl, Size(s, s))
                if (flash != null && flash.square == name && flashAlpha.value > 0f) drawRect(flash.color.copy(alpha = flashAlpha.value), tl, Size(s, s))
                if (name in highlights) inset(tl, s, K.Yellow, 3.dp.toPx())
                if (name == hintFrom) inset(tl, s, K.Yellow, 2.dp.toPx())
                if (name == hintTo) inset(tl, s, K.Yellow, 3.dp.toPx())
                if (selected == name) inset(tl, s, K.Blue, 3.dp.toPx())
                if (name == dropSq && name != dragFrom && legal.any { it.startsWith(dragFrom!! + name) }) inset(tl, s, K.Blue, 2.dp.toPx())
                if (name in threats) inset(tl, s, K.Red.copy(alpha = 0.92f), 1.5.dp.toPx(), PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())), pad = 3.dp.toPx())
            }
            // koordinatlar (mono 9sp, %72)
            if (coordinates) {
                val st = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                for (i in 0..7) {
                    val f = if (whiteBottom) i else 7 - i
                    val rBottom = if (whiteBottom) 0 else 7
                    val lightF = (f + rBottom) % 2 == 1
                    val fl = measurer.measure("${'a' + f}", st.copy(color = (if (lightF) th.coordOnLight else th.coordOnDark).copy(alpha = 0.72f)))
                    drawText(fl, topLeft = Offset(i * s + s - fl.size.width - 3.dp.toPx(), 8 * s - fl.size.height - 2.dp.toPx()))
                    val r = if (whiteBottom) 7 - i else i
                    val fLeft = if (whiteBottom) 0 else 7
                    val lightR = (fLeft + r) % 2 == 1
                    val rl = measurer.measure("${r + 1}", st.copy(color = (if (lightR) th.coordOnLight else th.coordOnDark).copy(alpha = 0.72f)))
                    drawText(rl, topLeft = Offset(3.dp.toPx(), i * s + 2.dp.toPx()))
                }
            }
            // şah sonarı: kızıl radyal, nabız gibi
            if (check) {
                val k = if (turnWhite) 'K' else 'k'
                board.entries.firstOrNull { it.value == k }?.let { e ->
                    val tl = topLeft(e.key, s)
                    val c = tl + Offset(s / 2, s / 2)
                    val wave = 0.5f - 0.5f * cos(sonar * 2 * PI.toFloat())
                    val op = 0.92f - 0.47f * wave
                    val sc = 0.96f + 0.04f * wave
                    scale(sc, pivot = c) {
                        drawRect(Brush.radialGradient(listOf(K.Red.copy(alpha = 0.85f * op), K.Red.copy(alpha = 0.25f * op)), center = c, radius = s * 0.75f), tl, Size(s, s))
                    }
                }
            }

            // 2) taşlar — dinlenirken düz, kalkınca gölgeli
            val moving = plan?.slides?.map { it.to }?.toSet().orEmpty()
            for ((sqName, pc) in board) {
                if (sqName == dragFrom) continue
                if (t < 1f && sqName in moving) continue
                val tl = topLeft(sqName, s)
                if (sqName == selected && dragFrom == null) {
                    // seçili taş: 3dp yukarı, %8 büyük, kalkış gölgesi
                    val g = selAnim.value
                    val lift = 3.dp.toPx() * g
                    val sz = s * (1f + 0.08f * g)
                    drawOval(Color.Black.copy(alpha = 0.35f * g), tl + Offset(s * 0.2f, s * 0.78f), Size(s * 0.6f, s * 0.16f))
                    drawPiece(pc, tl - Offset((sz - s) / 2, (sz - s) / 2 + lift), sz, 1f)
                } else drawPiece(pc, tl, s, 1f)
            }
            if (plan != null && t < 1f) {
                plan.captured?.let { (cs, cp) -> drawPiece(cp, topLeft(cs, s), s, 1f - pos) }
                val sc = flightScale(t)
                for (sl in plan.slides) {
                    val a = topLeft(sl.from, s); val b = topLeft(sl.to, s)
                    val p = Offset(a.x + (b.x - a.x) * pos, a.y + (b.y - a.y) * pos)
                    drawOval(Color.Black.copy(alpha = 0.4f * (1 - t)), p + Offset(s * 0.18f, s * 0.8f), Size(s * 0.64f, s * 0.16f))
                    val sz = s * sc
                    drawPiece(sl.piece, p - Offset((sz - s) / 2, (sz - s) / 2 + s * 0.1f * (sc - 1f) / 0.14f), sz, 1f)
                }
            }

            // 3) yasal hamle işaretleri: kobalt nokta (tebeşir kenarlı), kızıl alım halkası
            selected?.let { sel ->
                val g = selAnim.value
                val gs = 0.4f + 0.6f * g
                legal.filter { it.startsWith(sel) }.map { it.substring(2, 4) }.distinct().forEach { target ->
                    val tl = topLeft(target, s)
                    val c = tl + Offset(s / 2, s / 2)
                    if (board.containsKey(target)) {
                        drawCircle(K.Red.copy(alpha = g), (s / 2 - 4.dp.toPx() - 1.5.dp.toPx()) * gs, c, style = Stroke(3.dp.toPx()))
                    } else {
                        val r = s * 0.135f * gs
                        drawCircle(K.Blue.copy(alpha = 0.76f * g), r, c)
                        drawCircle(K.Ink.copy(alpha = 0.94f * g), r, c, style = Stroke(2.dp.toPx() * gs))
                    }
                }
            }

            // 4) vektör oklar
            val at = arrowT.value
            for (a in arrows) {
                if (a.uci.length < 4) continue
                val c1 = topLeft(a.uci.substring(0, 2), s) + Offset(s / 2, s / 2)
                val c2 = topLeft(a.uci.substring(2, 4), s) + Offset(s / 2, s / 2)
                val ang = atan2(c2.y - c1.y, c2.x - c1.x)
                val len = hypot(c2.x - c1.x, c2.y - c1.y)
                val dir = Offset(cos(ang), sin(ang))
                val start = c1 + dir * (s * 0.24f)
                val tip = c2 - dir * (s * 0.2f)
                val headLen = s * 0.42f
                val shaftEnd = c2 - dir * (s * 0.2f + headLen * 0.85f)
                val frac = if (a.dashed) 1f else at
                val alpha = if (a.dashed) 0.92f * at else 0.2f + 0.72f * at
                val col = a.color.copy(alpha = alpha * 0.96f)
                val curEnd = start + (shaftEnd - start) * frac
                val w = s * 0.1f
                if (len > s * 0.5f) drawLine(col, start, curEnd, w, cap = if (a.dashed) StrokeCap.Butt else StrokeCap.Round,
                    pathEffect = if (a.dashed) PathEffect.dashPathEffect(floatArrayOf(s * 0.22f, s * 0.14f)) else null)
                if (frac > 0.6f) {
                    val tipNow = shaftEnd + (tip - shaftEnd) * ((frac - 0.6f) / 0.4f)
                    val n = Offset(-dir.y, dir.x)
                    val back = tipNow - dir * headLen
                    val head = Path().apply {
                        moveTo(tipNow.x, tipNow.y)
                        lineTo(back.x + n.x * headLen * 0.5f, back.y + n.y * headLen * 0.5f)
                        lineTo(back.x - n.x * headLen * 0.5f, back.y - n.y * headLen * 0.5f)
                        close()
                    }
                    drawPath(head, col)
                }
            }

            // 5) darbe efektleri
            if (burst != null && burstT.value < 1f) drawImpact(topLeft(burst, s) + Offset(s / 2, s / 2), s, "capture", burstT.value)
            if (fx != null && fxT.value < 1f) drawImpact(topLeft(fx.square, s) + Offset(s / 2, s / 2), s, fx.kind, fxT.value)

            // 6) hamle damgası: hedef karenin sağ üst köşesine iner
            if (badge != null && t >= 1f) CLASS_INFO[badge.cls]?.let { info ->
                val tl = topLeft(badge.square, s)
                val k = stampT.value
                val bs = maxOf(22.dp.toPx(), s * 0.42f)
                val o = Offset(tl.x + s + 4.dp.toPx() - bs, tl.y - 4.dp.toPx() - 6.dp.toPx() * (1 - k))
                val c = o + Offset(bs / 2, bs / 2)
                scale(1.6f - 0.6f * k, pivot = c) {
                    val cr = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    drawRoundRect(info.color.copy(alpha = k), o, Size(bs, bs), cr)
                    drawRoundRect(K.Base.copy(alpha = k), o, Size(bs, bs), cr, style = Stroke(1.dp.toPx()))
                    val tx = measurer.measure(info.symbol, TextStyle(color = info.onColor.copy(alpha = k), fontSize = (bs * 0.5f / density / fontScale).sp, fontWeight = FontWeight.Bold, fontFamily = Mono))
                    drawText(tx, topLeft = Offset(c.x - tx.size.width / 2f, c.y - tx.size.height / 2f))
                }
            }

            // 7) sürüklenen taş: başparmağın üstünde, %116, derin gölge
            dragFrom?.let { d ->
                board[d]?.let { pc ->
                    val sz = s * 1.16f
                    drawOval(Color.Black.copy(alpha = 0.45f), dragPos + Offset(-sz * 0.32f, sz * 0.3f), Size(sz * 0.64f, sz * 0.18f))
                    drawPiece(pc, dragPos - Offset(sz * 0.5f, sz * 0.62f), sz, 1f)
                }
            }
        }
    }

    promo?.let { cands ->
        Dialog(onDismissRequest = { promo = null }) {
            Column(Modifier.background(K.Surface).border(1.dp, K.LineStrong).padding(14.dp)) {
                Text("TERFİ", style = KType.label, color = K.Ink2)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (pc in listOf('q', 'r', 'b', 'n')) {
                        val uci = cands.firstOrNull { it.endsWith(pc) } ?: continue
                        val ch = if (turnWhite) pc.uppercaseChar() else pc
                        Box(
                            Modifier.size(60.dp).background(th.light).border(1.dp, K.LineSubtle)
                                .press(scale = 0.94f) { promo = null; holder.skipNext = false; onMove(uci) },
                            contentAlignment = Alignment.Center,
                        ) { Canvas(Modifier.size(56.dp)) { drawPiece(ch, Offset.Zero, size.width, 1f) } }
                    }
                }
            }
        }
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

/** Alınan taşları küçük çizen satır (oyuncu şeridinde) */
@Composable
fun CapturedRow(pieces: List<Char>, advantage: Int) {
    val drawPiece = rememberPieceDrawer()
    if (pieces.isEmpty() && advantage <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
        Canvas(Modifier.size(width = (pieces.size * 9 + 6).dp, height = 14.dp)) {
            pieces.forEachIndexed { i, c -> drawPiece(c, Offset(i * 9.dp.toPx(), 0f), 14.dp.toPx(), 1f) }
        }
        if (advantage > 0) Text("+$advantage", style = KType.mono.copy(fontSize = 11.sp), color = K.Ink2)
    }
}
