package com.example.ui.webview

object WebViewConfig {
    const val AI_STUDIO_APPS_URL = "https://aistudio.google.com/apps"
    const val AI_STUDIO_HOME_URL = "https://aistudio.google.com/"
    const val GOOGLE_LOGIN_URL = "https://accounts.google.com/"

    /**
     * Desktop Chrome User-Agent:
     * High compatibility with Google sign-in and AI Studio desktop studio interface.
     */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * Mobile Chrome User-Agent:
     * Standard mobile Chrome UA without 'Version/4.0' or '; wv' WebView tags,
     * ensuring Google OAuth sign-in is allowed.
     */
    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /**
     * Determines whether a URL is part of the internal AI Studio or Google Auth ecosystem.
     */
    fun isInternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("aistudio.google.com") ||
                lower.contains("accounts.google.com") ||
                lower.contains("myaccount.google.com") ||
                lower.contains("apis.google.com") ||
                lower.contains("oauth2.googleapis.com") ||
                lower.contains("ssl.gstatic.com") ||
                lower.contains("clients6.google.com") ||
                lower.contains("googleusercontent.com") ||
                lower.contains("content.googleapis.com")
    }
}
