package dev.chesscrab.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.R

/**
 * ChessCrab "Gelgit" kimliği: derin deniz mürekkebi zemin, mercan vurgu,
 * kum rengi tahta. Başlıklar serif (DM Serif Display), gövde Manrope,
 * sayılar/notasyon JetBrains Mono.
 */
object Crabs {
    val Bg = Color(0xFF0B161D)
    val Surface = Color(0xFF0F1E27)
    val Card = Color(0xFF152833)
    val CardHi = Color(0xFF1D3442)
    val Line = Color(0x1FFFFFFF)
    val Coral = Color(0xFFFF7A59)
    val CoralDeep = Color(0xFFE4573A)
    val Sea = Color(0xFF54D1BE)
    val SeaDeep = Color(0xFF1F7F78)
    val Sand = Color(0xFFF4E8CF)
    val Ink = Color(0xFF0B161D)
    val Gold = Color(0xFFF2C46D)
    val Text = Color(0xFFEEF3F1)
    val Muted = Color(0xFF92A8B0)
    val Faint = Color(0xFF5E7782)
}

private fun vf(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Display = FontFamily(
    Font(R.font.dm_serif, FontWeight.Normal),
    Font(R.font.dm_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val Body = FontFamily(listOf(400, 500, 600, 700, 800).map { vf(R.font.manrope, it) })
val Mono = FontFamily(listOf(400, 500, 700).map { vf(R.font.jetbrains_mono, it) })

private val Scheme = darkColorScheme(
    primary = Crabs.Coral,
    onPrimary = Crabs.Ink,
    secondary = Crabs.Sea,
    onSecondary = Crabs.Ink,
    background = Crabs.Bg,
    onBackground = Crabs.Text,
    surface = Crabs.Surface,
    onSurface = Crabs.Text,
    surfaceVariant = Crabs.Card,
    onSurfaceVariant = Crabs.Muted,
    surfaceContainer = Crabs.Surface,
    surfaceContainerHigh = Crabs.Card,
    surfaceContainerHighest = Crabs.CardHi,
    outline = Color(0xFF2C4757),
    error = Color(0xFFFF5A6E),
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontSize = 36.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 25.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 1.4.sp),
)

@Composable
fun CrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}

/** Hamle sınıfı: etiket, renk, rozet simgesi (standart notasyon işaretleri) */
data class ClassInfo(val label: String, val color: Color, val symbol: String)

val CLASS_INFO: Map<String, ClassInfo> = mapOf(
    "brilliant" to ClassInfo("Parlak", Color(0xFF3EE6D4), "!!"),
    "great" to ClassInfo("Harika", Color(0xFF7FB2FF), "!"),
    "best" to ClassInfo("En iyi", Color(0xFF7BD88F), "★"),
    "excellent" to ClassInfo("Mükemmel", Color(0xFFA6DDB0), "✓"),
    "good" to ClassInfo("İyi", Color(0xFFB9CBBE), "✓"),
    "book" to ClassInfo("Kitap", Color(0xFFD2B48C), "≡"),
    "inaccuracy" to ClassInfo("Belirsiz", Color(0xFFF2C46D), "?!"),
    "mistake" to ClassInfo("Hata", Color(0xFFFF9A5C), "?"),
    "miss" to ClassInfo("Kaçırılan", Color(0xFFFF7A9C), "✕"),
    "blunder" to ClassInfo("Büyük hata", Color(0xFFFF4F64), "??"),
    "forced" to ClassInfo("Zorunlu", Color(0xFF9DB3A8), "→"),
)

/** İnceleme tablosundaki sıra */
val CLASS_ORDER = listOf("brilliant", "great", "best", "excellent", "good", "book", "inaccuracy", "mistake", "miss", "blunder")

data class BoardTheme(val id: String, val name: String, val light: Color, val dark: Color)

val BOARD_THEMES = listOf(
    BoardTheme("sand", "Kum", Color(0xFFEDE3CB), Color(0xFF7A9E9A)),
    BoardTheme("coral", "Mercan", Color(0xFFF3E2D3), Color(0xFFC98A6E)),
    BoardTheme("deep", "Derin", Color(0xFFC9D5DE), Color(0xFF4F6E87)),
    BoardTheme("wood", "Ahşap", Color(0xFFF0D9B5), Color(0xFFB58863)),
)

fun boardTheme(id: String): BoardTheme = BOARD_THEMES.firstOrNull { it.id == id } ?: BOARD_THEMES[0]

/** Ekranları anında güncelleyen tercihler */
object Prefs {
    var boardTheme by mutableStateOf(runCatching { Crab.setting("board_theme", "sand") }.getOrDefault("sand"))
}
