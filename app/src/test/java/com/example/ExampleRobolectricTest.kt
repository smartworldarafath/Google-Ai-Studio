package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("AI Studio", appName)
  }

  @Test
  fun `verify app update manager initial state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val updateManager = com.example.update.AppUpdateManager(context)
    assertEquals("1.0.0", updateManager.currentVersionName)
    assertEquals(1, updateManager.currentVersionCode)
  }
}
