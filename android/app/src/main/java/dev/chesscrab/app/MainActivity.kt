package dev.chesscrab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chesscrab.app.ui.AnalysisModel
import dev.chesscrab.app.ui.AnalysisScreen
import dev.chesscrab.app.ui.CrabTheme
import dev.chesscrab.app.ui.LessonsScreen
import dev.chesscrab.app.ui.PlayModel
import dev.chesscrab.app.ui.PlayScreen
import dev.chesscrab.app.ui.RepertoireScreen
import dev.chesscrab.app.ui.SettingsScreen

enum class Tab(val label: String, val icon: ImageVector) {
    Play("Oyna", Icons.Filled.SportsEsports),
    Lessons("Dersler", Icons.AutoMirrored.Filled.MenuBook),
    Analysis("Analiz", Icons.Filled.Insights),
    Repertoire("Repertuvar", Icons.Filled.Psychology),
    Settings("Daha", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val sharedPgn = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent { CrabTheme { App(sharedPgn) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** lichess/chess.com uygulamasından "Paylaş → ChessCrab" ile gelen PGN */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedPgn.value = it }
        }
    }
}

@Composable
fun App(sharedPgn: androidx.compose.runtime.MutableState<String?>) {
    var tab by remember { mutableStateOf(Tab.Play) }
    val play = remember { PlayModel() }
    val analysis = remember { AnalysisModel() }

    sharedPgn.value?.let { pgn ->
        sharedPgn.value = null
        analysis.load(pgn, null)
        tab = Tab.Analysis
    }

    BackHandler(enabled = tab != Tab.Play) { tab = Tab.Play }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            Tab.Play -> PlayScreen(play, m) { pgn, id -> analysis.load(pgn, id); tab = Tab.Analysis }
            Tab.Lessons -> LessonsScreen(m)
            Tab.Analysis -> AnalysisScreen(analysis, m)
            Tab.Repertoire -> RepertoireScreen(m)
            Tab.Settings -> SettingsScreen(m) { pgn, id -> analysis.load(pgn, id); tab = Tab.Analysis }
        }
    }
}
