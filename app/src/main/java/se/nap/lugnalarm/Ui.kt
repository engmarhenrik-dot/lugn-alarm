package se.nap.lugnalarm

import android.content.Context
import android.util.TypedValue
import android.widget.TextView

const val TEXT_COLOR = 0xFFE6E1DC.toInt()
const val DIM_COLOR = 0xFF9AA3AF.toInt()
const val ACCENT_COLOR = 0xFF8FA3C9.toInt()

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.label(text: String, sizeSp: Float = 16f, color: Int = TEXT_COLOR): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
    }

val DAY_NAMES = arrayOf("Mån", "Tis", "Ons", "Tors", "Fre", "Lör", "Sön")

fun daysSummary(days: Int): String = when (days) {
    0 -> "En gång"
    127 -> "Varje dag"
    else -> DAY_NAMES.indices.filter { (days and (1 shl it)) != 0 }.joinToString(" ") { DAY_NAMES[it] }
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms + 59_999) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h tim $m min" else "$m min"
}
