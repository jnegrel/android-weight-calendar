package no.jene.weightcalendar.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

// Small settings, kept in SharedPreferences: a simple key -> value file managed by Android,
// better suited than a database table for a handful of values.
// Used by the screen (through the ViewModel) and by the reminder worker.
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Weight goal in kg, or null when none is set.
    var goalKg: Double?
        get() = if (prefs.contains(GOAL)) prefs.getFloat(GOAL, 0f).toDouble() else null
        set(value) = prefs.edit { if (value == null) remove(GOAL) else putFloat(GOAL, value.toFloat()) }

    // Daily reminder: on/off, and the time of day it fires (default 09:00).
    var reminderEnabled: Boolean
        get() = prefs.getBoolean(REMINDER_ON, false)
        set(value) = prefs.edit { putBoolean(REMINDER_ON, value) }

    var reminderTime: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt(REMINDER_TIME, 9 * 3600).toLong())
        set(value) = prefs.edit { putInt(REMINDER_TIME, value.toSecondOfDay()) }

    private companion object {
        const val GOAL = "goal_kg"
        const val REMINDER_ON = "reminder_on"
        const val REMINDER_TIME = "reminder_time"   // seconds since midnight
    }
}
