package com.bgmi.engine.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bgmi.engine.*
import com.google.android.material.switchmaterial.SwitchMaterial

class GamingFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_gaming, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardAnalytics).setOnClickListener {
            startActivity(Intent(requireContext(), DashboardActivity::class.java))
        }
        view.findViewById<View>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        view.findViewById<View>(R.id.cardLogs).setOnClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
        }

        // Optimization toggles
        val ctx = requireContext()
        setupSwitch(view, R.id.switchDns, EnginePrefs.isDnsOptEnabled(ctx)) { EnginePrefs.setDnsOptEnabled(ctx, it) }
        setupSwitch(view, R.id.switchTouch, EnginePrefs.isTouchBoostEnabled(ctx)) { EnginePrefs.setTouchBoostEnabled(ctx, it) }
        setupSwitch(view, R.id.switchGpu, EnginePrefs.isGpuBoostEnabled(ctx)) { EnginePrefs.setGpuBoostEnabled(ctx, it) }
        setupSwitch(view, R.id.switchDnd, EnginePrefs.isAutoDnd(ctx)) { EnginePrefs.setAutoDnd(ctx, it) }
        setupSwitch(view, R.id.switchClean, EnginePrefs.isPreGameCleanEnabled(ctx)) { EnginePrefs.setPreGameCleanEnabled(ctx, it) }
    }

    private fun setupSwitch(view: View, id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = view.findViewById<SwitchMaterial>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }
}
