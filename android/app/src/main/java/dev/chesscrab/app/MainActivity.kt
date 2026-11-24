package dev.chesscrab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import dev.chesscrab.app.ui.CrabTheme
import dev.chesscrab.app.ui.Crabs
import dev.chesscrab.app.ui.home.HomeScreen
import dev.chesscrab.app.ui.learn.DrillScreen
import dev.chesscrab.app.ui.learn.LessonPlayerScreen
import dev.chesscrab.app.ui.learn.LessonsList
import dev.chesscrab.app.ui.learn.PuzzleScreen
import dev.chesscrab.app.ui.learn.RepertoireList
import dev.chesscrab.app.ui.more.MoreScreen
import dev.chesscrab.app.ui.play.BotSelectScreen
import dev.chesscrab.app.ui.play.PlayModel
import dev.chesscrab.app.ui.play.PlayScreen
import dev.chesscrab.app.ui.review.ReviewModel
import dev.chesscrab.app.ui.review.ReviewScreen

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Ana Sayfa", Icons.Filled.Home),
    Play("Oyna", Icons.Filled.SportsEsports),
    Learn("Öğren", Icons.AutoMirrored.Filled.MenuBook),
    More("Daha", Icons.Filled.MoreHoriz),
}

sealed interface Route {
    data object BotSelect : Route
    data object Review : Route
    data object Puzzle : Route
    data class Lesson(val id: String) : Route
    data class Drill(val rep: String, val line: Int) : Route
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
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedPgn.value = it }
    }
}

@Composable
fun App(sharedPgn: MutableState<String?>, startTab: Tab = Tab.Home, startRoute: Route? = null, play: PlayModel = remember { PlayModel() }, review: ReviewModel = remember { ReviewModel() }) {
    var tab by remember { mutableStateOf(startTab) }
    var route by remember { mutableStateOf(startRoute) }
    var learnTab by remember { mutableStateOf(0) }

    sharedPgn.value?.let { pgn ->
        sharedPgn.value = null
        review.open(pgn, null, null)
        route = Route.Review
    }
    BackHandler(enabled = route != null || tab != Tab.Home) { if (route != null) route = null else tab = Tab.Home }

    fun openReview(pgn: String, json: String?, id: String?) { review.open(pgn, json, id); route = Route.Review }

    Scaffold(
        containerColor = Crabs.Bg,
        bottomBar = {
            if (route == null) NavigationBar(containerColor = Crabs.Surface) {
                for (t in Tab.entries) NavigationBarItem(
                    selected = tab == t, onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = t.label) }, label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Crabs.GreenDark, selectedTextColor = Color.White, unselectedIconColor = Crabs.Muted, unselectedTextColor = Crabs.Muted),
                )
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad).fillMaxSize()
        AnimatedContent(
            targetState = route ?: tab,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 24 }) togetherWith fadeOut(tween(120)) },
            label = "nav",
        ) { target ->
            when (target) {
                Route.BotSelect -> BotSelectScreen(play, m, onStart = { route = null; tab = Tab.Play }, onBack = { route = null })
                Route.Review -> ReviewScreen(review, m)
                Route.Puzzle -> PuzzleScreen(m, onExit = { route = null })
                is Route.Lesson -> LessonPlayerScreen(target.id, m, onExit = { route = null }, onNext = { route = Route.Lesson(it) })
                is Route.Drill -> DrillScreen(target.rep, target.line, m, onNext = { nl -> route = if (nl != null) Route.Drill(target.rep, nl) else null }, onExit = { route = null })
                Tab.Home -> HomeScreen(
                    play, m,
                    onPlay = { route = Route.BotSelect },
                    onContinueGame = { tab = Tab.Play },
                    onLesson = { route = Route.Lesson(it) },
                    onPuzzle = { route = Route.Puzzle },
                    onRepertoire = { learnTab = 1; tab = Tab.Learn },
                    onReviewPgn = { review.reset(); route = Route.Review },
                    onOpenGame = { pgn, id -> openReview(pgn, null, id) },
                )
                Tab.Play -> PlayScreen(play, m, onNewGame = { route = Route.BotSelect }, onReview = { pgn, json, id -> openReview(pgn, json, id) })
                Tab.Learn -> Column(m) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clip(RoundedCornerShape(10.dp)).background(Crabs.Card)) {
                        listOf("Dersler", "Repertuvar", "Bulmaca").forEachIndexed { i, l ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (learnTab == i) Crabs.GreenDark else Color.Transparent)
                                    .clickable { if (i == 2) route = Route.Puzzle else learnTab = i }.padding(vertical = 10.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) { Text(l, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (learnTab == 0) LessonsList(Modifier.weight(1f)) { route = Route.Lesson(it) }
                    else RepertoireList(Modifier.weight(1f)) { r, l -> route = Route.Drill(r, l) }
                }
                Tab.More -> MoreScreen(m, onOpenGame = { pgn, id -> openReview(pgn, null, id) }, onThemeChanged = {})
                else -> Unit
            }
        }
    }
}
