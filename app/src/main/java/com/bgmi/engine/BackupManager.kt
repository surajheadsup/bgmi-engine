package com.bgmi.engine

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_DIR = "bgmi_engine_backup"
    private const val BACKUP_FILE = "bgmi_backup.json"

    /**
     * Returns the backup directory on external storage (survives uninstall).
     */
    private fun getBackupDir(context: Context): File {
        // Use Documents directory — survives app uninstall
        val docs = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        val dir = File(docs, BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getBackupFile(context: Context): File {
        return File(getBackupDir(context), BACKUP_FILE)
    }

    /**
     * Backup all settings, whitelist, theme, and database to JSON.
     */
    fun backup(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ctx = activity.applicationContext
                val json = JSONObject()

                // Metadata
                json.put("backup_version", 1)
                json.put("app_version", getVersionName(ctx))
                json.put("timestamp", System.currentTimeMillis())
                json.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                // Engine preferences
                val prefs = ctx.getSharedPreferences("bgmi_engine_prefs", Context.MODE_PRIVATE)
                val prefsJson = JSONObject()
                for ((key, value) in prefs.all) {
                    when (value) {
                        is Boolean -> prefsJson.put(key, value)
                        is Int -> prefsJson.put(key, value)
                        is Long -> prefsJson.put(key, value)
                        is Float -> prefsJson.put(key, value.toDouble())
                        is String -> prefsJson.put(key, value)
                        is Set<*> -> prefsJson.put(key, JSONArray(value.toList()))
                    }
                }
                json.put("engine_prefs", prefsJson)

                // Theme
                val themePrefs = ctx.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                json.put("theme", themePrefs.getString("theme", "dark") ?: "dark")

                // Whitelist
                val whitelist = prefs.getStringSet("kill_whitelist", emptySet()) ?: emptySet()
                json.put("kill_whitelist", JSONArray(whitelist.toList()))

                // Game sessions (last 100)
                val db = com.bgmi.engine.data.AppDatabase.getInstance(ctx)
                val sessions = db.gameSessionDao().getRecentSessions(100)
                val sessionsJson = JSONArray()
                for (s in sessions) {
                    sessionsJson.put(JSONObject().apply {
                        put("startTime", s.startTime)
                        put("endTime", s.endTime)
                        put("durationMs", s.durationMs)
                        put("avgFps", s.avgFps)
                        put("avgTemp", s.avgTemp)
                        put("peakTemp", s.peakTemp)
                        put("avgCpu", s.avgCpu)
                        put("avgRam", s.avgRam)
                        put("minBattery", s.minBattery)
                        put("startBattery", s.startBattery)
                        put("endBattery", s.endBattery)
                        put("thermalHits", s.thermalHits)
                        put("modeChanges", s.modeChanges)
                        put("stabilizerRuns", s.stabilizerRuns)
                        put("performanceScore", s.performanceScore)
                        put("dominantMode", s.dominantMode)
                    })
                }
                json.put("sessions", sessionsJson)

                // Write to file
                val file = getBackupFile(ctx)
                file.writeText(json.toString(2))

                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Backup Complete")
                        .setMessage("Saved to:\n${file.absolutePath}\n\nIncludes: settings, whitelist, theme, ${sessions.size} sessions.\n\nThis file survives app uninstall.")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }

                Log.d(TAG, "Backup saved to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Backup Failed")
                        .setMessage("Error: ${e.message}")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    /**
     * Restore settings, whitelist, theme, and sessions from backup.
     */
    fun restore(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ctx = activity.applicationContext
                val file = getBackupFile(ctx)

                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("No Backup Found")
                            .setMessage("No backup file found at:\n${file.absolutePath}")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                    return@launch
                }

                val json = JSONObject(file.readText())
                val backupDate = json.optString("date", "Unknown")
                val backupVersion = json.optString("app_version", "?")
                val sessionCount = json.optJSONArray("sessions")?.length() ?: 0

                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Restore Backup?")
                        .setMessage("Backup from: $backupDate\nApp version: $backupVersion\nSessions: $sessionCount\n\nThis will overwrite current settings.")
                        .setPositiveButton("Restore") { _, _ ->
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                performRestore(activity, json)
                            }
                        }
                        .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Restore Failed")
                        .setMessage("Error: ${e.message}")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    private suspend fun performRestore(activity: AppCompatActivity, json: JSONObject) {
        try {
            val ctx = activity.applicationContext

            // Restore engine preferences
            val prefsJson = json.optJSONObject("engine_prefs")
            if (prefsJson != null) {
                val editor = ctx.getSharedPreferences("bgmi_engine_prefs", Context.MODE_PRIVATE).edit()
                editor.clear()
                for (key in prefsJson.keys()) {
                    val value = prefsJson.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) set.add(value.getString(i))
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.apply()
            }

            // Restore theme
            val theme = json.optString("theme", "dark")
            ctx.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                .edit().putString("theme", theme).apply()

            // Restore sessions
            val sessionsJson = json.optJSONArray("sessions")
            if (sessionsJson != null && sessionsJson.length() > 0) {
                val db = com.bgmi.engine.data.AppDatabase.getInstance(ctx)
                val dao = db.gameSessionDao()
                for (i in 0 until sessionsJson.length()) {
                    val s = sessionsJson.getJSONObject(i)
                    val session = com.bgmi.engine.data.GameSession(
                        startTime = s.getLong("startTime"),
                        endTime = s.getLong("endTime"),
                        durationMs = s.getLong("durationMs"),
                        avgFps = s.optInt("avgFps", 0),
                        avgTemp = s.getDouble("avgTemp"),
                        peakTemp = s.getDouble("peakTemp"),
                        avgCpu = s.getInt("avgCpu"),
                        avgRam = s.getInt("avgRam"),
                        minBattery = s.optInt("minBattery", 0),
                        startBattery = s.getInt("startBattery"),
                        endBattery = s.getInt("endBattery"),
                        thermalHits = s.getInt("thermalHits"),
                        modeChanges = s.getInt("modeChanges"),
                        stabilizerRuns = s.getInt("stabilizerRuns"),
                        performanceScore = s.getInt("performanceScore"),
                        dominantMode = s.getString("dominantMode")
                    )
                    dao.insert(session)
                }
            }

            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Restore Complete")
                    .setMessage("Settings, whitelist, theme, and ${sessionsJson?.length() ?: 0} sessions restored.\n\nRestart the app to apply theme changes.")
                    .setPositiveButton("Restart") { _, _ ->
                        val intent = activity.intent
                        activity.finish()
                        activity.startActivity(intent)
                    }
                    .setNegativeButton("Later") { d, _ -> d.dismiss() }
                    .show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}")
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Restore Failed")
                    .setMessage("Error: ${e.message}")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .show()
            }
        }
    }

    /**
     * Check if a backup exists — call on app startup to prompt restore after reinstall.
     */
    fun checkAndPromptRestore(activity: AppCompatActivity) {
        val ctx = activity.applicationContext
        val file = getBackupFile(ctx)
        if (!file.exists()) return

        // Only prompt if database is empty (fresh install)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val db = com.bgmi.engine.data.AppDatabase.getInstance(ctx)
            val count = db.gameSessionDao().getSessionCount()
            if (count > 0) return@launch // Already has data, don't prompt

            // Check if prefs are default (fresh install)
            val prefs = ctx.getSharedPreferences("bgmi_engine_prefs", Context.MODE_PRIVATE)
            if (prefs.all.isNotEmpty()) return@launch // Already configured

            val json = JSONObject(file.readText())
            val backupDate = json.optString("date", "Unknown")
            val sessionCount = json.optJSONArray("sessions")?.length() ?: 0

            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Backup Found")
                    .setMessage("A previous backup was found:\nDate: $backupDate\nSessions: $sessionCount\n\nWould you like to restore your settings and data?")
                    .setPositiveButton("Restore") { _, _ ->
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            performRestore(activity, json)
                        }
                    }
                    .setNegativeButton("Start Fresh") { d, _ -> d.dismiss() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun getVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
    }
}
