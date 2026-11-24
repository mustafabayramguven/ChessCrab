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

/** Dikey değerlendirme çubuğu (tahtanın yanında). */
@Composable
fun EvalBar(whiteCp: Int, whiteBottom: Boolean, modifier: Modifier = Modifier) {
    val target = winPercent(whiteCp) / 100f
    val w by animateFloatAsState(target, tween(600, easing = FastOutSlowInEasing), label = "eval")
    val whiteAhead = whiteCp >= 0
    Box(modifier.width(18.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF403D39))) {
        Canvas(Modifier.fillMaxSize()) {
            val hWhite = size.height * w
            if (whiteBottom) drawRect(Color(0xFFF2F2F2), Offset(0f, size.height - hWhite), Size(size.width, hWhite))
            else drawRect(Color(0xFFF2F2F2), Offset.Zero, Size(size.width, hWhite))
        }
        val atBottom = whiteAhead == whiteBottom
        Text(
            scoreLabel(whiteCp).removePrefix("+"),
            fontSize = 8.sp, fontWeight = FontWeight.Bold,
            color = if (whiteAhead) Color(0xFF403D39) else Color(0xFFF2F2F2),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter).padding(vertical = 3.dp).fillMaxWidth(),
        )
    }
}

/**
 * Oyun boyunca kazanma yüzdesi grafiği (chess.com İnceleme tarzı). Yeni noktalar
 * animasyonla çizilir; dokunarak/sürükleyerek hamleye gidilir.
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
    LaunchedEffect(wins.size) { reveal.snapTo(0f); reveal.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    val n = (wins.size - 1).coerceAtLeast(1)
    var m = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(Color(0xFF403D39))
    if (onSelect != null) {
        m = m.pointerInput(wins.size) { detectTapGestures { o -> onSelect(((o.x / size.width) * n + 0.5f).toInt().coerceIn(0, n)) } }
            .pointerInput(wins.size) { detectHorizontalDragGestures { ch, _ -> onSelect(((ch.position.x / size.width) * n + 0.5f).toInt().coerceIn(0, n)) } }
    }
    Canvas(modifier.then(m)) {
        if (wins.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val x = { i: Float -> i / n * w }
        val y = { p: Float -> h - p / 100f * h }
        // son segment animasyonla uzar
        val last = wins.size - 1
        val path = Path().apply {
            moveTo(0f, h)
            for (i in 0..last) {
                val px: Float; val py: Float
                if (i == last && last > 0) {
                    val prevY = y(wins[i - 1])
                    px = x(i - 1f) + (x(i.toFloat()) - x(i - 1f)) * reveal.value
                    py = prevY + (y(wins[i]) - prevY) * reveal.value
                } else { px = x(i.toFloat()); py = y(wins[i]) }
                lineTo(px, py)
                if (i == last) lineTo(px, h)
            }
            close()
        }
        drawPath(path, Color(0xFFF2F2F2))
        drawLine(Color(0x55888888), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.5f)
        classes.forEachIndexed { i, c ->
            val info = CLASS_INFO[c ?: return@forEachIndexed] ?: return@forEachIndexed
            if (c !in setOf("blunder", "mistake", "miss", "brilliant", "great", "inaccuracy")) return@forEachIndexed
            val ply = i + 1
            if (ply < wins.size) {
                drawCircle(Color.White, 6.5f, Offset(x(ply.toFloat()), y(wins[ply])))
                drawCircle(info.color, 5f, Offset(x(ply.toFloat()), y(wins[ply])))
            }
        }
        selected?.let {
            val sx = x(it.toFloat().coerceAtMost(n.toFloat()))
            drawLine(Crabs.Green, Offset(sx, 0f), Offset(sx, h), strokeWidth = 3f)
            if (it < wins.size) drawCircle(Crabs.Green, 7f, Offset(sx, y(wins[it])))
        }
    }
}

@Composable
fun ClassBadge(cls: String, size: Dp = 22.dp) {
    val info = CLASS_INFO[cls] ?: return
    Box(Modifier.size(size).clip(CircleShape).background(info.color), contentAlignment = Alignment.Center) {
        Text(info.symbol, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * (if (info.symbol.length > 1) 0.42f else 0.55f)).sp)
    }
}

/** Yengeç koç avatarı */
@Composable
fun CoachAvatar(size: Dp = 44.dp, emoji: String = "🦀", bg: Color = Crabs.Orange) {
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(bg, bg.copy(alpha = 0.7f)))), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Koçun konuşma balonu; metin değişince yumuşakça geçer. */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier, cls: String? = null, title: String? = null, extra: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoachAvatar()
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp), color = Color.White, modifier = Modifier.weight(1f)) {
            AnimatedContent(targetState = Triple(text, cls, title), transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(120))
            }, label = "coach") { (t, c, ti) ->
                Column(Modifier.padding(12.dp)) {
                    if (c != null || ti != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c != null) { ClassBadge(c, 20.dp); Spacer(Modifier.width(6.dp)) }
                            Text(ti ?: CLASS_INFO[c]?.label ?: "", color = CLASS_INFO[c]?.color?.let { darken(it) } ?: Color(0xFF302E2B), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(t, color = Color(0xFF302E2B), fontSize = 14.5.sp, lineHeight = 20.sp)
                    extra?.invoke()
                }
            }
        }
    }
}

private fun darken(c: Color) = Color(c.red * 0.78f, c.green * 0.78f, c.blue * 0.78f)

/** Oyuncu çubuğu: avatar, isim, puan, aldığı taşlar, saat. */
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
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(avatarColor), contentAlignment = Alignment.Center) {
            Text(avatar, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(subtitle, color = Crabs.Muted, fontSize = 12.sp)
                if (thinking) { Spacer(Modifier.width(6.dp)); ThinkingDots() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp)) {
                Canvas(Modifier.width((captured.size * 10 + 8).dp).height(16.dp)) {
                    captured.forEachIndexed { i, c -> pieces[c]?.let { drawPieceImage(it, Offset(i * 10.dp.toPx(), 0f), 16.dp.toPx()) } }
                }
                if (advantage > 0) Text("+$advantage", color = Crabs.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (clock != null) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(if (clockActive) Color.White else Color(0xFF262522)).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(clock, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (clockActive) Color(0xFF262522) else Crabs.Muted, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
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
            Box(Modifier.size(5.dp).clip(CircleShape).background(Crabs.Muted.copy(alpha = a)))
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

/** Ders bitince konfeti */
@Composable
fun Confetti(modifier: Modifier = Modifier, durationMs: Long = 2600) {
    val parts = remember {
        val r = Random(42)
        List(90) {
            floatArrayOf(r.nextFloat(), -r.nextFloat() * 0.3f, (r.nextFloat() - 0.5f) * 0.25f, 0.5f + r.nextFloat() * 0.6f, r.nextFloat() * 360f, r.nextInt(5).toFloat())
        }
    }
    val colors = listOf(Crabs.Green, Crabs.Orange, Crabs.Gold, Color(0xFF26C2A3), Color(0xFF749BBF))
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

/** Büyük, renkli eylem butonu (chess.com "Oyna" butonu gibi) */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color = Crabs.Green, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .border(0.dp, Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                if (enabled) drawRoundRect(Color(0x40000000), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(size.width, 4.dp.toPx()), cornerRadius = CornerRadius(10.dp.toPx()))
            }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}

/** Başlıklı koyu kart */
@Composable
fun CrabCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Crabs.Card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(14.dp)) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Dairesel ilerleme halkası */
@Composable
fun ProgressRing(fraction: Float, size: Dp = 46.dp, color: Color = Crabs.Green, label: String? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = LinearEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(size.toPx() * 0.11f)
            drawArc(Color(0xFF4F4C48), 0f, 360f, false, style = st)
            drawArc(color, -90f, 360f * f, false, style = Stroke(size.toPx() * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (label != null) Text(label, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Bold)
    }
}
