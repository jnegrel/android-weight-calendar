package no.jene.weightcalendar.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDateTime
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

    // Daily backup: on/off, the folder chosen with Android's folder picker (a content:// address,
    // stored as text), and when the last backup was written.
    var backupEnabled: Boolean
        get() = prefs.getBoolean(BACKUP_ON, false)
        set(value) = prefs.edit { putBoolean(BACKUP_ON, value) }

    var backupFolder: String?
        get() = prefs.getString(BACKUP_FOLDER, null)
        set(value) = prefs.edit { putString(BACKUP_FOLDER, value) }

    var lastBackup: LocalDateTime?
        get() = prefs.getString(LAST_BACKUP, null)?.let(LocalDateTime::parse)
        set(value) = prefs.edit { putString(LAST_BACKUP, value?.toString()) }

    private companion object {
        const val GOAL = "goal_kg"
        const val REMINDER_ON = "reminder_on"
        const val REMINDER_TIME = "reminder_time"   // seconds since midnight
        const val BACKUP_ON = "backup_on"
        const val BACKUP_FOLDER = "backup_folder"
        const val LAST_BACKUP = "last_backup"
    }
}
