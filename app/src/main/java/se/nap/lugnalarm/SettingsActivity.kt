package se.nap.lugnalarm

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private class ProfileViews(
        val start: EditText,
        val end: EditText,
        val minutes: EditText,
        val curve: Spinner,
        val previewBtn: Button
    )

    private val profiles = LinkedHashMap<String, ProfileViews>()
    private lateinit var rootSeek: SeekBar
    private lateinit var rootLabel: TextView
    private lateinit var brightSeek: SeekBar
    private lateinit var brightLabel: TextView
    private lateinit var maxVol: CheckBox

    private var preview: ToneEngine? = null
    private var previewKind: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        col.addView(label("Ljud och stegring", 28f))

        // ---- Ton ----
        val tone = Store.tone(this)
        col.addView(section("Ton"))
        rootLabel = label("", 15f, DIM_COLOR)
        rootSeek = SeekBar(this).apply {
            max = 110
            progress = (tone.rootHz - 55f).roundToInt().coerceIn(0, 110)
            setOnSeekBarChangeListener(listener { updateToneLabels() })
        }
        brightLabel = label("", 15f, DIM_COLOR)
        brightSeek = SeekBar(this).apply {
            max = 100
            progress = tone.brightness
            setOnSeekBarChangeListener(listener { updateToneLabels() })
        }
        col.addView(rootLabel); col.addView(rootSeek)
        col.addView(brightLabel); col.addView(brightSeek)
        updateToneLabels()

        maxVol = CheckBox(this).apply {
            text = "Sätt alarmvolymen till max medan det ringer (återställs efteråt). Då är procenten nedan procent av telefonens högsta volym."
            setTextColor(TEXT_COLOR)
            isChecked = Store.maxVolume(this@SettingsActivity)
        }
        col.addView(maxVol, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        // ---- Stegring per profil ----
        addProfile(col, Scheduler.KIND_ALARM, "Alarm (väckning)")
        addProfile(col, Scheduler.KIND_TIMER, "Tupplur (timer)")

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun section(title: String) =
        label(title, 20f, ACCENT_COLOR).apply { setPadding(0, dp(28), 0, dp(8)) }

    private fun listener(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = onChange()
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    private fun updateToneLabels() {
        rootLabel.text = "Grundton: ${55 + rootSeek.progress} Hz (lägre = mörkare)"
        brightLabel.text = "Övertoner: ${brightSeek.progress} % (mer hörbart i telefonens högtalare)"
    }

    private fun numberField(value: String, decimal: Boolean) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        setText(value)
        setTextColor(TEXT_COLOR)
        gravity = android.view.Gravity.END
        minEms = 4
    }

    private fun row(parent: LinearLayout, text: String, field: View) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        r.addView(label(text, 16f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(r)
    }

    private fun addProfile(parent: LinearLayout, kind: String, title: String) {
        val r = Store.ramp(this, kind)
        parent.addView(section(title))

        val start = numberField(r.startPct.toString(), false)
        val end = numberField(r.endPct.toString(), false)
        val minutes = numberField(trimZero(r.durationSec / 60.0), true)
        val curve = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, Ramp.CURVE_NAMES)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(r.curve.coerceIn(0, Ramp.CURVE_NAMES.size - 1))
        }
        row(parent, "Startvolym (%)", start)
        row(parent, "Slutvolym (%)", end)
        row(parent, "Stegras under (minuter)", minutes)
        parent.addView(label("Kurva", 16f).apply { setPadding(0, dp(8), 0, 0) })
        parent.addView(curve)

        val btn = Button(this)
        btn.text = "Provlyssna (stegrar på 8 sekunder)"
        btn.setOnClickListener { togglePreview(kind) }
        parent.addView(btn)

        profiles[kind] = ProfileViews(start, end, minutes, curve, btn)
    }

    private fun trimZero(v: Double): String {
        val s = "%.2f".format(v).replace(',', '.')
        return s.trimEnd('0').trimEnd('.')
    }

    private fun readTone() = Tone(55f + rootSeek.progress, brightSeek.progress)

    private fun readRamp(kind: String): Ramp {
        val d = Store.defaultRamp(kind)
        val p = profiles.getValue(kind)
        val s = p.start.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: d.startPct
        val e = p.end.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: d.endPct
        val min = p.minutes.text.toString().replace(',', '.').toDoubleOrNull()
        val sec = if (min == null) d.durationSec else (min * 60).roundToInt().coerceIn(5, 7200)
        return Ramp(s, e, sec, p.curve.selectedItemPosition)
    }

    private fun togglePreview(kind: String) {
        val wasKind = previewKind
        stopPreview()
        if (wasKind == kind) return
        preview = ToneEngine(readTone(), readRamp(kind), 8.0).also { it.start() }
        previewKind = kind
        profiles.getValue(kind).previewBtn.text = "Stoppa provlyssning"
        handler.postDelayed({ stopPreview() }, 14_000)
    }

    private fun stopPreview() {
        handler.removeCallbacksAndMessages(null)
        preview?.stop()
        preview = null
        previewKind = null
        profiles.values.forEach { it.previewBtn.text = "Provlyssna (stegrar på 8 sekunder)" }
    }

    override fun onPause() {
        stopPreview()
        Store.setTone(this, readTone())
        Store.setMaxVolume(this, maxVol.isChecked)
        profiles.keys.forEach { Store.setRamp(this, it, readRamp(it)) }
        super.onPause()
    }
}
