package dev.chesscrab.app.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.R

/**
 * Kare — Saville Pulsar Index.
 *
 * Saf karbon mat obsidyen zemin (soğuk mavi döküm yok), 1px yatay kılçizgiler
 * ve beş renkli Saville FAC sinyal kodu. Her sinyal rengi tek bir satranç
 * anlamı taşır:
 *  - tebeşir beyazı = parlak / momentum (!!)
 *  - kobalt        = yasal / en iyi (!)
 *  - kehribar      = ipucu / belirsiz / bulmaca ilerlemesi (?!)
 *  - kızıl         = tehdit / şah / büyük hata (??)
 */
object K {
    // karbon yüzeyler + Android Material 3 tonal adımları
    val Stage = Color(0xFF060607)
    val Base = Color(0xFF0B0B0C)
    val Shell = Color(0xFF0E0E10)
    val Surface = Color(0xFF111113)
    val Chrome = Color(0xFF141417)
    val Action = Color(0xFF17171A)
    val Elevated = Color(0xFF19191C)
    val LineSubtle = Color(0xFF242428)
    val LineStrong = Color(0xFF3D3D42)
    val Ink = Color(0xFFEAEAE4)
    val Ink2 = Color(0xFFA5A5A0)
    val Ink3 = Color(0xFF84847E)

    // Saville FAC 5 renk kodu
    val Industrial = Color(0xFF767672)
    val Blue = Color(0xFF2563EB)
    val BlueSoft = Color(0x292563EB)
    val BlueNav = Color(0x3D2563EB)
    val Yellow = Color(0xFFEAB308)
    val YellowSoft = Color(0x29EAB308)
    val Red = Color(0xFFDC2626)
    val RedSoft = Color(0x33DC2626)

    // telemetri izi ve sinyal hücreleri
    val Track = Color(0xFF1C1C20)
    val CellOff = Color(0xFF1D1D21)
    val CellLine = Color(0xFF303036)
}

val Archivo = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal),
    Font(R.font.archivo_500, FontWeight.Medium),
    Font(R.font.archivo_600, FontWeight.SemiBold),
    Font(R.font.archivo_700, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

/** Tipografi ölçeği (DESIGN.md) */
object KType {
    val display = TextStyle(fontFamily = Archivo, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, letterSpacing = 0.08.em)
    val headline = TextStyle(fontFamily = Archivo, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, letterSpacing = 0.08.em)
    val title = TextStyle(fontFamily = Archivo, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, letterSpacing = 0.1.em)
    val clock = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = Archivo, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val bodyCompact = TextStyle(fontFamily = Archivo, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
    val caption = TextStyle(fontFamily = Archivo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    val desc = TextStyle(fontFamily = Archivo, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = Archivo, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp, letterSpacing = 0.08.em)
    val code = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp, letterSpacing = 0.08.em)
    val mono = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
}

private val Scheme = darkColorScheme(
    primary = K.Blue, onPrimary = K.Ink, secondary = K.Yellow, onSecondary = K.Base,
    background = K.Base, onBackground = K.Ink, surface = K.Surface, onSurface = K.Ink,
    surfaceVariant = K.Action, onSurfaceVariant = K.Ink2, surfaceContainer = K.Chrome,
    surfaceContainerHigh = K.Elevated, surfaceContainerHighest = K.Elevated,
    outline = K.LineStrong, outlineVariant = K.LineSubtle, error = K.Red,
)

private val Type = Typography(
    headlineMedium = KType.headline, headlineSmall = KType.headline, titleLarge = KType.title,
    titleMedium = KType.bodyCompact, bodyLarge = KType.body, bodyMedium = KType.body, bodySmall = KType.desc,
    labelLarge = KType.label, labelMedium = KType.caption, labelSmall = KType.code,
)

@Composable
fun CrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}

/** Hareket dili: ease-out-expo, basış eğrisi ve süreler */
object Motion {
    val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val Press = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val TAP = 120
    const val STATE = 220
    const val VIEW = 260

    /** Testlerde sonsuz (ortam) animasyonlar kapatılır */
    var ambient = true

    /** Sistem "animasyonları kaldır" ayarı (uygulama açılışında okunur) */
    var systemReduced = false

    /** Azaltılmış hareket: ayar ya da sistem tercihi */
    val reduced: Boolean get() = Prefs.reducedMotion || systemReduced

    /** Sonsuz döngülü süsler (şah sonarı, pulsar nefesi) çalışsın mı */
    val loops: Boolean get() = ambient && !reduced
}

/**
 * Hamle kalitesi. `code` dört sinyal kodundan biridir (Semantic Hue Lock):
 * brilliant (beyaz), best (kobalt), inaccuracy (kehribar), blunder (kızıl);
 * daha nötr sınıflar endüstriyel griyle gösterilir.
 */
data class ClassInfo(val label: String, val symbol: String, val code: String) {
    val color: Color get() = codeColor(code)
    val onColor: Color get() = if (code == "best" || code == "blunder") K.Ink else K.Base
}

fun codeColor(code: String): Color = when (code) {
    "brilliant" -> K.Ink
    "best" -> K.Blue
    "inaccuracy" -> K.Yellow
    "blunder" -> K.Red
    else -> K.Industrial
}

val CLASS_INFO: Map<String, ClassInfo> = mapOf(
    "brilliant" to ClassInfo("Parlak", "!!", "brilliant"),
    "great" to ClassInfo("Harika", "!", "brilliant"),
    "best" to ClassInfo("En iyi", "!", "best"),
    "excellent" to ClassInfo("Mükemmel", "+", "best"),
    "good" to ClassInfo("İyi", "+", "industrial"),
    "book" to ClassInfo("Kitap", "=", "industrial"),
    "inaccuracy" to ClassInfo("Belirsiz", "?!", "inaccuracy"),
    "mistake" to ClassInfo("Hata", "?", "inaccuracy"),
    "miss" to ClassInfo("Kaçırılan", "×", "blunder"),
    "blunder" to ClassInfo("Büyük hata", "??", "blunder"),
    "forced" to ClassInfo("Zorunlu", "→", "industrial"),
)

/** Bir sınıfın dört koddan hangisine düştüğü (çözücü şerit için) */
fun decodeOf(cls: String?): String? = CLASS_INFO[cls]?.code?.takeIf { it != "industrial" } ?: if (cls != null) "best" else null

/** İnceleme tablosundaki sıra */
val CLASS_ORDER = listOf("brilliant", "great", "best", "excellent", "good", "book", "inaccuracy", "mistake", "miss", "blunder")

/** Dört sinyal kodu: (kod, kısa etiket) */
val DECODE = listOf(
    "brilliant" to "!! PARLAK",
    "best" to "! İYİ",
    "inaccuracy" to "?! UYARI",
    "blunder" to "?? HATA",
)

val CODE_EXPLAIN = mapOf(
    "brilliant" to ("!! Parlak hamle kodu" to "Rakibin savunmasını kıran fedakâr ya da çift tehditli hamle."),
    "best" to ("! En iyi hamle kodu" to "Motorun seçtiği, kontrolü ve gelişimi en yükseğe taşıyan hamle."),
    "inaccuracy" to ("?! Şüpheli hamle kodu" to "Avantajı azaltan ama oyunu hemen kaybettirmeyen adım."),
    "blunder" to ("?? Büyük hata kodu" to "Taş kaybına ya da zorunlu mata yol açan kritik boşluk."),
)

data class BoardTheme(val id: String, val name: String, val light: Color, val dark: Color) {
    /** Son hamle kareleri: karbonda sis mavisi / lacivert, diğerlerinde ona doğru karışım */
    val lastLight: Color get() = if (id == "carbon") Color(0xFFC9D8EF) else lerp(light, Color(0xFFC9D8EF), 0.6f)
    val lastDark: Color get() = if (id == "carbon") Color(0xFF253D63) else lerp(dark, Color(0xFF253D63), 0.6f)
    val coordOnLight: Color get() = if (id == "carbon") Color(0xFF26262A) else dark
    val coordOnDark: Color get() = if (id == "carbon") Color(0xFFD8D8D2) else light
}

val BOARD_THEMES = listOf(
    BoardTheme("carbon", "Karbon", Color(0xFFE4E4DD), Color(0xFF323236)),
    BoardTheme("ash", "Kül", Color(0xFFD9D9D3), Color(0xFF6E6E6A)),
    BoardTheme("sand", "Kum", Color(0xFFEDE3CB), Color(0xFF7A9E9A)),
    BoardTheme("deep", "Derin", Color(0xFFC9D5DE), Color(0xFF4F6E87)),
    BoardTheme("wood", "Ahşap", Color(0xFFF0D9B5), Color(0xFFB58863)),
)

fun boardTheme(id: String): BoardTheme = BOARD_THEMES.firstOrNull { it.id == id } ?: BOARD_THEMES[0]

/** Ekranları anında güncelleyen tercihler (Compose durumu) */
object Prefs {
    var boardTheme by mutableStateOf("carbon")
    var pieceSet by mutableStateOf("kare")
    var reducedMotion by mutableStateOf(false)

    fun load(ctx: android.content.Context? = null) {
        runCatching {
            boardTheme = Crab.setting("board_theme", "carbon")
            pieceSet = Crab.setting("piece_set", "kare")
            reducedMotion = Crab.setting("reduced_motion", "0") == "1"
        }
        if (ctx != null) runCatching {
            Motion.systemReduced = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    }

    fun setBoard(id: String) { boardTheme = id; runCatching { Crab.setSetting("board_theme", id) } }
    fun setPieces(id: String) { pieceSet = id; runCatching { Crab.setSetting("piece_set", id) } }
    fun setReduced(on: Boolean) { reducedMotion = on; runCatching { Crab.setSetting("reduced_motion", if (on) "1" else "0") } }
}
