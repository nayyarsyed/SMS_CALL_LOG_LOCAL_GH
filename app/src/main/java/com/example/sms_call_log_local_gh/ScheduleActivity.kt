package com.example.sms_call_log_local_gh

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.util.Calendar

class ScheduleActivity : AppCompatActivity() {
    private val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val weekdays = BooleanArray(7)
    private lateinit var enabledSwitch: SwitchCompat
    private lateinit var frequencySpinner: Spinner
    private lateinit var timeButton: Button
    private lateinit var daysButton: Button
    private lateinit var monthButton: Button
    private var hour = 2
    private var minute = 0
    private var monthDay = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE)
        hour = preferences.getInt(BackupScheduler.KEY_HOUR, 2)
        minute = preferences.getInt(BackupScheduler.KEY_MINUTE, 0)
        monthDay = preferences.getInt(BackupScheduler.KEY_MONTH_DAY, 1)
        val selectedDays = preferences.getInt(BackupScheduler.KEY_WEEKDAYS, 1 shl Calendar.MONDAY)
        for (index in days.indices) weekdays[index] = (selectedDays and (1 shl (index + 1))) != 0

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        layout.addView(TextView(this).apply {
            text = "Schedule Backup"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        enabledSwitch = SwitchCompat(this).apply {
            text = "Enable automatic backup"
            isChecked = preferences.getBoolean(BackupScheduler.KEY_ENABLED, false)
        }
        layout.addView(enabledSwitch, rowParams())

        layout.addView(label("Frequency"))
        val frequencyOptions = listOf("Hourly", "Daily", "Weekly", "Monthly")
        val frequencyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            frequencyOptions
        )
        frequencySpinner = Spinner(this).apply {
            adapter = frequencyAdapter
            val saved = preferences.getString(BackupScheduler.KEY_FREQUENCY, "Daily")
            setSelection(frequencyAdapter.getPosition(saved))
        }
        layout.addView(frequencySpinner, rowParams())

        timeButton = button("") { showTimePicker() }
        layout.addView(timeButton, rowParams())
        daysButton = button("") { showDaysPicker() }
        layout.addView(daysButton, rowParams())
        monthButton = button("") { showMonthPicker() }
        layout.addView(monthButton, rowParams())
        frequencySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) = updateVisibility()
        }
        updateVisibility()
        layout.addView(TextView(this).apply {
            text = "WorkManager runs backups in the background; Android may defer execution to protect battery life."
            textSize = 14f
        }, rowParams())
        layout.addView(button("Save schedule") { saveSchedule() }, rowParams())
        setContentView(layout)
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            hour = selectedHour
            minute = selectedMinute
            updateButtons()
        }, hour, minute, false).show()
    }

    private fun showDaysPicker() {
        val draft = weekdays.copyOf()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose weekdays")
            .setMultiChoiceItems(days.toTypedArray(), draft) { _, index, checked ->
                draft[index] = checked
            }
            .setPositiveButton("Done") { _, _ ->
                draft.copyInto(weekdays)
                if (weekdays.none { it }) weekdays[1] = true
                updateButtons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMonthPicker() {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 31
            value = monthDay
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Day of month")
            .setView(picker)
            .setPositiveButton("Done") { _, _ ->
                monthDay = picker.value
                updateButtons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSchedule() {
        val preferences = getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE)
        val weekdayMask = weekdays.indices
            .filter { weekdays[it] }
            .fold(0) { mask, index -> mask or (1 shl (index + 1)) }
        preferences.edit().apply {
            putString(BackupScheduler.KEY_FREQUENCY, frequencySpinner.selectedItem.toString())
            putInt(BackupScheduler.KEY_HOUR, hour)
            putInt(BackupScheduler.KEY_MINUTE, minute)
            putInt(BackupScheduler.KEY_WEEKDAYS, weekdayMask)
            putInt(BackupScheduler.KEY_MONTH_DAY, monthDay)
        }.apply()
        BackupScheduler.configure(this, enabledSwitch.isChecked)
        finish()
    }

    private fun updateVisibility() {
        val frequency = frequencySpinner.selectedItem?.toString() ?: "Daily"
        timeButton.visibility = if (frequency == "Hourly") android.view.View.GONE else android.view.View.VISIBLE
        daysButton.visibility = if (frequency == "Weekly") android.view.View.VISIBLE else android.view.View.GONE
        monthButton.visibility = if (frequency == "Monthly") android.view.View.VISIBLE else android.view.View.GONE
        updateButtons()
    }

    private fun updateButtons() {
        timeButton.text = "Time: ${String.format("%02d:%02d", hour, minute)}"
        daysButton.text = "Days: " + days.indices.filter { weekdays[it] }.joinToString { days[it] }
        monthButton.text = "Day of month: $monthDay"
    }

    private fun button(title: String, action: () -> Unit) =
        com.google.android.material.button.MaterialButton(this).apply {
            text = title
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun label(title: String) = TextView(this).apply {
        text = title
        textSize = 16f
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(12) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
