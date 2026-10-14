package no.jene.weightcalendar.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// DAO = "Data Access Object": the list of database operations the app needs.
// We only write the SQL (or an annotation); Room generates the actual code at build time.
@Dao
interface WeightDao {

    // A Flow is a stream of values: every time the table changes, a new list is emitted
    // and the screen redraws automatically.
    @Query("SELECT * FROM weights ORDER BY date")
    fun observeAll(): Flow<List<WeightEntry>>

    // The most recent entry strictly before the given day (null if there is none).
    @Query("SELECT * FROM weights WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun lastBefore(date: LocalDate): WeightEntry?

    // "Upsert" = insert, or update if a row with the same date already exists.
    @Upsert
    suspend fun upsert(entries: List<WeightEntry>)
}
