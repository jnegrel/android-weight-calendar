package no.jene.weightcalendar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.jene.weightcalendar.data.WeightDatabase
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.WeightRepository
import no.jene.weightcalendar.data.toDays
import java.time.LocalDate

// A ViewModel holds the data a screen shows and survives screen rotation
// (the screen itself is destroyed and redrawn when the phone is rotated).
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeightRepository(WeightDatabase.get(app))

    // All days, oldest first, with their change since the previous day.
    // The screen observes this and redraws when it changes.
    val days: StateFlow<Map<LocalDate, Day>> = repository.entries
        .map { it.toDays() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setWeight(date: LocalDate, weightKg: Double) {
        // Database work must not block the screen, so it runs in a coroutine
        // (a lightweight background task tied to this ViewModel's lifetime).
        viewModelScope.launch { repository.setWeight(date, weightKg) }
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
