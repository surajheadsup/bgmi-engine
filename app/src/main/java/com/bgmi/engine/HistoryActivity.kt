package com.bgmi.engine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bgmi.engine.data.AppDatabase
import com.bgmi.engine.data.GameSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.rvSessions)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessions = AppDatabase.getInstance(this@HistoryActivity).gameSessionDao().getAllSessions()
            withContext(Dispatchers.Main) {
                if (sessions.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = SessionAdapter(sessions) { session ->
                        val intent = Intent(this@HistoryActivity, SessionDetailActivity::class.java)
                        intent.putExtra("session_id", session.id)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    class SessionAdapter(
        private val sessions: List<GameSession>,
        private val onClick: (GameSession) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvSessionDate)
            val tvDuration: TextView = view.findViewById(R.id.tvSessionDuration)
            val tvStats: TextView = view.findViewById(R.id.tvSessionStats)
            val tvScore: TextView = view.findViewById(R.id.tvSessionScore)
            val tvMode: TextView = view.findViewById(R.id.tvSessionMode)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            val mins = session.durationMs / 60000
            val secs = (session.durationMs % 60000) / 1000

            holder.tvDate.text = dateFormat.format(Date(session.startTime))
            holder.tvDuration.text = "${mins}m ${secs}s"
            holder.tvStats.text = "FPS: ${session.avgFps} | Temp: ${String.format("%.1f", session.avgTemp)}° | CPU: ${session.avgCpu}%"
            holder.tvScore.text = "${session.performanceScore}"
            holder.tvScore.setTextColor(
                when {
                    session.performanceScore >= 70 -> 0xFF4ECB71.toInt()
                    session.performanceScore >= 40 -> 0xFFFFD700.toInt()
                    else -> 0xFFFF6B6B.toInt()
                }
            )
            holder.tvMode.text = session.dominantMode
            holder.tvMode.setTextColor(
                when (session.dominantMode) {
                    "EXTREME" -> 0xFF4ECB71.toInt()
                    "BALANCED" -> 0xFFFFD700.toInt()
                    else -> 0xFFFF6B6B.toInt()
                }
            )

            holder.itemView.setOnClickListener { onClick(session) }
        }

        override fun getItemCount() = sessions.size
    }
}
