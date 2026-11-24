package dev.chesscrab.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chesscrab.app.Crab
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Crabs {
    val Bg = Color(0xFF302E2B)
    val Surface = Color(0xFF262522)
    val Card = Color(0xFF3A3835)
    val CardHi = Color(0xFF45423E)
    val Green = Color(0xFF81B64C)
    val GreenDark = Color(0xFF5D8A35)
    val Orange = Color(0xFFE07B39)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFB0ADA8)
    val Faint = Color(0xFF7D7A75)
    val Gold = Color(0xFFF5C542)
}

private val Scheme = darkColorScheme(
    primary = Crabs.Green,
    onPrimary = Color.White,
    secondary = Crabs.Orange,
    onSecondary = Color.White,
    background = Crabs.Bg,
    onBackground = Crabs.Text,
    surface = Crabs.Surface,
    onSurface = Crabs.Text,
    surfaceVariant = Crabs.Card,
    onSurfaceVariant = Crabs.Muted,
    surfaceContainer = Crabs.Surface,
    surfaceContainerHigh = Crabs.Card,
    surfaceContainerHighest = Crabs.CardHi,
    outline = Color(0xFF4F4C48),
    error = Color(0xFFFA412D),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun CrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}

/** Hamle sınıfı: etiket, renk, rozet simgesi */
data class ClassInfo(val label: String, val color: Color, val symbol: String)

val CLASS_INFO: Map<String, ClassInfo> = mapOf(
    "brilliant" to ClassInfo("Parlak", Color(0xFF26C2A3), "!!"),
    "great" to ClassInfo("Harika", Color(0xFF749BBF), "!"),
    "best" to ClassInfo("En iyi", Color(0xFF81B64C), "★"),
    "excellent" to ClassInfo("Mükemmel", Color(0xFF96BC4B), "✓"),
    "good" to ClassInfo("İyi", Color(0xFF95B776), "✓"),
    "book" to ClassInfo("Kitap", Color(0xFFA88865), "≡"),
    "inaccuracy" to ClassInfo("Belirsiz", Color(0xFFF7C631), "?!"),
    "mistake" to ClassInfo("Hata", Color(0xFFFFA459), "?"),
    "miss" to ClassInfo("Kaçırılan", Color(0xFFFF7769), "✕"),
    "blunder" to ClassInfo("Büyük hata", Color(0xFFFA412D), "??"),
    "forced" to ClassInfo("Zorunlu", Color(0xFF97AF8B), "→"),
)

/** İnceleme tablosundaki sıra (chess.com düzeni) */
val CLASS_ORDER = listOf("brilliant", "great", "best", "excellent", "good", "book", "inaccuracy", "mistake", "miss", "blunder")

data class BoardTheme(val id: String, val name: String, val light: Color, val dark: Color)

val BOARD_THEMES = listOf(
    BoardTheme("green", "Yeşil", Color(0xFFEBECD0), Color(0xFF739552)),
    BoardTheme("wood", "Ahşap", Color(0xFFF0D9B5), Color(0xFFB58863)),
    BoardTheme("blue", "Mavi", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    BoardTheme("night", "Gece", Color(0xFFC8C6D8), Color(0xFF6E6A99)),
)

fun boardTheme(id: String): BoardTheme = BOARD_THEMES.firstOrNull { it.id == id } ?: BOARD_THEMES[0]

/** Ekranları anında güncelleyen tercihler */
object Prefs {
    var boardTheme by mutableStateOf(runCatching { Crab.setting("board_theme", "green") }.getOrDefault("green"))
}
