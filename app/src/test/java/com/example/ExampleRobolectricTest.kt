package com.example

import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.MyraSettingsRepository
import com.example.security.BiometricCapabilityStatus
import com.example.security.MyraBiometricAuthenticator
import com.example.system.MyraHomeWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MYRA AI", appName)
  }

  @Test
  fun `always live flag persists on and widget off clears it permanently`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    MyraSettingsRepository.setAlwaysLiveSync(context, true)
    assertTrue(MyraSettingsRepository.isAlwaysLiveSync(context))

    val offIntent = Intent(context, MyraHomeWidgetProvider::class.java).apply {
      action = MyraHomeWidgetProvider.ACTION_WIDGET_TURN_OFF
    }
    MyraHomeWidgetProvider().onReceive(context, offIntent)
    assertFalse(MyraSettingsRepository.isAlwaysLiveSync(context))
  }

  @Test
  fun `biometric capability maps status codes accurately`() {
    val readyStrong = MyraBiometricAuthenticator.mapCapability(
      BiometricManager.BIOMETRIC_SUCCESS,
      strongSupported = true
    )
    assertTrue(readyStrong is BiometricCapabilityStatus.Ready)
    assertTrue(readyStrong.isUsable)

    val noneEnrolled = MyraBiometricAuthenticator.mapCapability(
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    )
    assertEquals(BiometricCapabilityStatus.NoneEnrolled, noneEnrolled)
    assertFalse(noneEnrolled.isUsable)

    val hwUnavailable = MyraBiometricAuthenticator.mapCapability(
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    )
    assertEquals(BiometricCapabilityStatus.HardwareUnavailable, hwUnavailable)
  }

  @Test
  fun `biometric settings flags persist in DataStore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = MyraSettingsRepository(context)
    repo.updateBiometricLock(true)
    repo.updateBiometricOnAppLaunch(true)
    repo.updateBiometricForSensitiveActions(false)

    val settings = repo.settingsFlow.first()
    assertTrue(settings.biometricLockEnabled)
    assertTrue(settings.biometricRequireOnAppLaunch)
    assertFalse(settings.biometricRequireForSensitiveActions)
  }

  @Test
  fun `custom wake word settings persist in DataStore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = MyraSettingsRepository(context)
    repo.updateCustomWakeWord("Hey Nova")
    repo.updateStandbyWakeWordOnly(true)
    repo.updateWakeWordSensitivity(0.72f)

    val settings = repo.settingsFlow.first()
    assertEquals("Hey Nova", settings.customWakeWord)
    assertTrue(settings.standbyWakeWordOnly)
    assertEquals(0.72f, settings.wakeWordSensitivity, 0.01f)
  }
}
