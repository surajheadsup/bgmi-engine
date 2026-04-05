package com.bgmi.engine

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.bgmi.engine.ui.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WhitelistActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private var whitelist: MutableSet<String> = mutableSetOf()
    private var activeFilter = "ALL"
    private val filterButtons = mutableListOf<TextView>()

    // Known categories
    private val googleApps = setOf(
        "com.google.android.gms", "com.google.android.gms.persistent",
        "com.google.android.googlequicksearchbox", "com.google.android.inputmethod.latin",
        "com.google.android.gsf", "com.google.android.ext.services"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelist)

        recyclerView = findViewById(R.id.rvWhitelist)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvCount = findViewById(R.id.tvWhitelistCount)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val saved = getSharedPreferences("bgmi_engine_prefs", MODE_PRIVATE)
            .getStringSet("kill_whitelist", null)
        if (saved != null) whitelist.addAll(saved)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        setupFilters()
        refreshList()
    }

    private fun setupFilters() {
        val layout = findViewById<LinearLayout>(R.id.layoutFilter)
        val filters = listOf("ALL", "USER", "GOOGLE", "GAME")
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
                setTextColor(com.bgmi.engine.ui.Colors.onSurface(this@WhitelistActivity))
            }
            btn.setOnClickListener {
                activeFilter = f
                for (b in filterButtons) setToggleActive(b, false)
                setToggleActive(btn, true)
                refreshList()
            }
            layout.addView(btn)
            filterButtons.add(btn)
        }
        // Set ALL as active initially
        if (filterButtons.isNotEmpty()) setToggleActive(filterButtons[0], true)
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

    private fun refreshList() {
        val filtered = when (activeFilter) {
            "USER" -> whitelist.filter { !googleApps.contains(it) && !it.contains("pubg") }
            "GOOGLE" -> whitelist.filter { googleApps.contains(it) }
            "GAME" -> whitelist.filter { it.contains("pubg") || it.contains("game") }
            else -> whitelist.toList()
        }.sorted()

        tvCount.text = "${whitelist.size} whitelisted (showing ${filtered.size})"

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = WhitelistAdapter(filtered) { pkg ->
                whitelist.remove(pkg)
                saveWhitelist()
                refreshList()
            }
        }
    }

    private fun saveWhitelist() {
        getSharedPreferences("bgmi_engine_prefs", MODE_PRIVATE)
            .edit().putStringSet("kill_whitelist", whitelist).apply()
    }

    class WhitelistAdapter(
        private val items: List<String>,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<WhitelistAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvWlName)
            val tvCategory: TextView = view.findViewById(R.id.tvWlCategory)
            val btnRemove: TextView = view.findViewById(R.id.btnWlRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_whitelist, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pkg = items[position]
            holder.tvName.text = pkg

            // Category
            val cat = when {
                pkg.contains("pubg") || pkg.contains("game") -> "GAME"
                pkg.startsWith("com.google.") -> "GOOGLE"
                pkg.startsWith("com.vivo.") || pkg.startsWith("com.bbk.") -> "VIVO"
                else -> "USER"
            }
            holder.tvCategory.text = cat
            holder.tvCategory.setTextColor(when (cat) {
                "GAME" -> 0xFF4ECB71.toInt()
                "GOOGLE" -> 0xFF3498DB.toInt()
                "VIVO" -> 0xFFFFD700.toInt()
                else -> 0xFFAAAAAA.toInt()
            })

            holder.btnRemove.setOnClickListener { onRemove(pkg) }
        }

        override fun getItemCount() = items.size
    }
}
