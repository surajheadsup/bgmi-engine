package com.bgmi.engine.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bgmi.engine.R

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate and setContentView
        when (ThemeManager.getTheme(this)) {
            ThemeManager.LIGHT -> setTheme(R.style.AppTheme_Light)
            ThemeManager.AMOLED -> setTheme(R.style.AppTheme_Amoled)
            else -> setTheme(R.style.AppTheme)
        }
        super.onCreate(savedInstanceState)
    }
}
