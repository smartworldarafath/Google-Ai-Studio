package com.example.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Manages in-app update checks, APK streaming downloads with percentage progress,
 * disk persistence, and system package installation via FileProvider.
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"

        /**
         * The remote endpoint URL that returns the update configuration JSON.
         * Replace this with your actual release metadata endpoint.
         *
         * Expected response schema:
         * {
         *   "latest_version_code": 2,
         *   "latest_version_name": "1.1.0",
         *   "apk_url": "https://example.com/releases/app-v1.1.0.apk",
         *   "changelog": "Bug fixes and performance improvements"
         * }
         */
        var UPDATE_CONFIG_URL: String = "https://example.com/app-update.json"

        private const val PREFS_NAME = "aistudio_app_updates"
        private const val KEY_SAVED_VERSION_CODE = "saved_version_code"
        private const val KEY_SAVED_VERSION_NAME = "saved_version_name"
        private const val KEY_SAVED_FILE_PATH = "saved_file_path"
        private const val KEY_SAVED_APK_URL = "saved_apk_url"
        private const val KEY_SAVED_CHANGELOG = "saved_changelog"
        private const val KEY_LAST_CHECKED_TIME = "last_checked_time"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /** Returns current installed app version name (e.g. "1.0.0") */
    val currentVersionName: String
        get() = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

    /** Returns current installed app version code (e.g. 1) */
    val currentVersionCode: Int
        get() = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (packageInfo.longVersionCode and 0xFFFFFFFFL).toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }

    init {
        // Restore downloaded state from SharedPreferences on initialization
        restoreSavedState()
    }

    /**
     * Checks SharedPreferences and disk to see if a previously downloaded APK is already
     * available and ready to install.
     */
    fun restoreSavedState() {
        val savedCode = prefs.getInt(KEY_SAVED_VERSION_CODE, -1)
        val savedName = prefs.getString(KEY_SAVED_VERSION_NAME, null)
        val savedPath = prefs.getString(KEY_SAVED_FILE_PATH, null)
        val savedUrl = prefs.getString(KEY_SAVED_APK_URL, "") ?: ""
        val savedChangelog = prefs.getString(KEY_SAVED_CHANGELOG, "") ?: ""

        if (savedCode > currentVersionCode && savedName != null && savedPath != null) {
            val apkFile = File(savedPath)
            if (apkFile.exists() && apkFile.length() > 0) {
                Log.d(TAG, "Restored previously downloaded APK for version $savedName ($savedCode) at $savedPath")
                val updateInfo = UpdateInfo(
                    latestVersionCode = savedCode,
                    latestVersionName = savedName,
                    apkUrl = savedUrl,
                    changelog = savedChangelog
                )
                _uiState.value = UpdateUiState.ReadyToInstall(updateInfo, apkFile)
                return
            } else {
                // Clear stale preferences if file was removed
                clearSavedUpdate()
            }
        }

        // Default state if nothing downloaded
        if (_uiState.value is UpdateUiState.Idle) {
            val lastChecked = prefs.getLong(KEY_LAST_CHECKED_TIME, 0L)
            if (lastChecked > 0) {
                _uiState.value = UpdateUiState.UpToDate(currentVersionName, currentVersionCode, lastChecked)
            }
        }
    }

    /**
     * Checks remote UPDATE_CONFIG_URL for available updates.
     */
    fun checkForUpdates(customUrl: String? = null) {
        val targetUrl = customUrl ?: UPDATE_CONFIG_URL
        _uiState.value = UpdateUiState.Checking

        scope.launch {
            try {
                val updateInfo = withContext(Dispatchers.IO) {
                    fetchRemoteUpdateInfo(targetUrl)
                }

                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECKED_TIME, now).apply()

                if (updateInfo.latestVersionCode > currentVersionCode) {
                    // Check if this version has already been fully downloaded
                    val savedCode = prefs.getInt(KEY_SAVED_VERSION_CODE, -1)
                    val savedPath = prefs.getString(KEY_SAVED_FILE_PATH, null)

                    if (savedCode == updateInfo.latestVersionCode && savedPath != null) {
                        val file = File(savedPath)
                        if (file.exists() && file.length() > 0) {
                            _uiState.value = UpdateUiState.ReadyToInstall(updateInfo, file)
                            return@launch
                        }
                    }

                    _uiState.value = UpdateUiState.UpdateAvailable(
                        updateInfo = updateInfo,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode
                    )
                } else {
                    // Up to date
                    _uiState.value = UpdateUiState.UpToDate(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        lastCheckedTimeMillis = now
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}", e)
                _uiState.value = UpdateUiState.Error(
                    errorMessage = e.message ?: "Failed to check for updates. Please check network connection."
                )
            }
        }
    }

    /**
     * Simulates an update check for demo / testing purposes when the remote endpoint
     * is not yet live.
     */
    fun simulateDemoUpdate(mockVersionName: String = "1.1.0", mockVersionCode: Int = 2) {
        val demoInfo = UpdateInfo(
            latestVersionCode = mockVersionCode,
            latestVersionName = mockVersionName,
            apkUrl = "https://github.com/google/ai-studio/releases/download/v$mockVersionName/app-release.apk",
            changelog = "• Enhanced AI Studio Apps WebView performance\n• Integrated Google OAuth persistent login\n• In-app APK auto-updater with resume capability\n• Added file upload & download handling"
        )

        // Check if demo file already simulated
        val savedCode = prefs.getInt(KEY_SAVED_VERSION_CODE, -1)
        val savedPath = prefs.getString(KEY_SAVED_FILE_PATH, null)
        if (savedCode == mockVersionCode && savedPath != null) {
            val file = File(savedPath)
            if (file.exists() && file.length() > 0) {
                _uiState.value = UpdateUiState.ReadyToInstall(demoInfo, file)
                return
            }
        }

        _uiState.value = UpdateUiState.UpdateAvailable(
            updateInfo = demoInfo,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode
        )
    }

    /**
     * Downloads the APK with percentage streaming callbacks and persists file info on disk.
     */
    fun downloadUpdate(updateInfo: UpdateInfo) {
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Downloading(
            updateInfo = updateInfo,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = 0L
        )

        downloadJob = scope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    performDownload(updateInfo)
                }

                // Persist downloaded file info to SharedPreferences
                prefs.edit()
                    .putInt(KEY_SAVED_VERSION_CODE, updateInfo.latestVersionCode)
                    .putString(KEY_SAVED_VERSION_NAME, updateInfo.latestVersionName)
                    .putString(KEY_SAVED_FILE_PATH, apkFile.absolutePath)
                    .putString(KEY_SAVED_APK_URL, updateInfo.apkUrl)
                    .putString(KEY_SAVED_CHANGELOG, updateInfo.changelog)
                    .apply()

                _uiState.value = UpdateUiState.ReadyToInstall(updateInfo, apkFile)
            } catch (e: CancellationException) {
                Log.d(TAG, "Download was cancelled by user")
                _uiState.value = UpdateUiState.UpdateAvailable(
                    updateInfo = updateInfo,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _uiState.value = UpdateUiState.Error(
                    errorMessage = "Download failed: ${e.localizedMessage ?: "Unknown network error"}"
                )
            }
        }
    }

    /**
     * Cancels active download in progress.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /**
     * Installs the downloaded APK via FileProvider and Android Package Installer.
     */
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _uiState.value = UpdateUiState.Error("Downloaded APK file not found on disk. Please re-download.")
            return false
        }

        // Check Unknown Apps permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer: ${e.message}", e)
            _uiState.value = UpdateUiState.Error("Failed to launch package installer: ${e.message}")
            return false
        }
    }

    /**
     * Clears all saved update data and removes downloaded APK files.
     */
    fun clearSavedUpdate() {
        val savedPath = prefs.getString(KEY_SAVED_FILE_PATH, null)
        if (savedPath != null) {
            try {
                val file = File(savedPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        prefs.edit()
            .remove(KEY_SAVED_VERSION_CODE)
            .remove(KEY_SAVED_VERSION_NAME)
            .remove(KEY_SAVED_FILE_PATH)
            .remove(KEY_SAVED_APK_URL)
            .remove(KEY_SAVED_CHANGELOG)
            .apply()
    }

    private fun fetchRemoteUpdateInfo(endpointUrl: String): UpdateInfo {
        val request = Request.Builder()
            .url(endpointUrl)
            .header("User-Agent", "AIStudioAndroidWrapper/${currentVersionName}")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Server returned HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response body from update server")

        val json = JSONObject(responseBody)
        return UpdateInfo(
            latestVersionCode = json.getInt("latest_version_code"),
            latestVersionName = json.getString("latest_version_name"),
            apkUrl = json.getString("apk_url"),
            changelog = json.optString("changelog", "Bug fixes and performance improvements")
        )
    }

    private fun performDownload(updateInfo: UpdateInfo): File {
        val updatesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val targetFile = File(updatesDir, "aistudio-update-v${updateInfo.latestVersionName}.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = Request.Builder()
            .url(updateInfo.apkUrl)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to download APK: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty APK file stream")
        val contentLength = body.contentLength()
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            var lastReportedPercent = -1

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                if (contentLength > 0) {
                    val percent = ((totalRead * 100) / contentLength).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        scope.launch(Dispatchers.Main) {
                            _uiState.value = UpdateUiState.Downloading(
                                updateInfo = updateInfo,
                                progressPercent = percent,
                                downloadedBytes = totalRead,
                                totalBytes = contentLength
                            )
                        }
                    }
                }
            }
            outputStream.flush()
            return targetFile
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}
