package se.nap.lugnalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var minutesField: EditText
    private lateinit var timerIdle: LinearLayout
    private lateinit var timerRun: LinearLayout
    private lateinit var timerLeft: TextView
    private lateinit var alarmList: LinearLayout
    private lateinit var fsiBanner: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        renderAlarms()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fsiBanner.visibility =
            if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) TextView.VISIBLE else TextView.GONE
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        col.addView(label("Lugn alarm", 30f))

        fsiBanner = label("Alarmet kan inte visas över låsskärmen. Tryck här för att tillåta.", 14f, 0xFFE0B070.toInt()).apply {
            setPadding(0, dp(12), 0, dp(4))
            visibility = TextView.GONE
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                }
            }
        }
        col.addView(fsiBanner)

        // ---- Tupplur ----
        col.addView(label("Tupplur", 20f, ACCENT_COLOR).apply { setPadding(0, dp(24), 0, dp(8)) })

        timerIdle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        minutesField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Store.lastMinutes(this@MainActivity))
            setTextColor(TEXT_COLOR)
            gravity = Gravity.CENTER
            textSize = 24f
        }
        listOf(10, 20, 30, 45, 60).forEach { m ->
            chips.addView(Button(this).apply {
                text = "$m"
                setOnClickListener { minutesField.setText("$m") }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val startRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        startRow.addView(minutesField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        startRow.addView(label("min", 16f, DIM_COLOR).apply { setPadding(dp(8), 0, dp(16), 0) })
        startRow.addView(Button(this).apply {
            text = "Starta"
            setOnClickListener { startTimer() }
        })
        timerIdle.addView(chips)
        timerIdle.addView(startRow)
        col.addView(timerIdle)

        timerRun = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        timerLeft = label("", 44f).apply { gravity = Gravity.CENTER }
        timerRun.addView(timerLeft)
        timerRun.addView(Button(this).apply {
            text = "Avbryt tupplur"
            setOnClickListener {
                Scheduler.cancelTimer(this@MainActivity)
                updateTimer()
            }
        })
        col.addView(timerRun)

        // ---- Alarm ----
        col.addView(label("Alarm", 20f, ACCENT_COLOR).apply { setPadding(0, dp(32), 0, dp(8)) })
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(alarmList)
        col.addView(Button(this).apply {
            text = "+ Nytt alarm"
            setOnClickListener { editAlarm(null) }
        })

        col.addView(Button(this).apply {
            text = "Ljud och stegring"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(32) })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    // ---------- Timer ----------

    private fun startTimer() {
        val txt = minutesField.text.toString().replace(',', '.')
        val minutes = txt.toDoubleOrNull()
        if (minutes == null || minutes <= 0.0) {
            Toast.makeText(this, "Ange antal minuter", Toast.LENGTH_SHORT).show()
            return
        }
        Store.setLastMinutes(this, txt)
        Scheduler.startTimer(this, minutes)
        updateTimer()
    }

    private fun updateTimer() {
        val end = Store.timerEnd(this)
        val left = end - System.currentTimeMillis()
        if (end > 0 && left > 0) {
            val s = (left + 999) / 1000
            timerLeft.text = "%d:%02d".format(s / 60, s % 60)
            timerIdle.visibility = LinearLayout.GONE
            timerRun.visibility = LinearLayout.VISIBLE
        } else {
            timerIdle.visibility = LinearLayout.VISIBLE
            timerRun.visibility = LinearLayout.GONE
        }
    }

    // ---------- Alarm ----------

    private fun renderAlarms() {
        alarmList.removeAllViews()
        Store.alarms(this).forEach { a -> alarmList.addView(alarmRow(a)) }
    }

    private fun alarmRow(a: Alarm): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setOnClickListener { editAlarm(a) }
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label("%02d:%02d".format(a.hour, a.minute), 36f))
        info.addView(label(daysSummary(a.days), 14f, DIM_COLOR))
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(Switch(this).apply {
            isChecked = a.enabled
            setOnCheckedChangeListener { _, on -> toggleAlarm(a, on) }
        })
        row.addView(Button(this).apply {
            text = "✕"
            setOnClickListener {
                Scheduler.cancel(this@MainActivity, a)
                Store.saveAlarms(this@MainActivity, Store.alarms(this@MainActivity).filter { it.id != a.id })
                renderAlarms()
            }
        })
        return row
    }

    private fun toggleAlarm(a: Alarm, on: Boolean) {
        val list = Store.alarms(this).map { if (it.id == a.id) it.copy(enabled = on) else it }
        Store.saveAlarms(this, list)
        val updated = a.copy(enabled = on)
        if (on) {
            val at = Scheduler.schedule(this, updated)
            Toast.makeText(this, "Ringer om ${formatDuration(at - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
        } else {
            Scheduler.cancel(this, updated)
        }
    }

    private fun editAlarm(existing: Alarm?) {
        TimePickerDialog(this, { _, hh, mm -> pickDays(existing, hh, mm) },
            existing?.hour ?: 7, existing?.minute ?: 0, true).show()
    }

    private fun pickDays(existing: Alarm?, hh: Int, mm: Int) {
        val checked = BooleanArray(7) { existing != null && ((existing.days shr it) and 1) == 1 }
        AlertDialog.Builder(this)
            .setTitle("Upprepa (inget valt = en gång)")
            .setMultiChoiceItems(DAY_NAMES, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Spara") { _, _ ->
                var d = 0
                checked.forEachIndexed { i, c -> if (c) d = d or (1 shl i) }
                val a = Alarm(existing?.id ?: Store.nextId(this), hh, mm, d, true)
                val list = Store.alarms(this).filter { it.id != a.id } + a
                Store.saveAlarms(this, list.sortedWith(compareBy({ it.hour }, { it.minute })))
                val at = Scheduler.schedule(this, a)
                Toast.makeText(this, "Ringer om ${formatDuration(at - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
                renderAlarms()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }
}
