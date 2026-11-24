package dev.chesscrab.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.ui.CLASS_INFO
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.Display
import dev.chesscrab.app.ui.Mono
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontStyle
import kotlin.math.PI
import kotlin.math.sin
import dev.chesscrab.app.ui.board.drawPieceImage
import dev.chesscrab.app.ui.board.rememberPieces
import kotlin.math.exp
import kotlin.random.Random

fun winPercent(cp: Int): Float {
    val c = cp.coerceIn(-1000, 1000)
    return (50 + 50 * (2 / (1 + exp(-0.00368208 * c)) - 1)).toFloat()
}

fun scoreLabel(cp: Int): String = when {
    cp >= 9000 -> "M${(10000 - cp) / 10}"
    cp <= -9000 -> "-M${(10000 + cp) / 10}"
    else -> (if (cp >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", cp / 100.0)
}

/** Küçük, aralıklı, monospace üst başlık ("KOÇ NOTU", "HAMLE 12") */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Crabs.Muted) {
    Text(text.uppercase(java.util.Locale.forLanguageTag("tr")), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Arka plan için yumuşak dalga çizgileri */
@Composable
fun WaveBackdrop(modifier: Modifier = Modifier, color: Color = Crabs.Sand, lines: Int = 4, phase: Float = 0f) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        for (k in 0 until lines) {
            val baseY = h * (0.45f + k * 0.14f)
            val amp = h * (0.05f + k * 0.012f)
            val path = Path()
            var x = 0f
            path.moveTo(0f, baseY)
            while (x <= w) {
                val y = baseY + amp * sin((x / w * 2 * PI * (1.2 + k * 0.25) + phase + k).toFloat())
                path.lineTo(x, y); x += 6f
            }
            drawPath(path, color.copy(alpha = 0.10f - k * 0.018f), style = Stroke(1.6.dp.toPx()))
        }
    }
}

/** Dikey değerlendirme çubuğu: ince kapsül, kum (beyaz) / mürekkep (siyah). */
@Composable
fun EvalBar(whiteCp: Int, whiteBottom: Boolean, modifier: Modifier = Modifier) {
    val target = winPercent(whiteCp) / 100f
    val w by animateFloatAsState(target, tween(650, easing = FastOutSlowInEasing), label = "eval")
    val whiteAhead = whiteCp >= 0
    Box(modifier.width(17.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF07101A)).border(1.dp, Crabs.Line, RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val hWhite = size.height * w
            val top = if (whiteBottom) size.height - hWhite else 0f
            drawRect(Brush.verticalGradient(listOf(Crabs.Sand, Color(0xFFE2D2AE)), startY = top, endY = top + hWhite), Offset(0f, top), Size(size.width, hWhite))
            // orta çizgi
            drawLine(Crabs.Coral.copy(alpha = 0.8f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
        }
        val atBottom = whiteAhead == whiteBottom
        Text(
            scoreLabel(whiteCp).removePrefix("+").removePrefix("-").let { if (it.length > 3 && !it.startsWith("M")) it.substringBefore('.') else it },
            fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
            color = if (whiteAhead) Crabs.Ink else Crabs.Sand,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter).padding(vertical = 4.dp).fillMaxWidth(),
        )
    }
}

/**
 * "Gelgit çizgisi": oyun boyunca kazanma yüzdesi yumuşak bir dalga olarak.
 * Kum dolgu beyazın, koyu boşluk siyahın payı. Yeni hamlede dalga ilerler;
 * dokunarak/sürükleyerek hamleye gidilir.
 */
@Composable
fun EvalGraph(
    wins: List<Float>,
    classes: List<String?>,
    selected: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(wins.size) { reveal.snapTo(0f); reveal.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    val sel by animateFloatAsState((selected ?: -1).toFloat(), tween(220), label = "sel")
    val n = (wins.size - 1).coerceAtLeast(1)
    var m = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07101A)).border(1.dp, Crabs.Line, RoundedCornerShape(16.dp))
    if (onSelect != null) {
        m = m.pointerInput(wins.size) { detectTapGestures { o -> onSelect(((o.x / size.width) * n + 0.5f).toInt().coerceIn(0, n)) } }
            .pointerInput(wins.size) { detectHorizontalDragGestures { ch, _ -> onSelect(((ch.position.x / size.width) * n + 0.5f).toInt().coerceIn(0, n)) } }
    }
    Canvas(modifier.then(m)) {
        if (wins.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val pad = 6.dp.toPx()
        val x = { i: Float -> i / n * w }
        val y = { p: Float -> pad + (h - 2 * pad) * (1 - p / 100f) }
        val last = wins.size - 1
        val pts = (0..last).map { i ->
            if (i == last && last > 0) {
                val px = x(i - 1f) + (x(i.toFloat()) - x(i - 1f)) * reveal.value
                val py = y(wins[i - 1]) + (y(wins[i]) - y(wins[i - 1])) * reveal.value
                Offset(px, py)
            } else Offset(x(i.toFloat()), y(wins[i]))
        }
        // yumuşak eğri (Catmull-Rom → Bezier)
        val curve = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[(i - 2).coerceAtLeast(0)]; val p1 = pts[i - 1]; val p2 = pts[i]; val p3 = pts[(i + 1).coerceAtMost(pts.size - 1)]
                cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
            }
        }
        val fill = Path().apply { addPath(curve); lineTo(pts.last().x, h); lineTo(pts[0].x, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Crabs.Sand.copy(alpha = 0.95f), Crabs.Sand.copy(alpha = 0.55f))))
        drawPath(curve, Crabs.Sand, style = Stroke(2.dp.toPx()))
        drawLine(Crabs.Muted.copy(alpha = 0.45f), Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        classes.forEachIndexed { i, c ->
            val info = CLASS_INFO[c ?: return@forEachIndexed] ?: return@forEachIndexed
            if (c !in setOf("blunder", "mistake", "miss", "brilliant", "great", "inaccuracy")) return@forEachIndexed
            val ply = i + 1
            if (ply < pts.size) {
                val o = pts[ply]
                rotate(45f, o) { drawRect(Crabs.Ink, o - Offset(5.5f, 5.5f), Size(11f, 11f)); drawRect(info.color, o - Offset(4f, 4f), Size(8f, 8f)) }
            }
        }
        if (sel >= 0) {
            val sx = x(sel.coerceAtMost(n.toFloat()))
            drawLine(Crabs.Coral, Offset(sx, 0f), Offset(sx, h), strokeWidth = 2.dp.toPx())
            val i = sel.toInt().coerceIn(0, last)
            val sy = pts[i].y
            drawCircle(Crabs.Coral.copy(alpha = 0.3f), 9.dp.toPx(), Offset(sx, sy))
            drawCircle(Crabs.Coral, 4.dp.toPx(), Offset(sx, sy))
        }
    }
}

/** Hamle sınıfı rozeti: yuvarlatılmış kare ("çakıl") */
@Composable
fun ClassBadge(cls: String, size: Dp = 22.dp) {
    val info = CLASS_INFO[cls] ?: return
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(info.color), contentAlignment = Alignment.Center) {
        Text(info.symbol, color = Crabs.Ink, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (size.value * (if (info.symbol.length > 1) 0.42f else 0.55f)).sp)
    }
}

/** Yengeç koç avatarı */
@Composable
fun CoachAvatar(size: Dp = 40.dp, emoji: String = "🦀", bg: Color = Crabs.Coral) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(bg.copy(alpha = 0.35f), bg.copy(alpha = 0.12f))))
            .border(1.5.dp, bg.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.5f).sp) }
}

/**
 * Koç notu: sohbet balonu yerine kenar boşluğuna düşülmüş bir not.
 * Sol tarafta sınıf renginde ince şerit, üstte monospace etiket.
 */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier, cls: String? = null, title: String? = null, extra: (@Composable () -> Unit)? = null) {
    val accent = CLASS_INFO[cls]?.color ?: Crabs.Coral
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoachAvatar(36.dp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(Crabs.Card)
                .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) },
        ) {
            AnimatedContent(targetState = Triple(text, cls, title), transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 }) togetherWith fadeOut(tween(120))
            }, label = "coach") { (t, c, ti) ->
                Column(Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c != null) { ClassBadge(c, 18.dp); Spacer(Modifier.width(8.dp)) }
                        Text(
                            ti ?: CLASS_INFO[c]?.label ?: "Yengeç Koç",
                            color = CLASS_INFO[c]?.color ?: Crabs.Sand,
                            fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(t, color = Crabs.Text, fontSize = 14.5.sp, lineHeight = 21.sp)
                    extra?.invoke()
                }
            }
        }
    }
}

/** Oyuncu satırı: avatar, isim, puan, aldığı taşlar, saat. */
@Composable
fun PlayerBar(
    name: String,
    subtitle: String,
    avatar: String,
    avatarColor: Color,
    captured: List<Char>,
    advantage: Int,
    clock: String? = null,
    clockActive: Boolean = false,
    thinking: Boolean = false,
) {
    val pieces = rememberPieces()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(avatarColor.copy(alpha = 0.22f)).border(1.dp, avatarColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(avatar, fontSize = 18.sp, color = Crabs.Text, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(subtitle, color = Crabs.Faint, fontSize = 11.sp, fontFamily = Mono)
                if (thinking) { Spacer(Modifier.width(8.dp)); ThinkingDots() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp)) {
                Canvas(Modifier.width((captured.size * 10 + 8).dp).height(16.dp)) {
                    captured.forEachIndexed { i, c -> pieces[c]?.let { drawPieceImage(it, Offset(i * 10.dp.toPx(), 0f), 16.dp.toPx()) } }
                }
                if (advantage > 0) Text("+$advantage", color = Crabs.Muted, fontSize = 11.sp, fontFamily = Mono)
            }
        }
        if (clock != null) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (clockActive) Crabs.Sand else Color.Transparent)
                    .border(1.dp, if (clockActive) Crabs.Sand else Crabs.Line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(clock, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = if (clockActive) Crabs.Ink else Crabs.Muted, fontFamily = Mono)
            }
        }
    }
}

@Composable
fun ThinkingDots() {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { t = it } }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0..2) {
            val phase = ((t / 1_000_000L + i * 150) % 900) / 900f
            val a = 0.3f + 0.7f * (1 - kotlin.math.abs(phase * 2 - 1))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Crabs.Coral.copy(alpha = a)))
        }
    }
}

/** 0'dan hedefe sayan sayı (doğruluk yüzdesi) */
@Composable
fun CountUp(target: Float, decimals: Int = 1, durationMs: Int = 1200): String {
    val a = remember(target) { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return String.format(java.util.Locale.US, "%.${decimals}f", a.value)
}

/** Ders bitince konfeti (mercan, deniz köpüğü, kum, altın) */
@Composable
fun Confetti(modifier: Modifier = Modifier, durationMs: Long = 2600) {
    val parts = remember {
        val r = Random(42)
        List(90) {
            floatArrayOf(r.nextFloat(), -r.nextFloat() * 0.3f, (r.nextFloat() - 0.5f) * 0.25f, 0.5f + r.nextFloat() * 0.6f, r.nextFloat() * 360f, r.nextInt(5).toFloat())
        }
    }
    val colors = listOf(Crabs.Coral, Crabs.Sea, Crabs.Gold, Crabs.Sand, Color(0xFF7FB2FF))
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        start = withFrameNanos { it }
        while ((now - start) / 1_000_000 < durationMs) withFrameNanos { now = it }
    }
    val t = ((now - start) / 1_000_000f / 1000f).coerceAtLeast(0f)
    if ((now - start) / 1_000_000 >= durationMs) return
    Canvas(modifier.fillMaxSize()) {
        for (p in parts) {
            val px = (p[0] + p[2] * t) * size.width
            val py = (p[1] + p[3] * t + 0.35f * t * t) * size.height
            if (py > size.height) continue
            rotate(p[4] + t * 360f, Offset(px, py)) {
                drawRect(colors[p[5].toInt()], Offset(px - 6f, py - 3f), Size(12f, 6f), alpha = (1f - t / (durationMs / 1000f)).coerceIn(0f, 1f))
            }
        }
    }
}

/**
 * Hap biçimli ana eylem butonu. Renk mercan değilse (ör. Crabs.Card) ince
 * çerçeveli "hayalet" buton olarak çizilir.
 */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color = Crabs.Coral, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val ghost = color == Crabs.Card || color == Crabs.Surface || color == Crabs.CardHi
    val fg = if (ghost) Crabs.Text else Crabs.Ink
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (ghost) Color.Transparent else if (enabled) color else color.copy(alpha = 0.35f))
            .border(1.dp, if (ghost) Crabs.Line.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.6f), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.2.sp)
        }
    }
}

/** İnce çerçeveli koyu kart */
@Composable
fun CrabCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(20.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(16.dp)) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 22.dp, bottom = 10.dp))
}

/** Dairesel ilerleme halkası */
@Composable
fun ProgressRing(fraction: Float, size: Dp = 46.dp, color: Color = Crabs.Coral, label: String? = null, track: Color = Crabs.Line) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = LinearEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = size.toPx() * 0.08f
            drawArc(track, 0f, 360f, false, style = Stroke(st))
            drawArc(color, -90f, 360f * f, false, style = Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (label != null) Text(label, fontSize = (size.value * 0.24f).sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

/** Seçilebilir hap: seçiliyse mercan dolgu, değilse ince çerçeve. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, mono: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Crabs.Coral else Color.Transparent)
            .border(1.dp, if (selected) Crabs.Coral else Crabs.Line.copy(alpha = 0.22f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) Crabs.Ink else Crabs.Text, fontFamily = if (mono) Mono else null)
    }
}
