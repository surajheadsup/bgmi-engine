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

class WidgetStop : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgetStop"
        private const val ACTION_STOP = "com.bgmi.engine.WIDGET_STOP"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateSingleWidget(context, manager, id)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetStop::class.java))
        onUpdate(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action == ACTION_STOP) {
            try {
                if (!EngineService.isRunning) {
                    showQuickNotification(context, "Engine not running")
                    return
                }
                context.stopService(Intent(context, EngineService::class.java))
                showQuickNotification(context, "Engine Stopped")
            } catch (e: Exception) {
                showQuickNotification(context, "Stop failed: ${e.message?.take(40)}")
            }
        } else if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: manager.getAppWidgetIds(ComponentName(context, WidgetStop::class.java))
            for (id in ids) { updateSingleWidget(context, manager, id) }
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
            nm.notify(997, notif)
        } catch (_: Exception) {}
    }

    private fun updateSingleWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_stop)
        val intent = Intent(context, WidgetStop::class.java).apply { action = ACTION_STOP }
        val pi = PendingIntent.getBroadcast(context, 300 + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetRoot, pi)
        views.setOnClickPendingIntent(R.id.widgetIcon, pi)
        manager.updateAppWidget(id, views)
    }
}
