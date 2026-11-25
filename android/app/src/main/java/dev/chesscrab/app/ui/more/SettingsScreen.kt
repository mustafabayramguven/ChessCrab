package dev.chesscrab.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.BOARD_THEMES
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.Prefs
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.kare.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import uniffi.chesscrab_ffi.crabVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREVIEW_FEN = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"

@Composable
fun SettingsScreen(modifier: Modifier, onOpenGame: (String, String) -> Unit, onProgress: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var sound by remember { mutableStateOf(Crab.setting("sound", "1") == "1") }
    var depth by remember { mutableStateOf(Crab.setting("coach_depth", "12")) }
    var server by remember { mutableStateOf(Crab.setting("server")) }
    var user by remember { mutableStateOf(SyncClient.username) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr")) }

    Column(modifier.fillMaxSize()) {
        TopBar("Ayarlar", "SYS 010")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GameRow(title = "İlerleme", sub = "Seri takvimi, doğruluk eğrisi, açılışlar, rakipler", badge = "Aç", onClick = onProgress)
            Hairline()

            SectionLabel("Tahta", trailing = BOARD_THEMES.size.toString())
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in BOARD_THEMES) {
                    val sel = Prefs.boardTheme == t.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.press(scale = 0.95f) { Prefs.setBoard(t.id) }) {
                        Box(Modifier.size(84.dp).border(2.dp, if (sel) K.Ink else K.LineSubtle).padding(3.dp)) {
                            ChessBoard(PREVIEW_FEN, true, emptyList(), theme = t, coordinates = false)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(t.name.upTr(), style = KType.code, color = if (sel) K.Ink else K.Ink3)
                    }
                }
            }
            SectionLabel("Taş seti")
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegButton("Kare", Prefs.pieceSet == "kare", Modifier.weight(1f)) { Prefs.setPieces("kare") }
                SegButton("Klasik", Prefs.pieceSet == "klasik", Modifier.weight(1f)) { Prefs.setPieces("klasik") }
            }
            SectionLabel("Hareket", trailing = if (Motion.systemReduced) "sistem: azaltılmış" else null)
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegButton("Tam animasyon", !Prefs.reducedMotion, Modifier.weight(1f)) { Prefs.setReduced(false) }
                SegButton("Azaltılmış", Prefs.reducedMotion, Modifier.weight(1f)) { Prefs.setReduced(true) }
            }
            Text("Azaltılmış harekette taş uçuşu, darbe dalgaları, şah sonarı ve pulsar nefesi kapanır; ekranlar yalnızca solarak geçer.",
                style = KType.desc, color = K.Ink3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

            SectionLabel("Ses ve koç")
            ToggleRow("Sesler", "Hamle, alım ve şah sesleri", sound) { sound = it; Crab.setSetting("sound", if (it) "1" else "0") }
            Column(Modifier.fillMaxWidth().hairlineTop().hairlineBottom().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Koç derinliği", style = KType.body.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp), color = K.Ink)
                Text("Canlı koçun her hamlede ne kadar derin bakacağı (yüksek = daha doğru, daha çok pil).", style = KType.desc, color = K.Ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in listOf("10", "12", "14", "16")) BranchButton(d, depth == d, Modifier.weight(1f)) {
                        depth = d; Crab.setSetting("coach_depth", d); Crab.coach.setDepth(d.toUInt())
                    }
                }
            }

            val games = refresh.let { JSONArray(Crab.store.listJson("games")).objects() }
            SectionLabel("Oyunlarım", trailing = "${games.size}")
            if (games.isEmpty()) Text("Henüz kayıtlı oyun yok.", style = KType.desc, color = K.Ink3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            for (g in games.take(60)) {
                val d = g.getJSONObject("data")
                val acc = if (d.has("white_accuracy")) " · %.0f / %.0f".format(d.getDouble("white_accuracy"), d.getDouble("black_accuracy")) else ""
                GameRow(
                    title = "${d.optString("white")} – ${d.optString("black")}",
                    sub = "${d.optString("result")} · ${fmt.format(Date(d.optLong("created_ms", g.getLong("updated_ms"))))}$acc",
                    badge = "İncele", badgeColor = K.Ink,
                    trailing = {
                        Box(Modifier.size(TOUCH).press(scale = 0.9f) { Crab.store.delete("games", g.getString("id")); refresh++ }, contentAlignment = Alignment.Center) {
                            Icon(KIcons.Trash, "Sil", tint = K.Ink3, modifier = Modifier.size(18.dp))
                        }
                    },
                ) { onOpenGame(d.getString("pgn"), g.getString("id")) }
            }
            Hairline()

            SectionLabel("Eşitleme")
            Text("Kendi ChessCrab sunucunla oyunlar, ders ilerlemesi ve repertuvar tekrarları cihazlar arasında eşitlenir. Uygulama sunucusuz da tamamen çalışır.",
                style = KType.desc, color = K.Ink2, modifier = Modifier.padding(horizontal = 14.dp))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                refresh.let {
                    if (SyncClient.loggedIn) {
                        StatLine("Hesap", "${SyncClient.username} @ ${SyncClient.server.removePrefix("http://").removePrefix("https://")}")
                        StatLine("Son eşitleme", Crab.setting("last_sync").toLongOrNull()?.let { fmt.format(Date(it)) } ?: "hiç")
                        StatLine("Bekleyen", "${Crab.store.pendingCount()}")
                        SavilleButton("Şimdi eşitle", Modifier.fillMaxWidth(), cap = K.Blue, enabled = !busy) {
                            busy = true
                            scope.launch { SyncClient.sync().onSuccess { info = "$it kayıt güncellendi" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                        }
                        SavilleButton("Çıkış yap", Modifier.fillMaxWidth(), cap = K.Industrial) { SyncClient.logout(); refresh++ }
                    } else {
                        KTextField(server, { server = it }, "Sunucu (ör. http://192.168.1.10:3437)", keyboard = KeyboardType.Uri, mono = true)
                        KTextField(user, { user = it }, "Kullanıcı adı")
                        KTextField(pass, { pass = it }, "Şifre", keyboard = KeyboardType.Password, visual = PasswordVisualTransformation())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SavilleButton("Giriş", Modifier.weight(1f), cap = K.Blue, enabled = !busy && server.isNotBlank()) {
                                busy = true
                                scope.launch { SyncClient.auth(server, "login", user, pass).onSuccess { info = "Hoş geldin $it"; pass = "" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                            }
                            SavilleButton("Kayıt ol", Modifier.weight(1f), cap = K.Yellow, enabled = !busy && server.isNotBlank()) {
                                busy = true
                                scope.launch { SyncClient.auth(server, "register", user, pass).onSuccess { info = "Hoş geldin $it"; pass = "" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                            }
                        }
                    }
                }
                info?.let { Text(it, style = KType.desc, color = K.Ink) }
            }
            Hairline()
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(crabVersion().upTr(), style = KType.code, color = K.Ink3)
                Text("Kare taşları: konsept çizimi · Klasik: cburnett (Colin M.L. Burnett, GPLv2+)", style = KType.desc.copy(fontSize = 11.sp), color = K.Ink3)
                Text("Yazı tipleri: Archivo, JetBrains Mono (SIL OFL 1.1)", style = KType.desc.copy(fontSize = 11.sp), color = K.Ink3)
            }
        }
    }
}
