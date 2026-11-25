package se.nap.lugnalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: Scheduler.KIND_ALARM
        val id = intent.getIntExtra("id", 0)

        if (kind == Scheduler.KIND_ALARM) {
            val list = Store.alarms(context).toMutableList()
            val a = list.firstOrNull { it.id == id } ?: return
            if (!a.enabled) return
            if (a.days == 0) {
                // Engångsalarm: stäng av efter ringning.
                val idx = list.indexOfFirst { it.id == id }
                list[idx] = a.copy(enabled = false)
                Store.saveAlarms(context, list)
            } else {
                Scheduler.schedule(context, a) // nästa veckodag
            }
        } else {
            Store.setTimerEnd(context, 0L)
        }

        context.startForegroundService(
            Intent(context, RingService::class.java).putExtra("kind", kind)
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
    }
}
