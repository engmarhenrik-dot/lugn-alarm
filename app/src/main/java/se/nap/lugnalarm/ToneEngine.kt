package se.nap.lugnalarm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Syntetiserar ett lugnt, mörkt dron-ljud i realtid (inga ljudfiler behövs).
 * Flera sinustoner (grundton, kvint, oktav, moll-ters) med övertoner svävar
 * långsamt in och ut. Volymen styrs av [Ramp] i mjukvara, så "procent" betyder
 * procent av den volym som alarmströmmen har.
 */
class ToneEngine(
    private val tone: Tone,
    private val ramp: Ramp,
    private val durationOverrideSec: Double? = null
) {
    private val sr = 22050

    @Volatile private var stopping = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start() {
        if (track != null) return
        val minBuf = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf * 2, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        stopping = false
        t.play()
        thread = Thread { render(t) }.apply { isDaemon = true; start() }
    }

    fun stop() {
        val t = track ?: return
        stopping = true
        try {
            thread?.join(1000)
        } catch (_: InterruptedException) {
        }
        try {
            t.pause()
            t.flush()
        } catch (_: Exception) {
        }
        t.release()
        track = null
        thread = null
    }

    private fun gain(tSec: Double): Double = ramp.gainAt(tSec, durationOverrideSec).toDouble()

    private fun toShort(x: Double): Short =
        (x * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

    private fun render(t: AudioTrack) {
        val frames = 1024
        val buf = ShortArray(frames * 2)
        val twoPi = 2.0 * PI

        // Röster: undertoner, grundton, kvint, oktav, moll-ters över oktaven.
        val ratios = doubleArrayOf(0.5, 1.0, 1.5, 2.0, 2.4)
        val vAmp = doubleArrayOf(0.5, 1.0, 0.7, 0.6, 0.22)
        val lfoRate = doubleArrayOf(0.050, 0.070, 0.090, 0.060, 0.110) // Hz, långsamt "andande"
        val b = tone.brightness / 100.0
        val hAmp = doubleArrayOf(1.0, 0.9 * b, 0.5 * b) // överton 1, 2, 3
        val nV = ratios.size
        val lfoPh = DoubleArray(nV) { it * 1.3 }
        val pl = Array(nV) { DoubleArray(3) }
        val pr = Array(nV) { DoubleArray(3) }
        val detune = 0.002 // liten skillnad mellan vänster/höger ger bredd

        var fade = 1.0
        val fadeStep = 1.0 / (0.25 * sr)
        var n = 0L
        var g0 = gain(0.0)

        while (true) {
            val g1 = gain((n + frames).toDouble() / sr)
            for (i in 0 until frames) {
                val g = g0 + (g1 - g0) * i / frames
                var l = 0.0
                var r = 0.0
                for (v in 0 until nV) {
                    val lfo = 0.65 + 0.35 * sin(lfoPh[v])
                    lfoPh[v] += twoPi * lfoRate[v] / sr
                    for (h in 0 until 3) {
                        val f = tone.rootHz * ratios[v] * (h + 1)
                        val a = vAmp[v] * hAmp[h] * lfo
                        pl[v][h] += twoPi * f * (1 - detune) / sr
                        pr[v][h] += twoPi * f * (1 + detune) / sr
                        if (pl[v][h] > twoPi) pl[v][h] -= twoPi
                        if (pr[v][h] > twoPi) pr[v][h] -= twoPi
                        l += a * sin(pl[v][h])
                        r += a * sin(pr[v][h])
                    }
                }
                if (stopping) fade -= fadeStep
                val k = 0.15 * g * max(fade, 0.0)
                buf[2 * i] = toShort(l * k)
                buf[2 * i + 1] = toShort(r * k)
            }
            val written = t.write(buf, 0, buf.size)
            if (written < 0) break
            if (stopping && fade <= 0.0) break
            g0 = g1
            n += frames
        }
    }
}
