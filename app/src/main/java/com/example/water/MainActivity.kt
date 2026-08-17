package com.example.water

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isEmpty
import androidx.lifecycle.lifecycleScope
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // Must be registered as a class member (before onStart) — registering this
    // inside onCreate as a local val is not allowed by the ActivityResult API.
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Without notification permission, reminders won't show while WateR is locked",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- NEW: Request POST_NOTIFICATIONS at runtime (required on Android 13+) ---
        // Without this, showMissedNotification() in ReminderReceiver silently does
        // nothing on API 33+ devices where the user hasn't explicitly granted it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prefs = getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
        val db = WaterDatabase.getDatabase(this)

        val toggleSwitch = findViewById<SwitchMaterial>(R.id.toggleSwitch)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val activeRemindersContainer = findViewById<LinearLayout>(R.id.activeRemindersContainer)

        toggleSwitch.isChecked = prefs.getBoolean("is_active", false)

        // --- NEW: Helper to cancel one specific alarm by its request ID ---
        fun cancelAlarmById(id: Int) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ReminderReceiver::class.java)
            val pendingIntent =
                PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pendingIntent)
        }

        // --- NEW: Helper to remove just one record from storage, leaving the rest intact ---
        fun deleteAlarmData(id: Int) {
            val currentRecords =
                prefs.getStringSet("reminder_records", mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
            currentRecords.removeAll { it.split("::", limit = 2).getOrNull(0)?.toIntOrNull() == id }
            prefs.edit { putStringSet("reminder_records", currentRecords) }
        }

        // --- NEW: Helper Function to Draw the Blue Cards, now with a per-card delete button ---
        fun addReminderCardToUI(id: Int, displayText: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor("#DBEAFE".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 20) }
            }

            val label = TextView(this).apply {
                text = displayText
                textSize = 16f
                setPadding(40, 40, 20, 40)
                setTextColor("#1D4ED8".toColorInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val deleteButton = TextView(this).apply {
                text = "✕"
                textSize = 18f
                setPadding(30, 40, 40, 40)
                setTextColor("#1D4ED8".toColorInt())
                contentDescription = "Delete reminder"
            }

            row.addView(label)
            row.addView(deleteButton)

            deleteButton.setOnClickListener {
                cancelAlarmById(id)
                deleteAlarmData(id)
                activeRemindersContainer.removeView(row)

                // If that was the last reminder, there's nothing left for Pacman to run on
                if (activeRemindersContainer.isEmpty()) {
                    toggleSwitch.isChecked = false
                    prefs.edit { putBoolean("is_active", false) }
                }

                Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show()
            }

            activeRemindersContainer.addView(row)
        }

        // --- NEW: Load saved reminders when app opens (Fixes UI Amnesia) ---
        // Each record is stored as "id::type::data::displayText" so identical display
        // text (e.g. two reminders both saying "Repeating every 2.0 hours") no longer
        // collapses into a single Set entry — the id keeps every record distinct.
        val savedRecords = prefs.getStringSet("reminder_records", mutableSetOf()) ?: mutableSetOf()
        for (record in savedRecords) {
            val parts = record.split("::", limit = 4)
            val id = parts.getOrNull(0)?.toIntOrNull()
            val displayText = parts.getOrNull(3)
            if (id != null && displayText != null) {
                addReminderCardToUI(id, displayText)
            }
        }

        // --- INTAKE TRACKER UI ---
        val progressText = findViewById<TextView>(R.id.progressText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val btnAddSmall = findViewById<Button>(R.id.btnAddSmall)
        val btnSubSmall = findViewById<Button>(R.id.btnSubSmall)
        val customAmountInput = findViewById<EditText>(R.id.customAmountInput)
        val btnAddCustom = findViewById<Button>(R.id.btnAddCustom)
        val streakCalendar = findViewById<CalendarView>(R.id.streakCalendar)

        fun updateCalendar() {
            lifecycleScope.launch(Dispatchers.IO) {
                val allLogs = db.waterDao().getAllLogs()
                val events = mutableListOf<CalendarDay>()

                for (log in allLogs) {
                    if (log.goalReached) {
                        val parts = log.date.split("-")
                        if (parts.size == 3) {
                            val calendar = Calendar.getInstance()
                            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                            val calendarDay = CalendarDay(calendar)
                            calendarDay.imageResource = android.R.drawable.presence_online
                            events.add(calendarDay)
                        }
                    }
                }
                withContext(Dispatchers.Main) { streakCalendar.setCalendarDays(events) }
            }
        }

        fun updateUI(log: WaterLog?) {
            val currentMl = log?.amountDrankMl ?: 0
            val goalMl = (prefs.getFloat("daily_goal", 2.5f) * 1000).toInt()
            progressText.text = getString(R.string.progress_format, currentMl, goalMl)
            progressBar.max = goalMl
            progressBar.progress = currentMl
        }

        fun addWater(amount: Int) {
            lifecycleScope.launch(Dispatchers.IO) {
                val goalMl = (prefs.getFloat("daily_goal", 2.5f) * 1000).toInt()
                var currentLog = db.waterDao().getLogByDate(todayDate)

                if (currentLog == null) {
                    val startingAmount = if (amount > 0) amount else 0
                    currentLog =
                        WaterLog(todayDate, startingAmount, goalMl, startingAmount >= goalMl)
                } else {
                    var newAmount = currentLog.amountDrankMl + amount
                    if (newAmount < 0) newAmount = 0
                    currentLog = currentLog.copy(
                        amountDrankMl = newAmount,
                        dailyGoalMl = goalMl,
                        goalReached = newAmount >= goalMl
                    )
                }

                db.waterDao().insertOrUpdate(currentLog)
                withContext(Dispatchers.Main) {
                    updateUI(currentLog)
                    updateCalendar()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val log = db.waterDao().getLogByDate(todayDate)
            withContext(Dispatchers.Main) { updateUI(log) }
        }
        updateCalendar()

        btnAddSmall.setOnClickListener { addWater(250) }
        btnSubSmall.setOnClickListener { addWater(-250) }
        btnAddCustom.setOnClickListener { view ->
            val customText = customAmountInput.text.toString().trim()
            val customAmount = customText.toIntOrNull()
            if (customAmount == null) {
                Snackbar.make(
                    view,
                    "Please enter a valid whole number of ml",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            addWater(customAmount)
            customAmountInput.text.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        val screenHome = findViewById<View>(R.id.screenHome)
        val screenCalendarLayout = findViewById<View>(R.id.screenCalendar)
        val bottomNavigation =
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        val selectedDayIntakeText = findViewById<TextView>(R.id.selectedDayIntakeText)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    screenHome.visibility = View.VISIBLE
                    screenCalendarLayout.visibility = View.GONE
                    true
                }

                R.id.nav_calendar -> {
                    screenHome.visibility = View.GONE
                    screenCalendarLayout.visibility = View.VISIBLE
                    true
                }

                else -> false
            }
        }

        streakCalendar.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val clickedCalendar = calendarDay.calendar
                val clickedDateString =
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(clickedCalendar.time)

                lifecycleScope.launch(Dispatchers.IO) {
                    val logForClickedDay = db.waterDao().getLogByDate(clickedDateString)
                    withContext(Dispatchers.Main) {
                        if (logForClickedDay != null) {
                            val amount = logForClickedDay.amountDrankMl
                            val goal = logForClickedDay.dailyGoalMl
                            selectedDayIntakeText.text =
                                getString(R.string.intake_format, amount, goal)

                            selectedDayIntakeText.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 32f
                                setColor(if (logForClickedDay.goalReached) "#10B981".toColorInt() else "#F59E0B".toColorInt())
                            }
                            selectedDayIntakeText.setTextColor(Color.WHITE)
                        } else {
                            selectedDayIntakeText.setText(R.string.no_water_logged)
                            selectedDayIntakeText.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 32f
                                setColor("#6B7280".toColorInt())
                            }
                            selectedDayIntakeText.setTextColor(Color.WHITE)
                        }
                    }
                }
            }
        })

        val goalInput = findViewById<EditText>(R.id.goalInput)
        val btnUpdateGoal = findViewById<Button>(R.id.btnUpdateGoal)
        val btnTabRepeat = findViewById<Button>(R.id.btnTabRepeat)
        val btnTabAtTime = findViewById<Button>(R.id.btnTabAtTime)
        val inputRepeatInterval = findViewById<EditText>(R.id.inputRepeatInterval)
        val inputSpecificTime = findViewById<TextView>(R.id.inputSpecificTime)
        val btnAddReminder = findViewById<Button>(R.id.btnAddReminder)

        goalInput.setText(prefs.getFloat("daily_goal", 2.5f).toString())

        btnUpdateGoal.setOnClickListener { view ->
            val goalText = goalInput.text.toString().trim()
            val goalValue = goalText.toFloatOrNull()
            if (goalValue == null || goalValue <= 0f) {
                Snackbar.make(
                    view,
                    "Please enter a valid goal in liters (e.g. 2.5)",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            prefs.edit { putFloat("daily_goal", goalValue) }
            addWater(0)

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            goalInput.clearFocus()
            Toast.makeText(this, "Daily goal updated!", Toast.LENGTH_SHORT).show()
        }

        var isRepeatTabActive = true

        btnTabRepeat.setOnClickListener {
            isRepeatTabActive = true
            btnTabRepeat.backgroundTintList = ColorStateList.valueOf("#1D4ED8".toColorInt())
            btnTabRepeat.setTextColor(Color.WHITE)
            btnTabAtTime.backgroundTintList = ColorStateList.valueOf("#E5E7EB".toColorInt())
            btnTabAtTime.setTextColor("#6B7280".toColorInt())
            inputRepeatInterval.visibility = View.VISIBLE
            inputSpecificTime.visibility = View.GONE
        }

        btnTabAtTime.setOnClickListener {
            isRepeatTabActive = false
            btnTabAtTime.backgroundTintList = ColorStateList.valueOf("#1D4ED8".toColorInt())
            btnTabAtTime.setTextColor(Color.WHITE)
            btnTabRepeat.backgroundTintList = ColorStateList.valueOf("#E5E7EB".toColorInt())
            btnTabRepeat.setTextColor("#6B7280".toColorInt())
            inputSpecificTime.visibility = View.VISIBLE
            inputRepeatInterval.visibility = View.GONE
        }

        var selectedHour = 8
        var selectedMinute = 0

        inputSpecificTime.setOnClickListener {
            val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute

                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                inputSpecificTime.text = timeFormat.format(calendar.time)
                inputSpecificTime.setTextColor(Color.BLACK)

            }, selectedHour, selectedMinute, false)
            timePickerDialog.show()
        }

        // --- NEW: Make sure we're actually allowed to schedule exact alarms (Android 12+) ---
        fun canScheduleExactAlarms(): Boolean {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true // permission doesn't exist pre-Android 12
            }
        }

        fun requestExactAlarmPermission() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Toast.makeText(
                    this,
                    "Please allow WateR to schedule exact alarms so reminders fire on time",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // --- NEW: Helper function to guarantee a request-code ID is actually free ---
        // Prevents two reminders (e.g. a repeating one and an at-time one) from ever
        // landing on the same PendingIntent request code and silently overwriting
        // each other's alarm.
        fun generateUniqueAlarmId(preferredId: Int): Int {
            val existingIds =
                (prefs.getStringSet("reminder_records", mutableSetOf()) ?: mutableSetOf())
                    .mapNotNull { it.split("::", limit = 2).getOrNull(0)?.toIntOrNull() }
                    .toSet()
            var candidate = preferredId
            while (existingIds.contains(candidate)) {
                candidate++
            }
            return candidate
        }

        // --- NEW: Helper function to save alarms to memory ---
        // Format: "id::type::data::displayText"
        //   type = "R" (repeating) or "E" (exact time)
        //   data = hours (for R) or "hour,minute" (for E)
        // Storing type+data alongside the id lets a boot receiver reconstruct
        // and reschedule every alarm after the device restarts.
        fun saveAlarmData(id: Int, type: String, data: String, displayText: String) {
            val currentRecords =
                prefs.getStringSet("reminder_records", mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
            currentRecords.add("$id::$type::$data::$displayText")
            prefs.edit { putStringSet("reminder_records", currentRecords) }
        }

        btnAddReminder.setOnClickListener { view ->
            if (!canScheduleExactAlarms()) {
                requestExactAlarmPermission()
                return@setOnClickListener
            }

            val reminderTextDisplay: String
            val newReminderId: Int

            if (isRepeatTabActive) {
                val intervalText = inputRepeatInterval.text.toString().trim()
                val hours = intervalText.toFloatOrNull()
                if (hours == null || hours <= 0f) {
                    Snackbar.make(
                        view,
                        "Please enter a valid interval in hours (e.g. 2.0)",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Repeating alarms live in the 10000+ ID range so they can never
                // collide with "at time" alarms (which use hour*100+minute, max 2359),
                // and generateUniqueAlarmId re-checks against everything already saved.
                val uniqueId =
                    generateUniqueAlarmId(10000 + (System.currentTimeMillis() % 10000).toInt())
                scheduleAlarm(this, hours, uniqueId)
                newReminderId = uniqueId

                reminderTextDisplay = getString(
                    R.string.reminder_card_format,
                    getString(R.string.repeating_format, hours.toString())
                )
                saveAlarmData(uniqueId, "R", hours.toString(), reminderTextDisplay)
                inputRepeatInterval.text.clear()

            } else {
                if (inputSpecificTime.text == getString(R.string.hint_time)) {
                    Snackbar.make(view, "Please select a time!", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Run through generateUniqueAlarmId too — if the user adds two
                // reminders at the exact same time, they now get two independent
                // alarms instead of the second one silently overwriting the first.
                val exactId = generateUniqueAlarmId(selectedHour * 100 + selectedMinute)
                scheduleExactAlarm(this, selectedHour, selectedMinute, exactId)
                newReminderId = exactId

                reminderTextDisplay = getString(
                    R.string.reminder_card_format,
                    getString(R.string.specific_time_format, inputSpecificTime.text)
                )
                saveAlarmData(exactId, "E", "$selectedHour,$selectedMinute", reminderTextDisplay)

                inputSpecificTime.text = getString(R.string.hint_time)
                inputSpecificTime.setTextColor("#6B7280".toColorInt())
            }

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            addReminderCardToUI(newReminderId, reminderTextDisplay)
            Toast.makeText(this, "Spydy reminder added! 👾", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            val isActive = toggleSwitch.isChecked

            if (isActive && activeRemindersContainer.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please add a reminder first to activate Pacman! 👻",
                    Toast.LENGTH_LONG
                ).show()
                toggleSwitch.isChecked = false
                return@setOnClickListener
            }

            prefs.edit { putBoolean("is_active", isActive) }

            if (isActive) {
                Toast.makeText(this, "Pacman activated! 👾", Toast.LENGTH_SHORT).show()
            } else {
                // --- Fix Bug 2: Loop through memory and kill EVERY alarm accurately ---
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val savedRecords =
                    prefs.getStringSet("reminder_records", mutableSetOf()) ?: mutableSetOf()

                for (record in savedRecords) {
                    val id = record.split("::", limit = 2).getOrNull(0)?.toIntOrNull() ?: continue
                    val intent = Intent(this, ReminderReceiver::class.java)
                    val pendingIntent =
                        PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_IMMUTABLE)
                    alarmManager.cancel(pendingIntent)
                }

                // Clear the memory and the screen completely
                prefs.edit {
                    remove("reminder_records")
                }
                activeRemindersContainer.removeAllViews()

                Toast.makeText(
                    this,
                    "Pacman reminder disabled. All alarms cleared.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // --- NEW: Request Overlay Permission Logic ---
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            Toast.makeText(
                this,
                "Please allow WateR to display the Pacman reminder!",
                Toast.LENGTH_LONG
            ).show()
            startActivity(intent)
        }
    }

    companion object {
        // Updated to accept a unique ID
        fun scheduleAlarm(context: Context, hours: Float, requestCode: Int = 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("IS_REPEATING", true)
                putExtra("HOURS", hours)
                putExtra("REQUEST_ID", requestCode)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + (hours * 3600 * 1000).toLong()

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        // Updated to explicitly accept the ID
        fun scheduleExactAlarm(context: Context, hour: Int, minute: Int, requestCode: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("IS_REPEATING", false)
                putExtra("HOUR", hour)
                putExtra("MINUTE", minute)
                putExtra("REQUEST_ID", requestCode)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}