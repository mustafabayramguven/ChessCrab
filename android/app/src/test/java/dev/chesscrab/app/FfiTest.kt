package dev.chesscrab.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.chesscrab_ffi.AnalysisProgress
import uniffi.chesscrab_ffi.ChessGame
import uniffi.chesscrab_ffi.CrabEngine
import uniffi.chesscrab_ffi.CrabException
import uniffi.chesscrab_ffi.LessonPlayer
import uniffi.chesscrab_ffi.RepertoireDrill
import uniffi.chesscrab_ffi.SyncStore
import uniffi.chesscrab_ffi.analyzePgn
import uniffi.chesscrab_ffi.coursesJson
import uniffi.chesscrab_ffi.crabVersion
import uniffi.chesscrab_ffi.srsReviewJson
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Kotlin ↔ Rust köprüsünün uçtan uca testi (uygulamanın kullandığı yollar). */
class FfiTest {
    @Test
    fun gameAndEngine() {
        assertTrue(crabVersion().contains("CrabFish"))
        val g = ChessGame()
        val v = GameView.parse(g.play("e4"))
        assertEquals(listOf("e4"), v.sans)
        assertTrue(!v.turnWhite)
        val e = CrabEngine(8u)
        val mv = JSONObject(e.bestMove(g, 3u, null)!!)
        GameView.parse(g.play(mv.getString("uci")))
        assertEquals(2u, g.ply())
        assertTrue(g.undo())
        val ev = JSONObject(e.evaluate("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", 6u, 2u, null))
        assertEquals("a1a8", ev.getString("best"))
        assertEquals("mate", ev.getJSONObject("score").getString("type"))
        try { g.play("Ke5"); throw AssertionError("yasa dışı hamle kabul edildi") } catch (_: CrabException) {}
        val mate = ChessGame.fromPgn("1. f3 e5 2. g4 Qh4# 0-1")
        val mv2 = GameView.parse(mate.stateJson())
        assertTrue(mv2.over)
        assertEquals("black", mv2.winner)
    }

    @Test
    fun lessonsAndDrill() {
        val courses = JSONArray(coursesJson())
        assertTrue(courses.length() >= 2)
        val p = LessonPlayer("taktik-catal")
        p.next()
        val fb = JSONObject(p.submitMove("e4f6"))
        assertEquals("correct", fb.getString("verdict"))
        val d = RepertoireDrill("siyah-caro", 0u)
        val f2 = JSONObject(d.submit("c7c6"))
        assertTrue(f2.getBoolean("correct"))
        val card = JSONObject(srsReviewJson(null, d.cardId(), "good", 0L))
        assertEquals(86_400_000L, card.getLong("due_ms"))
    }

    @Test
    fun analysisWithProgress() {
        val calls = AtomicInteger(0)
        val cb = object : AnalysisProgress {
            override fun onProgress(done: UInt, total: UInt) { calls.incrementAndGet() }
        }
        val r = JSONObject(analyzePgn("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0", 6u, 2u, cb))
        assertEquals(7, r.getJSONArray("moves").length())
        assertEquals(8, calls.get())
        assertEquals("blunder", r.getJSONArray("moves").getJSONObject(5).getString("class"))
    }

    @Test
    fun syncStore() {
        val f = File.createTempFile("crab", ".db"); f.delete()
        val s = SyncStore.open(f.absolutePath)
        s.put("games", "a", "{\"pgn\":\"*\"}")
        assertEquals(1u, s.pendingCount())
        val req = s.prepareSyncJson()
        val resp = JSONObject().put("cursor", 5).put("accepted", 1).put("changes", JSONArray()).toString()
        s.applySyncJson(req, resp)
        assertEquals(0u, s.pendingCount())
        assertEquals("*", JSONObject(s.get("games", "a")!!).getString("pgn"))
        s.setMeta("pref.server", "http://x")
        assertEquals("http://x", s.meta("pref.server"))
        s.resetSync()
        assertEquals(1u, s.pendingCount())
    }
}
