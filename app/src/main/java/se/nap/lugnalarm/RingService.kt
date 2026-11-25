package se.nap.lugnalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class RingService : Service() {
    companion object {
        const val ACTION_STOP = "se.nap.lugnalarm.STOP"
        const val ACTION_FINISHED = "se.nap.lugnalarm.FINISHED"
        private const val CHANNEL = "ring"
        private const val NOTIF_ID = 42
        private const val MAX_RING_MS = 60 * 60 * 1000L
    }

    private var engine: ToneEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var prevVolume = -1
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (engine != null) return START_NOT_STICKY // ringer redan

        val kind = intent?.getStringExtra("kind") ?: Scheduler.KIND_ALARM
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // ljudet kommer från vår egen ljudmotor
                enableVibration(false)
            }
        )

        val ringIntent = Intent(this, RingActivity::class.java)
            .putExtra("kind", kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags2 = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPi = PendingIntent.getActivity(this, 0, ringIntent, flags2)
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, RingService::class.java).setAction(ACTION_STOP), flags2
        )

        val title = if (kind == Scheduler.KIND_TIMER) "Tupplur slut" else "Alarm"
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Tryck för att öppna, eller stäng av")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stäng av", stopPi
                ).build()
            )
            .build()
        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lugnalarm:ring")
            .apply { acquire(MAX_RING_MS + 60_000) }

        if (Store.maxVolume(this)) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            prevVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }

        engine = ToneEngine(Store.tone(this), Store.ramp(this, kind)).also { it.start() }
        handler.postDelayed({ stopSelf() }, MAX_RING_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        engine?.stop()
        engine = null
        if (prevVolume >= 0) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0)
            prevVolume = -1
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
        super.onDestroy()
    }
}
