package dev.chesscrab.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.R

/**
 * ChessCrab "Gelgit" kimliği: derin deniz mürekkebi zemin, mercan vurgu,
 * kum rengi tahta. Başlıklar serif (DM Serif Display), gövde Manrope,
 * sayılar/notasyon JetBrains Mono.
 */
data class Palette(
    val id: String, val name: String, val dark: Boolean,
    val bg: Color, val surface: Color, val card: Color, val cardHi: Color, val line: Color,
    val coral: Color, val coralDeep: Color, val sea: Color, val seaDeep: Color,
    val sand: Color, val ink: Color, val gold: Color, val text: Color, val muted: Color, val faint: Color,
    val chartBg: Color,
)

val PALETTES = listOf(
    Palette(
        "gelgit", "Gelgit", true,
        Color(0xFF0B161D), Color(0xFF0F1E27), Color(0xFF152833), Color(0xFF1D3442), Color(0x1FFFFFFF),
        Color(0xFFFF7A59), Color(0xFFE4573A), Color(0xFF54D1BE), Color(0xFF1F7F78),
        Color(0xFFF4E8CF), Color(0xFF0B161D), Color(0xFFF2C46D), Color(0xFFEEF3F1), Color(0xFF92A8B0), Color(0xFF5E7782),
        Color(0xFF07101A),
    ),
    Palette(
        "gece", "Gece", true,
        Color(0xFF000000), Color(0xFF07090B), Color(0xFF0F1316), Color(0xFF182025), Color(0x1CFFFFFF),
        Color(0xFF54D1BE), Color(0xFF2FA898), Color(0xFFFF7A59), Color(0xFFB04A33),
        Color(0xFFEDE6D6), Color(0xFF001312), Color(0xFFF2C46D), Color(0xFFEEF2F1), Color(0xFF8C9AA0), Color(0xFF56636A),
        Color(0xFF05080A),
    ),
    Palette(
        "resif", "Resif", true,
        Color(0xFF17111F), Color(0xFF1D1527), Color(0xFF261B33), Color(0xFF312340), Color(0x1FFFFFFF),
        Color(0xFFFF6F91), Color(0xFFE04E73), Color(0xFF7FD8C9), Color(0xFF3B8F84),
        Color(0xFFF6E7D7), Color(0xFF1A0F17), Color(0xFFFFC26B), Color(0xFFF4EEF6), Color(0xFFA99BB5), Color(0xFF6E6079),
        Color(0xFF100B16),
    ),
    Palette(
        "kumsal", "Kumsal", false,
        Color(0xFFF5EEE1), Color(0xFFEFE6D5), Color(0xFFFFFBF3), Color(0xFFF1E7D6), Color(0x220B161D),
        Color(0xFFE8623F), Color(0xFFC94B2B), Color(0xFF1C9787), Color(0xFF136A60),
        Color(0xFF5A3A24), Color(0xFF1B1410), Color(0xFFB8862B), Color(0xFF16242C), Color(0xFF5F727A), Color(0xFF97A3A6),
        Color(0xFF16242C),
    ),
)

fun palette(id: String): Palette = PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]

/**
 * ChessCrab renkleri. Değerler seçili paletten okunur; palet bir
 * Compose durumu olduğundan tema değişince bütün ekranlar anında yenilenir.
 */
object Crabs {
    private val p: Palette get() = palette(Prefs.appTheme)
    val Bg get() = p.bg
    val Surface get() = p.surface
    val Card get() = p.card
    val CardHi get() = p.cardHi
    val Line get() = p.line
    val Coral get() = p.coral
    val CoralDeep get() = p.coralDeep
    val Sea get() = p.sea
    val SeaDeep get() = p.seaDeep
    /** Vurgulu metin (koyu temalarda kum, açık temada koyu kahve) */
    val Sand get() = p.sand
    /** Dolu vurgu üstündeki metin */
    val Ink get() = p.ink
    val Gold get() = p.gold
    val Text get() = p.text
    val Muted get() = p.muted
    val Faint get() = p.faint
    val ChartBg get() = p.chartBg
    val Dark get() = p.dark
    /** Grafiklerde/saatte "beyaz" ve "siyah" taraf: temadan bağımsız */
    val Ivory = Color(0xFFF4E8CF)
    val Abyss = Color(0xFF05090C)
}

val Display = FontFamily(
    Font(R.font.dm_serif, FontWeight.Normal),
    Font(R.font.dm_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val Body = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

private fun schemeOf(p: Palette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.coral, onPrimary = p.ink, secondary = p.sea, onSecondary = p.ink,
    background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
    surfaceVariant = p.card, onSurfaceVariant = p.muted, surfaceContainer = p.surface,
    surfaceContainerHigh = p.card, surfaceContainerHighest = p.cardHi,
    outline = p.faint, outlineVariant = p.line, error = Color(0xFFFF5A6E),
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
    val p = palette(Prefs.appTheme)
    MaterialTheme(colorScheme = schemeOf(p), typography = Type, content = content)
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

/** Ekranları anında güncelleyen tercihler (Compose durumu; değişince her şey yeniden çizilir) */
object Prefs {
    var boardTheme by mutableStateOf("sand")
    var appTheme by mutableStateOf("gelgit")

    /** Uygulama açılışında kayıtlı tercihleri yükler */
    fun load() {
        runCatching {
            boardTheme = Crab.setting("board_theme", "sand")
            appTheme = Crab.setting("app_theme", "gelgit")
        }
    }

    fun setBoard(id: String) { boardTheme = id; runCatching { Crab.setSetting("board_theme", id) } }
    fun setApp(id: String) { appTheme = id; runCatching { Crab.setSetting("app_theme", id) } }
}

/** Animasyon ayarları. Testlerde sonsuz (ortam) animasyonlar kapatılır. */
object Motion {
    var ambient = true
}
