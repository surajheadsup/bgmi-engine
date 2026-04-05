package com.bgmi.engine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bgmi.engine.ui.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val toolsFragment = ToolsFragment()
    private val gamingFragment = GamingFragment()
    private val settingsFragment = SettingsFragment()
    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        when (ThemeManager.getTheme(this)) {
            ThemeManager.LIGHT -> setTheme(R.style.AppTheme_Light)
            ThemeManager.AMOLED -> setTheme(R.style.AppTheme_Amoled)
            else -> setTheme(R.style.AppTheme)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_nav)

        ShizukuManager.init()

        // Add all fragments once, hide all except home
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.navHostContainer, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.navHostContainer, gamingFragment, "gaming").hide(gamingFragment)
                .add(R.id.navHostContainer, toolsFragment, "tools").hide(toolsFragment)
                .add(R.id.navHostContainer, homeFragment, "home")
                .commit()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Theme-aware nav bar color
        val tv = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, tv, true)
        bottomNav.setBackgroundColor(tv.data)
        window.navigationBarColor = tv.data

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_tools -> toolsFragment
                R.id.nav_gaming -> gamingFragment
                R.id.nav_settings -> settingsFragment
                else -> homeFragment
            }
            if (target != activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit()
                activeFragment = target
            }
            true
        }

        // Restore tab after theme change
        val selectedTab = savedInstanceState?.getInt("selected_tab", R.id.nav_home) ?: R.id.nav_home
        if (selectedTab != R.id.nav_home) {
            bottomNav.selectedItemId = selectedTab
        }

        // Check for backup restore on fresh install
        BackupManager.checkAndPromptRestore(this)

        // Check for OTA updates
        UpdateManager.checkOnAppOpen(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        outState.putInt("selected_tab", bottomNav.selectedItemId)
    }

    override fun onDestroy() {
        ShizukuManager.destroy()
        super.onDestroy()
    }
}
