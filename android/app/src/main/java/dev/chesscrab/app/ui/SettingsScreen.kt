package dev.chesscrab.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.chesscrab.app.Crab
import dev.chesscrab.app.SyncClient
import dev.chesscrab.app.objects
import kotlinx.coroutines.launch
import org.json.JSONArray
import uniffi.chesscrab_ffi.crabVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier, onAnalyse: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(Crab.setting("server")) }
    var user by remember { mutableStateOf(SyncClient.username) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("tr")) }

    fun auth(kind: String) {
        busy = true
        scope.launch {
            SyncClient.auth(server, kind, user, pass)
                .onSuccess { info = "Hoş geldin $it, senkronize edildi."; pass = "" }
                .onFailure { info = "Hata: ${it.message}" }
            busy = false; refresh++
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Oyunlarım", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val games = refresh.let { JSONArray(Crab.store.listJson("games")).objects() }
        if (games.isEmpty()) Text("Henüz kayıtlı oyun yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (g in games.take(50)) {
            val d = g.getJSONObject("data")
            Row(
                Modifier.fillMaxWidth().clickable { onAnalyse(d.getString("pgn"), g.getString("id")) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${d.optString("white")} – ${d.optString("black")}  ${d.optString("result")}", fontWeight = FontWeight.SemiBold)
                    val acc = if (d.has("white_accuracy")) " · %.0f / %.0f".format(d.getDouble("white_accuracy"), d.getDouble("black_accuracy")) else ""
                    Text(fmt.format(Date(d.optLong("created_ms", g.getLong("updated_ms")))) + " · " + d.optString("opening", "") + acc,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Crab.store.delete("games", g.getString("id")); refresh++ }) { Icon(Icons.Filled.Delete, "sil") }
            }
            HorizontalDivider()
        }

        Spacer(Modifier.height(20.dp))
        Text("Senkronizasyon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Kendi ChessCrab sunucunla telefon ve web arasında oyunlar, ders ilerlemesi ve repertuvar tekrarları eşitlenir. Uygulama sunucusuz da tamamen çalışır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                refresh.let {
                    if (SyncClient.loggedIn) {
                        Text("Giriş: ${SyncClient.username} @ ${SyncClient.server}")
                        val last = Crab.setting("last_sync").toLongOrNull()
                        Text("Son senkronizasyon: ${last?.let { fmt.format(Date(it)) } ?: "hiç"} · bekleyen ${Crab.store.pendingCount()} değişiklik",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    SyncClient.sync().onSuccess { info = "$it kayıt güncellendi" }.onFailure { info = "Hata: ${it.message}" }
                                    busy = false; refresh++
                                }
                            }) { Text("Şimdi senkronize et") }
                            OutlinedButton(onClick = { SyncClient.logout(); refresh++ }) { Text("Çıkış") }
                        }
                    } else {
                        OutlinedTextField(server, { server = it }, label = { Text("Sunucu (ör. http://192.168.1.10:3437)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(user, { user = it }, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pass, { pass = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(enabled = !busy && server.isNotBlank(), onClick = { auth("login") }) { Text("Giriş") }
                            OutlinedButton(enabled = !busy && server.isNotBlank(), onClick = { auth("register") }) { Text("Kayıt ol") }
                        }
                    }
                }
                info?.let { Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(crabVersion(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Motor, analiz ve dersler tamamen cihazda çalışır (Rust).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

