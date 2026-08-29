package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabsHelper {
    /**
     * Opens a URL in Chrome Custom Tabs with standard styling.
     * Falls back to generic ACTION_VIEW intent if Custom Tabs is not available.
     */
    fun openUrl(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Ignore if no browser found
            }
        }
    }
}
