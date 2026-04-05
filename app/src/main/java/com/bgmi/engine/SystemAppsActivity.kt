package com.bgmi.engine

import android.os.Bundle
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.bgmi.engine.ui.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemAppsActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStatus: TextView
    private var allApps: List<SystemAppInfo> = emptyList()
    private var filteredApps: List<SystemAppInfo> = emptyList()
    private var activeFilter = "ALL"
    private val filterButtons = mutableListOf<TextView>()

    data class SystemAppInfo(
        val packageName: String,
        val appName: String,
        val isSystem: Boolean,
        val isEnabled: Boolean,
        val isInstalled: Boolean,
        val isBloatware: Boolean = false,
        val bloatReason: String = ""
    )

    // Safe-to-remove bloatware — these can be disabled/uninstalled without breaking the OS
    private val BLOATWARE = mapOf(
        // Vivo / iQOO bloat
        "com.vivo.appstore" to "iQOO/Vivo App Store",
        "com.vivo.browser" to "Vivo Browser",
        "com.vivo.weather" to "Vivo Weather",
        "com.vivo.vms" to "Vivo Message Service",
        "com.vivo.space" to "Vivo Space",
        "com.vivo.game" to "Vivo Game Center",
        "com.vivo.easyshare" to "Vivo Easy Share",
        "com.vivo.assistant" to "Vivo Assistant",
        "com.vivo.magazine" to "Vivo Magazine",
        "com.vivo.email" to "Vivo Email",
        "com.vivo.minscreen" to "Vivo Mini Screen",
        "com.vivo.agent" to "Vivo Agent",
        "com.vivo.collage" to "Vivo Collage",
        "com.vivo.compass" to "Vivo Compass",
        "com.iqoo.store" to "iQOO Store",
        "com.iqoo.engineermode" to "iQOO Engineer Mode",
        // Samsung bloat
        "com.samsung.android.app.spage" to "Samsung Free / Bixby",
        "com.samsung.android.bixby.agent" to "Bixby Voice",
        "com.samsung.android.visionintelligence" to "Bixby Vision",
        "com.samsung.android.game.gamehome" to "Samsung Game Launcher",
        "com.samsung.android.app.tips" to "Samsung Tips",
        "com.samsung.android.mobileservice" to "Samsung Experience Service",
        "com.samsung.android.app.watchmanagerstub" to "Galaxy Watch Stub",
        "com.samsung.android.arzone" to "AR Zone",
        // Xiaomi / Redmi bloat
        "com.miui.analytics" to "MIUI Analytics",
        "com.miui.msa.global" to "MIUI System Ads",
        "com.miui.videoplayer" to "Mi Video",
        "com.miui.gallery" to "Mi Gallery (if using Google Photos)",
        "com.miui.player" to "Mi Music",
        "com.miui.weather2" to "Mi Weather",
        "com.miui.yellowpage" to "Mi Yellow Pages",
        "com.xiaomi.glgm" to "Xiaomi Game Center",
        "com.xiaomi.gamecenter" to "Xiaomi Games",
        // OnePlus bloat
        "com.oneplus.store" to "OnePlus Store",
        "com.oneplus.membership" to "OnePlus Membership",
        "com.heytap.browser" to "HeyTap Browser",
        "com.heytap.market" to "HeyTap App Market",
        "com.coloros.gamespace" to "ColorOS Game Space",
        // Google bloat (safe to remove if not used)
        "com.google.android.apps.magazines" to "Google News",
        "com.google.android.videos" to "Google TV",
        "com.google.android.music" to "YouTube Music (preinstall)",
        "com.google.android.apps.tachyon" to "Google Meet",
        "com.google.android.apps.docs" to "Google Docs",
        "com.google.android.apps.maps" to "Google Maps (if not used)",
        "com.google.ar.core" to "Google AR Core",
        // Facebook / Meta bloat
        "com.facebook.system" to "Facebook System Service",
        "com.facebook.appmanager" to "Facebook App Manager",
        "com.facebook.services" to "Facebook Services",
        "com.facebook.katana" to "Facebook App",
        // Microsoft bloat
        "com.microsoft.appmanager" to "Microsoft App Manager",
        "com.microsoft.skydrive" to "OneDrive",
        "com.linkedin.android" to "LinkedIn",
        "com.microsoft.office.officehubrow" to "Microsoft Office Hub",
        // Other common bloat
        "com.netflix.partner.activation" to "Netflix Activation",
        "com.spotify.music" to "Spotify (preinstall)",
        "com.amazon.mShop.android.shopping" to "Amazon Shopping (preinstall)",
        "in.amazon.mShop.android.shopping" to "Amazon India (preinstall)",
        "com.booking" to "Booking.com",
        "com.tripadvisor.tripadvisor" to "TripAdvisor",
        "flipkart.com" to "Flipkart (preinstall)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_apps)

        recyclerView = findViewById(R.id.rvApps)
        tvStatus = findViewById(R.id.tvStatus)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupFilters()
        setupSearch()
        loadApps()
    }

    private fun setupFilters() {
        val layout = findViewById<LinearLayout>(R.id.layoutFilters)
        val filters = listOf("ALL", "BLOAT", "USER", "SYSTEM", "DISABLED")
        for ((index, f) in filters.withIndex()) {
            val btn = TextView(this).apply {
                text = f
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.toggle_btn_bg)
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.marginStart = dpToPx(3)
                if (index < filters.size - 1) lp.marginEnd = dpToPx(3)
                layoutParams = lp
                setTextColor(com.bgmi.engine.ui.Colors.onSurface(this@SystemAppsActivity))
            }
            btn.setOnClickListener {
                activeFilter = f
                for (b in filterButtons) setToggleActive(b, false)
                setToggleActive(btn, true)
                applyFilter()
            }
            layout.addView(btn)
            filterButtons.add(btn)
        }
        if (filterButtons.isNotEmpty()) setToggleActive(filterButtons[0], true)
    }

    private fun setupSearch() {
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                applyFilter()
            }
        })
    }

    private fun loadApps() {
        tvStatus.text = "Loading apps..."
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = mutableListOf<SystemAppInfo>()

            // Get all packages with their state
            val result = ShizukuManager.runCommand("pm list packages -f")
            if (!result.success) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Failed to load apps (Shizuku error)"
                }
                return@launch
            }

            // Get disabled packages
            val disabledResult = ShizukuManager.runCommand("pm list packages -d")
            val disabledPkgs = if (disabledResult.success) {
                disabledResult.output.lines().map { it.removePrefix("package:").trim() }.toSet()
            } else emptySet()

            // Get system packages
            val systemResult = ShizukuManager.runCommand("pm list packages -s")
            val systemPkgs = if (systemResult.success) {
                systemResult.output.lines().map { it.removePrefix("package:").trim() }.toSet()
            } else emptySet()

            // Parse all packages
            for (line in result.output.lines()) {
                val raw = line.trim()
                if (!raw.startsWith("package:")) continue
                // Format: package:/path/to/apk=com.example.package
                val eqIndex = raw.lastIndexOf("=")
                if (eqIndex < 0) continue
                val pkg = raw.substring(eqIndex + 1).trim()
                if (pkg.isEmpty()) continue

                val isSystem = systemPkgs.contains(pkg)
                val isEnabled = !disabledPkgs.contains(pkg)
                val bloatEntry = BLOATWARE.entries.find { pkg.startsWith(it.key) || pkg == it.key }
                val isBloat = bloatEntry != null

                // Derive a readable name from the package
                val appName = bloatEntry?.value ?: pkg.split(".").last().replaceFirstChar { it.uppercase() }

                apps.add(SystemAppInfo(pkg, appName, isSystem, isEnabled, true, isBloat, bloatEntry?.value ?: ""))
            }

            apps.sortBy { it.packageName }
            allApps = apps

            withContext(Dispatchers.Main) {
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val searchQuery = findViewById<TextInputEditText>(R.id.etSearch).text?.toString()?.lowercase() ?: ""

        filteredApps = allApps.filter { app ->
            val matchesFilter = when (activeFilter) {
                "BLOAT" -> app.isBloatware && app.isEnabled
                "USER" -> !app.isSystem
                "SYSTEM" -> app.isSystem
                "DISABLED" -> !app.isEnabled
                else -> true
            }
            val matchesSearch = searchQuery.isEmpty() ||
                app.packageName.lowercase().contains(searchQuery) ||
                app.appName.lowercase().contains(searchQuery)
            matchesFilter && matchesSearch
        }

        val bloatCount = allApps.count { it.isBloatware && it.isEnabled }
        tvStatus.text = if (activeFilter == "BLOAT") {
            "$bloatCount bloatware detected — safe to disable"
        } else {
            "${filteredApps.size} apps (${allApps.count { !it.isSystem }} user, ${allApps.count { it.isSystem }} system, $bloatCount bloat)"
        }
        recyclerView.adapter = SystemAppAdapter(filteredApps) { app, action ->
            performAction(app, action)
        }
    }

    private fun performAction(app: SystemAppInfo, action: String) {
        if (action == "remove") {
            // Bloatware removal — show options dialog
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove ${app.appName}")
                .setMessage("${app.packageName}\n\nChoose removal method:")
                .setPositiveButton("Uninstall (user)") { _, _ ->
                    executeAction(app, "uninstall")
                }
                .setNeutralButton("Disable") { _, _ ->
                    executeAction(app, "disable")
                }
                .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                .show()
            return
        }

        val message = when (action) {
            "disable" -> "Disable ${app.appName}?\n\nThis will prevent the app from running. You can re-enable it later."
            "enable" -> "Enable ${app.appName}?"
            "uninstall" -> "Uninstall ${app.appName} for current user?\n\nSystem apps can be restored via factory reset."
            else -> return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm: ${action.replaceFirstChar { it.uppercase() }}")
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                executeAction(app, action)
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun executeAction(app: SystemAppInfo, action: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Try primary command
            val cmd = when (action) {
                "disable" -> "pm disable-user --user 0 ${app.packageName}"
                "enable" -> "pm enable ${app.packageName}"
                "uninstall" -> "pm uninstall -k --user 0 ${app.packageName}"
                else -> return@launch
            }

            val result = ShizukuManager.runCommand(cmd)

            // If disable failed, try uninstall-for-user as fallback
            if (!result.success && action == "disable") {
                val fallback = ShizukuManager.runCommand("pm uninstall -k --user 0 ${app.packageName}")
                withContext(Dispatchers.Main) {
                    if (fallback.success) {
                        MaterialAlertDialogBuilder(this@SystemAppsActivity)
                            .setTitle("Removed")
                            .setMessage("${app.appName} removed for current user (disable failed, used uninstall instead).\n\nRestore via factory reset if needed.")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                        loadApps()
                    } else {
                        MaterialAlertDialogBuilder(this@SystemAppsActivity)
                            .setTitle("Protected by System")
                            .setMessage("${app.appName} cannot be disabled or removed.\n\nThis app is protected by the OS at a level that requires root access.\n\nError: ${fallback.error.take(100)}")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (result.success) {
                    val msg = when (action) {
                        "uninstall" -> "${app.appName} removed for current user.\nRestore via factory reset if needed."
                        "enable" -> "${app.appName} enabled."
                        else -> "${app.appName}: ${result.output.trim()}"
                    }
                    MaterialAlertDialogBuilder(this@SystemAppsActivity)
                        .setTitle("Done")
                        .setMessage(msg)
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                    loadApps()
                } else {
                    if (action == "uninstall") {
                        MaterialAlertDialogBuilder(this@SystemAppsActivity)
                            .setTitle("Protected by System")
                            .setMessage("${app.appName} cannot be removed.\n\nThis is a core system component that requires root.\n\nError: ${result.error.take(100)}")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    } else {
                        MaterialAlertDialogBuilder(this@SystemAppsActivity)
                            .setTitle("Failed")
                            .setMessage(result.error.take(200))
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            }
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

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // --- Adapter ---
    class SystemAppAdapter(
        private val apps: List<SystemAppInfo>,
        private val onAction: (SystemAppInfo, String) -> Unit
    ) : RecyclerView.Adapter<SystemAppAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvAppIcon: TextView = view.findViewById(R.id.tvAppIcon)
            val tvAppName: TextView = view.findViewById(R.id.tvAppName)
            val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
            val tvAppState: TextView = view.findViewById(R.id.tvAppState)
            val btnAction: TextView = view.findViewById(R.id.btnAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_system_app, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]

            holder.tvAppName.text = app.appName
            holder.tvPackageName.text = app.packageName

            if (app.isBloatware && app.isEnabled) {
                holder.tvAppIcon.text = "⚠️"
                holder.tvAppState.text = "BLOATWARE"
                holder.tvAppState.setTextColor(0xFFFF8C00.toInt())
                holder.btnAction.text = "Remove"
                holder.btnAction.setTextColor(0xFFFF6B6B.toInt())
                holder.btnAction.setOnClickListener { onAction(app, "remove") }
            } else if (!app.isEnabled) {
                holder.tvAppIcon.text = "⛔"
                holder.tvAppState.text = "DISABLED"
                holder.tvAppState.setTextColor(0xFFFF6B6B.toInt())
                holder.btnAction.text = "Enable"
                holder.btnAction.setTextColor(0xFF4ECB71.toInt())
                holder.btnAction.setOnClickListener { onAction(app, "enable") }
            } else if (app.isSystem) {
                holder.tvAppIcon.text = "📦"
                holder.tvAppState.text = "SYSTEM"
                holder.tvAppState.setTextColor(0xFF3498DB.toInt())
                holder.btnAction.text = "Disable"
                holder.btnAction.setTextColor(0xFFFFD700.toInt())
                holder.btnAction.setOnClickListener { onAction(app, "disable") }
            } else {
                holder.tvAppIcon.text = "📱"
                holder.tvAppState.text = "USER"
                holder.tvAppState.setTextColor(0xFF4ECB71.toInt())
                holder.btnAction.text = "Uninstall"
                holder.btnAction.setTextColor(0xFFFF6B6B.toInt())
                holder.btnAction.setOnClickListener { onAction(app, "uninstall") }
            }
        }

        override fun getItemCount() = apps.size
    }
}
