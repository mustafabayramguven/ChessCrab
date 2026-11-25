package dev.chesscrab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chesscrab.app.ui.CrabTheme
import dev.chesscrab.app.ui.K
import dev.chesscrab.app.ui.KType
import dev.chesscrab.app.ui.Motion
import dev.chesscrab.app.ui.home.LobbyScreen
import dev.chesscrab.app.ui.kare.KIcons
import dev.chesscrab.app.ui.kare.hairlineTop
import dev.chesscrab.app.ui.kare.press
import dev.chesscrab.app.ui.learn.DrillScreen
import dev.chesscrab.app.ui.learn.LearnScreen
import dev.chesscrab.app.ui.learn.LessonPlayerScreen
import dev.chesscrab.app.ui.learn.PuzzleScreen
import dev.chesscrab.app.ui.learn.RushScreen
import dev.chesscrab.app.ui.more.SettingsScreen
import dev.chesscrab.app.ui.play.BotSelectScreen
import dev.chesscrab.app.ui.play.PlayModel
import dev.chesscrab.app.ui.play.PlayScreen
import dev.chesscrab.app.ui.progress.ProgressScreen
import dev.chesscrab.app.ui.review.ReviewModel
import dev.chesscrab.app.ui.review.ReviewScreen

/** Alt gezinme: başparmak bölgesinde beş hedef */
enum class Tab(val label: String, val icon: ImageVector) {
    Lobby("Lobi", KIcons.Lobby),
    Play("Oyna", KIcons.Play),
    Learn("Öğren", KIcons.Puzzle),
    Analysis("Analiz", KIcons.Analysis),
    Settings("Ayarlar", KIcons.Settings),
}

sealed interface Route {
    data object BotSelect : Route
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
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
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
fun App(
    sharedPgn: MutableState<String?>,
    startTab: Tab = Tab.Lobby,
    startRoute: Route? = null,
    play: PlayModel = remember { PlayModel() },
    review: ReviewModel = remember { ReviewModel() },
    startLearnTab: Int = 0,
) {
    var tab by remember { mutableStateOf(startTab) }
    var route by remember { mutableStateOf(startRoute) }
    var learnTab by remember { mutableIntStateOf(startLearnTab) }

    sharedPgn.value?.let { pgn ->
        sharedPgn.value = null
        review.open(pgn, null, null)
        route = null; tab = Tab.Analysis
    }
    BackHandler(enabled = route != null || tab != Tab.Lobby) { if (route != null) route = null else tab = Tab.Lobby }

    fun openReview(pgn: String, json: String?, id: String?) { review.open(pgn, json, id); route = null; tab = Tab.Analysis }

    Scaffold(
        containerColor = K.Base,
        bottomBar = { if (route == null) BottomNav(tab) { tab = it } },
    ) { pad ->
        val m = Modifier.padding(pad).fillMaxSize()
        val dx = with(LocalDensity.current) { 12.dp.roundToPx() }
        AnimatedContent(
            targetState = route ?: tab,
            transitionSpec = {
                // ekran görünümü: 12dp yatay kayma + opaklık, 260ms ease-out-expo; azaltılmış harekette yalnız 120ms opaklık
                if (Motion.reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                else (fadeIn(tween(Motion.VIEW, easing = Motion.Expo)) + slideInHorizontally(tween(Motion.VIEW, easing = Motion.Expo)) { dx }) togetherWith
                    fadeOut(tween(Motion.VIEW / 2, easing = Motion.Expo))
            },
            label = "nav",
        ) { target ->
            when (target) {
                Route.BotSelect -> BotSelectScreen(play, m, onStart = { route = null; tab = Tab.Play }, onBack = { route = null })
                Route.Puzzle -> PuzzleScreen(m, onExit = { route = null })
                Route.Rush -> RushScreen(m, onExit = { route = null })
                Route.Progress -> ProgressScreen(m, onBack = { route = null })
                is Route.Lesson -> LessonPlayerScreen(target.id, m, onExit = { route = null }, onNext = { route = Route.Lesson(it) })
                is Route.Drill -> DrillScreen(target.rep, target.line, m, onNext = { nl -> route = if (nl != null) Route.Drill(target.rep, nl) else null }, onExit = { route = null })
                Tab.Lobby -> LobbyScreen(
                    play, m,
                    onPlay = { route = Route.BotSelect },
                    onContinueGame = { tab = Tab.Play },
                    onLesson = { route = Route.Lesson(it) },
                    onPuzzle = { route = Route.Puzzle },
                    onRush = { route = Route.Rush },
                    onProgress = { route = Route.Progress },
                    onRepertoire = { learnTab = 1; tab = Tab.Learn },
                    onReviewPgn = { review.reset(); tab = Tab.Analysis },
                    onOpenGame = { pgn, id -> openReview(pgn, null, id) },
                )
                Tab.Play -> PlayScreen(play, m, onNewGame = { route = Route.BotSelect }, onReview = { pgn, json, id -> openReview(pgn, json, id) })
                Tab.Learn -> LearnScreen(
                    m, learnTab, onTab = { learnTab = it },
                    onLesson = { route = Route.Lesson(it) },
                    onDrill = { r, l -> route = Route.Drill(r, l) },
                    onPuzzle = { route = Route.Puzzle },
                    onRush = { route = Route.Rush },
                )
                Tab.Analysis -> ReviewScreen(review, m)
                Tab.Settings -> SettingsScreen(m, onOpenGame = { pgn, id -> openReview(pgn, null, id) }, onProgress = { route = Route.Progress })
                else -> Unit
            }
        }
    }
}

/**
 * Material 3 alt gezinme: #141417 zemin, üst kılçizgi, 20dp çizgi ikonlar,
 * etkin sekmede 56×28 kobalt hap.
 */
@Composable
private fun BottomNav(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(K.Chrome).hairlineTop().navigationBarsPadding().height(66.dp).padding(horizontal = 8.dp).padding(top = 2.dp, bottom = 8.dp),
    ) {
        for (t in Tab.entries) {
            val sel = t == tab
            val fg by animateColorAsState(if (sel) K.Ink else K.Ink3, tween(Motion.TAP), label = "nf")
            val pill by animateDpAsState(if (sel) 56.dp else 24.dp, tween(Motion.STATE, easing = Motion.Expo), label = "np")
            val pillBg by animateColorAsState(if (sel) K.BlueNav else K.BlueNav.copy(alpha = 0f), tween(Motion.STATE), label = "npb")
            Column(
                Modifier.weight(1f).fillMaxHeight().press(scale = 0.94f) { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(pill).height(28.dp).clip(RoundedCornerShape(50)).background(pillBg), contentAlignment = Alignment.Center) {
                    Icon(t.icon, contentDescription = t.label, tint = fg, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = KType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
            }
        }
    }
}
