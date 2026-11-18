package com.example.system

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.MyraSettingsRepository
import com.example.voice.MyraVoiceService

class MyraBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val shouldStayLive = MyraSettingsRepository.isAlwaysLiveSync(context)
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (shouldStayLive && hasMic) {
                runCatching {
                    MyraVoiceService.start(context, MyraVoiceService.ACTION_GO_LIVE)
                }
            }
            MyraHomeWidgetProvider.updateAllWidgets(context)
        }
    }
}
