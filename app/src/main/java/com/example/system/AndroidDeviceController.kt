package com.example.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.data.BatteryTelemetry
import com.example.data.InstalledAppInfo
import com.example.data.PendingCallRequest

sealed interface VoiceIntentParseResult {
    data object NotADeviceCommand : VoiceIntentParseResult
    data class Executed(val actionName: String, val spokenSummaryHi: String, val spokenSummaryEn: String) : VoiceIntentParseResult
    data class NeedsCallConfirmation(val request: PendingCallRequest) : VoiceIntentParseResult
}

class AndroidDeviceController(private val context: Context) {

    private val appContext = context.applicationContext
    private var torchEnabled = false

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == appContext.packageName) return@mapNotNull null
                val label = info.loadLabel(pm).toString().trim()
                InstalledAppInfo(appName = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun launchPackage(packageName: String): Boolean {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun openAppByQuery(query: String): InstalledAppInfo? {
        val lower = query.lowercase().trim()
        val apps = getInstalledLaunchableApps()
        val exact = apps.firstOrNull { it.appName.lowercase() == lower }
        val partial = exact ?: apps.firstOrNull {
            it.appName.lowercase().contains(lower) || lower.contains(it.appName.lowercase())
        }
        if (partial != null && launchPackage(partial.packageName)) {
            return partial
        }
        return null
    }

    fun openCamera(): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun openSettings(): Boolean = startSafely(
        Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    )

    fun openWifiSettings(): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return startSafely(intent)
    }

    fun openBluetoothSettings(): Boolean = startSafely(
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    )

    fun initiateDialConfirmed(phoneNumber: String): Boolean {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun prepareSms(phoneNumber: String = "", body: String = ""): Boolean {
        val uri = Uri.parse("smsto:${phoneNumber.trim()}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (body.isNotBlank()) putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun openWhatsApp(message: String = ""): Boolean {
        val pm = appContext.packageManager
        val waIntent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (waIntent != null && message.isBlank()) {
            waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return startSafely(waIntent)
        }
        val uri = if (message.isNotBlank()) {
            Uri.parse("https://wa.me/?text=${Uri.encode(message)}")
        } else {
            Uri.parse("https://wa.me/")
        }
        return startSafely(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun setAlarm(hour24: Int, minute: Int, label: String = "MYRA Alarm"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun setTimer(seconds: Int, label: String = "MYRA Timer"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(5))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchNavigation(destination: String): Boolean {
        val clean = destination.ifBlank { "Nearby" }
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(clean)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(navIntent)) return true
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(clean)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(geoIntent)
    }

    fun openWebSearch(query: String): Boolean {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(searchIntent)) return true
        val browser = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return startSafely(browser)
    }

    fun toggleFlashlight(): Result<Boolean> {
        return runCatching {
            val camMgr = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camMgr.cameraIdList.firstOrNull { id ->
                val chars = camMgr.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: error("Flashlight hardware unavailable")
            torchEnabled = !torchEnabled
            camMgr.setTorchMode(cameraId, torchEnabled)
            torchEnabled
        }
    }

    fun readBatteryTelemetry(): BatteryTelemetry {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Active"
        }
        return BatteryTelemetry(
            levelPercent = pct,
            isCharging = isCharging,
            statusLabel = statusLabel,
            temperatureCelsius = tempTenths / 10f,
            voltageMillivolts = voltage
        )
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(appContext.packageName)
        } else true
    }

    fun openBatteryOptimizationSettings(): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun pinVoiceHomeShortcut(): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) return false
        val shortcutIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_LAUNCH_VOICE_MODE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val shortcut = ShortcutInfoCompat.Builder(appContext, "myra_voice_shortcut")
            .setShortLabel(appContext.getString(R.string.myra_shortcut_short))
            .setLongLabel(appContext.getString(R.string.myra_shortcut_long))
            .setIcon(IconCompat.createWithResource(appContext, R.mipmap.ic_launcher_round))
            .setIntent(shortcutIntent)
            .build()

        val callbackIntent = ShortcutManagerCompat.createShortcutResultIntent(appContext, shortcut)
        val successCallback = PendingIntent.getBroadcast(
            appContext,
            202,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return ShortcutManagerCompat.requestPinShortcut(appContext, shortcut, successCallback.intentSender)
    }

    fun pinFullBodyHomeWidget(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(appContext, MyraHomeWidgetProvider::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }

    fun tryExecuteNaturalCommand(rawText: String): VoiceIntentParseResult {
        val text = rawText.trim()
        val lower = text.lowercase()

        // 1. Battery
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            val b = readBatteryTelemetry()
            val chargeHi = if (b.isCharging) "और अभी चार्ज हो रही है" else ""
            val chargeEn = if (b.isCharging) "and currently charging" else ""
            return VoiceIntentParseResult.Executed(
                actionName = "Battery Info (${b.levelPercent}%)",
                spokenSummaryHi = "आपके फ़ोन की बैटरी ${b.levelPercent}% है $chargeHi। तापमान ${b.temperatureCelsius}°C है।",
                spokenSummaryEn = "Your phone battery is at ${b.levelPercent}% $chargeEn (${b.temperatureCelsius}°C)."
            )
        }

        // 2. Flashlight / Torch
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैश")) {
            val res = toggleFlashlight()
            return if (res.isSuccess) {
                val on = res.getOrDefault(false)
                VoiceIntentParseResult.Executed(
                    actionName = if (on) "Torch ON" else "Torch OFF",
                    spokenSummaryHi = if (on) "जी, मैंने टॉर्च चालू कर दी है।" else "जी, मैंने टॉर्च बंद कर दी है।",
                    spokenSummaryEn = if (on) "Flashlight turned on." else "Flashlight turned off."
                )
            } else {
                VoiceIntentParseResult.Executed(
                    actionName = "Torch unavailable",
                    spokenSummaryHi = "माफ़ कीजिए, इस डिवाइस पर फ्लैशलाइट उपलब्ध नहीं है।",
                    spokenSummaryEn = "Sorry, flashlight hardware is not available on this device."
                )
            }
        }

        // 3. Camera
        if (lower.contains("open camera") || lower.contains("camera kholo") || lower.contains("कैमरा खोलो") || lower == "camera") {
            val ok = openCamera()
            return VoiceIntentParseResult.Executed(
                actionName = if (ok) "Camera Opened" else "Camera Unavailable",
                spokenSummaryHi = if (ok) "जी, कैमरा खोल रही हूँ।" else "कैमरा ऐप उपलब्ध नहीं मिला।",
                spokenSummaryEn = if (ok) "Opening the camera now." else "Camera app was not found."
            )
        }

        // 4. Wi-Fi / Bluetooth / Settings
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("वाईफाई") || lower.contains("वाई-फाई")) {
            openWifiSettings()
            return VoiceIntentParseResult.Executed(
                actionName = "Wi-Fi Settings",
                spokenSummaryHi = "जी, वाई-फ़ाई सेटिंग्स खोल रही हूँ।",
                spokenSummaryEn = "Opening Wi-Fi settings."
            )
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            openBluetoothSettings()
            return VoiceIntentParseResult.Executed(
                actionName = "Bluetooth Settings",
                spokenSummaryHi = "जी, ब्लूटूथ सेटिंग्स खोल रही हूँ।",
                spokenSummaryEn = "Opening Bluetooth settings."
            )
        }
        if (lower.contains("open settings") || lower.contains("settings kholo") || lower.contains("सेटिंग्स खोलो")) {
            openSettings()
            return VoiceIntentParseResult.Executed(
                actionName = "System Settings",
                spokenSummaryHi = "जी, फ़ोन की सेटिंग्स खोल रही हूँ।",
                spokenSummaryEn = "Opening Android system settings."
            )
        }

        // 5. WhatsApp
        if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप")) {
            openWhatsApp()
            return VoiceIntentParseResult.Executed(
                actionName = "Open WhatsApp",
                spokenSummaryHi = "जी, व्हाट्सएप खोल रही हूँ।",
                spokenSummaryEn = "Opening WhatsApp."
            )
        }

        // 6. Phone Call (Always confirm before dialing!)
        if (lower.startsWith("call ") || lower.contains("कॉल करो") || lower.contains("phone lagao")) {
            val digits = text.filter { it.isDigit() || it == '+' }
            val target = if (digits.length >= 3) digits else text
                .replace("call", "", ignoreCase = true)
                .replace("कॉल करो", "")
                .trim()
                .ifBlank { "1234567890" }
            return VoiceIntentParseResult.NeedsCallConfirmation(
                PendingCallRequest(
                    targetDisplay = target,
                    phoneNumber = if (digits.length >= 3) digits else ""
                )
            )
        }

        // 7. SMS
        if (lower.contains("sms") || lower.contains("send message") || lower.contains("मैसेज भेजो")) {
            prepareSms()
            return VoiceIntentParseResult.Executed(
                actionName = "Prepare SMS",
                spokenSummaryHi = "जी, मैसेज भेजने के लिए SMS ऐप खोल रही हूँ।",
                spokenSummaryEn = "Opening SMS composer."
            )
        }

        // 8. Alarm
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            val nums = Regex("\\d{1,2}").findAll(lower).map { it.value.toInt() }.toList()
            val hour = nums.getOrNull(0)?.coerceIn(0, 23) ?: 7
            val minute = nums.getOrNull(1)?.coerceIn(0, 59) ?: 0
            setAlarm(hour, minute)
            return VoiceIntentParseResult.Executed(
                actionName = "Set Alarm %02d:%02d".format(hour, minute),
                spokenSummaryHi = "जी, %02d:%02d का अलार्म सेट करने के लिए क्लॉक खोल रही हूँ।".format(hour, minute),
                spokenSummaryEn = "Setting an alarm for %02d:%02d.".format(hour, minute)
            )
        }

        // 9. Navigation
        if (lower.startsWith("navigate to ") || lower.contains("रास्ता दिखाओ") || lower.contains("directions to ")) {
            val dest = text
                .replace("navigate to", "", ignoreCase = true)
                .replace("directions to", "", ignoreCase = true)
                .replace("रास्ता दिखाओ", "")
                .trim()
                .ifBlank { "New Delhi" }
            launchNavigation(dest)
            return VoiceIntentParseResult.Executed(
                actionName = "Navigate: $dest",
                spokenSummaryHi = "जी, $dest के लिए नेविगेशन शुरू कर रही हूँ।",
                spokenSummaryEn = "Launching navigation to $dest."
            )
        }

        // 10. Search web
        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.contains("सर्च करो")) {
            val q = text
                .replace("search", "", ignoreCase = true)
                .replace("google", "", ignoreCase = true)
                .replace("सर्च करो", "")
                .trim()
                .ifBlank { text }
            openWebSearch(q)
            return VoiceIntentParseResult.Executed(
                actionName = "Web Search: $q",
                spokenSummaryHi = "जी, \"$q\" के लिए वेब सर्च खोल रही हूँ।",
                spokenSummaryEn = "Searching the web for \"$q\"."
            )
        }

        // 11. Open installed app
        if (lower.startsWith("open ") || lower.contains("खोलो") || lower.contains("kholo")) {
            val appQuery = text
                .replace("open", "", ignoreCase = true)
                .replace("app", "", ignoreCase = true)
                .replace("खोलो", "")
                .replace("kholo", "", ignoreCase = true)
                .trim()
            if (appQuery.isNotBlank()) {
                val launched = openAppByQuery(appQuery)
                if (launched != null) {
                    return VoiceIntentParseResult.Executed(
                        actionName = "Opened ${launched.appName}",
                        spokenSummaryHi = "जी, ${launched.appName} खोल रही हूँ।",
                        spokenSummaryEn = "Opening ${launched.appName}."
                    )
                }
            }
        }

        return VoiceIntentParseResult.NotADeviceCommand
    }

    private fun startSafely(intent: Intent): Boolean {
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val ACTION_LAUNCH_VOICE_MODE = "com.example.myra.ACTION_LAUNCH_VOICE_MODE"
    }
}
