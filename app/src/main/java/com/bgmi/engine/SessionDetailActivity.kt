package com.bgmi.engine

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity
import com.bgmi.engine.data.AppDatabase
import com.bgmi.engine.data.GameSession
import com.bgmi.engine.data.StatSnapshot
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val sessionId = intent.getLongExtra("session_id", -1)
        if (sessionId == -1L) {
            finish()
            return
        }

        loadSessionDetail(sessionId)
    }

    private fun loadSessionDetail(sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@SessionDetailActivity)
            val session = db.gameSessionDao().getSessionById(sessionId)
            val snapshots = db.statSnapshotDao().getSnapshotsForSession(sessionId)
            val logs = db.engineLogDao().getLogsForSession(sessionId)

            if (session == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            withContext(Dispatchers.Main) {
                populateHeader(session)
                populateStats(session)
                setupTempChart(snapshots)
                setupFpsChart(snapshots)
                setupCpuRamChart(snapshots)
                populateLogs(logs)
            }
        }
    }

    private fun populateHeader(session: GameSession) {
        val dateFormat = SimpleDateFormat("MMM dd yyyy, HH:mm", Locale.getDefault())
        val mins = session.durationMs / 60000
        val secs = (session.durationMs % 60000) / 1000

        findViewById<TextView>(R.id.tvDetailDate).text = dateFormat.format(Date(session.startTime))
        findViewById<TextView>(R.id.tvDetailDuration).text = "${mins}m ${secs}s"
        findViewById<TextView>(R.id.tvDetailScore).text = "${session.performanceScore}"
        findViewById<TextView>(R.id.tvDetailScore).setTextColor(
            when {
                session.performanceScore >= 70 -> 0xFF4ECB71.toInt()
                session.performanceScore >= 40 -> 0xFFFFD700.toInt()
                else -> 0xFFFF6B6B.toInt()
            }
        )
        findViewById<TextView>(R.id.tvDetailMode).text = session.dominantMode
        findViewById<TextView>(R.id.tvDetailMode).setTextColor(
            when (session.dominantMode) {
                "EXTREME" -> 0xFF4ECB71.toInt()
                "BALANCED" -> 0xFFFFD700.toInt()
                else -> 0xFFFF6B6B.toInt()
            }
        )
    }

    private fun populateStats(session: GameSession) {
        findViewById<TextView>(R.id.tvStatFps).text = "Avg FPS: ${session.avgFps}"
        findViewById<TextView>(R.id.tvStatTemp).text = "Avg Temp: ${String.format("%.1f", session.avgTemp)}°C"
        findViewById<TextView>(R.id.tvStatPeakTemp).text = "Peak Temp: ${String.format("%.1f", session.peakTemp)}°C"
        findViewById<TextView>(R.id.tvStatCpu).text = "Avg CPU: ${session.avgCpu}%"
        findViewById<TextView>(R.id.tvStatRam).text = "Avg RAM: ${session.avgRam}%"
        findViewById<TextView>(R.id.tvStatBattery).text = "Battery: ${session.startBattery}% → ${session.endBattery}% (drain: ${session.startBattery - session.endBattery}%)"
        findViewById<TextView>(R.id.tvStatThermal).text = "Thermal Hits: ${session.thermalHits} | Mode Changes: ${session.modeChanges}"
        findViewById<TextView>(R.id.tvStatStabilizer).text = "Stabilizer Runs: ${session.stabilizerRuns}"
    }

    private fun setupTempChart(snapshots: List<StatSnapshot>) {
        val chart = findViewById<LineChart>(R.id.chartTemp)
        if (snapshots.isEmpty()) {
            chart.setNoDataText("No data")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val entries = snapshots
            .filter { it.tempCelsius > 0 }
            .map { Entry((it.elapsedMs / 1000f), it.tempCelsius.toFloat()) }

        if (entries.isEmpty()) {
            chart.setNoDataText("No temperature data")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
            color = 0xFFFF6B6B.toInt()
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = 0xFFFF6B6B.toInt()
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        styleChart(chart, dataSet)
    }

    private fun setupFpsChart(snapshots: List<StatSnapshot>) {
        val chart = findViewById<LineChart>(R.id.chartFpsDetail)
        if (snapshots.isEmpty()) {
            chart.setNoDataText("No data")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val entries = snapshots
            .filter { it.fps > 0 }
            .map { Entry((it.elapsedMs / 1000f), it.fps.toFloat()) }

        if (entries.isEmpty()) {
            chart.setNoDataText("No FPS data")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val dataSet = LineDataSet(entries, "FPS").apply {
            color = 0xFF4ECB71.toInt()
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = 0xFF4ECB71.toInt()
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        styleChart(chart, dataSet)
    }

    private fun setupCpuRamChart(snapshots: List<StatSnapshot>) {
        val chart = findViewById<LineChart>(R.id.chartCpuRam)
        if (snapshots.isEmpty()) {
            chart.setNoDataText("No data")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val cpuEntries = snapshots
            .filter { it.cpuPercent >= 0 }
            .map { Entry((it.elapsedMs / 1000f), it.cpuPercent.toFloat()) }

        val ramEntries = snapshots
            .filter { it.ramPercent >= 0 }
            .map { Entry((it.elapsedMs / 1000f), it.ramPercent.toFloat()) }

        val cpuSet = LineDataSet(cpuEntries, "CPU %").apply {
            color = 0xFFFFD700.toInt()
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val ramSet = LineDataSet(ramEntries, "RAM %").apply {
            color = 0xFF9B59B6.toInt()
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.apply {
            data = LineData(cpuSet, ramSet)
            description.isEnabled = false
            legend.textColor = 0xFFAAAAAA.toInt()
            setBackgroundColor(com.bgmi.engine.ui.Colors.surfaceHigh(this@SessionDetailActivity))
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = 0xFF888888.toInt()
                setDrawGridLines(false)
            }
            axisLeft.apply {
                textColor = 0xFF888888.toInt()
                axisMinimum = 0f
                axisMaximum = 100f
            }
            axisRight.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            animateX(500)
            invalidate()
        }
    }

    private fun populateLogs(logs: List<com.bgmi.engine.data.EngineLogEntry>) {
        val tvLogs = findViewById<TextView>(R.id.tvSessionLogs)
        if (logs.isEmpty()) {
            tvLogs.text = "No logs for this session"
            return
        }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        for (log in logs) {
            sb.append("[${timeFormat.format(Date(log.timestamp))}] ${log.type}: ${log.message}\n")
        }
        tvLogs.text = sb.toString().trimEnd()
    }

    private fun styleChart(chart: LineChart, dataSet: LineDataSet) {
        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.textColor = 0xFFAAAAAA.toInt()
            setBackgroundColor(com.bgmi.engine.ui.Colors.surfaceHigh(this@SessionDetailActivity))
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = 0xFF888888.toInt()
                setDrawGridLines(false)
            }
            axisLeft.textColor = 0xFF888888.toInt()
            axisRight.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            animateX(500)
            invalidate()
        }
    }
}
