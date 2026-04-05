package com.bgmi.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val GITHUB_OWNER = "surajheadsup"
    private const val GITHUB_REPO = "bgmi-engine"
    private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val PREF_NAME = "update_prefs"
    private const val PREF_SKIP_VERSION = "skip_version"

    data class ReleaseInfo(
        val tagName: String,
        val versionCode: Int,
        val releaseName: String,
        val body: String,
        val apkUrl: String,
        val apkSize: Long
    )

    /**
     * Check for updates on app open. Call from MainActivity.onCreate().
     */
    fun checkOnAppOpen(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val release = fetchLatestRelease() ?: return@launch
                val currentVersionCode = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt()
                        else @Suppress("DEPRECATION") it.versionCode
                    }

                if (release.versionCode <= currentVersionCode) {
                    Log.d(TAG, "App is up to date (current=$currentVersionCode, latest=${release.versionCode})")
                    return@launch
                }

                // Check if user skipped this version
                val skipped = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getInt(PREF_SKIP_VERSION, 0)
                if (skipped == release.versionCode) {
                    Log.d(TAG, "User skipped version ${release.versionCode}")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, release)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}")
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val url = URL(RELEASES_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                return null
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val tagName = json.optString("tag_name", "")
            val releaseName = json.optString("name", tagName)
            val body = json.optString("body", "")

            // Extract version code from tag (e.g., "v4.1.0" → 5, "v5" → 5, or use assets)
            val versionCode = extractVersionCode(tagName)
            if (versionCode <= 0) {
                Log.w(TAG, "Could not parse version code from tag: $tagName")
                return null
            }

            // Find APK asset
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0)
                    break
                }
            }

            if (apkUrl.isEmpty()) {
                Log.w(TAG, "No APK found in release assets")
                return null
            }

            return ReleaseInfo(tagName, versionCode, releaseName, body, apkUrl, apkSize)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Extract version code from tag name.
     * Supports: "v5", "v4.1", "v4.1.0", "5", "4.1.0"
     * Convention: tag "v5" = versionCode 5, "v4.1" = versionCode 41, "v4.1.0" = versionCode 410
     * Or if tag contains a plain number, use that directly.
     */
    private fun extractVersionCode(tag: String): Int {
        val clean = tag.removePrefix("v").removePrefix("V").trim()

        // Try plain integer first (e.g., "5" or "v5")
        clean.toIntOrNull()?.let { return it }

        // Try semver (e.g., "4.1.0" → major*100 + minor*10 + patch)
        val parts = clean.split(".")
        if (parts.isNotEmpty()) {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 100 + minor * 10 + patch
        }

        return 0
    }

    private fun showUpdateDialog(activity: AppCompatActivity, release: ReleaseInfo) {
        val sizeMb = if (release.apkSize > 0) String.format("%.1f MB", release.apkSize / (1024.0 * 1024.0)) else "Unknown size"
        val changelog = release.body.ifBlank { "Bug fixes and improvements" }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Update Available — ${release.tagName}")
            .setMessage("$changelog\n\nSize: $sizeMb")
            .setPositiveButton("Update Now") { _, _ ->
                startDownloadAndInstall(activity, release)
            }
            .setNegativeButton("Later") { d, _ -> d.dismiss() }
            .setNeutralButton("Skip") { _, _ ->
                activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(PREF_SKIP_VERSION, release.versionCode).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun startDownloadAndInstall(activity: AppCompatActivity, release: ReleaseInfo) {
        // Show progress dialog
        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Downloading Update")
            .setMessage("Starting download...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apkFile = downloadApk(activity, release.apkUrl, release.tagName) { progress ->
                    activity.runOnUiThread {
                        progressDialog.setMessage("Downloading... $progress%")
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (apkFile != null) {
                        installApk(activity, apkFile)
                    } else {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Download Failed")
                            .setMessage("Could not download the update. Check your internet connection.")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Download Failed")
                        .setMessage("Error: ${e.message}")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    private fun downloadApk(
        context: Context,
        apkUrl: String,
        tag: String,
        onProgress: (Int) -> Unit
    ): File? {
        val updatesDir = File(context.getExternalFilesDir(null), "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        // Clean old APKs
        updatesDir.listFiles()?.forEach { it.delete() }

        val apkFile = File(updatesDir, "bgmi-engine-$tag.apk")

        val url = URL(apkUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000

        try {
            val totalSize = conn.contentLength.toLong()
            var downloaded = 0L

            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (totalSize > 0) {
                            val progress = (downloaded * 100 / totalSize).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }

            Log.d(TAG, "APK downloaded: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            return apkFile
        } catch (e: Exception) {
            Log.e(TAG, "APK download error: ${e.message}")
            apkFile.delete()
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
            MaterialAlertDialogBuilder(activity)
                .setTitle("Install Failed")
                .setMessage("Could not install the update: ${e.message}")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
        }
    }
}
