package dev.chesscrab.app.ui.kare

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.ui.Archivo
import dev.chesscrab.app.ui.DECODE
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Mono
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.codeColor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private val TR = Locale.forLanguageTag("tr")
fun String.upTr(): String = uppercase(TR)

/** Android Material 3 köşe yarıçapları (DESIGN: android-control 12, sm 8) */
val RControl = RoundedCornerShape(12.dp)
val RSmall = RoundedCornerShape(8.dp)
val RStamp = RoundedCornerShape(2.dp)
val TOUCH = 48.dp

// ---------------------------------------------------------------- yardımcılar

fun winPercent(cp: Int): Float {
    val c = cp.coerceIn(-1000, 1000)
    return (50 + 50 * (2 / (1 + exp(-0.00368208 * c)) - 1)).toFloat()
}

fun scoreLabel(cp: Int): String = when {
    cp >= 9000 -> "M${(10000 - cp) / 10}"
    cp <= -9000 -> "-M${(10000 + cp) / 10}"
    else -> (if (cp >= 0) "+" else "") + String.format(Locale.US, "%.1f", cp / 100.0)
}

/** "+1.8 BEYAZ" / "# MAT" biçiminde okuma */
fun evalReadout(cp: Int): String = when {
    abs(cp) >= 9000 -> "# MAT ${if (cp > 0) "BEYAZ" else "SİYAH"}"
    else -> "${scoreLabel(cp)} ${if (cp >= 0) "BEYAZ" else "SİYAH"}"
}

/** Basınca küçülen (120 ms, basış eğrisi) tıklama; dalga efekti yok */
fun Modifier.press(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !Motion.reduced) scale else 1f, tween(Motion.TAP, easing = Motion.Press), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, indication = null, enabled = enabled, onClick = onClick)
}

/** Alt kılçizgi (1px, Subtle) */
fun Modifier.hairlineBottom(color: Color = K.LineSubtle): Modifier = drawBehind {
    drawRect(color, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
}

fun Modifier.hairlineTop(color: Color = K.LineSubtle): Modifier = drawBehind {
    drawRect(color, Offset.Zero, Size(size.width, 1.dp.toPx()))
}

/** Sayan sayı (doğruluk, skor) — azaltılmış harekette anında */
@Composable
fun countUp(target: Float, decimals: Int = 1, durationMs: Int = 900): String {
    val a = remember(target) { Animatable(if (Motion.reduced) target else 0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = Motion.Expo)) }
    return String.format(Locale.US, "%.${decimals}f", a.value)
}

// ---------------------------------------------------------------- yapı taşları

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = K.LineSubtle) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Saville 5 renk şeridi */
@Composable
fun SavilleStrip(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (c in listOf(K.Industrial, K.Blue, K.Yellow, K.Red, K.Ink)) Box(Modifier.size(12.dp, 6.dp).background(c))
    }
}

/** Katalog kodu rozeti ("KRE 042") */
@Composable
fun CatalogCode(text: String, modifier: Modifier = Modifier, color: Color = K.Ink2, border: Color = K.LineSubtle) {
    Text(
        text.upTr(), style = KType.code, color = color, maxLines = 1,
        modifier = modifier.border(1.dp, border).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Oyma Saville katalog başlığı: büyük harf, geniş aralıklı başlık + katalog kodu + renk şeridi */
@Composable
fun TopBar(title: String, code: String, onBack: (() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(K.Chrome).hairlineBottom().heightIn(min = 52.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(TOUCH).press(scale = 0.9f, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Geri", tint = K.Ink2, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title.upTr(), style = KType.title, color = K.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            CatalogCode(code)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else SavilleStrip()
    }
}

/** Bölüm etiketi (büyük harf, 0.08em, ikincil) */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.upTr(), style = KType.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em), color = K.Ink2, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.upTr(), style = KType.code, color = K.Ink3)
    }
}

/** Saville imza butonu: obsidyen dolgu, güçlü kılçizgi, sağ kenarda renk başlığı */
@Composable
fun SavilleButton(text: String, modifier: Modifier = Modifier, cap: Color = K.Blue, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .press(enabled, 0.98f, onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .heightIn(min = TOUCH)
            .clip(RControl)
            .background(K.Base)
            .border(1.dp, K.LineStrong, RControl)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Satır içi kullanımda kompakt (metin + başlık); fillMaxWidth verilince metin solda, başlık sağ kenarda
        Text(
            text.upTr(), style = KType.label, color = K.Ink, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp, end = 10.dp, top = 15.dp, bottom = 15.dp),
        )
        Box(Modifier.width(14.dp).fillMaxHeight().background(cap))
    }
}

/** Küçük eylem: ikon + etiket, açıkken kobalt çerçeve */
@Composable
fun MiniAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val border by animateColorAsState(if (on) K.Blue else K.LineSubtle, tween(Motion.TAP), label = "ma")
    val bg by animateColorAsState(if (on) K.BlueSoft else K.Action, tween(Motion.TAP), label = "mab")
    Row(
        modifier.press(enabled, 0.97f, onClick).graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .heightIn(min = TOUCH).clip(RControl).background(bg).border(1.dp, border, RControl).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = K.Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = KType.caption, color = K.Ink, maxLines = 1)
    }
}

/** 3 sütunlu eylem satırı */
@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** Çözücü kod düğmesi: metin + sağda 8dp renk şeridi */
@Composable
fun CodeChip(text: String, swatch: Color, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bc by animateColorAsState(if (active) K.Ink else K.LineSubtle, tween(Motion.TAP), label = "cc")
    Row(
        modifier.press(scale = 0.97f, onClick = onClick).heightIn(min = TOUCH).clip(RSmall)
            .background(if (active) K.Elevated else K.Surface).border(1.dp, bc, RSmall).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = KType.label.copy(letterSpacing = 0.02.em), color = if (active) K.Ink else K.Ink2, maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 6.dp, end = 3.dp))
        Box(Modifier.width(8.dp).fillMaxHeight().background(swatch))
    }
}

/** Dört kodlu çözücü şerit (!! ! ?! ??) */
@Composable
fun DecodeBar(active: String?, modifier: Modifier = Modifier, onTap: (String) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((code, text) in DECODE) CodeChip(text, codeColor(code), active == code, Modifier.weight(1f)) { onTap(code) }
    }
}

/** Hamle zaman çizelgesi çipi (mono, kalite noktası; etkinse tebeşir dolgu) */
@Composable
fun PlyChip(label: String, dot: Color?, active: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg by animateColorAsState(if (active) K.Ink else K.Surface, tween(Motion.TAP), label = "pc")
    var m = modifier
    if (onClick != null) m = m.press(scale = 0.95f, onClick = onClick)
    Row(
        m.heightIn(min = if (compact) 30.dp else TOUCH).clip(RSmall).background(bg).border(1.dp, if (active) K.Ink else K.LineSubtle, RSmall)
            .padding(horizontal = if (compact) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(7.dp).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = KType.mono, color = if (active) K.Base else K.Ink2, maxLines = 1)
    }
}

/** Varyant dalı düğmesi */
@Composable
fun BranchButton(text: String, active: Boolean = false, modifier: Modifier = Modifier, mono: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.press(scale = 0.97f, onClick = onClick).heightIn(min = TOUCH).clip(RSmall)
            .background(if (active) K.BlueSoft else K.Base).border(1.dp, if (active) K.Blue else K.LineStrong, RSmall).padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = if (mono) KType.mono.copy(fontSize = 11.sp) else KType.caption, color = K.Ink, maxLines = 2)
    }
}

/** Bölümlü seçici düğmesi (etkinse tebeşir dolgu, obsidyen yazı) */
@Composable
fun SegButton(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) K.Ink else K.Base, tween(Motion.TAP), label = "sb")
    Box(
        modifier.press(scale = 0.98f, onClick = onClick).heightIn(min = TOUCH).background(bg).border(1.dp, if (active) K.Ink else K.LineSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.upTr(), style = KType.caption.copy(letterSpacing = 0.04.em), color = if (active) K.Base else K.Ink2, maxLines = 1)
    }
}

/** Süre kontrolü kartı (mono numara + etiket) */
@Composable
fun TcTile(num: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bc by animateColorAsState(if (selected) K.Ink else K.LineSubtle, tween(Motion.TAP), label = "tc")
    Column(
        modifier.press(scale = 0.97f, onClick = onClick).height(54.dp).clip(RSmall).background(if (selected) K.Elevated else K.Base)
            .border(1.dp, bc, RSmall).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(num, style = KType.mono.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = K.Ink)
        Text(label, style = KType.desc.copy(fontSize = 11.sp), color = K.Ink2)
    }
}

/** Kılçizgiyle ayrılmış liste satırı (lobi maç satırı) */
@Composable
fun GameRow(
    title: String,
    sub: String,
    badge: String? = null,
    badgeColor: Color = K.LineStrong,
    badgeText: Color = K.Ink,
    modifier: Modifier = Modifier,
    bg: Color = K.Base,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.press(scale = 0.99f, onClick = onClick)
    Row(
        m.background(bg).hairlineTop().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = KType.body.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp), color = K.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = KType.desc, color = K.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Text(badge.upTr(), style = KType.label.copy(letterSpacing = 0.06.em), color = badgeText, maxLines = 1,
                modifier = Modifier.clip(RSmall).border(1.dp, badgeColor, RSmall).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
    }
}

/** Monogram karesi (oyuncu işareti) */
@Composable
fun Monogram(text: String, white: Boolean = false, size: Dp = 28.dp, filled: Boolean = white) {
    Box(
        Modifier.size(size).background(if (filled) K.Ink else K.Elevated).border(1.dp, if (filled) K.Ink else K.LineStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.take(2).upTr(), style = KType.caption.copy(fontWeight = FontWeight.Bold, fontSize = if (size < 30.dp) 12.sp else 13.sp), color = if (filled) K.Base else K.Ink)
    }
}

/** Oyuncu şeridi: monogram, isim, alt satır, saat (çalışırken tebeşir) */
@Composable
fun PlayerStrip(
    mark: String,
    markWhite: Boolean,
    name: String,
    sub: String,
    clock: String?,
    running: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val bg by animateColorAsState(if (active) K.Surface else K.Base, tween(Motion.STATE), label = "ps")
    val cbg by animateColorAsState(if (running) K.Ink else K.Base, tween(Motion.STATE), label = "clk")
    val cfg by animateColorAsState(if (running) K.Base else K.Ink2, tween(Motion.STATE), label = "clkf")
    Row(
        modifier.fillMaxWidth().background(bg).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(mark, markWhite)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = KType.bodyCompact, color = K.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sub, style = KType.desc.copy(fontSize = 11.sp), color = K.Ink2, maxLines = 1)
                extra?.invoke()
            }
        }
        if (clock != null) {
            Text(
                clock, style = KType.clock, color = cfg, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(min = 70.dp).background(cbg).border(1.dp, if (running) K.Ink else K.LineSubtle).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Koç şeridi: başlık + açıklama + isteğe bağlı Saville eylemi */
@Composable
fun CoachStrip(title: String, desc: String, modifier: Modifier = Modifier, code: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().background(K.Surface).hairlineBottom().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (code != null) { Stamp(code); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = KType.bodyCompact, color = K.Ink)
            if (desc.isNotEmpty()) Text(desc, style = KType.desc.copy(lineHeight = 15.6.sp), color = K.Ink2)
        }
        if (action != null) { Spacer(Modifier.width(8.dp)); action() }
    }
}

/** Hamle damgası (kare köşeli, mono): sınıfın sinyal rengiyle */
@Composable
fun Stamp(cls: String, size: Dp = 22.dp) {
    val info = dev.chesscrab.app.ui.CLASS_INFO[cls] ?: return
    Box(Modifier.defaultMinSize(size, size).clip(RStamp).background(info.color).border(1.dp, K.Base, RStamp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(info.symbol, style = KType.mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = info.onColor)
    }
}

/** Sinyal hücreleri (seri / ilerleme): yapılan = tebeşir, etkin = kehribar büyür */
@Composable
fun SignalCells(total: Int, done: Int, active: Int? = null, modifier: Modifier = Modifier, cell: Dp = 12.dp, activeColor: Color = K.Yellow, doneColor: Color = K.Ink) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until total) {
            val isDone = i < done
            val isActive = i == active
            val sc by animateFloatAsState(if (isActive && !Motion.reduced) 1.18f else 1f, tween(240, easing = Motion.Expo), label = "sig")
            val c by animateColorAsState(if (isActive) activeColor else if (isDone) doneColor else K.CellOff, tween(240, easing = Motion.Expo), label = "sigc")
            Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(cell).background(c).border(1.dp, if (isActive || isDone) c else K.CellLine))
        }
    }
}

/** Sinyal okuma bandı: başlık + meta + sağda hücreler */
@Composable
fun SignalReadout(title: String, meta: String, modifier: Modifier = Modifier, right: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KType.bodyCompact, color = K.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = KType.desc.copy(fontSize = 11.sp), color = K.Ink2, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        right()
    }
}

/** İstatistik satırı: büyük harf etiket — mono değer, altında kılçizgi */
@Composable
fun StatLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = K.Ink) {
    Row(modifier.fillMaxWidth().hairlineBottom().padding(bottom = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.upTr(), style = KType.desc.copy(fontSize = 11.sp, letterSpacing = 0.05.em), color = K.Ink2, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = KType.mono.copy(fontWeight = FontWeight.Bold), color = valueColor, maxLines = 1)
    }
}

/** Yatay momentum izi: tebeşir dolgu, ortada kızıl iğne */
@Composable
fun EvalTrack(cp: Int, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val target = (winPercent(cp) / 100f).coerceIn(0.04f, 0.96f)
    val w by animateFloatAsState(target, tween(if (Motion.reduced) 120 else 480, easing = Motion.Expo), label = "et")
    Box(modifier.fillMaxWidth().height(height).background(K.Track).border(1.dp, K.LineSubtle)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(w).background(K.Ink))
        Box(Modifier.fillMaxHeight().width(1.5.dp).align(Alignment.Center).background(K.Red))
    }
}

/** Basit ilerleme izi (0..1) */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, color: Color = K.Ink, height: Dp = 5.dp) {
    val w by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(if (Motion.reduced) 120 else 480, easing = Motion.Expo), label = "pt")
    Box(modifier.fillMaxWidth().height(height).background(K.Track).border(1.dp, K.LineSubtle)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(w).background(color))
    }
}

/**
 * Doğrusal pulsar: beş üst üste kılçizgi sırt; etkin hamle çevresinde Gauss zarfı
 * ve kızıl oynatma çizgisi. `values` piyon biriminde (beyaz açısından).
 */
@Composable
fun LinearPulsar(values: List<Float>, active: Int, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val act by animateFloatAsState(active.toFloat(), tween(if (Motion.reduced) 0 else 420, easing = Motion.Expo), label = "lp")
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val k = h / 104f
        val n = values.size
        val ridges = 5; val samples = 56
        val center = act / (n - 1).coerceAtLeast(1)
        for (r in 0 until ridges) {
            val alpha = if (r == ridges - 1) 0.96f else 0.22f + r * 0.14f
            val lw = if (r == ridges - 1) 2.0f else 1.1f
            val p = Path()
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val x = t * w
                val vPos = t * (n - 1)
                val lo = vPos.toInt().coerceIn(0, n - 1); val hi = (lo + 1).coerceAtMost(n - 1)
                val frac = vPos - lo
                val v = values[lo] * (1 - frac) + values[hi] * frac
                val env = exp(-((t - center) * 4.5f).let { it * it })
                val spike = abs(sin(i * 1.35f + r * 1.7f)) * (6 + abs(v) * 5.2f) * (0.35f + env * 0.95f)
                val y = maxOf(4f, (18 + r * 17) - spike - v.coerceIn(-2f, 4f) * 2.2f) * k
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, K.Ink.copy(alpha = alpha), style = Stroke(lw.dp.toPx() * 0.75f))
        }
        val ax = center * w
        drawLine(K.Red, Offset(ax, 2f), Offset(ax, h - 2f), 1.5.dp.toPx())
    }
}

/**
 * Kutupsal pulsar: 4 halka + 12 kollu radar; değerlendirme çokgeni ve kızıl etkin kol.
 * Analiz ekranında hafifçe "nefes alır" (azaltılmış harekette durur).
 */
@Composable
fun PolarPulsar(values: List<Float>, active: Int, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val phase = if (Motion.loops) {
        rememberInfiniteTransition(label = "polar").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "ph").value
    } else 0f
    val act by animateFloatAsState(active.toFloat(), tween(if (Motion.reduced) 0 else 420, easing = Motion.Expo), label = "pa")
    Canvas(modifier.size(size)) {
        val cx = this.size.width / 2; val cy = this.size.height / 2
        val maxR = this.size.width * 0.44f
        val ring = K.LineSubtle
        for (f in listOf(0.24f, 0.48f, 0.72f, 0.96f)) drawCircle(ring, maxR * f, Offset(cx, cy), style = Stroke(1f))
        for (i in 0 until 12) {
            val a = (2 * PI * i / 12).toFloat()
            drawLine(ring, Offset(cx + cos(a) * maxR * 0.24f, cy + sin(a) * maxR * 0.24f), Offset(cx + cos(a) * maxR, cy + sin(a) * maxR), 1f)
        }
        if (values.isEmpty()) return@Canvas
        val total = values.size
        val p = Path()
        for (i in 0..total) {
            val idx = i % total
            val a = (2 * PI * idx / total - PI / 2).toFloat()
            val v = values[idx].coerceIn(-4.5f, 4.5f)
            val pulse = sin(phase + idx * 0.7f) * 2.5f * (this.size.width / 224f) * 2.5f
            val r = maxR * (0.56f + v * 0.08f) + pulse
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, K.Ink.copy(alpha = 0.07f))
        drawPath(p, K.Ink, style = Stroke(1.5.dp.toPx()))
        val ai = act.toInt().coerceIn(0, total - 1)
        val aa = (2 * PI * act / total - PI / 2).toFloat()
        val ar = maxR * (0.56f + values[ai].coerceIn(-4.5f, 4.5f) * 0.08f)
        val tip = Offset(cx + cos(aa) * ar, cy + sin(aa) * ar)
        drawLine(K.Red, Offset(cx, cy), tip, 1.5.dp.toPx())
        drawCircle(K.Red, 3.dp.toPx(), tip)
    }
}

/** Pulsar modülü: başlık + okuma, momentum izi, doğrusal pulsar, çözücü şerit */
@Composable
fun PulsarModule(
    cp: Int,
    values: List<Float>,
    active: Int,
    decodeActive: String?,
    modifier: Modifier = Modifier,
    header: String = "MOMENTUM PULSAR",
    onDecode: ((String) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(K.Base).hairlineBottom().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(header, style = KType.label.copy(fontWeight = FontWeight.SemiBold), color = K.Ink2, modifier = Modifier.weight(1f))
            Text(evalReadout(cp), style = KType.mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em), color = K.Ink)
        }
        EvalTrack(cp)
        LinearPulsar(values, active)
        if (onDecode != null) DecodeBar(decodeActive, onTap = onDecode)
    }
}

/** Basit çizgi trend grafiği (İlerleme): kılçizgi ızgara, tebeşir çizgi, kare noktalar */
@Composable
fun TrendChart(values: List<Float>, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val intro = remember(values) { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(values) { intro.animateTo(1f, tween(700, easing = Motion.Expo)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val lo = (values.min() - 6f).coerceAtLeast(0f); val hi = (values.max() + 4f).coerceAtMost(100f)
        val span = (hi - lo).coerceAtLeast(10f)
        val n = (values.size - 1).coerceAtLeast(1)
        for (k in 0..3) { val yy = pad + (h - 2 * pad) * k / 3f; drawLine(K.LineSubtle, Offset(0f, yy), Offset(w, yy), 1f) }
        val avg = values.average().toFloat()
        val ay = pad + (h - 2 * pad) * (1 - (avg - lo) / span)
        drawLine(K.Industrial, Offset(0f, ay), Offset(w, ay), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        val pts = values.mapIndexed { i, v -> Offset(if (values.size == 1) w / 2 else pad + (w - 2 * pad) * i / n, pad + (h - 2 * pad) * (1 - (v - lo) / span)) }
        val shown = (pts.size * intro.value).toInt().coerceIn(1, pts.size)
        val p = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until shown) lineTo(pts[i].x, pts[i].y) }
        drawPath(p, K.Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Square))
        for (i in 0 until shown) { val q = pts[i]; drawRect(K.Base, q - Offset(4f, 4f) * density, Size(8f, 8f) * density); drawRect(K.Ink, q - Offset(2.5f, 2.5f) * density, Size(5f, 5f) * density) }
        val last = pts[shown - 1]
        drawLine(K.Red, Offset(last.x, 0f), Offset(last.x, h), 1.5.dp.toPx())
    }
}

/** Anahtar (Switch): açıkken kobalt iz */
@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = K.Blue, checkedThumbColor = K.Ink, checkedBorderColor = K.Blue,
            uncheckedTrackColor = K.Base, uncheckedThumbColor = K.Ink3, uncheckedBorderColor = K.LineStrong,
        ),
    )
}

/** Anahtarlı ayar satırı */
@Composable
fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().hairlineTop().background(K.Base).heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KType.body.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp), color = K.Ink)
            Text(sub, style = KType.desc, color = K.Ink2)
        }
        KSwitch(value, onChange)
    }
}

/** Keskin metin alanı */
@Composable
fun KTextField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, visual: VisualTransformation = VisualTransformation.None, mono: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        label = { Text(label, style = KType.caption) },
        textStyle = if (mono) KType.mono else KType.body,
        shape = RSmall,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = visual,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = K.Blue, unfocusedBorderColor = K.LineStrong, focusedLabelColor = K.Blue, unfocusedLabelColor = K.Ink3,
            cursorColor = K.Blue, focusedContainerColor = K.Base, unfocusedContainerColor = K.Base, focusedTextColor = K.Ink, unfocusedTextColor = K.Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Gövde metni */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = K.Ink2, style: TextStyle = KType.desc) {
    Text(text, style = style, color = color, modifier = modifier)
}

/** Etiket (küçük, büyük harf) */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = K.Ink2, border: Color = K.LineSubtle) {
    Text(text.upTr(), style = KType.caption.copy(fontSize = 11.sp, letterSpacing = 0.05.em), color = color, maxLines = 1,
        modifier = modifier.border(1.dp, border).padding(horizontal = 6.dp, vertical = 3.dp))
}

/** Arama/eşleşme taraması gibi kayan kobalt sinyal (süren oyun göstergesi) */
@Composable
fun ScanPulse(modifier: Modifier = Modifier) {
    val t = if (Motion.loops) rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "s").value else 0.5f
    Canvas(modifier.size(38.dp, 6.dp).border(1.dp, K.Blue)) {
        val w = size.width
        val c = w * (1.5f - 2f * t)
        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, K.Blue, Color.Transparent), startX = c - w / 2, endX = c + w / 2))
    }
}

/** Ders/oyun listelerinde Archivo başlık + açıklama bloğu */
@Composable
fun Band(modifier: Modifier = Modifier, bg: Color = K.Base, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(bg).hairlineBottom().padding(horizontal = 14.dp, vertical = 12.dp), content = content)
}

/** Okuma oranında kısa yazı tipi ailesi seçici (yardımcı) */
val ArchivoBold = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold)
val MonoBold = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold)
