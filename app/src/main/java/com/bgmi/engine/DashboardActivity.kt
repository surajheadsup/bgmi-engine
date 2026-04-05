package com.bgmi.engine

import android.content.Intent
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
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : BaseActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        db = AppDatabase.getInstance(this)

        findViewById<View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessionDao = db.gameSessionDao()

            val totalSessions = sessionDao.getSessionCount()
            val totalPlayMs = sessionDao.getTotalPlayTimeMs() ?: 0L
            val avgFps = sessionDao.getOverallAvgFps() ?: 0.0
            val avgTemp = sessionDao.getOverallAvgTemp() ?: 0.0
            val avgScore = sessionDao.getOverallAvgScore() ?: 0.0
            val peakTemp = sessionDao.getHighestPeakTemp() ?: 0.0
            val totalThermal = sessionDao.getTotalThermalHits() ?: 0

            // Last 7 days sessions for chart
            val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
            val recentSessions = sessionDao.getSessionsSince(weekAgo)

            // Best session this week
            val bestSession = sessionDao.getBestSessionSince(weekAgo)

            withContext(Dispatchers.Main) {
                // Summary cards
                findViewById<TextView>(R.id.tvTotalSessions).text = "$totalSessions"
                findViewById<TextView>(R.id.tvTotalPlayTime).text = formatDuration(totalPlayMs)
                findViewById<TextView>(R.id.tvAvgFps).text = if (avgFps > 0) "${avgFps.toInt()}" else "--"
                findViewById<TextView>(R.id.tvAvgTemp).text = if (avgTemp > 0) String.format("%.1f°C", avgTemp) else "--"
                findViewById<TextView>(R.id.tvAvgScore).text = "${avgScore.toInt()}"
                findViewById<TextView>(R.id.tvPeakTemp).text = if (peakTemp > 0) String.format("%.1f°C", peakTemp) else "--"
                findViewById<TextView>(R.id.tvTotalThermal).text = "$totalThermal"

                // Best session
                if (bestSession != null) {
                    findViewById<TextView>(R.id.tvBestSession).text =
                        "Score: ${bestSession.performanceScore} | ${formatDuration(bestSession.durationMs)} | FPS: ${bestSession.avgFps}"
                } else {
                    findViewById<TextView>(R.id.tvBestSession).text = "No sessions yet"
                }

                // FPS chart (last 10 sessions)
                setupFpsChart(recentSessions.takeLast(10))

                // Score trend chart
                setupScoreChart(recentSessions.takeLast(10))
            }
        }
    }

    private fun setupFpsChart(sessions: List<com.bgmi.engine.data.GameSession>) {
        val chart = findViewById<BarChart>(R.id.chartFps)
        if (sessions.isEmpty()) {
            chart.setNoDataText("No sessions yet")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val entries = sessions.mapIndexed { i, s -> BarEntry(i.toFloat(), s.avgFps.toFloat()) }
        val labels = sessions.map {
            SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.startTime))
        }

        val dataSet = BarDataSet(entries, "Avg FPS").apply {
            color = 0xFF4ECB71.toInt()
            valueTextColor = 0xFFAAAAAA.toInt()
            valueTextSize = 10f
        }

        chart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.textColor = 0xFFAAAAAA.toInt()
            setBackgroundColor(com.bgmi.engine.ui.Colors.surfaceHigh(this@DashboardActivity))
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = 0xFF888888.toInt()
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.textColor = 0xFF888888.toInt()
            axisRight.isEnabled = false
            animateY(500)
            invalidate()
        }
    }

    private fun setupScoreChart(sessions: List<com.bgmi.engine.data.GameSession>) {
        val chart = findViewById<LineChart>(R.id.chartScore)
        if (sessions.isEmpty()) {
            chart.setNoDataText("No sessions yet")
            chart.setNoDataTextColor(0xFFAAAAAA.toInt())
            return
        }

        val entries = sessions.mapIndexed { i, s -> Entry(i.toFloat(), s.performanceScore.toFloat()) }
        val labels = sessions.map {
            SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.startTime))
        }

        val dataSet = LineDataSet(entries, "Performance Score").apply {
            color = com.bgmi.engine.ui.Colors.ACCENT.toInt()
            valueTextColor = 0xFFAAAAAA.toInt()
            valueTextSize = 10f
            lineWidth = 2f
            setCircleColor(com.bgmi.engine.ui.Colors.ACCENT.toInt())
            circleRadius = 3f
            setDrawFilled(true)
            fillColor = com.bgmi.engine.ui.Colors.ACCENT.toInt()
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.textColor = 0xFFAAAAAA.toInt()
            setBackgroundColor(com.bgmi.engine.ui.Colors.surfaceHigh(this@DashboardActivity))
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = 0xFF888888.toInt()
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                textColor = 0xFF888888.toInt()
                axisMinimum = 0f
                axisMaximum = 100f
            }
            axisRight.isEnabled = false
            animateX(500)
            invalidate()
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = ms / 60000
        val hours = totalMin / 60
        val mins = totalMin % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }
}
