package dev.chesscrab.app.ui.board

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.chesscrab.app.Crab
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Ses dosyası yok: tahta sesleri çalışırken sentezlenir (ahşap "tok" sesi vb.). */
object Sounds {
    enum class S { MOVE, CAPTURE, CHECK, CASTLE, END, CORRECT, WRONG }

    private const val RATE = 22050
    private val tracks = HashMap<S, AudioTrack>()
    val enabled: Boolean get() = Crab.setting("sound", "1") == "1"

    private fun knock(buf: FloatArray, at: Int, gain: Float, pitch: Float) {
        val rnd = Random(7)
        for (i in 0 until (RATE * 0.06).toInt()) {
            val j = at + i
            if (j >= buf.size) break
            val t = i / RATE.toFloat()
            val env = exp(-t * 90f)
            val body = sin(2 * PI * pitch * t).toFloat() * 0.7f + sin(2 * PI * pitch * 2.3 * t).toFloat() * 0.25f
            val click = if (i < 60) (rnd.nextFloat() * 2 - 1) * exp(-i / 12f) else 0f
            buf[j] += gain * (body * env + click * 0.6f)
        }
    }

    private fun tone(buf: FloatArray, at: Int, freq: Float, dur: Float, gain: Float) {
        for (i in 0 until (RATE * dur).toInt()) {
            val j = at + i
            if (j >= buf.size) break
            val t = i / RATE.toFloat()
            val env = minOf(1f, t * 200f) * exp(-t * 6f)
            buf[j] += gain * sin(2 * PI * freq * t).toFloat() * env
        }
    }

    private fun synth(s: S): ShortArray {
        val len = when (s) { S.END -> 0.9f; S.CORRECT, S.WRONG -> 0.45f; else -> 0.18f }
        val buf = FloatArray((RATE * len).toInt())
        when (s) {
            S.MOVE -> knock(buf, 0, 0.9f, 170f)
            S.CAPTURE -> { knock(buf, 0, 1f, 230f); knock(buf, (RATE * 0.035).toInt(), 0.7f, 150f) }
            S.CASTLE -> { knock(buf, 0, 0.8f, 170f); knock(buf, (RATE * 0.07).toInt(), 0.8f, 190f) }
            S.CHECK -> { knock(buf, 0, 0.9f, 200f); tone(buf, (RATE * 0.02).toInt(), 1320f, 0.15f, 0.25f) }
            S.END -> { tone(buf, 0, 523f, 0.5f, 0.3f); tone(buf, (RATE * 0.12).toInt(), 659f, 0.5f, 0.3f); tone(buf, (RATE * 0.24).toInt(), 784f, 0.6f, 0.3f) }
            S.CORRECT -> { tone(buf, 0, 660f, 0.2f, 0.35f); tone(buf, (RATE * 0.1).toInt(), 990f, 0.3f, 0.35f) }
            S.WRONG -> { tone(buf, 0, 196f, 0.3f, 0.4f); tone(buf, 0, 207f, 0.3f, 0.3f) }
        }
        return ShortArray(buf.size) { (buf[it].coerceIn(-1f, 1f) * 24000).toInt().toShort() }
    }

    fun play(s: S) {
        if (!enabled) return
        runCatching {
            val t = tracks.getOrPut(s) {
                val pcm = synth(s)
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                    .also { it.write(pcm, 0, pcm.size) }
            }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    /** SAN'a göre doğru ses */
    fun forSan(san: String?) {
        when {
            san == null -> {}
            san.contains('#') -> play(S.END)
            san.contains('+') -> play(S.CHECK)
            san.startsWith("O-O") -> play(S.CASTLE)
            san.contains('x') -> play(S.CAPTURE)
            else -> play(S.MOVE)
        }
    }
}
