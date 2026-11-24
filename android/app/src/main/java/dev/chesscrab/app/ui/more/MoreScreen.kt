package dev.chesscrab.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import dev.chesscrab.app.ui.BOARD_THEMES
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.board.ChessBoard
import dev.chesscrab.app.ui.components.BigButton
import dev.chesscrab.app.ui.components.SectionTitle
import kotlinx.coroutines.launch
import org.json.JSONArray
import uniffi.chesscrab_ffi.crabVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreScreen(modifier: Modifier, onOpenGame: (String, String) -> Unit, onThemeChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var theme by remember { mutableStateOf(Crab.setting("board_theme", "green")) }
    var sound by remember { mutableStateOf(Crab.setting("sound", "1") == "1") }
    var depth by remember { mutableStateOf(Crab.setting("coach_depth", "12")) }
    var server by remember { mutableStateOf(Crab.setting("server")) }
    var user by remember { mutableStateOf(SyncClient.username) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr")) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        SectionTitle("Tahta")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (t in BOARD_THEMES) {
                val sel = theme == t.id
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    theme = t.id; Crab.setSetting("board_theme", t.id); dev.chesscrab.app.ui.Prefs.boardTheme = t.id; onThemeChanged()
                }) {
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(6.dp)).border(3.dp, if (sel) Crabs.Green else Color.Transparent, RoundedCornerShape(6.dp)).padding(3.dp)) {
                        ChessBoard("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", true, emptyList(), theme = t, coordinates = false)
                    }
                    Text(t.name, fontSize = 12.sp, color = if (sel) Crabs.Text else Crabs.Muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Sesler", "Hamle, alım ve şah sesleri", sound) { sound = it; Crab.setSetting("sound", if (it) "1" else "0") }
        Spacer(Modifier.height(6.dp))
        Text("Koç derinliği", fontWeight = FontWeight.SemiBold)
        Text("Canlı koçun her hamlede ne kadar derin bakacağı (yüksek = daha doğru, daha çok pil)", color = Crabs.Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            for (d in listOf("10", "12", "14", "16")) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (depth == d) Crabs.Green else Crabs.Card).clickable {
                    depth = d; Crab.setSetting("coach_depth", d); Crab.coach.setDepth(d.toUInt())
                }.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(d, fontWeight = FontWeight.Bold) }
            }
        }

        SectionTitle("Oyunlarım")
        val games = refresh.let { JSONArray(Crab.store.listJson("games")).objects() }
        if (games.isEmpty()) Text("Henüz kayıtlı oyun yok.", color = Crabs.Muted)
        for (g in games.take(60)) {
            val d = g.getJSONObject("data")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Crabs.Surface)
                    .clickable { onOpenGame(d.getString("pgn"), g.getString("id")) }.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("${d.optString("white")} – ${d.optString("black")}  ${d.optString("result")}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    val acc = if (d.has("white_accuracy")) " · %.0f / %.0f".format(d.getDouble("white_accuracy"), d.getDouble("black_accuracy")) else ""
                    Text(fmt.format(Date(d.optLong("created_ms", g.getLong("updated_ms")))) + " · " + d.optString("opening", "") + acc, fontSize = 12.sp, color = Crabs.Muted, maxLines = 1)
                }
                IconButton(onClick = { Crab.store.delete("games", g.getString("id")); refresh++ }) { Icon(Icons.Filled.Delete, "sil", tint = Crabs.Faint) }
            }
        }

        SectionTitle("Senkronizasyon")
        Text("Kendi ChessCrab sunucunla cihazların arasında oyunlar, ders ilerlemesi ve repertuvar tekrarları eşitlenir. Uygulama sunucusuz da tamamen çalışır.", color = Crabs.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        val tf = OutlinedTextFieldDefaults.colors(focusedBorderColor = Crabs.Green)
        refresh.let {
            if (SyncClient.loggedIn) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Crabs.Card).padding(14.dp)) {
                    Text("${SyncClient.username} @ ${SyncClient.server}", fontWeight = FontWeight.SemiBold)
                    val last = Crab.setting("last_sync").toLongOrNull()
                    Text("Son: ${last?.let { fmt.format(Date(it)) } ?: "hiç"} · bekleyen ${Crab.store.pendingCount()}", color = Crabs.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    BigButton("Şimdi senkronize et", enabled = !busy) {
                        busy = true
                        scope.launch { SyncClient.sync().onSuccess { info = "$it kayıt güncellendi" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Çıkış yap", color = Crabs.Muted, modifier = Modifier.clickable { SyncClient.logout(); refresh++ }.padding(6.dp))
                }
            } else {
                OutlinedTextField(server, { server = it }, label = { Text("Sunucu (ör. http://192.168.1.10:3437)") }, singleLine = true, colors = tf,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Kullanıcı adı") }, singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Şifre") }, singleLine = true, colors = tf, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Giriş", Modifier.weight(1f), enabled = !busy && server.isNotBlank()) {
                        busy = true
                        scope.launch { SyncClient.auth(server, "login", user, pass).onSuccess { info = "Hoş geldin $it"; pass = "" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                    }
                    BigButton("Kayıt ol", Modifier.weight(1f), color = Crabs.CardHi, enabled = !busy && server.isNotBlank()) {
                        busy = true
                        scope.launch { SyncClient.auth(server, "register", user, pass).onSuccess { info = "Hoş geldin $it"; pass = "" }.onFailure { info = "Hata: ${it.message}" }; busy = false; refresh++ }
                    }
                }
            }
        }
        info?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(24.dp))
        Text(crabVersion(), color = Crabs.Faint, fontSize = 11.sp)
        Text("Taşlar: cburnett (Colin M.L. Burnett, GPLv2+)", color = Crabs.Faint, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Crabs.Muted, fontSize = 12.sp)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Crabs.Green))
    }
}

