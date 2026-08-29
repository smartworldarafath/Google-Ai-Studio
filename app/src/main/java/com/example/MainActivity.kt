package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.IntroSplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.webview.WebViewConfig
import com.example.ui.webview.WebViewScreen
import com.example.update.AppUpdateManager

enum class AppScreen {
    SPLASH,
    WEBVIEW,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable 120Hz display refresh rate for buttery smooth 120 FPS UI & zero frame drops
        enable120HzRefreshRate()

        // Enable and configure persistent cookie and session retention
        enablePersistentStorage()

        updateManager = AppUpdateManager(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Request runtime permissions on launch (Notifications & Storage/Media)
                    PermissionRequester()

                    MainAppNavHost(
                        updateManager = updateManager,
                        onClearCache = { clearAppCache() },
                        onClearCookies = { clearAppCookies() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Flush all cookies to disk so login state persists across recent app swipes
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        // Save session state to persistent disk storage
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    private fun enablePersistentStorage() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.flush()
        } catch (_: Exception) {}
    }

    private fun enable120HzRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    windowManager.defaultDisplay
                }
                val modes = display?.supportedModes
                var maxRate = 60f
                var bestModeId = 0
                modes?.forEach { mode ->
                    if (mode.refreshRate > maxRate) {
                        maxRate = mode.refreshRate
                        bestModeId = mode.modeId
                    }
                }
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = 120f
                    if (bestModeId != 0) {
                        preferredDisplayModeId = bestModeId
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun clearAppCache() {
        try {
            WebStorage.getInstance().deleteAllData()
            val webView = WebView(applicationContext)
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            webView.destroy()
            Toast.makeText(this, "Cache and offline data cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear cache: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAppCookies() {
        try {
            CookieManager.getInstance().removeAllCookies { success ->
                CookieManager.getInstance().flush()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (success) "Session cookies cleared. Signed out." else "Cookies cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear cookies: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PermissionRequester() {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainAppNavHost(
    updateManager: AppUpdateManager,
    onClearCache: () -> Unit,
    onClearCookies: () -> Unit
) {
    var showIntroSplash by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf(AppScreen.WEBVIEW) }
    var isDesktopMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Persistent WebView Screen - Always mounted in memory in the background
        WebViewScreen(
            isDesktopMode = isDesktopMode,
            onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
        )

        // 2. Settings Screen as a smooth sliding overlay
        AnimatedVisibility(
            visible = currentScreen == AppScreen.SETTINGS,
            enter = slideInHorizontally { width -> width } + fadeIn(),
            exit = slideOutHorizontally { width -> width } + fadeOut()
        ) {
            SettingsScreen(
                updateManager = updateManager,
                isDesktopMode = isDesktopMode,
                onToggleDesktopMode = { isDesktopMode = it },
                onClearCache = onClearCache,
                onClearCookies = onClearCookies,
                onNavigateBack = { currentScreen = AppScreen.WEBVIEW }
            )
        }

        // 3. Neon Intro Animation Splash Screen on Startup
        AnimatedVisibility(
            visible = showIntroSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(500))
        ) {
            IntroSplashScreen(
                onAnimationFinished = {
                    showIntroSplash = false
                }
            )
        }
    }
}
