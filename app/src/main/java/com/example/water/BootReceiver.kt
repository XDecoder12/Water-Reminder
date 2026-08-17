package com.example.water

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * AlarmManager wipes every scheduled alarm on device reboot. Without this,
 * a user who had Pacman "active" before a restart would see the toggle still
 * on, but nothing would actually fire until they manually re-added reminders.
 *
 * This receiver reads the same "reminder_records" the UI already maintains
 * (id::type::data::displayText) and reschedules each one using the exact
 * same request ID it originally had, so per-reminder delete and the
 * toggle-off cancel-all loop keep working correctly afterward.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (!canScheduleExact) return

        val savedRecords = prefs.getStringSet("reminder_records", mutableSetOf()) ?: mutableSetOf()

        for (record in savedRecords) {
            val parts = record.split("::", limit = 4)
            if (parts.size != 4) continue

            val id = parts[0].toIntOrNull() ?: continue
            val type = parts[1]
            val data = parts[2]

            when (type) {
                "R" -> {
                    val hours = data.toFloatOrNull() ?: continue
                    MainActivity.scheduleAlarm(context, hours, id)
                }

                "E" -> {
                    val hourMinute = data.split(",")
                    val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: continue
                    val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: continue
                    MainActivity.scheduleExactAlarm(context, hour, minute, id)
                }
            }
        }
    }
}