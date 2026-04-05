package com.bgmi.engine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class EngineWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.bgmi.engine.WIDGET_TOGGLE"
        private const val ACTION_KILL = "com.bgmi.engine.WIDGET_KILL"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EngineWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, EngineWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (EngineService.isRunning) {
                    context.stopService(Intent(context, EngineService::class.java))
                } else {
                    val serviceIntent = Intent(context, EngineService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                refreshWidgets(context)
            }
            ACTION_KILL -> {
                if (ShizukuManager.hasPermission()) {
                    Thread { GameOptimizer.killHeavyApps() }.start()
                }
                refreshWidgets(context)
            }
        }
    }

    private fun refreshWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, EngineWidget::class.java))
        onUpdate(context, manager, ids)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val running = EngineService.isRunning
        val stats = EngineService.latestStats

        // Status
        views.setTextViewText(R.id.widgetStatus, if (running) "ON" else "OFF")
        views.setTextColor(R.id.widgetStatus,
            if (running) 0xFF4ECB71.toInt() else 0xFFFF6B6B.toInt())

        // Action button icon + background
        if (running) {
            views.setTextViewText(R.id.widgetActionIcon, "■")
            views.setInt(R.id.widgetActionBg, "setBackgroundResource", R.drawable.widget_btn_stop)
        } else {
            views.setTextViewText(R.id.widgetActionIcon, "▶")
            views.setInt(R.id.widgetActionBg, "setBackgroundResource", R.drawable.widget_btn_start)
        }

        // Stats
        if (running && stats != null) {
            val temp = maxOf(
                if (stats.thermalCelsius > 0) stats.thermalCelsius else 0.0,
                stats.batteryTempCelsius.toDouble()
            )
            views.setTextViewText(R.id.widgetTemp, "🌡 ${String.format("%.0f", temp)}°C  🔋 ${stats.batteryPercent}%")
            views.setTextViewText(R.id.widgetMode, EngineService.currentMode.displayName)
        } else {
            views.setTextViewText(R.id.widgetTemp, "🌡 --°C  🔋 --%")
            views.setTextViewText(R.id.widgetMode, "--")
        }

        // Toggle engine intent
        val toggleIntent = Intent(context, EngineWidget::class.java).apply {
            action = ACTION_TOGGLE
        }
        views.setOnClickPendingIntent(R.id.widgetAction, PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        // Kill apps intent
        val killIntent = Intent(context, EngineWidget::class.java).apply {
            action = ACTION_KILL
        }
        views.setOnClickPendingIntent(R.id.widgetKill, PendingIntent.getBroadcast(
            context, 1, killIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        // Open app on header tap
        val openIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetStatus, openIntent)

        manager.updateAppWidget(id, views)
    }
}
