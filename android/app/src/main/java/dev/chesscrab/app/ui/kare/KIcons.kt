package dev.chesscrab.app.ui.kare

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Konseptteki el çizimi çizgi ikonlar (24×24, yuvarlak uçlu kontur).
 * Emoji ya da Unicode satranç glifi kullanılmaz.
 */
object KIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
    private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x ${y}h${w}v${h}h${-w}z"

    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // alt gezinme (stroke 1.75)
    val Play = icon("play", 1.75f, "M8 20h8M9 17h6l1-7-4-6-4 6 1 7z", circle(12f, 4f, 1.2f))
    val Puzzle = icon("puzzle", 1.75f, "M12 3l2.6 5.4L20.5 9l-4.3 4.1 1 5.9L12 16.2 6.8 19l1-5.9L3.5 9l5.9-.6L12 3z")
    val Analysis = icon("analysis", 1.75f, "M3 17L9 11L13 15L21 7", "M15 7H21V13")
    val Lobby = icon("lobby", 1.75f, rect(3f, 3f, 7f, 7f), rect(14f, 3f, 7f, 7f), rect(3f, 14f, 7f, 7f), rect(14f, 14f, 7f, 7f))
    val Settings = icon("settings", 1.75f, "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1", circle(15f, 6f, 2f), circle(9f, 12f, 2f), circle(17f, 18f, 2f))

    // eylemler (stroke 2)
    val Target = icon("target", 2f, circle(12f, 12f, 8f), circle(12f, 12f, 2.5f), "M12 2v3M12 19v3M2 12h3M19 12h3")
    val Bulb = icon("bulb", 2f, "M9 18h6M10 22h4M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2z")
    val Reset = icon("reset", 2f, "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5")
    val Undo = icon("undo", 2f, "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Prev = icon("prev", 2f, "M15 18L9 12L15 6")
    val Next = icon("next", 2f, "M9 18L15 12L9 6")
    val First = icon("first", 2f, "M17 18L11 12L17 6", "M7 6v12")
    val PlayTri = icon("playtri", 2f, "M5 3L19 12L5 21Z")
    val Pause = icon("pause", 2f, "M8 5v14M16 5v14")
    val Plus = icon("plus", 2f, "M12 5v14M5 12h14")
    val Flip = icon("flip", 2f, "M7 4v16M7 4L4 7M7 4l3 3", "M17 20V4M17 20l-3-3M17 20l3-3")
    val Flag = icon("flag", 2f, "M5 21V4", "M5 4h11l-2 4 2 4H5")
    val Coach = icon("coach", 2f, "M4 5h16v10H9l-5 4z", "M8 9h8M8 12h5")
    val Back = icon("back", 2f, "M15 18L9 12L15 6")
    val Close = icon("close", 2f, "M6 6l12 12M18 6L6 18")
    val Trash = icon("trash", 2f, "M4 7h16M10 11v6M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3")
    val Compass = icon("compass", 2f, circle(12f, 12f, 9f), "M15.5 8.5l-2 5-5 2 2-5z")
    val Summary = icon("summary", 2f, rect(4f, 4f, 16f, 16f), "M8 15v-3M12 15V9M16 15v-5")
    val Eye = icon("eye", 2f, "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
}
