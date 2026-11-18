package com.example.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.system.MyraHomeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyraVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusCollectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = MyraSessionController.getInstance(applicationContext)
        val hasMicPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            controller.setToast("Microphone permission required for continuous listening.")
            MyraServiceBridge.setServiceRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_GO_LIVE -> {
                controller.goLiveForever(speakGreeting = true)
            }
            ACTION_WAKE_STANDBY -> {
                controller.startWakeStandbyListening()
            }
            ACTION_LISTEN -> {
                controller.startActiveVoiceSession()
            }
            ACTION_STOP -> {
                controller.stopCurrentInteraction()
            }
            ACTION_TURN_OFF -> {
                controller.turnOffForever()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification(MyraServiceBridge.statusSubtitle.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        MyraServiceBridge.setServiceRunning(true)
        MyraHomeWidgetProvider.updateAllWidgets(applicationContext)

        if (statusCollectorJob == null) {
            statusCollectorJob = serviceScope.launch {
                MyraServiceBridge.statusSubtitle.collectLatest { subtitle ->
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    mgr.notify(NOTIFICATION_ID, buildNotification(subtitle))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        MyraServiceBridge.setServiceRunning(false)
        MyraHomeWidgetProvider.updateAllWidgets(applicationContext)
        statusCollectorJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.myra_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.myra_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val listenPending = serviceActionPendingIntent(ACTION_LISTEN, 101)
        val standbyPending = serviceActionPendingIntent(ACTION_WAKE_STANDBY, 104)
        val offPending = serviceActionPendingIntent(ACTION_TURN_OFF, 103)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("MYRA AI • Hands-Free Voice")
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Listen", listenPending)
            .addAction(0, "Wake Standby", standbyPending)
            .addAction(0, "Turn Off", offPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun serviceActionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, MyraVoiceService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "myra_voice_service_channel"
        const val NOTIFICATION_ID = 4091
        const val ACTION_GO_LIVE = "com.example.myra.ACTION_GO_LIVE"
        const val ACTION_WAKE_STANDBY = "com.example.myra.ACTION_WAKE_STANDBY"
        const val ACTION_LISTEN = "com.example.myra.ACTION_LISTEN"
        const val ACTION_STOP = "com.example.myra.ACTION_STOP"
        const val ACTION_TURN_OFF = "com.example.myra.ACTION_TURN_OFF"

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, MyraVoiceService::class.java).apply {
                if (action != null) this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MyraVoiceService::class.java))
        }
    }
}
