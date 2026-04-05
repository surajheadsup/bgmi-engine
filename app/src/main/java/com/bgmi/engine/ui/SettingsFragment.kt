package com.bgmi.engine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import com.bgmi.engine.*
import com.google.android.material.switchmaterial.SwitchMaterial
import android.content.Intent

class SettingsFragment : Fragment() {

    private var isThemeChanging = false
    private var isApplyingPreset = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val btnDark = view.findViewById<TextView>(R.id.btnThemeDark)
        val btnLight = view.findViewById<TextView>(R.id.btnThemeLight)
        val btnAmoled = view.findViewById<TextView>(R.id.btnThemeAmoled)

        fun updateThemeButtons(theme: String) {
            btnDark.setTextColor(if (theme == ThemeManager.DARK) com.bgmi.engine.ui.Colors.ACCENT.toInt() else 0xFF666666.toInt())
            btnLight.setTextColor(if (theme == ThemeManager.LIGHT) com.bgmi.engine.ui.Colors.ACCENT.toInt() else 0xFF666666.toInt())
            btnAmoled.setTextColor(if (theme == ThemeManager.AMOLED) com.bgmi.engine.ui.Colors.ACCENT.toInt() else 0xFF666666.toInt())
        }

        fun applyTheme(theme: String) {
            if (isThemeChanging) return
            if (ThemeManager.getTheme(ctx) == theme) return
            isThemeChanging = true
            ThemeManager.setTheme(ctx, theme)
            updateThemeButtons(theme)
            // Restart activity cleanly — new Intent clears the task
            val intent = requireActivity().intent
            requireActivity().finish()
            startActivity(intent)
        }

        updateThemeButtons(ThemeManager.getTheme(ctx))

        btnDark.setOnClickListener { applyTheme(ThemeManager.DARK) }
        btnLight.setOnClickListener { applyTheme(ThemeManager.LIGHT) }
        btnAmoled.setOnClickListener { applyTheme(ThemeManager.AMOLED) }

        // Performance presets
        val btnCompetitive = view.findViewById<TextView>(R.id.btnPresetCompetitive)
        val btnBalanced = view.findViewById<TextView>(R.id.btnPresetBalanced)
        val btnBattery = view.findViewById<TextView>(R.id.btnPresetBattery)
        val btnCustom = view.findViewById<TextView>(R.id.btnPresetCustom)
        val tvPresetInfo = view.findViewById<TextView>(R.id.tvPresetInfo)
        val tvPresetDesc = view.findViewById<TextView>(R.id.tvPresetDesc)
        val presetBtns = listOf(btnCompetitive, btnBalanced, btnBattery, btnCustom)

        fun updatePresetUI(active: String) {
            for (btn in presetBtns) setToggleInactive(btn)
            when {
                active.contains("competitive") -> {
                    setToggleActive(btnCompetitive)
                    tvPresetInfo.text = EnginePrefs.PRESET_COMPETITIVE.name
                    tvPresetDesc.text = EnginePrefs.PRESET_COMPETITIVE.description
                }
                active.contains("battery") -> {
                    setToggleActive(btnBattery)
                    tvPresetInfo.text = EnginePrefs.PRESET_BATTERY.name
                    tvPresetDesc.text = EnginePrefs.PRESET_BATTERY.description
                }
                active.contains("custom") -> {
                    setToggleActive(btnCustom)
                    tvPresetInfo.text = "Custom"
                    tvPresetDesc.text = "Your own settings — any toggle change stays here\nWarning: ${EnginePrefs.getWarningTemp(ctx)}°C | Emergency: ${EnginePrefs.getEmergencyTemp(ctx)}°C"
                }
                else -> {
                    setToggleActive(btnBalanced)
                    tvPresetInfo.text = EnginePrefs.PRESET_BALANCED.name
                    tvPresetDesc.text = EnginePrefs.PRESET_BALANCED.description
                }
            }
        }

        updatePresetUI(EnginePrefs.getActivePreset(ctx))

        fun switchToCustom() {
            EnginePrefs.setActivePreset(ctx, "custom")
            updatePresetUI("custom")
        }

        fun applyAndRefresh(preset: EnginePrefs.Preset) {
            isApplyingPreset = true
            EnginePrefs.applyPreset(ctx, preset)
            updatePresetUI(EnginePrefs.getActivePreset(ctx))
            view.findViewById<SwitchMaterial>(R.id.switchVoice).isChecked = EnginePrefs.isVoiceEnabled(ctx)
            view.findViewById<SwitchMaterial>(R.id.switchBrightness).isChecked = EnginePrefs.isDropBrightness(ctx)
            view.findViewById<SwitchMaterial>(R.id.switchBatteryEst).isChecked = EnginePrefs.isBatteryEstimatorEnabled(ctx)
            view.findViewById<SwitchMaterial>(R.id.switchKillBg).isChecked = EnginePrefs.isKillAllBg(ctx)
            isApplyingPreset = false
            android.widget.Toast.makeText(ctx, "${preset.name} applied", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnCompetitive.setOnClickListener { applyAndRefresh(EnginePrefs.PRESET_COMPETITIVE) }
        btnBalanced.setOnClickListener { applyAndRefresh(EnginePrefs.PRESET_BALANCED) }
        btnBattery.setOnClickListener { applyAndRefresh(EnginePrefs.PRESET_BATTERY) }
        btnCustom.setOnClickListener {
            switchToCustom()
            android.widget.Toast.makeText(ctx, "Custom mode — change any setting below", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Thermal settings
        view.findViewById<View>(R.id.cardThermal).setOnClickListener {
            startActivity(Intent(ctx, SettingsActivity::class.java))
        }

        // General toggles — auto-switch to Custom on manual change only
        setupSwitch(view, R.id.switchVoice, EnginePrefs.isVoiceEnabled(ctx)) { EnginePrefs.setVoiceEnabled(ctx, it); if (!isApplyingPreset) switchToCustom() }
        setupSwitch(view, R.id.switchBrightness, EnginePrefs.isDropBrightness(ctx)) { EnginePrefs.setDropBrightness(ctx, it); if (!isApplyingPreset) switchToCustom() }
        setupSwitch(view, R.id.switchBatteryEst, EnginePrefs.isBatteryEstimatorEnabled(ctx)) { EnginePrefs.setBatteryEstimatorEnabled(ctx, it); if (!isApplyingPreset) switchToCustom() }
        setupSwitch(view, R.id.switchKillBg, EnginePrefs.isKillAllBg(ctx)) { EnginePrefs.setKillAllBg(ctx, it); if (!isApplyingPreset) switchToCustom() }

        // Backup / Restore
        view.findViewById<View>(R.id.btnBackup).setOnClickListener {
            BackupManager.backup(requireActivity() as AppCompatActivity)
        }
        view.findViewById<View>(R.id.btnRestore).setOnClickListener {
            BackupManager.restore(requireActivity() as AppCompatActivity)
        }

        // Version info
        try {
            val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val versionName = pInfo.versionName ?: "?"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
            view.findViewById<TextView>(R.id.tvAppVersion).text = "BGMI Engine Pro v$versionName"
            view.findViewById<TextView>(R.id.tvVersionCode).text = "Build $versionCode"
        } catch (_: Exception) {}
    }

    private fun setupSwitch(view: View, id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = view.findViewById<SwitchMaterial>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun setToggleActive(btn: TextView) {
        btn.setBackgroundResource(R.drawable.toggle_btn_bg_active)
        btn.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun setToggleInactive(btn: TextView) {
        btn.setBackgroundResource(R.drawable.toggle_btn_bg)
        btn.setTextColor(com.bgmi.engine.ui.Colors.onSurface(btn.context))
    }
}
