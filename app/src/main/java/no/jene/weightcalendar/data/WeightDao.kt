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

    // Same list, read once (for the CSV export).
    @Query("SELECT * FROM weights ORDER BY date")
    suspend fun getAll(): List<WeightEntry>

    @Query("SELECT * FROM weights WHERE date = :date")
    suspend fun get(date: LocalDate): WeightEntry?

    // The nearest *measured* days around a date (null if there is none).
    // In SQLite, booleans are stored as 0/1.
    @Query("SELECT * FROM weights WHERE date < :date AND estimated = 0 ORDER BY date DESC LIMIT 1")
    suspend fun lastMeasuredBefore(date: LocalDate): WeightEntry?

    @Query("SELECT * FROM weights WHERE date > :date AND estimated = 0 ORDER BY date LIMIT 1")
    suspend fun firstMeasuredAfter(date: LocalDate): WeightEntry?

    // "Upsert" = insert, or update if a row with the same date already exists.
    @Upsert
    suspend fun upsert(entries: List<WeightEntry>)

    @Query("DELETE FROM weights WHERE date = :date")
    suspend fun delete(date: LocalDate)

    // Remove the estimated days strictly between two dates.
    @Query("DELETE FROM weights WHERE estimated = 1 AND date > :from AND date < :to")
    suspend fun deleteEstimatedBetween(from: LocalDate, to: LocalDate)
}
