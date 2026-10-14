package no.jene.weightcalendar.data

import androidx.room.withTransaction
import java.io.InputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// The repository holds the app's rules for reading and writing data.
// The screens talk to the repository, never directly to the database.
class WeightRepository(private val db: WeightDatabase) {

    private val dao = db.weightDao()

    val entries = dao.observeAll()

    // Save the weight for a day. If days were skipped since the previous entry,
    // fill them by a straight line between the two weights and mark them as estimated.
    suspend fun logWeight(date: LocalDate, weightKg: Double) {
        val previous = dao.lastBefore(date)
        val filled = if (previous == null) emptyList() else interpolate(previous, date, weightKg)
        dao.upsert(filled + WeightEntry(date, weightKg, estimated = false))
    }

    // Read a CSV file in the format "date,weight_kg,estimated" (header line first)
    // and add or overwrite those days. Returns the number of days imported.
    suspend fun importCsv(input: InputStream): Int {
        val rows = input.bufferedReader().useLines { lines ->
            lines.drop(1)                        // skip the header
                .filter { it.isNotBlank() }
                .map { line ->
                    val (date, weight, estimated) = line.split(",").map { it.trim() }
                    WeightEntry(LocalDate.parse(date), weight.toDouble(), estimated == "1")
                }
                .toList()
        }
        db.withTransaction { dao.upsert(rows) }  // all or nothing, and much faster
        return rows.size
    }

    private fun interpolate(previous: WeightEntry, date: LocalDate, weightKg: Double): List<WeightEntry> {
        val gap = ChronoUnit.DAYS.between(previous.date, date)
        return (1 until gap).map { i ->
            val w = previous.weightKg + (weightKg - previous.weightKg) * i / gap
            WeightEntry(previous.date.plusDays(i), w, estimated = true)
        }
    }
}
