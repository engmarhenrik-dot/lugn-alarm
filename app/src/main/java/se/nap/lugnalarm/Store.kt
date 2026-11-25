package se.nap.lugnalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stegring: start- och slutvolym i procent, tid i sekunder, kurvform. */
data class Ramp(val startPct: Int, val endPct: Int, val durationSec: Int, val curve: Int) {
    /** Volym 0..1 efter [tSec] sekunder. Efter stegringen hålls slutvolymen. */
    fun gainAt(tSec: Double, durationOverrideSec: Double? = null): Float {
        val d = max1(durationOverrideSec ?: durationSec.toDouble())
        val x = (tSec / d).coerceIn(0.0, 1.0)
        val s = when (curve) {
            0 -> x                       // linjär
            1 -> x * x                   // mjuk start: långsam i början
            else -> x * x * (3 - 2 * x)  // S-kurva: mjuk i båda ändar
        }
        return ((startPct + (endPct - startPct) * s) / 100.0).toFloat()
    }

    private fun max1(v: Double) = if (v < 1.0) 1.0 else v

    companion object {
        val CURVE_NAMES = arrayOf("Linjär", "Mjuk start (långsam i början)", "S-kurva (mjuk i båda ändar)")
    }
}

/** Grundton i Hz (djup = låg) och hur mycket övertoner som hörs (0-100). */
data class Tone(val rootHz: Float, val brightness: Int)

/** days: bitmask, bit 0 = måndag ... bit 6 = söndag. 0 = en gång. */
data class Alarm(val id: Int, val hour: Int, val minute: Int, val days: Int, val enabled: Boolean)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("lugn", Context.MODE_PRIVATE)

    fun tone(c: Context): Tone = p(c).let { Tone(it.getFloat("root", 73.4f), it.getInt("bright", 50)) }

    fun setTone(c: Context, t: Tone) {
        p(c).edit().putFloat("root", t.rootHz).putInt("bright", t.brightness).apply()
    }

    fun defaultRamp(kind: String) = if (kind == "timer") Ramp(3, 50, 300, 2) else Ramp(5, 70, 600, 2)

    fun ramp(c: Context, kind: String): Ramp {
        val d = defaultRamp(kind)
        val s = p(c)
        return Ramp(
            s.getInt("${kind}_start", d.startPct),
            s.getInt("${kind}_end", d.endPct),
            s.getInt("${kind}_dur", d.durationSec),
            s.getInt("${kind}_curve", d.curve)
        )
    }

    fun setRamp(c: Context, kind: String, r: Ramp) {
        p(c).edit()
            .putInt("${kind}_start", r.startPct)
            .putInt("${kind}_end", r.endPct)
            .putInt("${kind}_dur", r.durationSec)
            .putInt("${kind}_curve", r.curve)
            .apply()
    }

    fun maxVolume(c: Context) = p(c).getBoolean("maxvol", true)
    fun setMaxVolume(c: Context, v: Boolean) = p(c).edit().putBoolean("maxvol", v).apply()

    fun timerEnd(c: Context) = p(c).getLong("timer_end", 0L)
    fun setTimerEnd(c: Context, t: Long) = p(c).edit().putLong("timer_end", t).apply()

    fun lastMinutes(c: Context): String = p(c).getString("last_min", "20") ?: "20"
    fun setLastMinutes(c: Context, s: String) = p(c).edit().putString("last_min", s).apply()

    fun alarms(c: Context): List<Alarm> {
        val arr = JSONArray(p(c).getString("alarms", "[]"))
        val out = ArrayList<Alarm>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Alarm(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.getBoolean("e"))
            )
        }
        return out
    }

    fun saveAlarms(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute)
                    .put("d", it.days).put("e", it.enabled)
            )
        }
        p(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun nextId(c: Context): Int {
        val id = p(c).getInt("next_id", 1)
        p(c).edit().putInt("next_id", id + 1).apply()
        return id
    }
}
