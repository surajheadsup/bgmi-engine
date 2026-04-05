package com.bgmi.engine

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bgmi.engine.data.AppDatabase
import com.bgmi.engine.data.EngineLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.ViewGroup

class LogViewerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private var allLogs: List<EngineLogEntry> = emptyList()
    private var activeFilter: String? = null
    private val filterButtons = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        recyclerView = findViewById(R.id.rvLogs)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupFilters()
        loadLogs()
    }

    private fun setupFilters() {
        val layout = findViewById<LinearLayout>(R.id.layoutFilters)
        val filters = listOf("ALL", "THERMAL", "MODE", "KILL", "ERROR", "INFO")

        for ((index, filter) in filters.withIndex()) {
            val btn = TextView(this).apply {
                text = filter
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.toggle_btn_bg)
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.marginStart = dpToPx(3)
                if (index < filters.size - 1) lp.marginEnd = dpToPx(3)
                layoutParams = lp
                setTextColor(com.bgmi.engine.ui.Colors.onSurface(this@LogViewerActivity))
            }
            btn.setOnClickListener {
                activeFilter = if (filter == "ALL") null else filter
                updateFilterSelection(btn)
                applyFilter()
            }
            layout.addView(btn)
            filterButtons.add(btn)
        }
        // Set ALL as active initially
        if (filterButtons.isNotEmpty()) setToggleActive(filterButtons[0], true)
    }

    private fun updateFilterSelection(selected: TextView) {
        for (btn in filterButtons) setToggleActive(btn, false)
        setToggleActive(selected, true)
    }

    private fun setToggleActive(btn: TextView, active: Boolean) {
        if (active) {
            btn.setBackgroundResource(R.drawable.toggle_btn_bg_active)
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.setBackgroundResource(R.drawable.toggle_btn_bg)
            btn.setTextColor(com.bgmi.engine.ui.Colors.onSurface(btn.context))
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun loadLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            allLogs = AppDatabase.getInstance(this@LogViewerActivity).engineLogDao().getRecentLogs(500)
            withContext(Dispatchers.Main) { applyFilter() }
        }
    }

    private fun applyFilter() {
        val filtered = if (activeFilter == null) allLogs
        else allLogs.filter { it.type == activeFilter }

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = LogAdapter(filtered)
        }
    }

    class LogAdapter(private val logs: List<EngineLogEntry>) :
        RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        private val timeFormat = SimpleDateFormat("MMM dd HH:mm:ss", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTime: TextView = view.findViewById(R.id.tvLogTime)
            val tvType: TextView = view.findViewById(R.id.tvLogType)
            val tvMessage: TextView = view.findViewById(R.id.tvLogMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            holder.tvTime.text = timeFormat.format(Date(log.timestamp))
            holder.tvType.text = log.type
            holder.tvMessage.text = log.message

            holder.tvType.setTextColor(
                when (log.type) {
                    "THERMAL" -> 0xFFFF6B6B.toInt()
                    "MODE" -> 0xFFFFD700.toInt()
                    "KILL" -> 0xFF9B59B6.toInt()
                    "ERROR" -> 0xFFFF6B6B.toInt()
                    "SHIZUKU" -> 0xFF3498DB.toInt()
                    else -> 0xFF4ECB71.toInt()
                }
            )
        }

        override fun getItemCount() = logs.size
    }
}
