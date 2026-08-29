package com.example.update

import java.io.File

/**
 * Data model representing remote update metadata fetched from UPDATE_CONFIG_URL.
 *
 * Expected JSON shape:
 * {
 *   "latest_version_code": 2,
 *   "latest_version_name": "1.1.0",
 *   "apk_url": "https://example.com/releases/app-v1.1.0.apk",
 *   "changelog": "Bug fixes and performance improvements"
 * }
 */
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val changelog: String
)

/**
 * Sealed class representing all possible UI states of the App Updates section.
 */
sealed class UpdateUiState {
    /** Initial state or when no check is running */
    object Idle : UpdateUiState()

    /** Actively fetching update metadata from remote endpoint */
    object Checking : UpdateUiState()

    /** App is on the latest available version */
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val lastCheckedTimeMillis: Long = System.currentTimeMillis()
    ) : UpdateUiState()

    /** A newer version is available on the server, not yet downloaded */
    data class UpdateAvailable(
        val updateInfo: UpdateInfo,
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : UpdateUiState()

    /** APK is currently downloading with real-time percentage progress */
    data class Downloading(
        val updateInfo: UpdateInfo,
        val progressPercent: Int,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : UpdateUiState()

    /** APK is already downloaded on disk and ready for installation */
    data class ReadyToInstall(
        val updateInfo: UpdateInfo,
        val apkFile: File
    ) : UpdateUiState()

    /** An error occurred during check or download */
    data class Error(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : UpdateUiState()
}
