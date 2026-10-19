package no.jene.weightcalendar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.jene.weightcalendar.data.Settings
import no.jene.weightcalendar.data.WeightDatabase
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.WeightRepository
import no.jene.weightcalendar.data.toDays
import no.jene.weightcalendar.reminder.Reminder
import java.time.LocalDate
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
    suspend fun importCsv(uri: Uri): Int {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("Cannot open file")
        return input.use { repository.importCsv(it) }
    }

    // Returns the number of exported days, or throws if the file cannot be written.
    suspend fun exportCsv(uri: Uri): Int {
        val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot write file")
        return output.use { repository.exportCsv(it) }
    }
}

data class ReminderSetting(val enabled: Boolean, val time: LocalTime)
