package com.example.ui.webview

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.util.CustomTabsHelper

/**
 * Javascript Interface to communicate sidebar drawer state from the web page to Compose.
 */
class AndroidNavBridge(
    private val onSidebarToggled: (Boolean) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onSidebarStateChanged(isOpen: Boolean) {
        onSidebarToggled(isOpen)
    }
}

/**
 * WebChromeClient that handles file upload dialogs, web progress updates, and console logs.
 */
class AiStudioWebChromeClient(
    private val onProgressUpdate: (Int) -> Unit,
    private val onTitleUpdate: (String) -> Unit,
    private val onFileChooseRequest: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressUpdate(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleUpdate(it) }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return onFileChooseRequest(filePathCallback, fileChooserParams)
    }
}

/**
 * WebViewClient that manages link navigation, error handling, and cookie sync.
 */
class AiStudioWebViewClient(
    private val context: Context,
    private val onPageStartedCallback: (String) -> Unit,
    private val onPageFinishedCallback: (String) -> Unit,
    private val onErrorCallback: (String, Boolean) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "AiStudioWebViewClient"
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        // Handle external custom schemes (tel:, mailto:, intent:)
        if (url.startsWith("mailto:") || url.startsWith("tel:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (_: Exception) {}
            return true
        }

        if (url.startsWith("intent:")) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
            return true
        }

        // Check if URL is within the AI Studio or Google Auth ecosystem
        if (WebViewConfig.isInternalUrl(url)) {
            // Keep inside the WebView for full app immersion & cookie continuity
            return false
        }

        // Open external links (e.g. documentation, help pages, external links) in Custom Tabs
        CustomTabsHelper.openUrl(context, url)
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        url?.let { onPageStartedCallback(it) }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Ensure cookies and local storage are persisted to disk
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        url?.let { onPageFinishedCallback(it) }

        // Inject script to detect when the left navigation drawer/slider opens
        val jsScript = """
            (function() {
                if (window.__aiStudioDrawerObserverAttached) return;
                window.__aiStudioDrawerObserverAttached = true;

                function checkSidebarOpen() {
                    try {
                        // 1. Check backdrop/scrim presence
                        const backdrops = document.querySelectorAll('mat-drawer-backdrop, .mat-drawer-backdrop, .cdk-overlay-backdrop, .backdrop, [class*="backdrop"]');
                        for (let b of backdrops) {
                            const style = window.getComputedStyle(b);
                            if (style.display !== 'none' && style.visibility !== 'hidden' && (b.classList.contains('mat-drawer-shown') || parseFloat(style.opacity || '0') > 0.05)) {
                                return true;
                            }
                        }

                        // 2. Check drawer/sidenav classes and visibility
                        const drawers = document.querySelectorAll('mat-sidenav, mat-drawer, aside, nav, ms-side-nav, [role="navigation"], .mat-drawer, .mat-sidenav, .sidebar, .side-nav, .drawer');
                        for (let d of drawers) {
                            if (d.classList.contains('mat-drawer-opened') || 
                                d.classList.contains('mat-sidenav-opened') || 
                                d.classList.contains('opened') || 
                                d.classList.contains('expanded') || 
                                d.classList.contains('is-open') ||
                                d.getAttribute('aria-expanded') === 'true') {
                                return true;
                            }
                            const rect = d.getBoundingClientRect();
                            const style = window.getComputedStyle(d);
                            if (rect.width > 120 && rect.left >= -10 && rect.right > 100 && style.display !== 'none' && style.visibility !== 'hidden') {
                                if (style.position === 'fixed' || style.position === 'absolute' || parseInt(style.zIndex || '0') > 5) {
                                    return true;
                                }
                            }
                        }

                        // 3. Check document classes
                        if (document.body.classList.contains('nav-open') || document.body.classList.contains('drawer-open') || document.documentElement.classList.contains('drawer-open')) {
                            return true;
                        }

                        return false;
                    } catch (e) {
                        return false;
                    }
                }

                let lastState = null;
                function notifyState() {
                    const isOpen = checkSidebarOpen();
                    if (isOpen !== lastState) {
                        lastState = isOpen;
                        if (window.AndroidNavBridge && window.AndroidNavBridge.onSidebarStateChanged) {
                            window.AndroidNavBridge.onSidebarStateChanged(isOpen);
                        }
                    }
                }

                // Observer for DOM alterations
                const observer = new MutationObserver(function() {
                    notifyState();
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class', 'style', 'aria-expanded', 'aria-hidden', 'hidden']
                });

                // Listen to clicks anywhere to detect drawer open/close taps
                document.addEventListener('click', function() {
                    setTimeout(notifyState, 50);
                    setTimeout(notifyState, 150);
                    setTimeout(notifyState, 350);
                    setTimeout(notifyState, 600);
                }, true);

                setTimeout(notifyState, 100);
                setTimeout(notifyState, 500);
            })();
        """.trimIndent()
        view?.evaluateJavascript(jsScript, null)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Only trigger full-screen error if the main frame failed to load
        if (request?.isForMainFrame == true) {
            val description = error?.description?.toString() ?: "Network connection error"
            Log.e(TAG, "Main frame load error: $description (code: ${error?.errorCode})")
            onErrorCallback(description, true)
        }
    }
}

/**
 * DownloadListener that delegates file downloads to Android's DownloadManager.
 */
class AiStudioDownloadListener(private val context: Context) : DownloadListener {
    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrEmpty()) return

        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val cookies = CookieManager.getInstance().getCookie(url)

            if (!cookies.isNullOrEmpty()) {
                request.addRequestHeader("Cookie", cookies)
            }
            if (!userAgent.isNullOrEmpty()) {
                request.addRequestHeader("User-Agent", userAgent)
            }

            request.setTitle(filename)
            request.setDescription("Downloading file from AI Studio...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(request)

            Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AiStudioDownload", "Failed to enqueue download: ${e.message}", e)
            Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
