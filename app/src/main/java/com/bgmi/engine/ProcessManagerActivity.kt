package com.bgmi.engine

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class ProcessManagerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var tvWhitelistInfo: TextView
    private var allProcesses: List<ProcessInfo> = emptyList()
    private var filteredProcesses: List<ProcessInfo> = emptyList()
    private var whitelist: MutableSet<String> = mutableSetOf()
    private var adapter: ProcessAdapter? = null
    private var activeFilter = "ALL" // ALL, USER, SYSTEM
    private var searchQuery = ""

    private val hiddenPackages = setOf(
        "com.bgmi.engine", "com.android.systemui", "com.android.launcher3",
        "com.android.phone", "com.android.server.telecom",
        "moe.shizuku.privileged.api"
    )

    // System package patterns — anything matching these is "system"
    private val systemPatterns = listOf(
        "vendor.", "android.hardware.", "android.hidl.", "com.android.",
        "com.qualcomm.", "com.qti.", "org.codeaurora.",
        "com.google.android.",  // All Google services
        "com.vivo.",            // All Vivo services
        "com.bbk.",             // iQOO/BBK services
        "com.iqoo.",            // iQOO services
        "android.process.", "android.system.",
        ".dataservices", ".pasr", ".qms", ".qtidataservices",
        "sensors.", "media.", "audioserver", "cameraserver", "surfaceflinger",
        "netd", "logd", "vold", "installd", "servicemanager",
        "qccsyshal", "_zygote"
    )

    data class ProcessInfo(
        val packageName: String,
        val memoryKb: Long,
        val isSystem: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_process_manager)

        recyclerView = findViewById(R.id.rvProcesses)
        tvStatus = findViewById(R.id.tvProcessStatus)
        tvWhitelistInfo = findViewById(R.id.tvWhitelistInfo)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load whitelist
        val prefs = getSharedPreferences("bgmi_engine_prefs", MODE_PRIVATE)
        val saved = prefs.getStringSet("kill_whitelist", null)
        if (saved != null) {
            whitelist.addAll(saved)
        } else {
            whitelist.addAll(listOf(
                "com.google.android.gms", "com.google.android.gms.persistent",
                "com.google.android.inputmethod.latin", "com.google.android.googlequicksearchbox",
                "com.pubg.imobile", "com.whatsapp", "com.whatsapp.w4b"
            ))
            saveWhitelist()
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnRefresh).setOnClickListener { loadProcesses() }

        // Kill All (respects filter + whitelist)
        findViewById<TextView>(R.id.btnKillAll).setOnClickListener {
            val toKill = filteredProcesses.filter {
                !it.isSystem && !whitelist.contains(it.packageName) && !hiddenPackages.contains(it.packageName)
            }
            if (toKill.isEmpty()) {
                tvStatus.text = "Nothing to kill"
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Kill ${toKill.size} apps?")
                .setMessage(toKill.take(8).joinToString("\n") { "• ${shortName(it.packageName)} (${it.memoryKb/1024}MB)" } +
                    if (toKill.size > 8) "\n...+${toKill.size - 8} more" else "")
                .setPositiveButton("Kill") { d, _ -> d.dismiss(); launchKillTerminal(toKill) }
                .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                .show()
        }

        // Kill Selected
        findViewById<TextView>(R.id.btnKillSelected).setOnClickListener {
            val selected = adapter?.getSelected() ?: emptyList()
            if (selected.isEmpty()) {
                tvStatus.text = "Tap apps to select first"
                return@setOnClickListener
            }
            launchKillTerminal(allProcesses.filter { selected.contains(it.packageName) })
        }

        // Whitelist screen
        findViewById<TextView>(R.id.btnManageWhitelist).setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }

        // Search
        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter tabs
        val btnAll = findViewById<TextView>(R.id.btnFilterAll)
        val btnUser = findViewById<TextView>(R.id.btnFilterUser)
        val btnSystem = findViewById<TextView>(R.id.btnFilterSystem)

        val setActiveTab = { active: String ->
            activeFilter = active
            btnAll.setTextColor(if (active == "ALL") com.bgmi.engine.ui.Colors.ACCENT.toInt() else 0xFF666666.toInt())
            btnUser.setTextColor(if (active == "USER") 0xFF4ECB71.toInt() else 0xFF666666.toInt())
            btnSystem.setTextColor(if (active == "SYSTEM") 0xFFFFD700.toInt() else 0xFF666666.toInt())
            applyFilter()
        }

        btnAll.setOnClickListener { setActiveTab("ALL") }
        btnUser.setOnClickListener { setActiveTab("USER") }
        btnSystem.setOnClickListener { setActiveTab("SYSTEM") }

        loadProcesses()
    }

    override fun onResume() {
        super.onResume()
        // Reload whitelist (may have changed in WhitelistActivity)
        val saved = getSharedPreferences("bgmi_engine_prefs", MODE_PRIVATE)
            .getStringSet("kill_whitelist", null)
        if (saved != null) {
            whitelist.clear()
            whitelist.addAll(saved)
        }
        updateWhitelistInfo()
        if (allProcesses.isNotEmpty()) applyFilter()
    }

    private fun loadProcesses() {
        tvStatus.text = "Loading..."
        tvStatus.setTextColor(0xFFFFD700.toInt())

        lifecycleScope.launch(Dispatchers.IO) {
            val result = ShizukuManager.runCommand("ps -A -o PID,RSS,NAME")
            if (!result.success) {
                withContext(Dispatchers.Main) { tvStatus.text = "Failed: ${result.error}"; tvStatus.setTextColor(0xFFFF6B6B.toInt()) }
                return@launch
            }

            val processes = mutableMapOf<String, Pair<Long, Boolean>>()
            for (line in result.output.lines()) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 3) continue
                val name = parts.last()
                val memKb = parts[1].toLongOrNull() ?: continue
                if (!name.contains(".") || name.startsWith("[")) continue
                if (hiddenPackages.contains(name)) continue

                val isSystem = systemPatterns.any { name.startsWith(it) || name.contains(it) }
                val existing = processes[name]?.first ?: 0
                if (memKb > existing) processes[name] = memKb to isSystem
            }

            val sorted = processes.map { ProcessInfo(it.key, it.value.first, it.value.second) }
                .sortedByDescending { it.memoryKb }

            allProcesses = sorted

            withContext(Dispatchers.Main) {
                applyFilter()
                val userCount = sorted.count { !it.isSystem }
                val sysCount = sorted.count { it.isSystem }
                val totalMb = sorted.sumOf { it.memoryKb } / 1024
                tvStatus.text = "${sorted.size} apps (${userCount} user, ${sysCount} system) | ${totalMb}MB"
                tvStatus.setTextColor(0xFF4ECB71.toInt())
                updateWhitelistInfo()
            }
        }
    }

    private fun applyFilter() {
        var list = when (activeFilter) {
            "USER" -> allProcesses.filter { !it.isSystem }
            "SYSTEM" -> allProcesses.filter { it.isSystem }
            else -> allProcesses
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.packageName.lowercase().contains(searchQuery) }
        }

        filteredProcesses = list
        adapter = ProcessAdapter(list, whitelist, hiddenPackages) { pkg, add ->
            if (add) whitelist.add(pkg) else whitelist.remove(pkg)
            saveWhitelist()
            updateWhitelistInfo()
        }
        recyclerView.adapter = adapter
    }

    private fun launchKillTerminal(toKill: List<ProcessInfo>) {
        KillTerminalActivity.packagesToKill = toKill.map { it.packageName }
        startActivity(Intent(this, KillTerminalActivity::class.java))
    }

    private fun saveWhitelist() {
        getSharedPreferences("bgmi_engine_prefs", MODE_PRIVATE)
            .edit().putStringSet("kill_whitelist", whitelist).apply()
    }

    private fun updateWhitelistInfo() {
        tvWhitelistInfo.text = if (whitelist.isEmpty()) "No whitelisted apps"
        else "Protected: ${whitelist.size} apps"
    }

    private fun shortName(pkg: String): String {
        val parts = pkg.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else pkg
    }

    class ProcessAdapter(
        private val processes: List<ProcessInfo>,
        private val whitelist: MutableSet<String>,
        private val hidden: Set<String>,
        private val onWhitelistChanged: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<ProcessAdapter.VH>() {

        private val selected = mutableSetOf<String>()

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvProcName)
            val tvMemory: TextView = view.findViewById(R.id.tvProcMemory)
            val tvTag: TextView = view.findViewById(R.id.tvProcTag)
            val cbSelect: CheckBox = view.findViewById(R.id.cbProcSelect)
            val btnWhitelist: TextView = view.findViewById(R.id.btnProcWhitelist)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_process, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val proc = processes[position]
            val memMb = proc.memoryKb / 1024
            val isWhitelisted = whitelist.contains(proc.packageName)
            val isSelected = selected.contains(proc.packageName)

            // Short name
            val parts = proc.packageName.split(".")
            holder.tvName.text = if (parts.size > 2) parts.takeLast(2).joinToString(".") else proc.packageName

            // Memory
            holder.tvMemory.text = "${memMb}MB"
            holder.tvMemory.setTextColor(when {
                memMb >= 300 -> 0xFFFF6B6B.toInt()
                memMb >= 100 -> 0xFFFFD700.toInt()
                else -> 0xFF888888.toInt()
            })

            // Tag
            when {
                isWhitelisted -> {
                    holder.tvTag.text = "SAFE"
                    holder.tvTag.setTextColor(0xFF4ECB71.toInt())
                    holder.tvTag.visibility = View.VISIBLE
                }
                proc.isSystem -> {
                    holder.tvTag.text = "SYS"
                    holder.tvTag.setTextColor(0xFFFFD700.toInt())
                    holder.tvTag.visibility = View.VISIBLE
                }
                else -> holder.tvTag.visibility = View.GONE
            }

            // Select
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = isSelected
            holder.cbSelect.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(proc.packageName) else selected.remove(proc.packageName)
            }

            // Whitelist
            holder.btnWhitelist.text = if (isWhitelisted) "🛡" else "+"
            holder.btnWhitelist.setTextColor(if (isWhitelisted) 0xFF4ECB71.toInt() else 0xFF555555.toInt())
            holder.btnWhitelist.setOnClickListener {
                onWhitelistChanged(proc.packageName, !isWhitelisted)
                notifyItemChanged(position)
            }

            // Tap row = toggle select
            holder.itemView.setOnClickListener {
                if (selected.contains(proc.packageName)) selected.remove(proc.packageName) else selected.add(proc.packageName)
                notifyItemChanged(position)
            }

            holder.itemView.setBackgroundColor(
                if (isSelected) com.bgmi.engine.ui.Colors.selected(holder.itemView.context)
                else com.bgmi.engine.ui.Colors.itemBg(holder.itemView.context)
            )
        }

        override fun getItemCount() = processes.size
        fun getSelected(): List<String> = selected.toList()
    }
}
