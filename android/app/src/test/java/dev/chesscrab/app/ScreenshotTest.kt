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

    private val opera = """[White "Paul Morphy"]
[Black "Sen"]
[Result "1-0"]

1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0"""

    private fun shot(name: String, startTab: Tab = Tab.Home, route: Route? = null, play: PlayModel = PlayModel(), review: ReviewModel = ReviewModel()) {
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
}
