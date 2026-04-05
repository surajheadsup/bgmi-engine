package com.bgmi.engine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast

class WidgetKill : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgetKill"
        private const val ACTION_KILL = "com.bgmi.engine.WIDGET_KILL_BG"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Log.d(TAG, "onUpdate called for ${ids.size} widgets")
        for (id in ids) {
            updateSingleWidget(context, manager, id)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled — first widget added")
        // Force update all widgets when first added
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetKill::class.java))
        onUpdate(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action == ACTION_KILL) {
            Log.d(TAG, "Kill action received!")
            if (!ShizukuManager.hasPermission()) {
                showQuickNotification(context, "Shizuku permission needed")
                return
            }
            showQuickNotification(context, "Killing background apps...")
            Thread {
                GameOptimizer.killHeavyApps()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showQuickNotification(context, "Background apps killed")
                }
            }.start()
        } else if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            // Also re-bind clicks on system update
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: manager.getAppWidgetIds(ComponentName(context, WidgetKill::class.java))
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }
    }

    private fun showQuickNotification(context: Context, message: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "bgmi_widget_alerts"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(channelId) == null) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(channelId, "Widget Actions",
                            android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                            description = "Feedback from home screen widgets"
                        }
                    )
                }
            }
            val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("BGMI Engine")
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(3000)
                .build()
            nm.notify(999, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Notification failed: ${e.message}")
        }
    }

    private fun updateSingleWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_kill)

        val intent = Intent(context, WidgetKill::class.java).apply {
            action = ACTION_KILL
        }
        val pi = PendingIntent.getBroadcast(
            context, 100 + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pi)
        views.setOnClickPendingIntent(R.id.widgetIcon, pi)

        manager.updateAppWidget(id, views)
        Log.d(TAG, "Widget $id updated with PendingIntent")
    }
}
