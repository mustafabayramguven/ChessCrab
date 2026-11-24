package dev.chesscrab.app

import dev.chesscrab.app.ui.components.bounceClick
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.unit.sp
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
    Home("Liman", Icons.Filled.Home),
    Play("Oyna", Icons.Filled.SportsEsports),
    Learn("Öğren", Icons.AutoMirrored.Filled.MenuBook),
    More("Daha", Icons.Filled.MoreHoriz),
}

sealed interface Route {
    data object BotSelect : Route
    data object Review : Route
    data object Puzzle : Route
    data object Rush : Route
    data object Progress : Route
    data class Lesson(val id: String) : Route
    data class Drill(val rep: String, val line: Int) : Route
}

class MainActivity : ComponentActivity() {
    private val sharedPgn = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            CrabTheme {
                // durum çubuğu simgeleri temaya uysun (açık temada koyu simgeler)
                val dark = Crabs.Dark
                androidx.compose.runtime.LaunchedEffect(dark) {
                    val st = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = st, navigationBarStyle = st)
                }
                App(sharedPgn)
            }
        }
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
            if (route == null) FloatingNav(tab) { tab = it }
        },
    ) { pad ->
        val m = Modifier.padding(pad).fillMaxSize()
        AnimatedContent(
            targetState = route ?: tab,
            transitionSpec = {
                val into = targetState is Route; val from = initialState is Route
                when {
                    // ekran açılırken sağdan kayar, arkadaki hafifçe küçülür
                    into && !from -> (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260))) togetherWith
                        (scaleOut(tween(260), targetScale = 0.94f) + fadeOut(tween(200)))
                    from && !into -> (scaleIn(tween(300), initialScale = 0.94f) + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(220)))
                    !into && !from -> {
                        // sekmeler arası: yöne göre yatay kayma
                        val dir = if ((targetState as Tab).ordinal > (initialState as Tab).ordinal) 1 else -1
                        (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { dir * it / 6 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(240)) { -dir * it / 6 } + fadeOut(tween(160)))
                    }
                    else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                }
            },
            label = "nav",
        ) { target ->
            when (target) {
                Route.BotSelect -> BotSelectScreen(play, m, onStart = { route = null; tab = Tab.Play }, onBack = { route = null })
                Route.Review -> ReviewScreen(review, m)
                Route.Puzzle -> PuzzleScreen(m, onExit = { route = null })
                Route.Rush -> dev.chesscrab.app.ui.learn.RushScreen(m, onExit = { route = null })
                Route.Progress -> dev.chesscrab.app.ui.progress.ProgressScreen(m, onBack = { route = null })
                is Route.Lesson -> LessonPlayerScreen(target.id, m, onExit = { route = null }, onNext = { route = Route.Lesson(it) })
                is Route.Drill -> DrillScreen(target.rep, target.line, m, onNext = { nl -> route = if (nl != null) Route.Drill(target.rep, nl) else null }, onExit = { route = null })
                Tab.Home -> HomeScreen(
                    play, m,
                    onPlay = { route = Route.BotSelect },
                    onContinueGame = { tab = Tab.Play },
                    onLesson = { route = Route.Lesson(it) },
                    onPuzzle = { route = Route.Puzzle },
                    onRush = { route = Route.Rush },
                    onProgress = { route = Route.Progress },
                    onRepertoire = { learnTab = 1; tab = Tab.Learn },
                    onReviewPgn = { review.reset(); route = Route.Review },
                    onOpenGame = { pgn, id -> openReview(pgn, null, id) },
                )
                Tab.Play -> PlayScreen(play, m, onNewGame = { route = Route.BotSelect }, onReview = { pgn, json, id -> openReview(pgn, json, id) })
                Tab.Learn -> Column(m) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
                        dev.chesscrab.app.ui.components.Overline("Öğren · çevrimdışı kurslar")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            listOf("Dersler", "Repertuvar", "Bulmaca").forEachIndexed { i, l ->
                                val sel = learnTab == i
                                val col by androidx.compose.animation.animateColorAsState(if (sel) Crabs.Text else Crabs.Faint, tween(250), label = "lt")
                                val uw by androidx.compose.animation.core.animateDpAsState(if (sel) 28.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = 0.5f), label = "lu")
                                Column(Modifier.clickable { learnTab = i }) {
                                    Text(l, fontFamily = dev.chesscrab.app.ui.Display, fontSize = 26.sp, color = col)
                                    Box(Modifier.padding(top = 2.dp).height(3.dp).width(uw).clip(RoundedCornerShape(2.dp)).background(Crabs.Coral))
                                }
                            }
                        }
                    }
                    AnimatedContent(learnTab, Modifier.weight(1f), transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { dir * it / 5 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(220)) { -dir * it / 5 } + fadeOut(tween(150)))
                    }, label = "learn") { lt ->
                        when (lt) {
                            0 -> LessonsList(Modifier.fillMaxSize()) { route = Route.Lesson(it) }
                            1 -> RepertoireList(Modifier.fillMaxSize()) { r, l -> route = Route.Drill(r, l) }
                            else -> dev.chesscrab.app.ui.learn.PuzzleHub(Modifier.fillMaxSize(), onDaily = { route = Route.Puzzle }, onRush = { route = Route.Rush })
                        }
                    }
                }
                Tab.More -> MoreScreen(m, onOpenGame = { pgn, id -> openReview(pgn, null, id) }, onThemeChanged = {}, onProgress = { route = Route.Progress })
                else -> Unit
            }
        }
    }
}

/** Yüzen alt menü: seçili sekme mercan bir hapa genişler. */
@Composable
private fun FloatingNav(tab: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Crabs.Bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Crabs.Card).border(1.dp, Crabs.Line, RoundedCornerShape(28.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (t in Tab.entries) {
                val sel = t == tab
                val bg by androidx.compose.animation.animateColorAsState(if (sel) Crabs.Coral else Color.Transparent, tween(260), label = "nb")
                val fg by androidx.compose.animation.animateColorAsState(if (sel) Crabs.Ink else Crabs.Muted, tween(260), label = "nf")
                Row(
                    Modifier.bounceClick(scaleTo = 0.9f) { onSelect(t) }.clip(RoundedCornerShape(22.dp)).background(bg)
                        .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f)).padding(horizontal = if (sel) 16.dp else 14.dp, vertical = 11.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = fg, modifier = Modifier.size(22.dp))
                    if (sel) { Spacer(Modifier.width(8.dp)); Text(t.label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1) }
                }
            }
        }
    }
}
