package se.nap.lugnalarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock

class RingActivity : Activity() {
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val kind = intent.getStringExtra("kind") ?: Scheduler.KIND_ALARM
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 72f
            setTextColor(TEXT_COLOR)
            gravity = Gravity.CENTER
        })
        root.addView(
            label(if (kind == Scheduler.KIND_TIMER) "Tupluren är slut" else "God morgon", 20f, DIM_COLOR).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(48))
            }
        )
        root.addView(Button(this).apply {
            text = "Stäng av"
            textSize = 22f
            setOnClickListener { stopRinging() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)))
        setContentView(root)

        val filter = IntentFilter(RingService.ACTION_FINISHED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(finishReceiver, filter)
        }
    }

    private fun stopRinging() {
        startService(Intent(this, RingService::class.java).setAction(RingService.ACTION_STOP))
        finish()
    }

    override fun onDestroy() {
        unregisterReceiver(finishReceiver)
        super.onDestroy()
    }
}
