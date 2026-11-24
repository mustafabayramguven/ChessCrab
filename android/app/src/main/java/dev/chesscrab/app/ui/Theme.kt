package dev.chesscrab.app.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Fallback = darkColorScheme(
    primary = Color(0xFFE07B39),
    onPrimary = Color(0xFF1A1310),
    secondary = Color(0xFFF0A35E),
    background = Color(0xFF161512),
    surface = Color(0xFF262421),
    surfaceVariant = Color(0xFF302E2B),
    onBackground = Color(0xFFE8E6E3),
    onSurface = Color(0xFFE8E6E3),
)

/** Material You: Android 12+ duvar kağıdı renkleri, değilse ChessCrab turuncusu. */
@Composable
fun CrabTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(LocalContext.current) else Fallback
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Hamle sınıfı renkleri ve etiketleri (analiz) */
val CLASS_INFO: Map<String, Triple<String, Color, String>> = mapOf(
    "book" to Triple("Kitap", Color(0xFFA88865), ""),
    "brilliant" to Triple("Parlak", Color(0xFF1BACA6), "!!"),
    "great" to Triple("Harika", Color(0xFF5C8BB0), "!"),
    "best" to Triple("En iyi", Color(0xFF98BC49), "★"),
    "excellent" to Triple("Mükemmel", Color(0xFF98BC49), ""),
    "good" to Triple("İyi", Color(0xFF97AF8B), ""),
    "inaccuracy" to Triple("Belirsiz", Color(0xFFF0C15C), "?!"),
    "mistake" to Triple("Hata", Color(0xFFE6912C), "?"),
    "miss" to Triple("Kaçırılan", Color(0xFFFF7769), "?"),
    "blunder" to Triple("Büyük hata", Color(0xFFCA3431), "??"),
    "forced" to Triple("Zorunlu", Color(0xFF97AF8B), ""),
)
