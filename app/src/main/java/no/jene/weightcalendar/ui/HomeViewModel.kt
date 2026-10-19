package no.jene.weightcalendar.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.jene.weightcalendar.backup.Backup
import no.jene.weightcalendar.data.Settings
import no.jene.weightcalendar.data.WeightDatabase
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.WeightRepository
import no.jene.weightcalendar.data.toDays
import no.jene.weightcalendar.reminder.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// A ViewModel holds the data a screen shows and survives screen rotation
// (the screen itself is destroyed and redrawn when the phone is rotated).
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeightRepository(WeightDatabase.get(app))

    // All days, oldest first, with their change since the previous day.
    // The screen observes this and redraws when it changes.
    val days: StateFlow<Map<LocalDate, Day>> = repository.entries
        .map { it.toDays() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val settings = Settings(app)

    // The weight goal in kg, or null when none is set. MutableStateFlow = a StateFlow we
    // can change ourselves; the screen only sees the read-only version.
    private val _goal = MutableStateFlow(settings.goalKg)
    val goal: StateFlow<Double?> = _goal

    // null removes the goal.
    fun setGoal(kg: Double?) {
        settings.goalKg = kg
        _goal.value = kg
    }

    // Daily reminder settings, shown in the reminder dialog.
    private val _reminder = MutableStateFlow(ReminderSetting(settings.reminderEnabled, settings.reminderTime))
    val reminder: StateFlow<ReminderSetting> = _reminder

    fun setReminder(enabled: Boolean, time: LocalTime) {
        settings.reminderEnabled = enabled
        settings.reminderTime = time
        _reminder.value = ReminderSetting(enabled, time)
        Reminder.schedule(getApplication())   // plan (or cancel) the next reminder
    }

    // Daily backup settings, shown in the backup dialog.
    private val _backup = MutableStateFlow(readBackupSetting())
    val backup: StateFlow<BackupSetting> = _backup

    private fun readBackupSetting() = BackupSetting(
        enabled = settings.backupEnabled,
        folderName = settings.backupFolder?.let { Backup.folderName(getApplication(), it) },
        lastBackup = settings.lastBackup,
    )

    // Re-read the settings (the background backup may have run since the screen opened).
    fun refreshBackup() { _backup.value = readBackupSetting() }

    // The folder comes from Android's folder picker. By default the app may only use it
    // until the phone restarts: takePersistableUriPermission keeps the access for good.
    fun setBackupFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        settings.backupFolder = uri.toString()
        settings.backupEnabled = true          // choosing a folder turns the backup on
        Backup.schedule(getApplication())
        refreshBackup()
    }

    fun setBackupEnabled(enabled: Boolean) {
        settings.backupEnabled = enabled
        Backup.schedule(getApplication())
        refreshBackup()
    }

    // Returns the file name, or throws if the backup failed.
    suspend fun backupNow(): String = Backup.run(getApplication()).also { refreshBackup() }

    fun setWeight(date: LocalDate, weightKg: Double, note: String?) {
        // Database work must not block the screen, so it runs in a coroutine
        // (a lightweight background task tied to this ViewModel's lifetime).
        viewModelScope.launch { repository.setWeight(date, weightKg, note) }
        // Today is logged: the reminder (if it was shown) is no longer needed.
        if (date == LocalDate.now()) Reminder.dismissNotification(getApplication())
    }

    fun delete(date: LocalDate) {
        viewModelScope.launch { repository.delete(date) }
    }

    // Returns the number of imported days, or throws if the file is not valid.
    // The file may also contain settings: the screen is updated and the reminder replanned.
    suspend fun importCsv(uri: Uri): Int {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("Cannot open file")
        val count = input.use { repository.importCsv(it, settings) }
        _goal.value = settings.goalKg
        _reminder.value = ReminderSetting(settings.reminderEnabled, settings.reminderTime)
        Reminder.schedule(getApplication())
        return count
    }

    // Returns the number of exported days, or throws if the file cannot be written.
    suspend fun exportCsv(uri: Uri): Int {
        val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot write file")
        return output.use { repository.exportCsv(it, settings) }
    }
}

data class ReminderSetting(val enabled: Boolean, val time: LocalTime)

data class BackupSetting(val enabled: Boolean, val folderName: String?, val lastBackup: LocalDateTime?)
