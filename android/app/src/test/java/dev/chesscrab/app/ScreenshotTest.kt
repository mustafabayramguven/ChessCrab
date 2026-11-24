package dev.chesscrab.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chesscrab.app.ui.CrabTheme
import dev.chesscrab.app.ui.play.BOTS
import dev.chesscrab.app.ui.play.PlayModel
import dev.chesscrab.app.ui.review.Phase
import dev.chesscrab.app.ui.review.ReviewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uniffi.chesscrab_ffi.analyzePgn

/** Uygulamanın ana ekranlarını çizip PNG olarak kaydeder (CI'da incelenir). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    init {
        // sonsuz ortam animasyonları test saatini meşgul etmesin
        dev.chesscrab.app.ui.Motion.ambient = false
    }

    private val opera = """[White "Paul Morphy"]
[Black "Sen"]
[Result "1-0"]

1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0"""

    private fun shot(name: String, startTab: Tab = Tab.Home, route: Route? = null, play: PlayModel = PlayModel(), review: ReviewModel = ReviewModel()) {
        dev.chesscrab.app.ui.Prefs.appTheme = "gelgit"; dev.chesscrab.app.ui.Prefs.boardTheme = "sand"
        compose.setContent { CrabTheme { App(mutableStateOf(null), startTab, route, play, review) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun home() = shot("01-ana-sayfa")

    @Test fun botSelect() = shot("02-bot-secimi", route = Route.BotSelect)

    @Test fun playWithCoach() {
        val p = PlayModel()
        // Siyahın 4...Nd4?! ve beyazın 5.Nxe5?? hatasıyla kısa bir oyun
        p.loadForPreview(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "d1e2", "c6d4", "f3e5"), BOTS[3], analyse = true)
        shot("03-oyun-canli-koc", startTab = Tab.Play, play = p)
    }

    private fun reviewModel(ply: Int? = null, retry: Boolean = false): ReviewModel {
        val r = ReviewModel()
        r.open(opera, analyzePgn(opera, 10u, 2u, null), null)
        if (ply != null) { r.phase = Phase.Steps; r.go(ply); if (retry) r.startRetry() }
        return r
    }

    @Test fun reviewSummary() = shot("04-inceleme-ozet", route = Route.Review, review = reviewModel())

    @Test fun reviewBrilliant() = shot("05-inceleme-parlak", route = Route.Review, review = reviewModel(31))

    @Test fun reviewMistake() = shot("06-inceleme-hata", route = Route.Review, review = reviewModel(22))

    @Test fun reviewRetry() = shot("07-daha-iyisini-dene", route = Route.Review, review = reviewModel(22, retry = true))

    @Test fun lessons() = shot("08-dersler", startTab = Tab.Learn)

    @Test fun lessonPlayer() = shot("09-ders", route = Route.Lesson("prensip-merkez"))

    @Test fun puzzle() = shot("10-bulmaca", route = Route.Puzzle)

    @Test fun drill() = shot("11-repertuvar", route = Route.Drill("siyah-caro", 0))

    @Test fun more() = shot("12-ayarlar", startTab = Tab.More)

    /** İlerleme ekranı için örnek oyunlar ve seri */
    private fun seed() {
        val opens = listOf("İtalyan Oyunu", "Sicilya Savunması", "Caro-Kann Savunması", "İtalyan Oyunu", "Londra Sistemi")
        val accs = listOf(62.0, 70.5, 66.0, 74.2, 71.0, 79.8, 76.4, 83.1, 80.2, 86.5)
        accs.forEachIndexed { i, a ->
            val white = i % 2 == 0
            val res = listOf("1-0", "0-1", "1/2-1/2", "1-0", "1-0")[i % 5]
            val d = org.json.JSONObject()
                .put("pgn", "1. e4 e5 *").put("white", if (white) "Sen" else "Ahtapot Ayla (1300)").put("black", if (white) "Fok Fikret (1600)" else "Sen")
                .put("result", res).put("opening", opens[i % opens.size]).put("created_ms", 1_700_000_000_000L + i * 86_400_000L)
                .put("white_accuracy", if (white) a else 60.0).put("black_accuracy", if (white) 60.0 else a)
            Crab.store.put("games", "g$i", d.toString())
        }
        val t = Streak.today()
        Crab.setSetting("active_days", ((t - 6)..t).plus(listOf(t - 12, t - 13, t - 20)).joinToString(","))
        Crab.setSetting("rush_best", "14")
    }

    @Test fun progress() { seed(); shot("13-ilerleme", route = Route.Progress) }

    @Test fun rushIntro() { Crab.setSetting("rush_best", "14"); shot("14-firtina", route = Route.Rush) }

    @Test fun rushRun() {
        dev.chesscrab.app.ui.Prefs.appTheme = "gelgit"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CrabTheme {
                androidx.compose.material3.Surface(color = dev.chesscrab.app.ui.Crabs.Bg) {
                    dev.chesscrab.app.ui.learn.RushScreen(androidx.compose.ui.Modifier, onExit = {}, startRunning = true)
                }
            }
        }
        // süre işlesin ama fırtına bitmesin: elle 12 saniye ilerlet
        repeat(120) { compose.mainClock.advanceTimeBy(100) }
        compose.onRoot().captureRoboImage("build/screens/15-firtina-oyun.png")
    }

    @Test fun lightTheme() {
        seed()
        dev.chesscrab.app.ui.Prefs.appTheme = "kumsal"; dev.chesscrab.app.ui.Prefs.boardTheme = "sand"
        compose.setContent { CrabTheme { App(mutableStateOf(null)) } }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(3000); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/16-tema-kumsal.png")
    }

    /** Tema çalışırken değişince tahta ve uygulama hemen güncellenmeli */
    @Test fun themeSwitchLive() {
        val p = PlayModel()
        p.loadForPreview(listOf("e2e4", "e7e5", "g1f3", "b8c6"), BOTS[2], analyse = false)
        dev.chesscrab.app.ui.Prefs.appTheme = "gelgit"; dev.chesscrab.app.ui.Prefs.boardTheme = "sand"
        compose.setContent { CrabTheme { App(mutableStateOf(null), Tab.Play, null, p, ReviewModel()) } }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
        compose.runOnUiThread { dev.chesscrab.app.ui.Prefs.setApp("resif"); dev.chesscrab.app.ui.Prefs.setBoard("coral") }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/17-tema-canli-degisim.png")
        org.junit.Assert.assertEquals("coral", Crab.setting("board_theme"))
    }
}
