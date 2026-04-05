package com.bgmi.engine.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bgmi.engine.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ToolsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardProcessManager).setOnClickListener {
            startActivity(Intent(requireContext(), ProcessManagerActivity::class.java))
        }

        view.findViewById<View>(R.id.cardSystemApps).setOnClickListener {
            startActivity(Intent(requireContext(), SystemAppsActivity::class.java))
        }

        view.findViewById<View>(R.id.cardBuildInfo).setOnClickListener {
            startActivity(Intent(requireContext(), DeviceInfoActivity::class.java))
        }

        view.findViewById<View>(R.id.cardDiagnostics).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        view.findViewById<View>(R.id.cardExport).setOnClickListener {
            Thread {
                val file = ExportHelper.exportAllSessionsCsv(requireContext())
                if (isAdded) requireActivity().runOnUiThread {
                    if (file != null) ExportHelper.shareFile(requireContext(), file)
                    else Toast.makeText(requireContext(), "No sessions yet", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }
    }
}
