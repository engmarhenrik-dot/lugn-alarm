package se.nap.lugnalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    const val KIND_ALARM = "alarm"
    const val KIND_TIMER = "timer"
    private const val TIMER_CODE = 999999

    fun nextTrigger(a: Alarm, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..8) {
            if (cal.timeInMillis > now) {
                if (a.days == 0) return cal.timeInMillis
                val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // måndag = 0
                if ((a.days and (1 shl dow)) != 0) return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun pi(c: Context, code: Int, kind: String, id: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra("kind", kind)
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** setAlarmClock är exakt, väcker telefonen ur Doze och kräver ingen specialbehörighet. */
    private fun set(c: Context, at: Long, pending: PendingIntent) {
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(c)), pending)
    }

    fun schedule(c: Context, a: Alarm): Long {
        val at = nextTrigger(a)
        set(c, at, pi(c, a.id, KIND_ALARM, a.id))
        return at
    }

    fun cancel(c: Context, a: Alarm) {
        am(c).cancel(pi(c, a.id, KIND_ALARM, a.id))
    }

    fun startTimer(c: Context, minutes: Double) {
        val end = System.currentTimeMillis() + (minutes * 60_000).toLong()
        Store.setTimerEnd(c, end)
        set(c, end, pi(c, TIMER_CODE, KIND_TIMER, 0))
    }

    fun cancelTimer(c: Context) {
        am(c).cancel(pi(c, TIMER_CODE, KIND_TIMER, 0))
        Store.setTimerEnd(c, 0L)
    }

    fun rescheduleAll(c: Context) {
        Store.alarms(c).filter { it.enabled }.forEach { schedule(c, it) }
        val end = Store.timerEnd(c)
        if (end > System.currentTimeMillis()) {
            set(c, end, pi(c, TIMER_CODE, KIND_TIMER, 0))
        }
    }
}
