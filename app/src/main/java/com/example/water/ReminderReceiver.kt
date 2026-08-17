package com.example.water

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return

        // 1. Check lock status ONLY — that's the sole trigger for the notification
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        // 2. Execute exact logic based on lock state
        if (isLocked) {
            // Phone is LOCKED -> Show Notification Only
            showMissedNotification(context)
        } else {
            // Phone is UNLOCKED (screen on and in use) -> Show Pacman Animation Only
            context.startService(Intent(context, SpiderOverlayService::class.java))
        }

        // 3. Reschedule the next alarm — guard against the user having revoked
        //    exact-alarm permission after this reminder was originally scheduled
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (!canScheduleExact) return

        val requestId = intent.getIntExtra("REQUEST_ID", 0)
        val isRepeating = intent.getBooleanExtra("IS_REPEATING", true)
        if (isRepeating) {
            val hours = intent.getFloatExtra("HOURS", prefs.getFloat("frequency_hours", 2.0f))
            MainActivity.scheduleAlarm(context, hours, requestId)
        } else {
            val hour = intent.getIntExtra("HOUR", 8)
            val minute = intent.getIntExtra("MINUTE", 0)
            MainActivity.scheduleExactAlarm(context, hour, minute, requestId)
        }
    }

    private fun showMissedNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "water_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "Water Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Drink Water! 💧")
            .setContentText("It's time to hydrate.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        manager.notify(101, notification)
    }
}