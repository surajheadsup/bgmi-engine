package com.bgmi.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.ui.BaseActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : BaseActivity() {

    private lateinit var tvOutput: TextView
    private val outputLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        tvOutput = findViewById(R.id.tvCommandOutput)

        setupThermalSliders()
        setupGameOptToggles()
        setupGeneralToggles()
        setupExport()
        setupDiagnostics()
    }

    // --- Thermal Sliders ---

    private fun setupThermalSliders() {
        setupSlider(R.id.sbWarningTemp, R.id.tvWarningValue, EnginePrefs.getWarningTemp(this))
        { EnginePrefs.setWarningTemp(this, it); EnginePrefs.setActivePreset(this, "custom") }

        setupSlider(R.id.sbEmergencyTemp, R.id.tvEmergencyValue, EnginePrefs.getEmergencyTemp(this))
        { EnginePrefs.setEmergencyTemp(this, it); EnginePrefs.setActivePreset(this, "custom") }
    }

    private fun setupSlider(seekId: Int, tvId: Int, initial: Int, onChanged: (Int) -> Unit) {
        val sb = findViewById<SeekBar>(seekId)
        val tv = findViewById<TextView>(tvId)
        sb.progress = initial - 30
        tv.text = "${initial}°C"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val temp = progress + 30
                tv.text = "${temp}°C"
                if (fromUser) onChanged(temp)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    // --- Game Optimization Toggles ---

    private fun setupGameOptToggles() {
        // DNS
        val dnsSwitch = findViewById<SwitchMaterial>(R.id.switchDns)
        val dnsProviderLayout = findViewById<LinearLayout>(R.id.layoutDnsProvider)
        dnsSwitch.isChecked = EnginePrefs.isDnsOptEnabled(this)
        dnsProviderLayout.visibility = if (dnsSwitch.isChecked) View.VISIBLE else View.GONE
        dnsSwitch.setOnCheckedChangeListener { _, checked ->
            EnginePrefs.setDnsOptEnabled(this, checked)
            dnsProviderLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // DNS provider buttons
        val currentDns = EnginePrefs.getDnsProvider(this)
        val dnsCloudflare = findViewById<TextView>(R.id.chipCloudflare)
        val dnsGoogle = findViewById<TextView>(R.id.chipGoogle)
        val dnsAdguard = findViewById<TextView>(R.id.chipAdguard)
        val dnsBtns = listOf(dnsCloudflare, dnsGoogle, dnsAdguard)
        fun updateDnsSelection(selected: String) {
            for (btn in dnsBtns) setToggleActive(btn, false)
            when (selected) {
                "cloudflare" -> setToggleActive(dnsCloudflare, true)
                "google" -> setToggleActive(dnsGoogle, true)
                "adguard" -> setToggleActive(dnsAdguard, true)
            }
        }
        updateDnsSelection(currentDns)
        dnsCloudflare.setOnClickListener { EnginePrefs.setDnsProvider(this, "cloudflare"); updateDnsSelection("cloudflare") }
        dnsGoogle.setOnClickListener { EnginePrefs.setDnsProvider(this, "google"); updateDnsSelection("google") }
        dnsAdguard.setOnClickListener { EnginePrefs.setDnsProvider(this, "adguard"); updateDnsSelection("adguard") }

        // Touch Boost
        setupSwitch(R.id.switchTouchBoost, EnginePrefs.isTouchBoostEnabled(this)) { EnginePrefs.setTouchBoostEnabled(this, it) }

        // GPU
        setupSwitch(R.id.switchGpuBoost, EnginePrefs.isGpuBoostEnabled(this)) { EnginePrefs.setGpuBoostEnabled(this, it) }

        // Pre-Game Clean
        setupSwitch(R.id.switchPreGameClean, EnginePrefs.isPreGameCleanEnabled(this)) { EnginePrefs.setPreGameCleanEnabled(this, it) }
    }

    // --- General Toggles ---

    private fun setupGeneralToggles() {
        setupSwitch(R.id.switchAutoDnd, EnginePrefs.isAutoDnd(this)) { EnginePrefs.setAutoDnd(this, it) }
        setupSwitch(R.id.switchVoice, EnginePrefs.isVoiceEnabled(this)) { EnginePrefs.setVoiceEnabled(this, it) }
        setupSwitch(R.id.switchDropBrightness, EnginePrefs.isDropBrightness(this)) { EnginePrefs.setDropBrightness(this, it) }
        setupSwitch(R.id.switchBatteryEst, EnginePrefs.isBatteryEstimatorEnabled(this)) { EnginePrefs.setBatteryEstimatorEnabled(this, it) }
        setupSwitch(R.id.switchKillAllBg, EnginePrefs.isKillAllBg(this)) { EnginePrefs.setKillAllBg(this, it) }
    }

    // --- Export ---

    private fun setupExport() {
        findViewById<View>(R.id.btnExportCsv).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = ExportHelper.exportAllSessionsCsv(this@SettingsActivity)
                withContext(Dispatchers.Main) {
                    if (file != null) {
                        ExportHelper.shareFile(this@SettingsActivity, file)
                    } else {
                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle("No Data")
                            .setMessage("No sessions recorded yet. Play some BGMI first!")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            }
        }

        findViewById<View>(R.id.btnShareLastSession).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val sessions = com.bgmi.engine.data.AppDatabase.getInstance(this@SettingsActivity)
                    .gameSessionDao().getRecentSessions(1)
                if (sessions.isNotEmpty()) {
                    val file = ExportHelper.exportSessionDetail(this@SettingsActivity, sessions[0].id)
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            ExportHelper.shareFile(this@SettingsActivity, file)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle("No Data")
                            .setMessage("No sessions recorded yet.")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            }
        }
    }

    // --- Diagnostics ---

    private fun setupDiagnostics() {
        // Copy & clear
        findViewById<View>(R.id.btnCopyOutput).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("BGMI Engine Diagnostic", outputLog.toString()))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnClearOutput).setOnClickListener {
            outputLog.clear()
            tvOutput.text = "Cleared."
        }

        // Shizuku status
        val tvStatus = findViewById<TextView>(R.id.tvDiagShizukuStatus)
        val available = ShizukuManager.isShizukuAvailable()
        val permission = ShizukuManager.hasPermission()
        tvStatus.text = "Shizuku: ${if (available) "Running" else "NOT running"} | Permission: ${if (permission) "GRANTED" else "DENIED"}"
        tvStatus.setTextColor(if (available && permission) 0xFF4ECB71.toInt() else 0xFFFF6B6B.toInt())

        // Test buttons
        findViewById<View>(R.id.btnTestWhoami).setOnClickListener { runTest("whoami", "whoami") }
        findViewById<View>(R.id.btnTestRefreshRate).setOnClickListener { runTest("settings get global vivo_screen_refresh_rate_mode", "Refresh Rate") }
        findViewById<View>(R.id.btnTestBrightness).setOnClickListener { runTest("settings get system screen_brightness", "Brightness") }
        findViewById<View>(R.id.btnTestListApps).setOnClickListener { runTest("dumpsys activity recents | grep 'Recent #' | head -10", "Recent Apps") }
        findViewById<View>(R.id.btnTestKillWhatsapp).setOnClickListener { runTest("am force-stop com.whatsapp", "Kill WhatsApp") }
        findViewById<View>(R.id.btnTestKillAmazon).setOnClickListener { runTest("am force-stop com.amazon.mShop.android.shopping", "Kill Amazon") }

        val etCustom = findViewById<TextInputEditText>(R.id.etCustomCommand)
        findViewById<View>(R.id.btnRunCustom).setOnClickListener {
            val cmd = etCustom.text?.toString()?.trim()
            if (!cmd.isNullOrBlank()) runTest(cmd, "Custom")
            else appendOut("!", "Enter a command first")
        }
    }

    private fun runTest(cmd: String, label: String) {
        appendOut(">", "[$label] $cmd")
        if (!ShizukuManager.isShizukuAvailable()) { appendOut("!", "Shizuku NOT running"); return }
        if (!ShizukuManager.hasPermission()) { appendOut("!", "No permission"); return }

        lifecycleScope.launch(Dispatchers.IO) {
            val t = System.currentTimeMillis()
            val r = ShizukuManager.runCommand(cmd)
            val ms = System.currentTimeMillis() - t
            withContext(Dispatchers.Main) {
                if (r.success) appendOut("+", "[${ms}ms] ${r.output.ifBlank { "(success, no output)" }}")
                else appendOut("!", "[${ms}ms] ${r.error}")
            }
        }
    }

    private fun appendOut(tag: String, msg: String) {
        outputLog.append("[$tag] $msg\n")
        tvOutput.text = outputLog.toString()
        (tvOutput.parent as? android.widget.ScrollView)?.post {
            (tvOutput.parent as android.widget.ScrollView).fullScroll(android.widget.ScrollView.FOCUS_DOWN)
        }
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

    private fun setupSwitch(id: Int, initial: Boolean, onChanged: (Boolean) -> Unit) {
        val s = findViewById<SwitchMaterial>(id)
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, checked -> onChanged(checked) }
    }
}
