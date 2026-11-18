package com.example.system

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AssistantState
import com.example.data.MyraSettingsRepository
import com.example.voice.MyraServiceBridge
import com.example.voice.MyraSessionController
import com.example.voice.MyraVoiceService

class MyraHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_GO_LIVE -> {
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        action = AndroidDeviceController.ACTION_LAUNCH_VOICE_MODE
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(launchIntent)
                } else {
                    MyraSettingsRepository.setAlwaysLiveSync(context, true)
                    MyraVoiceService.start(context, MyraVoiceService.ACTION_GO_LIVE)
                }
                updateAllWidgets(context)
            }

            ACTION_WIDGET_TURN_OFF -> {
                MyraSettingsRepository.setAlwaysLiveSync(context, false)
                MyraSessionController.getIfInitialized()?.turnOffForever()
                    ?: run {
                        MyraVoiceService.stop(context)
                        MyraServiceBridge.setAssistantState(AssistantState.IDLE, "OFF • Tap GO LIVE")
                    }
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_GO_LIVE = "com.example.myra.WIDGET_GO_LIVE"
        const val ACTION_WIDGET_TURN_OFF = "com.example.myra.WIDGET_TURN_OFF"

        fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MyraHomeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context)
            for (id in ids) {
                mgr.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val isLive = MyraSettingsRepository.isAlwaysLiveSync(context) || MyraServiceBridge.serviceRunning.value
            val assistantState = MyraServiceBridge.assistantState.value
            val views = RemoteViews(context.packageName, R.layout.widget_myra_home)

            views.setTextViewText(R.id.tv_widget_status, if (isLive) "LIVE" else "OFF")
            views.setInt(
                R.id.tv_widget_status,
                "setBackgroundResource",
                if (isLive) R.drawable.bg_widget_status_live else R.drawable.bg_widget_status_off
            )
            views.setTextColor(
                R.id.tv_widget_status,
                if (isLive) 0xFF1BE39D.toInt() else 0xFFB8C4E6.toInt()
            )

            val subtitle = when {
                !isLive -> "OFF forever • Tap GO LIVE"
                assistantState == AssistantState.SPEAKING -> "Speaking with you..."
                assistantState == AssistantState.LISTENING -> "Live • Listening..."
                assistantState == AssistantState.THINKING -> "Thinking..."
                else -> "Live Always On • Ready"
            }
            views.setTextViewText(R.id.tv_widget_subtitle, subtitle)

            views.setTextViewText(
                R.id.btn_widget_live,
                if (isLive) "● LIVE ON" else "● GO LIVE"
            )

            // Tap full-body avatar -> open app full-body hub
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                301,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_widget_fullbody, openAppPending)
            views.setOnClickPendingIntent(R.id.tv_widget_title, openAppPending)

            // 1-tap GO LIVE forever
            val liveIntent = Intent(context, MyraHomeWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_GO_LIVE
            }
            val livePending = PendingIntent.getBroadcast(
                context,
                302,
                liveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_live, livePending)

            // 1-tap OFF forever
            val offIntent = Intent(context, MyraHomeWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TURN_OFF
            }
            val offPending = PendingIntent.getBroadcast(
                context,
                303,
                offIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_off, offPending)

            return views
        }
    }
}
