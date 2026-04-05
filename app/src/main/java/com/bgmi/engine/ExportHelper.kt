package com.bgmi.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.bgmi.engine.data.AppDatabase
import com.bgmi.engine.data.GameSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    private const val TAG = "ExportHelper"

    fun exportAllSessionsCsv(context: Context): File? {
        return try {
            val sessions = AppDatabase.getInstance(context).gameSessionDao().getAllSessions()
            if (sessions.isEmpty()) return null

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            val fileName = "bgmi_sessions_${dateFormat.format(Date())}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            val csv = StringBuilder()
            csv.appendLine("Date,Duration(min),AvgFPS,AvgTemp,AvgCPU,AvgRAM,PeakTemp,BatteryDrain,ThermalHits,ModeChanges,Score,Mode")

            val rowFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            for (s in sessions) {
                csv.appendLine(
                    "${rowFormat.format(Date(s.startTime))}," +
                    "${s.durationMs / 60000}," +
                    "${s.avgFps}," +
                    "${String.format("%.1f", s.avgTemp)}," +
                    "${s.avgCpu}," +
                    "${s.avgRam}," +
                    "${String.format("%.1f", s.peakTemp)}," +
                    "${s.startBattery - s.endBattery}," +
                    "${s.thermalHits}," +
                    "${s.modeChanges}," +
                    "${s.performanceScore}," +
                    "${s.dominantMode}"
                )
            }

            file.writeText(csv.toString())
            Log.d(TAG, "Exported ${sessions.size} sessions to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            null
        }
    }

    fun exportSessionDetail(context: Context, sessionId: Long): File? {
        return try {
            val db = AppDatabase.getInstance(context)
            val session = db.gameSessionDao().getSessionById(sessionId) ?: return null
            val snapshots = db.statSnapshotDao().getSnapshotsForSession(sessionId)
            val logs = db.engineLogDao().getLogsForSession(sessionId)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            val fileName = "bgmi_session_${dateFormat.format(Date(session.startTime))}.txt"
            val file = File(context.getExternalFilesDir(null), fileName)

            val sb = StringBuilder()
            sb.appendLine("═══ BGMI Engine Pro - Session Report ═══")
            sb.appendLine()
            sb.appendLine("Date: ${SimpleDateFormat("MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(session.startTime))}")
            sb.appendLine("Duration: ${session.durationMs / 60000}m ${(session.durationMs % 60000) / 1000}s")
            sb.appendLine("Score: ${session.performanceScore}/100")
            sb.appendLine("Mode: ${session.dominantMode}")
            sb.appendLine()
            sb.appendLine("── Stats ──")
            sb.appendLine("Avg FPS: ${session.avgFps}")
            sb.appendLine("Avg Temp: ${String.format("%.1f", session.avgTemp)}°C")
            sb.appendLine("Peak Temp: ${String.format("%.1f", session.peakTemp)}°C")
            sb.appendLine("Avg CPU: ${session.avgCpu}%")
            sb.appendLine("Avg RAM: ${session.avgRam}%")
            sb.appendLine("Battery: ${session.startBattery}% → ${session.endBattery}% (drain: ${session.startBattery - session.endBattery}%)")
            sb.appendLine("Thermal Hits: ${session.thermalHits}")
            sb.appendLine("Mode Changes: ${session.modeChanges}")
            sb.appendLine("Stabilizer Runs: ${session.stabilizerRuns}")

            if (snapshots.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("── Timeline (${snapshots.size} snapshots) ──")
                sb.appendLine("Time,FPS,Temp,CPU,RAM,Battery,Ping,Mode")
                for (snap in snapshots) {
                    val sec = snap.elapsedMs / 1000
                    sb.appendLine("${sec}s,${snap.fps},${String.format("%.1f", snap.tempCelsius)},${snap.cpuPercent},${snap.ramPercent},${snap.batteryPercent},${snap.pingMs},${snap.mode}")
                }
            }

            if (logs.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("── Logs ──")
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                for (log in logs) {
                    sb.appendLine("[${timeFormat.format(Date(log.timestamp))}] ${log.type}: ${log.message}")
                }
            }

            file.writeText(sb.toString())
            Log.d(TAG, "Exported session detail to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Session export failed", e)
            null
        }
    }

    fun shareFile(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".csv")) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Session Data"))
    }
}
