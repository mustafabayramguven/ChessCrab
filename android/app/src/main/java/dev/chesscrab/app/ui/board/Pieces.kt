package dev.chesscrab.app.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.chesscrab.app.R
import dev.chesscrab.app.ui.Prefs
import kotlin.math.roundToInt

/** Taş çizici: (taş harfi, sol üst, kare boyu, saydamlık) */
typealias PieceDrawer = DrawScope.(Char, Offset, Float, Float) -> Unit

/**
 * "Kare" taş seti: konseptteki elle çizilmiş, düz geometrik Staunton siluetleri
 * (45×45 görüntü kutusu). Beyaz: #FAFAF5 dolgu / #0D0F14 kontur; siyah: #101217 / #C9CDD6.
 */
private object KarePieces {
    private sealed interface Shape
    private class Circ(val cx: Float, val cy: Float, val r: Float, val detail: Boolean = false) : Shape
    private class Shp(val path: Path, val fill: Boolean = true, val detailStroke: Float = 0f, val strokeOnly: Float = 0f) : Shape
    private class Base(val x: Float, val y: Float, val w: Float, val h: Float) : Shape

    private fun p(d: String) = PathParser().parsePathString(d).toPath()

    private val shapes: Map<Char, List<Shape>> by lazy {
        val base = Base(10f, 31.5f, 25f, 5.5f)
        mapOf(
            'p' to listOf(Circ(22.5f, 14f, 5.4f), Shp(p("M15.5 22c2-3 12-3 14 0l2.2 9.5H13.3l2.2-9.5z")), Base(10.5f, 31.5f, 24f, 5.5f)),
            'r' to listOf(Shp(p("M11.5 10h4.5v4h4v-4h5v4h4v-4h4.5v8.5h-22V10z")), Shp(p("M14.5 18.5h16l1.5 13h-19l1.5-13z")), base),
            'n' to listOf(Shp(p("M12.5 31.5c1-9.5 5-15.5 5.5-19.5l4-3 3 2c4.5 1.2 7.5 5.5 7.5 11.5l-4.2 1-3.3-3-2.5 11h-10z")), Circ(24f, 15.5f, 1.4f, detail = true), base),
            'b' to listOf(Circ(22.5f, 8.5f, 2.6f), Shp(p("M15.5 20.5c0-5.2 3.2-9.5 7-9.5s7 4.3 7 9.5c0 3-2 6-3 11h-8c-1-5-3-8-3-11z")), Shp(p("M19.5 17.5l6-4"), fill = false, detailStroke = 1.6f), base),
            'q' to listOf(
                Circ(9.5f, 13f, 2.2f), Circ(16f, 9.5f, 2.2f), Circ(22.5f, 8f, 2.3f), Circ(29f, 9.5f, 2.2f), Circ(35.5f, 13f, 2.2f),
                Shp(p("M10.5 16l4 15.5h16l4-15.5-6.2 6-5.8-10.2-5.8 10.2-6.2-6z")), base,
            ),
            'k' to listOf(
                Shp(p("M22.5 5v8M18.5 9h8"), fill = false, strokeOnly = 2.2f),
                Shp(p("M12.5 18.5c0-3.8 3.2-5.8 6.2-5.8 1.9 0 3.1 1 3.8 2 .7-1 1.9-2 3.8-2 3 0 6.2 2 6.2 5.8 0 4-3 7-4 13h-12c-1-6-4-9-4-13z")),
                base,
            ),
        )
    }

    fun DrawScope.drawKare(c: Char, topLeft: Offset, size: Float, alpha: Float) {
        val white = c.isUpperCase()
        val fill = if (white) Color(0xFFFAFAF5) else Color(0xFF101217)
        val stroke = if (white) Color(0xFF0D0F14) else Color(0xFFC9CDD6)
        val sw = if (white) 1.8f else 1.35f
        val detail = if (white) Color(0xFF252933) else Color(0xFFEAEAE4)
        val list = shapes[c.lowercaseChar()] ?: return
        val k = size * 0.86f / 45f
        val inset = size * 0.07f
        translate(topLeft.x + inset, topLeft.y + inset) {
            scale(k, k, pivot = Offset.Zero) {
                val st = Stroke(sw, join = StrokeJoin.Round, cap = StrokeCap.Round)
                for (s in list) when (s) {
                    is Circ -> if (s.detail) drawCircle(detail, s.r, Offset(s.cx, s.cy), alpha = alpha) else {
                        drawCircle(fill, s.r, Offset(s.cx, s.cy), alpha = alpha)
                        drawCircle(stroke, s.r, Offset(s.cx, s.cy), alpha = alpha, style = st)
                    }
                    is Shp -> when {
                        s.strokeOnly > 0f -> drawPath(s.path, stroke, alpha = alpha, style = Stroke(s.strokeOnly, cap = StrokeCap.Round))
                        s.detailStroke > 0f -> drawPath(s.path, detail, alpha = alpha, style = Stroke(s.detailStroke, cap = StrokeCap.Round))
                        else -> { drawPath(s.path, fill, alpha = alpha); drawPath(s.path, stroke, alpha = alpha, style = st) }
                    }
                    is Base -> {
                        drawRoundRect(fill, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(1.5f), alpha = alpha)
                        drawRoundRect(stroke, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(1.5f), alpha = alpha, style = st)
                    }
                }
            }
        }
    }
}

/** Klasik (cburnett) bitmapleri */
@Composable
private fun rememberBitmaps(): Map<Char, ImageBitmap> {
    val ids = mapOf(
        'K' to R.drawable.piece_wk, 'Q' to R.drawable.piece_wq, 'R' to R.drawable.piece_wr,
        'B' to R.drawable.piece_wb, 'N' to R.drawable.piece_wn, 'P' to R.drawable.piece_wp,
        'k' to R.drawable.piece_bk, 'q' to R.drawable.piece_bq, 'r' to R.drawable.piece_br,
        'b' to R.drawable.piece_bb, 'n' to R.drawable.piece_bn, 'p' to R.drawable.piece_bp,
    )
    return ids.mapValues { ImageBitmap.imageResource(it.value) }
}

/** Seçili taş setine göre çizici (ayarlardan değişince anında) */
@Composable
fun rememberPieceDrawer(set: String = Prefs.pieceSet): PieceDrawer {
    val bitmaps = rememberBitmaps()
    return remember(set, bitmaps) {
        val klasik: PieceDrawer = { c, tl, s, a ->
            bitmaps[c]?.let { img ->
                drawImage(
                    img, IntOffset.Zero, IntSize(img.width, img.height),
                    IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), IntSize(s.roundToInt(), s.roundToInt()),
                    alpha = a, filterQuality = FilterQuality.High,
                )
            }
        }
        val kare: PieceDrawer = { c, tl, s, a -> with(KarePieces) { drawKare(c, tl, s, a) } }
        if (set == "klasik") klasik else kare
    }
}
