package no.jene.weightcalendar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.jene.weightcalendar.data.WeightDatabase
import no.jene.weightcalendar.data.WeightEntry
import no.jene.weightcalendar.data.WeightRepository
import java.time.LocalDate

// A ViewModel holds the data a screen shows and survives screen rotation
// (the screen itself is destroyed and redrawn when the phone is rotated).
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeightRepository(WeightDatabase.get(app))

    // All entries, oldest first. The screen observes this and redraws when it changes.
    val entries: StateFlow<List<WeightEntry>> = repository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun logToday(weightKg: Double) {
        // Database work must not block the screen, so it runs in a coroutine
        // (a lightweight background task tied to this ViewModel's lifetime).
        viewModelScope.launch { repository.logWeight(LocalDate.now(), weightKg) }
    }

    // Returns the number of imported days, or throws if the file is not valid.
    suspend fun importCsv(uri: Uri): Int {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("Cannot open file")
        return input.use { repository.importCsv(it) }
    }
}
