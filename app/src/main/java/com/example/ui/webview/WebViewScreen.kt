package com.example.ui.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    isDesktopMode: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(WebViewConfig.AI_STUDIO_APPS_URL) }
    var pageTitle by remember { mutableStateOf("AI Studio Apps") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(false) }

    // File Chooser Callback State for <input type="file">
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val results: Array<Uri>? = when {
                intent?.data != null -> arrayOf(intent.data!!)
                intent?.clipData != null -> {
                    val clipData = intent.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // Handle Android native back button & back gestures
    BackHandler {
        when {
            showExitDialog -> {
                showExitDialog = false
            }
            isSidebarOpen -> {
                isSidebarOpen = false
                webViewInstance?.evaluateJavascript(
                    """
                    (function() {
                        const backdrop = document.querySelector('mat-drawer-backdrop, .mat-drawer-backdrop, .cdk-overlay-backdrop, .backdrop, [class*="backdrop"]');
                        if (backdrop) { backdrop.click(); return; }
                        const closeBtn = document.querySelector('button[aria-label*="close" i], button[aria-label*="Close" i], button[title*="close" i], .close-button, .drawer-close');
                        if (closeBtn) { closeBtn.click(); return; }
                        const navToggle = document.querySelector('button[aria-label*="menu" i], button[aria-label*="navigation" i], [data-test-id="nav-toggle"]');
                        if (navToggle) { navToggle.click(); return; }
                    })();
                    """.trimIndent(),
                    null
                )
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
            }
            else -> {
                showExitDialog = true
            }
        }
    }

    // Update User Agent if Desktop/Mobile Mode is toggled
    LaunchedEffect(isDesktopMode) {
        webViewInstance?.let { webView ->
            val targetUa = if (isDesktopMode) WebViewConfig.DESKTOP_USER_AGENT else WebViewConfig.MOBILE_USER_AGENT
            webView.settings.userAgentString = targetUa
            webView.reload()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp)
    ) {
        // Full-screen native web view rendering live Google AI Studio Apps directly
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // 1. Enable Hardware Acceleration & Cookie Management
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isNestedScrollingEnabled = true

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this@apply, true)

                    // 2. Configure WebSettings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = WebSettings.LOAD_DEFAULT
                        offscreenPreRaster = true

                        // Custom Chrome Mobile or Desktop UA to ensure Google OAuth sign-in succeeds
                        userAgentString = if (isDesktopMode) {
                            WebViewConfig.DESKTOP_USER_AGENT
                        } else {
                            WebViewConfig.MOBILE_USER_AGENT
                        }
                    }

                    // 3. Javascript Bridge for detecting sidebar drawer state
                    addJavascriptInterface(
                        AndroidNavBridge { isOpen ->
                            isSidebarOpen = isOpen
                        },
                        "AndroidNavBridge"
                    )

                    // 4. Download Listener
                    setDownloadListener(AiStudioDownloadListener(ctx))

                    // 5. WebChromeClient (file uploads & progress)
                    webChromeClient = AiStudioWebChromeClient(
                        onProgressUpdate = { newProgress ->
                            progress = newProgress / 100f
                            isLoading = newProgress < 100
                        },
                        onTitleUpdate = { title ->
                            pageTitle = title
                        },
                        onFileChooseRequest = { callback, params ->
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = callback

                            val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            try {
                                filePickerLauncher.launch(intent)
                                true
                            } catch (e: Exception) {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = null
                                false
                            }
                        }
                    )

                    // 5. WebViewClient (routing & errors)
                    webViewClient = AiStudioWebViewClient(
                        context = ctx,
                        onPageStartedCallback = { url ->
                            currentUrl = url
                            isLoading = true
                            hasError = false
                        },
                        onPageFinishedCallback = { url ->
                            currentUrl = url
                            isLoading = false
                        },
                        onErrorCallback = { error, isMainFrame ->
                            if (isMainFrame) {
                                hasError = true
                                errorMessage = error
                                isLoading = false
                            }
                        }
                    )

                    // Load the entry URL
                    loadUrl(WebViewConfig.AI_STUDIO_APPS_URL)
                    webViewInstance = this
                }
            },
            update = { webView ->
                // Keep instance reference updated
                webViewInstance = webView
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("aistudio_webview")
        )

        // Slim top progress bar (visible only during loading)
        AnimatedVisibility(
            visible = isLoading && progress < 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("webview_loading_indicator"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        // Floating Settings Button - Only visible when the left drawer / slider is open
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_settings_button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings & Updates",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // No-Internet / Connection Error Overlay
        if (hasError) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "Connection Error",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Text(
                                text = if (errorMessage.isNotEmpty()) errorMessage else "Unable to load Google AI Studio. Please check your internet connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Settings")
                                }

                                Button(
                                    onClick = {
                                        hasError = false
                                        isLoading = true
                                        webViewInstance?.loadUrl(WebViewConfig.AI_STUDIO_APPS_URL)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("retry_connection_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit AI Studio?") },
            text = { Text("Are you sure you want to close the application?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }

    // Sync cookies when leaving screen or destroying
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }
}
