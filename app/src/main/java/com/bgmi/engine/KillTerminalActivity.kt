package com.bgmi.engine

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity

class KillTerminalActivity : BaseActivity() {

    private lateinit var tvTerminal: TextView
    private lateinit var tvStatus: TextView
    private lateinit var scrollView: ScrollView
    private val log = StringBuilder()

    companion object {
        var packagesToKill: List<String> = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kill_terminal)

        tvTerminal = findViewById(R.id.tvTerminal)
        tvStatus = findViewById(R.id.tvTerminalStatus)
        scrollView = findViewById(R.id.scrollTerminal)

        findViewById<TextView>(R.id.btnClose).setOnClickListener { finish() }

        if (packagesToKill.isEmpty()) {
            appendLine("$ No packages to kill", "#FF6B6B")
            return
        }

        startKilling()
    }

    private fun startKilling() {
        val packages = packagesToKill.toList()
        tvStatus.text = "Killing ${packages.size} apps..."
        tvStatus.setTextColor(0xFFFFD700.toInt())

        appendLine("$ bgmi-engine kill-all --count=${packages.size}", "#E94560")
        appendLine("", null)

        lifecycleScope.launch(Dispatchers.IO) {
            var killed = 0
            var failed = 0
            var notRunning = 0

            for ((i, pkg) in packages.withIndex()) {
                val shortPkg = pkg.split(".").takeLast(2).joinToString(".")
                // Strip :subprocess suffix to get base package
                val basePkg = pkg.split(":").first()

                // Check if process is running using ps (pidof doesn't match subprocesses)
                val psBefore = ShizukuManager.runCommand("ps -A -o PID,NAME | grep $basePkg")
                val wasRunning = psBefore.success && psBefore.output.trim().isNotEmpty()
                val pidsBefore = if (wasRunning) {
                    psBefore.output.lines().mapNotNull { it.trim().split("\\s+".toRegex()).firstOrNull()?.trim() }
                        .filter { it.all { c -> c.isDigit() } }
                } else emptyList()

                if (!wasRunning) {
                    notRunning++
                    withContext(Dispatchers.Main) {
                        appendLine("  — $shortPkg (not running)", "#888888")
                        tvStatus.text = "Progress: ${i + 1}/${packages.size}"
                    }
                    continue
                }

                withContext(Dispatchers.Main) {
                    appendLine("$ kill $shortPkg (${pidsBefore.size} procs)", "#888888")
                }

                // Step 1: force-stop + set stopped state (same as manual Force Stop in Settings)
                ShizukuManager.runCommand("am force-stop $basePkg")
                // Cancel all pending alarms & jobs so app doesn't auto-restart
                ShizukuManager.runCommandSilent("cmd alarm remove-all $basePkg")
                ShizukuManager.runCommandSilent("cmd jobscheduler cancel $basePkg")
                ShizukuManager.runCommandSilent("cmd notification cancel_all $basePkg")
                GameOptimizer.removeFromRecents(basePkg)

                // Step 2: kill -9 all PIDs directly (catches subprocesses)
                for (pid in pidsBefore) {
                    ShizukuManager.runCommandSilent("kill -9 $pid")
                }
                // Re-run force-stop to catch anything that respawned during kill
                ShizukuManager.runCommand("am force-stop $basePkg")

                // Step 3: wait and verify
                delay(300)
                val psAfter = ShizukuManager.runCommand("ps -A -o PID,NAME | grep $basePkg")
                val stillAlive = psAfter.success && psAfter.output.trim().isNotEmpty()

                if (stillAlive) {
                    // Step 4: app restarted — kill new PIDs
                    val newPids = psAfter.output.lines().mapNotNull { it.trim().split("\\s+".toRegex()).firstOrNull()?.trim() }
                        .filter { it.all { c -> c.isDigit() } }
                    for (pid in newPids) ShizukuManager.runCommandSilent("kill -9 $pid")
                    delay(200)

                    val psFinal = ShizukuManager.runCommand("ps -A -o PID,NAME | grep $basePkg")
                    val finalAlive = psFinal.success && psFinal.output.trim().isNotEmpty()

                    if (finalAlive) {
                        failed++
                        withContext(Dispatchers.Main) { appendLine("  ✗ $shortPkg respawns (persistent)", "#FF6B6B") }
                    } else {
                        killed++
                        withContext(Dispatchers.Main) { appendLine("  ✓ $shortPkg killed (retry)", "#FFD700") }
                    }
                } else {
                    killed++
                    withContext(Dispatchers.Main) { appendLine("  ✓ $shortPkg killed", "#4ECB71") }
                }

                withContext(Dispatchers.Main) { tvStatus.text = "Progress: ${i + 1}/${packages.size}" }
            }

            withContext(Dispatchers.Main) {
                appendLine("", null)
                appendLine("═══════════════════════════════", "#333333")
                val summary = "Killed: $killed | Persistent: $failed | Skipped: $notRunning"
                appendLine(summary, if (failed == 0) "#4ECB71" else "#FFD700")
                if (failed > 0) {
                    appendLine("Persistent apps auto-restart via system", "#888888")
                }
                appendLine("═══════════════════════════════", "#333333")

                tvStatus.text = "Done — $killed killed, $failed persistent"
                tvStatus.setTextColor(if (failed == 0) 0xFF4ECB71.toInt() else 0xFFFFD700.toInt())
            }
        }
    }

    private fun appendLine(text: String, colorHex: String?) {
        log.append(text).append("\n")
        tvTerminal.text = log.toString()
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
